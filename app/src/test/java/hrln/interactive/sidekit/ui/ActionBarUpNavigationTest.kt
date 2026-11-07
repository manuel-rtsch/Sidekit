package hrln.interactive.sidekit.ui

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import hrln.interactive.sidekit.design.action.ChromeState
import hrln.interactive.sidekit.design.action.PublishChrome
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.canvas.SidekitCanvas
import hrln.interactive.sidekit.design.components.SidekitItem
import hrln.interactive.sidekit.design.components.SidekitList
import hrln.interactive.sidekit.design.components.SidekitScaffold
import hrln.interactive.sidekit.design.components.SidekitText
import hrln.interactive.sidekit.design.theme.SidekitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Up from the action bar must return to the content. D-pad focus search is geometric, and the bar
 * is drawn over the content, so a scrolled list leaves nothing for it to find. The scaffold and the
 * bar hand Up back explicitly; these tests pin both the restoring and the fallback path.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActionBarUpNavigationTest {
    @get:Rule val compose = createComposeRule()

    private object KeypadInputMode : InputModeManager {
        override val inputMode = InputMode.Keyboard
        override fun requestInputMode(inputMode: InputMode) = true
    }

    private fun launchSwitchingScreens() {
        compose.setContent {
            CompositionLocalProvider(LocalInputModeManager provides KeypadInputMode) {
                SidekitTheme {
                    SidekitCanvas(chrome = remember { ChromeState() }) {
                        var screen by remember { mutableIntStateOf(0) }
                        // Two separate screens: pressing the action disposes the first scaffold
                        // entirely, exactly like a navigation.
                        if (screen == 0) {
                            SidekitScaffold(
                                title = "Screen A",
                                actions = listOf(SideAction("next", "Next", onClick = { screen = 1 })),
                            ) { padding ->
                                SidekitList(contentPadding = padding) {
                                    items(6) { i -> SidekitItem("A $i", onClick = {}) }
                                }
                            }
                        } else {
                            SidekitScaffold(
                                title = "Screen B",
                                actions = listOf(SideAction("next", "Next", onClick = {})),
                            ) { padding ->
                                SidekitList(contentPadding = padding) {
                                    items(6) { i -> SidekitItem("B $i", onClick = {}) }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1000)
    }

    private var listState: LazyListState? = null

    private fun launchScrolledList() {
        compose.setContent {
            CompositionLocalProvider(LocalInputModeManager provides KeypadInputMode) {
                SidekitTheme {
                    SidekitCanvas(chrome = remember { ChromeState() }) {
                        SidekitScaffold(
                            title = "Rows",
                            actions = listOf(SideAction("alpha", "Alpha", onClick = {})),
                        ) { padding ->
                            val state = remember { LazyListState() }
                            androidx.compose.runtime.SideEffect { listState = state }
                            SidekitList(contentPadding = padding, state = state) {
                                items(8) { i -> SidekitItem("Row $i", onClick = {}) }
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1000)
    }

    private fun key(code: Int) {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            compose.onRoot().performKeyPress(androidx.compose.ui.input.key.KeyEvent(KeyEvent(action, code)))
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(200)
    }

    private fun focusedLabel(): String? = compose.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()?.let { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
            ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
    }

    @Test fun upReturnsToTheRowThatHadFocusInAScrolledList() {
        launchScrolledList()
        compose.onNodeWithText("Row 0").requestFocus()
        compose.waitForIdle()

        var lastRow: String? = null
        var presses = 0
        while (focusedLabel() != "Alpha" && presses++ < 16) {
            focusedLabel()?.takeIf { it.startsWith("Row ") }?.let { lastRow = it }
            key(KeyEvent.KEYCODE_DPAD_DOWN)
        }

        assertEquals("Alpha", focusedLabel())
        assertNotNull("never reached a content row", lastRow)
        val state = listState!!
        assertTrue(
            "test must exercise a scrolled list",
            state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0,
        )

        key(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals(lastRow, focusedLabel())
    }

    @Test fun upWorksOnScreensThatPublishChromeWithoutAScaffold() {
        compose.setContent {
            CompositionLocalProvider(LocalInputModeManager provides KeypadInputMode) {
                SidekitTheme {
                    SidekitCanvas(chrome = remember { ChromeState() }) {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxWidth().height(120.dp).testTag("body").focusable()) {
                                SidekitText("Body")
                            }
                            Spacer(Modifier.weight(1f))
                        }
                        PublishChrome(header = null, actions = listOf(SideAction("alpha", "Alpha", onClick = {})))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1000)

        compose.onNodeWithTag("body").requestFocus()
        compose.waitForIdle()
        compose.onNodeWithTag("body").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_LEFT) // saves the focused body child

        compose.onNodeWithContentDescription("Alpha").requestFocus()
        compose.waitForIdle()
        assertEquals("Alpha", focusedLabel())

        key(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("body").assertIsFocused()
    }

    @Test fun upAfterAnActionReplacesTheContentEntersTheNewContent() {
        launchSwitchingScreens()
        compose.onNodeWithText("A 0").requestFocus()
        compose.waitForIdle()
        var presses = 0
        while (focusedLabel() != "Next" && presses++ < 12) key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("Next", focusedLabel())

        compose.onNodeWithText("Next").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Screen B").assertExists()
        compose.onNodeWithText("B 0").assertExists()


        key(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals("B 0", focusedLabel())
    }
}
