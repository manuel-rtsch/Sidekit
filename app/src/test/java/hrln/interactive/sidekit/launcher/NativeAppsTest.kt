package hrln.interactive.sidekit.launcher

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import hrln.interactive.sidekit.sdk.SidekitContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class NativeAppsTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val context: Context = application

    /** Discovery under test ignores signing certificates; trust has its own tests. */
    private fun discover() = NativeApps.discover(context) { true }

    private fun addNativeApp(
        packageName: String,
        label: String,
        activityName: String = "$packageName.MainActivity",
        appId: String? = null,
        minSchema: Int? = null,
        minSchemaString: String? = null,
        launchRoute: String? = null
    ) = addNativeAppForTest(context, packageName, label, activityName, appId, minSchema, minSchemaString, launchRoute)

    @Test
    fun `discovers native apps with their contract metadata sorted by label`() {
        addNativeApp("test.zeta", "Zeta", appId = "zeta", minSchema = 1, launchRoute = "home")
        addNativeApp("test.alpha", "Alpha", minSchema = 1)

        val apps = discover()

        assertEquals(listOf("test.alpha", "test.zeta"), apps.map { it.packageName })
        assertEquals("native:test.alpha", apps[0].visibilityId)
        assertEquals("test.zeta", apps[1].packageName)
        assertEquals("zeta", apps[1].appId)
        assertEquals(1, apps[1].minSchema)
        assertEquals("home", apps[1].launchRoute)
    }

    @Test
    fun `skips apps that need a newer schema`() {
        addNativeApp("test.future", "Future", minSchema = SidekitContract.SCHEMA_VERSION + 1)
        addNativeApp("test.current", "Current", minSchema = SidekitContract.SCHEMA_VERSION)

        assertEquals(listOf("test.current"), discover().map { it.packageName })
    }

    @Test
    fun `reads a string MIN_SCHEMA from the manifest`() {
        addNativeApp("test.string", "String", minSchemaString = "1")
        addNativeApp("test.future", "Future", minSchemaString = (SidekitContract.SCHEMA_VERSION + 1).toString())

        val apps = discover()

        assertEquals(listOf("test.string"), apps.map { it.packageName })
        assertEquals(1, apps.single().minSchema)
    }

    @Test
    fun `skips this Sidekit package and keeps one entry per app`() {
        addNativeApp(context.packageName, "Sidekit itself")
        addNativeApp("test.twice", "Twice", activityName = "test.twice.First")
        addNativeApp("test.twice", "Twice again", activityName = "test.twice.Second")

        assertEquals(listOf("test.twice"), discover().map { it.packageName })
    }

    @Test
    fun `an app that is not signed by a trusted certificate gets no tile`() {
        addNativeApp("test.trusted", "Trusted")
        addNativeApp("test.untrusted", "Untrusted")

        val apps = NativeApps.discover(context) { it == "test.trusted" }

        assertEquals(listOf("test.trusted"), apps.map { it.packageName })
    }

    @Test
    fun `an app cannot claim a built-in id`() {
        addNativeApp("test.spoof", "Spoof", appId = "builtin:Mail")
        addNativeApp("test.real", "Real")

        assertEquals(listOf("test.real"), discover().map { it.packageName })
    }

    @Test
    fun `one entry per app id, in label order`() {
        addNativeApp("test.aaa", "Zeta", appId = "shared")
        addNativeApp("test.bbb", "Alpha", appId = "shared")

        val apps = discover()

        assertEquals(listOf("test.bbb"), apps.map { it.packageName })
        assertEquals("shared", apps.single().appId)
    }

    @Test
    fun `launch intent is explicit and names the host authority and route`() {
        val app = NativeApp("test.alpha", "test.alpha.MainActivity", "Alpha", launchRoute = "home")

        val intent = app.launchIntent(SidekitContract.AUTHORITY_DEV)

        assertEquals(SidekitContract.ACTION_NATIVE_APP, intent.action)
        assertEquals(ComponentName("test.alpha", "test.alpha.MainActivity"), intent.component)
        assertEquals(SidekitContract.AUTHORITY_DEV, intent.getStringExtra(SidekitContract.EXTRA_HOST_AUTHORITY))
        assertEquals("home", intent.getStringExtra(SidekitContract.EXTRA_LAUNCH_ROUTE))
        assertNull(app.launchIntent().getStringExtra(SidekitContract.EXTRA_HOST_AUTHORITY))

        val appWithoutRoute = NativeApp("test.beta", "test.beta.MainActivity", "Beta")
        assertNull(appWithoutRoute.launchIntent().getStringExtra(SidekitContract.EXTRA_LAUNCH_ROUTE))
    }

    @Test
    fun `launch starts the explicit activity with this build as the configuration host`() {
        val app = NativeApp("test.alpha", "test.alpha.MainActivity", "Alpha", launchRoute = "home")

        assertTrue(app.launch(application))

        val started = shadowOf(application).nextStartedActivity
        assertEquals(ComponentName("test.alpha", "test.alpha.MainActivity"), started.component)
        assertEquals(
            SidekitContract.authorityForPackage(context.packageName),
            started.getStringExtra(SidekitContract.EXTRA_HOST_AUTHORITY)
        )
        assertEquals("home", started.getStringExtra(SidekitContract.EXTRA_LAUNCH_ROUTE))
    }
}
