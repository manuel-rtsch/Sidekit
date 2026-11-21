package hrln.interactive.sidekit.apps.settings

import android.app.ActivityManager
import android.app.role.RoleManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.apps.everyday.CompactRowHeight
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.action.withNotice
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.navigation.SubApp
import hrln.interactive.sidekit.onboarding.OnboardingScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun NavGraphBuilder.settingsGraph(nav: NavHostController) {
    composable(SubApp.Settings.route) {
        SettingsHome(
            onHome = { nav.popBackStack() },
            onAbout = { nav.navigate("${SubApp.Settings.route}/about") },
            onCaseColour = { nav.navigate("${SubApp.Settings.route}/case-colour") },
            onSetup = { nav.navigate("${SubApp.Settings.route}/setup") },
            onHiddenApps = { nav.navigate("${SubApp.Settings.route}/hidden-apps") },
            onSystem = { nav.navigate("${SubApp.Settings.route}/system") },
            onKeyboard = { nav.navigate(keyboardSettingsRoute()) },
            onArrange = { nav.navigate("${SubApp.Settings.route}/arrange") },
            onLanguage = { nav.navigate("${SubApp.Settings.route}/language") }
        )
    }
    keyboardSettingsGraph(nav)
    keypadMappingGraph(nav)
    composable("${SubApp.Settings.route}/hidden-apps") { HiddenAppsScreen { nav.popBackStack() } }
    composable("${SubApp.Settings.route}/system") { SystemSettingsScreen { nav.popBackStack() } }
    composable("${SubApp.Settings.route}/setup") { OnboardingScreen(onFinish = { nav.popBackStack() }, replay = true) }
    composable("${SubApp.Settings.route}/case-colour") { CaseColourScreen { nav.popBackStack() } }
    composable("${SubApp.Settings.route}/arrange") { ArrangeHomeScreen { nav.popBackStack() } }
    composable("${SubApp.Settings.route}/language") { LanguageScreen { nav.popBackStack() } }
    composable("${SubApp.Settings.route}/about") { AboutScreen { nav.popBackStack() } }
}

@Composable
internal fun SettingsHome(onHome: () -> Unit, onAbout: () -> Unit, onCaseColour: () -> Unit = {}, onSetup: () -> Unit = {}, onHiddenApps: () -> Unit = {}, onSystem: () -> Unit = {}, onKeyboard: () -> Unit = {}, onArrange: () -> Unit = {}, onLanguage: () -> Unit = {}) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val prefs = remember { SidekitPreferences(context) }
    val caseColour = rememberCaseColour(prefs)
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(Unit) {
        revision++
        onPauseOrDispose {}
    }
    val home = remember(revision) { context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME) }
    fun open(action: String) {
        try {
            context.startActivity(Intent(action))
        } catch (_: Exception) {
            message = resources.getString(R.string.settings_unavailable)
        }
    }
    val listLayout = remember(revision) { prefs.listLayout }
    val compactLayout = remember(revision) { prefs.compactLayout }
    val list = rememberLazyListState()
    val actions = withNotice(message, { message = null }, listOf(SideAction.back("home", stringResource(R.string.settings_home), onHome)))
    SidekitScaffold(stringResource(R.string.settings_title), actions = actions) { padding ->
        SidekitList(padding, state = list) {
            // Toggles act in place: compact rows, no arrow.
            item {
                SidekitItem(stringResource(R.string.settings_launcher_default), Modifier.height(CompactRowHeight).listFade(list, 0), secondary = stringResource(if (listLayout) R.string.settings_list else R.string.settings_grid), secondaryToEnd = true, onClick = {
                    prefs.listLayout = !prefs.listLayout
                    revision++
                })
            }
            // Shorter header and action bar on every screen: more room for the body.
            item {
                SidekitItem(stringResource(R.string.settings_compact_layout), Modifier.height(CompactRowHeight).listFade(list, 1), secondary = stringResource(if (compactLayout) R.string.settings_on else R.string.settings_off), secondaryToEnd = true, onClick = {
                    prefs.compactLayout = !prefs.compactLayout
                    revision++
                })
            }
            // Opens a floating system dialog rather than a Sidekit page, so it stays a compact row.
            item { SidekitItem(stringResource(R.string.settings_home_app), Modifier.height(CompactRowHeight).listFade(list, 2), secondary = if (home) "Sidekit" else stringResource(R.string.settings_choose), secondaryToEnd = true, onClick = { open(Settings.ACTION_HOME_SETTINGS) }) }
            // Everything below opens another page: full-height rows with a link arrow.
            item { SidekitItem(stringResource(R.string.settings_arrange_apps), Modifier.listFade(list, 3), showLinkArrow = true, onClick = onArrange) }
            item { SidekitItem(stringResource(R.string.settings_case_colour), Modifier.listFade(list, 4), secondary = caseColour?.displayLabel() ?: stringResource(R.string.settings_original_blue), showLinkArrow = true, onClick = onCaseColour) }
            // All keypad preferences live together on the Keyboard page, with a separate key guide.
            item { SidekitItem(stringResource(R.string.settings_keyboard), Modifier.listFade(list, 5), showLinkArrow = true, onClick = onKeyboard) }
            item { SidekitItem(stringResource(R.string.language_title), secondary = languageSummary(), showLinkArrow = true, onClick = onLanguage) }
            item { SidekitItem(stringResource(R.string.settings_hide_apps), showLinkArrow = true, onClick = onHiddenApps) }
            // Device settings live on their own page, so this screen stays Sidekit's own settings.
            item { SidekitItem(stringResource(R.string.settings_system), showLinkArrow = true, onClick = onSystem) }
            item { SidekitItem(stringResource(R.string.settings_setup_tour), showLinkArrow = true, onClick = onSetup) }
            item { SidekitItem(stringResource(R.string.settings_about), showLinkArrow = true, onClick = onAbout) }
        }
    }
}

