package hrln.interactive.sidekit.design.input

import android.content.res.Resources
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import hrln.interactive.sidekit.design.R

/**
 * One physical key: the input device that sent it and the code it reported. Keys are told apart
 * by key code; a key the system has no key code for ([KeyEvent.KEYCODE_UNKNOWN]) by its scan code.
 *
 * [device] is the input device's name, which stays the same across restarts and reconnects (its
 * id doesn't), so a binding only ever matches the keypad it was learned on: an external keyboard
 * or headset that sends the same code is left alone.
 */
data class PhysicalKey(val device: String, val keyCode: Int, val scanCode: Int = 0) {
    /** Readable name in English, e.g. "F1" or "Scan code 250". */
    val label: String
        get() = if (keyCode == KeyEvent.KEYCODE_UNKNOWN) "Scan code $scanCode" else keyName(keyCode)

    /** Readable name in the app language, e.g. "F1" or "Scan code 250". */
    fun label(resources: Resources): String = if (keyCode == KeyEvent.KEYCODE_UNKNOWN) resources.getString(R.string.sk_sidekit_key_scan_code, scanCode) else keyName(keyCode, resources)

    companion object {
        /** KeyEvent's own constant names, by code (keyCodeToString isn't available everywhere). */
        private val names: Map<Int, String> by lazy {
            KeyEvent::class.java.fields
                .filter { it.name.startsWith("KEYCODE_") && it.type == Int::class.javaPrimitiveType && java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .associate { it.getInt(null) to it.name.removePrefix("KEYCODE_") }
        }

        fun of(event: KeyEvent, device: String): PhysicalKey {
            val known = event.keyCode != KeyEvent.KEYCODE_UNKNOWN
            return PhysicalKey(device, event.keyCode, if (known) 0 else event.scanCode)
        }

        /**
         * "KEYCODE_DPAD_UP" -> "D-pad up", "KEYCODE_F1" -> "F1". The names are Android's own
         * constants, so only a code with no name ("Key 300") is put in [resources]' language.
         */
        fun keyName(code: Int, resources: Resources? = null): String {
            val raw = names[code] ?: return resources?.getString(R.string.sk_sidekit_key_code, code) ?: "Key $code"
            if (raw.length <= 3) return raw
            return raw.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }.replace("Dpad", "D-pad")
        }
    }
}

/**
 * Something a key does in SideKit: one printed key of a [KeypadModule] (or one of the system
 * keys). [keyCode] is the code the Sidephone's own module sends for it, which is what the rest of
 * SideKit (T9 typing, focus, Back, Options) acts on. [label] and [hint] are in the language of the
 * resources [KeypadControls] was given (English without any).
 */
class KeypadControl(val keyCode: Int, val label: String, val hint: String)

/**
 * The controls of each module, in the order they're printed on it. Pass [Resources] (for example
 * `LocalContext.current.resources`) to get the labels and hints in the app language; without them
 * they are English.
 */
