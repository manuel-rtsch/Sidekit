package hrln.interactive.sidekit.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.apps.everyday.CompactRowHeight
import hrln.interactive.sidekit.apps.settings.*
import hrln.interactive.sidekit.apps.sideport.registry.PortedApps
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.design.input.*
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.launcher.rememberNativeApps
import hrln.interactive.sidekit.sdk.settings.KeypadGuide
import hrln.interactive.sidekit.sdk.theme.caseColourChoices

@Composable
internal fun OnboardingScreen(onFinish: () -> Unit, replay: Boolean = false) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val prefs = remember { SidekitPreferences(context) }
    var step by rememberSaveable { mutableIntStateOf(if (replay) 0 else prefs.setupStep) }
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val keypad = LocalKeypad.current
    val hidden = rememberHiddenApps(prefs)
    val ported by remember { PortedApps.get(context).apps }.collectAsStateWithLifecycle()
    val native = rememberNativeApps()
    val selectedColour = rememberCaseColour(prefs)
    var listLayout by remember { mutableStateOf(prefs.listLayout) }
    var inputMode by remember { mutableStateOf(prefs.inputMode) }
    var tourIndex by rememberSaveable { mutableIntStateOf(0) }
    val modules = remember(keypad?.module) { orderedModules(keypad?.module ?: KeypadModule.T9) }
    LaunchedEffect(keypad?.module) { tourIndex = 0 }
    val access = remember { setupAccess() }
    val requested = remember { mutableStateListOf<Int>() }
    val result = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { revision++ }
    val permissionResult = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        revision++
        if (it.values.any { granted -> !granted }) message = resources.getString(R.string.onboarding_not_granted)
    }
    LifecycleResumeEffect(Unit) {
        revision++
        onPauseOrDispose {}
    }
    fun move(next: Int) {
        step = next
        message = null
        if (!replay) prefs.setupStep = next
    }
    fun finish() {
        prefs.setupComplete = true
        prefs.setupStep = 0
        onFinish()
    }
    fun open(intent: Intent) {
        try {
            result.launch(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            message = resources.getString(R.string.onboarding_unavailable)
        } catch (_: SecurityException) {
            message = resources.getString(R.string.onboarding_cannot_open)
        }
    }
    fun request(entry: SetupAccess) {
        message = null
        if (entry.intent != null) {
            open(entry.intent.invoke(context))
        } else if (entry.title in requested) {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
        } else {
            requested.add(entry.title)
            permissionResult.launch(entry.permissions.toTypedArray())
        }
    }
    val titles = listOf(
        R.string.onboarding_title_hello,
        R.string.onboarding_title_yours,
        R.string.onboarding_title_home,
        R.string.onboarding_title_apps,
        R.string.onboarding_title_access,
        R.string.onboarding_title_typing,
        R.string.onboarding_title_keypad
    )
    val backLabel = stringResource(R.string.onboarding_back)
    val showAllLabel = stringResource(R.string.onboarding_show_all)
    val nextLabel = stringResource(if (step == 6) R.string.onboarding_finish else R.string.onboarding_next)
    BackHandler {
        if (step > 0) {
            move(step - 1)
        } else if (replay) {
            onFinish()
        }
    }
    SidekitScaffold(
        stringResource(titles[step]),
        actions = buildList {
            if (step > 0 || replay) add(SideAction.back("back", backLabel) { if (step > 0) move(step - 1) else onFinish() })
            // The hide-apps step shares its list with Settings, so it shares the bar's Show all too.
            if (step == 3 && hidden.isNotEmpty()) add(SideAction("showAll", showAllLabel, tone = ActionTone.Secondary) { prefs.hiddenApps = emptySet() })
            add(SideAction.forward("next", nextLabel, { if (step == 6) finish() else move(step + 1) }))
        }
    ) { padding ->
        key(step) {
            val listState = rememberLazyListState()
            LaunchedEffect(tourIndex) { if (step == 6) listState.scrollToItem(0) }
            SidekitList(padding, state = listState) {
                if (step != 6) item { SidekitText(stringResource(R.string.onboarding_progress, step + 1, titles.size), style = SidekitTheme.typography.label) }
                when (step) {
                    0 -> {
                        item { Copy(stringResource(R.string.onboarding_hello_title), stringResource(R.string.onboarding_hello_body)) }
                        item {
                            val enabled = remember(revision) { homeAccess.enabled(context) }
                            SidekitLineButton(stringResource(if (enabled) R.string.onboarding_is_home else R.string.onboarding_set_home), { request(homeAccess) })
                        }
                        item { Copy(stringResource(R.string.onboarding_home_title), stringResource(R.string.onboarding_home_body)) }
                    }

                    1 -> caseColourChoices(selectedColour) { prefs.caseColour = it }

                    2 -> {
                        item { Copy(stringResource(R.string.onboarding_layout_title), stringResource(R.string.onboarding_layout_body)) }
                        item {
                            LayoutChoice(false, listLayout) {
                                listLayout = false
                                prefs.listLayout = false
                            }
                        }
                        item {
                            LayoutChoice(true, listLayout) {
                                listLayout = true
                                prefs.listLayout = true
                            }
                        }
                    }

                    3 -> hiddenAppChoices(context, hidden, ported, native = native) { prefs.hiddenApps = it }

                    4 -> {
                        item { Copy(stringResource(R.string.onboarding_access_title), stringResource(R.string.onboarding_access_body)) }
                        access.forEach { entry ->
                            item {
                                val enabled = remember(revision) { entry.enabled(context) }
                                val title = stringResource(entry.title)
                                val reason = stringResource(entry.reason)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SidekitLineButton("${if (enabled) "✓ " else ""}$title", { request(entry) }, variant = if (enabled) LineButtonVariant.Secondary else LineButtonVariant.Primary)
                                    SidekitText(if (enabled) stringResource(R.string.onboarding_enabled, reason) else reason, style = SidekitTheme.typography.label.copy(fontSize = 24.sp, lineHeight = 30.sp), maxLines = Int.MAX_VALUE)
                                }
                            }
                        }
                        item { Copy(stringResource(R.string.onboarding_accounts_title), stringResource(R.string.onboarding_accounts_body)) }
                    }

                    5 -> {
                        item { Copy(stringResource(R.string.onboarding_typing_title), stringResource(R.string.onboarding_typing_body)) }
                        T9Mode.entries.forEach { mode ->
                            item {
                                val label = stringResource(if (mode == T9Mode.Numeric) R.string.onboarding_numbers else R.string.onboarding_letters)
                                SidekitLineButton("${if (inputMode == mode) "✓ " else ""}$label", {
                                    inputMode = mode
                                    prefs.inputMode = mode
                                    keypad?.let(prefs::applyInputDefault)
                                }, variant = if (inputMode == mode) LineButtonVariant.Primary else LineButtonVariant.Secondary)
                            }
                        }
                        item { Copy(stringResource(R.string.onboarding_words_title), stringResource(R.string.onboarding_words_body)) }
                    }

                    6 -> {
                        item {
                            SidekitItem(
                                stringResource(R.string.onboarding_module),
                                Modifier.height(CompactRowHeight).listFade(listState, 0),
                                secondary = stringResource(R.string.onboarding_module_position, tourIndex + 1, modules.size, tourLabel(modules[tourIndex])),
                                secondaryToEnd = true,
                                onClick = { tourIndex = (tourIndex + 1) % modules.size }
                            )
                        }
                        item { SidekitLineButton(stringResource(R.string.onboarding_use_module, modules[tourIndex].displayLabel()), { keypad?.useModule(modules[tourIndex]) }, variant = LineButtonVariant.Secondary) }
                        item { KeypadGuide(modules[tourIndex]) }
                        item { SidekitText(stringResource(R.string.onboarding_current_module, keypad?.module?.displayLabel() ?: "T9"), style = SidekitTheme.typography.label, maxLines = Int.MAX_VALUE) }
                    }
                }
                message?.let { item { Copy(stringResource(R.string.onboarding_access_update), it) } }
            }
        }
    }
}

