package hrln.interactive.sidekit.sdk.media

import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Providers that authenticate artwork with a header (Jellyfin sends `Authorization`) must get their
 * own cache entries and their headers on the wire. Sharing an entry with the unauthenticated
 * request for the same URL would either serve a 401 body nobody decoded or leak one account's
 * artwork into another's list, and putting the token in the URL is what this avoids.
 */
class RemoteArtworkHeadersTest {

    @Test
    fun `no headers keeps the previous cache key`() {
        // Entries written before this change must still be readable: the empty map is not a new key.
        assertEquals(
            RemoteArtwork.fileName(IMAGE_URL, 64, 64),
            RemoteArtwork.fileName(IMAGE_URL, 64, 64, emptyMap())
        )
    }

    @Test
    fun `header sets get their own cache entries`() {
        val token = mapOf("Authorization" to "MediaBrowser Token=\"abc\"")
        assertNotEquals(
            RemoteArtwork.fileName(IMAGE_URL, 64, 64),
            RemoteArtwork.fileName(IMAGE_URL, 64, 64, token)
        )
        assertNotEquals(
            RemoteArtwork.fileName(IMAGE_URL, 64, 64, token),
            RemoteArtwork.fileName(IMAGE_URL, 64, 64, mapOf("Authorization" to "MediaBrowser Token=\"def\""))
        )
    }

    @Test
    fun `header order does not change the cache key`() {
        val a = mapOf("Authorization" to "token", "X-Emby-Client" to "Sidetrack")
        val b = linkedMapOf("X-Emby-Client" to "Sidetrack", "Authorization" to "token")
        assertEquals(
            RemoteArtwork.fileName(IMAGE_URL, 64, 64, a),
            RemoteArtwork.fileName(IMAGE_URL, 64, 64, b)
        )
    }

    @Test
    fun `header values never reach the file name`() {
        val secret = "sup3r-s3cret-token"
        val name = RemoteArtwork.fileName(IMAGE_URL, 64, 64, mapOf("Authorization" to secret))
        assertEquals(true, name.matches(Regex("[0-9a-f]{64}\\.img")))
        assertEquals(false, name.contains(secret))
    }

    @Test
    fun `request headers carry the accept type and the provider headers`() {
        val connection = RecordingConnection()

        RemoteArtwork.applyRequestHeaders(
            connection,
            mapOf("Authorization" to "MediaBrowser Token=\"abc\"")
        )

        assertEquals("image/*", connection.properties["Accept"])
        assertEquals("MediaBrowser Token=\"abc\"", connection.properties["Authorization"])
    }

    @Test
    fun `an empty header map still asks for an image`() {
        val connection = RecordingConnection()

        RemoteArtwork.applyRequestHeaders(connection, emptyMap())

        assertEquals(mapOf("Accept" to "image/*"), connection.properties)
    }

    private class RecordingConnection : HttpURLConnection(URL("https://example.com/art")) {
        val properties = linkedMapOf<String, String>()

        override fun setRequestProperty(key: String, value: String) {
            properties[key] = value
        }

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun connect() = Unit
    }

    private companion object {
        const val IMAGE_URL = "https://example.com/Items/abc/Images/Primary"
    }
}
