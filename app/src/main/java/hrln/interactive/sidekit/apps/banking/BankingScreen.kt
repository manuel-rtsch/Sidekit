package hrln.interactive.sidekit.apps.banking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.design.action.ActionMotion
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.action.undoBar
import hrln.interactive.sidekit.design.components.ItemColour
import hrln.interactive.sidekit.design.components.SidekitCard
import hrln.interactive.sidekit.design.components.SidekitItem
import hrln.interactive.sidekit.design.components.SidekitList
import hrln.interactive.sidekit.design.components.SidekitScaffold
import hrln.interactive.sidekit.design.components.SidekitSwipeRow
import hrln.interactive.sidekit.design.components.SidekitText
import hrln.interactive.sidekit.design.components.SidekitTextField
import hrln.interactive.sidekit.design.components.SidekitTile
import hrln.interactive.sidekit.design.components.listFade
import hrln.interactive.sidekit.design.components.rememberSwipeRevealGroup
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.ui.SidekitPreviewFrame
import hrln.interactive.sidekit.ui.SidephonePreview
import kotlinx.coroutines.flow.filter

/** What the home screen shows of an account (already formatted). */
data class BankAccount(val name: String, val balance: String, val caption: String)

data class BankTransaction(val description: String, val amount: String, val id: Long = 0)

object BankingSampleData {
    // Placeholder content from "Banking / Home" (96:959).
    val account = BankAccount("Everyday · 4821", "$2,480.50", "Available balance")
    val transactions = listOf(
        BankTransaction("Groceries", "−$64.20", 1),
        BankTransaction("Salary", "+$2,150.00", 2)
    )
}

object BankingTags {
    const val PAYEE = "banking_payee"
    const val AMOUNT = "banking_amount"
    const val NOTE = "banking_note"
    const val ACCOUNT_NAME = "banking_account_name"
    const val ACCOUNT_OPENING = "banking_account_opening"
}

/**
 * Undo split shared by every Banking list: the main action holds the left slot and the right slot
 * offers "Undo delete Barclays" until the window closes. Nothing else is taken away.
 */
@Composable
private fun withUndo(undo: BankingUndo?, onUndo: () -> Unit, main: List<SideAction>): List<SideAction> = if (undo == null) {
    main
} else {
    undoBar(
        continueWith = main.last(),
        undo = SideAction.undo(contentDescription = undo.label(), onClick = onUndo)
    )
}

/**
 * "Banking / Home" (96:959): the selected account's balance, then its transactions (swipe left
 * to delete, with undo in the bar). "← Accounts" and "Pay →" lead to the account list and the
 * payment recorder. With no accounts yet the bar offers to add one.
 */
@Composable
fun BankingScreen(
    account: BankAccount?,
    transactions: List<BankTransaction>,
    onAccounts: () -> Unit = {},
    onPay: () -> Unit = {},
    onDeleteTransaction: ((BankTransaction) -> Unit)? = null,
    undo: BankingUndo? = null,
    onUndo: () -> Unit = {},
    options: List<SideAction> = emptyList()
) {
    val actions = when {
        account == null -> listOf(SideAction.forward("forward", stringResource(R.string.banking_add_account), onAccounts))

        else -> withUndo(
            undo,
            onUndo,
            listOf(
                SideAction.back("back", stringResource(R.string.banking_accounts), onAccounts),
                SideAction.forward("forward", stringResource(R.string.banking_pay), onPay)
            )
        )
    }
    val trackerNotice = stringResource(R.string.banking_tracker_notice)
    val homeEmpty = stringResource(R.string.banking_home_empty)
    SidekitScaffold(title = stringResource(R.string.banking_title), actions = actions, options = options) { padding ->
        val listState = rememberLazyListState()
        val swipe = rememberSwipeRevealGroup()
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { swipe.closeAll() }
        }
        SidekitList(padding, state = listState) {
            item(key = "balance", contentType = "balance") {
                if (account != null) {
                    BalanceCard(account, Modifier.listFade(listState, 0))
                } else {
                    NoticeCard(homeEmpty, Modifier.listFade(listState, 0))
                }
            }
            itemsIndexed(transactions, key = { _, t -> t.id }, contentType = { _, _ -> "tx" }) { i, t ->
                val row = @Composable { SidekitItem(t.description, secondary = t.amount) }
                if (onDeleteTransaction == null) {
                    Column(Modifier.listFade(listState, i + 1)) { row() }
                } else {
                    SidekitSwipeRow(
                        key = t.id,
                        group = swipe,
                        onDelete = { onDeleteTransaction(t) },
                        modifier = Modifier.animateItem().listFade(listState, i + 1),
                        deleteLabel = stringResource(R.string.banking_delete_item, t.description)
                    ) { row() }
                }
            }
            if (account != null) {
                item(key = "notice", contentType = "notice") {
                    NoticeCard(trackerNotice, Modifier.listFade(listState, transactions.size + 1))
                }
            }
        }
    }
}

