package hrln.interactive.sidekit.apps.translate

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.LocalChrome
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.action.withNotice
import hrln.interactive.sidekit.design.components.ItemColour
import hrln.interactive.sidekit.design.components.SidekitCard
import hrln.interactive.sidekit.design.components.SidekitIconImage
import hrln.interactive.sidekit.design.components.SidekitIconItem
import hrln.interactive.sidekit.design.components.SidekitItem
import hrln.interactive.sidekit.design.components.SidekitItemRow
import hrln.interactive.sidekit.design.components.SidekitList
import hrln.interactive.sidekit.design.components.SidekitScaffold
import hrln.interactive.sidekit.design.components.SidekitSwipeRow
import hrln.interactive.sidekit.design.components.SidekitText
import hrln.interactive.sidekit.design.components.SidekitTextField
import hrln.interactive.sidekit.design.components.SidekitTile
import hrln.interactive.sidekit.design.components.listFade
import hrln.interactive.sidekit.design.components.rememberSwipeRevealGroup
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.navigation.SubApp
import hrln.interactive.sidekit.ui.SidekitPreviewFrame
import hrln.interactive.sidekit.ui.SidephonePreview

object TranslateTags {
    const val INPUT = "translate_input"
}

fun NavGraphBuilder.translateGraph(nav: NavHostController) {
    composable(SubApp.Translate.route) {
        val context = LocalContext.current.applicationContext
        val vm: TranslateViewModel = viewModel(
            factory = remember(context) {
                viewModelFactory { initializer { TranslateViewModel(Translate.repository(context), MyMemoryTranslator()) } }
            }
        )
        val state by vm.state.collectAsStateWithLifecycle()
        val history by vm.history.collectAsStateWithLifecycle()
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), vm::microphonePermissionResult)
        TranslateSpeechEffects(vm, state.talking, state.microphonePermissionNeeded, state.source.language, state.target.language)
        val chrome = LocalChrome.current
        // Registered after the canvas's own handler, so this one is asked first: close open options
        // (History's "Clear history") before leaving the mode, as Back does everywhere else.
        BackHandler(enabled = state.mode != TranslateMode.Translate) {
            if (chrome?.optionsOpen == true) chrome.closeOptions() else vm.show(TranslateMode.Translate)
        }
        val baseActions = when (state.mode) {
            TranslateMode.Translate -> if (state.translating) {
                listOf(SideAction.cancel("translate", onClick = vm::cancelTranslation))
            } else {
                listOf(
                    SideAction.back("history", stringResource(R.string.translate_history)) { vm.show(TranslateMode.History) },
                    SideAction.forward("translate", stringResource(R.string.translate_action), vm::translate)
                )
            }

            TranslateMode.History, TranslateMode.Large, TranslateMode.Language -> listOf(SideAction.back("back", stringResource(R.string.translate_title)) { vm.show(TranslateMode.Translate) })
        }
        val error = state.error?.let { notice ->
            notice.language?.let { stringResource(notice.message, it.label) } ?: stringResource(notice.message)
        }
        val actions = when {
            error != null -> withNotice(error, vm::dismissError, emptyList())

            state.microphonePermissionNeeded -> listOf(
                SideAction.cancel("cancel") { vm.microphonePermissionResult(false) },
                SideAction("allow", stringResource(R.string.translate_allow), tone = ActionTone.Secondary) { permission.launch(Manifest.permission.RECORD_AUDIO) }
            )

            else -> baseActions
        }
        val options = if (state.mode == TranslateMode.Translate) {
            listOf(
                SideAction("source", stringResource(R.string.translate_from_language, state.source.language.label)) { vm.pickLanguage(TranslateSide.Source) },
                SideAction("target", stringResource(R.string.translate_to_language, state.target.language.label)) { vm.pickLanguage(TranslateSide.Target) },
                SideAction("swap", stringResource(R.string.translate_swap), SidekitIcon.Translate, onClick = vm::swapLanguages),
                SideAction("large", stringResource(R.string.translate_large_text), SidekitIcon.SmartDisplay) { vm.show(TranslateMode.Large) },
                SideAction("clear", stringResource(R.string.translate_clear), SidekitIcon.Close, onClick = vm::clear)
            )
        } else if (state.mode == TranslateMode.History && !history.isNullOrEmpty()) {
            listOf(SideAction("clear", stringResource(R.string.translate_clear_history), SidekitIcon.DeleteForever, tone = ActionTone.Destructive, onClick = vm::clearHistory))
        } else {
            emptyList()
        }
        val title = when (state.mode) {
            TranslateMode.Translate -> stringResource(R.string.translate_title)
            TranslateMode.History -> stringResource(R.string.translate_translations)
            TranslateMode.Large -> state.target.language.label
            TranslateMode.Language -> languageListTitle(state.picking)
        }
        SidekitScaffold(title = title, actions = actions, options = options) { padding ->
            when (state.mode) {
                TranslateMode.Translate -> TranslateBody(padding, state, vm::onMic, vm::setText, vm::pickLanguage, vm::speak)
                TranslateMode.Language -> LanguageList(padding, vm.language(state.picking), vm::chooseLanguage)
                TranslateMode.Large -> LargeTranslation(padding, state.target.text)
                TranslateMode.History -> TranslationHistory(padding, history.orEmpty(), vm::open, vm::remove)
            }
        }
    }
}

