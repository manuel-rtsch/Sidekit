package hrln.interactive.sidekit.apps.settings

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import hrln.interactive.sidekit.design.canvas.SidekitCanvas
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.home.HomePreferences
import hrln.interactive.sidekit.navigation.SubApp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Settings → Arrange apps smoke test (rendering). The edit-buffer logic is unit-tested directly
 * in [hrln.interactive.sidekit.home.HomeArrangerTest]; manipulating the chrome action bar through
 * compose clicks is unreliable, so the interesting behaviour lives at the state-holder level.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArrangeHomeTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun cleanPrefs() {
        HomePreferences(context).reset()
    }

    @Test
    fun `renders every app with the four pinned dock apps marked In dock`() {
        compose.setContent {
            SidekitTheme {
                SidekitCanvas {
                    ArrangeHomeScreen(back = {})
                }
            }
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("In dock").assertCountEquals(HomePreferences.DOCK_MAX)
        SubApp.dock.forEach { compose.onNodeWithText(it.label).assertIsDisplayed() }
        // Rows are each an independent click target beside their move arrows, not hidden under a merged tile.
        compose.onNodeWithContentDescription("Move Photos up").assertExists()
        compose.onNodeWithText("Photos").assertExists()
    }
}