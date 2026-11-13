package hrln.interactive.sidekit.sdk.input

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
 * 3. treat the top-right key as Options: a tap opens the options, a hold moves focus to the first
 *    action button; swallow the top-left CALL key, which the keypad accessibility service turns
 *    into Back before the system dialer starts;
 * 4. send typing keys to [Keypad.dispatch] and only then to the platform.
 */
class SidekitInputController(
    val keypad: Keypad,
    val chrome: ChromeState,
    private val onHome: () -> Unit = {},
    private val onSearch: () -> Unit = {}
) {
    /** Tap Options opens the options; holding it jumps to the first action button. */
    private val optionsHold = HoldToAct(
        onHold = { _, _ ->
            keypad.finishTyping()
            chrome.focusActionBar()
        }
    )

    /** Forgets a hold whose key never got its release (for example, the window lost focus). */
    fun reset() = optionsHold.reset()

    /**
     * Handles a raw key event. Returns the event the platform should still dispatch, or null when
     * Sidekit consumed the press.
     */
    fun handle(raw: KeyEvent): KeyEvent? = translate(raw)?.let(::handleTranslated)

    /** Detection and custom/default mapping, without running the action loop. */
    fun translate(raw: KeyEvent): KeyEvent? {
        keypad.observe(raw)
        return keypad.translateForUi(raw)
    }

    /** Handles an event that has already been through [translate]. */
    fun handleTranslated(event: KeyEvent): KeyEvent? {
        // Generic chrome actions from the controller. A normal keyboard's Home/Search keys keep
        // their platform behaviour.
        if (keypad.module == KeypadModule.GamePad &&
            (event.keyCode == NavControls.Home || event.keyCode == NavControls.Search)
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (event.keyCode == NavControls.Home) onHome() else onSearch()
            }
            return null
        }
        // The game controller's Y button jumps straight to the first action button.
        if (keypad.module == KeypadModule.GamePad && event.keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                keypad.finishTyping()
                chrome.focusActionBar()
            }
            return null
        }
        when (val hold = optionsHold.handle(event) { code -> if (SystemKeys.isOptions(code)) code else null }) {
            HoldToAct.Result.Pass -> Unit

            HoldToAct.Result.Consume -> return null

            is HoldToAct.Result.Deliver -> {
                // A short tap: the down was swallowed, the release is the Options action.
                hold.events.firstOrNull { it.action == KeyEvent.ACTION_UP }?.let {
                    if (!it.isCanceled) {
                        keypad.finishTyping()
                        chrome.toggleOptions()
                    }
                }
                return null
            }
        }
        // Keypad top-left (CALL) is turned into Back by the keypad accessibility service before
        // the system can open the dialer. Without the service only its release reaches us, after
        // the dialer has launched: swallow it so nothing happens twice.
        if (SystemKeys.isBack(event)) return null
        return if (keypad.dispatch(event)) null else event
    }
}
