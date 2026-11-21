package hrln.interactive.sidekit.design.components

import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import hrln.interactive.sidekit.design.action.ActionMotion
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.PublishActionOverride
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.input.Dictation
import hrln.interactive.sidekit.design.input.KeypadModule
import hrln.interactive.sidekit.design.input.LocalKeypad
import hrln.interactive.sidekit.design.input.QwertyLayout
import hrln.interactive.sidekit.design.input.T9Engine
import hrln.interactive.sidekit.design.input.T9Mode
import hrln.interactive.sidekit.design.input.T9Session
import hrln.interactive.sidekit.design.input.TextFieldT9Buffer
import hrln.interactive.sidekit.design.input.insertDictated
import hrln.interactive.sidekit.design.input.rememberDictation
import hrln.interactive.sidekit.design.theme.SidekitTheme

/**
 * The SideKit text field. With the Sundial or game controller attached it behaves like any text
 * field, using the virtual keyboard. With a keypad that has letters (T9, mini QWERTY) the virtual
 * keyboard never opens: keys are typed by multi-tap (the letter still cycling is highlighted
 * inline) and the action bar turns into word completions for the letters so far (tap one, or
 * D-pad left / right and OK, to finish the word).
 *
 * When no word is being typed the bar shows the input mode (tap to switch letters / numbers),
 * dictation (speak a phrase; it goes in at the cursor) and Done, which ends typing and gives the
 * screen its own actions back. With the virtual keyboard, dictation is the keyboard's own mic.
 *
 * A single-line field hands D-pad Up / Down to focus navigation instead of the caret: there is no
 * second line to move within, and on a keypad that types the field is the only focused thing on
 * screen, so Up / Down are how the rest of the screen is reached again. Multi-line fields keep the
 * caret. On the mini QWERTY, whose direction cross types letters while a field owns the keypad,
 * tapping the suggestion rocker with nothing to complete also leaves the field, and holding it
 * leaves while finishing the word.
 *
 * @param focusOnKeypad make this the screen's main field: typing on the keypad while nothing is
 *   focused starts typing here.
 * @param dictation offer the typing bar's mic. Off for fields that take numbers or a fixed format,
 *   for private text that shouldn't go to a speech service, and for screens with their own mic.
 *   A masked field (see [masked]) never offers it.
 * @param keyboardType the keyboard the virtual IME opens: text, URI, password and so on.
 * @param autoCorrectEnabled ask the keyboard for autocorrect; null leaves it to the IME.
 * @param masked renders the text as dots, e.g. a password. A masked field hides T9 suggestions and
 *   the composing highlight, because a suggested word would be a guess about a secret that the
 *   field is supposed to conceal, and it is always single-line. Pair it with
 *   `KeyboardType.Password` for the password keyboard.
 * @param imeAction the keyboard's action key. Null keeps the historical derivation: `Done` when
 *   [onDone] is set, `Default` otherwise.
 * @param onNext runs when the action key is `Next`; the caller moves the focus and the keyboard
 *   stays up, so a form is filled without returning the screen's actions.
 * @param contentType the autofill hint for this field, so a password manager can fill it.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun SidekitTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = SidekitTheme.typography.body,
    placeholder: String? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    focusOnKeypad: Boolean = false,
    singleLine: Boolean = false,
    onDone: (() -> Unit)? = null,
    autoFocus: Boolean = false,
    trailingTypingAction: SideAction? = null,
    onKeypadFocus: (() -> Unit)? = null,
    dictation: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    autoCorrectEnabled: Boolean? = null,
    masked: Boolean = false,
    imeAction: ImeAction? = null,
    onNext: (() -> Unit)? = null,
    contentType: ContentType? = null
) {
    val keypad = LocalKeypad.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // An auto-focused field counts as focused from its first frame, so the T9 bar is published in
    // the same frame as the screen and the action bar morphs once, not twice.
    var focused by remember { mutableStateOf(autoFocus) }
    var awaitingFocus by remember { mutableStateOf(autoFocus) }
    if (autoFocus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
    // Where the field goes when the keypad asks to get out of it: the nearest control that way, or
    // nowhere when the screen has none, which is still better than staying in the field.
    val leaveField: (FocusDirection) -> Unit = { direction ->
        if (!focusManager.moveFocus(direction)) focusManager.clearFocus()
    }
    val leaveFromKeypad by rememberUpdatedState { leaveField(FocusDirection.Down) }
    val session = remember(keypad, state) {
        keypad?.let { T9Session(it, TextFieldT9Buffer(state), scope, onEscape = { leaveFromKeypad() }) }
    }
    val t9 = keypad?.typing == true
    // Next means "hand the typing on", Done means "I am finished with this field". Asking for Next
    // without a target leaves the ordinary action in place rather than a key that does nothing.
    val resolvedImeAction = when (imeAction) {
        ImeAction.Next -> when {
            onNext != null -> ImeAction.Next
            onDone != null -> ImeAction.Done
            else -> ImeAction.Default
        }

        null -> if (onDone != null) ImeAction.Done else ImeAction.Default

        else -> imeAction
    }
    // What the action key does: Next hands typing on (the caller moves the caret, the keyboard stays
    // up), everything else wraps the field up.
    val performAction: () -> Unit = {
        if (resolvedImeAction == ImeAction.Next) onNext?.invoke() else onDone?.invoke()
    }
    // The bar's Done also leaves the field. Enter does not: a keypad keeps its mode and the screen
    // keeps the field, which is how the search bar stays open after a submit.
    val finishAndLeave: () -> Unit = {
        session?.finish()
        if (resolvedImeAction != ImeAction.Next) focusManager.clearFocus()
        performAction()
    }

    val activateOnKeypad by rememberUpdatedState(onKeypadFocus)
    if (focusOnKeypad && keypad != null) {
        DisposableEffect(keypad, focusRequester) {
            keypad.registerFallback(focusRequester) { activateOnKeypad?.invoke() }
            onDispose { keypad.unregisterFallback(focusRequester) }
        }
    }
    DisposableEffect(keypad, session) {
        onDispose { if (keypad?.session === session) keypad?.session = null }
    }
    // The module was swapped mid-edit: put the virtual keyboard away for a keypad with letters,
    // bring it up for one without (Sundial, game controller).
    var lastT9 by remember { mutableStateOf(t9) }
    LaunchedEffect(t9, focused) {
        if (focused && t9) {
            keyboard?.hide()
        } else if (focused && lastT9) {
            keyboard?.show()
        }
        lastT9 = t9
    }

    val context = LocalContext.current
    val speech = rememberDictation()
    // Checked up front so phones without a recogniser never show a mic that can only fail.
    val canDictate = !masked && dictation && remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }
    // A word started on the keypad while listening is finished before the phrase goes in, so the
    // phrase never lands inside it.
    val dictate = {
        session?.finish()
        speech.start {
            session?.finish()
            state.insertDictated(it)
        }
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Focus can move while the permission dialog is up; then the phrase has nowhere to go.
        if (granted) {
            if (focused) dictate()
        } else {
            Toast.makeText(context, "Allow the microphone to dictate", Toast.LENGTH_SHORT).show()
        }
    }
    // Leaving the field ends dictation; a phrase still in flight would land in the wrong place.
    LaunchedEffect(focused) { if (!focused) speech.release() }

    // The typing bar belongs to the keypad. A visible on-screen keyboard is the input instead, and
    // the screen behind it has already given up its own bar to it.
    val imeVisible = LocalImeVisible.current ?: WindowInsets.isImeVisible
    if (focused && t9 && keypad != null && session != null && !imeVisible) {
        PublishActionOverride(
            t9Actions(
                maxSuggestions = if (trailingTypingAction == null) MAX_SHOWN else MAX_SHOWN - 1,
                engine = keypad.engine,
                session = session,
                suggestions = !masked,
                dictation = speech.takeIf { canDictate },
                // With a screen's own (square) trailing button there's no room for Done's label:
                // the other three share the rest of the bar equally, like the home dock.
                compact = trailingTypingAction != null,
                onDictate = {
                    when {
                        speech.listening -> speech.stop()
                        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> dictate()
                        else -> microphone.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onDone = finishAndLeave
            ),
            // The Options key while typing picks the input mode directly.
            options = T9Mode.entries.map { mode ->
                val label = when (mode) {
                    T9Mode.MultiTap -> "abc"
                    T9Mode.Numeric -> "123"
                }
                SideAction(
                    "mode.${mode.name}",
                    label,
                    tone = if (keypad.engine.mode == mode) ActionTone.Primary else ActionTone.Muted,
                    contentDescription = "Input mode $label"
                ) { session.setMode(mode) }
            },
            trailingActions = listOfNotNull(trailingTypingAction)
        )
    }

    val colors = SidekitTheme.colors
    val style = remember(textStyle, colors.light) { textStyle.copy(color = colors.light) }
    val scroll = rememberScrollState()
    val layout = remember { mutableStateOf<(() -> TextLayoutResult?)?>(null) }
    val highlightColor = colors.emphasisA1.copy(alpha = 0.55f)
    // Read in the draw phase only: composing a word redraws the field, it doesn't recompose it.
    val highlight: () -> TextRange? = { if (!masked && focused && keypad?.session === session) keypad?.engine?.highlight else null }
    val fieldModifier = modifier
        .focusRequester(focusRequester)
        .onFocusChanged { f ->
            if (awaitingFocus && !f.isFocused) return@onFocusChanged
            awaitingFocus = false
            focused = f.isFocused
            if (keypad == null || session == null) return@onFocusChanged
            if (f.isFocused) {
                keypad.session = session
                keypad.prepare()
            } else if (keypad.session === session) {
                session.finish()
                keypad.session = null
            }
        }
        // A one-line field has no vertical caret targets, so Up / Down mean "leave the field"
        // there. Intercept before the text field: otherwise it eats Up to move the caret to the
        // line start, and the screen never sees the navigation. Modified arrows stay the text
        // field's (Shift+Up selects, Ctrl+arrows move by words).
        .onPreviewKeyEvent { event ->
            if (!singleLine) return@onPreviewKeyEvent false
            val rocker = keypad?.module == KeypadModule.Qwerty && event.nativeKeyEvent.keyCode == QwertyLayout.SUGGEST
            if (rocker) {
                // The suggestion rocker's tap is left alone while a word is composing (the session
                // cycles completions and a hold leaves). With nothing to complete, a tap leaves.
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 &&
                    keypad?.engine?.composing != true
                ) {
                    session?.finish()
                    leaveField(FocusDirection.Down)
                    return@onPreviewKeyEvent true
                }
                return@onPreviewKeyEvent false
            }
            if (event.isShiftPressed || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
                return@onPreviewKeyEvent false
            }
            when (event.key) {
                Key.DirectionUp, Key.DirectionDown -> {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        session?.finish()
                        leaveField(if (event.key == Key.DirectionUp) FocusDirection.Up else FocusDirection.Down)
                    }
                    true
                }

                else -> false
            }
        }
        // Before the IME: on the Sidephone the virtual keyboard stays connected even when hidden,
        // and must not see keypad presses while T9 is typing.
        .onInterceptKeyBeforeSoftKeyboard { event ->
            if (keypad == null || session == null) return@onInterceptKeyBeforeSoftKeyboard false
            val native = keypad.translate(event.nativeKeyEvent) ?: return@onInterceptKeyBeforeSoftKeyboard true
            keypad.observe(native)
            if (!keypad.typing) return@onInterceptKeyBeforeSoftKeyboard false
            val enter = native.keyCode == android.view.KeyEvent.KEYCODE_ENTER || native.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
            if (enter && singleLine) {
                // OK takes a highlighted completion first; otherwise it submits.
                if (session.handleOk(native)) return@onInterceptKeyBeforeSoftKeyboard true
                // A one-line field has no newline: Enter / OK is the field's action.
                if (native.action == android.view.KeyEvent.ACTION_DOWN && native.repeatCount == 0) {
                    session.finish()
                    performAction()
                }
                true
            } else {
                session.onKeyEvent(native)
            }
        }
        // Autofill keeps the hint on the field itself, not on the card around it.
        .then(if (contentType != null) Modifier.contentType(contentType) else Modifier)
    val fieldKeyboardOptions = KeyboardOptions(
        autoCorrectEnabled = autoCorrectEnabled,
        keyboardType = keyboardType,
        showKeyboardOnFocus = !t9,
        imeAction = resolvedImeAction
    )
    val fieldKeyboardAction = when {
        resolvedImeAction == ImeAction.Next -> onNext?.let { next -> KeyboardActionHandler { next() } }
        onDone != null -> KeyboardActionHandler { onDone() }
        else -> null
    }
    val fieldDecoration: TextFieldDecorator = { field ->
        Box(
            Modifier.drawBehind {
                // The T9 word being formed: a soft highlight behind the text, drawn from the text
                // layout, so the cursor stays a plain caret (no selection handles).
                val range = highlight() ?: return@drawBehind
                val result = layout.value?.invoke() ?: return@drawBehind
                val len = result.layoutInput.text.length
                if (range.collapsed || range.max > len) return@drawBehind
                val path = result.getPathForRange(range.min, range.max)
                val dx = if (singleLine) -scroll.value.toFloat() else 0f
                val dy = if (singleLine) 0f else -scroll.value.toFloat()
                translate(dx, dy) { drawPath(path, highlightColor) }
            }
        ) {
            if (placeholder != null && state.text.isEmpty()) SidekitText(placeholder, style = textStyle, color = colors.surfaceA6)
            field()
        }
    }
    if (masked) {
        // A secure field obfuscates the text and keeps it off the semantics tree, so an
        // accessibility service cannot read the secret out of it. It is single-line by design:
        // a password is never a paragraph.
        BasicSecureTextField(
            state = state,
            modifier = fieldModifier,
            textStyle = style,
            cursorBrush = SolidColor(colors.light),
            keyboardOptions = fieldKeyboardOptions,
            onKeyboardAction = fieldKeyboardAction,
            onTextLayout = { getResult -> layout.value = getResult },
            decorator = fieldDecoration,
            textObfuscationMode = TextObfuscationMode.RevealLastTyped,
            scrollState = scroll
        )
    } else {
        BasicTextField(
            state = state,
            modifier = fieldModifier,
            textStyle = style,
            cursorBrush = SolidColor(colors.light),
            keyboardOptions = fieldKeyboardOptions,
            onKeyboardAction = fieldKeyboardAction,
            lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(),
            scrollState = scroll,
            onTextLayout = { getResult -> layout.value = getResult },
            decorator = fieldDecoration
        )
    }
}

/**
 * [SidekitTextField] for a plain `String` value. The field keeps its own [TextFieldState] (T9
 * composes inside it) and reports text changes through [onValueChange].
 */
