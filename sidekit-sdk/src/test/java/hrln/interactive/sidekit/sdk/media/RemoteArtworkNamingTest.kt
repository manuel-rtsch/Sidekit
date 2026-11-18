package hrln.interactive.sidekit.sdk.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cache key and the decode sampling: two screens drawing the same track at different sizes must
 * not share an entry, and the key has to be a safe file name.
 */
class RemoteArtworkNamingTest {
    @Test
    fun `file name is stable for the same url and size`() {
        val url = "https://example.com/image/ab67616d00001e02000000000000000000000000"
        assertEquals(RemoteArtwork.fileName(url, 104, 104), RemoteArtwork.fileName(url, 104, 104))
    }

    @Test
    fun `file name separates sizes and urls`() {
        val url = "https://example.com/image/abc"
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
}
