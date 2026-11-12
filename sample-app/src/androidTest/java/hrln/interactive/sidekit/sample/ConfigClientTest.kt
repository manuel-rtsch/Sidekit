package hrln.interactive.sidekit.sample

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import hrln.interactive.sidekit.sdk.config.HostStatus
import hrln.interactive.sidekit.sdk.config.SidekitConfigClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs in the sample APK, a different package from Sidekit, so it exercises the real provider
 * boundary instead of an in-process fake. On a device without Sidekit it must report NotInstalled
 * and return no config; with Sidekit installed it must read the host snapshot.
 */
@RunWith(AndroidJUnit4::class)
class ConfigClientTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun clientProbesAndReadsFromAnotherPackage() {
        val status = SidekitConfigClient.status(context)
        val config = SidekitConfigClient.read(context)

        if (status == HostStatus.Available) {
            assertNotNull(config)
            assertTrue(config!!.isCompatible())
        } else {
            assertNull(config)
        }
    }
}
