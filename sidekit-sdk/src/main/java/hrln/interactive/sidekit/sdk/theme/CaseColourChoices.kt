package hrln.interactive.sidekit.sdk.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.theme.CaseColour
import hrln.interactive.sidekit.design.theme.SidekitColors
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.design.theme.readableContentOn
import hrln.interactive.sidekit.sdk.R

/**
 * The 3×3 grid, row by row. `null` is the original blue and sits next to sky Blue; the last row
 * has only two choices, so its final cell stays empty.
 */
private val gridOrder: List<CaseColour?> = listOf(
    CaseColour.Cloud,
    CaseColour.Blue,
    null,
    CaseColour.Lime,
    CaseColour.Sage,
    CaseColour.Orange,
    CaseColour.Lavender,
    CaseColour.Pink
)

/**
 * The case-colour picker, shared by Sidekit's Settings/onboarding and standalone Sidekit apps so
 * every host offers the same swatches.
 */
fun LazyListScope.caseColourChoices(selected: CaseColour?, choose: (CaseColour?) -> Unit) {
    item { SidekitText(stringResource(R.string.sk_sdk_case_colour_intro), style = SidekitTheme.typography.label) }
    item {
        val original = stringResource(R.string.sk_sdk_case_colour_original)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(SidekitTheme.spacing.itemOuterGap)) {
            gridOrder.chunked(3).forEach { row ->
                SidekitItemRow {
                    row.forEach { colour ->
                        val active = colour == selected
                        val swatch = colour?.color ?: SidekitColors().primaryA1
                        val label = colour?.displayLabel() ?: original
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
    item {
        val active = selected?.displayLabel() ?: stringResource(R.string.sk_sdk_case_colour_original)
        SidekitText(stringResource(R.string.sk_sdk_case_colour_active, active), style = SidekitTheme.typography.label)
    }
}
