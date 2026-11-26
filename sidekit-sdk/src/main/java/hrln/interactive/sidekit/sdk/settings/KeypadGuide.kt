package hrln.interactive.sidekit.sdk.settings

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hrln.interactive.sidekit.design.components.SidekitCard
import hrln.interactive.sidekit.design.components.SidekitIconImage
import hrln.interactive.sidekit.design.components.SidekitText
import hrln.interactive.sidekit.design.components.SidekitTile
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.input.KeypadModule
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.design.theme.contentColorFor
import hrln.interactive.sidekit.sdk.R

/**
 * One printed key: the name the detail card and screen readers use, what a tap and a long press
 * do, and the ink the cap itself carries.
 *
 * [icon] and [printed] describe what is printed on the physical key, so the diagram reads like the
 * module instead of repeating the translated [label]: a D-pad key prints only its arrow, the
 * controller's face buttons print Y / X / B / A, and a blank cap beside Home prints a dash.
 * [printed] is null when the cap has no text of its own.
 */
data class KeypadGuideKey(
    val label: String,
    val tap: String,
    val hold: String? = null,
    val icon: SidekitIcon? = null,
    val printed: String? = null
)

/**
 * The keys shown in [KeypadGuide], in the order they sit on the physical module. Pass [resources]
 * (for example `LocalContext.current.resources`) for the app language; without them it is English.
 */
