package hrln.interactive.sidekit.design.action

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionsBarTest {
    @Test
    fun `a screen can reserve a key's hold until its last owner disposes`() {
        val chrome = ChromeState()
        assertFalse(chrome.isHoldReserved(KeyEvent.KEYCODE_DPAD_LEFT))

        val first = chrome.reserveHold(KeyEvent.KEYCODE_DPAD_LEFT)
        val second = chrome.reserveHold(KeyEvent.KEYCODE_DPAD_LEFT)
        assertTrue(chrome.isHoldReserved(KeyEvent.KEYCODE_DPAD_LEFT))

        first()
        first() // releasing twice must not steal the second owner's claim
        assertTrue(chrome.isHoldReserved(KeyEvent.KEYCODE_DPAD_LEFT))
        second()
        assertFalse(chrome.isHoldReserved(KeyEvent.KEYCODE_DPAD_LEFT))
    }

    @Test
    fun `only options that keep the bar open leave it open`() {
        val publisher = ChromePublisher()
        publisher.options = listOf(
            SideAction("shuffle", label = "Shuffle", keepOptionsOpen = true, onClick = {}),
            SideAction("repeat", label = "Repeat", onClick = {})
        )
        var closes = 0
        val bar = publisher.optionsBar { closes++ }

        bar.leading.first { it.key == "options.shuffle" }.onClick()

        assertEquals(0, closes)

        bar.leading.first { it.key == "options.repeat" }.onClick()

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

    @Test
    fun `close belongs to the trailing zone`() {
        val publisher = ChromePublisher()
        publisher.options = listOf(
            SideAction.square(
                "queue",
                icon = hrln.interactive.sidekit.design.icons.SidekitIcon.QueueMusic,
                contentDescription = "Queue",
                onClick = {}
            )
        )

        val bar = publisher.optionsBar {}

        assertEquals(listOf("options.queue"), bar.leading.map { it.key })
        assertEquals(listOf("options.close"), bar.trailing.map { it.key })
    }

    @Test
    fun `a screen trailing action becomes the trailing zone`() {
        val chrome = ChromeState()
        val publisher = ChromePublisher()
        publisher.actions = listOf(SideAction("main", label = "Main", onClick = {}))
        publisher.trailingActions = listOf(
            SideAction.square(
                "clear",
                icon = hrln.interactive.sidekit.design.icons.SidekitIcon.Close,
                contentDescription = "Clear",
                onClick = {}
            )
        )
        chrome.publishers += publisher

        val bar = chrome.bar

        assertEquals(listOf("main"), bar.leading.map { it.key })
        assertEquals(listOf("clear"), bar.trailing.map { it.key })
    }
}
