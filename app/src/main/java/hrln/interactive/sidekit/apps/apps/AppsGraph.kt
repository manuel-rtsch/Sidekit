package hrln.interactive.sidekit.apps.apps

import android.content.Intent
import android.net.Uri
import android.os.Process
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import hrln.interactive.sidekit.apps.sideport.registry.PortedApps
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.home.HomePreferences
import hrln.interactive.sidekit.home.homeId
import hrln.interactive.sidekit.navigation.SubApp

fun NavGraphBuilder.appsGraph(nav: NavHostController) {
    composable(SubApp.Apps.route) {
        val context = LocalContext.current
        val vm: AppsViewModel = viewModel(factory = remember { viewModelFactory { initializer { AppsViewModel(AppsRepository(context.applicationContext)) } } })
        AppsScreen(vm) { nav.popBackStack() }
    }
}

@Composable
internal fun AppsScreen(vm: AppsViewModel, back: () -> Unit) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val ported by remember { PortedApps.get(context).apps }.collectAsStateWithLifecycle()
    val home = remember(context) { HomePreferences(context) }
    var homeRevision by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<LaunchableApp?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    fun perform(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            message = "Couldn't open app: ${e.localizedMessage}"
        }
    }
    val app = selected
    val pinnedId = app?.homeId(context)
    val pinned = remember(pinnedId, homeRevision) { pinnedId != null && pinnedId in home.homeOrder }
    val portedPackage = app?.let { target -> ported.firstOrNull { it.packageName == target.component.packageName } }
    val options = if (app == null) {
        emptyList()
    } else {
        buildList {
            val id = app.homeId(context)
            if (portedPackage != null) {
                add(
                    SideAction("sideport", "In Sideport", tone = ActionTone.Muted) {
                        message = "${app.label} is a Sideport app, so it can't also be pinned as a normal app."
                    }
                )
            } else {
                add(
                    SideAction(if (pinned) "unpin" else "pin", if (pinned) "Unpin from home" else "Pin to home") {
                        if (pinned) home.unpin(id) else home.pin(id)
                        homeRevision++
                    }
                )
            }
            add(SideAction("info", "App info") { perform { vm.repository.info(app) } })
            add(
                SideAction("uninstall", "Uninstall") {
                    perform { context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}")).putExtra(Intent.EXTRA_USER, app.user)) }
                }
            )
        }
    }
    val list = rememberLazyListState()
    SidekitScaffold("Apps", actions = listOf(SideAction.back("back", "Home", back)), options = options) { padding ->
        SidekitList(padding, state = list) {
            item { AppSearchField(query) { vm.query.value = it } }
            item { SidekitCard { SidekitText(selected?.let { "${it.label} selected. Press Options to pin it to home, see app info or uninstall." } ?: "Tap to open · hold an app for Options", style = SidekitTheme.typography.label, maxLines = Int.MAX_VALUE) } }
            (message ?: state.error)?.let { item { SidekitCard(onClick = { message = null }) { SidekitText(it, style = SidekitTheme.typography.label, maxLines = Int.MAX_VALUE) } } }
            if (!state.loaded && state.error == null) item { SidekitItem("Loading apps…") }
            if (state.loaded && state.apps.isEmpty()) item { SidekitItem("No matching apps") }
            itemsIndexed(state.apps, key = { _, it -> it.key }) { index, target ->
                AppRow(
                    target,
                    vm.repository,
                    Modifier.listFade(list, index + 2),
                    secondary = if (target.user != Process.myUserHandle()) "Work profile" else null,
                    onClick = {
                        selected = target
                        perform { vm.repository.launch(target) }
                    },
                    onLongClick = { selected = target },
                    onLongClickLabel = "App options"
                )
            }
        }
    }
}
