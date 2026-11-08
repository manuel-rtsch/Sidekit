package hrln.interactive.sidekit.apps.spotify.bridge

import hrln.interactive.sidekit.system.NativeLibraries

/**
 * Guarded entry point to `libsidekit_spotify.so`. Never calls `System.loadLibrary` from an
 * initializer: a build or emulator without the ABI is a normal state, not a crash.
 *
 * The native spike lands behind these declarations. Until then [isAvailable] is false and the
 * Kotlin session can keep using the local catalog.
 */
object NativeBridge {
    const val LIBRARY = "sidekit_spotify"

    val isAvailable: Boolean get() = NativeLibraries.load(LIBRARY)

    fun initialize(): Boolean = isAvailable && runCatching { nativeInit() }.getOrDefault(false)

    fun connect(accessToken: String): Boolean = isAvailable && runCatching { nativeConnect(accessToken) }.getOrDefault(false)

    fun disconnect() {
        if (isAvailable) runCatching { nativeDisconnect() }
    }

    private external fun nativeInit(): Boolean
    private external fun nativeConnect(accessToken: String): Boolean
    private external fun nativeDisconnect()
}