object KeypadControls {
    fun of(module: KeypadModule, resources: Resources? = null): List<KeypadControl> {
        val t = Text(resources)
        val system = listOf(
            // Back is the plain Back key here: the Sidephone's top-left key (CALL) only becomes Back
            // through the keypad accessibility service, which keeps working whatever is mapped.
            t.control(KeyEvent.KEYCODE_BACK, R.string.sk_sidekit_key_back, "Back", R.string.sk_sidekit_hint_back, "Go back a screen"),
            t.control(KeyEvent.KEYCODE_ENDCALL, R.string.sk_sidekit_key_options, "Options", R.string.sk_sidekit_hint_options, "Show the screen's extra actions")
        )
        val dpad = listOf(
            t.control(KeyEvent.KEYCODE_DPAD_UP, R.string.sk_sidekit_key_up, "Up", R.string.sk_sidekit_hint_move_up, "Move up"),
            t.control(KeyEvent.KEYCODE_DPAD_DOWN, R.string.sk_sidekit_key_down, "Down", R.string.sk_sidekit_hint_move_down, "Move down"),
            t.control(KeyEvent.KEYCODE_DPAD_LEFT, R.string.sk_sidekit_key_left, "Left", R.string.sk_sidekit_hint_move_left_cursor, "Move left; cursor"),
            t.control(KeyEvent.KEYCODE_DPAD_RIGHT, R.string.sk_sidekit_key_right, "Right", R.string.sk_sidekit_hint_move_right_cursor, "Move right; cursor")
        )
        return when (module) {
            KeypadModule.T9 -> system + dpad + listOf(
                t.printed(KeyEvent.KEYCODE_ENTER, "OK", R.string.sk_sidekit_hint_select_or_suggestion, "Select, or take a suggestion")
            ) + (1..9).map { t.digit(it) } + listOf(
                t.printed(KeyEvent.KEYCODE_STAR, "*", R.string.sk_sidekit_hint_punctuation_symbols, "Punctuation and symbols"),
                t.digit(0),
                t.printed(KeyEvent.KEYCODE_POUND, "#", R.string.sk_sidekit_hint_case_numbers, "Case; hold for numbers"),
                t.control(KeyEvent.KEYCODE_HOME, R.string.sk_sidekit_key_home, "Home", R.string.sk_sidekit_hint_go_home, "Go to the home screen"),
                t.control(KeyEvent.KEYCODE_DEL, R.string.sk_sidekit_key_delete, "Delete", R.string.sk_sidekit_hint_delete, "Delete the previous character")
            )

            KeypadModule.Qwerty -> system + QwertyLayout.letters.map { (code, letters) ->
                val printed = QwertyLayout.printed[code].toString()
                KeypadControl(code, letters.uppercase(), t.get(R.string.sk_sidekit_hint_hold_for, "Hold for %1\$s", printed))
            } + listOf(
                KeypadControl(KeyEvent.KEYCODE_SPACE, t.get(R.string.sk_sidekit_key_space, "Space"), t.get(R.string.sk_sidekit_hint_hold_for, "Hold for %1\$s", "0")),
                t.control(KeyEvent.KEYCODE_DEL, R.string.sk_sidekit_key_delete, "Delete", R.string.sk_sidekit_hint_delete, "Delete the previous character"),
                t.control(KeyEvent.KEYCODE_ENTER, R.string.sk_sidekit_key_enter, "Enter", R.string.sk_sidekit_hint_new_line, "New line, or take a suggestion"),
                t.printed(QwertyLayout.SUGGEST, "↕", R.string.sk_sidekit_hint_next_suggestion, "Next suggestion"),
                t.printed(QwertyLayout.SYM, "SYM", R.string.sk_sidekit_hint_symbols, "Symbols"),
                t.printed(QwertyLayout.CASE, "aA", R.string.sk_sidekit_hint_case_numbers, "Case; hold for numbers")
            )

            KeypadModule.Sundial -> system + listOf(
                t.control(KeyEvent.KEYCODE_DPAD_UP, R.string.sk_sidekit_key_top, "Top", R.string.sk_sidekit_hint_move_up, "Move up"),
                t.control(KeyEvent.KEYCODE_MEDIA_NEXT, R.string.sk_sidekit_key_right_edge, "Right edge", R.string.sk_sidekit_hint_next_track, "Next track"),
                t.control(KeyEvent.KEYCODE_DPAD_DOWN, R.string.sk_sidekit_key_bottom, "Bottom", R.string.sk_sidekit_hint_move_down, "Move down"),
                t.control(KeyEvent.KEYCODE_MEDIA_PREVIOUS, R.string.sk_sidekit_key_left_edge, "Left edge", R.string.sk_sidekit_hint_previous_track, "Previous track"),
                t.control(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, R.string.sk_sidekit_key_centre, "Centre", R.string.sk_sidekit_hint_play_pause, "Play or pause; hold for Confirm"),
                t.control(KeyEvent.KEYCODE_DPAD_LEFT, R.string.sk_sidekit_key_corner_left, "Corner ←", R.string.sk_sidekit_hint_corner_left, "Move left; hold for Options fallback"),
                t.control(KeyEvent.KEYCODE_DPAD_RIGHT, R.string.sk_sidekit_key_corner_right, "Corner →", R.string.sk_sidekit_hint_move_right, "Move right"),
                t.printed(KeyEvent.KEYCODE_TAB, "Tab", R.string.sk_sidekit_hint_tab, "Next control; hold for Back"),
                t.control(KeyEvent.KEYCODE_ENTER, R.string.sk_sidekit_key_enter, "Enter", R.string.sk_sidekit_hint_enter_sundial, "Select; hold for Options (some revisions report Play/Pause)")
            )

            KeypadModule.GamePad -> system + dpad + listOf(
                t.printed(KeyEvent.KEYCODE_BUTTON_A, "A", R.string.sk_sidekit_hint_confirm, "Confirm"),
                t.printed(KeyEvent.KEYCODE_BUTTON_B, "B", R.string.sk_sidekit_hint_back_in_games, "Back in games"),
                t.printed(KeyEvent.KEYCODE_BUTTON_X, "X", R.string.sk_sidekit_hint_button_x, "Options; hold for the action bar"),
                t.printed(KeyEvent.KEYCODE_BUTTON_Y, "Y", R.string.sk_sidekit_hint_button_y, "Jump to the action bar"),
                t.printed(KeyEvent.KEYCODE_BUTTON_SELECT, "Select", R.string.sk_sidekit_hint_options, "Show the screen's extra actions"),
                t.control(KeyEvent.KEYCODE_BUTTON_L1, R.string.sk_sidekit_key_shoulder_left, "Left shoulder", R.string.sk_sidekit_hint_open_search, "Open search"),
                t.control(KeyEvent.KEYCODE_BUTTON_R1, R.string.sk_sidekit_key_shoulder_right, "Right shoulder", R.string.sk_sidekit_hint_open_search, "Open search"),
                t.printed(KeyEvent.KEYCODE_BUTTON_START, "Start", R.string.sk_sidekit_hint_go_home, "Go to the home screen")
            )
        }
    }

