package hrln.interactive.sidekit.apps.spotify.playback

import hrln.interactive.sidekit.apps.media.MediaSessionOwner
import hrln.interactive.sidekit.apps.media.MediaSessionRegistry
import hrln.interactive.sidekit.apps.media.MediaSessions
import hrln.interactive.sidekit.apps.spotify.data.SpotifyGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Process-scoped playback owner. The activity and screens may come and go; this object holds the
 * native player lifetime and claims the one media session while it is producing audio.
 *
 * Main-thread only, like [MediaSessionOwner]. Long-running gateway work is moved onto [scope].
 */
class SpotifyPlayback(
    private val gateway: SpotifyGateway,
    private val scope: CoroutineScope,
    private val registry: MediaSessionRegistry = MediaSessions
) : MediaSessionOwner {
    override val sessionId = "sidetracks"
    override val notificationChannelId = "sidetracks"
    override val notificationChannelName = "Sidetracks"
    override val notificationId = 0x6D76

    @Volatile
    private var playing = false

    override val isActive: Boolean get() = playing

    /** Registers once so starting local Music can pause this owner even before the first track. */
    fun register() = registry.register(this)

    suspend fun start(uri: String, positionMs: Long) {
        register()
        registry.activate(this)
        playing = true
        try {
            gateway.load(uri, positionMs, autoplay = true)
        } catch (t: Throwable) {
            playing = false
            registry.deactivate(this)
            throw t
        }
    }

    suspend fun resume() {
        registry.activate(this)
        playing = true
        gateway.play()
    }

    override fun pause() {
        playing = false
        registry.deactivate(this)
        scope.launch { runCatching { gateway.pause() } }
    }

    suspend fun seek(positionMs: Long) = gateway.seek(positionMs)

    suspend fun setVolume(volume: Float) = gateway.setVolume(volume)

    suspend fun stop() {
        playing = false
        registry.deactivate(this)
        gateway.stop()
    }

    suspend fun disconnect() {
        stop()
        gateway.disconnect()
    }
}
