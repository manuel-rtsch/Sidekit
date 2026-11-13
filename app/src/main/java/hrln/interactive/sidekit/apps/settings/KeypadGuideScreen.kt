package hrln.interactive.sidekit.apps.settings

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import hrln.interactive.sidekit.apps.everyday.CompactRowHeight
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.SidekitItem
import hrln.interactive.sidekit.design.components.SidekitList
import hrln.interactive.sidekit.design.components.SidekitScaffold
import hrln.interactive.sidekit.design.components.listFade
import hrln.interactive.sidekit.design.input.KeypadModule
import hrln.interactive.sidekit.design.input.LocalKeypad
import hrln.interactive.sidekit.sdk.settings.KeypadGuide

/**
 * Settings > Keyboard > Key guide: the keys of the module in use, with one tap or hold action
 * shown at a time. The module row cycles through the modules so the guide can be compared without
 * going back; a custom mapping fixes the module, so that row is read-only.
 */
@Composable
internal fun KeypadGuideScreen(back: () -> Unit) {
    val keypad = LocalKeypad.current
    val custom = keypad?.mapper?.customModule
    val module = keypad?.module ?: KeypadModule.T9
    val list = rememberLazyListState()
    SidekitScaffold("Key guide", actions = listOf(SideAction.back("back", "Keyboard", back))) { padding ->
        SidekitList(padding, state = list) {
            item {
                SidekitItem(
                    "Module",
                    Modifier.height(CompactRowHeight).listFade(list, 0),
                    secondary = custom?.let { "${it.label} · custom" } ?: module.label,
                    secondaryToEnd = true,
                    onClick = if (custom == null) {
                        {
                            keypad?.useModule(KeypadModule.entries[(module.ordinal + 1) % KeypadModule.entries.size])
                        }
                    } else {
                        null
                    }
                )
            }
            item { KeypadGuide(module) }
        }
    }
}
