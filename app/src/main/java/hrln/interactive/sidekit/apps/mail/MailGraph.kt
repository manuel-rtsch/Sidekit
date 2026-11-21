package hrln.interactive.sidekit.apps.mail

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.NoticeKind
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.action.undoBar
import hrln.interactive.sidekit.design.action.withNotice
import hrln.interactive.sidekit.design.components.SidekitCard
import hrln.interactive.sidekit.design.components.SidekitItem
import hrln.interactive.sidekit.design.components.SidekitList
import hrln.interactive.sidekit.design.components.SidekitScaffold
import hrln.interactive.sidekit.design.components.SidekitSwipeRow
import hrln.interactive.sidekit.design.components.SidekitText
import hrln.interactive.sidekit.design.components.SidekitTextField
import hrln.interactive.sidekit.design.components.listFade
import hrln.interactive.sidekit.design.components.rememberSwipeRevealGroup
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.navigation.SubApp
import hrln.interactive.sidekit.sdk.ui.UndoWindowLifecycle

private val ROOT = SubApp.Mail.route
private val HOME = "$ROOT/home"
private val FOLDERS = "$ROOT/folders"
private val FOLDER = "$ROOT/folder/{folder}"
private val COMPOSE = "$ROOT/compose?id={id}"
object MailTags {
    const val TO = "mail_to"
    const val SUBJECT = "mail_subject"
    const val BODY = "mail_body"
}

