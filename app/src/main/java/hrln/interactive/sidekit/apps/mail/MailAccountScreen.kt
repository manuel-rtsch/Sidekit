package hrln.interactive.sidekit.apps.mail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.*
import kotlinx.coroutines.launch

@Composable
fun MailAccountScreen(account: ImapSmtpAccount, onBack: () -> Unit, onConnected: () -> Unit) {
    val config = remember { runCatching { account.config() }.getOrNull() }
    var email by remember { mutableStateOf(config?.email.orEmpty()) }
    var username by remember { mutableStateOf(config?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var imap by remember { mutableStateOf(config?.imapHost.orEmpty()) }
    var imapPort by remember { mutableStateOf(config?.imapPort?.toString() ?: "993") }
    var smtp by remember { mutableStateOf(config?.smtpHost.orEmpty()) }
    var port by remember { mutableStateOf(config?.smtpPort?.toString() ?: "465") }
    var message by remember { mutableIntStateOf(R.string.mail_account_intro) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    SidekitScaffold(
        stringResource(R.string.mail_account_title),
        actions = listOf(
            SideAction.back("back", stringResource(R.string.mail_back), onBack),
            SideAction("connect", stringResource(if (busy) R.string.mail_account_connecting else R.string.mail_account_connect)) {
                if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            account.connect(MailConfig(email.trim(), username.trim().ifBlank { email.trim() }, password.ifEmpty { config?.password.orEmpty() }, imap.trim(), smtp.trim(), port.toIntOrNull() ?: 0, imapPort.toIntOrNull() ?: 0))
                            password = ""
                            onConnected()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            message = R.string.mail_account_failed
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        ),
        options = listOf(
            SideAction("disconnect", stringResource(R.string.mail_account_disconnect)) {
                account.disconnect()
                onBack()
            }
        )
    ) { padding ->
        SidekitList(padding) {
            item { SidekitCard(Modifier.fillMaxWidth()) { SidekitText(stringResource(message), maxLines = Int.MAX_VALUE) } }
            item { SidekitTextField(email, { email = it }, Modifier.fillMaxWidth(), placeholder = stringResource(R.string.mail_account_email), singleLine = true) }
            item { SidekitTextField(username, { username = it }, Modifier.fillMaxWidth(), placeholder = stringResource(R.string.mail_account_username), singleLine = true) }
            item { hrln.interactive.sidekit.apps.integrations.SecretField(password, { password = it }, stringResource(if (config == null) R.string.mail_account_password else R.string.mail_account_password_keep)) }
            item { SidekitTextField(imap, { imap = it }, Modifier.fillMaxWidth(), placeholder = stringResource(R.string.mail_account_imap_host), singleLine = true) }
            item { SidekitTextField(imapPort, { imapPort = it }, Modifier.fillMaxWidth(), placeholder = stringResource(R.string.mail_account_imap_port), singleLine = true, dictation = false) }
            item { SidekitTextField(smtp, { smtp = it }, Modifier.fillMaxWidth(), placeholder = stringResource(R.string.mail_account_smtp_host), singleLine = true) }
            item { SidekitTextField(port, { port = it }, Modifier.fillMaxWidth(), placeholder = stringResource(R.string.mail_account_smtp_port), singleLine = true, dictation = false) }
        }
    }
}
