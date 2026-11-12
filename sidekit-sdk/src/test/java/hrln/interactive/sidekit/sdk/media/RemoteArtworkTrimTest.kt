package hrln.interactive.sidekit.sdk.media

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RemoteArtwork.trim] runs while other loads write into the same cache directory. Reading file
 * metadata inside the sort made TimSort throw "Comparison method violates its general contract!"
 * on the Sidephone, so these tests pin the snapshot behaviour.
 */
class RemoteArtworkTrimTest {

    @Test
    fun `trim reads each file's metadata once instead of during the sort`() {
        val timestampReads = AtomicInteger()
        val lengthReads = AtomicInteger()
        val files = (0 until 64).map { index ->
            object : File("artwork-$index.img") {
                // Values move on every read, like files being written by a second load().
                override fun lastModified(): Long = timestampReads.incrementAndGet().toLong() * (index + 1)
                override fun length(): Long = lengthReads.incrementAndGet().toLong()
                override fun delete(): Boolean = true
            }
        }
        val dir = object : File("artwork") {
            override fun listFiles(): Array<File> = files.toTypedArray()
        }

        RemoteArtwork.trim(dir, maxBytes = 0)

        // The old comparator re-read these O(n log n) times and saw inconsistent keys.
        assertEquals(files.size, timestampReads.get())
        assertEquals(files.size, lengthReads.get())
    }

    @Test
    fun `trim deletes oldest files until the cache is under budget`() {
        val dir = tempDir()
        try {
            val oldest = write(dir, "oldest.img", bytes = 10, ageMinutes = 3)
            val middle = write(dir, "middle.img", bytes = 10, ageMinutes = 2)
            val newest = write(dir, "newest.img", bytes = 10, ageMinutes = 1)

            RemoteArtwork.trim(dir, maxBytes = 15)

            assertFalse(oldest.exists())
            assertFalse(middle.exists())
            assertTrue(newest.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `trim leaves in-flight atomic writes alone`() {
        val dir = tempDir()
        try {
            val partial = write(dir, "cover.img.new", bytes = 100, ageMinutes = 5)
            val cached = write(dir, "cover.img", bytes = 10, ageMinutes = 1)

            RemoteArtwork.trim(dir, maxBytes = 0)

            assertTrue(partial.exists())
            assertFalse(cached.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun tempDir(): File = File.createTempFile("sidekit-artwork", "").apply {
        delete()
        mkdirs()
    }

    private fun write(dir: File, name: String, bytes: Int, ageMinutes: Long): File {
        val file = File(dir, name)
        file.writeBytes(ByteArray(bytes))
        file.setLastModified(System.currentTimeMillis() - ageMinutes * 60_000)
        return file
    }
}
