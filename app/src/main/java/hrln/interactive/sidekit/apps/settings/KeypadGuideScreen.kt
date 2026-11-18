package hrln.interactive.sidekit.apps.settings

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
 * shown at a time. The module row previews the other modules so the guide can be compared without
 * changing the module in use; a custom mapping fixes the module, so that row is read-only.
 */
@Composable
internal fun KeypadGuideScreen(back: () -> Unit) {
    val keypad = LocalKeypad.current
    val custom = keypad?.mapper?.customModule
    val inUse = keypad?.module ?: KeypadModule.T9
    // A preview only: browsing the guide must not switch the live module (picking Controller on a
    // T9 phone would turn keypad typing off). The Module setting on the Keyboard page does that.
    var preview by rememberSaveable(inUse) { mutableStateOf(inUse) }
    val module = custom ?: preview
    val list = rememberLazyListState()
    SidekitScaffold("Key guide", actions = listOf(SideAction.back("back", "Keyboard", back))) { padding ->
        SidekitList(padding, state = list) {
            item {
                SidekitItem(
                    "Module",
                    Modifier.height(CompactRowHeight).listFade(list, 0),
                    secondary = custom?.let { "${it.label} · custom" } ?: if (module == inUse) "${module.label} · in use" else module.label,
                    secondaryToEnd = true,
                    onClick = if (custom == null) {
                        { preview = KeypadModule.entries[(preview.ordinal + 1) % KeypadModule.entries.size] }
                    } else {
                        null
                    }
                )
            }
            item { KeypadGuide(module) }
        }
    }
}
