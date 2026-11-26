package hrln.interactive.sidekit.design.canvas

import android.view.ViewTreeObserver
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import hrln.interactive.sidekit.design.action.ActionBarHost
import hrln.interactive.sidekit.design.action.ChromeState
import hrln.interactive.sidekit.design.action.HeaderHost
import hrln.interactive.sidekit.design.action.LocalChrome
import hrln.interactive.sidekit.design.action.LocalHeaderOptionsInset
import hrln.interactive.sidekit.design.action.headerOptionsInset
import hrln.interactive.sidekit.design.input.Keypad
import hrln.interactive.sidekit.design.input.LocalKeypad
import hrln.interactive.sidekit.design.theme.SidekitTheme

/**
 * The shared canvas every Sidekit screen is designed on: 480 wide and at least 640 tall. The width
 * is fixed so rows and the action bar keep their proportions; the height grows on taller keypad
 * phones instead of leaving a letterbox.
 */
object DesignCanvas {
    const val WIDTH = 480
    const val MIN_HEIGHT = 640

    /**
     * Density that fits the 480 x [MIN_HEIGHT] design into [widthPx] x [heightPx], so 1.dp in
     * Sidekit code is exactly 1 Figma px. A portrait panel at least 3:4 tall is width-limited:
     * the canvas fills its width and grows down. A wider panel is height-limited and keeps dark
     * side bars instead of stretching rows sideways.
     */
    fun density(widthPx: Int, heightPx: Int): Float = minOf(widthPx / WIDTH.toFloat(), heightPx / MIN_HEIGHT.toFloat())
}

/** The old Sidephone-only name for [DesignCanvas], kept for published SDK consumers. */
@Deprecated("Use DesignCanvas; the canvas is 480 wide and grows taller on other keypad phones.", ReplaceWith("DesignCanvas"))
object SidephoneCanvas {
    const val WIDTH = DesignCanvas.WIDTH
    const val HEIGHT = DesignCanvas.MIN_HEIGHT

    fun density(widthPx: Int, heightPx: Int): Float = DesignCanvas.density(widthPx, heightPx)
}

/**
 * Lays [content] out on the shared 480-wide design canvas. On the Sidephone this is exactly
 * 480 x 640 and a 1:1 mapping to physical pixels whatever density the system reports. On a taller
 * keypad phone the canvas keeps that width and grows down to fill the display. On a wider screen
 * it stays 640 tall and is centred between dark side bars, since rows and the action bar would only
 * stretch sideways. Font scale is pinned to 1 because sizes follow the design, not the system font
 * setting.
 *
 * The canvas also owns the system chrome every screen shares: the title ([HeaderHost]) and the
 * action bar ([ActionBarHost]), drawn above the screens so they morph between them instead of
 * moving with them. The bar rides on top of the soft keyboard. [keypad] (T9 typing) is provided
 * to text fields here.
 *
 * Overdraw: the activity drops its window background, so the canvas is the only full-screen
 * fill. The dark side bars are only painted when the window is wider than the design.
 */
