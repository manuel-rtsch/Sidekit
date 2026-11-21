package hrln.interactive.sidekit.apps.translate

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hrln.interactive.sidekit.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The two language/text pairs on the Translate screen, top to bottom. */
enum class TranslateSide { Source, Target }
data class TranslatePane(val language: TranslateLanguage, val text: String)
enum class TranslateMode { Translate, History, Large, Language }
data class SpeechRequest(val text: String, val language: TranslateLanguage)

/** A message for the notice bar, resolved by the UI; [language] fills a `%1$s` in [message] when set. */
data class TranslateNotice(@StringRes val message: Int, val language: TranslateLanguage? = null)

data class TranslateUiState(
    val source: TranslatePane = TranslatePane(TranslateLanguage.English, ""),
    val target: TranslatePane = TranslatePane(TranslateLanguage.Chinese, ""),
    val talking: TranslateSide? = null,
    val translating: Boolean = false,
    val mode: TranslateMode = TranslateMode.Translate,
    /** The side whose language is being chosen while [mode] is [TranslateMode.Language]. */
    val picking: TranslateSide = TranslateSide.Source,
    val error: TranslateNotice? = null,
    val microphonePermissionNeeded: Boolean = false
) {
    fun pane(side: TranslateSide) = if (side == TranslateSide.Source) source else target
    fun toggleMic(side: TranslateSide): TranslateUiState = copy(talking = if (talking == side) null else side, error = null)
}

