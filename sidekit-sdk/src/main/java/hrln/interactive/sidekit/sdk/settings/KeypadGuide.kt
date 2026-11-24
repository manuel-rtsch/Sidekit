package hrln.interactive.sidekit.sdk.settings

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hrln.interactive.sidekit.design.components.SidekitText
import hrln.interactive.sidekit.design.components.SidekitTile
import hrln.interactive.sidekit.design.input.KeypadModule
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.design.theme.contentColorFor
import hrln.interactive.sidekit.sdk.R

/** One printed key and what it does on tap and on a long press. */
data class KeypadGuideKey(val label: String, val tap: String, val hold: String? = null)

/**
 * The keys shown in [KeypadGuide], in the order they sit on the physical module. Pass [resources]
 * (for example `LocalContext.current.resources`) for the app language; without them it is English.
 */
fun keypadGuideRows(module: KeypadModule, resources: Resources? = null): List<List<KeypadGuideKey?>> {
    fun t(@StringRes id: Int, english: String, vararg args: Any): String = resources?.getString(id, *args) ?: if (args.isEmpty()) english else english.format(*args)
    fun key(label: String, tap: String, hold: String? = null) = KeypadGuideKey(label, tap, hold)
    val back = t(R.string.sk_sdk_guide_back, "Back")
    val home = t(R.string.sk_sdk_guide_home, "Home")
    val options = t(R.string.sk_sdk_guide_options, "Options")
    val up = t(R.string.sk_sdk_guide_up, "Up")
    val down = t(R.string.sk_sdk_guide_down, "Down")
    val left = t(R.string.sk_sdk_guide_left, "Left")
    val right = t(R.string.sk_sdk_guide_right, "Right")
    val delete = t(R.string.sk_sdk_guide_delete, "Delete")
    val goBackScreen = t(R.string.sk_sdk_guide_go_back_screen, "Go back a screen.")
    val openDialer = t(R.string.sk_sdk_guide_open_dialer, "Open the dialer.")
    val returnHome = t(R.string.sk_sdk_guide_return_home, "Return to your home app.")
    val extraActions = t(R.string.sk_sdk_guide_extra_actions, "Show this screen’s extra actions.")
    val jumpToBar = t(R.string.sk_sdk_guide_jump_to_bar, "Jump to the action bar.")
    val focusUp = t(R.string.sk_sdk_guide_focus_up, "Move focus up.")
    val focusDown = t(R.string.sk_sdk_guide_focus_down, "Move focus down.")
    val focusLeft = t(R.string.sk_sdk_guide_focus_left, "Move focus left.")
    val focusRight = t(R.string.sk_sdk_guide_focus_right, "Move focus right.")
    val deletePrevious = t(R.string.sk_sdk_guide_delete_previous, "Delete the previous character.")
    val cycleLetters = t(R.string.sk_sdk_guide_cycle_letters, "Tap repeatedly to cycle this key’s letters.")
    val cycleSymbols = t(R.string.sk_sdk_guide_cycle_symbols, "Tap repeatedly to cycle symbols.")
    val changeCase = t(R.string.sk_sdk_guide_change_case, "Change letter case.")
    val lettersAndNumbers = t(R.string.sk_sdk_guide_letters_numbers, "Switch letters and numbers.")
    val focusActionBar = t(R.string.sk_sdk_guide_focus_action_bar, "Focus the action bar.")
    val activateFocused = t(R.string.sk_sdk_guide_activate_focused, "Activate the focused control.")
    val openOptions = t(R.string.sk_sdk_guide_open_options, "Open options.")
    fun type(digit: Int) = t(R.string.sk_sdk_guide_type_digit, "Type %1\$d.", digit)
    fun digit(n: Int) = key("$n", cycleLetters, type(n))
    val navigation: List<KeypadGuideKey?> = listOf(
        key(back, goBackScreen, openDialer),
        key(home, returnHome),
        key(options, extraActions, jumpToBar)
    )
    return when (module) {
        // The five-way control is three rows tall. The soft keys sit above and below it, so the
        // two outer columns have a gap in the middle row. This keeps the D-pad cross aligned.
        KeypadModule.T9 -> listOf(
            listOf(
                key(back, goBackScreen, openDialer),
                null,
                key(up, focusUp),
                null,
                key(options, extraActions, jumpToBar)
            ),
            listOf(
                null,
                key(left, t(R.string.sk_sdk_guide_focus_left_text, "Move focus left. In text, move the cursor or suggestion selection.")),
                key("OK", t(R.string.sk_sdk_guide_ok, "Activate the focused control or confirm a suggestion.")),
                key(right, t(R.string.sk_sdk_guide_focus_right_text, "Move focus right. In text, move the cursor or suggestion selection.")),
                null
            ),
            listOf(
                key(home, returnHome),
                null,
                key(down, focusDown),
                null,
                key(delete, deletePrevious)
            )
        ) + listOf(
            listOf(null, key("1", t(R.string.sk_sdk_guide_punctuation, "Tap repeatedly for punctuation."), type(1)), digit(2), digit(3), null),
            listOf(null, digit(4), digit(5), digit(6), null),
            listOf(null, digit(7), digit(8), digit(9), null),
            listOf(null, key("*", cycleSymbols), key("0", t(R.string.sk_sdk_guide_space_in_letters, "Insert a space in letter mode."), type(0)), key("#", changeCase, lettersAndNumbers), null)
        )

        // null marks the Delete key, whose name is translated.
        KeypadModule.Qwerty -> listOf(navigation) + listOf(
            listOf("QW", "ER", "TY", "UI", "OP"),
            listOf("AS", "DF", "GH", "JK", "L"),
            listOf("ZX", "CV", "BN", "M", null)
        ).map { row ->
            row.map { label ->
                if (label == null) {
                    key(delete, deletePrevious)
                } else {
                    key(label, t(R.string.sk_sdk_guide_choose_letter, "Choose a letter from %1\$s.", label), t(R.string.sk_sdk_guide_type_printed, "Type the printed digit or symbol."))
                }
            }
        } + listOf(
            listOf(key("↕", t(R.string.sk_sdk_guide_cycle_suggestions, "Cycle the suggested words.")), key("SYM", cycleSymbols), key("aA", changeCase, lettersAndNumbers)),
            listOf(
                key(t(R.string.sk_sdk_guide_space, "Space"), t(R.string.sk_sdk_guide_insert_space, "Insert a space."), type(0)),
                key(t(R.string.sk_sdk_guide_enter, "Enter"), t(R.string.sk_sdk_guide_confirm_or_new_line, "Confirm a suggestion or insert a new line."))
            )
        )

        // The Sundial is a ring of eight keys around the centre key. Lay it out exactly as it sits
        // on the module: top-left is left, top-right is right, bottom-left is Tab, bottom-right is
        // Enter, and the media keys are the middle row.
        KeypadModule.Sundial -> listOf(
            listOf(key("←", focusLeft), key("↑", focusUp), key("→", focusRight)),
            listOf(
                key(t(R.string.sk_sdk_guide_previous, "Previous"), t(R.string.sk_sdk_guide_previous_track, "Previous media track."), focusLeft),
                key(t(R.string.sk_sdk_guide_play_pause, "Play / pause"), t(R.string.sk_sdk_guide_play_pause_media, "Play or pause media."), t(R.string.sk_sdk_guide_confirm_focused, "Confirm the focused control.")),
                key(t(R.string.sk_sdk_guide_next, "Next"), t(R.string.sk_sdk_guide_next_track, "Next media track."), focusRight)
            ),
            listOf(
                key("Tab", t(R.string.sk_sdk_guide_next_control, "Move to the next control."), t(R.string.sk_sdk_guide_go_back, "Go back.")),
                key("↓", focusDown),
                key(t(R.string.sk_sdk_guide_enter, "Enter"), activateFocused, openOptions)
            )
        )

        // Five columns follow the controller exactly: the D-pad cross on the left, the small
        // shoulder button at the top right, and the face buttons staggered on the right.
        KeypadModule.GamePad -> listOf(
            listOf(
                null,
                key(up, focusUp),
                null,
                null,
                key(t(R.string.sk_sdk_guide_search, "Search"), t(R.string.sk_sdk_guide_open_search, "Open search."))
            ),
            listOf(
                key(left, focusLeft),
                null,
                key(right, focusRight),
                null,
                null
            ),
            listOf(
                null,
                key(down, focusDown),
                null,
                key(t(R.string.sk_sdk_guide_action, "Action"), focusActionBar),
                null
            ),
            listOf(
                null,
                null,
                key(options, openOptions, focusActionBar),
                null,
                key(back, goBackScreen)
            ),
            listOf(
                key(home, t(R.string.sk_sdk_guide_go_home_screen, "Go to the home screen.")),
                null,
                null,
                key(t(R.string.sk_sdk_guide_confirm, "Confirm"), activateFocused),
                null
            )
        )
    }
}

