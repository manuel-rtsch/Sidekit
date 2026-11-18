package hrln.interactive.sidekit.sdk.input

import android.annotation.SuppressLint
import android.view.KeyCharacterMap
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
 * 3. give the Sundial's ring keys their holds (Tab for Back, Enter for Options, Previous/Next for
 *    left/right) while a tap still sends the key's own action;
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
    private val onSearch: () -> Unit = {}
) {
    /** The Sundial has no Back/Options key: hold Tab / Enter, and hold Previous/Next for left/right. */
    private val sundialHold = HoldToAct(onHold = { action, source -> performSundialHold(action, source) })

    /** Tap Options opens the options; holding it jumps to the first action button. */
    private val optionsHold = HoldToAct(
        onHold = { _, _ ->
            keypad.finishTyping()
            chrome.focusActionBar()
        }
    )

    /** Forgets a hold whose key never got its release (the window lost focus). */
    fun reset() {
        sundialHold.reset()
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
        // Holds belong to the Sundial ring only: Tab/Enter on a Bluetooth keyboard or Next on a headset
        // keep their normal behaviour, as does a T9 OK if the remembered module is stale.
        val fromSundial = raw.device?.name in SUNDIAL_DEVICES || raw.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD
        when (val hold = sundialHold.handle(translated) { code -> if (fromSundial) sundialHoldAction(code) else null }) {
            HoldToAct.Result.Pass -> Unit

            HoldToAct.Result.Consume -> return true

            is HoldToAct.Result.Deliver -> {
                hold.events.forEach(::dispatchTranslated)
                return true
            }
        }
        when (val hold = optionsHold.handle(translated) { code -> if (SystemKeys.isOptions(code)) code else null }) {
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
     * Performs a Sundial hold after the timer fires or the key is released late.
     *
     * Back comes from holding a hardware key, not from a system back gesture, so the
     * predictive-back lint does not apply; the dispatcher still routes through registered
     * callbacks.
     */
    @SuppressLint("GestureBackNavigation")
    private fun performSundialHold(action: Int, source: KeyEvent) {
        when (action) {
            KeyEvent.KEYCODE_BACK -> onBack()

            KeyEvent.KEYCODE_ENDCALL -> {
                keypad.finishTyping()
                chrome.toggleOptions()
            }

            NavControls.Home -> onHome()

            NavControls.Search -> onSearch()

            else -> dispatchNav(action, source)
        }
    }

    /** The Sundial keys that get a hold action, and only while its layout is auto-detected. */
    private fun sundialHoldAction(code: Int): Int? {
        if (keypad.module != KeypadModule.Sundial || keypad.mapper.customModule != null) return null
        return NavControls.sundialHoldFor[code]
    }

    /** Sends a held Sundial key's navigation action as a normal press/release. */
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

    /** Routes an already-translated event; also used for the tap released by [sundialHold]. */
    private fun dispatchTranslated(event: KeyEvent): Boolean {
        // Generic chrome actions from the controller. A normal keyboard's Home/Search keys keep
        // their platform behaviour.
        if (keypad.module == KeypadModule.GamePad &&
            (event.keyCode == NavControls.Home || event.keyCode == NavControls.Search)
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (event.keyCode == NavControls.Home) onHome() else onSearch()
            }
            return true
        }
        // The controller's Y button jumps straight to the first action button.
        if (keypad.module == KeypadModule.GamePad && event.keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                keypad.finishTyping()
                chrome.focusActionBar()
            }
            return true
        }
        when {
            // Keypad top-right: the universal Options key. Acts on release; the press is consumed
            // so the system's end-call handling never sees it.
            SystemKeys.isOptions(event) -> {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                    keypad.finishTyping()
                    chrome.toggleOptions()
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

    private companion object {
        /** Input devices that carry the Sundial ring (newer media module, older combined keyboard). */
        val SUNDIAL_DEVICES = setOf("gxa535_media", "gxa535_keyboard")
    }
}
