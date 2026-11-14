package hrln.interactive.sidekit.design.action

import org.junit.Assert.assertEquals
import org.junit.Test

class OptionsBarTest {
    @Test
    fun `only options that keep the bar open leave it open`() {
        val publisher = ChromePublisher()
        publisher.options = listOf(
            SideAction("shuffle", label = "Shuffle", keepOptionsOpen = true, onClick = {}),
            SideAction("repeat", label = "Repeat", onClick = {})
        )
        var closes = 0
        val bar = publisher.optionsBar { closes++ }

        bar.first { it.key == "options.shuffle" }.onClick()

        assertEquals(0, closes)

        bar.first { it.key == "options.repeat" }.onClick()

        assertEquals(1, closes)
    }

    @Test
    fun `square options forward keepOptionsOpen`() {
        val action = SideAction.square(
            "shuffle",
            icon = hrln.interactive.sidekit.design.icons.SidekitIcon.ArrowRightAlt,
            contentDescription = "Shuffle",
            keepOptionsOpen = true,
            onClick = {}
        )

        assertEquals(true, action.keepOptionsOpen)
    }
}
