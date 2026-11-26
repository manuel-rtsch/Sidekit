package hrln.interactive.sidekit.design.input

import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent

/**
 * Tells the attached keypad module from the keys it sends.
 *
 * Each swappable module appears as its own input device: `gxa535_media`, `gxa535_qwerty`,
 * `gxa535_t9` and `gxa535_gamepad`. Older firmware exposed everything as `gxa535_keyboard`.
 * `gxa535_phone` is the phone's own key device: the T9 slides are wired to it on some firmware,
 * which is why it is read from its digits but never trusted wholesale. Measured with `getevent`:
 *
 * - T9: digits, `*`, `#` (plus D-pad, OK = `KEY_ENTER`, left soft key = Home, right = Delete).
 * - Mini QWERTY: letters, one per key (`KEY_Q` for QW...), Space, Ctrl / Alt / Shift.
 * - Sundial: centre = Play/Pause, ring = Up / Next / Down / Previous, corners = Left / Right /
 *   Tab / Enter. The ring reports eight separate keys; it has no rotation axis.
 * - Game controller: gamepad buttons.
 *
 * Keys shared between modules (D-pad, Enter, call keys, Delete) say nothing about a module, which
 * is why [NAMES] is asked first: a device that names itself answers on the D-pad too.
 */
internal object KeypadDetector {
    /**
     * The input devices that name the module they carry. Reading the name is what makes the D-pad
     * an answer: the keys every module shares can only be attributed to the device that sends them.
     * `gxa535_keyboard` is deliberately absent - older firmware used that one name for every
     * module, so it can only be read from the keys.
     */
    private val NAMES = mapOf(
        "gxa535_t9" to KeypadModule.T9,
        "gxa535_qwerty" to KeypadModule.Qwerty,
        "gxa535_media" to KeypadModule.Sundial,
        "gxa535_gamepad" to KeypadModule.GamePad
    )

    /**
     * The input devices that carry a swappable module. An allowlist, not a `gxa535_*` prefix:
     * a future device should be added deliberately rather than trusted by name alone.
     */
    private val MODULE_DEVICES = NAMES.keys + "gxa535_keyboard"

    /** The phone's own key device. It carries the T9 slides, but also keys no module owns. */
    private const val PHONE_DEVICE = "gxa535_phone"

    /** The module [deviceName] names, or null for a device that has to be read from its keys. */
    fun moduleNamedBy(deviceName: String?): KeypadModule? = NAMES[deviceName]

    /** The module attached right now, read from the input devices instead of a key press. */
    fun attachedModule(): KeypadModule? = InputDevice.getDeviceIds()
        .asSequence()
        .mapNotNull { id -> runCatching { InputDevice.getDevice(id)?.name }.getOrNull() }
        .firstNotNullOfOrNull(::moduleNamedBy)

    /** True for a keypad module, the virtual keyboard, or an event with no readable device. */
    internal fun isKeypadDevice(name: String?, deviceId: Int): Boolean = name == null || name in MODULE_DEVICES || deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD

    /**
     * True for a press from the keypad — a swappable module, the phone's own key device, or an
     * injected / remapped key — rather than an external keyboard or a headset button, which send
     * some of the same keys and must keep their own behaviour.
     */
    internal fun isKeypadEvent(name: String?, deviceId: Int): Boolean = deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD || name == PHONE_DEVICE || isKeypadDevice(name, deviceId)

    fun moduleFor(event: KeyEvent): KeypadModule? = moduleFor(event.device?.name, event.deviceId, event.keyCode)

    /**
     * The module a key from [deviceName] reveals, or null when it reveals nothing.
     *
     * Kept free of the event so the whole table can be tested without an input device.
     */
    fun moduleFor(deviceName: String?, deviceId: Int, code: Int): KeypadModule? {
        // A module that names itself is read from the name, so its D-pad and Enter - the keys every
        // module shares - identify it just as well as the buttons only it has.
        moduleNamedBy(deviceName)?.let { return it }

        // The phone's own keys also carry the T9 slides. Only its digits identify a module: no
        // other module types one, and the D-pad, call and Home keys it shares must stay silent.
        if (deviceName == PHONE_DEVICE) return if (isDigit(code)) KeypadModule.T9 else null

        // Headset buttons send Play/Pause too: only trust the keypad (or injected / remapped keys,
        // which come from the virtual device).
        if (!isKeypadDevice(deviceName, deviceId)) return null

        return when {
            code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z || code == KeyEvent.KEYCODE_SPACE ||
                code == KeyEvent.KEYCODE_SHIFT_LEFT || code == KeyEvent.KEYCODE_ALT_LEFT ||
                code == KeyEvent.KEYCODE_CTRL_LEFT -> KeypadModule.Qwerty

            code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_MEDIA_NEXT ||
                code == KeyEvent.KEYCODE_MEDIA_PREVIOUS || code == KeyEvent.KEYCODE_TAB -> KeypadModule.Sundial

            isGamepadButton(code) -> KeypadModule.GamePad

            isDigit(code) -> KeypadModule.T9

            else -> null
        }
    }

    /**
     * `KeyEvent.isGamepadButton`, spelled out because that static helper needs an Android runtime,
     * and the table above is worth testing without one. The list is AOSP's: the face, shoulder,
     * stick and start/select buttons, plus the numbered extras.
     */
    private fun isGamepadButton(code: Int): Boolean = when (code) {
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_C,
        KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_Z,
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_MODE ->
            true

        else -> code in KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16
    }

    /** The keys only a module with digits has. */
    private fun isDigit(code: Int): Boolean = code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ||
        code == KeyEvent.KEYCODE_STAR || code == KeyEvent.KEYCODE_POUND
}
