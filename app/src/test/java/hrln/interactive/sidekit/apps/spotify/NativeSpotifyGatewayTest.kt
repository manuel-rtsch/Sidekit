package hrln.interactive.sidekit.apps.spotify

import hrln.interactive.sidekit.apps.spotify.bridge.SpotifyNative
import hrln.interactive.sidekit.apps.spotify.data.NativeSpotifyGateway
import hrln.interactive.sidekit.apps.spotify.data.SpotifyFailure
import hrln.interactive.sidekit.apps.spotify.data.SpotifyGatewayException
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

    private class FakeNative(override val isAvailable: Boolean = true) : SpotifyNative {
        var token: String? = null
        var playerCreated = false
        var loadOk = true

        override fun initialize() = isAvailable
        override fun connect(accessToken: String): Boolean {
            token = accessToken
            return isAvailable
        }
        override fun disconnect() = Unit
        override fun createPlayer(): Boolean {
            playerCreated = true
            return isAvailable
        }
        override fun load(uri: String, positionMs: Long, autoplay: Boolean) = loadOk
        override fun play() = isAvailable
        override fun pause() = isAvailable
        override fun stop() = isAvailable
        override fun seek(positionMs: Long) = isAvailable
        override fun setVolume(volume: Float) = Unit
        override fun readAudio(timeoutMs: Long): ByteArray? = null
        override fun audioFormat(): String? = null
        override fun pollEvent(): String? = null
    }
}
