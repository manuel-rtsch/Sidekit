package hrln.interactive.sidekit.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import hrln.interactive.sidekit.design.action.ChromeState
import hrln.interactive.sidekit.design.action.LocalChrome
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.SidekitScaffold
import hrln.interactive.sidekit.design.icons.SidekitIcon
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Bounds checks for the leading/trailing zones against the real action bar. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActionBarZonesTest {
    @get:Rule val compose = createComposeRule()

    private object KeypadInputMode : InputModeManager {
        override val inputMode = InputMode.Keyboard
        override fun requestInputMode(inputMode: InputMode) = true
    }

    @Test
    fun optionsOpenedByTheOptionsKeyFocusTheFirstOption() {
        lateinit var chrome: ChromeState
        compose.setContent {
            CompositionLocalProvider(LocalInputModeManager provides KeypadInputMode) {
                SidekitPreviewFrame {
                    chrome = LocalChrome.current!!
                    SidekitScaffold(
                        title = "Zones",
                        options = listOf(
                            square("Queue", "QueueMusic"),
                            square("Sleep timer", "Bedtime")
                        )
                    ) {}
                }
            }
        }
        settle()

        compose.runOnUiThread { chrome.toggleOptions(focusBar = true) }
        settle()

        compose.onNodeWithContentDescription("Queue").assertIsFocused()
    }

    @Test
    fun allSquareOptionsKeepCloseAtTheRightEdge() {
        lateinit var chrome: ChromeState
        compose.setContent {
            SidekitPreviewFrame {
                chrome = LocalChrome.current!!
                SidekitScaffold(
                    title = "Zones",
                    options = listOf(
                        square("Queue", "QueueMusic"),
                        square("Sleep timer", "Bedtime")
                    )
                ) {}
            }
        }
        settle()
        compose.runOnUiThread { chrome.toggleOptions() }
        settle()

        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val close = compose.onNodeWithContentDescription("Close options").fetchSemanticsNode().boundsInRoot
        val queue = compose.onNodeWithContentDescription("Queue").fetchSemanticsNode().boundsInRoot

        assertTrue("close should be in the right half", close.left > root.center.x)
        assertTrue("close should hug the right edge", close.right > root.right - close.width)
        assertTrue("options should stay left of close", queue.right < close.left)
    }

    @Test
    fun optionsCloseComesBeforeAScreenTrailingAction() {
        lateinit var chrome: ChromeState
        compose.setContent {
            SidekitPreviewFrame {
                chrome = LocalChrome.current!!
                SidekitScaffold(
                    title = "Zones",
                    options = listOf(square("Queue", "QueueMusic")),
                    trailingActions = listOf(clear())
                ) {}
            }
        }
        settle()
        compose.runOnUiThread { chrome.toggleOptions() }
        settle()

        val close = compose.onNodeWithContentDescription("Close options").fetchSemanticsNode().boundsInRoot
        val clear = compose.onNodeWithContentDescription("Clear").fetchSemanticsNode().boundsInRoot

        assertTrue("close should come before the screen trailing action", clear.left >= close.right)
        assertTrue("the trailing action should hug the right edge", clear.right > close.right)
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
    }

    private fun square(label: String, icon: String) = SideAction.square(
        label,
        icon = SidekitIcon.valueOf(icon),
        contentDescription = label,
        onClick = {}
    )

    private fun clear() = SideAction.square(
        "clear",
        icon = SidekitIcon.Close,
        contentDescription = "Clear",
        onClick = {}
    )
}
