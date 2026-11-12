package hrln.interactive.sidekit.sdk.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.theme.CaseColour
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.design.theme.readableContentOn

/**
 * The case-colour picker, shared by Sidekit's Settings/onboarding and standalone Sidekit apps so
 * every host offers the same swatches.
 */
fun LazyListScope.caseColourChoices(selected: CaseColour?, choose: (CaseColour?) -> Unit) {
    item { SidekitText("Match your Sidephone case", style = SidekitTheme.typography.label) }
    item {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(SidekitTheme.spacing.itemOuterGap)) {
            CaseColour.entries.chunked(3).forEach { row ->
                SidekitItemRow {
                    row.forEach { colour ->
                        val active = colour == selected
                        Box(
                            Modifier.weight(1f).height(88.dp)
                                .border(if (active) 3.dp else 0.dp, if (active) Color.White else Color.Transparent, SidekitTheme.shapes.item)
                                .padding(6.dp)
                                .clip(SidekitTheme.shapes.item)
                                .background(colour.color)
                                .selectable(active, role = Role.RadioButton, onClick = { choose(colour) })
                                .semantics { contentDescription = colour.label },
                            contentAlignment = Alignment.Center
                        ) {
                            if (active) SidekitIconImage(SidekitIcon.Check, tint = readableContentOn(colour.color))
                        }
                    }
                }
            }
        }
    }
    item { SidekitText("Active: ${selected?.label ?: "Original blue"}", style = SidekitTheme.typography.label) }
    item { SidekitLineButton("Original blue", { choose(null) }, variant = LineButtonVariant.Secondary) }
}