fun keypadGuideRows(module: KeypadModule, resources: Resources? = null): List<List<KeypadGuideKey?>> {
    fun t(@StringRes id: Int, english: String, vararg args: Any): String = resources?.getString(id, *args) ?: if (args.isEmpty()) english else english.format(*args)
    fun key(
        label: String,
        tap: String,
        hold: String? = null,
        icon: SidekitIcon? = null,
        printed: String? = null
    ) = KeypadGuideKey(label, tap, hold, icon, printed)
    val back = t(R.string.sk_sdk_guide_back, "Back")
    val home = t(R.string.sk_sdk_guide_home, "Home")
    val options = t(R.string.sk_sdk_guide_options, "Options")
    val up = t(R.string.sk_sdk_guide_up, "Up")
    val down = t(R.string.sk_sdk_guide_down, "Down")
    val left = t(R.string.sk_sdk_guide_left, "Left")
    val right = t(R.string.sk_sdk_guide_right, "Right")
    val delete = t(R.string.sk_sdk_guide_delete, "Delete")
    val space = t(R.string.sk_sdk_guide_space, "Space")
    val enter = t(R.string.sk_sdk_guide_enter, "Enter")
    val previous = t(R.string.sk_sdk_guide_previous, "Previous")
    val playPause = t(R.string.sk_sdk_guide_play_pause, "Play / pause")
    val next = t(R.string.sk_sdk_guide_next, "Next")
    val search = t(R.string.sk_sdk_guide_search, "Search")
    val playingNow = t(R.string.sk_sdk_guide_playing_now, "Playing now")
    val confirm = t(R.string.sk_sdk_guide_confirm, "Confirm")
    val goBackScreen = t(R.string.sk_sdk_guide_go_back_screen, "Go back a screen.")
    val openDialer = t(R.string.sk_sdk_guide_open_dialer, "Open the dialer.")
    val returnHome = t(R.string.sk_sdk_guide_return_home, "Return to your home app.")
    val extraActions = t(R.string.sk_sdk_guide_extra_actions, "Show this screen\u2019s extra actions.")
    val jumpToBar = t(R.string.sk_sdk_guide_jump_to_bar, "Jump to the action bar.")
    val focusUp = t(R.string.sk_sdk_guide_focus_up, "Move focus up.")
    val focusDown = t(R.string.sk_sdk_guide_focus_down, "Move focus down.")
    val focusLeft = t(R.string.sk_sdk_guide_focus_left, "Move focus left.")
    val focusRight = t(R.string.sk_sdk_guide_focus_right, "Move focus right.")
    val focusLeftText = t(R.string.sk_sdk_guide_focus_left_text, "Move focus left. In text, move the cursor or suggestion selection.")
    val focusRightText = t(R.string.sk_sdk_guide_focus_right_text, "Move focus right. In text, move the cursor or suggestion selection.")
    val okAction = t(R.string.sk_sdk_guide_ok, "Activate the focused control or confirm a suggestion.")
    val deletePrevious = t(R.string.sk_sdk_guide_delete_previous, "Delete the previous character.")
    val cycleLetters = t(R.string.sk_sdk_guide_cycle_letters, "Tap repeatedly to cycle this key\u2019s letters.")
    val cycleSymbols = t(R.string.sk_sdk_guide_cycle_symbols, "Tap repeatedly to cycle symbols.")
    val changeCase = t(R.string.sk_sdk_guide_change_case, "Change letter case.")
    val lettersAndNumbers = t(R.string.sk_sdk_guide_letters_numbers, "Switch letters and numbers.")
    val focusActionBar = t(R.string.sk_sdk_guide_focus_action_bar, "Focus the action bar.")
    val activateFocused = t(R.string.sk_sdk_guide_activate_focused, "Activate the focused control.")
    val openOptions = t(R.string.sk_sdk_guide_open_options, "Open options.")
    val openSearch = t(R.string.sk_sdk_guide_open_search, "Open search.")
    val openPlayingNow = t(R.string.sk_sdk_guide_open_playing_now, "Open the playing now screen.")
    val goHomeScreen = t(R.string.sk_sdk_guide_go_home_screen, "Go to the home screen.")
    val previousTrack = t(R.string.sk_sdk_guide_previous_track, "Previous media track.")
    val playPauseMedia = t(R.string.sk_sdk_guide_play_pause_media, "Play or pause media.")
    val confirmFocused = t(R.string.sk_sdk_guide_confirm_focused, "Confirm the focused control.")
    val nextTrack = t(R.string.sk_sdk_guide_next_track, "Next media track.")
    val nextControl = t(R.string.sk_sdk_guide_next_control, "Move to the next control.")
    val goBack = t(R.string.sk_sdk_guide_go_back, "Go back.")
    fun chooseLetter(label: String) = t(R.string.sk_sdk_guide_choose_letter, "Choose a letter from %1\$s.", label)
    val typePrinted = t(R.string.sk_sdk_guide_type_printed, "Type the printed digit or symbol.")
    val cycleSuggestions = t(R.string.sk_sdk_guide_cycle_suggestions, "Cycle the suggested words.")
    val insertSpace = t(R.string.sk_sdk_guide_insert_space, "Insert a space.")
    val confirmOrNewLine = t(R.string.sk_sdk_guide_confirm_or_new_line, "Confirm a suggestion or insert a new line.")
    val recents = t(R.string.sk_sdk_guide_recents, "Recents")
    val showRecents = t(R.string.sk_sdk_guide_show_recents, "Show recent apps.")
    // The two blank caps beside Home print a dash, not a word.
    val dash = "\u2013"
    fun type(digit: Int) = t(R.string.sk_sdk_guide_type_digit, "Type %1\$d.", digit)
    fun digit(n: Int) = key("$n", cycleLetters, type(n), printed = "$n")
    return when (module) {
        // The five-way control is three rows tall. The soft keys sit above and below it, so the
        // two outer columns have a gap in the middle row. This keeps the D-pad cross aligned.
        KeypadModule.T9 -> listOf(
            listOf(
                key(back, goBackScreen, openDialer, SidekitIcon.Call, printed = back),
                null,
                key(up, focusUp, icon = SidekitIcon.ArrowUpward, printed = up),
                null,
                key(options, extraActions, jumpToBar, SidekitIcon.CallEnd, printed = options)
            ),
            listOf(
                null,
                key(left, focusLeftText, icon = SidekitIcon.ArrowLeftAlt, printed = left),
                key("OK", okAction, openPlayingNow, printed = "OK"),
                key(right, focusRightText, icon = SidekitIcon.ArrowRightAlt, printed = right),
                null
            ),
            listOf(
                key(home, returnHome, openSearch, icon = SidekitIcon.Home, printed = home),
                null,
                key(down, focusDown, icon = SidekitIcon.ArrowDownward, printed = down),
                null,
                key(delete, deletePrevious, icon = SidekitIcon.Backspace, printed = delete)
            )
        ) + listOf(
            listOf(null, key("1", t(R.string.sk_sdk_guide_punctuation, "Tap repeatedly for punctuation."), type(1), printed = "1"), digit(2), digit(3), null),
            listOf(null, digit(4), digit(5), digit(6), null),
            listOf(null, digit(7), digit(8), digit(9), null),
            listOf(null, key("*", cycleSymbols, printed = "*"), key("0", t(R.string.sk_sdk_guide_space_in_letters, "Insert a space in letter mode."), type(0), printed = "0"), key("#", changeCase, lettersAndNumbers, printed = "#"), null)
        )

        // The mini QWERTY's top row is five keys on the module: the call glyph, a dash that goes
        // back, the Home dot, a dash for Recents, and the end-call glyph that opens Options.
        // Recents is a system key Sidekit never translates, so its row describes the hardware.
        // null marks the Delete key, whose name is translated. The bottom row is one line of five.
        KeypadModule.Qwerty -> listOf(
            listOf(
                key(back, goBackScreen, openDialer, SidekitIcon.Call),
                key(back, goBackScreen, printed = dash),
                key(home, returnHome, openSearch, icon = SidekitIcon.Home),
                key(recents, showRecents, printed = dash),
                key(options, extraActions, jumpToBar, SidekitIcon.CallEnd)
            )
        ) + listOf(
            listOf("QW", "ER", "TY", "UI", "OP"),
            listOf("AS", "DF", "GH", "JK", "L"),
            listOf("ZX", "CV", "BN", "M", null)
        ).map { row ->
            row.map { label ->
                if (label == null) {
                    key(delete, deletePrevious, icon = SidekitIcon.Backspace)
                } else {
                    key(label, chooseLetter(label), typePrinted, printed = label)
                }
            }
        } + listOf(
            listOf(
                key("\u2195", cycleSuggestions, printed = "\u2195"),
                key("SYM", cycleSymbols, printed = "SYM"),
                key(space, insertSpace, type(0), printed = space),
                key("aA", changeCase, lettersAndNumbers, printed = "aA"),
                key(enter, confirmOrNewLine, openPlayingNow, icon = SidekitIcon.KeyboardReturn, printed = enter)
            )
        )

        // The Sundial is a ring of eight keys around the centre key. Lay it out exactly as it sits
        // on the module: top-left is left, top-right is right, bottom-left is Tab, bottom-right is
        // Enter, and the media keys are the middle row.
        // Its labels are the printed glyphs themselves, so the arrows need no icon above them.
        KeypadModule.Sundial -> listOf(
            listOf(
                key("\u2190", focusLeft, printed = "\u2190"),
                key("\u2191", focusUp, printed = "\u2191"),
                key("\u2192", focusRight, printed = "\u2192")
            ),
            listOf(
                key(previous, previousTrack, printed = previous),
                key(playPause, playPauseMedia, confirmFocused, SidekitIcon.PlayArrow, printed = playPause),
                key(next, nextTrack, printed = next)
            ),
            listOf(
                key("Tab", nextControl, goBack, printed = "Tab"),
                key("\u2193", focusDown, printed = "\u2193"),
                key(enter, activateFocused, openOptions, SidekitIcon.KeyboardReturn, printed = enter)
            )
        )

        // Five columns follow the controller exactly: the D-pad cross on the left, the small
        // shoulder button at the top right, and the face buttons staggered on the right. The
        // D-pad, Search and Home print only their glyph; the face buttons print the letter on the
        // shell — Y is Playing Now, X opens Options, B goes back, A confirms.
        KeypadModule.GamePad -> listOf(
            listOf(
                null,
                key(up, focusUp, icon = SidekitIcon.ArrowUpward),
                null,
                null,
                key(search, openSearch, icon = SidekitIcon.Search)
            ),
            listOf(
                key(left, focusLeft, icon = SidekitIcon.ArrowLeftAlt),
                null,
                key(right, focusRight, icon = SidekitIcon.ArrowRightAlt),
                null,
                null
            ),
            listOf(
                null,
                key(down, focusDown, icon = SidekitIcon.ArrowDownward),
                null,
                key(playingNow, openPlayingNow, printed = "Y"),
                null
            ),
            listOf(
                null,
                null,
                key(options, openOptions, focusActionBar, printed = "X"),
                null,
                key(back, goBackScreen, printed = "B")
            ),
            listOf(
                key(home, goHomeScreen, icon = SidekitIcon.Home),
                null,
                null,
                key(confirm, activateFocused, printed = "A"),
                null
            )
        )
    }
}