/** Kept as a small state-driven composable for the Figma previews and UI tests. */
@Composable
fun TranslateScreen(
    state: TranslateUiState,
    onMic: (TranslateSide) -> Unit,
    onText: (String) -> Unit = {},
    onTranslate: () -> Unit = {}
) {
    SidekitScaffold(
        title = stringResource(R.string.translate_title),
        actions = listOf(SideAction.forward("translate", stringResource(R.string.translate_action), onTranslate))
    ) { padding ->
        TranslateBody(padding, state, onMic, onText, {}, { _, _ -> })
    }
}

@Composable
private fun TranslateBody(
    padding: androidx.compose.foundation.layout.PaddingValues,
    state: TranslateUiState,
    onMic: (TranslateSide) -> Unit,
    onText: (String) -> Unit,
    onPickLanguage: (TranslateSide) -> Unit,
    onSpeak: (String, TranslateSide) -> Unit
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(padding),
        verticalArrangement = Arrangement.spacedBy(SidekitTheme.spacing.itemOuterGap)
    ) {
        TranslateSide.entries.forEach { side ->
            val pane = state.pane(side)
            LanguageRow(pane.language, talking = state.talking == side, onPick = { onPickLanguage(side) }, onMic = { onMic(side) })
            if (side == TranslateSide.Source) {
                SidekitCard(Modifier.fillMaxWidth().heightIn(min = SidekitTheme.spacing.itemHeight), color = SidekitTheme.colors.surfaceA2) {
                    SidekitTextField(
                        value = pane.text,
                        onValueChange = onText,
                        modifier = Modifier.fillMaxWidth().testTag(TranslateTags.INPUT),
                        placeholder = stringResource(R.string.translate_placeholder),
                        focusOnKeypad = true,
                        autoFocus = true,
                        // Translate has its own mic, which listens in the chosen source language.
                        dictation = false
                    )
                }
            } else {
                TextRow(
                    if (state.translating) stringResource(R.string.translate_translating) else pane.text.ifEmpty { stringResource(R.string.translate_translation) },
                    // Tapping the result reads it aloud in the target language.
                    onClick = if (pane.text.isNotBlank() && !state.translating) ({ onSpeak(pane.text, side) }) else null
                )
            }
        }
        if (state.microphonePermissionNeeded) {
            SidekitText(stringResource(R.string.translate_microphone_rationale), style = SidekitTheme.typography.caption, color = SidekitTheme.colors.surfaceA6, maxLines = Int.MAX_VALUE)
        }
        SidekitText(pluralStringResource(R.plurals.translate_service, MyMemoryTranslator.MAX_CHARS, MyMemoryTranslator.MAX_CHARS), style = SidekitTheme.typography.small, color = SidekitTheme.colors.surfaceA6)
    }
}

