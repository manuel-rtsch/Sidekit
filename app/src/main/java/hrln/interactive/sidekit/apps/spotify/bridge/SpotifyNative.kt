package hrln.interactive.sidekit.apps.spotify.bridge

/**
 * Testable edge of the JNI library. Calls are blocking; `NativeSpotifyGateway` moves them to IO.
 */
interface SpotifyNative {
    val isAvailable: Boolean

    fun initialize(): Boolean
    fun setTmpDir(path: String)
    fun connect(accessToken: String): Boolean
    fun setAccessToken(accessToken: String): Boolean
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

    fun catalogLibrary(filter: String): String?
    fun catalogCollection(kind: String, id: String): String?
    fun catalogArtist(id: String): String?
    fun catalogAlbumsBy(id: String): String?
    fun catalogTopTracks(id: String): String?
    fun catalogSearch(query: String, scopes: String, limit: Long): String?
}
