package hrln.interactive.sidekit.sdk.input

import android.annotation.SuppressLint
import android.view.KeyEvent
import hrln.interactive.sidekit.design.action.ChromeState
import hrln.interactive.sidekit.design.input.HoldToAct
import hrln.interactive.sidekit.design.input.Keypad
import hrln.interactive.sidekit.design.input.KeypadModule
import hrln.interactive.sidekit.design.input.NavControls
import hrln.interactive.sidekit.design.input.SystemKeys

/**
 * The activity-level key loop every Sidekit host shares.
 *
 * The order matters and is the same on Sidekit and a Sidekit-native app:
 *
 * 1. detect the keypad module from the raw event, before navigation rewrites it;
 * 2. apply the custom mapping and each module's default navigation, so Options, Back, focus and
 *    typing all see the mapped key;
 * 3. give each module's hold keys their hold (Tab for Back and Enter for Options on the Sundial,
 *    its corner arrows for Search and Playing Now, Home for Search and OK/Enter for Playing Now on
 *    T9 and the mini QWERTY) while a tap still sends the key's own action; every hold defers the
 *    tap, so a held corner's Search/Playing Now never competes with a repeated left/right move;
 * 4. treat the top-right key as Options (on release) and swallow the top-left CALL key, which the
 *    keypad accessibility service turns into Back before the system dialer starts;
 * 5. send typing keys to [Keypad.dispatch] and only then to the platform.
 *
 * [dispatch] is the activity's own `super.dispatchKeyEvent`, kept as a lambda so a hold that fires
 * from a timer can still deliver its synthetic press/release pair. [onBack] performs a Back from
 * the activity, which goes through its `OnBackPressedDispatcher`.
 */
class SidekitInputController(
    val keypad: Keypad,
    val chrome: ChromeState,
    private val dispatch: (KeyEvent) -> Boolean,
    private val onBack: () -> Unit,
    private val onHome: () -> Unit = {},
    private val onSearch: () -> Unit = {},
    private val onPlayingNow: () -> Unit = {}
) {
    /**
     * The second layer of a module that has no dedicated key for a chrome action: the Sundial's
     * Back and Options, T9's and the mini QWERTY's Search and Playing Now. Taps keep their role.
     */
    private val holds = HoldToAct(onHold = { action, source -> performHold(action, source) })

    /** Tap Options opens the options; holding it jumps to the first action button. */
    private val optionsHold = HoldToAct(
        onHold = { _, _ ->
            keypad.finishTyping()
            chrome.focusActionBar()
        }
    )

    /** Forgets a hold whose key never got its release (the window lost focus). */
    fun reset() {
        holds.reset()
        optionsHold.reset()
    }

    /**
     * Handles a raw key event. Returns true when Sidekit consumed it, false to let the platform
     * dispatch it.
     */
    fun handle(raw: KeyEvent): Boolean {
        // Detect the module from the raw key before navigation rewrites it, so a first QWERTY press
        // on a letter that is also a navigator (T/D/B/J) still reveals the module.
        keypad.observe(raw)
        // Keypad mapping first, plus each module's default navigation, so Options, Back, focus and
        // typing all see the mapped key.
        val translated = keypad.translateForUi(raw) ?: return true
        // Holds apply to the keypad only: Tab/Enter/Home on a Bluetooth keyboard or Next on a headset
        // keep their normal behaviour, a custom mapping replaces them, and a focused text field
        // keeps its OK/Enter.
        when (
            val hold = holds.handle(translated) { code ->
                // A screen's own hold (the library's held arrows) beats the module's chrome hold.
                if (chrome.isHoldReserved(code)) null else keypad.holdAction(code, raw)
            }
        ) {
            HoldToAct.Result.Pass -> Unit

            HoldToAct.Result.Consume -> return true

            is HoldToAct.Result.Deliver -> {
                hold.events.forEach(::dispatchTranslated)
                return true
            }
        }
        when (
            val hold = optionsHold.handle(translated) { code -> if (SystemKeys.isOptions(code)) code else null }
        ) {
            HoldToAct.Result.Pass -> Unit

            HoldToAct.Result.Consume -> return true

            is HoldToAct.Result.Deliver -> {
                hold.events.forEach(::dispatchTranslated)
                return true
            }
        }
        return dispatchTranslated(translated)
    }

    /**
     * Performs a hold after the timer fires or the key is released late.
     *
     * Back comes from holding a hardware key, not from a system back gesture, so the
     * predictive-back lint does not apply; the dispatcher still routes through registered
     * callbacks.
     */
    @SuppressLint("GestureBackNavigation")
    private fun performHold(action: Int, source: KeyEvent) {
        when (action) {
            NavControls.Back -> onBack()

            NavControls.Options -> {
                keypad.finishTyping()
                chrome.openOptionsOrFocusBar()
            }

            NavControls.Home -> onHome()

            NavControls.Search -> onSearch()

            NavControls.PlayingNow -> {
                keypad.finishTyping()
                onPlayingNow()
            }

            else -> dispatchNav(action, source)
        }
    }

    /** Sends a held key's navigation action (the Sundial's Confirm) as a normal press/release. */
    private fun dispatchNav(code: Int, from: KeyEvent) {
        val down = KeyEvent(
            from.downTime, from.eventTime, KeyEvent.ACTION_DOWN, code, 0,
            from.metaState, from.deviceId, from.scanCode, from.flags, from.source
        )
        val up = KeyEvent(
            from.downTime, from.eventTime + 1, KeyEvent.ACTION_UP, code, 0,
            from.metaState, from.deviceId, from.scanCode, from.flags, from.source
        )
        dispatchTranslated(down)
        dispatchTranslated(up)
    }

    /** Routes an already-translated event; also used for the tap released by [holds]. */
    private fun dispatchTranslated(event: KeyEvent): Boolean {
        // Generic chrome actions from the controller. A normal keyboard's Home/Search/PlayingNow keys keep
        // their platform behaviour.
        if (keypad.module == KeypadModule.GamePad &&
            (event.keyCode == NavControls.Home || event.keyCode == NavControls.Search || event.keyCode == NavControls.PlayingNow)
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                when (event.keyCode) {
                    NavControls.Home -> onHome()

                    NavControls.Search -> onSearch()

                    NavControls.PlayingNow -> {
                        keypad.finishTyping()
                        onPlayingNow()
                    }
                }
            }
            return true
        }
        // Y reaches PlayingNow through defaultFor, so it needs no case of its own here.
        when {
            // Keypad top-right: the universal Options key. Acts on release; the press is consumed
            // so the system's end-call handling never sees it.
            SystemKeys.isOptions(event) -> {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                    keypad.finishTyping()
                    chrome.toggleOptions(focusBar = true)
                }
                return true
            }

            // Keypad top-left (CALL) is turned into Back by the keypad accessibility service before
            // the system can open the dialer. Without the service only its release reaches us, after
            // the dialer has launched: swallow it so nothing happens twice.
            SystemKeys.isBack(event) -> return true
        }
        return keypad.dispatch(event) || dispatch(event)
    }
}