@Composable
fun SidekitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = SidekitTheme.typography.body,
    placeholder: String? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    focusOnKeypad: Boolean = false,
    singleLine: Boolean = false,
    onDone: (() -> Unit)? = null,
    autoFocus: Boolean = false,
    dictation: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    autoCorrectEnabled: Boolean? = null,
    masked: Boolean = false,
    imeAction: ImeAction? = null,
    onNext: (() -> Unit)? = null,
    contentType: ContentType? = null
) {
    val state = rememberTextFieldState(value)
    val latest by rememberUpdatedState(onValueChange)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { latest(it) }
    }
    LaunchedEffect(value) {
        if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value)
    }
    SidekitTextField(
        state = state,
        modifier = modifier,
        textStyle = textStyle,
        placeholder = placeholder,
        focusRequester = focusRequester,
        focusOnKeypad = focusOnKeypad,
        singleLine = singleLine,
        onDone = onDone,
        autoFocus = autoFocus,
        dictation = dictation,
        keyboardType = keyboardType,
        autoCorrectEnabled = autoCorrectEnabled,
        masked = masked,
        imeAction = imeAction,
        onNext = onNext,
        contentType = contentType
    )
}

/**
 * The keypad typing action bar. Keys are positional (`t9.0`, `t9.1`, `t9.2`) so the bar's buttons
 * are reused as the completions change: each key press swaps labels in place and the strip grows
 * or shrinks between one and three buttons, instead of rebuilding.
 */
