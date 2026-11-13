package hrln.interactive.sidekit.config

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import hrln.interactive.sidekit.BuildConfig
import hrln.interactive.sidekit.apps.settings.SidekitPreferences
import hrln.interactive.sidekit.design.input.KeypadModule
import hrln.interactive.sidekit.sdk.SidekitContract
import hrln.interactive.sidekit.sdk.config.SidekitConfig
import hrln.interactive.sidekit.sdk.config.SidekitConfigCodec
import hrln.interactive.sidekit.sdk.config.SidekitPreferenceFiles
import hrln.interactive.sidekit.sdk.input.SharedPreferencesKeypadPreferences

/**
 * Exposes Sidekit's theme and keypad configuration to Sidekit-native apps.
 *
 * The provider is exported read-only. It has no write or file path: clients call `getVersion` and
 * `getConfig`, and the provider notifies `content://<authority>/config` observers whenever the
 * settings that feed the contract change.
 *
 * Only the keys listed in [SidekitConfigCodec] leave the process. Hidden apps, home order,
 * credentials, messages and the other app-private stores are never read here.
 *
 * Access is decided by the caller's signing certificate (see [TrustedCallers]). The normal read
 * permission remains the declared intent, but it is not part of the access decision: it adds no
 * identity, and a client installed before Sidekit defined it can stay ungranted forever.
 */
// The provider is the host side of the SDK's internal wire format; the codec and file names are
// restricted so third-party apps cannot depend on them.
@SuppressLint("RestrictedApi")
open class SidekitConfigProvider : ContentProvider() {
    private var appContext: Context? = null

    // SharedPreferences holds listeners weakly; keep this one alive for the provider's lifetime.
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun onCreate(): Boolean {
        val app = context?.applicationContext ?: return false
        appContext = app
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (isContractKey(key)) notifyConfigChanged(app)
        }
        settingsFile(app).registerOnSharedPreferenceChangeListener(listener)
        keypadFile(app).registerOnSharedPreferenceChangeListener(listener)
        listeners += listener
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!callerCanRead()) {
            val permission = context?.packageName?.let(SidekitContract::readPermissionForPackage)
            throw SecurityException("Missing $permission")
        }
        return when (method) {
            SidekitContract.METHOD_GET_VERSION -> readConfig().let { config ->
                SidekitConfigCodec.encodeVersion(config.schema, config.hostVersion, config.capabilities)
            }

            SidekitContract.METHOD_GET_CONFIG -> SidekitConfigCodec.encodeConfig(readConfig())

            else -> throw IllegalArgumentException("Unknown Sidekit configuration method: $method")
        }
    }

    /**
     * Whether the current caller is signed by a trusted certificate.
     *
     * The provider's `android:readPermission` is not checked here: Android does not enforce it
     * for [call], and the permission is `normal`, so it records intent rather than identity. A
     * client installed before Sidekit defined the permission can also remain permanently
     * ungranted. The signing-certificate allowlist is the real gate, so install order no longer
     * decides whether a trusted app can read the configuration. This hook is open so tests can
     * exercise both outcomes without a second installed app.
     */
    internal open fun callerCanRead(): Boolean {
        val context = context ?: return false
        return TrustedCallers.isTrustedCaller(context)
    }

    private fun readConfig(): SidekitConfig {
        val app = appContext?.applicationContext ?: context?.applicationContext
            ?: return SidekitConfig(hostVersion = BuildConfig.VERSION_NAME)
        val preferences = SidekitPreferences(app)
        val keypad = SharedPreferencesKeypadPreferences(app, SidekitPreferenceFiles.KEYPAD)
        return SidekitConfig(
            schema = SidekitContract.SCHEMA_VERSION,
            hostVersion = BuildConfig.VERSION_NAME,
            capabilities = SidekitContract.CAPABILITIES,
            setupComplete = preferences.setupComplete,
            caseColour = preferences.caseColour?.name,
            compactLayout = preferences.compactLayout,
            inputMode = preferences.inputMode.name,
            keypadModule = keypad.module()?.name,
            customModule = keypad.customModule()?.name,
            profiles = KeypadModule.entries.mapNotNull { module ->
                keypad.profiles(module).takeIf { it.isNotEmpty() }?.let { module.name to it }
            }.toMap()
        )
    }

    private fun notifyConfigChanged(context: Context) {
        context.contentResolver.notifyChange(configUri(context), null)
    }

    /**
     * Only the keys that feed the contract wake observers. Hidden apps, setup step, list layout
     * and the legacy T9 flag stay private and must not cause a client re-read.
     */
    private fun isContractKey(key: String?): Boolean = key == null ||
        key in CONTRACT_SETTINGS_KEYS ||
        key in CONTRACT_KEYPAD_KEYS ||
        key.startsWith(CONTRACT_PROFILE_PREFIX)

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = readOnly()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = readOnly()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = readOnly()

    override fun bulkInsert(uri: Uri, values: Array<out ContentValues>): Int = readOnly()

    private fun readOnly(): Nothing = throw UnsupportedOperationException("Sidekit configuration is read-only")

    private fun settingsFile(context: Context): SharedPreferences = context.getSharedPreferences(SidekitPreferenceFiles.SETTINGS, Context.MODE_PRIVATE)

    private fun keypadFile(context: Context): SharedPreferences = context.getSharedPreferences(SidekitPreferenceFiles.KEYPAD, Context.MODE_PRIVATE)

    private fun configUri(context: Context): Uri {
        val authority = SidekitContract.authorityForPackage(context.packageName)
        return Uri.parse("content://$authority/${SidekitContract.PATH_CONFIG}")
    }

    private companion object {
        val CONTRACT_SETTINGS_KEYS = setOf("setup_complete", "case_colour", "compact_layout", "input_mode")
        val CONTRACT_KEYPAD_KEYS = setOf("module", "custom_module")
        const val CONTRACT_PROFILE_PREFIX = "profile."
    }
}
