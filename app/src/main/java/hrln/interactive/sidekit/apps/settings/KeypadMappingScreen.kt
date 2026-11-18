package hrln.interactive.sidekit.apps.settings

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import hrln.interactive.sidekit.design.input.KeypadControls
import hrln.interactive.sidekit.design.input.KeypadModule
import hrln.interactive.sidekit.navigation.SubApp
import hrln.interactive.sidekit.sdk.settings.KeyCaptureScreen
import hrln.interactive.sidekit.sdk.settings.KeyTesterScreen
import hrln.interactive.sidekit.sdk.settings.KeypadMappingScreen

private val MAPPING = "${SubApp.Settings.route}/keypad-mapping"
private val TESTER = "$MAPPING/tester"
private val LEARN = "$MAPPING/key/{module}/{code}"

internal fun keypadMappingRoute() = MAPPING

/** Settings > Keypad mapping, its key capture and its key tester. */
internal fun NavGraphBuilder.keypadMappingGraph(nav: NavHostController) {
    composable(MAPPING) {
        KeypadMappingScreen(
            back = { nav.popBackStack() },
            learn = { module, control -> nav.navigate("$MAPPING/key/${module.name}/${control.keyCode}") },
            tester = { nav.navigate(TESTER) }
        )
    }
    composable(LEARN) { entry ->
        val module = KeypadModule.entries.firstOrNull { it.name == entry.arguments?.getString("module") }
        val control = module?.let { m -> entry.arguments?.getString("code")?.toIntOrNull()?.let { KeypadControls.find(m, it) } }
        if (module == null || control == null) {
            LaunchedEffect(Unit) { nav.popBackStack() }
            return@composable
        }
        KeyCaptureScreen(module, control, done = { nav.popBackStack() })
    }
    composable(TESTER) { KeyTesterScreen { nav.popBackStack() } }
}
