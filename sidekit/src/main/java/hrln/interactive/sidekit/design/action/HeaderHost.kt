package hrln.interactive.sidekit.design.action

import androidx.compose.animation.core.animate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hrln.interactive.sidekit.design.components.SidekitHeader
import hrln.interactive.sidekit.design.components.SidekitIconImage
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.motion.SidekitMotion
import hrln.interactive.sidekit.design.theme.SidekitIndication
import hrln.interactive.sidekit.design.theme.SidekitSpacing
import hrln.interactive.sidekit.design.theme.SidekitTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Stable
private class HeaderModel {
    var current: HeaderSpec? by mutableStateOf(null, referentialEqualityPolicy())
    var previous: HeaderSpec? by mutableStateOf(null, referentialEqualityPolicy())

    /** Swap progress; reset synchronously (see ActionSlot.contentProgress). */
    var progress by mutableFloatStateOf(1f)
        private set
    private var job: Job? = null
    private var owner: ChromePublisher? = null

    fun sync(top: ChromePublisher?, spec: HeaderSpec?, scope: CoroutineScope) {
        val old = current
        val newScreen = top !== owner
        owner = top
        current = spec
        // A screen without the shared title draws its own header in that place (the launcher's
        // clock): the old title goes at once, as fading it there would draw it over that header.
        if (spec == null) {
            job?.cancel()
            previous = null
            progress = 1f
            return
        }
        // A screen updating its own title (the clock ticking, a note saved) changes in place; only a
        // new screen plays the swap.
        if (newScreen && old?.title != spec?.title && (old != null || spec != null)) {
            previous = old
            progress = 0f
            job?.cancel()
            job = scope.launch {
                animate(0f, 1f, animationSpec = SidekitMotion.title) { v, _ -> progress = v }
                previous = null
            }
        }
    }
}

/**
 * The end space the header's touch-only Options button takes up in the top band, measured inside the
 * page-padded content area — zero whenever the button is not on screen.
 *
 * The button belongs to the header, which the canvas draws above the screens, so it takes space from
 * whatever else shares that band: the shared title (see [HeaderHost]) and a custom
 * [hrln.interactive.sidekit.design.components.SidekitScaffold] header, which reserves this value for
 * itself. A body that starts in the band (a card drawn on Now Playing, say) opts in the same way:
 *
 *     Modifier.padding(end = LocalHeaderOptionsInset.current)
 *
 * The value is zero in keypad mode and on a screen without options, because the button is neither
 * shown nor reserved there: content keeps the full width whenever the button would not be in the way.
 */
val LocalHeaderOptionsInset = compositionLocalOf { 0.dp }

/**
 * What [LocalHeaderOptionsInset] holds: the button's hit target plus its inset from the panel edge,
 * less the page padding the content area already gave up. Zero while the button is not on screen, so
 * entering keypad mode gives the band its full width back instead of leaving a gap.
 */
internal fun headerOptionsInset(touchMode: Boolean, hasOptions: Boolean, spacing: SidekitSpacing): Dp = if (touchMode && hasOptions) {
    spacing.itemPadding + OPTIONS_BUTTON_SIZE - spacing.pagePadding
} else {
    0.dp
}

/**
 * The shared screen title. On navigation the old title fades out and the new one fades in, both
 * in the same fixed place: the title is the screen's anchor, so it never moves. Both are single
 * text layers faded in the draw phase, measured once.
 *
 * In touch mode, a screen with options also gets a small overflow button in the top-right corner,
 * centred on the title's line. It is the touch equivalent of the keypad's Options key: the keypad
 * does not need it, so it disappears as soon as a key is pressed — and with it the space it held,
 * so a keypad never pays for a button it cannot reach. The title keeps its own inset clear while
 * the button is shown, and [LocalHeaderOptionsInset] carries the same reservation to the screens.
 */
