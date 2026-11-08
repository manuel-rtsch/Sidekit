package hrln.interactive.sidekit.apps.spotify

import hrln.interactive.sidekit.apps.media.MediaSessionOwner
import hrln.interactive.sidekit.apps.media.MediaSessionRegistry
import hrln.interactive.sidekit.apps.spotify.data.SpotifyFailure
import hrln.interactive.sidekit.apps.spotify.data.SpotifyGateway
import hrln.interactive.sidekit.apps.spotify.data.SpotifyGatewayException
import hrln.interactive.sidekit.apps.spotify.playback.SpotifyPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyPlaybackTest {
    @Test fun `start claims the session and loads the track`() = runTest {
        val gateway = FakeGateway()
        val registry = MediaSessionRegistry()
        val playback = SpotifyPlayback(gateway, CoroutineScope(StandardTestDispatcher(testScheduler)), registry)

        playback.start("spotify:track:a", 0L)

        assertSame(playback, registry.active)
        assertTrue(playback.isActive)
        assertEquals("spotify:track:a", gateway.loadedUri)
        assertTrue(gateway.autoplay)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `starting another owner pauses Sidetracks`() = runTest {
        val gateway = FakeGateway()
        val registry = MediaSessionRegistry()
        val playback = SpotifyPlayback(gateway, CoroutineScope(StandardTestDispatcher(testScheduler)), registry)
        val other = FakeOwner()
        registry.register(other)
        playback.start("spotify:track:a", 0L)

        registry.activate(other)
        runCurrent()

        assertFalse(playback.isActive)
        assertTrue(gateway.paused)
        assertSame(other, registry.active)
    }

    @Test fun `failed load releases the session`() = runTest {
        val gateway = FakeGateway(failLoad = true)
        val registry = MediaSessionRegistry()
        val playback = SpotifyPlayback(gateway, CoroutineScope(StandardTestDispatcher(testScheduler)), registry)

        runCatching { playback.start("spotify:track:a", 0L) }

        assertFalse(playback.isActive)
        assertNull(registry.active)
    }

    @Test fun `stop deactivates and tells the gateway`() = runTest {
        val gateway = FakeGateway()
        val registry = MediaSessionRegistry()
        val playback = SpotifyPlayback(gateway, CoroutineScope(StandardTestDispatcher(testScheduler)), registry)
        playback.start("spotify:track:a", 0L)

        playback.stop()

        assertFalse(playback.isActive)
        assertNull(registry.active)
        assertTrue(gateway.stopped)
    }

    private class FakeGateway(private val failLoad: Boolean = false) : SpotifyGateway {
        var loadedUri: String? = null
        var autoplay = false
        var paused = false
        var stopped = false

        override suspend fun connect(accessToken: String) = Unit
        override suspend fun disconnect() = Unit

        override suspend fun load(uri: String, positionMs: Long, autoplay: Boolean) {
            if (failLoad) throw SpotifyGatewayException(SpotifyFailure.TrackUnavailable, "missing")
            loadedUri = uri
            this.autoplay = autoplay
        }

        override suspend fun play() = Unit
        override suspend fun pause() {
            paused = true
        }

        override suspend fun stop() {
            stopped = true
        }

        override suspend fun seek(positionMs: Long) = Unit
        override suspend fun setVolume(volume: Float) = Unit
    }

    private class FakeOwner : MediaSessionOwner {
        override val sessionId = "other"
        override val notificationChannelId = "other"
        override val notificationChannelName = "Other"
        override val notificationId = 42
        override var isActive = true
            private set

        override fun pause() {
            isActive = false
        }
    }
}