class TranslateViewModel(
    private val repository: TranslateRepository,
    private val translator: Translator
) : ViewModel() {
    private val _state = MutableStateFlow(TranslateUiState())
    val state: StateFlow<TranslateUiState> = _state.asStateFlow()
    val history: StateFlow<List<Translation>?> = repository.history
    private val _speech = MutableSharedFlow<SpeechRequest>(extraBufferCapacity = 1)
    val speech: SharedFlow<SpeechRequest> = _speech.asSharedFlow()
    private var translateJob: Job? = null
    private var sourceLanguage = TranslateLanguage.English
    private var targetLanguage = TranslateLanguage.Chinese

    fun onMic(side: TranslateSide) {
        val wasTalking = _state.value.talking == side
        _state.update { it.toggleMic(side) }
        if (!wasTalking && _state.value.talking == side) {
            _state.update { it.copy(microphonePermissionNeeded = true) }
        }
    }

    fun microphonePermissionResult(granted: Boolean) {
        _state.update { it.copy(microphonePermissionNeeded = false) }
        if (!granted) {
            _state.update { it.copy(talking = null) }
            say(R.string.translate_error_microphone)
        }
    }

    fun onHeard(side: TranslateSide, text: String) {
        if (text.isBlank()) return
        if (side == TranslateSide.Source) {
            _state.update { it.copy(source = it.source.copy(text = text), error = null) }
            translate()
        } else {
            translate(text, targetLanguage, sourceLanguage, reverse = true)
        }
    }

    fun onSpeechError(notice: TranslateNotice) {
        _state.update { it.copy(talking = null, error = notice) }
    }

    fun stopTalking() {
        _state.update { it.copy(talking = null) }
    }

    fun setText(text: String) {
        _state.update { it.copy(source = it.source.copy(text = text.take(MyMemoryTranslator.MAX_CHARS)), error = null) }
    }

    fun translate() {
        val text = _state.value.source.text.trim()
        if (text.isEmpty()) {
            say(R.string.translate_error_empty)
            return
        }
        translate(text, sourceLanguage, targetLanguage, reverse = false)
    }

    private fun translate(text: String, from: TranslateLanguage, to: TranslateLanguage, reverse: Boolean) {
        translateJob?.cancel()
        _state.update { it.copy(translating = true, error = null) }
        translateJob = viewModelScope.launch {
            try {
                val result = translator.translate(text, from, to)
                repository.add(text, result, from, to)
                _state.update {
                    if (reverse) {
                        it.copy(source = it.source.copy(text = result), target = it.target.copy(text = text), translating = false)
                    } else {
                        it.copy(target = it.target.copy(text = result), translating = false)
                    }
                }
                if (_state.value.talking != null) _speech.tryEmit(SpeechRequest(result, to))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(translating = false) }
                say(R.string.translate_error_failed)
            }
        }
    }

    fun cancelTranslation() {
        translateJob?.cancel()
        _state.update { it.copy(translating = false) }
    }

    fun swapLanguages() {
        // An in-flight result belongs to the old direction and would land in the wrong pane.
        cancelTranslation()
        val oldSource = sourceLanguage
        sourceLanguage = targetLanguage
        targetLanguage = oldSource
        _state.update {
            it.copy(
                source = TranslatePane(sourceLanguage, it.target.text),
                target = TranslatePane(targetLanguage, it.source.text)
            )
        }
    }

    fun language(side: TranslateSide): TranslateLanguage = if (side == TranslateSide.Source) sourceLanguage else targetLanguage

    /** Opens the language list for [side], dropping any pending microphone request. */
    fun pickLanguage(side: TranslateSide) {
        _state.update { it.copy(mode = TranslateMode.Language, picking = side, talking = null, microphonePermissionNeeded = false) }
    }

    /**
     * Sets the language being picked and returns to the translator. Choosing the language already on the
     * other side swaps the pair rather than translating a language into itself. An existing translation
     * is redone in the new pair so the result never sits under the wrong language.
     */
    fun chooseLanguage(language: TranslateLanguage) {
        val side = _state.value.picking
        val changed = language != language(side)
        val other = if (side == TranslateSide.Source) targetLanguage else sourceLanguage
        if (language == other) {
            // A swap moves each text with its language, so nothing needs translating again.
            swapLanguages()
            show(TranslateMode.Translate)
            return
        }
        if (side == TranslateSide.Source) sourceLanguage = language else targetLanguage = language
        _state.update {
            it.copy(
                source = it.source.copy(language = sourceLanguage),
                target = it.target.copy(language = targetLanguage),
                mode = TranslateMode.Translate
            )
        }
        if (!changed) return
        val current = _state.value
        // A translation still in flight is for the old pair: redo it too, or drop it if there's nothing to redo.
        if (current.source.text.isNotBlank() && (current.target.text.isNotEmpty() || current.translating)) {
            translate()
        } else if (current.translating) {
            cancelTranslation()
        }
    }

    fun clear() {
        cancelTranslation()
        _state.update { it.copy(source = it.source.copy(text = ""), target = it.target.copy(text = ""), error = null) }
    }

    fun show(mode: TranslateMode) {
        _state.update { it.copy(mode = mode) }
    }
    fun open(entry: Translation) {
        sourceLanguage = entry.source
        targetLanguage = entry.target
        _state.update {
            it.copy(
                source = TranslatePane(entry.source, entry.sourceText),
                target = TranslatePane(entry.target, entry.translatedText),
                mode = TranslateMode.Translate
            )
        }
    }

    fun remove(id: Long) = repository.remove(id)
    fun clearHistory() = repository.clear()
    fun speak(text: String, side: TranslateSide) {
        if (text.isNotBlank()) _speech.tryEmit(SpeechRequest(text, if (side == TranslateSide.Source) sourceLanguage else targetLanguage))
    }

    fun say(@StringRes message: Int) {
        _state.update { it.copy(error = TranslateNotice(message)) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    override fun onCleared() {
        translateJob?.cancel()
    }
}

/** Sample content is used only by previews and screenshot tests, never as translation output. */
object TranslateSampleData {
    val base = TranslatePane(TranslateLanguage.English, "Apple") to TranslatePane(TranslateLanguage.Chinese, "苹果")
    val heard = "Apple Oranges and people too eating cheese and banana" to "苹果橙子和人们也吃奶酪和香蕉"
    fun state(talking: TranslateSide? = null) = TranslateUiState(base.first, base.second, talking)
}
