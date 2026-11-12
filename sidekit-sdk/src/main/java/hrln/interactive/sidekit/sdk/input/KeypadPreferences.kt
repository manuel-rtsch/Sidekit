package hrln.interactive.sidekit.sdk.input

import android.content.Context
import android.content.SharedPreferences
import hrln.interactive.sidekit.design.input.KeypadModule
import hrln.interactive.sidekit.design.input.KeypadPreferences
import hrln.interactive.sidekit.design.input.PhysicalKey
import hrln.interactive.sidekit.sdk.SidekitContract
import hrln.interactive.sidekit.sdk.config.CachingSidekitConfigSource
import hrln.interactive.sidekit.sdk.config.SidekitConfig
import hrln.interactive.sidekit.sdk.config.SidekitConfigSource
import hrln.interactive.sidekit.sdk.config.SidekitPreferenceFiles

/**
 * The local keypad configuration, stored in the same `sidekit_keypad` file Sidekit has always used.
 *
 * Sidekit and a standalone Sidekit app both use this. A Sidekit-native app passes its own
 * [fileName] if it keeps a separate copy.
 */
class SharedPreferencesKeypadPreferences(
    context: Context,
    fileName: String = SidekitPreferenceFiles.KEYPAD
) : KeypadPreferences {
    private val prefs = context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    // SharedPreferences keeps listeners weakly; hold the strong reference until observe's stop
    // function runs, or a mapper's listener can be collected while it is still needed.
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun module(): KeypadModule? = prefs.getString(KEY_MODULE, null)?.let { moduleNamed(it) }

    override fun legacyT9(): Boolean = prefs.getBoolean(KEY_LEGACY_T9, true)

    override fun customModule(): KeypadModule? = prefs.getString(KEY_CUSTOM_MODULE, null)?.let { moduleNamed(it) }

    override fun profiles(module: KeypadModule): Map<Int, PhysicalKey> = prefs.getStringSet(KEY_PROFILE + module.name, emptySet()).orEmpty().mapNotNull { decode(it) }.toMap()

    override fun observe(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == KEY_MODULE || key == KEY_LEGACY_T9 || key == KEY_CUSTOM_MODULE || key.startsWith(KEY_PROFILE)) {
                onChange()
            }
        }
        listeners += listener
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            listeners -= listener
        }
    }

    override fun writeModule(module: KeypadModule) {
        prefs.edit().putString(KEY_MODULE, module.name).apply()
    }

    override fun writeCustomModule(module: KeypadModule?) {
        prefs.edit().apply {
            if (module == null) remove(KEY_CUSTOM_MODULE) else putString(KEY_CUSTOM_MODULE, module.name)
        }.apply()
    }

    override fun writeProfile(module: KeypadModule, bindings: Map<Int, PhysicalKey>) {
        val entries = bindings.map { (control, key) -> encode(control, key) }.toSet()
        prefs.edit().putStringSet(KEY_PROFILE + module.name, entries).apply()
    }

    private companion object {
        const val KEY_LEGACY_T9 = "t9"
        const val KEY_MODULE = "module"
        const val KEY_CUSTOM_MODULE = "custom_module"
        const val KEY_PROFILE = "profile."

        fun moduleNamed(name: String): KeypadModule? = KeypadModule.entries.firstOrNull { it.name == name }

        /** "control|keyCode|scanCode|device": the device name goes last because it may hold anything. */
        fun encode(control: Int, key: PhysicalKey) = "$control|${key.keyCode}|${key.scanCode}|${key.device}"

        fun decode(entry: String): Pair<Int, PhysicalKey>? {
            val parts = entry.split('|', limit = 4)
            if (parts.size != 4) return null
            val control = parts[0].toIntOrNull() ?: return null
            val code = parts[1].toIntOrNull() ?: return null
            val scan = parts[2].toIntOrNull() ?: return null
            return control to PhysicalKey(parts[3], code, scan)
        }
    }
}

/**
 * The host's keypad configuration, read through [SidekitConfigSource].
 *
 * All writes are ignored: while Sidekit is installed it owns the keypad setup, and a client must
 * never change it. The source's snapshot already contains the host's active module, custom
 * module and per-module profiles.
 */
class HostKeypadPreferences(source: SidekitConfigSource) : KeypadPreferences {
    private val source = CachingSidekitConfigSource(source)

    override fun module(): KeypadModule? = snapshot()?.keypadModuleOrDefault()

    // A host snapshot without a module means "not configured yet"; T9 is the safe default.
    override fun legacyT9(): Boolean = true

    override fun customModule(): KeypadModule? = snapshot()?.customModuleOrDefault()

    override fun profiles(module: KeypadModule): Map<Int, PhysicalKey> = snapshot()?.profiles?.get(module.name).orEmpty()

    override fun observe(onChange: () -> Unit): () -> Unit = source.observe { onChange() }

    private fun snapshot(): SidekitConfig? = source.read()?.takeIf { it.hasCapability(SidekitContract.CAPABILITY_KEYPAD) }
}
