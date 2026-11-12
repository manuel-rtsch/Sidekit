package hrln.interactive.sidekit.apps.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.theme.CaseColour
import hrln.interactive.sidekit.design.theme.SidekitColors
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.design.theme.readableContentOn

@Composable
internal fun rememberCaseColour(prefs: SidekitPreferences): CaseColour? {
    var selected by remember(prefs) { mutableStateOf(prefs.caseColour) }
    DisposableEffect(prefs) {
        val stop = prefs.observeCaseColour { selected = prefs.caseColour }
        selected = prefs.caseColour
        onDispose { stop() }
    }
    return selected
}

@Composable
internal fun CaseColourScreen(back: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { SidekitPreferences(context) }
    val selected = rememberCaseColour(prefs)
    SidekitScaffold("Case colour", actions = listOf(SideAction.back("back", "Settings", back))) { padding ->
        SidekitList(padding) {
            caseColourChoices(selected) { prefs.caseColour = it }
        }
    }
}

internal fun androidx.compose.foundation.lazy.LazyListScope.caseColourChoices(selected: CaseColour?, choose: (CaseColour?) -> Unit) {
    item { SidekitText("Match your Sidephone case", style = SidekitTheme.typography.label) }
    item {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(SidekitTheme.spacing.itemOuterGap)) {
            (CaseColour.entries + listOf(null)).chunked(3).forEach { row ->
                SidekitItemRow {
                    row.forEach { colour ->
                        val active = colour == selected
                        val swatch = colour?.color ?: SidekitColors().primaryA1
                        val label = colour?.label ?: "Original blue"
                        Box(
                            Modifier.weight(1f).height(88.dp)
                                .border(if (active) 3.dp else 0.dp, if (active) Color.White else Color.Transparent, SidekitTheme.shapes.item)
                                .padding(6.dp)
                                .clip(SidekitTheme.shapes.item)
                                .background(swatch)
                                .selectable(active, role = Role.RadioButton, onClick = { choose(colour) })
                                .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center
                        ) {
                            if (active) SidekitIconImage(SidekitIcon.Check, tint = readableContentOn(swatch))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    item { SidekitText("Active: ${selected?.label ?: "Original blue"}", style = SidekitTheme.typography.label) }
}
