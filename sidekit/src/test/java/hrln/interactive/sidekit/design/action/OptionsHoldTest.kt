package hrln.interactive.sidekit.design.action

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Sundial's held Enter: when the current screen has options it opens them, otherwise it hands
 * the hold to the main action bar. One gesture always lands focus in some bar.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [31])
class OptionsHoldTest {
    @Test
    fun `openOptionsOrFocusBar opens the options when the screen has any`() {
        val chrome = ChromeState()
        chrome.publishers += ChromePublisher().apply {
            options = listOf(SideAction("opt", label = "Option", onClick = {}))
        }

        assertFalse(chrome.optionsOpen)
        chrome.openOptionsOrFocusBar()
        assertTrue(chrome.optionsOpen)
    }

    @Test
    fun `openOptionsOrFocusBar focuses the action bar when the screen has none`() {
        val chrome = ChromeState()
        chrome.publishers += ChromePublisher().apply {
            actions = listOf(SideAction("main", label = "Main", onClick = {}))
        }

        var focused = 0
        chrome.registerActionBarFocus {
            focused++
            true
        }

        assertFalse(chrome.hasOptions)
        chrome.openOptionsOrFocusBar()
        assertEquals(1, focused)
        assertFalse(chrome.optionsOpen)
    }
}
