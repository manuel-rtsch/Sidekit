package hrln.interactive.sidekit.sdk.settings

import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.input.KeypadModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The guide's rows are the module as printed on the hardware: the caps carry the glyph or legend
 * the physical key shows, and [KeypadGuideKey.label] stays the translated name for the detail card.
 */
class KeypadGuideRowsTest {
    @Test
    fun `T9 prints its glyph keys and the word on the centre key`() {
        val rows = keypadGuideRows(KeypadModule.T9)

        val topLeft = rows[0][0]!!
        assertEquals(SidekitIcon.Call, topLeft.icon)
        assertEquals("Back", topLeft.printed)

        // The one cap above the numpad that really prints a word.
        assertEquals("OK", rows[1][2]!!.printed)
        assertNull(rows[1][2]!!.icon)

        assertEquals("5", rows[4][2]!!.printed)
    }

    @Test
    fun `gamepad prints only glyphs on the d-pad and the letters on the face buttons`() {
        val rows = keypadGuideRows(KeypadModule.GamePad)
        val caps = rows.map { row -> row.map { it?.printed } }

        // Up, Search and Home show a glyph and no word.
        assertNull(caps[0][1])
        assertNull(caps[0][4])
        assertNull(caps[4][0])
        assertEquals(SidekitIcon.Search, rows[0][4]!!.icon)

        // The face buttons print what is on the shell.
        assertEquals("Y", rows[2][3]!!.printed)
        assertEquals("X", rows[3][2]!!.printed)
        assertEquals("B", rows[3][4]!!.printed)
        assertEquals("A", rows[4][3]!!.printed)
        assertEquals("Playing now", rows[2][3]!!.label)
    }

    @Test
    fun `mini qwerty folds its chrome into the five keys on top`() {
        val rows = keypadGuideRows(KeypadModule.Qwerty)
        val top = rows.first()

        assertEquals(5, top.size)
        assertEquals(SidekitIcon.Call, top[0]!!.icon)
        assertEquals("\u2013", top[1]!!.printed)
        assertEquals(SidekitIcon.Home, top[2]!!.icon)
        assertEquals("Recents", top[3]!!.label)
        assertEquals("\u2013", top[3]!!.printed)
        assertEquals(SidekitIcon.CallEnd, top[4]!!.icon)

        // One bottom row of five, and the letters still print their pair.
        val bottom = rows.last()
        assertEquals(5, bottom.size)
        assertEquals("\u2195", bottom[0]!!.printed)
        assertEquals("Space", bottom[2]!!.printed)
        assertEquals(5, rows.size)
        assertEquals("QW", rows[1][0]!!.printed)
        assertEquals("Delete", rows[3][4]!!.label)
    }

    @Test
    fun `t9 and the mini qwerty carry search and playing now on their free holds`() {
        for (module in listOf(KeypadModule.T9, KeypadModule.Qwerty)) {
            val keys = keypadGuideRows(module).flatten().filterNotNull()
            assertEquals("Open search.", keys.first { it.label == "Home" }.hold)
            // OK on T9, Enter on the mini QWERTY: both keep their tap and hold Playing Now.
            assertEquals("Open the playing now screen.", keys.first { it.label == "OK" || it.label == "Enter" }.hold)
        }
    }

    @Test
    fun `sundial prints the words on its ring`() {
        val rows = keypadGuideRows(KeypadModule.Sundial)
        assertEquals("Play / pause", rows[1][1]!!.printed)
        assertEquals("Tab", rows[2][0]!!.printed)
        assertEquals("\u2193", rows[2][1]!!.printed)
    }
}