    fun find(module: KeypadModule, keyCode: Int, resources: Resources? = null): KeypadControl? = of(module, resources).firstOrNull { it.keyCode == keyCode }

    /** Looks strings up in [resources], or formats the English text when there are none. */
    private class Text(private val resources: Resources?) {
        fun get(@StringRes id: Int, english: String, vararg args: Any): String = resources?.getString(id, *args) ?: if (args.isEmpty()) english else english.format(*args)

        /** A key with a translatable name. */
        fun control(code: Int, @StringRes label: Int, englishLabel: String, @StringRes hint: Int, englishHint: String) = KeypadControl(code, get(label, englishLabel), get(hint, englishHint))

        /** A key named by what is printed on it ("OK", "*", "A"), the same in every language. */
        fun printed(code: Int, label: String, @StringRes hint: Int, englishHint: String) = KeypadControl(code, label, get(hint, englishHint))

        fun digit(n: Int): KeypadControl {
            val letters = listOf("", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz")[n]
            val hint = when (n) {
                0 -> get(R.string.sk_sidekit_hint_digit_0, "Space; hold for 0")
                1 -> get(R.string.sk_sidekit_hint_digit_1, "Punctuation; hold for 1")
                else -> get(R.string.sk_sidekit_hint_digit, "Letters; hold for %1\$d", n)
            }
            return KeypadControl(KeyEvent.KEYCODE_0 + n, if (letters.isEmpty()) "$n" else "$n $letters", hint)
        }
    }
}

/**
 * Custom keypad mapping, for keypads SideKit doesn't recognise and for anyone who wants a
 * Sidephone key somewhere else.
 *
 * Off by default ([customModule] is null): keys arrive untouched and the module is detected from
 * them as before. Turned on, SideKit treats the keypad as [customModule] (detection pauses, so a
 * stray key can't switch it away) and every key bound in that module's profile is rewritten to the
 * control it was bound to ([translate]) before anything else sees it. Keys that aren't bound keep
 * doing what they did, so a half-finished profile never takes a working key away.
 *
 * Each module keeps its own profile, saved across restarts. Every SideKit window with a keypad
 * (the app and the Sideport overlay) shares the saved profiles.
 */
@Stable
class KeyMapper internal constructor(
    private val preferences: KeypadPreferences?,
    private val deviceName: (KeyEvent) -> String = ::defaultDeviceName
) {
    /** The module the custom mapping is for, or null for automatic detection. */
    var customModule: KeypadModule? by mutableStateOf(null)
        private set

    private var profiles: Map<KeypadModule, Map<Int, PhysicalKey>> by mutableStateOf(emptyMap())

    /** Keys held down, and the code each one started with: its repeats and release keep it. */
    private val held = HashMap<PhysicalKey, Int>()
    private var last: Translation? = null
    private var capture: ((PhysicalKey, KeyEvent) -> Boolean)? = null
    private var watcher: ((PhysicalKey, KeyEvent) -> Unit)? = null

    init {
        load()
        preferences?.observe { load() }
    }

    /** Keys bound in [module]'s profile, by the control they were bound to. */
    fun bindings(module: KeypadModule): Map<Int, PhysicalKey> = profiles[module].orEmpty()

    internal fun setCustom(module: KeypadModule?) {
        if (customModule == module) return
        customModule = module
        preferences?.writeCustomModule(module)
    }

    /**
     * Binds [key] to the control [controlCode] of [module]. A key does one thing, so if it was
     * bound to another control that control goes back to its default; that control is returned.
     */
    fun bind(module: KeypadModule, controlCode: Int, key: PhysicalKey): Int? {
        val current = bindings(module)
        val displaced = current.entries.firstOrNull { it.value == key && it.key != controlCode }?.key
        save(module, current.filterValues { it != key } + (controlCode to key))
        return displaced
    }

    /** The control [key] is bound to in [module], if any. */
    fun controlFor(module: KeypadModule, key: PhysicalKey): Int? = bindings(module).entries.firstOrNull { it.value == key }?.key

    fun reset(module: KeypadModule, controlCode: Int) = save(module, bindings(module) - controlCode)

    fun resetAll(module: KeypadModule) = save(module, emptyMap())

    /**
     * Rewrites a key event from the keypad to the control it is mapped to. Returns [event] itself
     * when nothing changes, and null when the press belongs to a key capture and must go no
     * further. Safe to call more than once for the same event as it travels through dispatch.
     */
    fun translate(event: KeyEvent): KeyEvent? {
        // The same event reaches several stages (before the IME, then the activity). The system
        // recycles event objects, so identity alone doesn't prove it's the same press.
        last?.let {
            if (it.matches(event)) return it.out
            // Already translated (handed on by an earlier stage): translating again would map it twice.
            if (event === it.out && event !== it.event) return event
        }
        val out = compute(event)
        last = Translation(event, event.action, event.keyCode, event.downTime, event.eventTime, event.repeatCount, out)
        return out
    }

    /**
     * Sends the next key presses to [onKey] instead of SideKit while it returns true: for learning
     * a key. A captured press is swallowed whole (its repeats and release too). Returns a function
     * that stops capturing.
     */
    fun capture(onKey: (PhysicalKey, KeyEvent) -> Boolean): () -> Unit {
        capture = onKey
        return { if (capture === onKey) capture = null }
    }

    /** Shows every key press (and what it became) to [onKey] without changing it: the key tester. */
    fun watch(onKey: (PhysicalKey, KeyEvent) -> Unit): () -> Unit {
        watcher = onKey
        return { if (watcher === onKey) watcher = null }
    }

    /** The physical key an event came from. */
    fun keyOf(event: KeyEvent): PhysicalKey = PhysicalKey.of(event, deviceName(event))

    private fun compute(event: KeyEvent): KeyEvent? {
        // Virtual keyboards mark their key events; they're never the keypad.
        if (event.flags and KeyEvent.FLAG_SOFT_KEYBOARD != 0) return event
        if (event.action == KeyEvent.ACTION_MULTIPLE) return event
        val key = keyOf(event)
        val code = when (event.action) {
            KeyEvent.ACTION_DOWN -> held[key]?.takeIf { event.repeatCount > 0 } ?: run {
                // A new press: decided now, then kept until its release.
                val taken = capture?.invoke(key, event) == true
                (if (taken) SWALLOWED else mappedCode(key)).also { held[key] = it }
            }

            // A release finishes the press the way it started, even if the mapping changed meanwhile.
            else -> held.remove(key) ?: if (capture != null) SWALLOWED else mappedCode(key)
        }
        if (code == SWALLOWED) return null
        val out = if (code == event.keyCode) event else event.withCode(code)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) watcher?.invoke(key, out)
        return out
    }

