package hrln.interactive.sidekit.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small rasterised icons for installed apps, loaded off the main thread and cached. */
internal object AppIcons {
    private const val SIZE = 64

    private val cache = object : LruCache<String, Bitmap>(512 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun load(context: Context, packageName: String): Bitmap? = cache.get(packageName) ?: withContext(Dispatchers.IO) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).also { bitmap ->
                drawable.setBounds(0, 0, SIZE, SIZE)
                drawable.draw(Canvas(bitmap))
                cache.put(packageName, bitmap)
            }
        }.getOrNull()
    }
}