@Composable
fun SidekitCanvas(
    modifier: Modifier = Modifier,
    chrome: ChromeState = remember { ChromeState() },
    keypad: Keypad? = null,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val scale = DesignCanvas.density(constraints.maxWidth, constraints.maxHeight)
        val sideBars = constraints.maxWidth - DesignCanvas.WIDTH * scale >= 1f
        if (sideBars) Box(Modifier.fillMaxSize().background(SidekitTheme.colors.dark))
        CompositionLocalProvider(
            LocalDensity provides Density(density = scale, fontScale = 1f),
            LocalChrome provides chrome,
            LocalKeypad provides keypad
        ) {
            val hasFocus = revealFocusOnKeys()
            // The header's Options button is a touch affordance: it follows the window's input
            // mode, so a keypad press hides it again in the same frame it arrives.
            val touchMode = rememberTouchMode()
            // The button's space in the top band, published to the screens: a custom header and a
            // body drawn in that band reserve it so neither runs under the button, and both get the
            // width back as soon as a keypad press takes the button away.
            val optionsInset = headerOptionsInset(touchMode.value, chrome.hasOptions, SidekitTheme.spacing)
            // Screens that publish chrome without a scaffold (camera, video) still need an Up
            // target. The scaffold registers a better one that includes its header.
            val canvasFocus = remember { FocusRequester() }
            DisposableEffect(chrome, canvasFocus) {
                chrome.registerCanvasContentFocus(canvasFocus)
                onDispose { chrome.unregisterCanvasContentFocus(canvasFocus) }
            }
            Box(
                modifier = Modifier
                    // Design width, full height: the width is exactly 480 on any panel at least
                    // 3:4 tall, and the height is 640 or more.
                    .width(DesignCanvas.WIDTH.dp)
                    .fillMaxHeight()
                    .onFocusChanged { hasFocus.value = it.hasFocus }
                    // Keep the content's last focused child saved for the action bar's Up key.
                    // The press records where focus is before the move, the release where it
                    // landed; while focus is in the bar saveContentFocus() is a no-op, so the
                    // memory survives.
                    .onPreviewKeyEvent { event ->
                        if (event.key.isNavigationKey) chrome.saveContentFocus()
                        false
                    }
                    // Screens slide during navigation; keep them inside the panel.
                    .clipToBounds()
                    .background(SidekitTheme.colors.surfaceA1)
            ) {
                CompositionLocalProvider(LocalHeaderOptionsInset provides optionsInset) {
                    Box(Modifier.fillMaxSize().focusRequester(canvasFocus)) { content() }
                }
                // Back closes the options bar before it goes back a screen.
                BackHandler(enabled = chrome.optionsOpen) { chrome.closeOptions() }
                HeaderHost(
                    state = chrome,
                    touchMode = touchMode.value,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(start = SidekitTheme.spacing.pagePadding, end = SidekitTheme.spacing.pagePadding, top = SidekitTheme.spacing.pagePadding)
                )
                ActionBarHost(
                    state = chrome,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .imePadding()
                        .padding(SidekitTheme.spacing.pagePadding)
                )
            }
        }
    }
}

private val Key.isNavigationKey: Boolean
    get() = this == Key.DirectionUp || this == Key.DirectionDown ||
        this == Key.DirectionLeft || this == Key.DirectionRight

/**
 * Whether the window is in touch mode. Android flips this on the first keypad press and back on
 * the first touch, before the event is dispatched, so the header's touch-only Options button can
 * hide and show in the same frame as the input that changed the mode.
 */
@Composable
private fun rememberTouchMode(): MutableState<Boolean> {
    val view = LocalView.current
    val touchMode = remember(view) { mutableStateOf(view.isInTouchMode) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnTouchModeChangeListener { touchMode.value = it }
        view.viewTreeObserver.addOnTouchModeChangeListener(listener)
        onDispose { view.viewTreeObserver.removeOnTouchModeChangeListener(listener) }
    }
    return touchMode
}

/**
 * Android swallows the first D-pad press after a touch: it only takes the window out of touch
 * mode. With nothing focused that press would show nothing, so when touch mode ends this focuses
 * the top item, and the first press already shows where you are. Returns the state the canvas
 * keeps up to date with whether anything inside it is focused.
 */
@Composable
private fun revealFocusOnKeys(): MutableState<Boolean> {
    val hasFocus = remember { mutableStateOf(false) }
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnTouchModeChangeListener { inTouchMode ->
            if (!inTouchMode && !hasFocus.value) {
                // Post the move so an Options hold that is already focusing the action bar can
                // land first; if it did, this fallback sees the focus and stays out of the way.
                view.post {
                    if (!hasFocus.value) focusManager.moveFocus(FocusDirection.Down)
                }
            }
        }
        view.viewTreeObserver.addOnTouchModeChangeListener(listener)
        onDispose { view.viewTreeObserver.removeOnTouchModeChangeListener(listener) }
    }
    return hasFocus
}
