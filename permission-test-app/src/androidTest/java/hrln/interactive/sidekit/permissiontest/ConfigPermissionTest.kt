package hrln.interactive.sidekit.permissiontest

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs in a separate APK that deliberately does not declare the SDK's read permission.
 *
 * The provider is exported with a normal permission, so this is the real cross-UID boundary: a
 * caller without `READ_APP_CONFIG` must be rejected even though it can resolve the provider.
 */
@RunWith(AndroidJUnit4::class)
class ConfigPermissionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun providerRejectsCallerWithoutReadPermission() {
        val installed = AUTHORITIES.filter { authority ->
            context.packageManager.resolveContentProvider(authority, 0) != null
        }
        assumeTrue("Sidekit is not installed on this device", installed.isNotEmpty())

        installed.forEach { authority ->
            val uri = Uri.parse("content://$authority/config")
            val error = runCatching {
                context.contentResolver.call(uri, "getConfig", null, null)
            }.exceptionOrNull()
            assertTrue(
                "Expected SecurityException from $authority, got $error",
                error is SecurityException
            )
        }
    }

    private companion object {
        val AUTHORITIES = listOf(
            "hrln.interactive.sidekit.config",
            "hrln.interactive.sidekit.dev.config"
        )
    }
}
