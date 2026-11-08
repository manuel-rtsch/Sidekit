package hrln.interactive.sidekit.design.action

/**
 * The bar while a delete can still be taken back.
 *
 * Undo is an offer, not a mode, so it never removes the thing the screen is for: the bar keeps two
 * slots, the left one holds the continuation ([continueWith]) and the right one — which is empty
 * until something can be undone — shows [undo] for as long as the window lasts. The undo button is
 * only ever added, never substituted, and always lands on the same side.
 *
 * When a screen has both a way back and a primary action the primary wins the left slot, so the
 * exit button steps aside for the window; the system back still leaves, and the exit returns with
 * the primary.
 */
fun undoBar(continueWith: SideAction, undo: SideAction? = null): List<SideAction> =
    if (undo == null) listOf(continueWith) else listOf(continueWith, undo)
