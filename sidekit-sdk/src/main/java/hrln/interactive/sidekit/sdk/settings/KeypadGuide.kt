package hrln.interactive.sidekit.sdk.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.design.input.KeypadModule
import hrln.interactive.sidekit.design.theme.SidekitTheme

data class KeypadGuideKey(val label: String, val action: String)

/** A long-press action shown below the key diagram. */
data class KeypadGuideHold(val key: String, val action: String)

/** The keys shown in [KeypadGuide], in the order they sit on the physical module. */
fun keypadGuideRows(module: KeypadModule): List<List<KeypadGuideKey?>> {
    fun key(label: String, action: String) = KeypadGuideKey(label, action)
    val navigation: List<KeypadGuideKey?> = listOf(key("Back", "Top-left: Back. Hold to open the dialer. Enable Sidekit keypad in Accessibility first."), key("Home", "Return to your home app."), key("Options", "Top-right: reveal this screen’s extra actions."))
    return when (module) {
        // The five-way control is three rows tall. The soft keys sit above and below it, so the
        // two outer columns have a gap in the middle row. This keeps the D-pad cross aligned.
        KeypadModule.T9 -> listOf(
            listOf(
                key("Back", "Go back a screen. Hold to open the dialer."),
                null,
                key("Up", "Move focus up."),
                null,
                key("Options", "Show this screen’s extra actions. Hold to jump to the action bar.")
            ),
            listOf(
                null,
                key("Left", "Move focus left; in text, move the cursor or suggestion selection."),
                key("Select", "Activate the focused control or confirm a suggestion."),
                key("Right", "Move focus right; in text, move the cursor or suggestion selection."),
                null
            ),
            listOf(
                key("Home", "Return to your home app."),
                null,
                key("Down", "Move focus down."),
                null,
                key("Delete", "Delete the previous character.")
            )
        ) + listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("Symbols", "0", "Case")
        ).map { row ->
            row.map { label ->
                key(
                    label,
                    when (label) {
                        "Symbols" -> "Tap repeatedly to cycle punctuation and symbols."
                        "Case" -> "Change letter case. Hold to switch letters and numbers."
                        "0" -> "Insert a space in letter mode. Hold to type 0."
                        "1" -> "Tap repeatedly for punctuation. Hold to type 1."
                        else -> "Tap repeatedly to cycle this key’s characters. Hold to type its digit."
                    }
                )
            }
        }

        KeypadModule.Qwerty -> listOf(navigation) + listOf(
            listOf("QW", "ER", "TY", "UI", "OP"),
            listOf("AS", "DF", "GH", "JK", "L"),
            listOf("ZX", "CV", "BN", "M", "Delete")
        ).map { row -> row.map { key(it, if (it == "Delete") "Delete the previous character." else "Tap repeatedly to choose a letter from $it. Hold for the digit or symbol printed above the key.") } } + listOf(
            listOf(key("↕", "Cycle the suggested words."), key("SYM", "Tap repeatedly to cycle symbols."), key("aA", "Tap to change letter case. Hold to switch letters / numbers.")),
            listOf(key("Space", "Insert a space. Hold to type 0."), key("Enter", "Confirm a suggestion or insert a new line."))
        )

        // The Sundial is a ring of eight keys around the centre key. Lay it out exactly as it sits
        // on the module: top-left is left, top-right is right, bottom-left is Tab, bottom-right is
        // Enter, and the media keys are the middle row.
        KeypadModule.Sundial -> listOf(
            listOf(key("←", "Move focus left. Hold for Options if Enter is unavailable."), key("↑", "Move focus up. Hold for Search."), key("→", "Move focus right.")),
            listOf(key("Previous", "Previous media track; hold for left."), key("Play / pause", "Play or pause media. Hold to Confirm."), key("Next", "Next media track; hold for right.")),
            listOf(key("Tab", "Move to the next focusable control; hold for Back."), key("↓", "Move focus down. Hold for Home."), key("Enter", "Activate the focused control; hold for Options. Some revisions report this as Play/Pause."))
        )

        // Five columns follow the controller exactly: the D-pad cross on the left, the small
        // shoulder button at the top right, and the face buttons staggered on the right.
        KeypadModule.GamePad -> listOf(
            listOf(
                null,
                key("Up", "Move focus up."),
                null,
                null,
                key("Search", "Open search.")
            ),
            listOf(
                key("Left", "Move focus left."),
                null,
                key("Right", "Move focus right."),
                null,
                null
            ),
            listOf(
                null,
                key("Down", "Move focus down."),
                null,
                key("Action", "Focus the action bar."),
                null
            ),
            listOf(
                null,
                null,
                key("Options", "Open options; hold to focus the action bar."),
                null,
                key("Back", "Go back a screen.")
            ),
            listOf(
                key("Home", "Go to the home screen."),
                null,
                null,
                key("Confirm", "Activate the focused control."),
                null
            )
        )
    }
}

