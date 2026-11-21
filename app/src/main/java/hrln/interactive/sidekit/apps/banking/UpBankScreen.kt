package hrln.interactive.sidekit.apps.banking

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.apps.integrations.PrivateSettings
import hrln.interactive.sidekit.apps.integrations.SecretField
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.*
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable
fun UpBankScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val settings = remember { PrivateSettings(context, "up_connection") }
    var token by remember { mutableStateOf("") }
    var snapshot by remember { mutableStateOf<UpSnapshot?>(null) }
    var accountId by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf(resources.getString(R.string.banking_up_intro)) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) { settings.read() }.optJSONObject("snapshot")?.let { snapshot = UpSnapshot.parse(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            notice = resources.getString(R.string.banking_up_unreadable)
        }
    }
    fun sync(more: Boolean = false) {
        if (busy) return
        if (more && token.isNotBlank()) {
            notice = resources.getString(R.string.banking_up_refresh_first)
            return
        }
        val newToken = token.trim()
        busy = true
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { if (newToken.isNotBlank()) JSONObject() else settings.read() }
                val credential = newToken.ifBlank { saved.optString("token") }
                val connector = UpBankConnector(credential)
                val next = if (more && snapshot != null) connector.more(snapshot!!) else connector.sync()
                withContext(Dispatchers.IO) { settings.write(JSONObject().put("token", credential).put("snapshot", next.json())) }
                snapshot = next
                if (!more) accountId = null
                token = ""
                notice = resources.getString(R.string.banking_up_synced)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                notice = resources.getString(R.string.banking_up_failed)
            } finally {
                busy = false
            }
        }
    }
    val loading = stringResource(R.string.banking_loading)
    val syncLabel = if (busy) loading else stringResource(if (snapshot == null) R.string.banking_connect else R.string.banking_refresh)
    SidekitScaffold(
        stringResource(R.string.banking_up_title),
        actions = listOf(SideAction.back("back", stringResource(R.string.banking_back), onBack), SideAction("sync", syncLabel) { sync() }),
        options = listOf(
            SideAction("disconnect", stringResource(R.string.banking_up_disconnect)) {
                if (!busy) {
                    settings.clear()
                    snapshot = null
                    token = ""
                    notice = resources.getString(R.string.banking_up_disconnected)
                }
            }
        )
    ) { padding ->
        SidekitList(padding) {
            item { UpInfo(stringResource(R.string.banking_up_option), notice) }
            item { SecretField(token, { token = it }, stringResource(if (snapshot == null) R.string.banking_up_token else R.string.banking_up_new_token)) }
            snapshot?.let { data ->
                item {
                    val synced = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(LocalConfiguration.current.locales[0])
                        .format(Instant.ofEpochMilli(data.at).atZone(ZoneId.systemDefault()))
                    UpInfo(stringResource(R.string.banking_up_last_synced), synced)
                }
                items(data.accounts.size) { i ->
                    val a = data.accounts[i]
                    SidekitItem(a.name, secondary = "${a.currency} ${BigDecimal.valueOf(a.cents, 2)}", onClick = { accountId = if (accountId == a.id) null else a.id })
                }
                val transactions = data.transactions.filter { accountId == null || it.accountId == accountId }
                if (transactions.isEmpty()) item { SidekitItem(stringResource(R.string.banking_up_no_transactions)) }
                items(transactions.size) { i ->
                    val t = transactions[i]
                    UpInfo(t.description, "${t.currency} ${BigDecimal.valueOf(t.cents, 2)} · ${t.status}\n${t.createdAt}")
                }
                if (data.next != null) item { SidekitItem(stringResource(R.string.banking_up_older), secondary = if (busy) loading else stringResource(R.string.banking_up_load_next), onClick = { sync(true) }) }
            }
        }
    }
}

@Composable
private fun UpInfo(title: String, secondary: String) {
    SidekitCard(Modifier.fillMaxWidth()) {
        Column {
            SidekitText(title, maxLines = Int.MAX_VALUE)
            SidekitText(secondary, maxLines = Int.MAX_VALUE, style = hrln.interactive.sidekit.design.theme.SidekitTheme.typography.small)
        }
    }
}
