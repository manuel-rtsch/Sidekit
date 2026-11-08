package hrln.interactive.sidekit.apps.spotify.data

import android.content.Context
import hrln.interactive.sidekit.apps.spotify.auth.EncryptedSpotifyCredentialStore
import hrln.interactive.sidekit.apps.spotify.auth.SpotifyAuth
import hrln.interactive.sidekit.apps.spotify.bridge.NativeBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide Spotify identity. It owns the credential store and token refresh, not the UI; the
 * ViewModel observes [signedIn] and asks for an access token when a real gateway is connected.
 */
class SpotifySession(
    private val auth: SpotifyAuth = SpotifyAuth(NoopCredentialStore)
) {
    private val _signedIn = MutableStateFlow(auth.hasCredentials())
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    val nativeAvailable: Boolean get() = NativeBridge.isAvailable

    private val nativeGateway by lazy {
        NativeSpotifyGateway(NativeBridge) { accessToken() }
    }
    val gateway: SpotifyGateway get() = nativeGateway

    init {
        if (nativeAvailable) NativeBridge.initialize()
    }

    suspend fun signIn(openBrowser: suspend (String) -> Unit) {
        auth.signIn(openBrowser)
        _signedIn.value = true
    }

    fun signOut() {
        NativeBridge.disconnect()
        auth.clear()
        _signedIn.value = false
    }

    /** Connects librespot with the current access token, creating the one player on first use. */
    suspend fun ensurePlaybackConnected() = nativeGateway.ensureConnected()

    suspend fun accessToken(): String? = auth.accessToken()

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
            SpotifyAuth(EncryptedSpotifyCredentialStore(context.applicationContext))
        ).also { instance = it }
    }
}
