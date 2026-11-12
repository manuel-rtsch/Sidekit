package hrln.interactive.sidekit.sdk.system

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import hrln.interactive.sidekit.design.input.SystemKeys

/**
 * Makes the keypad's top-left key (it reports CALL) a system-wide Back key.
 *
 * On the Sidephone the window manager launches the dialer on CALL before any app receives the key
 * ("CALL key: launching dialer"), so an app can't remap it from its activity. An accessibility
 * service that filters key events sees keys before that happens. This one filters only that key:
 * a short press performs Back (in Sidekit or any other app), holding it opens the dialer.
 *
 * A host can register [KeypadSystemBack.delegate] to handle the press first (Sideport does this
 * while a projection is showing). Otherwise the service performs the global Back action.
 *
 * It requests no accessibility events and can't read window content, so it costs nothing while
 * idle. Consumers declare it in their own manifest; enable it in Settings > Accessibility.
 */
open class KeypadAccessibilityService : AccessibilityService() {

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!SystemKeys.isBack(event)) return false
        if (event.action == KeyEvent.ACTION_UP) {
            if (event.eventTime - event.downTime >= SystemKeys.LONG_PRESS_MS) {
                openDialer()
            } else if (KeypadSystemBack.delegate?.invoke() != true) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
        // Consume the press too, or the system still starts the dialer on key down.
        return true
    }

    private fun openDialer() {
        try {
            startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // No dialer installed.
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