/** Long-press actions for the module, shown as a legend under the diagram. */
fun keypadGuideHolds(module: KeypadModule): List<KeypadGuideHold> = when (module) {
    KeypadModule.T9 -> listOf(
        KeypadGuideHold("Back", "Open the dialer."),
        KeypadGuideHold("Options", "Jump to the action bar."),
        KeypadGuideHold("Case", "Switch between letters and numbers."),
        KeypadGuideHold("1", "Type 1."),
        KeypadGuideHold("2–9", "Type the digit."),
        KeypadGuideHold("0", "Type 0.")
    )

    KeypadModule.Qwerty -> listOf(
        KeypadGuideHold("Letter keys", "Type the digit or symbol printed above the key."),
        KeypadGuideHold("aA", "Switch between letters and numbers."),
        KeypadGuideHold("Space", "Type 0.")
    )

    KeypadModule.Sundial -> listOf(
        KeypadGuideHold("Tab", "Go back."),
        KeypadGuideHold("Enter", "Open options."),
        KeypadGuideHold("Previous", "Move focus left."),
        KeypadGuideHold("Next", "Move focus right."),
        KeypadGuideHold("Up", "Open search."),
        KeypadGuideHold("Down", "Go to the home screen."),
        KeypadGuideHold("Play / pause", "Confirm the focused control."),
        KeypadGuideHold("←", "Open options if Enter reports Play/Pause.")
    )

    KeypadModule.GamePad -> listOf(
        KeypadGuideHold("Options", "Jump to the action bar.")
    )
}

@Composable
fun KeypadGuide(module: KeypadModule) {
    val rows = remember(module) { keypadGuideRows(module) }
    val holds = remember(module) { keypadGuideHolds(module) }
    val title = SidekitTheme.typography.label.copy(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
    val section = SidekitTheme.typography.label.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
    val caption = SidekitTheme.typography.label.copy(fontSize = 20.sp, lineHeight = 21.sp)
    val detail = SidekitTheme.typography.label.copy(fontSize = 16.sp, lineHeight = 18.sp)

    val diagramTitle = if (module == KeypadModule.GamePad) "Controller diagram" else "${module.label} diagram"

    Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SidekitText(diagramTitle, style = title, maxLines = 1, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            rows.forEach { row ->
                val keyStyle = if (row.size > 5) caption.copy(fontSize = 15.sp, lineHeight = 17.sp) else caption
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { key ->
                        if (key == null) {
                            Spacer(Modifier.weight(1f).height(34.dp))
                        } else {
                            SidekitTile(Modifier.weight(1f).height(34.dp)) {
                                SidekitText(key.label, style = keyStyle, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        if (holds.isNotEmpty()) {
            SidekitText("Long hold functionalities", style = section, maxLines = 1)
            holds.forEach { hold ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .clip(SidekitTheme.shapes.item)
                            .background(SidekitTheme.colors.surfaceA3)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        SidekitText(hold.key, style = detail, maxLines = 1)
                    }
                    SidekitText(hold.action, style = detail, maxLines = Int.MAX_VALUE, modifier = Modifier.weight(1f))
                }
            }
        }
        if (module == KeypadModule.Sundial) {
            SidekitText(
                "No dedicated Back or Options key: hold Tab for Back and Enter for Options. Some Sundial revisions report Enter as the centre Play/Pause key. In that case hold Play/Pause to Confirm and hold ← for Options; taps still control media.",
                style = detail,
                maxLines = Int.MAX_VALUE
            )
        }
        if (!module.typesOnKeypad) SidekitText("Text fields use the on-screen keyboard with this module.", style = caption, maxLines = Int.MAX_VALUE)
    }
}
