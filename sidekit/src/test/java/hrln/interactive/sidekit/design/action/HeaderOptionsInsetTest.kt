package hrln.interactive.sidekit.design.action

import androidx.compose.ui.unit.dp
import hrln.interactive.sidekit.design.theme.SidekitSpacing
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The space the header's touch Options button takes out of the top band. It is a real reservation
 * while the button is on screen and nothing at all when the keypad owns the phone, so a screen's
 * header gets its full width back rather than leaving a gap for a button it cannot reach.
 */
class HeaderOptionsInsetTest {
    @Test
    fun `the button reserves its hit target and its gap to the panel edge`() {
        // 30 itemPadding + 56 hit target - 10 pagePadding the content area already gave up.
        assertEquals(76.dp, headerOptionsInset(touchMode = true, hasOptions = true, SidekitSpacing()))
    }

    @Test
    fun `keypad mode reserves nothing`() {
        assertEquals(0.dp, headerOptionsInset(touchMode = false, hasOptions = true, SidekitSpacing()))
    }

    @Test
    fun `a screen without options reserves nothing`() {
        assertEquals(0.dp, headerOptionsInset(touchMode = true, hasOptions = false, SidekitSpacing()))
    }

    @Test
    fun `the reservation follows the spacing tokens`() {
        val spacing = SidekitSpacing(itemPadding = 20.dp, pagePadding = 5.dp)

        assertEquals(71.dp, headerOptionsInset(touchMode = true, hasOptions = true, spacing))
    }
}
