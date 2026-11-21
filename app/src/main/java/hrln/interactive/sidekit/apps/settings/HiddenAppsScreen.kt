package hrln.interactive.sidekit.apps.settings

import android.content.Context
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.apps.apps.AppsRepository
import hrln.interactive.sidekit.apps.sideport.registry.PortedApp
import hrln.interactive.sidekit.apps.sideport.registry.PortedApps
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.home.HomePreferences
import hrln.interactive.sidekit.home.homeId
import hrln.interactive.sidekit.launcher.NativeApp
import hrln.interactive.sidekit.launcher.rememberNativeApps
import hrln.interactive.sidekit.navigation.SubApp

internal fun SubApp.visibilityId() = "builtin:$name"
internal fun PortedApp.visibilityId() = "ported:$packageName"

@Composable
internal fun rememberHiddenApps(prefs: SidekitPreferences): Set<String> {
    var hidden by remember(prefs) { mutableStateOf(prefs.hiddenApps) }
    DisposableEffect(prefs) {
        val stop = prefs.observeHiddenApps { hidden = prefs.hiddenApps }
        hidden = prefs.hiddenApps
        onDispose { stop() }
    }
    return hidden
}

@Composable
internal fun HiddenAppsScreen(back: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { SidekitPreferences(context) }
    val hidden = rememberHiddenApps(prefs)
    val home = remember(context) { HomePreferences(context) }
    val ported by remember { PortedApps.get(context).apps }.collectAsStateWithLifecycle()
    val repository = remember(context) { AppsRepository(context.applicationContext) }
    val installed by remember { repository.apps }.collectAsStateWithLifecycle(emptyList())
    val native = rememberNativeApps()
    // Only directly-pinned installed apps are home entries, so only they can be hidden.
    val pinned = remember(installed, native) {
        val ids = home.homeOrder.filter { it.startsWith(HomePreferences.INSTALLED_PREFIX) }.toSet()
        val nativePackages = native.mapTo(HashSet()) { it.packageName }
        installed.filter { it.component.packageName !in nativePackages }
            .map { it.homeId(context) to it.label }.filter { it.first in ids }
    }
    val settings = stringResource(R.string.settings_title)
    val showAll = stringResource(R.string.settings_show_all)
    val actions = buildList {
        add(SideAction.back("back", settings, back))
        if (hidden.isNotEmpty()) add(SideAction("showAll", showAll, tone = ActionTone.Secondary) { prefs.hiddenApps = emptySet() })
    }
    SidekitScaffold(stringResource(R.string.settings_hide_apps), actions = actions) { padding ->
        SidekitList(padding) { hiddenAppChoices(context, hidden, ported, pinned, native) { prefs.hiddenApps = it } }
    }
}

internal fun LazyListScope.hiddenAppChoices(
    context: Context,
    hidden: Set<String>,
    ported: List<PortedApp>,
    installed: List<Pair<String, String>> = emptyList(),
    native: List<NativeApp> = emptyList(),
    save: (Set<String>) -> Unit
) {
    item { SidekitText(stringResource(R.string.settings_hide_apps_intro), style = SidekitTheme.typography.label.copy(fontSize = 24.sp, lineHeight = 30.sp), maxLines = Int.MAX_VALUE) }
    val choices = SubApp.entries.map { it.visibilityId() to it.label(context) } +
        ported.map { it.visibilityId() to it.label } + native.map { it.visibilityId to it.label } + installed
    items(choices, key = { it.first }) { (id, label) ->
        SidekitItem(label, Modifier.height(76.dp), secondary = stringResource(if (id in hidden) R.string.settings_hidden else R.string.settings_shown), onClick = {
            save(if (id in hidden) hidden - id else hidden + id)
        })
    }
}
