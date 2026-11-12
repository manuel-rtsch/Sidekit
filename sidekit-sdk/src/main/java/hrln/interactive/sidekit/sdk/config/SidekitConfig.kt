package hrln.interactive.sidekit.sdk.config

import hrln.interactive.sidekit.design.input.KeypadModule
import hrln.interactive.sidekit.design.input.PhysicalKey
import hrln.interactive.sidekit.design.input.T9Mode
import hrln.interactive.sidekit.design.theme.CaseColour
import hrln.interactive.sidekit.sdk.SidekitContract

/**
 * A read-only snapshot of the configuration a Sidekit host exposes to Sidekit-native apps.
 *
 * The names are stored as strings, not enums, so a newer host can add a case colour or keypad
 * module without breaking an older client. Use the `…OrDefault` helpers for that fallback.
 */
data class SidekitConfig(
    val schema: Int = SidekitContract.SCHEMA_VERSION,
    val hostVersion: String = "",
    val capabilities: List<String> = SidekitContract.CAPABILITIES,
    val setupComplete: Boolean = false,
    val caseColour: String? = null,
    val compactLayout: Boolean = false,
    val inputMode: String = T9Mode.MultiTap.name,
    val keypadModule: String? = null,
    val customModule: String? = null,
    /** Module name (e.g. `T9`) to control code to the physical key bound to it. */
    val profiles: Map<String, Map<Int, PhysicalKey>> = emptyMap()
) {
    /** Unknown case colours fall back to the original blue. */
    fun caseColourOrDefault(): CaseColour? = CaseColour.named(caseColour)

    /** Unknown input modes fall back to multi-tap. */
    fun inputModeOrDefault(): T9Mode = T9Mode.entries.firstOrNull { it.name == inputMode } ?: T9Mode.MultiTap

    /** Unknown keypad modules are treated as unset. */
    fun keypadModuleOrDefault(): KeypadModule? = keypadModule?.let { name -> KeypadModule.entries.firstOrNull { it.name == name } }

    /** Unknown custom modules are treated as off. */
    fun customModuleOrDefault(): KeypadModule? = customModule?.let { name -> KeypadModule.entries.firstOrNull { it.name == name } }

    /** True when this host understands the schema this SDK was built against. */
    fun isCompatible(): Boolean = schema in 1..SidekitContract.SCHEMA_VERSION

    /** Whether the host advertised [capability] in its version response. */
    fun hasCapability(capability: String): Boolean = capability in capabilities
}

/**
 * Where [SidekitConfig] comes from: the host provider today, an in-memory fake in tests.
 *
 * [read] returns null when no compatible host is available. [observe] calls [onChange] whenever
 * the host configuration changes and returns a function that stops observing.
 */
interface SidekitConfigSource {
    fun read(): SidekitConfig?
    fun observe(onChange: (SidekitConfig?) -> Unit): () -> Unit
}

/** A fixed source for previews, tests and apps that are running without a host. */
class StaticSidekitConfigSource(private val config: SidekitConfig?) : SidekitConfigSource {
    override fun read(): SidekitConfig? = config
    override fun observe(onChange: (SidekitConfig?) -> Unit): () -> Unit = {}
}

/**
 * Keeps one snapshot until the host says it changed.
 *
 * A host read is a binder round-trip (and [SidekitConfigClient] probes the version first), so a
 * keypad mapper that asks for the module, custom module and every profile should not pay for it
 * each time. [observe] refreshes the snapshot before forwarding the change.
 */
internal class CachingSidekitConfigSource(private val source: SidekitConfigSource) : SidekitConfigSource {
    private val lock = Any()
    private var cached: SidekitConfig? = null
    private var loaded = false

    override fun read(): SidekitConfig? = synchronized(lock) {
        if (!loaded) {
            cached = source.read()
            loaded = true
        }
        cached
    }

    override fun observe(onChange: (SidekitConfig?) -> Unit): () -> Unit = source.observe { config ->
        synchronized(lock) {
            cached = config
            loaded = true
        }
        onChange(config)
    }
}
