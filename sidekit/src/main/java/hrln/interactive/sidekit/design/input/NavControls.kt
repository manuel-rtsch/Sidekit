// The key constants read as navigation keys in code; ktlint's property-naming rule would force
// SCREAMING_SNAKE_CASE for these compile-time constants.
@file:Suppress("ktlint:standard:property-naming")

package hrln.interactive.sidekit.design.input

import android.view.KeyEvent

/**
 * The navigation Sidekit's own screens move with, no matter which module is attached.
 *
 * Compose's focus system already moves on the D-pad and activates a focused control on Enter, and
 * Android treats [Back] ([KeyEvent.KEYCODE_BACK]) as "go back" everywhere. T9 and the Sundial send
 * exactly these keys natively, so they don't need help. The game controller and mini QWERTY send a
 * different set of keys (gamepad buttons, modifier keys), so [Keypad.translateForUi] rewrites
 * their native navigation keys to these canonical ones before anything else sees them. Back and
 * Options here mirror [SystemKeys].
 *
 * These are key codes, not a separate input layer: keeping them as real KeyEvents lets the rest of
 * Sidekit (T9Session, SidekitTextField, focus) keep working unchanged on the already-canonical
 * modules.
 */
object NavControls {
    const val Up = KeyEvent.KEYCODE_DPAD_UP
    const val Down = KeyEvent.KEYCODE_DPAD_DOWN
    const val Left = KeyEvent.KEYCODE_DPAD_LEFT
    const val Right = KeyEvent.KEYCODE_DPAD_RIGHT

    /** Select / open the focused control. [KeyEvent.KEYCODE_ENTER] is what T9 calls OK and ListItems act on. */
    const val Confirm = KeyEvent.KEYCODE_ENTER
    const val Back = KeyEvent.KEYCODE_BACK
    const val Options = KeyEvent.KEYCODE_ENDCALL

    /** Universal chrome actions the host handles (not focus movement). */
    const val Home = KeyEvent.KEYCODE_HOME
    const val Search = KeyEvent.KEYCODE_SEARCH
    const val PlayingNow = KeyEvent.KEYCODE_MUSIC

    /** Every canonical key, for contract tests. */
    val all = listOf(Up, Down, Left, Right, Confirm, Back, Options, Home, Search, PlayingNow)

    /**
     * How each module's native keys map to canonical navigation, active while the module is
     * auto-detected and no text field owns the keypad (see [Keypad.translateForUi]).
     *
     * - T9 and the Sundial already send the canonical keys, so they map nothing.
     * - The game controller's D-pad is canonical too; A confirms and B goes back. X opens
     *   Options, Start goes Home, Y opens Playing Now, and Search is the top-right key. On the
     *   Sidephone's 5x3 tile that key reports as Select, not as a shoulder; the shoulder codes
     *   stay mapped for controller revisions that really have them.
     * - Mini QWERTY: its bottom row is the modifier keys (SUGGEST / SYM / CASE = Ctrl / Alt /
     *   Shift) and its only dedicated directional control is a single up-down rocker (code 113),
     *   so navigation uses a cross of letter keys measured on-device: **T up, D left, B down,
     *   J right**, with the **centre G key (the printed 5 / GH key) as Confirm**, as on a D-pad.
     *   These are letters while a text field is focused and navigation keys when not (the
     *   session guard in [Keypad.translateForUi] chooses which). The dedicated Enter key is
     *   Confirm too. On a screen with a type-to-search field (the launcher, Apps, Phonebook, …)
     *   they stay letters, so every letter can start a search; there the rocker still moves up
     *   and down.
     */
    val defaultFor: Map<KeypadModule, Map<Int, Int>> = mapOf(
        KeypadModule.T9 to emptyMap(),
        KeypadModule.Sundial to emptyMap(),
        KeypadModule.GamePad to mapOf(
            KeyEvent.KEYCODE_BUTTON_A to Confirm,
            KeyEvent.KEYCODE_BUTTON_B to Back,
            KeyEvent.KEYCODE_BUTTON_X to Options,
            // The tile's small top-right key arrives as Select (BTN_SELECT); a real generic
            // controller reports it as L1/R1 instead. All three mean Search here.
            KeyEvent.KEYCODE_BUTTON_SELECT to Search,
            KeyEvent.KEYCODE_BUTTON_START to Home,
            KeyEvent.KEYCODE_BUTTON_L1 to Search,
            KeyEvent.KEYCODE_BUTTON_R1 to Search,
            KeyEvent.KEYCODE_BUTTON_Y to PlayingNow
        ),
        KeypadModule.Qwerty to mapOf(
            // Measured on-device: T / D / B / J around the printed 5 / GH key form a directional
            // cross, so that centre key confirms like the centre of a D-pad.
            KeyEvent.KEYCODE_T to Up,
            KeyEvent.KEYCODE_D to Left,
            KeyEvent.KEYCODE_B to Down,
            KeyEvent.KEYCODE_J to Right,
            KeyEvent.KEYCODE_G to Confirm
        )
    )

