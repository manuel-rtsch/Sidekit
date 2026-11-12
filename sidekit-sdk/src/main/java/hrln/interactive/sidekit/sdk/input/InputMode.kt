package hrln.interactive.sidekit.sdk.input

import hrln.interactive.sidekit.design.input.Keypad
import hrln.interactive.sidekit.design.input.T9Buffer
import hrln.interactive.sidekit.design.input.T9Mode

/**
 * Applies the user's starting input mode to [this] keypad.
 *
 * Sidekit calls this from `onResume` so the mode chosen in Settings or onboarding is active as
 * soon as the activity is in front. A Sidekit-native app calls it with the host's `input.mode`,
 * or its own, depending on where its configuration comes from.
 */
fun Keypad.applyInputMode(mode: T9Mode) {
    finishTyping()
    engine.setMode(
        mode,
        object : T9Buffer {
            override val text: CharSequence = ""
            override val selectionStart = 0
            override val selectionEnd = 0
            override fun replace(start: Int, end: Int, text: String, selectStart: Int, selectEnd: Int) {}
        }
    )
}
