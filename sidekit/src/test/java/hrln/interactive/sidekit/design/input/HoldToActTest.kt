package hrln.interactive.sidekit.design.input

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The hold layer of the keypad: a press waits for the timer or the release, and a key nobody claims
 * keeps its tap.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [31])
class HoldToActTest {
    private class FakeScheduler : HoldToAct.Scheduler {
        private var action: (() -> Unit)? = null

        override fun schedule(delayMs: Long, action: () -> Unit): HoldToAct.Cancellable {
            this.action = action
            return HoldToAct.Cancellable { this.action = null }
        }

        fun elapse() {
            val fire = action ?: error("nothing scheduled")
            action = null
            fire()
        }
    }

    private val scheduler = FakeScheduler()
    private val held = mutableListOf<Int>()
    private val hold = HoldToAct(holdMs = 400, onHold = { action, _ -> held.add(action) }, scheduler = scheduler)

    private fun down(code: Int, at: Long = 1_000, repeat: Int = 0) = KeyEvent(at, at, KeyEvent.ACTION_DOWN, code, repeat)

    private fun up(code: Int, downAt: Long = 1_000, at: Long = 1_100) = KeyEvent(downAt, at, KeyEvent.ACTION_UP, code, 0)

    /** T9: OK holds Playing Now, every digit holds nothing. */
    private fun actionOf(code: Int): Int? = if (code == KeyEvent.KEYCODE_ENTER) NavControls.PlayingNow else null

    @Test
    fun `the hold fires while the key is still down`() {
        assertEquals(HoldToAct.Result.Consume, hold.handle(down(KeyEvent.KEYCODE_ENTER), ::actionOf))
        scheduler.elapse()
        assertEquals(listOf(NavControls.PlayingNow), held)
        // The release of a hold that already fired is swallowed, so it cannot also tap.
        assertEquals(HoldToAct.Result.Consume, hold.handle(up(KeyEvent.KEYCODE_ENTER, at = 1_500), ::actionOf))
        assertEquals(listOf(NavControls.PlayingNow), held)
    }

    @Test
    fun `a tap is delivered as a press and its release`() {
        assertEquals(HoldToAct.Result.Consume, hold.handle(down(KeyEvent.KEYCODE_ENTER), ::actionOf))
        val delivered = hold.handle(up(KeyEvent.KEYCODE_ENTER), ::actionOf) as HoldToAct.Result.Deliver
        assertEquals(
            listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP),
            delivered.events.map { it.action }
        )
        assertTrue(held.isEmpty())
    }

    @Test
    fun `a key that belongs to typing keeps its tap`() {
        assertEquals(HoldToAct.Result.Pass, hold.handle(down(KeyEvent.KEYCODE_5), ::actionOf))
        assertEquals(HoldToAct.Result.Pass, hold.handle(up(KeyEvent.KEYCODE_5), ::actionOf))
        assertTrue(held.isEmpty())
    }

    /**
     * The bug this pins: holding OK in a text field ends the word and leaves the field, so the
     * keypad has no session by the release. The release then looked like a hold nobody had made,
     * and Playing Now opened as the key was lifted.
     */
    @Test
    fun `a decline is not undone by the release`() {
        // A text field owns the keypad during the press: no hold action.
        assertEquals(HoldToAct.Result.Pass, hold.handle(down(KeyEvent.KEYCODE_ENTER), { null }))
        // The field's own hold ran and left; by the release the key would hold again.
        assertEquals(HoldToAct.Result.Pass, hold.handle(up(KeyEvent.KEYCODE_ENTER, at = 1_500), ::actionOf))
        assertTrue("a declined press must never become a hold", held.isEmpty())
    }

    @Test
    fun `a press lost to touch mode still recovers its hold`() {
        // Only the release arrives; its downTime says it was held.
        assertEquals(HoldToAct.Result.Consume, hold.handle(up(KeyEvent.KEYCODE_ENTER, at = 1_500), ::actionOf))
        assertEquals(listOf(NavControls.PlayingNow), held)
    }

    @Test
    fun `pressing another key drops the pending tap`() {
        hold.handle(down(KeyEvent.KEYCODE_ENTER), ::actionOf)
        assertEquals(HoldToAct.Result.Pass, hold.handle(down(KeyEvent.KEYCODE_5), ::actionOf))
        // The dropped press's release is not a tap: it never reaches the screen.
        assertEquals(HoldToAct.Result.Consume, hold.handle(up(KeyEvent.KEYCODE_ENTER), ::actionOf))
        assertTrue(held.isEmpty())
    }
}
