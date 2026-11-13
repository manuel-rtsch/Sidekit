package hrln.interactive.sidekit.apps.settings

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import hrln.interactive.sidekit.apps.everyday.CompactRowHeight
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.design.input.KeypadModule
import hrln.interactive.sidekit.design.input.LocalKeypad
import hrln.interactive.sidekit.design.input.T9Mode
import hrln.interactive.sidekit.navigation.SubApp

private val KEYBOARD = "${SubApp.Settings.route}/keyboard"
private val GUIDE = "$KEYBOARD/guide"

internal fun keyboardSettingsRoute() = KEYBOARD

internal fun keyboardGuideRoute() = GUIDE

/** Settings > Keyboard: what the keypad does and what its keys look like. */
internal fun NavGraphBuilder.keyboardSettingsGraph(nav: NavHostController) {
    composable(KEYBOARD) {
        KeyboardSettingsScreen(
            back = { nav.popBackStack() },
            onKeypadMapping = { nav.navigate(keypadMappingRoute()) },
            onKeyGuide = { nav.navigate(keyboardGuideRoute()) }
        )
    }
    composable(GUIDE) { KeypadGuideScreen(back = { nav.popBackStack() }) }
}

/**
 * One page for the keypad preferences that used to be separate rows on the Settings home:
 * the module, the typing mode, the Back key service and the custom mapping. The key guide is a
 * separate page so this one stays a short, scannable list.
 */
@Composable
internal fun KeyboardSettingsScreen(back: () -> Unit, onKeypadMapping: () -> Unit, onKeyGuide: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SidekitPreferences(context) }
    val keypad = LocalKeypad.current
    var revision by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        revision++
        onPauseOrDispose {}
    }
    val accessibility = remember(revision) {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        enabled.split(':').mapNotNull(ComponentName::unflattenFromString).any {
            it.packageName == context.packageName && it.className.endsWith("KeypadAccessibilityService")
        }
    }
    val inputMode = remember(revision) { prefs.inputMode }
    val custom = keypad?.mapper?.customModule
    val module = keypad?.module ?: KeypadModule.T9
    val list = rememberLazyListState()
    SidekitScaffold("Keyboard", actions = listOf(SideAction.back("back", "Settings", back))) { padding ->
        SidekitList(padding, state = list) {
            // A custom mapping fixes the module: the row opens its editor instead of cycling.
            item {
                SidekitItem(
                    "Module",
                    Modifier.height(CompactRowHeight).listFade(list, 0),
                    secondary = custom?.let { "${it.label} · custom" } ?: module.label,
                    secondaryToEnd = true,
                    onClick = {
                        if (custom != null) {
                            onKeypadMapping()
                        } else {
                            keypad?.useModule(KeypadModule.entries[(module.ordinal + 1) % KeypadModule.entries.size])
                            revision++
                        }
                    }
                )
            }
            item {
                SidekitItem(
                    "Typing",
                    Modifier.height(CompactRowHeight).listFade(list, 1),
                    secondary = if (!module.typesOnKeypad) {
                        "On-screen keyboard"
                    } else if (inputMode == T9Mode.Numeric) {
                        "Numbers"
                    } else {
                        "Letters"
                    },
                    secondaryToEnd = true,
                    onClick = if (module.typesOnKeypad) {
                        {
                            prefs.inputMode = T9Mode.entries[(prefs.inputMode.ordinal + 1) % T9Mode.entries.size]
                            keypad?.let { prefs.applyInputDefault(it) }
                            revision++
                        }
                    } else {
                        null
                    }
                )
            }
            item {
                SidekitItem(
                    "Back key",
                    Modifier.height(CompactRowHeight).listFade(list, 2),
                    secondary = if (accessibility) "On" else "Off",
                    showLinkArrow = true,
                    showLinkArrowWithSecondary = true,
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } catch (_: Exception) {
                            // Leave the row unchanged if the system page isn't available.
                        }
                    }
                )
            }
            item {
                SidekitItem(
                    "Key mapping",
                    Modifier.height(CompactRowHeight).listFade(list, 3),
                    secondary = if (custom != null) "Custom" else "Auto",
                    showLinkArrow = true,
                    showLinkArrowWithSecondary = true,
                    onClick = onKeypadMapping
                )
            }
            // The guide is the page's main reference, so it stays at full opacity and compact
            // enough to sit above the action bar without scrolling.
            item { SidekitItem("Key guide", Modifier.height(CompactRowHeight), showLinkArrow = true, onClick = onKeyGuide) }
        }
    }
}