    private fun mappedCode(key: PhysicalKey): Int {
        val module = customModule ?: return key.keyCode
        return controlFor(module, key) ?: key.keyCode
    }

    private fun load() {
        val p = preferences ?: return
        customModule = p.customModule()
        profiles = KeypadModule.entries.associateWith { module -> p.profiles(module) }
    }

    private fun save(module: KeypadModule, bindings: Map<Int, PhysicalKey>) {
        profiles = profiles + (module to bindings)
        preferences?.writeProfile(module, bindings)
    }

    companion object {
        private const val SWALLOWED = Int.MIN_VALUE

        private fun defaultDeviceName(event: KeyEvent): String = event.device?.name ?: if (event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD) "Virtual" else ""

        /** A mapper that saves to [preferences] (the keypad's configuration store). */
        fun create(preferences: KeypadPreferences): KeyMapper = KeyMapper(preferences)

        /** A mapper for tests, with a fixed way of naming devices. */
        fun forTesting(preferences: KeypadPreferences? = null, deviceName: (KeyEvent) -> String = { "" }) = KeyMapper(preferences, deviceName)
    }
}

private class Translation(
    val event: KeyEvent,
    val action: Int,
    val keyCode: Int,
    val downTime: Long,
    val eventTime: Long,
    val repeat: Int,
    val out: KeyEvent?
) {
    fun matches(e: KeyEvent) = e === event && e.action == action && e.keyCode == keyCode && e.downTime == downTime &&
        e.eventTime == eventTime && e.repeatCount == repeat
}