@Composable
private fun LanguageRow(language: TranslateLanguage, talking: Boolean, onPick: () -> Unit, onMic: () -> Unit) {
    val colors = SidekitTheme.colors
    val name = language.label
    SidekitItemRow {
        SidekitTile(Modifier.weight(1f), onClick = onPick, onClickLabel = stringResource(R.string.translate_change_language), contentAlignment = Alignment.CenterStart) {
            Row(Modifier.padding(horizontal = SidekitTheme.spacing.itemPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SidekitIconImage(SidekitIcon.Translate, size = 44.dp)
                SidekitText(name, Modifier.weight(1f))
                SidekitIconImage(SidekitIcon.ArrowRightAlt)
            }
        }
        SidekitIconItem(
            icon = if (talking) SidekitIcon.MicOff else SidekitIcon.Mic,
            contentDescription = stringResource(if (talking) R.string.translate_stop_listening else R.string.translate_speak, name),
            modifier = Modifier.size(SidekitTheme.spacing.itemHeight),
            color = if (talking) colors.dangerA2 else colors.emphasisA1,
            iconSize = 44.dp,
            onClick = onMic
        )
    }
}

@Composable
private fun TextRow(text: String, onClick: (() -> Unit)? = null) {
    SidekitCard(Modifier.fillMaxWidth().heightIn(min = SidekitTheme.spacing.itemHeight), color = SidekitTheme.colors.surfaceA2, onClick = onClick) {
        SidekitText(text, color = SidekitTheme.colors.surfaceA6, maxLines = Int.MAX_VALUE)
    }
}

/** The language list on its own, for previews and screenshot tests. */
@Composable
fun TranslateLanguageScreen(side: TranslateSide, selected: TranslateLanguage, onChoose: (TranslateLanguage) -> Unit = {}) {
    SidekitScaffold(
        title = languageListTitle(side),
        actions = listOf(SideAction.back("back", stringResource(R.string.translate_title)) {})
    ) { padding -> LanguageList(padding, selected, onChoose) }
}

@Composable
private fun languageListTitle(side: TranslateSide): String = stringResource(if (side == TranslateSide.Source) R.string.translate_from_title else R.string.translate_to_title)

@Composable
private fun LanguageList(
    padding: androidx.compose.foundation.layout.PaddingValues,
    selected: TranslateLanguage,
    onChoose: (TranslateLanguage) -> Unit
) {
    val listState = rememberLazyListState()
    // The row that opened the list is gone, so nothing holds focus and Up/Down would do nothing.
    // Start on the ticked language; a frame is needed because LazyColumn composes rows during layout.
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { selectedFocus.requestFocus() }
    }
    SidekitList(padding, state = listState) {
        itemsIndexed(TranslateLanguage.entries, key = { _, it -> it.name }) { index, language ->
            val active = language == selected
            SidekitItem(
                language.label,
                Modifier.listFade(listState, index).then(if (active) Modifier.focusRequester(selectedFocus) else Modifier),
                icon = if (active) SidekitIcon.Check else null,
                colour = if (active) ItemColour.Secondary else ItemColour.Primary,
                onClick = { onChoose(language) }
            )
        }
    }
}

@Composable
private fun LargeTranslation(padding: androidx.compose.foundation.layout.PaddingValues, text: String) {
    Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
        SidekitText(text.ifEmpty { stringResource(R.string.translate_large_empty) }, style = SidekitTheme.typography.headline, maxLines = Int.MAX_VALUE)
    }
}

@Composable
private fun TranslationHistory(
    padding: androidx.compose.foundation.layout.PaddingValues,
    history: List<Translation>,
    onOpen: (Translation) -> Unit,
    onRemove: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    val swipe = rememberSwipeRevealGroup()
    SidekitList(padding, state = listState) {
        if (history.isEmpty()) item { TextRow(stringResource(R.string.translate_history_empty)) }
        itemsIndexed(history, key = { _, it -> it.id }) { index, entry ->
            SidekitSwipeRow(key = entry.id, group = swipe, onDelete = { onRemove(entry.id) }, modifier = Modifier.animateItem().listFade(listState, index), deleteLabel = stringResource(R.string.translate_delete)) {
                SidekitItem(entry.sourceText, secondary = entry.translatedText, onClick = { onOpen(entry) })
            }
        }
    }
}

@SidephonePreview
@Composable
private fun TranslateBasePreview() = SidekitPreviewFrame { TranslateScreen(TranslateSampleData.state(), {}) }

@SidephonePreview
@Composable
private fun TranslateTalkingPreview() = SidekitPreviewFrame { TranslateScreen(TranslateSampleData.state(TranslateSide.Target), {}) }

@SidephonePreview
@Composable
private fun TranslateLargePromptPreview() = SidekitPreviewFrame {
    TranslateScreen(TranslateSampleData.state().copy(source = TranslatePane(TranslateLanguage.English, TranslateSampleData.heard.first), target = TranslatePane(TranslateLanguage.Chinese, TranslateSampleData.heard.second)), {})
}

@SidephonePreview
@Composable
private fun TranslateLanguagePreview() = SidekitPreviewFrame { TranslateLanguageScreen(TranslateSide.Target, TranslateLanguage.Chinese) }
