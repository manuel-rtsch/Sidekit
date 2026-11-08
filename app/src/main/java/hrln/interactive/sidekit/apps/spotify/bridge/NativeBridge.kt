package hrln.interactive.sidekit.apps.spotify.bridge

import hrln.interactive.sidekit.system.NativeLibraries

/**
 * Guarded entry point to `libsidekit_spotify.so`. Never calls `System.loadLibrary` from an
 * initializer: a build or emulator without the ABI is a normal state, not a crash.
 */
object NativeBridge : SpotifyNative {
    const val LIBRARY = "sidekit_spotify"

    override val isAvailable: Boolean get() = NativeLibraries.load(LIBRARY)

    override fun initialize(): Boolean = isAvailable && runCatching { nativeInit() }.getOrDefault(false)

    override fun connect(accessToken: String): Boolean = isAvailable && runCatching { nativeConnect(accessToken) }.getOrDefault(false)

    override fun disconnect() {
        if (isAvailable) runCatching { nativeDisconnect() }
    }

    override fun createPlayer(): Boolean = isAvailable && runCatching { nativeCreatePlayer() }.getOrDefault(false)

    override fun load(uri: String, positionMs: Long, autoplay: Boolean): Boolean = isAvailable && runCatching { nativeLoad(uri, positionMs, autoplay) }.getOrDefault(false)

    override fun play(): Boolean = isAvailable && runCatching { nativePlay() }.getOrDefault(false)

    override fun pause(): Boolean = isAvailable && runCatching { nativePause() }.getOrDefault(false)

    override fun stop(): Boolean = isAvailable && runCatching { nativeStop() }.getOrDefault(false)

    override fun seek(positionMs: Long): Boolean = isAvailable && runCatching { nativeSeek(positionMs) }.getOrDefault(false)

    override fun setVolume(volume: Float) {
        if (isAvailable) runCatching { nativeSetVolume(volume) }
    }

    override fun readAudio(timeoutMs: Long): ByteArray? = if (isAvailable) runCatching { nativeReadAudio(timeoutMs) }.getOrNull() else null

    override fun audioFormat(): String? = if (isAvailable) runCatching { nativeAudioFormat() }.getOrNull() else null

    override fun pollEvent(): String? = if (isAvailable) runCatching { nativePollEvent() }.getOrNull() else null

    private external fun nativeInit(): Boolean
    private external fun nativeConnect(accessToken: String): Boolean
    private external fun nativeDisconnect()
    private external fun nativeCreatePlayer(): Boolean
    private external fun nativeLoad(uri: String, positionMs: Long, autoplay: Boolean): Boolean
    private external fun nativePlay(): Boolean
    private external fun nativePause(): Boolean
    private external fun nativeStop(): Boolean
    private external fun nativeSeek(positionMs: Long): Boolean
    private external fun nativeSetVolume(volume: Float)
    private external fun nativeReadAudio(timeoutMs: Long): ByteArray?
    private external fun nativeAudioFormat(): String?
    private external fun nativePollEvent(): String?
}
