package hrln.interactive.sidekit.apps.media

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RemoteArtworkTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `file name is stable for the same url and size`() {
        val url = "https://i.scdn.co/image/ab67616d00001e02000000000000000000000000"
        assertEquals(RemoteArtwork.fileName(url, 104, 104), RemoteArtwork.fileName(url, 104, 104))
    }

    @Test
    fun `file name separates sizes and urls`() {
        val url = "https://i.scdn.co/image/abc"
        assertNotEquals(RemoteArtwork.fileName(url, 104, 104), RemoteArtwork.fileName(url, 208, 208))
        assertNotEquals(RemoteArtwork.fileName(url, 104, 104), RemoteArtwork.fileName("$url?v=2", 104, 104))
    }

    @Test
    fun `file name is filesystem safe`() {
        val name = RemoteArtwork.fileName("https://example.com/a b?x=1&y=2#frag", 48, 48)
        assertTrue(name, name.matches(Regex("[0-9a-f]{64}\\.img")))
    }

    @Test
    fun `sample size keeps the decode near the drawn size`() {
        assertEquals(1, RemoteArtwork.sampleSize(300, 300, 300, 300))
        assertEquals(4, RemoteArtwork.sampleSize(640, 640, 104, 104))
        assertEquals(16, RemoteArtwork.sampleSize(2560, 2560, 104, 104))
        assertEquals(1, RemoteArtwork.sampleSize(10, 10, 104, 104))
    }

    @Test
    fun `trim drops least recently used files until under the cap`() {
        val dir = temp.newFolder("artwork")
        val files = (0 until 5).map { i ->
            File(dir, "file$i.img").apply {
                writeBytes(ByteArray(100))
                setLastModified(1_000L + i)
            }
        }

        RemoteArtwork.trim(dir, maxBytes = 250)

        assertEquals(2, dir.listFiles()!!.size)
        assertTrue(!files[0].exists() && !files[1].exists() && !files[2].exists())
        assertTrue(files[3].exists() && files[4].exists())
    }

    @Test
    fun `trim leaves a cache under the cap alone`() {
        val dir = temp.newFolder("artwork")
        File(dir, "one.img").apply { writeBytes(ByteArray(10)) }

        RemoteArtwork.trim(dir, maxBytes = 100)

        assertTrue(File(dir, "one.img").exists())
    }

    @Test
    fun `trim tolerates a missing directory`() {
        RemoteArtwork.trim(File(temp.root, "nope"), maxBytes = 100)
    }
}
