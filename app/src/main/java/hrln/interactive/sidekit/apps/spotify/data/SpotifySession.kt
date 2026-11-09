package hrln.interactive.sidekit.apps.spotify.data

import android.content.Context
import hrln.interactive.sidekit.apps.spotify.auth.EncryptedSpotifyCredentialStore
import hrln.interactive.sidekit.apps.spotify.auth.SpotifyAuth
import hrln.interactive.sidekit.apps.spotify.bridge.NativeBridge
import hrln.interactive.sidekit.apps.spotify.playback.SpotifyAudioSink
import hrln.interactive.sidekit.apps.spotify.playback.SpotifyPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Process-wide Spotify identity. It owns the credential store and token refresh, not the UI; the
 * ViewModel observes [signedIn] and asks for an access token when a real gateway is connected.
 */
class SpotifySession(
    private val auth: SpotifyAuth = SpotifyAuth(NoopCredentialStore),
    private val tmpDir: String? = null
) {
    private val _signedIn = MutableStateFlow(auth.hasCredentials())
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    val nativeAvailable: Boolean get() = NativeBridge.isAvailable

    private val nativeGateway by lazy {
        NativeSpotifyGateway(NativeBridge) { accessToken() }
    }
    val gateway: SpotifyGateway get() = nativeGateway

    private val webApi by lazy { SpotifyWebApi { accessToken() } }
    val catalog: SpotifyCatalog by lazy {
        if (nativeAvailable) NativeSpotifyCatalog(NativeBridge) { nativeGateway.ensureSession() } else SpotifyWebCatalog(webApi)
    }

    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val playback: SpotifyPlayback by lazy {
        SpotifyPlayback(
            gateway = nativeGateway,
            scope = playbackScope,
            audio = SpotifyAudioSink(NativeBridge, playbackScope)
        )
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
        nativeGateway.reset()
        NativeBridge.disconnect()
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

    suspend fun setVolume(volume: Float) = playback.setVolume(volume)

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
            context.applicationContext.cacheDir.absolutePath
        ).also { instance = it }
    }
}