@Composable
private fun Copy(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SidekitText(title, style = SidekitTheme.typography.headline.copy(fontSize = 30.sp, lineHeight = 36.sp), maxLines = Int.MAX_VALUE)
        SidekitText(body, style = SidekitTheme.typography.label.copy(fontSize = 24.sp, lineHeight = 30.sp), maxLines = Int.MAX_VALUE)
    }
}

@Composable
private fun LayoutChoice(list: Boolean, selected: Boolean, choose: () -> Unit) {
    SidekitTile(Modifier.fillMaxWidth().height(112.dp), color = if (list == selected) SidekitTheme.colors.emphasisA1 else SidekitTheme.colors.surfaceA3, onClick = choose) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SidekitText("${if (list == selected) "✓ " else ""}${stringResource(if (list) R.string.onboarding_list else R.string.onboarding_grid)}", style = SidekitTheme.typography.headline.copy(fontSize = 30.sp, lineHeight = 36.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.width(90.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(if (list) 3 else 2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(if (list) 1 else 3) {
                                Box(Modifier.weight(1f).height(if (list) 10.dp else 18.dp).clip(SidekitTheme.shapes.item).background(SidekitTheme.colors.surfaceA6))
                            }
                        }
                    }
                }
                SidekitText(stringResource(if (list) R.string.onboarding_list_body else R.string.onboarding_grid_body), style = SidekitTheme.typography.label.copy(fontSize = 22.sp, lineHeight = 26.sp), maxLines = 2)
            }
        }
    }
}

internal fun orderedModules(current: KeypadModule) = listOf(current) + KeypadModule.entries.filter { it != current }

@Composable
private fun tourLabel(module: KeypadModule) = when (module) {
    KeypadModule.Qwerty -> "QWERTY"
    KeypadModule.GamePad -> stringResource(R.string.onboarding_controller)
    else -> module.displayLabel()
}