/** "Everyday account balance" (96:1012): 171 high, texts at y 18, 58 and 137. */
@Composable
private fun BalanceCard(account: BankAccount, modifier: Modifier = Modifier) {
    val colors = SidekitTheme.colors
    SidekitTile(modifier.fillMaxWidth().height(171.dp), contentAlignment = Alignment.TopStart) {
        Column(
            Modifier.padding(start = SidekitTheme.spacing.itemPadding, end = SidekitTheme.spacing.itemPadding, top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SidekitText(account.name, style = SidekitTheme.typography.caption, color = colors.surfaceA6)
            SidekitText(account.balance, style = SidekitTheme.typography.numeric)
            SidekitText(account.caption, style = SidekitTheme.typography.caption, color = colors.surfaceA6)
        }
    }
}

@Composable
private fun NoticeCard(text: String, modifier: Modifier = Modifier) {
    SidekitCard(modifier.fillMaxWidth(), color = SidekitTheme.colors.surfaceA2) {
        SidekitText(text, style = SidekitTheme.typography.caption, color = SidekitTheme.colors.surfaceA6, maxLines = Int.MAX_VALUE)
    }
}

/** Home screen state from the book. [caption] is the line under the balance. */
internal fun homeAccount(book: BankBook, caption: String): BankAccount? = book.selected?.let {
    BankAccount(it.name, Money.format(book.balance(it.id)), caption)
}

internal fun homeTransactions(book: BankBook): List<BankTransaction> {
    val account = book.selected ?: return emptyList()
    return book.transactionsOf(account.id).map { BankTransaction(it.payee, Money.format(it.cents, signed = true), it.id) }
}

/**
 * Every account with its balance. Tapping one shows it on the home screen; swipe to delete (its
 * transactions go too), with undo. "New →" adds an account.
 */
@Composable
fun BankingAccountsScreen(
    book: BankBook,
    onBack: () -> Unit,
    onOpen: (Account) -> Unit,
    onNew: () -> Unit,
    onEdit: (Account) -> Unit,
    onDelete: (Account) -> Unit,
    undo: BankingUndo?,
    onUndo: () -> Unit
) {
    BackHandler(onBack = onBack)
    val selected = book.selected
    val accountsEmpty = stringResource(R.string.banking_accounts_empty)
    SidekitScaffold(
        title = stringResource(R.string.banking_accounts),
        actions = withUndo(
            undo,
            onUndo,
            listOf(
                SideAction.back("back", stringResource(R.string.banking_title), onBack),
                SideAction.forward("forward", stringResource(R.string.banking_new), onNew)
            )
        ),
        options = listOfNotNull(selected?.let { SideAction("edit", stringResource(R.string.banking_edit_item, it.name), SidekitIcon.Edit) { onEdit(it) } })
    ) { padding ->
        val listState = rememberLazyListState()
        val swipe = rememberSwipeRevealGroup()
        SidekitList(padding, state = listState) {
            if (book.accounts.isEmpty()) {
                item(key = "empty") { NoticeCard(accountsEmpty, Modifier.listFade(listState, 0)) }
            }
            itemsIndexed(book.accounts, key = { _, a -> a.id }, contentType = { _, _ -> "account" }) { i, a ->
                SidekitSwipeRow(
                    key = a.id,
                    group = swipe,
                    onDelete = { onDelete(a) },
                    modifier = Modifier.animateItem().listFade(listState, i),
                    deleteLabel = stringResource(R.string.banking_delete_item, a.name)
                ) {
                    SidekitItem(
                        a.name,
                        secondary = Money.format(book.balance(a.id)),
                        colour = if (a.id == selected?.id) ItemColour.Secondary else ItemColour.Primary,
                        onClick = { onOpen(a) }
                    )
                }
            }
        }
    }
}

/** A labelled one-line T9 field on a card, as used by Banking's forms. */
@Composable
internal fun FieldCard(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    tag: String,
    focus: FocusRequester,
    onDone: () -> Unit,
    autoFocus: Boolean = false,
    focusOnKeypad: Boolean = false
) {
    SidekitCard(Modifier.fillMaxWidth().heightIn(min = SidekitTheme.spacing.itemHeight)) {
        SidekitTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart).testTag(tag),
            placeholder = placeholder,
            focusRequester = focus,
            focusOnKeypad = focusOnKeypad,
            singleLine = true,
            onDone = onDone,
            autoFocus = autoFocus
        )
    }
}

/**
 * Records a payment (money out) or, with [incoming], money in. The bar narrates: "← Cancel" until
 * the form is valid, then the back arrow shrinks to a square and a green "Record ✓" splits out.
 */