@Composable
internal fun HeaderHost(state: ChromeState, modifier: Modifier = Modifier, touchMode: Boolean = false) {
    val model = remember { HeaderModel() }
    val scope = rememberCoroutineScope()
    // Read here only to subscribe: HeaderHost recomposes whenever the top screen or its title changes.
    state.top?.header
    val hasOptions = state.hasOptions
    // Read at apply time, not from this composition: a screen attaching in the same frame has
    // already synced through onHeaderChange, and syncing back to the stale `top` would undo it.
    SideEffect { state.top?.let { model.sync(it, it.header, scope) } }
    // Publishers notify in the frame a screen changes, so the title never trails the body by a frame.
    DisposableEffect(state, model) {
        state.onHeaderChange = { state.top?.let { model.sync(it, it.header, scope) } }
        onDispose { state.onHeaderChange = null }
    }

    val density = LocalDensity.current
    val travel = with(density) { TITLE_TRAVEL.toPx() }
    // The title's own inset is only the hit target: its text box already keeps itemPadding inside.
    // A custom header has no such inset, which is why [LocalHeaderOptionsInset] carries that too.
    val optionsShown = touchMode && hasOptions
    val titleModifier = Modifier.padding(end = if (optionsShown) OPTIONS_BUTTON_SIZE else 0.dp)
    // The title is drawn from headerPaddingTop down, so its line box starts there: put the button's
    // centre on the middle of that box, and figure and text sit on the same axis.
    val optionsTop = with(density) {
        (
            SidekitTheme.spacing.headerPaddingTop + SidekitTheme.typography.title.lineHeight.toDp() / 2 -
                OPTIONS_BUTTON_SIZE / 2
            ).coerceAtLeast(0.dp)
    }
    val current = model.current
    val previous = model.previous
    // As in the action bar: draw what is composed at rest until composition catches up with a swap
    // that has already reset the (draw-phase) progress.
    val progress = { if (model.current !== current || model.previous !== previous) 1f else model.progress }
    Box(
        modifier
            .fillMaxWidth()
            .height(SidekitTheme.spacing.headerHeight)
            .then(current?.modifier ?: Modifier)
    ) {
        previous?.let { old ->
            SidekitHeader(
                title = old.title,
                textAlign = old.align,
                modifier = titleModifier.graphicsLayer {
                    val p = progress()
                    // Out first, then in: two titles never share the slot (overlapping text reads as garbage).
                    alpha = (1f - p / HANDOFF).coerceIn(0f, 1f)
                    translationY = -p * travel
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
            )
        }
        if (current != null) {
            SidekitHeader(
                title = current.title,
                textAlign = current.align,
                modifier = titleModifier.graphicsLayer {
                    val p = progress()
                    // Composition hasn't caught up with a screen that has no shared title: hide it now.
                    alpha = if (model.current == null) 0f else ((p - HANDOFF) / (1f - HANDOFF)).coerceIn(0f, 1f)
                    translationY = (1f - p) * travel
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
            )
        }
        if (optionsShown) {
            HeaderOptionsButton(
                expanded = state.optionsOpen,
                onClick = state::toggleOptions,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = SidekitTheme.spacing.itemPadding, top = optionsTop)
            )
        }
    }
}

/**
 * The touch-only way into the Options bar. It is deliberately bare: a 56 dp hit target around the
 * same 40 dp icon every other control uses, with the standard SideKit press veil. The icon turns
 * into a cross while the bar is open, so the same spot closes it. It never takes D-pad focus, so
 * keypad navigation is exactly as it was without it.
 */
@Composable
private fun HeaderOptionsButton(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Not "Close options": the action bar's own close button already says that, and two controls
    // with the same description on one screen are ambiguous to screen readers and to tests.
    val label = if (expanded) "Hide options" else "Options"
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(OPTIONS_BUTTON_SIZE)
            .clip(SidekitTheme.shapes.control)
            .focusProperties { canFocus = false }
            .clickable(
                interactionSource = interaction,
                indication = SidekitIndication,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        SidekitIconImage(if (expanded) SidekitIcon.Close else SidekitIcon.MoreHoriz)
    }
}

/** How far the titles slide during a swap: none, so the title keeps a fixed position. */
private val TITLE_TRAVEL = 0.dp

/** Fraction of the swap spent fading the old title out before the new one starts fading in. */
private const val HANDOFF = 0.35f

/**
 * Touch target of the header's Options button; also the title space reserved while it is on screen.
 * Larger than the icon it wraps, so the button is easy to hit without growing the glyph.
 */
private val OPTIONS_BUTTON_SIZE = 56.dp
