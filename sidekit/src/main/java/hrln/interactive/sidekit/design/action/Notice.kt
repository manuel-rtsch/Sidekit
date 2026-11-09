package hrln.interactive.sidekit.design.action

/**
 * How long a notice stays before the bar returns.
 *
 * Success is short because the user already knows what they did; everything else gets enough time
 * to be read. Tapping the notice always dismisses it early.
 */
enum class NoticeKind(val autoDismissMs: Long) {
    /** A confirmation: the action succeeded. */
    Success(2_000),

    /** Information, warnings and errors. */
    Other(5_000)
}

/**
 * The bar while a transient message is showing.
 *
 * A notice replaces the screen's actions with one dense Muted button: the message is where the
 * user is already looking, and tapping it dismisses. Use it for transient success/error feedback;
 * use [undoBar] for reversible deletion, and a body row only for persistent explanation.
 *
 * The bar returns on its own after [NoticeKind.autoDismissMs], so a notice never blocks the
 * workflow for longer than it takes to read.
 */
fun withNotice(
    notice: String?,
    onDismiss: () -> Unit,
    actions: List<SideAction>,
    kind: NoticeKind = NoticeKind.Other
): List<SideAction> = if (notice == null) actions else listOf(SideAction.notice(message = notice, kind = kind, onClick = onDismiss))
