package hrln.interactive.sidekit.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.canvas.SidekitCanvas
import hrln.interactive.sidekit.design.components.SidekitScaffold
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.theme.SidekitSpacing
import hrln.interactive.sidekit.design.theme.SidekitTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActionBarWrapScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { SidekitPreviewFrame(content) }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun longLabelsWrap() = shoot("action_labels_wrap") {
        SidekitScaffold(
            title = "Wrap test",
            actions = listOf(
                SideAction.forward("a", "Open notification settings", onClick = {}),
                SideAction("b", "Mark as unread") {}
            )
        ) {}
    }

    /** A short wrapped label must stay beside its icon, not spread to the button's edges. */
    @Test fun shortWrappedLabelKeepsIconClose() {
        compose.setContent {
            SidekitPreviewFrame {
                SidekitScaffold(
                    title = "Leading icon",
                    actions = listOf(
                        SideAction("a", "Open settings", SidekitIcon.Settings) {},
                        SideAction("b", "New playlist", SidekitIcon.Add) {}
                    )
                ) {}
            }
        }
        compose.waitForIdle()
        val button = compose.onNodeWithContentDescription("Open settings").fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText("Open settings").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "the wrapped label should report its tight text width, not the whole column",
            label.right <= button.right - 20f
        )
        compose.onRoot().captureRoboImage("screenshots/action_labels_wrap_icon.png")
    }

    /** A word must not be split across the two rows; the label shrinks slightly instead. */
    @Test fun wrappedWordsStayWhole() = shoot("action_labels_wrap_words") {
        SidekitScaffold(
            title = "Two short words",
            actions = listOf(
                SideAction("a", "Rearrange order", SidekitIcon.Settings) {},
                SideAction("b", "Clear cache") {}
            )
        ) {}
    }

    /** Two lines must still fit the shorter compact bar (100 dp). */
    @Test fun compactBarFitsTwoLines() {
        compose.setContent {
            SidekitTheme(spacing = SidekitSpacing.Compact) {
                SidekitCanvas {
                    SidekitScaffold(
                        title = "Compact wrap",
                        actions = listOf(
                            SideAction("a", "Open settings", SidekitIcon.Settings) {},
                            SideAction("b", "Mark as unread") {}
                        )
                    ) {}
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/action_labels_wrap_compact.png")
    }

    @Test fun denseLabelsWrap() = shoot("action_labels_wrap_dense") {
        SidekitScaffold(
            title = "Dense wrap test",
            actions = listOf(
                SideAction("a", "Inbox cache refresh failed, retry later", dense = true) {},
                SideAction("b", "Dismiss", dense = true) {}
            )
        ) {}
    }
}
