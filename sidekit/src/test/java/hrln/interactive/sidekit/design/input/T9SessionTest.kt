package hrln.interactive.sidekit.design.input

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private class SessionBuffer : T9Buffer {
    var value = ""
    override val text: CharSequence get() = value
    override val selectionStart get() = value.length
    override val selectionEnd get() = value.length
    override fun replace(start: Int, end: Int, text: String, selectStart: Int, selectEnd: Int) {
        value = value.substring(0, start) + text + value.substring(end)
    }
}

/** The mini QWERTY's rocker is its only way out of a field: a tap cycles, a hold leaves. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [31])
class T9SessionTest {
    private val words = T9Words.parse("hello\nhelp\nhell")
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun session(escaped: () -> Unit = {}): Pair<T9Session, Keypad> {
        val keypad = Keypad.forTesting(scope, words, KeypadModule.Qwerty)
        val buffer = SessionBuffer()
        // Start a word so the rocker has suggestions to cycle.
        keypad.engine.type("hel", buffer)
        assertTrue(keypad.engine.composing)
        return T9Session(keypad, buffer, scope, onEscape = escaped) to keypad
    }

    private fun down(code: Int, at: Long, repeat: Int = 0) = KeyEvent(at, at, KeyEvent.ACTION_DOWN, code, repeat)

    private fun up(code: Int, at: Long) = KeyEvent(at, at, KeyEvent.ACTION_UP, code, 0)

    @Test
    fun `a tap cycles suggestions and stays`() {
        var escapes = 0
        val (session, keypad) = session { escapes++ }
        val selected = keypad.engine.selected
        assertTrue(session.onKeyEvent(down(QwertyLayout.SUGGEST, 1_000)))
        assertEquals(selected + 1, keypad.engine.selected)
        assertEquals(0, escapes)
    }

    @Test
    fun `a hold finishes the word and asks to leave`() {
        var escapes = 0
        val (session, keypad) = session { escapes++ }
        val start = 1_000L
        session.onKeyEvent(down(QwertyLayout.SUGGEST, start))
        assertTrue(session.onKeyEvent(down(QwertyLayout.SUGGEST, start + keypad.longPressMs + 1, repeat = 2)))
        assertEquals(1, escapes)
        assertFalse(keypad.engine.composing)
        // The rest of the press cannot type or move the new focus.
        assertTrue(session.onKeyEvent(up(QwertyLayout.SUGGEST, start + keypad.longPressMs + 10)))
    }

    @Test
    fun `with nothing composing the tap is left to the field`() {
        val keypad = Keypad.forTesting(scope, words, KeypadModule.Qwerty)
        var escapes = 0
        val session = T9Session(keypad, SessionBuffer(), scope, onEscape = { escapes++ })
        assertFalse(session.onKeyEvent(down(QwertyLayout.SUGGEST, 1_000)))
        assertEquals(0, escapes)
    }
}
