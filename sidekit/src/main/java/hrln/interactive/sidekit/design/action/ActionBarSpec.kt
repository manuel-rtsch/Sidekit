package hrln.interactive.sidekit.design.action

import androidx.compose.runtime.Immutable

/** Which edge of the action bar owns a slot. */
internal enum class ActionPlacement {
    /** Normal actions, laid out from the leading edge. */
    Leading,

    /** Actions pinned to the trailing edge, such as Options' close button. */
    Trailing
}

/** A slot on its way into the layout model: the action plus the zone it belongs to. */
internal data class ActionTarget(val action: SideAction, val placement: ActionPlacement)

/**
 * The two zones of the action bar. [leading] holds the screen's ordinary actions; [trailing] holds
 * actions that must end at the right edge. The zones are flattened in this order for keyed slot
 * reuse and D-pad order, so a trailing action is always after every leading one.
 */
@Immutable
internal data class ActionBarSpec(
    val leading: List<SideAction> = emptyList(),
    val trailing: List<SideAction> = emptyList()
) {
    val isEmpty: Boolean get() = leading.isEmpty() && trailing.isEmpty()

    /** Flattened order the bar model and animation use. */
    fun targets(): List<ActionTarget> = buildList(leading.size + trailing.size) {
        leading.forEach { add(ActionTarget(it, ActionPlacement.Leading)) }
        trailing.forEach { add(ActionTarget(it, ActionPlacement.Trailing)) }
    }

    /** Flattened actions, for consumers that only need keys or click handlers. */
    fun actions(): List<SideAction> = leading + trailing
}
