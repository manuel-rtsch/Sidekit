package hrln.interactive.sidekit.apps.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hrln.interactive.sidekit.apps.sideport.registry.PortedApps
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.home.HomeArranger
import hrln.interactive.sidekit.home.HomePreferences
import hrln.interactive.sidekit.navigation.SubApp

/**
 * Settings → Arrange apps. Edit the home order and the four-slot dock against a local buffer and
 * commit only on Done, so Back genuinely cancels and Reset only touches the buffer.
 *
 * Interaction is keypad-first: each row toggles its dock membership on tap (showing "In dock"),
 * and the row's paired up/down arrows reorder it. The row arrows keep the action bar to the
 * commit controls (Back / Done / Reset) instead of encoding a global "selected row".
 */
@Composable
internal fun ArrangeHomeScreen(back: () -> Unit) {
    val context = LocalContext.current
    val home = remember(context) { HomePreferences(context) }
    val ported by remember { PortedApps.get(context).apps }.collectAsStateWithLifecycle()
    val subsById = remember(SubApp.entries) { SubApp.entries.associateBy { it.visibilityId() } }
    val portedById = remember(ported) { ported.associateBy { it.visibilityId() } }
    val known = remember(subsById, portedById) { subsById.keys + portedById.keys }
    // Editing buffer, seeded from the stored prefs once. Nothing is written until commit() (Done).
    val arranger = remember(home, known) { HomeArranger(home, known) }
    val order = arranger.order
    val dock = arranger.dock
    fun done() {
        arranger.commit()
        back()
    }
    fun cancel() = back() // buffer never committed, so Back leaves the launcher unchanged

    val list = rememberLazyListState()
    SidekitScaffold(
        "Arrange apps",
        actions = listOf(
            SideAction.back("back", "Settings", ::cancel),
            SideAction.forward("done", "Done", ::done, icon = SidekitIcon.Check),
            SideAction("reset", "Reset", icon = SidekitIcon.Undo, tone = ActionTone.Muted, onClick = { arranger.reset() }),
        ),
    ) { padding ->
        SidekitList(padding, state = list) {
            item {
                SidekitText(
                    "Tap a row to pin or unpin it in the four-slot dock (shown as \"In dock\"). Use each row's arrows to move it through the home order. Done saves; Back leaves it unchanged.",
                    style = SidekitTheme.typography.label.copy(fontSize = 24.sp, lineHeight = 30.sp, textAlign = TextAlign.Start),
                    maxLines = Int.MAX_VALUE,
                )
            }
            val last = order.lastIndex
            itemsIndexed(order, key = { _, id -> id }) { index, id ->
                val label = subsById[id]?.label ?: portedById[id]?.label ?: return@itemsIndexed
                val enabledUp = index > 0
                val enabledDown = index < last
                // The up/down arrows sit beside the row (not inside its clickable tile) so each is its
                // own D-pad focus target and touch target; the tile itself toggles the dock pin.
                Row(
                    Modifier.fillMaxWidth().height(76.dp).listFade(list, index + 1),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SidekitTheme.spacing.itemContentGap),
                ) {
                    Column {
                        ItemArrow(up = true, enabled = enabledUp, contentDescription = "Move $label up") { arranger.move(id, -1) }
                        ItemArrow(up = false, enabled = enabledDown, contentDescription = "Move $label down") { arranger.move(id, +1) }
                    }
                    SidekitItem(
                        title = label,
                        modifier = Modifier.weight(1f),
                        secondary = if (id in dock) "In dock" else null,
                        onClick = { arranger.toggleDock(id) },
                    )
                }
            }
        }
    }
}

/** A small round arrow button; `up=false` flips the upward glyph to point down. Disabled state is dimmed. */
@Composable
private fun ItemArrow(up: Boolean, enabled: Boolean, contentDescription: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(50))
            .alpha(if (enabled) 1f else 0.3f)
            .then(if (enabled) Modifier.clickable(onClickLabel = contentDescription, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        SidekitIconImage(
            SidekitIcon.ArrowUpward,
            contentDescription = contentDescription,
            modifier = if (up) Modifier else Modifier.graphicsLayer { rotationZ = 180f },
        )
    }
}