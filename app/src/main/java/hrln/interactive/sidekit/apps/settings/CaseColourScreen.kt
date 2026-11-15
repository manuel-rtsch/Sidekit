package hrln.interactive.sidekit.apps.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.SidekitList
import hrln.interactive.sidekit.design.components.SidekitScaffold
import hrln.interactive.sidekit.design.theme.CaseColour
import hrln.interactive.sidekit.sdk.theme.caseColourChoices

@Composable
internal fun rememberCaseColour(prefs: SidekitPreferences): CaseColour? {
    var selected by remember(prefs) { mutableStateOf(prefs.caseColour) }
    DisposableEffect(prefs) {
        val stop = prefs.observeCaseColour { selected = prefs.caseColour }
        selected = prefs.caseColour
        onDispose { stop() }
    }
    return selected
}

@Composable
internal fun CaseColourScreen(back: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { SidekitPreferences(context) }
    val selected = rememberCaseColour(prefs)
    SidekitScaffold("Case colour", actions = listOf(SideAction.back("back", "Settings", back))) { padding ->
        SidekitList(padding) {
            caseColourChoices(selected) { prefs.caseColour = it }
        }
    }
}
