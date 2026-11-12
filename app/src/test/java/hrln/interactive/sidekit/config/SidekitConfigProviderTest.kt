package hrln.interactive.sidekit.config

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import hrln.interactive.sidekit.apps.settings.SidekitPreferences
import hrln.interactive.sidekit.design.input.KeypadModule
import hrln.interactive.sidekit.design.input.PhysicalKey
import hrln.interactive.sidekit.design.input.T9Mode
import hrln.interactive.sidekit.design.theme.CaseColour
import hrln.interactive.sidekit.sdk.SidekitContract
import hrln.interactive.sidekit.sdk.config.HostStatus
import hrln.interactive.sidekit.sdk.config.SidekitConfig
import hrln.interactive.sidekit.sdk.config.SidekitConfigClient
import hrln.interactive.sidekit.sdk.config.SidekitConfigCodec
import hrln.interactive.sidekit.sdk.config.SidekitPreferenceFiles
import hrln.interactive.sidekit.sdk.input.SharedPreferencesKeypadPreferences
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
class SidekitConfigProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val configUri = Uri.parse("content://${SidekitContract.AUTHORITY_STABLE}/${SidekitContract.PATH_CONFIG}")

    @Before
    fun clearPreferences() {
        context.getSharedPreferences(SidekitPreferenceFiles.SETTINGS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(SidekitPreferenceFiles.KEYPAD, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `provider round-trips only the theme and keypad contract`() {
        val preferences = SidekitPreferences(context).apply {
            setupComplete = true
            caseColour = CaseColour.Lime
            compactLayout = true
            inputMode = T9Mode.Numeric
            hiddenApps = setOf("builtin:Mail", "builtin:Vault")
        }
        val keypad = SharedPreferencesKeypadPreferences(context)
        keypad.writeModule(KeypadModule.T9)
        keypad.writeCustomModule(KeypadModule.Qwerty)
        keypad.writeProfile(KeypadModule.T9, mapOf(131 to PhysicalKey("Sidephone keypad", 19)))

        val provider = provider()
        val version = provider.call(SidekitContract.METHOD_GET_VERSION, null, null)!!
        assertEquals(SidekitContract.SCHEMA_VERSION, version.getInt(SidekitConfigCodec.KEY_SCHEMA))
        assertEquals(SidekitContract.CAPABILITIES, version.getStringArray(SidekitConfigCodec.KEY_CAPABILITIES)?.toList())

        val bundle = provider.call(SidekitContract.METHOD_GET_CONFIG, null, null)!!
        val config = SidekitConfigCodec.decodeConfig(bundle)!!
        assertTrue(config.isCompatible())
        assertTrue(config.setupComplete)
        assertEquals(CaseColour.Lime.name, config.caseColour)
        assertTrue(config.compactLayout)
        assertEquals(T9Mode.Numeric.name, config.inputMode)
        assertEquals(KeypadModule.T9.name, config.keypadModule)
        assertEquals(KeypadModule.Qwerty.name, config.customModule)
        assertEquals(mapOf(131 to PhysicalKey("Sidephone keypad", 19)), config.profiles[KeypadModule.T9.name])

        val keys = bundle.keySet()
        assertFalse(keys.any { it.contains("hidden") || it.contains("home") || it.contains("dock") })
    }

    @Test
    fun `a caller without the read permission is denied`() {
        val provider = provider(allowed = false)
        assertThrows(SecurityException::class.java) {
            provider.call(SidekitContract.METHOD_GET_CONFIG, null, null)
        }
    }

    @Test
    fun `preference changes notify content observers`() {
        val provider = provider()
        val changes = AtomicInteger()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                changes.incrementAndGet()
            }
        }
        context.contentResolver.registerContentObserver(configUri, true, observer)
        try {
            SidekitPreferences(context).compactLayout = true
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(changes.get() > 0)
        } finally {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    @Test
    fun `private preference changes do not notify content observers`() {
        val provider = provider()
        val changes = AtomicInteger()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                changes.incrementAndGet()
            }
        }
        context.contentResolver.registerContentObserver(configUri, true, observer)
        try {
            SidekitPreferences(context).apply {
                hiddenApps = setOf("builtin:Mail")
                setupStep = 3
                listLayout = true
            }
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, changes.get())
        } finally {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    @Test
    fun `the provider is exported read-only with the SDK permission`() {
        val info = context.packageManager.getProviderInfo(
            android.content.ComponentName(context, SidekitConfigProvider::class.java),
            0
        )
        assertTrue(info.exported)
        assertEquals(SidekitContract.authorityForPackage(context.packageName), info.authority)
        // Named after the host package so the stable and dev builds can coexist.
        assertEquals(SidekitContract.readPermissionForPackage(info.packageName), info.readPermission)
        assertNull(info.writePermission)
        assertFalse(info.grantUriPermissions)
    }

    @Test
    fun `the provider has no write or file path`() {
        val provider = provider()
        assertThrows(UnsupportedOperationException::class.java) { provider.insert(configUri, ContentValues()) }
        assertThrows(UnsupportedOperationException::class.java) { provider.update(configUri, ContentValues(), null, null) }
        assertThrows(UnsupportedOperationException::class.java) { provider.delete(configUri, null, null) }
        assertThrows(UnsupportedOperationException::class.java) { provider.bulkInsert(configUri, arrayOf(ContentValues())) }
        val declared = SidekitConfigProvider::class.java.declaredMethods.map { it.name }
        assertFalse(declared.any { it.startsWith("openFile") || it.startsWith("openAssetFile") || it.startsWith("openTypedAssetFile") })
    }

    @Test
    fun `client probes stable then dev and reads the requested authority`() {
        registerHost(SidekitContract.AUTHORITY_STABLE, SidekitConfig(caseColour = CaseColour.Lime.name))
        registerHost(SidekitContract.AUTHORITY_DEV, SidekitConfig(caseColour = CaseColour.Orange.name))

        assertEquals(HostStatus.Available, SidekitConfigClient.status(context))
        assertEquals(CaseColour.Lime.name, SidekitConfigClient.read(context)?.caseColour)

        assertEquals(HostStatus.Available, SidekitConfigClient.status(context, SidekitContract.AUTHORITY_DEV))
        assertEquals(CaseColour.Orange.name, SidekitConfigClient.read(context, SidekitContract.AUTHORITY_DEV)?.caseColour)
    }

    @Test
    fun `known authorities map to their Sidekit packages`() {
        assertEquals(SidekitContract.PACKAGE_STABLE, SidekitContract.packageForAuthority(SidekitContract.AUTHORITY_STABLE))
        assertEquals(SidekitContract.PACKAGE_DEV, SidekitContract.packageForAuthority(SidekitContract.AUTHORITY_DEV))
        assertNull(SidekitContract.packageForAuthority("com.example.evil.config"))
    }

    @Test
    fun `client honours a host authority passed by the launcher`() {
        val intent = Intent().putExtra(SidekitContract.EXTRA_HOST_AUTHORITY, SidekitContract.AUTHORITY_DEV)
        assertEquals(SidekitContract.AUTHORITY_DEV, SidekitConfigClient.authorityFrom(intent))
        assertNull(SidekitConfigClient.authorityFrom(null))
        assertNull(SidekitConfigClient.authorityFrom(Intent().putExtra(SidekitContract.EXTRA_HOST_AUTHORITY, "com.example.evil.config")))

        registerHost(SidekitContract.AUTHORITY_STABLE, SidekitConfig(caseColour = CaseColour.Lime.name))
        assertEquals(
            HostStatus.NotInstalled,
            SidekitConfigClient.status(context, "com.example.missing.config")
        )
    }

    @Test
    fun `a missing dev host falls back to the stable host`() {
        registerHost(SidekitContract.AUTHORITY_STABLE, SidekitConfig(caseColour = CaseColour.Lime.name))

        assertEquals(HostStatus.Available, SidekitConfigClient.status(context, SidekitContract.AUTHORITY_DEV))
        assertEquals(SidekitContract.AUTHORITY_STABLE, SidekitConfigClient.hostAuthority(context, SidekitContract.AUTHORITY_DEV))
        assertEquals(CaseColour.Lime.name, SidekitConfigClient.read(context, SidekitContract.AUTHORITY_DEV)?.caseColour)
    }

    @Test
    fun `an incompatible host is installed but unsupported`() {
        registerHost(SidekitContract.AUTHORITY_DEV, SidekitConfig(schema = SidekitContract.SCHEMA_VERSION + 1))

        assertEquals(HostStatus.InstalledUnsupported, SidekitConfigClient.status(context, SidekitContract.AUTHORITY_DEV))
        assertNull(SidekitConfigClient.read(context, SidekitContract.AUTHORITY_DEV))
    }

    @Test
    fun `a host that refuses the caller reports not permitted`() {
        val refusing = object : ContentProvider() {
            override fun onCreate(): Boolean = true
            override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = throw SecurityException("not trusted")
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        }
        registerHost(SidekitContract.AUTHORITY_DEV, refusing)

        assertEquals(HostStatus.NotPermitted, SidekitConfigClient.status(context, SidekitContract.AUTHORITY_DEV))
        assertNull(SidekitConfigClient.read(context, SidekitContract.AUTHORITY_DEV))
    }

    @Test
    fun `a host without a version response is unsupported`() {
        val provider = object : ContentProvider() {
            override fun onCreate(): Boolean = true
            override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = null
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        }
        registerHost(SidekitContract.AUTHORITY_DEV, provider)

        assertEquals(HostStatus.InstalledUnsupported, SidekitConfigClient.status(context, SidekitContract.AUTHORITY_DEV))
    }

    @Test
    fun `client observes changes to the host configuration`() {
        val provider = registerHost(SidekitContract.AUTHORITY_DEV, SidekitConfig(caseColour = CaseColour.Lime.name))
        var latest: String? = null
        val stop = SidekitConfigClient.observe(context, { latest = it.caseColour }, SidekitContract.AUTHORITY_DEV)
        try {
            provider.config = SidekitConfig(caseColour = CaseColour.Pink.name)
            context.contentResolver.notifyChange(
                Uri.parse("content://${SidekitContract.AUTHORITY_DEV}/${SidekitContract.PATH_CONFIG}"),
                null
            )
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(CaseColour.Pink.name, latest)
        } finally {
            stop()
        }
    }

    private fun provider(allowed: Boolean = true): TestProvider = Robolectric.buildContentProvider(TestProvider::class.java).create().get().also { it.allowed = allowed }

    private fun registerHost(authority: String, config: SidekitConfig): FakeConfigProvider {
        val provider = FakeConfigProvider(config)
        registerHost(authority, provider)
        return provider
    }

    private fun registerHost(authority: String, provider: ContentProvider) {
        ShadowContentResolver.registerProviderInternal(authority, provider)
        shadowOf(context.packageManager).addOrUpdateProvider(
            ProviderInfo().apply {
                name = provider.javaClass.name
                packageName = SidekitContract.packageForAuthority(authority) ?: context.packageName
                this.authority = authority
                exported = true
            }
        )
    }

    class TestProvider : SidekitConfigProvider() {
        var allowed = true

        override fun callerCanRead(): Boolean = allowed
    }

    class FakeConfigProvider(var config: SidekitConfig) : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = when (method) {
            SidekitContract.METHOD_GET_VERSION -> SidekitConfigCodec.encodeVersion(
                config.schema,
                config.hostVersion,
                config.capabilities
            )

            SidekitContract.METHOD_GET_CONFIG -> SidekitConfigCodec.encodeConfig(config)

            else -> null
        }

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
