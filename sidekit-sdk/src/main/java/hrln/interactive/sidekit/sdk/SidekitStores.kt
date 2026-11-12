package hrln.interactive.sidekit.sdk

import android.content.Context
import android.content.Intent
import hrln.interactive.sidekit.design.input.KeypadPreferences
import hrln.interactive.sidekit.sdk.config.SidekitConfigClient
import hrln.interactive.sidekit.sdk.input.HostKeypadPreferences
import hrln.interactive.sidekit.sdk.input.SharedPreferencesKeypadPreferences
import hrln.interactive.sidekit.sdk.theme.HostSidekitThemeStore
import hrln.interactive.sidekit.sdk.theme.SharedPreferencesSidekitThemeStore
import hrln.interactive.sidekit.sdk.theme.SidekitThemeStore

/**
 * Picks the host-backed or local stores for a Sidekit-native app.
 *
 * A host-backed store is read-only and follows Sidekit's settings. When no compatible Sidekit is
 * installed, the app gets its own writable files instead, so it still works standalone.
 */
object SidekitStores {
    /** The stores an app should use, plus the host authority they came from (null when local). */
    data class Stores(
        val theme: SidekitThemeStore,
        val keypad: KeypadPreferences,
        val hostAuthority: String?
    ) {
        val isHostBacked: Boolean get() = hostAuthority != null
    }

    /**
     * Resolves the stores for [context].
     *
     * Pass the launch [intent] so a dev Sidekit that started the app stays the host. Without one,
     * the stable build is preferred, then the dev build.
     *
     * A host is used only when it answers with a complete, compatible configuration. If Sidekit is
     * absent, older than this SDK, or its provider cannot be read, the app gets its own writable
     * stores instead. [localSettingsFile] and [localKeypadFile] name those files, so an app can
     * keep the settings it already had before it gained host support.
     */
    fun forHostOrLocal(
        context: Context,
        intent: Intent? = null,
        localSettingsFile: String = "sidekit_settings",
        localKeypadFile: String = "sidekit_keypad"
    ): Stores {
        val requested = SidekitConfigClient.authorityFrom(intent)
        val authority = SidekitConfigClient.hostAuthority(context, requested)
        // A provider can advertise a compatible schema and still fail to return a snapshot (for
        // example, a partially installed or broken host). Treat that as no host, not as an empty
        // configuration, so the app keeps working standalone.
        val config = authority?.let { SidekitConfigClient.read(context, it) }
        if (authority != null && config != null) {
            val source = SidekitConfigClient.source(context, authority)
            return Stores(
                theme = HostSidekitThemeStore(source),
                keypad = HostKeypadPreferences(source),
                hostAuthority = authority
            )
        }
        return local(context, localSettingsFile, localKeypadFile)
    }

    /** The app's own writable stores, used when no compatible Sidekit host is available. */
    fun local(
        context: Context,
        settingsFile: String = "sidekit_settings",
        keypadFile: String = "sidekit_keypad"
    ): Stores = Stores(
        theme = SharedPreferencesSidekitThemeStore(context, settingsFile),
        keypad = SharedPreferencesKeypadPreferences(context, keypadFile),
        hostAuthority = null
    )
}