@Composable
fun BankingPayScreen(
    book: BankBook,
    incoming: Boolean,
    onCancel: () -> Unit,
    onRecord: (accountId: Long, payee: String, cents: Long, note: String) -> Unit
) {
    var accountId by rememberSaveable { mutableStateOf(book.selected?.id ?: -1L) }
    var payee by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val account = book.accounts.firstOrNull { it.id == accountId } ?: book.selected
    val cents = Money.parse(amount)
    val valid = account != null && cents != null
    val payeeFocus = remember { FocusRequester() }
    val amountFocus = remember { FocusRequester() }
    val noteFocus = remember { FocusRequester() }
    val record = { if (account != null && cents != null) onRecord(account.id, payee, cents, note) }

    BackHandler(onBack = onCancel)
    val actions = if (valid) {
        listOf(
            SideAction.square("back", SidekitIcon.ArrowLeftAlt, stringResource(R.string.banking_cancel), onCancel, tone = ActionTone.Muted, motion = ActionMotion.Back),
            SideAction(
                "record",
                stringResource(R.string.banking_record),
                SidekitIcon.Check,
                iconAtEnd = true,
                tone = ActionTone.Secondary,
                motion = ActionMotion.Up,
                contentDescription = stringResource(if (incoming) R.string.banking_record_income else R.string.banking_record_payment),
                onClick = record
            )
        )
    } else {
        listOf(SideAction.cancel("back", onClick = onCancel))
    }
    val fromLabel = stringResource(if (incoming) R.string.banking_into else R.string.banking_from)
    val noAccount = stringResource(R.string.banking_no_account)
    val payeeHint = stringResource(if (incoming) R.string.banking_received_from else R.string.banking_pay_to)
    val amountHint = stringResource(R.string.banking_amount_hint, Money.format(0))
    val noteHint = stringResource(R.string.banking_note_hint)
    val payNotice = stringResource(R.string.banking_pay_notice)
    SidekitScaffold(title = stringResource(if (incoming) R.string.banking_add_income else R.string.banking_pay), actions = actions) { padding ->
        val listState = rememberLazyListState()
        SidekitList(padding, state = listState) {
            item(key = "from") {
                SidekitItem(
                    fromLabel,
                    Modifier.listFade(listState, 0),
                    secondary = account?.let { "${it.name} · ${Money.format(book.balance(it.id))}" } ?: noAccount,
                    // Tapping steps through the accounts.
                    onClick = if (book.accounts.size > 1) {
                        {
                            val i = book.accounts.indexOfFirst { it.id == account?.id }
                            accountId = book.accounts[(i + 1) % book.accounts.size].id
                        }
                    } else {
                        null
                    }
                )
            }
            item(key = "payee") {
                Column(Modifier.listFade(listState, 1)) {
                    FieldCard(payee, { payee = it }, payeeHint, BankingTags.PAYEE, payeeFocus, { amountFocus.requestFocus() }, autoFocus = true, focusOnKeypad = true)
                }
            }
            item(key = "amount") {
                Column(Modifier.listFade(listState, 2)) {
                    FieldCard(amount, { amount = it }, amountHint, BankingTags.AMOUNT, amountFocus, { noteFocus.requestFocus() })
                }
            }
            item(key = "note") {
                Column(Modifier.listFade(listState, 3)) {
                    FieldCard(note, { note = it }, noteHint, BankingTags.NOTE, noteFocus, { if (valid) record() })
                }
            }
            item(key = "notice") {
                NoticeCard(payNotice, Modifier.listFade(listState, 4))
            }
        }
    }
}