    /**
     * Hold actions for the Sundial's ring. A tap keeps the printed role (media, Tab, Enter); a
     * hold turns it into the navigation action the module has no dedicated key for. Tab/Enter have
     * no media role to preserve.
     *
     * Previous/Next are deliberately absent. Their tap is the media command, and the Sundial's
     * corner arrows already move left and right; taking the edge keys' hold away only made those
     * media controls feel like navigation. The arrows themselves stay plain as well: a hold defers
     * the press to its release and swallows key repeat, so holding ↓ through a long list would stop
     * scrolling and jump Home instead.
     *
     * There is therefore no spare hold on the Sundial, and it is the one module that cannot reach
     * Playing Now from its own keys.
     */
    val sundialHoldFor: Map<Int, Int> = mapOf(
        KeyEvent.KEYCODE_TAB to Back,
        KeyEvent.KEYCODE_ENTER to Options,
        // Some Sundial revisions report the bottom-right Enter key as the centre media key.
        // A hold still gives Confirm, while a tap keeps its media behaviour.
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to Confirm
    )

    /**
     * The holds T9 and the mini QWERTY have left, and the two chrome actions they carry.
     *
     * Neither module has a Search or a Playing Now key, and Home and OK/Enter are the only
     * non-repeating keys without a hold: every other key either types — and its hold already types
     * the printed digit or symbol — or moves with key repeat. A tap keeps the printed role (Home
     * stays the system Home key, OK/Enter keep selecting and committing), so the hold is the second
     * layer.
     *
     * The pair is the controller's clustering carried over: Search belongs on the chrome corner
     * next to Home/Start, and Playing Now on the confirm key beside the controller's Y / A face
     * buttons. While a text field owns the keypad both holds stand down, so OK/Enter keep their
     * typing role.
     */
    private val homeAndConfirmHoldFor: Map<Int, Int> = mapOf(
        KeyEvent.KEYCODE_HOME to Search,
        KeyEvent.KEYCODE_ENTER to PlayingNow
    )

    /**
     * Hold actions of every module, by the key the module sends.
     *
     * Holds are defaults like [defaultFor]: a custom mapping replaces them, and they only apply to
     * the keypad (a Bluetooth keyboard's Home/Enter keep their platform behaviour).
     *
     * The game controller has none. Y and Select already open Playing Now and Search on a single
     * press, and the Sundial's ring has no spare hold (see [sundialHoldFor]).
     */
    val holdFor: Map<KeypadModule, Map<Int, Int>> = mapOf(
        KeypadModule.T9 to homeAndConfirmHoldFor,
        KeypadModule.Qwerty to homeAndConfirmHoldFor,
        KeypadModule.Sundial to sundialHoldFor,
        KeypadModule.GamePad to emptyMap()
    )

    /** The canonical navigation key [code] becomes in [module]'s UI, or null to keep it as sent. */
    internal fun applyDefault(module: KeypadModule, code: Int): Int? = defaultFor[module]?.get(code)
}
