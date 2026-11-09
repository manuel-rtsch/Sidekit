package hrln.interactive.sidekit.apps.spotify.bridge

/**
 * Testable edge of the JNI library. Calls are blocking; `NativeSpotifyGateway` moves them to IO.
 */
interface SpotifyNative {
    val isAvailable: Boolean

    fun initialize(): Boolean
    fun connect(accessToken: String): Boolean
    fun disconnect()
    fun createPlayer(): Boolean
    fun load(uri: String, positionMs: Long, autoplay: Boolean): Boolean
    fun play(): Boolean
    fun pause(): Boolean
    fun stop(): Boolean
    fun seek(positionMs: Long): Boolean
    fun setVolume(volume: Float)
    fun readAudio(timeoutMs: Long): ByteArray?
    fun audioFormat(): String?
    fun pollEvent(): String?
}