/**
 * The keypad diagram: tap a key to read its tap and long-press actions. The detail card stays
 * above the keys so the answer is visible without scrolling away from the key that was tapped.
 */
@Composable
fun KeypadGuide(module: KeypadModule) {
    val resources = LocalContext.current.resources
    val rows = remember(module, resources) { keypadGuideRows(module, resources) }
    var selected by remember(module) { mutableStateOf<KeypadGuideKey?>(null) }
    val spacing = SidekitTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.pageGap)) {
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
                                SidekitText(key.label, style = SidekitTheme.typography.small, maxLines = 1)
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
    val surface = SidekitTheme.colors.surfaceA3
    val content = SidekitTheme.colors.contentColorFor(surface)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .clip(SidekitTheme.shapes.item)
            .background(surface),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            SidekitText(key.label, style = SidekitTheme.typography.label, color = content, maxLines = 1)
            GuideAction(stringResource(R.string.sk_sdk_guide_tap), key.tap, contentColor = content)
            GuideAction(
                stringResource(R.string.sk_sdk_guide_hold),
                key.hold ?: stringResource(R.string.sk_sdk_guide_no_long_press),
                muted = key.hold == null,
                contentColor = content
            )
        }
    }
}

@Composable
private fun GuideAction(
    label: String,
    action: String,
    muted: Boolean = false,
    contentColor: Color = SidekitTheme.colors.contentColorFor(SidekitTheme.colors.surfaceA3)
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
        SidekitText(
            action,
            Modifier.weight(1f),
            style = SidekitTheme.typography.tiny,
            color = if (muted) SidekitTheme.colors.surfaceA6 else contentColor,
            maxLines = Int.MAX_VALUE
        )
    }
}
