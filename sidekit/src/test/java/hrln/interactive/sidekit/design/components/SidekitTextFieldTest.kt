package hrln.interactive.sidekit.design.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import hrln.interactive.sidekit.design.input.Keypad
import hrln.interactive.sidekit.design.input.KeypadModule
import hrln.interactive.sidekit.design.input.LocalKeypad
import hrln.interactive.sidekit.design.input.T9Words
import hrln.interactive.sidekit.design.theme.SidekitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The field's contract beyond plain typing: a masked field must not publish its secret, and the
 * action key must run the action the caller asked for instead of always meaning Done.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [31])
class SidekitTextFieldTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a masked field is a password field to the platform`() {
        compose.setContent {
            SidekitTheme {
                SidekitTextField(
                    rememberTextFieldState("hunter2"),
                    masked = true,
                    keyboardType = KeyboardType.Password,
                    singleLine = true
                )
            }
        }
        compose.waitForIdle()
        // Android masks a node flagged as a password over accessibility services; the flag is what
        // keeps the secret out of the node description.
        compose.onNode(hasSetTextAction()).assert(isPasswordField())
    }

    @Test
    fun `an unmasked field is not a password field`() {
        compose.setContent {
            SidekitTheme { SidekitTextField(rememberTextFieldState("bowie"), singleLine = true) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("bowie").assertExists()
        compose.onNode(hasSetTextAction()).assert(!isPasswordField())
    }

    @Test
    fun `a Next action runs onNext and leaves onDone alone`() {
        var next = 0
        var done = 0
        compose.setContent {
            SidekitTheme {
                SidekitTextField(
                    rememberTextFieldState(""),
                    singleLine = true,
                    imeAction = ImeAction.Next,
                    onNext = { next++ },
                    onDone = { done++ }
                )
            }
        }
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        assertEquals(1, next)
        assertEquals(0, done)
    }

    @Test
    fun `without an ime action the field still means Done`() {
        var done = 0
        compose.setContent {
            SidekitTheme {
                SidekitTextField(rememberTextFieldState(""), singleLine = true, onDone = { done++ })
            }
        }
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        assertEquals(1, done)
    }

    @Test
    fun `Next without a target falls back to Done`() {
        var done = 0
        compose.setContent {
            SidekitTheme {
                SidekitTextField(
                    rememberTextFieldState(""),
                    singleLine = true,
                    imeAction = ImeAction.Next,
                    onDone = { done++ }
                )
            }
        }
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        assertEquals(1, done)
    }

    @Test
    fun `up on a single line field moves focus out`() {
        showField("hello", KeypadModule.T9)
        compose.onNode(hasSetTextAction()).requestFocus()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        compose.onNodeWithTag("above").assertIsFocused()
    }

    @Test
    fun `down on a single line field moves focus out`() {
        showField("hello", KeypadModule.T9)
        compose.onNode(hasSetTextAction()).requestFocus()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.onNodeWithTag("below").assertIsFocused()
    }

    @Test
    fun `the qwerty suggestion rocker leaves when there is nothing to complete`() {
        showField("", KeypadModule.Qwerty)
        compose.onNode(hasSetTextAction()).requestFocus()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.CtrlLeft) }
        compose.waitForIdle()
        compose.onNodeWithTag("below").assertIsFocused()
    }

    /** A one-line field between two focus stops, with [module] attached. */
    private fun showField(text: String, module: KeypadModule) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val keypad = remember { Keypad.forTesting(scope, T9Words.parse("hello\nhelp"), module) }
            val state = rememberTextFieldState(text)
            CompositionLocalProvider(LocalKeypad provides keypad) {
                SidekitTheme {
                    Column {
                        FocusStop("above")
                        SidekitTextField(state, singleLine = true)
                        FocusStop("below")
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun FocusStop(tag: String) {
        Box(Modifier.size(60.dp).focusable().testTag(tag))
    }

    private fun isPasswordField() = SemanticsMatcher("is a password field") { node ->
        node.config.getOrNull(SemanticsProperties.Password) != null
    }
}
