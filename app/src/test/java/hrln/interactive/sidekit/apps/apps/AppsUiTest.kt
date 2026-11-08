package hrln.interactive.sidekit.apps.apps

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import hrln.interactive.sidekit.design.action.ChromeState
import hrln.interactive.sidekit.design.action.LocalChrome
import hrln.interactive.sidekit.home.HomePreferences
import hrln.interactive.sidekit.home.homeId
import hrln.interactive.sidekit.ui.SidekitPreviewFrame
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [31])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `tap launches and Options pins and unpins the app`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        HomePreferences(context).reset()
        val app = LaunchableApp(ComponentName("org.mozilla", "Main"), Process.myUserHandle(), "Firefox")
        var launched: LaunchableApp? = null
        val source = object : AppsSource {
            override val apps = MutableStateFlow(listOf(app))
            override suspend fun icon(app: LaunchableApp): Bitmap? = null
            override fun launch(app: LaunchableApp) {
                launched = app
            }
            override fun info(app: LaunchableApp) {}
        }
        val vm = AppsViewModel(source)
        lateinit var chrome: ChromeState
        compose.setContent {
            SidekitPreviewFrame {
                chrome = LocalChrome.current!!
                AppsScreen(vm) {}
            }
        }
        compose.onRoot().captureRoboImage("/tmp/sidekit-apps.png")
        compose.onNodeWithText("Firefox").performClick()
        assertEquals(app, launched)
        compose.onNodeWithText("Firefox").performTouchInput { longClick() }
        compose.runOnIdle { chrome.toggleOptions() }
        // Pinning from the Apps screen adds a direct home entry (no Sideport), appending it to the
        // shared home order that Arrange apps and the derived dock already use.
        compose.onNodeWithText("Pin to home").assertExists().performClick()
        compose.runOnIdle { assertTrue(app.homeId(context) in HomePreferences(context).homeOrder) }
        // Re-open Options so the bar picks up the new pin state.
        compose.runOnIdle { chrome.closeOptions() }
        compose.runOnIdle { chrome.toggleOptions() }
        compose.onNodeWithText("Unpin from home").assertExists()
        compose.onNodeWithText("App info").assertExists()
        compose.onNodeWithText("Uninstall").assertExists()

        compose.onNodeWithText("Unpin from home").performClick()
        compose.runOnIdle { assertFalse(app.homeId(context) in HomePreferences(context).homeOrder) }
    }
}
