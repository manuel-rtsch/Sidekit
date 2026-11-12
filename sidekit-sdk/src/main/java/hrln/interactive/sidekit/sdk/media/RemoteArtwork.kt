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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * Artwork that lives on the network, for remote libraries (Spotify, podcast feeds).
 *
 * Local media keeps using [Thumbnails], which asks MediaProvider for a cached thumbnail. There is
 * no provider here, so this object does the same job with a bounded HTTP fetch:
 *
 * - memory: the same 1/32-of-heap LRU as [Thumbnails], shared across sizes;
 * - disk: `cacheDir/artwork`, one file per url+size, trimmed to [MAX_DISK_BYTES] oldest-first, so
 *   scrolling the same list after a restart does not refetch;
 * - network: at most two fetches at once, 10 s timeouts, an 8 MB cap, and cancellation that
 *   disconnects the connection when the row leaves composition;
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

    /** Serialises the delete pass so two trims never account for the same bytes twice. */
    private val trimLock = Any()

    @Volatile private var memory: LruCache<String, Bitmap>? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val fetch = Dispatchers.IO.limitedParallelism(2)

    private fun memory(context: Context): LruCache<String, Bitmap> = memory ?: synchronized(this) {
        memory ?: run {
            val memoryClassMb = context.getSystemService(ActivityManager::class.java)?.memoryClass ?: 128
            object : LruCache<String, Bitmap>(memoryClassMb * 1024 * 1024 / 32) {
                override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
            }.also { memory = it }
        }
    }

    private fun key(url: String, w: Int, h: Int) = "$url@${w}x$h"

    private fun cacheDir(context: Context) = File(context.cacheDir, "artwork").apply { mkdirs() }

    /** Stable file name for a url+size. Hashed so URLs with query strings stay filesystem-safe. */
    internal fun fileName(url: String, w: Int, h: Int): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key(url, w, h).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) } + ".img"
    }

    fun cached(context: Context, url: String, w: Int, h: Int): Bitmap? = memory(context)[key(url, w, h)]

    /** Loads [url] cropped to [w] x [h] px. Null while offline, on a bad response, or undecodable. */
    suspend fun load(context: Context, url: String, w: Int, h: Int): Bitmap? {
        if (w <= 0 || h <= 0 || url.isBlank()) return null
        val cache = memory(context)
        val key = key(url, w, h)
        cache[key]?.let { return it }
        return withContext(fetch) {
            cache[key]?.let { return@withContext it }
            val bitmap = readDisk(context, url, w, h) ?: download(context, url, w, h) ?: return@withContext null
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

    private fun readDisk(context: Context, url: String, w: Int, h: Int): Bitmap? = try {
        val file = File(cacheDir(context), fileName(url, w, h))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, w, h)
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }

    private suspend fun download(context: Context, url: String, w: Int, h: Int): Bitmap? {
        val bytes = get(url) ?: return null
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
        writeDisk(context, url, w, h, bitmap)
        return bitmap
    }

    private suspend fun get(url: String): ByteArray? {
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "image/*")
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

    private fun writeDisk(context: Context, url: String, w: Int, h: Int, bitmap: Bitmap) {
        val file = File(cacheDir(context), fileName(url, w, h))
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
        trim(cacheDir(context))
    }

    /**
     * Keeps the disk cache under [MAX_DISK_BYTES], dropping least-recently-used files first.
     *
     * [load] runs on two threads at once, so this directory changes while it is being trimmed:
     * in-flight [AtomicFile] writes add `.new` files and finish by renaming them. Metadata is
     * therefore read once into [Entry] values and sorted afterwards. A comparator that re-reads
     * `lastModified()` sees a file's key change between comparisons when a concurrent write lands;
     * TimSort then throws `IllegalArgumentException: Comparison method violates its general
     * contract!` and that kills the process.
     */
    internal fun trim(dir: File, maxBytes: Long = MAX_DISK_BYTES) {
        val entries = dir.listFiles()
            ?.filterNot { it.name.endsWith(ATOMIC_TEMP_SUFFIX) }
            ?.map { file -> Entry(file, file.length(), file.lastModified()) }
            ?.sortedBy { it.lastModified }
            ?: return
        synchronized(trimLock) {
            var total = entries.sumOf { it.bytes }
            for (entry in entries) {
                if (total <= maxBytes) break
                total -= entry.bytes
                entry.file.delete()
            }
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
    contentDescription: String? = null
) {
    BoxWithConstraints(modifier) {
        RemoteArtworkImage(
            url = url,
            width = maxWidth.value.coerceIn(1f, 1024f).dp,
            height = maxHeight.value.coerceIn(1f, 1024f).dp,
            modifier = Modifier.fillMaxSize(),
            placeholder = placeholder,
            contentDescription = contentDescription
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
    contentDescription: String? = null
) {
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val w = with(density) { width.roundToPx() }
    val h = with(density) { height.roundToPx() }
    val inspection = LocalInspectionMode.current
    var image by remember(url, w, h) {
        mutableStateOf(url?.let { RemoteArtwork.cached(context, it, w, h)?.asImageBitmap() })
    }
    if (url != null && image == null && !inspection) {
        LaunchedEffect(url, w, h) {
            image = RemoteArtwork.load(context, url, w, h)?.asImageBitmap()
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
