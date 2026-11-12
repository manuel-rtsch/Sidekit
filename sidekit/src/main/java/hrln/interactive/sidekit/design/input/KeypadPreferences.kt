package hrln.interactive.sidekit.design.input

/**
 * The keypad configuration [Keypad] and [KeyMapper] read and write.
 *
 * Sidekit itself uses a `SharedPreferences` implementation. A Sidekit-native app that finds a
 * host uses a read-only host-backed implementation, so it inherits the host's module choice and
 * custom mapping without ever changing them.
 *
 * Writes have no-op defaults: a read-only store only implements the getters.
 */
interface KeypadPreferences {
    /** The module the user chose, or null when detection should decide. */
    fun module(): KeypadModule?

    /**
     * The pre-module-detection default: true means T9 was the assumed keypad.
     *
     * Used only when [module] is null, so an old install that predates module names still starts
     * on T9.
     */
    fun legacyT9(): Boolean

    /** The module a custom mapping is for, or null when automatic detection is on. */
    fun customModule(): KeypadModule?

    /** Keys bound in [module]'s custom profile, by the control they were bound to. */
    fun profiles(module: KeypadModule): Map<Int, PhysicalKey>

    /** Calls [onChange] whenever the stored configuration changes and returns a stop function. */
    fun observe(onChange: () -> Unit): () -> Unit

    fun writeModule(module: KeypadModule) {}

    fun writeCustomModule(module: KeypadModule?) {}

    fun writeProfile(module: KeypadModule, bindings: Map<Int, PhysicalKey>) {}
}
