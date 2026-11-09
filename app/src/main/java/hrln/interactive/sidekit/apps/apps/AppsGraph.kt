package hrln.interactive.sidekit.apps.apps

import android.os.Process
import androidx.activity.compose.BackHandler
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
    var opened by remember { mutableStateOf<LaunchableApp?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    fun perform(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            message = "Couldn't open app: ${e.localizedMessage}"
        }
    }

    val app = opened
    if (app != null) {
        BackHandler { opened = null }
        val id = app.homeId(context)
        val pinned = remember(id, homeRevision) { id in home.homeOrder }
        val portedApp = ported.firstOrNull { it.packageName == app.component.packageName }
        AppDetailScreen(
            app = app,
            source = vm.repository,
            ported = portedApp,
            pinned = pinned,
            onBack = { opened = null },
            onPin = {
                home.pin(id)
                homeRevision++
            },
            onUnpin = {
                home.unpin(id)
                homeRevision++
            }
        )
        return
    }

    val list = rememberLazyListState()
    SidekitScaffold("Apps", actions = listOf(SideAction.back("back", "Home", back))) { padding ->
        SidekitList(padding, state = list) {
            item { AppSearchField(query) { vm.query.value = it } }
            item {
                SidekitCard {
                    SidekitText(
                        "Tap to open · hold an app to pin it, see app info or uninstall",
                        style = SidekitTheme.typography.label,
                        maxLines = Int.MAX_VALUE
                    )
                }
            }
            val banner = message ?: state.error
            banner?.let { item { SidekitCard(onClick = { message = null }) { SidekitText(it, style = SidekitTheme.typography.label, maxLines = Int.MAX_VALUE) } } }
            if (!state.loaded && state.error == null) item { SidekitItem("Loading apps…") }
            if (state.loaded && state.apps.isEmpty()) item { SidekitItem("No matching apps") }
            // listFade needs the row's real list index: search field and hint, plus the banner if shown.
            val rowsStart = 2 + if (banner != null) 1 else 0
            itemsIndexed(state.apps, key = { _, it -> it.key }) { index, target ->
                AppRow(
                    target,
                    vm.repository,
                    Modifier.listFade(list, index + rowsStart),
                    secondary = if (target.user != Process.myUserHandle()) "Work profile" else null,
                    onClick = { perform { vm.repository.launch(target) } },
                    onLongClick = { opened = target },
                    onLongClickLabel = "App page"
                )
            }
        }
    }
}
