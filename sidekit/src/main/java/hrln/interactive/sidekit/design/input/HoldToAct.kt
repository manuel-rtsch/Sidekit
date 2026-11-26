package hrln.interactive.sidekit.design.input

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/**
 * Gives a key a hold action without changing what a tap does.
 *
 * The press is held back until it is released or [holdMs] passes. A timer fires the hold while the
 * key is still down; a tap is delivered as a normal press/release pair, so a hold never also fires
 * the key's tap action. Used for the Sundial, whose ring has no Back or Options key: hold Tab for
 * Back and Enter for Options while a tap still sends the key's own action, and for the typed
 * modules, where OK / Enter holds Playing Now.
 *
 * A key that is not a hold key is passed through whole: its press goes to typing, and its release
 * follows even if the keypad has changed owners by then (a text field's own OK hold leaves the
 * field on the press, and the release must not become a hold the press never was).
 */
class HoldToAct(
    private val holdMs: Long = KeypadTiming.LONG_PRESS_MS,
    private val onHold: (action: Int, source: KeyEvent) -> Unit = { _, _ -> },
    private val scheduler: Scheduler = MainLooperScheduler()
) {
    sealed interface Result {
        /** Not one of ours: deliver the event normally. */
        data object Pass : Result

        /** Swallow the event. */
        data object Consume : Result

        /** Deliver these synthetic events through the normal key path. */
        data class Deliver(val events: List<KeyEvent>) : Result
    }

    fun interface Cancellable {
        fun cancel()
    }

    fun interface Scheduler {
        fun schedule(delayMs: Long, action: () -> Unit): Cancellable
    }

    private var pending: KeyEvent? = null
    private var fired = false
    private var timer: Cancellable? = null

    /**
     * The last key whose press was passed straight through because it was nobody's hold.
     *
     * Its release must pass through too. A key that belongs to typing can lose that status between
     * the press and the release — a text field's own hold on OK finishes the word and leaves the
     * field, so by the release the keypad has no session any more — and the release must not then
     * be read as a hold the press never was.
     */
    private var passed: Int? = null

    /** Forgets a press that never got its release (the window lost focus). */
    fun reset() {
        clear()
        passed = null
    }

    /**
     * [holdAction] returns the action key code for a held key, or null to leave that key alone.
     * Only one key can be pending; pressing another key drops the pending tap.
     */
    fun handle(event: KeyEvent, holdAction: (Int) -> Int?): Result {
        val action = holdAction(event.keyCode)
        if (action == null) {
            // Pressing another key drops the pending tap; releasing one (a rolled press) must not.
            if (event.action == KeyEvent.ACTION_DOWN) {
                clear()
                passed = event.keyCode
            }
            return Result.Pass
        }
        if (event.isCanceled) {
            clear()
            return Result.Consume
        }
        // The press was declined (it went to typing); its release goes with it, whatever the
        // keypad looks like now.
        if (event.keyCode == passed) {
            if (event.action == KeyEvent.ACTION_UP) passed = null
            return Result.Pass
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    clear()
                    passed = null
                    pending = event
                    timer = scheduler.schedule(holdMs) { fire(action, event) }
                    return Result.Consume
                }
                // The keypad doesn't send repeats, but if one arrives after the threshold and the
                // timer hasn't run yet, fire now.
                val down = pending
                if (!fired && down != null && event.eventTime - down.downTime >= holdMs) {
                    timer?.cancel()
                    timer = null
                    fire(action, down)
                }
                return Result.Consume
            }

            KeyEvent.ACTION_UP -> {
                val down = pending
                // The first key after a touch can lose its down to Android's touch-mode
                // transition. The release still carries the original downTime, so a hold can be
                // recovered from it instead of being swallowed as a stray release.
                if (down == null) {
                    if (event.eventTime - event.downTime >= holdMs) onHold(action, event)
                    return Result.Consume
                }
                // The release of a hold key that isn't the pending one (its press was dropped):
                // swallow it and leave the pending key's timer running.
                if (down.keyCode != event.keyCode) return Result.Consume
                timer?.cancel()
                timer = null
                if (fired) {
                    pending = null
                    fired = false
                    return Result.Consume
                }
                // Releasing after the threshold without a timer still counts as a hold.
                if (event.eventTime - down.downTime >= holdMs) {
                    fire(action, down)
                    pending = null
                    fired = false
                    return Result.Consume
                }
                pending = null
                val up = KeyEvent(
                    down.downTime, event.eventTime, KeyEvent.ACTION_UP, down.keyCode,
                    event.repeatCount, event.metaState, event.deviceId, event.scanCode,
                    event.flags, event.source
                )
                return Result.Deliver(listOf(down, up))
            }

            else -> return Result.Pass
        }
    }

    private fun fire(action: Int, source: KeyEvent) {
        if (pending !== source || fired) return
        fired = true
        timer = null
        onHold(action, source)
    }

    private fun clear() {
        timer?.cancel()
        timer = null
        pending = null
        fired = false
    }

    private class MainLooperScheduler : Scheduler {
        private val handler = Handler(Looper.getMainLooper())
        override fun schedule(delayMs: Long, action: () -> Unit): Cancellable {
            val runnable = Runnable { action() }
            handler.postDelayed(runnable, delayMs)
            return Cancellable { handler.removeCallbacks(runnable) }
        }
    }
}
