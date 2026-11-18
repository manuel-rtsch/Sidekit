package hrln.interactive.sidekit.sdk.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long a deletion can be taken back, counted only while its screen is visible. */
const val UNDO_WINDOW_MS = 5_000L

/**
 * One undo offer, measured in visible time.
 *
 * The five seconds are a budget of attention, not of wall clock: [pause] when the screen leaves
 * the foreground and [resume] when it comes back, so leaving the app mid-window does not silently
 * burn the offer, and returning shows the time that is actually left. Leaving the screen for good
 * is a commit, not a pause: cancel the window and finalise the deletion ([cancel]).
 */
class UndoWindow(
    private val scope: CoroutineScope,
    private val durationMs: Long = UNDO_WINDOW_MS,
    // nanoTime is monotonic on both Android and the JVM, so plain unit tests need no Android stub.
    private val elapsed: () -> Long = { System.nanoTime() / 1_000_000L },
    private val onExpire: () -> Unit
) {
    private var job: Job? = null
    private var remainingMs = durationMs
    private var deadline = 0L
    private var running = false
    private var armed = false

    /** True while a deletion can still be taken back (running or paused). */
    val isPending: Boolean get() = armed

    /** Offers a new deletion, replacing any earlier offer and restarting the five seconds. */
    fun start() {
        job?.cancel()
        armed = true
        remainingMs = durationMs
        run()
    }

    /** Stops the clock while the screen is not visible; [resume] continues with what is left. */
    fun pause() {
        if (!running) return
        remainingMs = (deadline - elapsed()).coerceAtLeast(0L)
        job?.cancel()
        job = null
        running = false
    }

    /** Continues a paused offer, or expires it if its time ran out at the moment it was paused. */
    fun resume() {
        if (!armed || running) return
        if (remainingMs <= 0L) expire() else run()
    }

    /** Drops the offer without restoring anything. Callers finalise the deletion first. */
    fun cancel() {
        job?.cancel()
        job = null
        running = false
        armed = false
        remainingMs = durationMs
    }

    private fun run() {
        running = true
        deadline = elapsed() + remainingMs
        val wait = remainingMs
        job = scope.launch {
            delay(wait)
            job = null
            expire()
        }
    }

    private fun expire() {
        running = false
        armed = false
        remainingMs = 0L
        onExpire()
    }
}

/**
 * Binds an [UndoWindow] to the screen that owns it.
 *
 * Backgrounding pauses the clock and returning resumes it; disposing the screen commits, so the
 * offer never follows the user to another conversation, tab or app.
 */
@Composable
fun UndoWindowLifecycle(window: UndoWindow, commit: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestCommit by rememberUpdatedState(commit)
    DisposableEffect(lifecycle, window) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> window.pause()
                Lifecycle.Event.ON_START -> window.resume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            window.cancel()
            latestCommit()
        }
    }
}

/**
 * Pauses [window] while the screen hides its Undo button (for example a draft's Send took its
 * place), so an offer the user cannot see never expires. Call it after [UndoWindowLifecycle];
 * [pending] must change when a new offer starts so that one is paused too.
 */
@Composable
fun PauseUndoWhileHidden(window: UndoWindow, shown: Boolean, pending: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestShown by rememberUpdatedState(shown)
    LaunchedEffect(window, shown, pending) {
        if (!shown) {
            window.pause()
        } else if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            window.resume()
        }
    }
    // UndoWindowLifecycle resumes on ON_START; registered after it, this pauses again while hidden.
    DisposableEffect(lifecycle, window) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && !latestShown) window.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/** A window for screens that keep their pending undo in composition state rather than a ViewModel. */
@Composable
fun rememberUndoWindow(onExpire: () -> Unit): UndoWindow {
    val scope = rememberCoroutineScope()
    val latestExpire by rememberUpdatedState(onExpire)
    return remember { UndoWindow(scope) { latestExpire() } }
}