fun NavGraphBuilder.mailGraph(nav: NavHostController) {
    navigation(startDestination = HOME, route = ROOT) {
        composable(HOME) {
            val vm = mailViewModel()
            val messages by vm.messages.collectAsStateWithLifecycle()
            val storedNotice by vm.notice.collectAsStateWithLifecycle()
            val storedNoticeKind by vm.noticeKind.collectAsStateWithLifecycle()
            val storageError by vm.storageError.collectAsStateWithLifecycle()
            val notice = (storageError ?: storedNotice)?.let { stringResource(it) }
            val noticeKind = if (storageError != null) NoticeKind.Other else storedNoticeKind
            val undo by vm.undo.collectAsStateWithLifecycle()
            UndoWindowLifecycle(vm.undoWindow, vm::commitUndo)
            val actions = mailActions(
                notice,
                noticeKind,
                vm::dismissNotice,
                undo != null,
                vm::undo,
                SideAction.back("folders", stringResource(R.string.mail_folders)) { nav.navigate(FOLDERS) },
                SideAction.forward("compose", stringResource(R.string.mail_compose), onClick = { nav.navigate("$ROOT/compose") }),
                stringResource(R.string.mail_undo_delete)
            )
            SidekitScaffold(
                title = stringResource(R.string.mail_title),
                actions = actions,
                options = listOf(SideAction("refresh", stringResource(R.string.mail_refresh_inbox)) { vm.refresh() })
            ) { padding ->
                MessageList(padding, messages, null, vm::delete, onAccount = { nav.navigate("$ROOT/account") }) { nav.navigate(if (it.folder == MailFolder.Inbox || it.folder == MailFolder.Sent) "$ROOT/read/${it.id}" else "$ROOT/compose?id=${it.id}") }
            }
        }
        composable("$ROOT/account") {
            val vm = mailViewModel()
            MailAccountScreen(vm.account as ImapSmtpAccount, { nav.popBackStack() }, {
                vm.refresh()
                nav.popBackStack()
            })
        }
        composable("$ROOT/read/{id}") { entry ->
            val vm = mailViewModel()
            val messages by vm.messages.collectAsStateWithLifecycle()
            val message = messages?.firstOrNull { it.id == entry.arguments?.getString("id")?.toLongOrNull() }
            SidekitScaffold(
                stringResource(R.string.mail_title),
                actions = listOf(
                    SideAction.back("back", stringResource(R.string.mail_back)) { nav.popBackStack() },
                    SideAction("reply", stringResource(R.string.mail_reply)) {
                        message?.let {
                            vm.saveDraft(null, runCatching { javax.mail.internet.InternetAddress.parse(it.sender).firstOrNull()?.address.orEmpty() }.getOrDefault(""), "Re: ${it.subject}", "")
                            nav.navigate("$ROOT/compose?id=${vm.messages.value?.firstOrNull()?.id}")
                        }
                    }
                )
            ) { padding ->
                SidekitList(padding) {
                    item {
                        SidekitCard(Modifier.fillMaxWidth()) {
                            Column {
                                SidekitText(message?.sender ?: stringResource(R.string.mail_message_unavailable), maxLines = Int.MAX_VALUE)
                                SidekitText(message?.subject.orEmpty(), maxLines = Int.MAX_VALUE)
                            }
                        }
                    }
                    item { SidekitText(message?.body.orEmpty(), maxLines = Int.MAX_VALUE) }
                }
            }
        }
        composable(FOLDERS) {
            val vm = mailViewModel()
            val messages by vm.messages.collectAsStateWithLifecycle()
            SidekitScaffold(title = stringResource(R.string.mail_folders_title), actions = listOf(SideAction.back("back", stringResource(R.string.mail_title)) { nav.popBackStack() })) { padding ->
                val state = rememberLazyListState()
                SidekitList(padding, state = state) {
                    val folders = listOf(MailFolder.Inbox, MailFolder.Draft, MailFolder.Outbox, MailFolder.Sent, MailFolder.HandedOff)
                    itemsIndexed(folders, key = { _, it -> it.name }) { index, folder ->
                        val count = messages.orEmpty().count { it.folder == folder }
                        SidekitItem(stringResource(folder.label), Modifier.listFade(state, index), secondary = pluralStringResource(R.plurals.mail_folder_count, count, count), icon = SidekitIcon.FolderCopy, showLinkArrow = true) {
                            nav.navigate("$ROOT/folder/${folder.name}")
                        }
                    }
                }
            }
        }
        composable(FOLDER) { entry ->
            val vm = mailViewModel()
            val messages by vm.messages.collectAsStateWithLifecycle()
            val storedNotice by vm.notice.collectAsStateWithLifecycle()
            val storedNoticeKind by vm.noticeKind.collectAsStateWithLifecycle()
            val storageError by vm.storageError.collectAsStateWithLifecycle()
            val notice = (storageError ?: storedNotice)?.let { stringResource(it) }
            val noticeKind = if (storageError != null) NoticeKind.Other else storedNoticeKind
            val undo by vm.undo.collectAsStateWithLifecycle()
            UndoWindowLifecycle(vm.undoWindow, vm::commitUndo)
            val folder = MailFolder.valueOf(entry.arguments?.getString("folder") ?: MailFolder.Draft.name)
            val actions = mailActions(
                notice,
                noticeKind,
                vm::dismissNotice,
                undo != null,
                vm::undo,
                SideAction.back("back", stringResource(R.string.mail_folders)) { nav.popBackStack() },
                SideAction.forward("compose", stringResource(R.string.mail_compose), onClick = { nav.navigate("$ROOT/compose") }),
                stringResource(R.string.mail_undo_delete)
            )
            SidekitScaffold(title = stringResource(folder.label), actions = actions) { padding ->
                MessageList(padding, messages, folder, vm::delete) { nav.navigate(if (it.folder == MailFolder.Inbox || it.folder == MailFolder.Sent) "$ROOT/read/${it.id}" else "$ROOT/compose?id=${it.id}") }
            }
        }
        composable(
            COMPOSE,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { entry ->
            val vm = mailViewModel()
            val messages by vm.messages.collectAsStateWithLifecycle()
            val id = entry.arguments?.getLong("id")?.takeIf { it >= 0 }
            val existing = messages.orEmpty().firstOrNull { it.id == id }
            if (messages == null) return@composable
            ComposeScreen(existing, onCancel = { nav.popBackStack() }, onSave = { to, subject, body ->
                vm.saveDraft(id, to, subject, body)
                nav.popBackStack()
            }, onSend = { to, subject, body ->
                vm.send(id, to, subject, body)
                nav.popBackStack(HOME, inclusive = false)
            })
        }
    }
}

@Composable
private fun mailViewModel(): MailViewModel {
    val activity = LocalActivity.current as ComponentActivity
    val context = activity.applicationContext
    val factory = remember(context) { viewModelFactory { initializer { MailViewModel(Mail.repository(context), ImapSmtpAccount(hrln.interactive.sidekit.apps.integrations.PrivateSettings(context, "mail_account"))) } } }
    return viewModel(activity, factory = factory)
}

private fun mailActions(
    notice: String?,
    noticeKind: NoticeKind,
    dismiss: () -> Unit,
    hasUndo: Boolean,
    undo: () -> Unit,
    first: SideAction,
    second: SideAction,
    undoDescription: String
): List<SideAction> = when {
    notice != null -> withNotice(notice, dismiss, emptyList(), noticeKind)
    hasUndo -> undoBar(continueWith = second, undo = SideAction.undo(contentDescription = undoDescription, onClick = undo))
    else -> listOf(first, second)
}

@Composable
private fun MessageList(
    padding: PaddingValues,
    messages: List<MailMessage>?,
    folder: MailFolder?,
    onDelete: (Long) -> Unit,
    onAccount: (() -> Unit)? = null,
    onOpen: (MailMessage) -> Unit
) {
    val shown = messages.orEmpty().filter { folder == null || it.folder == folder }
    val state = rememberLazyListState()
    val swipe = rememberSwipeRevealGroup()
    SidekitList(padding, state = state) {
        item(key = "notice") {
            SidekitCard(Modifier.fillMaxWidth().listFade(state, 0), color = SidekitTheme.colors.surfaceA2) {
                SidekitText(stringResource(R.string.mail_cache_notice), style = SidekitTheme.typography.caption, color = SidekitTheme.colors.surfaceA6, maxLines = Int.MAX_VALUE)
            }
        }
        if (onAccount != null) {
            item(key = "account") {
                SidekitItem(stringResource(R.string.mail_account), Modifier.listFade(state, 1), showLinkArrow = true, onClick = onAccount)
            }
        }
        if (messages != null && shown.isEmpty()) {
            item(key = "empty") {
                SidekitItem(stringResource(folder?.empty ?: R.string.mail_empty_local), Modifier.listFade(state, 2), secondary = stringResource(R.string.mail_empty_hint))
            }
        }
        itemsIndexed(shown, key = { _, it -> it.id }) { index, message ->
            SidekitSwipeRow(key = message.id, group = swipe, onDelete = { onDelete(message.id) }, modifier = Modifier.animateItem().listFade(state, index + 2), deleteLabel = stringResource(R.string.mail_delete_message)) {
                SidekitItem((if (message.folder == MailFolder.Inbox) message.sender else message.to).ifEmpty { stringResource(R.string.mail_no_recipient) }, secondary = "${stringResource(message.folder.label)} · ${message.subject.ifEmpty { stringResource(R.string.mail_no_subject) }}", onClick = { onOpen(message) })
            }
        }
    }
}

@Composable
private fun ComposeScreen(
    existing: MailMessage?,
    onCancel: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onSend: (String, String, String) -> Unit
) {
    var to by remember(existing?.id) { mutableStateOf(existing?.to.orEmpty()) }
    var subject by remember(existing?.id) { mutableStateOf(existing?.subject.orEmpty()) }
    var body by remember(existing?.id) { mutableStateOf(existing?.body.orEmpty()) }
    BackHandler(onBack = onCancel)
    SidekitScaffold(
        title = stringResource(existing?.folder?.label ?: R.string.mail_compose),
        actions = listOf(
            SideAction.cancel("cancel", onClick = onCancel),
            SideAction("save", stringResource(R.string.mail_save), tone = ActionTone.Muted) { onSave(to, subject, body) },
            SideAction.send("send") { onSend(to, subject, body) }
        )
    ) { padding ->
        val state = rememberLazyListState()
        SidekitList(padding, state = state) {
            item(key = "to") { MailField(stringResource(R.string.mail_to), to, { to = it }, Modifier.listFade(state, 0).testTag(MailTags.TO), true, true) }
            item(key = "subject") { MailField(stringResource(R.string.mail_subject), subject, { subject = it }, Modifier.listFade(state, 1).testTag(MailTags.SUBJECT), true, false) }
            item(key = "body") { MailField(stringResource(R.string.mail_message), body, { body = it }, Modifier.listFade(state, 2).testTag(MailTags.BODY), false, false) }
            item(key = "handoff") {
                SidekitText(stringResource(R.string.mail_send_hint), Modifier.listFade(state, 3), style = SidekitTheme.typography.caption, color = SidekitTheme.colors.surfaceA6, maxLines = Int.MAX_VALUE)
            }
        }
    }
}

@Composable
private fun MailField(
    placeholder: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier,
    singleLine: Boolean,
    autoFocus: Boolean
) {
    SidekitCard(modifier.fillMaxWidth().heightIn(min = SidekitTheme.spacing.itemHeight), color = SidekitTheme.colors.surfaceA2) {
        SidekitTextField(value, onValue, Modifier.fillMaxWidth(), placeholder = placeholder, singleLine = singleLine, focusOnKeypad = autoFocus, autoFocus = autoFocus)
    }
}
