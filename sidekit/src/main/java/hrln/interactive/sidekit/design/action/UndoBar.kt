package hrln.interactive.sidekit.design.action

/**
 * The bar while a delete can still be taken back.
 *
 * The bar keeps its two slots and never grows or hides the exit: the left slot is always the way
 * out, and for a few seconds the right slot promises [undo] instead of [primary] — the promise on
 * the table right now is "put it back". The primary action returns when the undo window closes.
 *
 * Pass [keepPrimary] where the primary is holding typed work (a draft's Send), so the undo becomes
 * a third button instead of stranding the text. Pass [trailing] for fixed companions (a locate or
 * permission action) that stay put either way.
 */
fun undoBar(
    exit: SideAction? = null,
    primary: SideAction? = null,
    undo: SideAction? = null,
    keepPrimary: Boolean = false,
    trailing: List<SideAction> = emptyList(),
): List<SideAction> = when {
    undo == null -> listOfNotNull(exit, primary) + trailing
    keepPrimary -> listOfNotNull(exit, primary, undo) + trailing
    else -> listOfNotNull(exit, undo) + trailing
}
