package hrln.interactive.sidekit.sdk.media

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [RemoteArtwork.clear] wipes the artwork directory and [RemoteArtwork.trim] evicts entries while
 * loads run, so an entry can disappear between the cache lookup and the decode. Decoding it anyway
 * made BitmapFactory log `Unable to decode stream: … ENOENT` for artwork that simply was not
 * cached any more, which reads like a broken image instead of a cache miss.
 */
class RemoteArtworkDecodeCachedTest {

    @Test
    fun `a missing entry is never handed to the decoder`() {
        val dir = tempDir()
        try {
            val result = RemoteArtwork.decodeCached(dir, URL, 64, 64) { file ->
                throw AssertionError("decoded absent file ${file.name}")
            }

            assertNull(result)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a cached entry is decoded`() {
        val dir = tempDir()
        try {
            val cached = File(dir, RemoteArtwork.fileName(URL, 64, 64)).apply { writeBytes(byteArrayOf(1)) }

            val result = RemoteArtwork.decodeCached(dir, URL, 64, 64) { it.name }

            assertEquals(cached.name, result)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `an entry of a different size does not count as cached`() {
        val dir = tempDir()
        try {
            File(dir, RemoteArtwork.fileName(URL, 64, 64)).writeBytes(byteArrayOf(1))

            val result = RemoteArtwork.decodeCached(dir, URL, 128, 128) { it.name }

            assertNull(result)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun tempDir(): File = File.createTempFile("sidekit-artwork", "").apply {
        delete()
        mkdirs()
    }

    private companion object {
        const val URL = "https://i.scdn.co/image/ab67616d00001e02"
    }
}
