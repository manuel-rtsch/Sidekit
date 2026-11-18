package hrln.interactive.sidekit.sdk.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import hrln.interactive.sidekit.design.action.ChromeState
import hrln.interactive.sidekit.design.input.Keypad
import hrln.interactive.sidekit.design.input.KeypadPreferences
import hrln.interactive.sidekit.sdk.SidekitStores
import hrln.interactive.sidekit.sdk.input.SidekitInputController
import hrln.interactive.sidekit.sdk.theme.SidekitThemeStore

/**
 * The Sidekit activity shell.
 *
 * It owns the theme, the keypad lifetime, the shared input loop and the full-screen window setup.
 * A host keeps its app-specific handling (HOME intents, links, services) in its subclass, which
 * only has to render [SidekitContent].
 *
 * [stores] is host-backed when a compatible Sidekit is installed and local otherwise. Subclasses
 * can override [stores], [themeStore] or [keypadPreferences] to supply their own.
 */
abstract class SidekitActivity : ComponentActivity() {
    /** The shared title + action bar; held here so hardware keys (Options) can reach it. */
    protected val chrome = ChromeState()

    /**
     * Host-backed when a compatible Sidekit is installed, local otherwise. The launch intent is
     * read here so a dev Sidekit that started the app stays the host.
     */
    protected open val stores: SidekitStores.Stores by lazy { SidekitStores.forHostOrLocal(this, intent) }

    protected open val themeStore: SidekitThemeStore get() = stores.theme

    protected open val keypadPreferences: KeypadPreferences get() = stores.keypad

    /** The swappable keypad. T9 typing and its dictionary only load once T9 is actually used. */
    protected val keypad: Keypad by lazy { Keypad.create(this, lifecycleScope, keypadPreferences) }

    private val input by lazy {
        SidekitInputController(
            keypad = keypad,
            chrome = chrome,
            dispatch = ::dispatchToPlatform,
            onBack = { onBackPressedDispatcher.onBackPressed() },
            onHome = ::onSidekitHome,
            onSearch = ::onSidekitSearch
        )
    }

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onSidekitCreate(savedInstanceState)
        enableEdgeToEdge()
        // Sidekit uses the whole display; the launcher header carries the clock.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        setContent {
            SidekitAppHost(store = themeStore, chrome = chrome, keypad = keypad) {
                SidekitContent()
            }
        }
        // The canvas paints every pixel, so the window background would only be overdraw: one
        // extra full-screen fill per frame on a GPU (PowerVR GE8320) that is fill-rate bound.
        // It stays in the theme so the first frame before Compose draws is black, not garbage.
        window.decorView.post { window.setBackgroundDrawable(null) }
    }

    /** App-specific startup, before the shared content is composed. */
    protected open fun onSidekitCreate(savedInstanceState: Bundle?) {}

    /** The screens of the app. The theme, canvas and keypad are provided by the shell. */
    @Composable
    protected abstract fun SidekitContent()

    /** App-specific resume work; Sidekit applies its input mode here. */
    protected open fun onSidekitResume(keypad: Keypad) {}

    /** A controller's Start/Home key. The host decides where its own home is. */
    protected open fun onSidekitHome() {}

    /** A controller's shoulder/Search key. The host opens its search surface. */
    protected open fun onSidekitSearch() {}

    final override fun onResume() {
        super.onResume()
        onSidekitResume(keypad)
    }

    final override fun onPause() {
        input.reset()
        super.onPause()
    }

    // Overriding dispatchKeyEvent is the only way to see a keypad event before Compose or the
    // system consumes it; lint flags both the override and the super call because ComponentActivity
    // marks them @RestrictTo.
    @SuppressLint("RestrictedApi")
    final override fun dispatchKeyEvent(event: KeyEvent): Boolean = input.handle(event)

    /**
     * The platform path for a key Sidekit did not consume; also used by the Sundial holds.
     *
     * [SidekitInputController.handle] already ends here for an unhandled key, so the override must
     * not dispatch it a second time as well.
     */
    @SuppressLint("RestrictedApi")
    private fun dispatchToPlatform(event: KeyEvent): Boolean = super.dispatchKeyEvent(event)
}
