package hrln.interactive.sidekit.design.action

/**
 * The bar while a transient message is showing.
 *
 * A notice replaces the screen's actions with one dense Muted button: the message is where the
 * user is already looking, and tapping it dismisses. Use it for transient success/error feedback;
 * use [undoBar] for reversible deletion, and a body row only for persistent explanation.
 */
fun withNotice(notice: String?, onDismiss: () -> Unit, actions: List<SideAction>): List<SideAction> = if (notice == null) actions else listOf(SideAction.notice(message = notice, onClick = onDismiss))