/** New account (or edit, with [existing]): name and the balance it has now. */
@Composable
fun BankingAccountEditor(
    existing: Account?,
    onCancel: () -> Unit,
    onSave: (name: String, openingCents: Long) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var opening by rememberSaveable { mutableStateOf(existing?.let { Money.plain(it.openingCents) }.orEmpty()) }
    val cents = Money.parseBalance(opening)
    val valid = name.isNotBlank() && cents != null
    val nameFocus = remember { FocusRequester() }
    val openingFocus = remember { FocusRequester() }
    val save = { if (valid) onSave(name, cents ?: 0L) }

    BackHandler(onBack = onCancel)
    val actions = if (valid) {
        listOf(
            SideAction.square("back", SidekitIcon.ArrowLeftAlt, stringResource(R.string.banking_cancel), onCancel, tone = ActionTone.Muted, motion = ActionMotion.Back),
            SideAction(
                "save",
                stringResource(R.string.banking_save),
                SidekitIcon.Check,
                iconAtEnd = true,
                tone = ActionTone.Secondary,
                motion = ActionMotion.Up,
                contentDescription = stringResource(R.string.banking_save_account),
                onClick = save
            )
        )
    } else {
        listOf(SideAction.cancel("back", onClick = onCancel))
    }
    val nameHint = stringResource(R.string.banking_account_name)
    val openingHint = if (existing == null) stringResource(R.string.banking_balance_now, Money.format(0)) else stringResource(R.string.banking_starting_balance)
    val hint = stringResource(if (existing == null) R.string.banking_new_account_hint else R.string.banking_edit_account_hint)
    SidekitScaffold(title = stringResource(if (existing == null) R.string.banking_new_account else R.string.banking_edit_account), actions = actions) { padding ->
        val listState = rememberLazyListState()
        SidekitList(padding, state = listState) {
            item(key = "name") {
                Column(Modifier.listFade(listState, 0)) {
                    FieldCard(name, { name = it }, nameHint, BankingTags.ACCOUNT_NAME, nameFocus, { openingFocus.requestFocus() }, autoFocus = existing == null, focusOnKeypad = true)
                }
            }
            item(key = "opening") {
                Column(Modifier.listFade(listState, 1)) {
                    FieldCard(opening, { opening = it }, openingHint, BankingTags.ACCOUNT_OPENING, openingFocus, save)
                }
            }
            item(key = "hint") {
                NoticeCard(hint, Modifier.listFade(listState, 2))
            }
        }
    }
}

/**
 * Shown until the user passes the screen lock. Asks immediately on arrival; "Unlock" asks again.
 * On a phone with no screen lock there's nothing to ask, so it says so and opens on request.
 */
@Composable
fun BankingLockedScreen(
    deviceSecure: Boolean,
    message: String?,
    onUnlock: () -> Unit,
    onBack: () -> Unit
) {
    val status = stringResource(if (deviceSecure) R.string.banking_locked else R.string.banking_no_screen_lock)
    val notice = message ?: stringResource(if (deviceSecure) R.string.banking_locked_notice else R.string.banking_no_lock_notice)
    val trackerNotice = stringResource(R.string.banking_tracker_notice)
    SidekitScaffold(
        title = stringResource(R.string.banking_title),
        actions = listOf(
            SideAction.square("back", SidekitIcon.ArrowLeftAlt, stringResource(R.string.banking_leave_banking), onBack, motion = ActionMotion.Back),
            SideAction(
                "unlock",
                stringResource(if (deviceSecure) R.string.banking_unlock else R.string.banking_open),
                SidekitIcon.Encrypted,
                contentDescription = stringResource(R.string.banking_unlock_banking),
                onClick = onUnlock
            )
        )
    ) { padding ->
        val listState = rememberLazyListState()
        SidekitList(padding, state = listState) {
            item {
                SidekitItem(status, Modifier.listFade(listState, 0), icon = SidekitIcon.Encrypted)
            }
            item {
                NoticeCard(notice, Modifier.listFade(listState, 1))
            }
            item { NoticeCard(trackerNotice, Modifier.listFade(listState, 2)) }
        }
    }
}

/** The encrypted file couldn't be read (e.g. its key was lost with a factory reset of the keystore). */
@Composable
fun BankingUnreadableScreen(onBack: () -> Unit, onErase: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    BackHandler { if (confirming) confirming = false else onBack() }
    val actions = if (confirming) {
        listOf(
            SideAction.cancel(icon = null, motion = ActionMotion.Up, contentDescription = stringResource(R.string.banking_keep_file)) { confirming = false },
            SideAction(
                "erase",
                stringResource(R.string.banking_erase),
                SidekitIcon.DeleteForever,
                tone = ActionTone.Destructive,
                weight = 1.2f,
                motion = ActionMotion.Up,
                contentDescription = stringResource(R.string.banking_confirm_erase),
                onClick = onErase
            )
        )
    } else {
        listOf(SideAction.back("back", stringResource(R.string.banking_leave), onBack))
    }
    val unreadable = stringResource(R.string.banking_unreadable)
    SidekitScaffold(
        title = stringResource(R.string.banking_title),
        actions = actions,
        options = listOf(SideAction("erase", stringResource(R.string.banking_erase_start_over), SidekitIcon.DeleteForever, tone = ActionTone.Destructive) { confirming = true })
    ) { padding ->
        val listState = rememberLazyListState()
        SidekitList(padding, state = listState) {
            item {
                NoticeCard(unreadable, Modifier.listFade(listState, 0))
            }
        }
    }
}

@SidephonePreview
@Composable
private fun BankingPreview() = SidekitPreviewFrame { BankingScreen(BankingSampleData.account, BankingSampleData.transactions) }

@SidephonePreview
@Composable
private fun BankingPayPreview() = SidekitPreviewFrame {
    BankingPayScreen(BankBook(listOf(Account(1, "Everyday", 248050, 0)), selectedAccountId = 1), false, {}, { _, _, _, _ -> })
}
