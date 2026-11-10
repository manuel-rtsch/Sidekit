package hrln.interactive.sidekit.apps.spotify.data

import hrln.interactive.sidekit.apps.spotify.bridge.SpotifyNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [SpotifyGateway] backed by librespot. Every JNI call is blocking and therefore runs on IO.
 * Native code returns only success/failure; stable failure mapping happens here.
 */
class NativeSpotifyGateway(
    private val native: SpotifyNative,
    private val settings: () -> SpotifySettings = { SpotifySettings() },
    private val accessToken: suspend () -> String?
) : SpotifyGateway {
    @Volatile
    private var connected = false

    @Volatile
    private var sessionConnected = false

    @Volatile
    private var sessionToken: String? = null

    override suspend fun connect(accessToken: String) = onIo {
        requireNative()
        if (connected) return@onIo
        connectSession(accessToken)
        createPlayer()
    }

    override suspend fun disconnect() = onIo {
        connected = false
        sessionConnected = false
        sessionToken = null
        if (native.isAvailable) native.disconnect()
    }

    override suspend fun load(uri: String, positionMs: Long, autoplay: Boolean) = onIo {
        requireNative()
        if (!native.load(uri, positionMs, autoplay)) fail(SpotifyFailure.TrackUnavailable, "Track unavailable")
    }

    override suspend fun play() = onIo {
        requireNative()
        if (!native.play()) fail(SpotifyFailure.NotConnected, "Playback is not ready")
    }

    override suspend fun pause() = onIo {
        requireNative()
        if (!native.pause()) fail(SpotifyFailure.NotConnected, "Playback is not ready")
    }

    override suspend fun stop() = onIo {
        requireNative()
        if (!native.stop()) fail(SpotifyFailure.NotConnected, "Playback is not ready")
    }

    override suspend fun seek(positionMs: Long) = onIo {
        requireNative()
        if (!native.seek(positionMs)) fail(SpotifyFailure.NotConnected, "Playback is not ready")
    }

    /** Forgets the native session after it has been torn down outside this gateway. */
    fun reset() {
        connected = false
        sessionConnected = false
        sessionToken = null
    }

    /**
     * Connects with the current stored token, refreshing it first when needed.
     *
     * `setAccessToken` is called on every attempt, not only when the token changed: librespot can
     * lose its access-point connection while the OAuth token is still valid, and a session that
     * has gone invalid must be rebuilt or playback fails for every track. The native call is a
     * cheap state update, so the extra round trip on each play is worth the liveness check.
     */
    suspend fun ensureConnected(): Boolean {
        val token = accessToken() ?: fail(SpotifyFailure.AuthenticationExpired, "Sign in again")
        if (!connected) {
            connect(token)
            return true
        }
        if (withContext(Dispatchers.IO) { native.setAccessToken(token) }) {
            sessionToken = token
            return false
        }
        connected = false
        sessionConnected = false
        sessionToken = null
        connect(token)
        return true
    }

    /** Connects the librespot session without creating an audio player. Used by catalog reads. */
    suspend fun ensureSession() = onIo {
        requireNative()
        val token = accessToken() ?: fail(SpotifyFailure.AuthenticationExpired, "Sign in again")
        if (!sessionConnected) {
            connectSession(token)
        } else if (!native.setAccessToken(token)) {
            // The native session is gone; reconnect from scratch rather than keep a stale token.
            sessionConnected = false
            sessionToken = null
            connectSession(token)
        } else {
            sessionToken = token
        }
    }

    private fun connectSession(token: String) {
        if (sessionConnected) return
        if (!native.connect(token)) fail(SpotifyFailure.AuthenticationExpired, "Could not connect")
        sessionConnected = true
        sessionToken = token
    }

    private fun createPlayer() {
        val current = settings()
        if (!native.createPlayer(current.bitrateKbps)) {
            native.disconnect()
            sessionConnected = false
            sessionToken = null
            fail(SpotifyFailure.Unknown, "Could not create the player")
        }
        connected = true
    }

    private fun requireNative() {
        if (!native.isAvailable) fail(SpotifyFailure.Unavailable, "Spotify playback is unavailable in this build")
    }

    private suspend fun onIo(block: suspend () -> Unit) {
        withContext(Dispatchers.IO) { block() }
    }

    private fun fail(failure: SpotifyFailure, message: String): Nothing = throw SpotifyGatewayException(failure, message)
}
