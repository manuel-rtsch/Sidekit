package hrln.interactive.sidekit.apps.spotify.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState as SessionState
import android.os.SystemClock
import hrln.interactive.sidekit.apps.media.AudioFocus
import hrln.interactive.sidekit.apps.media.MediaSessionOwner
import hrln.interactive.sidekit.apps.media.MediaSessionRegistry
import hrln.interactive.sidekit.apps.media.MediaSessions
import hrln.interactive.sidekit.apps.media.RemoteArtwork
import hrln.interactive.sidekit.apps.spotify.SpotifyMediaItem
import hrln.interactive.sidekit.apps.spotify.data.SpotifyGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Process-scoped playback owner. The activity and screens may come and go; this object holds the
 * native player lifetime, the platform media session and the foreground notification.
 *
 * Main-thread only, like [MediaSessionOwner]. Long-running gateway work is moved onto [scope].
 */
class SpotifyPlayback(
    private val gateway: SpotifyGateway,
    private val scope: CoroutineScope,
    private val registry: MediaSessionRegistry = MediaSessions,
    private val audio: SpotifyAudioSink? = null,
    private val context: Context? = null
) : MediaSessionOwner {
    override val sessionId = "sidetracks"
    override val notificationChannelId = "sidetracks"
    override val notificationChannelName = "Sidetracks playback"
    override val notificationId = 0x6D76

    @Volatile
    private var playing = false

    @Volatile
    private var positionMs = 0L

    @Volatile
    var current: SpotifyMediaItem? = null
        private set

    val position: Long get() = positionMs

    @Volatile
    var notificationArt: Bitmap? = null
        private set

    override val isActive: Boolean get() = playing

    /**
     * Queue ops normally live in the ViewModel so its screen state stays in sync. When the
     * ViewModel is gone, [fallbackNext]/[fallbackPrevious] keep the notification and lock-screen
     * transport working by advancing the process-wide queue instead.
     */
    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
    var onSeek: ((Long) -> Unit)? = null
    var fallbackNext: (() -> Unit)? = null
    var fallbackPrevious: (() -> Unit)? = null

    /** Lets the process owner clear queue state whenever playback is stopped from any control. */
    var onStopped: (() -> Unit)? = null

    private var mediaSession: MediaSession? = null
    private var artJob: Job? = null
    private var lastSessionUpdate = 0L
    private var noisyRegistered = false

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onAudioBecomingNoisy()
    }

    /** Headphones unplugged: pause rather than play out loud. */
    fun onAudioBecomingNoisy() = pause()

    private val focus = context?.let {
        AudioFocus(
            it,
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            onPause = { pauseInternal(abandonFocus = false) },
            onResume = { scope.launch { runCatching { resume() } } }
        )
    }

    val sessionToken: MediaSession.Token? get() = session()?.sessionToken

    /** Registers once so starting local Music can pause this owner even before the first track. */
    fun register() = registry.register(this)

    fun setNowPlaying(item: SpotifyMediaItem?, position: Long = 0L) {
        current = item
        positionMs = position
        artJob?.cancel()
        notificationArt = null
        if (context != null) updateMetadata()
        publish()
        if (item?.artUrl != null && context != null) {
            artJob = scope.launch {
                val bitmap = RemoteArtwork.load(context, item.artUrl!!, ART_PX, ART_PX)
                if (current?.id == item.id) {
                    notificationArt = bitmap
                    lastSessionUpdate = 0L
                    updateMetadata()
                    publish()
                }
            }
        }
    }

    /** Mirrors native player events into the media session and notification. */
    fun updateState(isPlaying: Boolean, position: Long) {
        playing = isPlaying
        positionMs = position
        publish()
    }

    suspend fun start(uri: String, positionMs: Long) {
        register()
        if (focus?.request() == false) return
        registry.activate(this)
        playing = true
        this.positionMs = positionMs
        // Release the previous AudioTrack before loading the next item. Reusing a live sink can
        // leave the old track's buffered PCM playing underneath the new one.
        audio?.pause()
        publish()
        try {
            gateway.load(uri, positionMs, autoplay = true)
            audio?.start()
        } catch (t: Throwable) {
            playing = false
            audio?.pause()
            focus?.abandon()
            registry.deactivate(this)
            publish()
            throw t
        }
    }

    suspend fun resume() {
        register()
        if (focus?.request() == false) return
        registry.activate(this)
        playing = true
        audio?.start()
        publish()
        gateway.play()
    }

    fun togglePlayPause() {
        if (playing) pause() else scope.launch { runCatching { resume() } }
    }

    override fun pause() = pauseInternal(abandonFocus = true)

    /**
     * Transient audio-focus loss keeps the focus request held so [AudioFocus] can resume us when
     * the call or notification ends. An explicit pause gives it back.
     */
    private fun pauseInternal(abandonFocus: Boolean) {
        playing = false
        audio?.pause()
        if (abandonFocus) focus?.abandon()
        registry.deactivate(this)
        publish()
        scope.launch { runCatching { gateway.pause() } }
    }

    suspend fun seek(positionMs: Long) {
        this.positionMs = positionMs
        publish()
        gateway.seek(positionMs)
    }

    fun requestSeek(positionMs: Long) {
        val callback = onSeek
        if (callback != null) callback(positionMs) else scope.launch { runCatching { seek(positionMs) } }
    }

    fun requestNext() = (onNext ?: fallbackNext)?.invoke()

    fun requestPrevious() = (onPrevious ?: fallbackPrevious)?.invoke()

    suspend fun stop() {
        playing = false
        audio?.pause()
        focus?.abandon()
        registry.deactivate(this)
        runCatching { gateway.stop() }
        mediaSession?.let {
            it.setPlaybackState(
                SessionState.Builder()
                    .setState(SessionState.STATE_STOPPED, 0L, 0f, SystemClock.elapsedRealtime())
                    .build()
            )
        }
        current = null
        notificationArt = null
        artJob?.cancel()
        onStopped?.invoke()
        publish()
    }

    suspend fun disconnect() {
        stop()
        gateway.disconnect()
        mediaSession?.release()
        mediaSession = null
    }

    private fun publish() {
        session()?.let { session ->
            val now = SystemClock.elapsedRealtime()
            if (current != null) updateMetadata(session, now)
            session.setPlaybackState(
                SessionState.Builder()
                    .setActions(
                        SessionState.ACTION_PLAY or SessionState.ACTION_PAUSE or
                            SessionState.ACTION_PLAY_PAUSE or SessionState.ACTION_SKIP_TO_NEXT or
                            SessionState.ACTION_SKIP_TO_PREVIOUS or SessionState.ACTION_SEEK_TO or
                            SessionState.ACTION_STOP
                    )
                    .setState(
                        when {
                            current == null -> SessionState.STATE_STOPPED
                            playing -> SessionState.STATE_PLAYING
                            else -> SessionState.STATE_PAUSED
                        },
                        positionMs,
                        if (playing) 1f else 0f,
                        now
                    )
                    .build()
            )
        }
        updateNoisy()
        context?.let { SpotifyPlaybackService.sync(it, this) }
    }

    private fun updateNoisy() {
        val context = context ?: return
        if (playing == noisyRegistered) return
        noisyRegistered = playing
        if (playing) {
            context.registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        } else {
            runCatching { context.unregisterReceiver(noisy) }
        }
    }

    private fun updateMetadata(session: MediaSession? = session(), now: Long = SystemClock.elapsedRealtime()) {
        val item = current ?: return
        session ?: return
        if (lastSessionUpdate != 0L && now - lastSessionUpdate < 250L) return
        lastSessionUpdate = now
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, item.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, item.creator)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, item.creator)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, item.durationMs)
                .apply { notificationArt?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build()
        )
    }

    private fun session(): MediaSession? {
        val context = context ?: return null
        return mediaSession ?: MediaSession(context, "SideKit Sidetracks").also { session ->
            session.setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() {
                        scope.launch { runCatching { resume() } }
                    }

                    override fun onPause() {
                        pause()
                    }

                    override fun onSkipToNext() {
                        requestNext()
                    }

                    override fun onSkipToPrevious() {
                        requestPrevious()
                    }

                    override fun onSeekTo(pos: Long) {
                        requestSeek(pos)
                    }

                    override fun onStop() {
                        scope.launch { runCatching { stop() } }
                    }
                }
            )
            session.isActive = true
            mediaSession = session
        }
    }

    private companion object {
        const val ART_PX = 256
    }
}
