package hrln.interactive.sidekit.sdk.media

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.util.AtomicFile
import hrln.interactive.sidekit.design.components.SidekitIconImage
import hrln.interactive.sidekit.design.icons.SidekitIcon
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext

/**
 * Artwork that lives on the network, for remote libraries (Spotify, podcast feeds).
 *
 * Local media keeps using [Thumbnails], which asks MediaProvider for a cached thumbnail. There is
 * no provider here, so this object does the same job with a bounded HTTP fetch:
 *
 * - memory: the same 1/32-of-heap LRU as [Thumbnails], shared across sizes;
 * - disk: `cacheDir/artwork`, one file per url+size, trimmed to [MAX_DISK_BYTES] oldest-first, so
 *   scrolling the same list after a restart does not refetch;
 * - network: at most two fetches at once, one fetch per url+size however many rows ask for it,
 *   10 s timeouts, an 8 MB cap, and cancellation that disconnects the connection once the last
 *   row wanting it leaves composition;
 * - decode: bounds first, `inSampleSize` to roughly the drawn size, then [Thumbnails.cropTo] in
 *   RGB_565, so a 104 px tile never holds a full-resolution cover.
 *
 * Failures return null and are not cached, so a later scroll retries.
 */
object RemoteArtwork {
    internal const val MAX_DISK_BYTES = 16L * 1024 * 1024
    private const val MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024
    private const val TIMEOUT_MS = 10_000
    private const val ATOMIC_TEMP_SUFFIX = ".new"

    /** A write whose temp file is older than this is not in flight; nothing takes an hour. */
    private const val STALE_TEMP_MS = 60L * 60_000

    /** Serialises the delete pass so two trims never account for the same bytes twice. */
    private val trimLock = Any()

    @Volatile private var memory: LruCache<String, Bitmap>? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val fetch = Dispatchers.IO.limitedParallelism(2)

    /** One fetch per url+size, however many rows ask for that picture at the same time. */
    private val flights = SingleFlight<Bitmap>(CoroutineScope(SupervisorJob() + fetch))

    private fun memory(context: Context): LruCache<String, Bitmap> = memory ?: synchronized(this) {
        memory ?: run {
            val memoryClassMb = context.getSystemService(ActivityManager::class.java)?.memoryClass ?: 128
            object : LruCache<String, Bitmap>(memoryClassMb * 1024 * 1024 / 32) {
                override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
            }.also { memory = it }
        }
    }

    private fun key(url: String, w: Int, h: Int, headers: Map<String, String>) = "$url@${w}x$h${headerFingerprint(headers)}"

    /**
     * Distinguishes entries fetched with different request headers.
     *
     * A provider that authenticates artwork by header (Jellyfin sends `Authorization`) must not
     * share a disk or memory entry with an unauthenticated request for the same URL, and the same
     * URL can be fetched by two accounts with different headers. The header names and values are
     * hashed, never stored: a token must not end up in a file name or in a log line. Order does
     * not matter, so a map built in another order still hits the same entry.
     */
    private fun headerFingerprint(headers: Map<String, String>): String {
        if (headers.isEmpty()) return ""
        val canonical = headers.entries
            .sortedBy { it.key.lowercase() }
            .joinToString("\n") { "${it.key.lowercase()}:${it.value}" }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return "#" + digest.joinToString("") { "%02x".format(it) }
    }

    private fun cacheDir(context: Context) = File(context.cacheDir, "artwork").apply { mkdirs() }

