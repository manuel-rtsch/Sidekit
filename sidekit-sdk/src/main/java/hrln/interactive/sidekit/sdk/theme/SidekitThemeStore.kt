package hrln.interactive.sidekit.sdk.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import hrln.interactive.sidekit.design.theme.CaseColour
import hrln.interactive.sidekit.sdk.SidekitContract
import hrln.interactive.sidekit.sdk.config.CachingSidekitConfigSource
import hrln.interactive.sidekit.sdk.config.SidekitConfig
import hrln.interactive.sidekit.sdk.config.SidekitConfigSource
import hrln.interactive.sidekit.sdk.config.SidekitPreferenceFiles

/** The theme choices a Sidekit-native app needs to render like its host. */
data class SidekitThemeConfig(
    val caseColour: CaseColour? = null,
    val compact: Boolean = false
)

/**
 * Reads and (when it owns the configuration) writes the Sidekit theme.
 *
 * The host-backed implementation is read-only: writes are ignored so a client never changes
 * Sidekit's configuration while Sidekit is installed.
 */
interface SidekitThemeStore {
    fun read(): SidekitThemeConfig
    fun observe(onChange: (SidekitThemeConfig) -> Unit): () -> Unit

    /** Optional. Host-backed stores ignore writes. */
    fun write(config: SidekitThemeConfig) {}

    fun writeCaseColour(colour: CaseColour?) = write(read().copy(caseColour = colour))

    fun writeCompact(compact: Boolean) = write(read().copy(compact = compact))
}

/**
 * Reads the theme from a local `SharedPreferences` file, matching Sidekit's own keys.
 *
 * Sidekit uses the default `sidekit_settings` file. A standalone Sideplay can pass its own file
 * name to keep a separate, writable copy.
 */
class SharedPreferencesSidekitThemeStore(
    context: android.content.Context,
    fileName: String = SidekitPreferenceFiles.SETTINGS
) : SidekitThemeStore {
    private val prefs = context.applicationContext.getSharedPreferences(fileName, android.content.Context.MODE_PRIVATE)

    override fun read(): SidekitThemeConfig = SidekitThemeConfig(
        caseColour = CaseColour.named(prefs.getString(KEY_CASE_COLOUR, null)),
        compact = prefs.getBoolean(KEY_COMPACT_LAYOUT, false)
    )

    override fun observe(onChange: (SidekitThemeConfig) -> Unit): () -> Unit {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == KEY_CASE_COLOUR || key == KEY_COMPACT_LAYOUT) onChange(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override fun write(config: SidekitThemeConfig) {
        prefs.edit()
            .putString(KEY_CASE_COLOUR, config.caseColour?.name)
            .putBoolean(KEY_COMPACT_LAYOUT, config.compact)
            .apply()
    }

    private companion object {
        const val KEY_CASE_COLOUR = "case_colour"
        const val KEY_COMPACT_LAYOUT = "compact_layout"
    }
}

/**
 * Reads the theme from the installed Sidekit host.
 *
 * There is no write path: Sidekit owns its settings. When the host has not completed setup the
 * snapshot's defaults (original blue, comfortable layout) apply.
 */
class HostSidekitThemeStore(source: SidekitConfigSource) : SidekitThemeStore {
    private val source = CachingSidekitConfigSource(source)

    override fun read(): SidekitThemeConfig = from(source.read())

    override fun observe(onChange: (SidekitThemeConfig) -> Unit): () -> Unit = source.observe { onChange(from(it)) }

    private fun from(config: SidekitConfig?): SidekitThemeConfig {
        val theme = config?.takeIf { it.hasCapability(SidekitContract.CAPABILITY_THEME) }
        return SidekitThemeConfig(
            caseColour = theme?.caseColourOrDefault(),
            compact = theme?.compactLayout ?: false
        )
    }
}

/**
 * Remembers the current theme and keeps it up to date with [store].
 *
 * This is the Compose entry point every Sidekit-native app uses so a Settings change in the host
 * re-lays out the client on the next frame.
 */
@Composable
fun rememberSidekitThemeConfig(store: SidekitThemeStore): SidekitThemeConfig {
    var config by remember(store) { mutableStateOf(store.read()) }
    DisposableEffect(store) {
        val stop = store.observe { config = it }
        config = store.read()
        onDispose { stop() }
    }
    return config
}
