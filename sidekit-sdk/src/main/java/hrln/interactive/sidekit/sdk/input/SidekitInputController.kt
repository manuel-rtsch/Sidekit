package hrln.interactive.sidekit.sdk.input

import android.view.KeyEvent
import hrln.interactive.sidekit.design.action.ChromeState
import hrln.interactive.sidekit.design.input.Keypad
import hrln.interactive.sidekit.design.input.SystemKeys

/**
 * The activity-level key loop every Sidekit host shares.
 *
 * The order matters and is the same on Sidekit and a Sidekit-native app:
 *
 * 1. detect the keypad module from the raw event, before navigation rewrites it;
 * 2. apply the custom mapping and each module's default navigation, so Options, Back, focus and
 *    typing all see the mapped key;
 * 3. treat the top-right key as Options (on release) and swallow the top-left CALL key, which the
 *    keypad accessibility service turns into Back before the system dialer starts;
 * 4. send typing keys to [Keypad.dispatch] and only then to the platform.
 */
class SidekitInputController(
    val keypad: Keypad,
    val chrome: ChromeState
) {
    /**
     * Handles a raw key event. Returns the event the platform should still dispatch, or null when
     * Sidekit consumed the press.
     */
    fun handle(raw: KeyEvent): KeyEvent? {
        // Detect the module from the raw key before navigation rewrites it, so a first QWERTY press
        // on a letter that is also a navigator (T/D/B/J) still reveals the module.
        keypad.observe(raw)
        val event = keypad.translateForUi(raw) ?: return null
        when {
            // Keypad top-right: the universal Options key. Acts on release; the press is consumed
            // so the system's end-call handling never sees it.
            SystemKeys.isOptions(event) -> {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                    keypad.finishTyping()
                    chrome.toggleOptions()
                }
                return null
            }

            // Keypad top-left (CALL) is turned into Back by the keypad accessibility service before
            // the system can open the dialer. Without the service only its release reaches us,
            // after the dialer has launched: swallow it so nothing happens twice.
            SystemKeys.isBack(event) -> return null
        }
        return if (keypad.dispatch(event)) null else event
    }
}