/**
 * The keypad diagram: each tile is printed like the physical key, and tapping one shows its tap
 * and long-press actions. The detail card stays above the keys so the answer is visible without
 * scrolling away from the key that was tapped, and it grows with its sentences instead of
 * clipping them.
 */
@Composable
fun KeypadGuide(module: KeypadModule, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val rows = remember(module, resources) { keypadGuideRows(module, resources) }
    var selected by remember(module) { mutableStateOf<KeypadGuideKey?>(null) }
    val spacing = SidekitTheme.spacing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.pageGap)) {
        AnimatedVisibility(
            visible = selected != null,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            selected?.let { KeypadGuideDetail(it) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing.itemOuterGap)) {
            rows.forEach { row ->
                val keyHeight = if (row.size > 3) 52.dp else 64.dp
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.itemOuterGap)) {
                    row.forEach { key ->
                        if (key == null) {
                            Spacer(Modifier.weight(1f).height(keyHeight))
                        } else {
                            SidekitTile(
                                Modifier.weight(1f).height(keyHeight),
                                color = if (selected == key) SidekitTheme.colors.emphasisA1 else SidekitTheme.colors.surfaceA3,
                                onClick = { selected = key },
                                onClickLabel = stringResource(R.string.sk_sdk_guide_show_key, key.label)
                            ) {
                                // What the cap prints: its glyph, the legend on a blank cap, or
                                // both. The translated name stays in the detail card.
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    key.icon?.let { SidekitIconImage(it, size = 20.dp) }
                                    key.printed?.let { printed ->
                                        SidekitText(
                                            printed,
                                            style = if (key.icon == null) SidekitTheme.typography.small else SidekitTheme.typography.tiny,
                                            // Two lines so a two-word legend still fits a fifth of the width.
                                            maxLines = if (key.icon == null) 1 else 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (module == KeypadModule.Sundial) {
            SidekitText(
                stringResource(R.string.sk_sdk_guide_sundial_note),
                style = SidekitTheme.typography.small,
                color = SidekitTheme.colors.surfaceA6,
                maxLines = 2
            )
        }
        if (!module.typesOnKeypad) {
            SidekitText(
                stringResource(R.string.sk_sdk_guide_onscreen_keyboard),
                style = SidekitTheme.typography.small,
                color = SidekitTheme.colors.surfaceA6,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun KeypadGuideDetail(key: KeypadGuideKey) {
    // The card grows with its tap and hold sentences instead of clipping them at a fixed height.
    SidekitCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                key.icon?.let { icon -> SidekitIconImage(icon, size = 28.dp) }
                SidekitText(key.label, style = SidekitTheme.typography.label, maxLines = 1)
            }
            GuideAction(stringResource(R.string.sk_sdk_guide_tap), key.tap)
            GuideAction(
                stringResource(R.string.sk_sdk_guide_hold),
                key.hold ?: stringResource(R.string.sk_sdk_guide_no_long_press),
                muted = key.hold == null
            )
        }
    }
}

@Composable
private fun GuideAction(
    label: String,
    action: String,
    muted: Boolean = false
) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        SidekitText(
            label,
            Modifier.width(52.dp),
            style = SidekitTheme.typography.tiny,
            color = SidekitTheme.colors.surfaceA6,
            maxLines = 1
        )
        // The card grows instead of clipping a long action at two lines.
        SidekitText(
            action,
            Modifier.weight(1f),
            style = SidekitTheme.typography.tiny,
            // SidekitCard paints the detail on surfaceA3, so its content colour is the readable one.
            color = if (muted) SidekitTheme.colors.surfaceA6 else SidekitTheme.colors.contentColorFor(SidekitTheme.colors.surfaceA3),
            maxLines = Int.MAX_VALUE
        )
    }
}
