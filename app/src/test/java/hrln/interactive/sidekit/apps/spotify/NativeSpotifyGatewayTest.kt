package hrln.interactive.sidekit.apps.spotify

import hrln.interactive.sidekit.apps.spotify.bridge.SpotifyNative
import hrln.interactive.sidekit.apps.spotify.data.NativeSpotifyGateway
import hrln.interactive.sidekit.apps.spotify.data.SpotifyFailure
import hrln.interactive.sidekit.apps.spotify.data.SpotifyGatewayException
import hrln.interactive.sidekit.apps.spotify.data.SpotifySettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSpotifyGatewayTest {
    @Test fun `unavailable library maps to Unavailable`() = runTest {
        val gateway = NativeSpotifyGateway(FakeNative(isAvailable = false)) { "token" }

        val failure = runCatching { gateway.connect("token") }.exceptionOrNull()

        assertTrue(failure is SpotifyGatewayException)
        assertEquals(SpotifyFailure.Unavailable, (failure as SpotifyGatewayException).failure)
    }

    @Test fun `connect creates the player`() = runTest {
        val native = FakeNative()
        val gateway = NativeSpotifyGateway(native) { "token" }

        gateway.connect("token")

        assertEquals("token", native.token)
        assertTrue(native.playerCreated)
    }

    @Test fun `failed load maps to TrackUnavailable`() = runTest {
        val native = FakeNative().apply { loadOk = false }
        val gateway = NativeSpotifyGateway(native) { "token" }

        val failure = runCatching { gateway.load("spotify:track:a", 0, true) }.exceptionOrNull()

        assertEquals(SpotifyFailure.TrackUnavailable, (failure as SpotifyGatewayException).failure)
    }

    @Test fun `ensureConnected uses the stored token`() = runTest {
        val native = FakeNative()
        val gateway = NativeSpotifyGateway(native) { "stored" }

        gateway.ensureConnected()

        assertEquals("stored", native.token)
    }

    @Test fun `player receives persisted bitrate`() = runTest {
        val native = FakeNative()
        val gateway = NativeSpotifyGateway(
            native,
            { SpotifySettings(audioQuality = hrln.interactive.sidekit.apps.spotify.AudioQuality.Normal) }
        ) { "token" }

        gateway.connect("token")

        assertEquals(96, native.bitrateKbps)
    }

    @Test fun `rotated token is pushed into the live session`() = runTest {
        val native = FakeNative()
        var token = "first"
        val gateway = NativeSpotifyGateway(native) { token }
        gateway.connect(token)
        token = "second"

        gateway.ensureConnected()

        assertEquals("second", native.token)
        assertEquals(1, native.connects)
    }

    @Test fun `dead native session reconnects from scratch`() = runTest {
        val native = FakeNative().apply { setTokenOk = false }
        var token = "first"
        val gateway = NativeSpotifyGateway(native) { token }
        gateway.connect(token)
        token = "second"

        gateway.ensureConnected()

        assertEquals("second", native.token)
        assertEquals(2, native.connects)
    }

    private class FakeNative(override val isAvailable: Boolean = true) : SpotifyNative {
        var token: String? = null
        var playerCreated = false
        var loadOk = true
        var setTokenOk = true
        var bitrateKbps: Int? = null
        var connects = 0

        override fun initialize() = isAvailable
        override fun setTmpDir(path: String) = Unit
        override fun connect(accessToken: String): Boolean {
            connects++
            token = accessToken
            return isAvailable
        }
        override fun setAccessToken(accessToken: String): Boolean {
            if (setTokenOk) token = accessToken
            return setTokenOk && isAvailable
        }
        override fun disconnect() = Unit
        override fun createPlayer(bitrateKbps: Int): Boolean {
            playerCreated = true
            this.bitrateKbps = bitrateKbps
            return isAvailable
        }
        override fun load(uri: String, positionMs: Long, autoplay: Boolean) = loadOk
        override fun play() = isAvailable
        override fun pause() = isAvailable
        override fun stop() = isAvailable
        override fun seek(positionMs: Long) = isAvailable
        override fun readAudio(timeoutMs: Long): ByteArray? = null
        override fun audioFormat(): String? = null
        override fun pollEvent(): String? = null
        override fun catalogLibrary(filter: String, offset: Long, limit: Long): String? = null
        override fun catalogCollection(kind: String, id: String, offset: Long, limit: Long): String? = null
        override fun catalogArtist(id: String): String? = null
        override fun catalogAlbumsBy(id: String, offset: Long, limit: Long): String? = null
        override fun catalogTopTracks(id: String, offset: Long, limit: Long): String? = null
        override fun catalogSearch(query: String, scopes: String, offset: Long, limit: Long): String? = null
        override fun collectionContains(set: String, uris: String): String? = null
        override fun collectionWrite(set: String, uris: String, removed: Boolean): Boolean = true
        override fun playlistSaved(id: String): String? = null
        override fun playlistSave(id: String, save: Boolean): Boolean = true
    }
}
