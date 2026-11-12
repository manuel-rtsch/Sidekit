package hrln.interactive.sidekit.sdk.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import hrln.interactive.sidekit.design.action.ChromeState
import hrln.interactive.sidekit.design.canvas.SidekitCanvas
import hrln.interactive.sidekit.design.input.Keypad
import hrln.interactive.sidekit.design.theme.SidekitColors
import hrln.interactive.sidekit.design.theme.SidekitSpacing
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.sdk.theme.SidekitThemeStore
import hrln.interactive.sidekit.sdk.theme.rememberSidekitThemeConfig

/**
 * The shared Sidekit shell: the host's theme, the canvas and the keypad.
 *
 * A Sidekit-native app renders its own screens inside [content]. [SidekitActivity] already wires
 * this up; apps that need a different activity base can call it directly.
 */
@Composable
fun SidekitAppHost(
    store: SidekitThemeStore,
    chrome: ChromeState = remember { ChromeState() },
    keypad: Keypad? = null,
    content: @Composable () -> Unit
) {
    val config = rememberSidekitThemeConfig(store)
    val spacing = remember(config.compact) {
        if (config.compact) SidekitSpacing.Compact else SidekitSpacing()
    }
    SidekitTheme(
        colors = config.caseColour?.palette() ?: SidekitColors(),
        spacing = spacing
    ) {
        SidekitCanvas(chrome = chrome, keypad = keypad) { content() }
    }
}
