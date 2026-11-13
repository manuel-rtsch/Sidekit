package hrln.interactive.sidekit.sdk.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import hrln.interactive.sidekit.design.components.SidekitText
import hrln.interactive.sidekit.design.components.SidekitTile
import hrln.interactive.sidekit.design.input.KeypadModule
import hrln.interactive.sidekit.design.theme.SidekitTheme

/** One printed key and what it does on tap and on a long press. */
data class KeypadGuideKey(val label: String, val tap: String, val hold: String? = null)

/** The keys shown in [KeypadGuide], in the order they sit on the physical module. */
fun keypadGuideRows(module: KeypadModule): List<List<KeypadGuideKey?>> {
    fun key(label: String, tap: String, hold: String? = null) = KeypadGuideKey(label, tap, hold)
    fun digit(n: Int) = key("$n", "Tap repeatedly to cycle this key’s letters.", "Type $n.")
    val navigation: List<KeypadGuideKey?> = listOf(
        key("Back", "Go back a screen.", "Open the dialer."),
        key("Home", "Return to your home app."),
        key("Options", "Show this screen’s extra actions.", "Jump to the action bar.")
    )
    return when (module) {
        // The five-way control is three rows tall. The soft keys sit above and below it, so the
        // two outer columns have a gap in the middle row. This keeps the D-pad cross aligned.
        KeypadModule.T9 -> listOf(
            listOf(
                key("Back", "Go back a screen.", "Open the dialer."),
                null,
                key("Up", "Move focus up."),
                null,
                key("Options", "Show this screen’s extra actions.", "Jump to the action bar.")
            ),
            listOf(
                null,
                key("Left", "Move focus left. In text, move the cursor or suggestion selection."),
                key("OK", "Activate the focused control or confirm a suggestion."),
                key("Right", "Move focus right. In text, move the cursor or suggestion selection."),
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
            listOf(null, key("1", "Tap repeatedly for punctuation.", "Type 1."), key("2", "Tap repeatedly to cycle this key’s letters.", "Type 2."), key("3", "Tap repeatedly to cycle this key’s letters.", "Type 3."), null),
            listOf(null, digit(4), digit(5), digit(6), null),
            listOf(null, digit(7), digit(8), digit(9), null),
            listOf(null, key("*", "Tap repeatedly to cycle symbols."), key("0", "Insert a space in letter mode.", "Type 0."), key("#", "Change letter case.", "Switch letters and numbers."), null)
        )

        KeypadModule.Qwerty -> listOf(navigation) + listOf(
            listOf("QW", "ER", "TY", "UI", "OP"),
            listOf("AS", "DF", "GH", "JK", "L"),
            listOf("ZX", "CV", "BN", "M", "Delete")
        ).map { row ->
            row.map { label ->
                if (label == "Delete") {
                    key(label, "Delete the previous character.")
                } else {
                    key(label, "Choose a letter from $label.", "Type the printed digit or symbol.")
                }
            }
        } + listOf(
            listOf(key("↕", "Cycle the suggested words."), key("SYM", "Tap repeatedly to cycle symbols."), key("aA", "Change letter case.", "Switch letters and numbers.")),
            listOf(key("Space", "Insert a space.", "Type 0."), key("Enter", "Confirm a suggestion or insert a new line."))
        )

        // The Sundial is a ring of eight keys around the centre key. Lay it out exactly as it sits
        // on the module: top-left is left, top-right is right, bottom-left is Tab, bottom-right is
        // Enter, and the media keys are the middle row.
        KeypadModule.Sundial -> listOf(
            listOf(key("←", "Move focus left.", "Open options if Enter reports Play/Pause."), key("↑", "Move focus up.", "Open search."), key("→", "Move focus right.")),
            listOf(key("Previous", "Previous media track.", "Move focus left."), key("Play / pause", "Play or pause media.", "Confirm the focused control."), key("Next", "Next media track.", "Move focus right.")),
            listOf(key("Tab", "Move to the next control.", "Go back."), key("↓", "Move focus down.", "Go to the home screen."), key("Enter", "Activate the focused control.", "Open options."))
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
                key("Options", "Open options.", "Focus the action bar."),
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

/**
 * The keypad diagram: tap a key to read its tap and long-press actions. The detail card stays
 * above the keys so the answer is visible without scrolling away from the key that was tapped.
 */
@Composable
fun KeypadGuide(module: KeypadModule) {
    val rows = remember(module) { keypadGuideRows(module) }
    var selected by remember(module) { mutableStateOf<KeypadGuideKey?>(null) }
    val spacing = SidekitTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.pageGap)) {
        KeypadGuideDetail(selected)
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
                                onClickLabel = "Show ${key.label}"
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
                "Some Sundials report Enter as Play/Pause. Hold ← for Options.",
                style = SidekitTheme.typography.small,
                color = SidekitTheme.colors.surfaceA6,
                maxLines = 2
            )
        }
        if (!module.typesOnKeypad) {
            SidekitText(
                "Text fields use the on-screen keyboard with this module.",
                style = SidekitTheme.typography.small,
                color = SidekitTheme.colors.surfaceA6,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun KeypadGuideDetail(key: KeypadGuideKey?) {
    SidekitTile(
        Modifier.fillMaxWidth().height(132.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (key == null) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                SidekitText(
                    "Tap a key to see what it does.",
                    style = SidekitTheme.typography.label,
                    color = SidekitTheme.colors.surfaceA6,
                    maxLines = 2
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                SidekitText(key.label, style = SidekitTheme.typography.label, maxLines = 1)
                GuideAction("Tap", key.tap)
                GuideAction("Hold", key.hold ?: "No long press", muted = key.hold == null)
            }
        }
    }
}

@Composable
private fun GuideAction(label: String, action: String, muted: Boolean = false) {
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
        if (muted) {
            SidekitText(
                action,
                Modifier.weight(1f),
                style = SidekitTheme.typography.tiny,
                color = SidekitTheme.colors.surfaceA6,
                maxLines = 2
            )
        } else {
            SidekitText(
                action,
                Modifier.weight(1f),
                style = SidekitTheme.typography.tiny,
                maxLines = 2
            )
        }
    }
}
