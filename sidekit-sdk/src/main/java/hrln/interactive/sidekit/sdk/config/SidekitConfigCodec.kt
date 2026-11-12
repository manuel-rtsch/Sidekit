package hrln.interactive.sidekit.sdk.config

import android.os.Bundle
import androidx.annotation.RestrictTo
import hrln.interactive.sidekit.design.input.PhysicalKey
import hrln.interactive.sidekit.design.input.T9Mode
import hrln.interactive.sidekit.sdk.SidekitContract

/**
 * Encodes and decodes the configuration contract exchanged through `ContentResolver.call`.
 *
 * This is the wire format, not a public API: keys are stable and additive for the lifetime of a
 * [SidekitContract.SCHEMA_VERSION]. Both the host provider and [SidekitConfigClient] use it so a
 * malformed or partial response is rejected in one place.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object SidekitConfigCodec {
    const val KEY_SCHEMA = "schema"
    const val KEY_HOST_VERSION = "hostVersion"
    const val KEY_CAPABILITIES = "capabilities"
    const val KEY_SETUP_COMPLETE = "meta.setupComplete"
    const val KEY_CASE_COLOUR = "theme.caseColour"
    const val KEY_COMPACT_LAYOUT = "theme.compactLayout"
    const val KEY_INPUT_MODE = "input.mode"
    const val KEY_KEYPAD_MODULE = "input.module"
    const val KEY_CUSTOM_MODULE = "input.customModule"
    const val KEY_PROFILE_PREFIX = "input.profiles."

    private const val KEY_DEVICE = "device"
    private const val KEY_KEY_CODE = "keyCode"
    private const val KEY_SCAN_CODE = "scanCode"

    /** The `getVersion` response: just enough for a client to decide whether it can read config. */
    fun encodeVersion(schema: Int, hostVersion: String, capabilities: List<String>): Bundle = Bundle().apply {
        putInt(KEY_SCHEMA, schema)
        putString(KEY_HOST_VERSION, hostVersion)
        putStringArray(KEY_CAPABILITIES, capabilities.toTypedArray())
    }

    /** Decodes a `getVersion` response, or null when it did not carry a usable schema. */
    fun decodeVersion(bundle: Bundle?): SidekitConfig? {
        val schema = schemaOf(bundle) ?: return null
        return SidekitConfig(
            schema = schema,
            hostVersion = bundle?.getString(KEY_HOST_VERSION).orEmpty(),
            capabilities = bundle.capabilities()
        )
    }

    /** The `getConfig` response: the full, additive configuration snapshot. */
    fun encodeConfig(config: SidekitConfig): Bundle = Bundle().apply {
        putInt(KEY_SCHEMA, config.schema)
        putString(KEY_HOST_VERSION, config.hostVersion)
        putStringArray(KEY_CAPABILITIES, config.capabilities.toTypedArray())
        putBoolean(KEY_SETUP_COMPLETE, config.setupComplete)
        config.caseColour?.let { putString(KEY_CASE_COLOUR, it) }
        putBoolean(KEY_COMPACT_LAYOUT, config.compactLayout)
        putString(KEY_INPUT_MODE, config.inputMode)
        config.keypadModule?.let { putString(KEY_KEYPAD_MODULE, it) }
        config.customModule?.let { putString(KEY_CUSTOM_MODULE, it) }
        config.profiles.forEach { (module, bindings) ->
            putBundle(
                KEY_PROFILE_PREFIX + module,
                Bundle().apply {
                    bindings.forEach { (control, key) -> putBundle(control.toString(), encodeKey(key)) }
                }
            )
        }
    }

    /** Decodes a `getConfig` response, or null when the bundle has no usable schema. */
    fun decodeConfig(bundle: Bundle?): SidekitConfig? {
        val schema = schemaOf(bundle) ?: return null
        return SidekitConfig(
            schema = schema,
            hostVersion = bundle?.getString(KEY_HOST_VERSION).orEmpty(),
            capabilities = bundle.capabilities(),
            setupComplete = bundle?.getBoolean(KEY_SETUP_COMPLETE, false) ?: false,
            caseColour = bundle?.getString(KEY_CASE_COLOUR),
            compactLayout = bundle?.getBoolean(KEY_COMPACT_LAYOUT, false) ?: false,
            inputMode = bundle?.getString(KEY_INPUT_MODE) ?: T9Mode.MultiTap.name,
            keypadModule = bundle?.getString(KEY_KEYPAD_MODULE),
            customModule = bundle?.getString(KEY_CUSTOM_MODULE),
            profiles = bundle?.profiles().orEmpty()
        )
    }

    private fun schemaOf(bundle: Bundle?): Int? {
        if (bundle == null || !bundle.containsKey(KEY_SCHEMA)) return null
        return bundle.getInt(KEY_SCHEMA).takeIf { it > 0 }
    }

    private fun Bundle?.capabilities(): List<String> = this?.getStringArray(KEY_CAPABILITIES)?.toList().orEmpty()

    private fun Bundle.profiles(): Map<String, Map<Int, PhysicalKey>> {
        val result = mutableMapOf<String, Map<Int, PhysicalKey>>()
        for (key in keySet()) {
            if (!key.startsWith(KEY_PROFILE_PREFIX)) continue
            val module = key.removePrefix(KEY_PROFILE_PREFIX)
            if (module.isEmpty()) continue
            val encoded = getBundle(key) ?: continue
            val bindings = encoded.keySet().mapNotNull { control ->
                val keyCode = control.toIntOrNull() ?: return@mapNotNull null
                val physicalKey = encoded.getBundle(control)?.let(::decodeKey) ?: return@mapNotNull null
                keyCode to physicalKey
            }.toMap()
            result[module] = bindings
        }
        return result
    }

    private fun encodeKey(key: PhysicalKey): Bundle = Bundle().apply {
        putString(KEY_DEVICE, key.device)
        putInt(KEY_KEY_CODE, key.keyCode)
        putInt(KEY_SCAN_CODE, key.scanCode)
    }

    private fun decodeKey(bundle: Bundle): PhysicalKey? {
        val device = bundle.getString(KEY_DEVICE) ?: return null
        if (!bundle.containsKey(KEY_KEY_CODE)) return null
        return PhysicalKey(
            device = device,
            keyCode = bundle.getInt(KEY_KEY_CODE),
            scanCode = bundle.getInt(KEY_SCAN_CODE, 0)
        )
    }
}
