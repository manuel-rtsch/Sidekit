package hrln.interactive.sidekit.apps.settings

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.SidekitItem
import hrln.interactive.sidekit.design.components.SidekitList
import hrln.interactive.sidekit.design.components.SidekitScaffold
import hrln.interactive.sidekit.design.components.SidekitText
import hrln.interactive.sidekit.design.components.listFade
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.locale.AppLanguage
import hrln.interactive.sidekit.locale.AppLanguage.findActivity

/** Settings → Language: the phone's language, or one of the languages Sidekit ships. */
@Composable
internal fun LanguageScreen(back: () -> Unit) {
    val context = LocalContext.current
    // MainActivity handles locale changes in place, so re-read the choice when the language changes.
    val locales = LocalConfiguration.current.locales
    val selected = remember(locales) { AppLanguage.selected(context) }
    // null is "follow the phone"; each language is named in itself so it can be found from any.
    val choices = remember { listOf<String?>(null) + AppLanguage.supported }
    val current = stringResource(R.string.language_current)
    val list = rememberLazyListState()
    SidekitScaffold(stringResource(R.string.language_title), actions = listOf(SideAction.back("back", stringResource(R.string.language_back), back))) { padding ->
        SidekitList(padding, state = list) {
            item { SidekitText(stringResource(R.string.language_intro), style = SidekitTheme.typography.label, maxLines = Int.MAX_VALUE) }
            itemsIndexed(choices) { index, tag ->
                SidekitItem(
                    tag?.let(AppLanguage::nativeName) ?: stringResource(R.string.language_system),
                    Modifier.listFade(list, index),
                    secondary = if (tag == selected) current else null,
                    secondaryToEnd = true,
                    onClick = { context.findActivity()?.let { AppLanguage.select(it, tag) } }
                )
            }
        }
    }
}

/** The Settings row value: the chosen language's own name, or nothing while it follows the phone. */
@Composable
internal fun languageSummary(): String? {
    val context = LocalContext.current
    val locales = LocalConfiguration.current.locales
    val selected = remember(locales) { AppLanguage.selected(context) }
    return selected?.let(AppLanguage::nativeName)
}
