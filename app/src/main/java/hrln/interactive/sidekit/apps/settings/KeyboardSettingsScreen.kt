package hrln.interactive.sidekit.apps.settings

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import hrln.interactive.sidekit.R
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
    SidekitScaffold(stringResource(R.string.settings_keyboard), actions = listOf(SideAction.back("back", stringResource(R.string.settings_title), back))) { padding ->
        SidekitList(padding, state = list) {
            // A custom mapping fixes the module: the row opens its editor instead of cycling.
            item {
                SidekitItem(
                    stringResource(R.string.settings_module),
                    Modifier.height(CompactRowHeight).listFade(list, 0),
                    secondary = custom?.let { stringResource(R.string.settings_module_custom, it.label) } ?: module.displayLabel(),
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
                    stringResource(R.string.settings_typing),
                    Modifier.height(CompactRowHeight).listFade(list, 1),
                    secondary = stringResource(
                        if (!module.typesOnKeypad) {
                            R.string.settings_typing_on_screen
                        } else if (inputMode == T9Mode.Numeric) {
                            R.string.settings_typing_numbers
                        } else {
                            R.string.settings_typing_letters
                        }
                    ),
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
                    stringResource(R.string.settings_back_key),
                    Modifier.height(CompactRowHeight).listFade(list, 2),
                    secondary = stringResource(if (accessibility) R.string.settings_on else R.string.settings_off),
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
                    stringResource(R.string.settings_key_mapping),
                    Modifier.height(CompactRowHeight).listFade(list, 3),
                    secondary = stringResource(if (custom != null) R.string.settings_key_mapping_custom else R.string.settings_key_mapping_auto),
                    showLinkArrow = true,
                    showLinkArrowWithSecondary = true,
                    onClick = onKeypadMapping
                )
            }
            // The guide is the page's main reference, so it stays at full opacity and compact
            // enough to sit above the action bar without scrolling.
            item { SidekitItem(stringResource(R.string.settings_key_guide), Modifier.height(CompactRowHeight), showLinkArrow = true, onClick = onKeyGuide) }
        }
    }
}