    /**
     * Stable file name for a url+size (+headers). Hashed so URLs with query strings and header
     * values stay out of the filesystem.
     */
    internal fun fileName(url: String, w: Int, h: Int, headers: Map<String, String> = emptyMap()): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key(url, w, h, headers).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) } + ".img"
    }

    fun cached(
        context: Context,
        url: String,
        w: Int,
        h: Int,
        headers: Map<String, String> = emptyMap()
    ): Bitmap? = memory(context)[key(url, w, h, headers)]

    /**
     * Loads [url] cropped to [w] x [h] px. Null while offline, on a bad response, or undecodable.
     *
     * [headers] travel with every request for this image, which is how a provider that authenticates
     * artwork with a header keeps its token out of the URL.
     */
    suspend fun load(
        context: Context,
        url: String,
        w: Int,
        h: Int,
        headers: Map<String, String> = emptyMap()
    ): Bitmap? {
        if (w <= 0 || h <= 0 || url.isBlank()) return null
        val cache = memory(context)
        val key = key(url, w, h, headers)
        cache[key]?.let { return it }
        // Two rows routinely want the same picture at the same size — the notification and the
        // now-playing backdrop, say. Without the shared flight both miss the memory cache, both
        // download, and both write the same AtomicFile temp path: the first rename wins and the
        // second fails with `Failed to rename … .img.new`, leaking the temp file it leaves behind.
        return flights.run(key) {
            cache[key]?.let { return@run it }
            val bitmap = readDisk(context, url, w, h, headers)
                ?: download(context, url, w, h, headers)
                ?: return@run null
            cache.put(key, bitmap)
            bitmap
        }
    }

    /** Drops the in-memory bitmaps; the disk cache stays until evicted or [clear]. */
    fun clearMemory() {
        memory?.evictAll()
    }

    /** Removes memory and disk copies (sign-out, "clear cache"). */
    fun clear(context: Context) {
        clearMemory()
        cacheDir(context).deleteRecursively()
    }

    private fun readDisk(
        context: Context,
        url: String,
        w: Int,
        h: Int,
        headers: Map<String, String> = emptyMap()
    ): Bitmap? = try {
        decodeCached(cacheDir(context), url, w, h, headers) { file ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, w, h)
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        }
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }

    /**
     * Runs [decode] on the cached entry for url+size, or returns null when there is none.
     *
     * The directory is not stable: [clear] deletes all of it when the user signs out and [trim]
     * evicts entries on every write, so the file can be gone before the decoder opens it. Handing
     * a missing path to BitmapFactory made every ordinary miss show up in logcat as
     * `Unable to decode stream: … ENOENT`, which reads like broken artwork. A cache miss is not
     * an error, so it must not be reported as one.
     */
    internal inline fun <T> decodeCached(
        dir: File,
        url: String,
        w: Int,
        h: Int,
        headers: Map<String, String> = emptyMap(),
        decode: (File) -> T?
    ): T? {
        val file = File(dir, fileName(url, w, h, headers))
        return if (file.isFile) decode(file) else null
    }

    private suspend fun download(
        context: Context,
        url: String,
        w: Int,
        h: Int,
        headers: Map<String, String>
    ): Bitmap? {
        val bytes = get(url, headers) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, w, h)
        }
        val decoded = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: OutOfMemoryError) {
            null
        } ?: return null
        // cropTo recycles the source; store the cropped result so the next restart decodes it directly.
        val bitmap = try {
            Thumbnails.cropTo(decoded, w, h)
        } catch (_: OutOfMemoryError) {
            decoded.recycle()
            return null
        }
        writeDisk(context, url, w, h, bitmap, headers)
        return bitmap
    }

    private suspend fun get(url: String, headers: Map<String, String>): ByteArray? {
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                applyRequestHeaders(this, headers)
            }
        } catch (_: Exception) {
            return null
        }
        // A row that leaves composition cancels this coroutine; drop the socket rather than
        // finishing a download nobody wants.
        val cancel = currentCoroutineContext()[Job]?.invokeOnCompletion { connection.disconnect() }
        return try {
            if (connection.responseCode !in 200..299) return null
            val declared = connection.contentLengthLong
            if (declared > MAX_DOWNLOAD_BYTES) return null
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream(declared.takeIf { it in 1..MAX_DOWNLOAD_BYTES }?.toInt() ?: 8192)
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_DOWNLOAD_BYTES) return null
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        } catch (_: Exception) {
            null
        } finally {
            cancel?.dispose()
            connection.disconnect()
        }
    }

    /**
     * Sets the fixed accept header plus whatever the caller supplies. Provider headers win over the
     * default when both name the same field.
     */
    internal fun applyRequestHeaders(connection: HttpURLConnection, headers: Map<String, String>) {
        connection.setRequestProperty("Accept", "image/*")
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
    }

    private fun writeDisk(
        context: Context,
        url: String,
        w: Int,
        h: Int,
        bitmap: Bitmap,
        headers: Map<String, String> = emptyMap()
    ) {
        val file = File(cacheDir(context), fileName(url, w, h, headers))
        val atomic = AtomicFile(file)
        try {
            val out = atomic.startWrite()
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                atomic.failWrite(out)
                return
            }
            atomic.finishWrite(out)
        } catch (_: Exception) {
            atomic.failWrite(null)
            return
        }
        // finishWrite only logs when its rename fails (a clear() that removed the directory under
        // this write, say) and leaves the temp file behind. It is not the cache entry, so drop it
        // rather than let it sit outside trim()'s budget until [STALE_TEMP_MS] passes.
        if (!file.isFile) tempFile(file).delete()
        trim(cacheDir(context))
    }

    private fun tempFile(file: File) = File(file.path + ATOMIC_TEMP_SUFFIX)

    /**
     * Keeps the disk cache under [MAX_DISK_BYTES], dropping least-recently-used files first.
     *
     * [load] runs on two threads at once, so this directory changes while it is being trimmed:
     * in-flight [AtomicFile] writes add `.new` files and finish by renaming them. Metadata is
     * therefore read once into [Entry] values and sorted afterwards. A comparator that re-reads
     * `lastModified()` sees a file's key change between comparisons when a concurrent write lands;
     * TimSort then throws `IllegalArgumentException: Comparison method violates its general
     * contract!` and that kills the process.
     *
     * Temp files are never charged to the budget — a write that is still running owns its own — but
     * one older than [STALE_TEMP_MS] is a leftover from a write that failed to rename, and is
     * removed here so it cannot accumulate for the life of the install.
     */
    internal fun trim(
        dir: File,
        maxBytes: Long = MAX_DISK_BYTES,
        now: Long = System.currentTimeMillis()
    ) {
        val files = dir.listFiles() ?: return
        val entries = files
            .filterNot { it.name.endsWith(ATOMIC_TEMP_SUFFIX) }
            .map { file -> Entry(file, file.length(), file.lastModified()) }
            .sortedBy { it.lastModified }
        val staleTemps = files
            .filter { it.name.endsWith(ATOMIC_TEMP_SUFFIX) }
            .map { file -> Entry(file, 0L, file.lastModified()) }
            .filter { now - it.lastModified > STALE_TEMP_MS }
        synchronized(trimLock) {
            var total = entries.sumOf { it.bytes }
            for (entry in entries) {
                if (total <= maxBytes) break
                total -= entry.bytes
                entry.file.delete()
            }
            staleTemps.forEach { it.file.delete() }
        }
    }

    private class Entry(val file: File, val bytes: Long, val lastModified: Long)

    /** Power of two that keeps the decode near [w] x [h] instead of full resolution. */
    internal fun sampleSize(width: Int, height: Int, w: Int, h: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= w && height / (sample * 2) >= h) sample *= 2
        return sample
    }
}

