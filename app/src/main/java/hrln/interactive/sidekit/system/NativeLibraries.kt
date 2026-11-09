package hrln.interactive.sidekit.system

/**
 * Loads the app's native libraries without taking the process down when one is missing.
 *
 * A build only carries the ABIs it was compiled for, so `System.loadLibrary` can legitimately
 * fail on an emulator or a split APK. Callers ask once and show a "not available in this build"
 * state instead of crashing from an object initialiser.
 *
 * Results are cached, including failures: a library that is not packaged will not appear later.
 * Thread-safe; loading is idempotent per name.
 */
object NativeLibraries {
    private val loaded = HashSet<String>()
    private val failed = HashSet<String>()

    /** True if [name] (without the `lib` prefix and `.so` suffix) was loaded. */
    @Synchronized
    fun load(name: String): Boolean {
        if (name in loaded) return true
        if (name in failed) return false
        return try {
            System.loadLibrary(name)
            loaded += name
            true
        } catch (_: UnsatisfiedLinkError) {
            failed += name
            false
        }
    }

    @Synchronized
    fun isAvailable(name: String): Boolean = name in loaded
}
