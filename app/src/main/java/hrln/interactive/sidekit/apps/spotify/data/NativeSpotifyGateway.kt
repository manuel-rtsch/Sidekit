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
    private val accessToken: suspend () -> String?
) : SpotifyGateway {
    override suspend fun connect(accessToken: String) = onIo {
        requireNative()
        if (!native.connect(accessToken)) fail(SpotifyFailure.AuthenticationExpired, "Could not connect")
        if (!native.createPlayer()) fail(SpotifyFailure.Unknown, "Could not create the player")
    }

    override suspend fun disconnect() = onIo {
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

    override suspend fun setVolume(volume: Float) = onIo {
        requireNative()
        native.setVolume(volume)
    }

    /** Connects with the current stored token, refreshing it first when needed. */
    suspend fun ensureConnected() {
        val token = accessToken() ?: fail(SpotifyFailure.AuthenticationExpired, "Sign in again")
        connect(token)
    }

    private fun requireNative() {
        if (!native.isAvailable) fail(SpotifyFailure.Unavailable, "Spotify playback is unavailable in this build")
    }

    private suspend fun onIo(block: suspend () -> Unit) {
        withContext(Dispatchers.IO) { block() }
    }

    private fun fail(failure: SpotifyFailure, message: String): Nothing = throw SpotifyGatewayException(failure, message)
}
