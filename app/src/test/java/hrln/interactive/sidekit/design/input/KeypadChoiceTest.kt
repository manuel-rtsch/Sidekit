package hrln.interactive.sidekit.design.input

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A module chosen in a UI outranks detection from the same keypad, because the keys that reveal a
 * module - the D-pad above all - are also the keys the user presses right after choosing.
 */
@RunWith(AndroidJUnit4::class)
class KeypadChoiceTest {
    private fun keypad() = Keypad.forTesting(
        CoroutineScope(Dispatchers.Unconfined),
        words = T9Words.parse("")
    )

    private fun press(code: Int) = KeyEvent(
        0L,
        0L,
        KeyEvent.ACTION_DOWN,
        code,
        0,
        0,
        1,
        0
    )

    @Test
    fun theChoiceSurvivesTheKeysOfTheSameKeypad() {
        val keypad = keypad()
        keypad.observe(press(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(KeypadModule.GamePad, keypad.module)

        // The welcome page steps to the next module to teach it; the controller is still attached.
        keypad.chooseModule(KeypadModule.Qwerty)
        assertEquals(KeypadModule.Qwerty, keypad.module)

        // Navigating the page presses the D-pad, which names the controller again.
        keypad.observe(press(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(KeypadModule.Qwerty, keypad.module)
    }

    @Test
    fun aDifferentKeypadHasTheLastWord() {
        val keypad = keypad()
        keypad.observe(press(KeyEvent.KEYCODE_BUTTON_A))
        keypad.chooseModule(KeypadModule.Qwerty)

        // Swapping to the T9 slide types a digit, so the choice no longer describes the hardware.
        keypad.observe(press(KeyEvent.KEYCODE_5))
        assertEquals(KeypadModule.T9, keypad.module)
    }

    @Test
    fun detectionStillSwitchesWithNoChoiceToKeep() {
        val keypad = keypad()
        keypad.observe(press(KeyEvent.KEYCODE_5))
        assertEquals(KeypadModule.T9, keypad.module)

        keypad.observe(press(KeyEvent.KEYCODE_Q))
        assertEquals(KeypadModule.Qwerty, keypad.module)
    }
}
