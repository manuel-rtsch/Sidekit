package hrln.interactive.sidekit.apps.spotify.data

import android.content.Context
import hrln.interactive.sidekit.apps.spotify.AudioQuality
import hrln.interactive.sidekit.apps.spotify.SpotifyMediaItem
import hrln.interactive.sidekit.apps.spotify.SpotifyTrack
import hrln.interactive.sidekit.apps.spotify.auth.EncryptedSpotifyCredentialStore
import hrln.interactive.sidekit.apps.spotify.auth.SpotifyAuth
import hrln.interactive.sidekit.apps.spotify.bridge.NativeBridge
import hrln.interactive.sidekit.apps.spotify.playback.SpotifyAudioSink
import hrln.interactive.sidekit.apps.spotify.playback.SpotifyPlayback
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Process-wide Spotify identity. It owns the credential store and token refresh, not the UI; the
 * ViewModel observes [signedIn] and asks for an access token when a real gateway is connected.
 */
class SpotifySession(
    private val auth: SpotifyAuth = SpotifyAuth(NoopCredentialStore),
    private val tmpDir: String? = null,
    context: Context? = null
) {
    private val _signedIn = MutableStateFlow(auth.hasCredentials())
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val appContext = context?.applicationContext
    private val settingsStore = appContext?.let { SpotifySettingsStore(it) }

    // Legacy file names kept so existing dev installs keep their queue and history.
    private val queueStore = appContext?.let { FileSpotifyQueueStore(File(it.filesDir, "sidetracks-queue.bin")) }
    private val historyStore = appContext?.let { FileSpotifyHistoryStore(File(it.filesDir, "sidetracks-history.bin")) }
    private val _settings = MutableStateFlow(settingsStore?.read() ?: SpotifySettings())
    val settings: StateFlow<SpotifySettings> = _settings.asStateFlow()

    val nativeAvailable: Boolean get() = NativeBridge.isAvailable

    private val nativeGateway by lazy {
        NativeSpotifyGateway(NativeBridge, { _settings.value }, { accessToken() })
    }
    val gateway: SpotifyGateway get() = nativeGateway

    private val webApi by lazy { SpotifyWebApi { accessToken() } }
    val catalog: SpotifyCatalog by lazy {
        if (nativeAvailable) NativeSpotifyCatalog(NativeBridge) { nativeGateway.ensureSession() } else SpotifyWebCatalog(webApi)
    }
    val writer: SpotifyLibraryWriter? get() = catalog as? SpotifyLibraryWriter

    /** Process-wide queue and history so leaving Sideplay does not forget what is playing. */
    val queue = SpotifyQueueRepository(
        load = { queueStore?.read() ?: SpotifyQueueRepository.Snapshot() },
        save = { queueStore?.write(it) }
    )
    val history = SpotifyHistoryRepository(
        load = { historyStore?.read().orEmpty() },
        save = { historyStore?.write(it) }
    )

    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val playback: SpotifyPlayback by lazy {
        SpotifyPlayback(
            gateway = nativeGateway,
            scope = playbackScope,
            audio = SpotifyAudioSink(NativeBridge, playbackScope),
            context = appContext
        ).also { player ->
            // Adopt transport actions when no ViewModel is attached. The ViewModel installs its
            // own callbacks while it exists and clears them in onCleared().
            player.fallbackNext = { skipNext() }
            player.fallbackPrevious = { skipPrevious() }
            player.onStopped = { queue.stop() }
        }
    }

    init {
        if (nativeAvailable) {
            tmpDir?.let(NativeBridge::setTmpDir)
            NativeBridge.initialize()
        }
    }

    suspend fun signIn(openBrowser: suspend (String) -> Unit) {
        auth.signIn(openBrowser)
        _signedIn.value = true
    }

    fun signOut() {
        queue.stop()
        history.clear()
        if (nativeAvailable) {
            playback.setNowPlaying(null)
            playback.pause()
            // Stop the decoder immediately; waiting for the coroutine below leaves a window in
            // which the current track keeps playing after the credentials have been cleared.
            NativeBridge.stop()
            playbackScope.launch { runCatching { playback.disconnect() } }
        } else {
            nativeGateway.reset()
            NativeBridge.disconnect()
        }
        auth.clear()
        _signedIn.value = false
    }

    /** Connects librespot with the current access token, creating the one player on first use. */
    suspend fun ensurePlaybackConnected() = nativeGateway.ensureConnected()

    suspend fun play(uri: String, positionMs: Long = 0L) {
        ensurePlaybackConnected()
        withContext(Dispatchers.Main.immediate) { playback.start(uri, positionMs) }
    }

    suspend fun resume() {
        ensurePlaybackConnected()
        withContext(Dispatchers.Main.immediate) { playback.resume() }
    }

    suspend fun seek(positionMs: Long) = playback.seek(positionMs)

    fun pause() = playback.pause()

    suspend fun accessToken(): String? = auth.accessToken()

    fun setAudioQuality(quality: AudioQuality) {
        _settings.update { it.copy(audioQuality = quality) }
        settingsStore?.write(_settings.value)
    }

    fun togglePlayback() {
        if (playback.isActive) {
            playback.pause()
        } else {
            playbackScope.launch { runCatching { resume() } }
        }
    }

    fun stopPlayback() {
        queue.stop()
        playbackScope.launch { runCatching { playback.stop() } }
    }

    /**
     * Advances the queue when the notification/lock screen has no ViewModel to call back into.
     * The ViewModel follows the same queue semantics, so the UI adopts this state on return.
     */
    fun skipNext() {
        val before = queue.snapshot.current ?: return
        val snapshot = queue.next(auto = false)
        if (snapshot.current !== before) snapshot.current?.let(::startFromTransport)
    }

    /** Restarts the current item past the 3 s threshold, otherwise returns to queue history. */
    fun skipPrevious() {
        val current = queue.snapshot.current ?: return
        if (playback.position > 3_000L) {
            playbackScope.launch { runCatching { seek(0L) } }
            return
        }
        val snapshot = queue.previous(playback.position)
        if (snapshot.current !== current) {
            snapshot.current?.let(::startFromTransport)
        } else {
            playbackScope.launch { runCatching { seek(0L) } }
        }
    }

    private fun startFromTransport(item: SpotifyMediaItem) {
        if (item is SpotifyTrack) history.record(item, System.currentTimeMillis())
        playback.setNowPlaying(item, 0L)
        val uri = item.uri ?: return
        playbackScope.launch {
            runCatching { play(uri, 0L) }.onFailure {
                playback.pause()
                playback.updateState(false, 0L)
            }
        }
    }

    /** One librespot player event, or null when nothing has happened since the last poll. */
    fun pollPlaybackEvent(): String? = if (nativeAvailable) NativeBridge.pollEvent() else null

    private object NoopCredentialStore : hrln.interactive.sidekit.apps.spotify.auth.SpotifyCredentialStore {
        override fun read(): hrln.interactive.sidekit.apps.spotify.auth.SpotifyCredentials? = null
        override fun save(credentials: hrln.interactive.sidekit.apps.spotify.auth.SpotifyCredentials) = Unit
        override fun clear() = Unit
    }
}

/** Lazily creates the one session for the process, matching how Sidekit owns local playback. */
object SpotifySessions {
    @Volatile
    private var instance: SpotifySession? = null

    fun get(context: Context): SpotifySession = instance ?: synchronized(this) {
        instance ?: SpotifySession(
            SpotifyAuth(EncryptedSpotifyCredentialStore(context.applicationContext)),
            context.applicationContext.cacheDir.absolutePath,
            context.applicationContext
        ).also { instance = it }
    }
}