/**
 * Draws [url] filling the space this composable is given, with a Sidekit icon until it arrives
 * (or if it never does). Requests cancel when the composable leaves composition, and a hit in the
 * memory cache paints without a recomposition of the row.
 */
@Composable
fun RemoteArtworkImage(
    url: String?,
    modifier: Modifier = Modifier,
    placeholder: SidekitIcon = SidekitIcon.Album,
    contentDescription: String? = null,
    headers: Map<String, String> = emptyMap()
) {
    BoxWithConstraints(modifier) {
        RemoteArtworkImage(
            url = url,
            width = maxWidth.value.coerceIn(1f, 1024f).dp,
            height = maxHeight.value.coerceIn(1f, 1024f).dp,
            modifier = Modifier.fillMaxSize(),
            placeholder = placeholder,
            contentDescription = contentDescription,
            headers = headers
        )
    }
}

@Composable
fun RemoteArtworkImage(
    url: String?,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    placeholder: SidekitIcon = SidekitIcon.Album,
    contentDescription: String? = null,
    headers: Map<String, String> = emptyMap()
) {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val w = with(density) { width.roundToPx() }
    val h = with(density) { height.roundToPx() }
    val inspection = LocalInspectionMode.current
    var image by remember(url, w, h, headers) {
        mutableStateOf(url?.let { RemoteArtwork.cached(context, it, w, h, headers)?.asImageBitmap() })
    }
    if (url != null && image == null && !inspection) {
        LaunchedEffect(url, w, h, headers) {
            image = RemoteArtwork.load(context, url, w, h, headers)?.asImageBitmap()
        }
    }
    Box(modifier.drawBehind { image?.let { drawRemoteArtwork(it) } }) {
        if (image == null) {
            SidekitIconImage(
                icon = placeholder,
                modifier = Modifier.align(Alignment.Center),
                contentDescription = contentDescription
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRemoteArtwork(image: ImageBitmap) {
    val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
    drawImage(image, srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height), dstSize = dst)
}