/** Device settings (Wi-Fi, display, sound, …), which open Android's own pages. */
@Composable
internal fun SystemSettingsScreen(back: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var message by remember { mutableStateOf<String?>(null) }
    fun open(action: String) {
        try {
            context.startActivity(Intent(action))
        } catch (_: Exception) {
            message = resources.getString(R.string.settings_unavailable)
        }
    }
    val list = rememberLazyListState()
    val actions = withNotice(message, { message = null }, listOf(SideAction.back("back", stringResource(R.string.settings_title), back)))
    SidekitScaffold(stringResource(R.string.settings_system), actions = actions) { padding ->
        SidekitList(padding, state = list) {
            itemsIndexed(SettingsUiState().rows) { index, row -> SidekitItem(stringResource(row.label), Modifier.height(CompactRowHeight).listFade(list, index), secondary = stringResource(row.value), onClick = { open(row.key.settingsAction()) }) }
        }
    }
}

/** Retained for design previews and screen-level tests. */
@Composable
fun SettingsScreen(rows: List<SettingRow>, onRowClick: (SettingKey) -> Unit, onHome: () -> Unit = {}, onAbout: () -> Unit = {}) {
    val list = rememberLazyListState()
    SidekitScaffold(stringResource(R.string.settings_title), actions = listOf(SideAction.back("home", stringResource(R.string.settings_home), onHome))) { padding ->
        SidekitList(padding, state = list) {
            itemsIndexed(rows) { index, row -> SidekitItem(stringResource(row.label), Modifier.height(CompactRowHeight).listFade(list, index), secondary = stringResource(row.value), onClick = { onRowClick(row.key) }) }
            item { SidekitItem(stringResource(R.string.settings_about), showLinkArrow = true, onClick = onAbout) }
        }
    }
}

@Composable
private fun AboutScreen(back: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    // The name comes from resources, so the dev build says "SideKit Dev" instead of the stable
    // name, and names itself and its package on its own row.
    val appName = stringResource(R.string.app_name)
    val devBuild = LocalResources.current.getBoolean(R.bool.is_dev_build)
    var rows by remember {
        mutableStateOf(
            listOf(
                resources.getString(R.string.settings_about_device) to "${Build.MANUFACTURER} ${Build.MODEL}",
                "Android" to resources.getString(R.string.settings_about_android_version, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
                resources.getString(R.string.settings_about_build) to Build.DISPLAY,
                resources.getString(R.string.settings_about_third_party) to resources.getString(R.string.settings_about_third_party_body)
            )
        )
    }
    LaunchedEffect(Unit) {
        val summaries = withContext(Dispatchers.IO) {
            val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
            val storage = runCatching {
                val stats = context.getSystemService(StorageStatsManager::class.java)
                resources.getString(
                    R.string.settings_about_storage_value,
                    Formatter.formatFileSize(context, stats.getFreeBytes(StorageManager.UUID_DEFAULT)),
                    Formatter.formatFileSize(context, stats.getTotalBytes(StorageManager.UUID_DEFAULT))
                )
            }.getOrDefault(resources.getString(R.string.settings_about_unavailable))
            buildList {
                add(appName to context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty())
                if (devBuild) add(resources.getString(R.string.settings_about_dev_build) to resources.getString(R.string.settings_about_dev_build_body, context.packageName))
                add(resources.getString(R.string.settings_about_storage) to storage)
                add(
                    resources.getString(R.string.settings_about_ram) to
                        resources.getString(R.string.settings_about_ram_value, Formatter.formatFileSize(context, memory.availMem), Formatter.formatFileSize(context, memory.totalMem))
                )
                add(resources.getString(R.string.settings_about_notices) to "AndroidX / Jetpack Compose — Apache License 2.0. Kotlin — Apache License 2.0.")
                add(resources.getString(R.string.settings_about_t9) to context.assets.open("t9/NOTICE").bufferedReader().use { it.readText() })
            }
        }
        rows = rows + summaries
    }
    val list = rememberLazyListState()
    SidekitScaffold(stringResource(R.string.settings_about), actions = listOf(SideAction.back("back", stringResource(R.string.settings_title), back))) { padding ->
        SidekitList(padding, state = list) {
            itemsIndexed(rows) { index, row ->
                SidekitCard(Modifier.listFade(list, index)) {
                    Column {
                        SidekitText(row.first, style = SidekitTheme.typography.headline)
                        SidekitText(row.second, style = SidekitTheme.typography.label, maxLines = Int.MAX_VALUE)
                    }
                }
            }
        }
    }
}
