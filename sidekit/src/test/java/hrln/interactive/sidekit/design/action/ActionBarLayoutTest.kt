package hrln.interactive.sidekit.design.action

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionBarLayoutTest {
    @Test
    fun `leftover width becomes the gap before the trailing zone`() {
        val layout = layout(
            slot("leading", square = true, placement = ActionPlacement.Leading),
            slot("trailing", square = true, placement = ActionPlacement.Trailing)
        )

        assertEquals(100f, layout.widths[0], 0.01f)
        assertEquals(100f, layout.widths[1], 0.01f)
        assertEquals(0f, layout.gaps[0], 0.01f)
        assertEquals(800f, layout.gaps[1], 0.01f)
    }

    @Test
    fun `leftover width stays after a leading-only square bar`() {
        val layout = layout(
            slot("one", square = true, placement = ActionPlacement.Leading),
            slot("two", square = true, placement = ActionPlacement.Leading)
        )

        assertEquals(10f, layout.gaps[1], 0.01f)
    }

    @Test
    fun `a trailing-only bar is right aligned`() {
        val layout = layout(slot("trailing", square = true, placement = ActionPlacement.Trailing))

        assertEquals(100f, layout.widths[0], 0.01f)
        assertEquals(900f, layout.gaps[0], 0.01f)
    }

    @Test
    fun `several trailing actions stay at the right edge`() {
        val layout = layout(
            slot("leading", square = true, placement = ActionPlacement.Leading),
            slot("one", square = true, placement = ActionPlacement.Trailing),
            slot("two", square = true, placement = ActionPlacement.Trailing)
        )

        assertEquals(690f, layout.gaps[1], 0.01f)
        assertEquals(10f, layout.gaps[2], 0.01f)
    }

    @Test
    fun `a flexible leading action fills the space before a trailing action`() {
        val layout = layout(
            slot("leading", square = false, placement = ActionPlacement.Leading),
            slot("trailing", square = true, placement = ActionPlacement.Trailing)
        )

        assertEquals(890f, layout.widths[0], 0.01f)
        assertEquals(100f, layout.widths[1], 0.01f)
        assertEquals(10f, layout.gaps[1], 0.01f)
    }

    private fun layout(vararg slots: ActionSlot): Result {
        val widths = FloatArray(slots.size)
        val gaps = FloatArray(slots.size)
        layoutSlots(slots.toList(), slots.size, WIDTH, HEIGHT, GAP, widths, gaps, settled = true)
        return Result(widths, gaps)
    }

    private fun slot(key: String, square: Boolean, placement: ActionPlacement) = ActionSlot(SideAction(key, label = key, square = square, onClick = {}), present = true, initialPlacement = placement)

    private data class Result(val widths: FloatArray, val gaps: FloatArray)

    private companion object {
        const val WIDTH = 1000
        const val HEIGHT = 100
        const val GAP = 10f
    }
}