private fun t9Actions(engine: T9Engine, session: T9Session, suggestions: Boolean, dictation: Dictation?, compact: Boolean, maxSuggestions: Int, onDictate: () -> Unit, onDone: () -> Unit): List<SideAction> {
    if (engine.composing && suggestions) {
        return engine.candidates.take(maxSuggestions).mapIndexed { i, word ->
            val chosen = i == engine.selected
            SideAction(
                key = "t9.$i",
                label = word,
                tone = if (chosen) ActionTone.Primary else ActionTone.Muted,
                // Longer words get more room so suggestions rarely truncate.
                weight = 1f + 0.12f * word.length + if (chosen) 0.4f else 0f,
                dense = true,
                contentDescription = "Suggestion $word"
            ) { session.pick(i) }
        }
    }
    return listOfNotNull(
        SideAction("t9.0", engine.modeLabel, tone = ActionTone.Muted, weight = if (compact) 1f else 0.8f, dense = true, motion = ActionMotion.Up, contentDescription = "Input mode ${engine.modeLabel}") {
            session.setMode(if (engine.mode == T9Mode.MultiTap) T9Mode.Numeric else T9Mode.MultiTap)
        },
        // No mic for digits: a number is quicker to key than to say.
        if (dictation != null && engine.mode != T9Mode.Numeric) {
            SideAction(
                "t9.1",
                icon = if (dictation.listening) SidekitIcon.MicOff else SidekitIcon.Mic,
                tone = if (dictation.listening) ActionTone.Primary else ActionTone.Muted,
                // Crowded, the bar's buttons take equal shares, like the home dock.
                square = !compact,
                motion = ActionMotion.Up,
                contentDescription = if (dictation.listening) "Stop dictating" else "Dictate",
                onClick = onDictate
            )
        } else {
            null
        },
        SideAction("t9.2", if (compact) null else "Done", SidekitIcon.Check, iconAtEnd = !compact, tone = ActionTone.Secondary, motion = ActionMotion.Up, contentDescription = "Done typing", onClick = onDone)
    )
}

private const val MAX_SHOWN = 3

/** Whether T9 typing is active anywhere (for screens that adapt, e.g. to keep their title visible). */
@Composable
fun rememberT9Active(): Boolean = LocalKeypad.current?.typing == true

/**
 * The on-screen keyboard's visibility as the typing bar sees it. Null reads the window. Internal so
 * tests can pin the state; the bar must not publish itself over a visible IME.
 */
internal val LocalImeVisible = staticCompositionLocalOf<Boolean?> { null }
