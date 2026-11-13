package hrln.interactive.sidekit.sdk.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyCharacterMap
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
import hrln.interactive.sidekit.design.input.HoldToAct
import hrln.interactive.sidekit.design.input.Keypad
import hrln.interactive.sidekit.design.input.KeypadModule
import hrln.interactive.sidekit.design.input.KeypadPreferences
import hrln.interactive.sidekit.design.input.NavControls
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
        SidekitInputController(keypad, chrome, onHome = ::onSidekitHome, onSearch = ::onSidekitSearch)
    }

    /** No dedicated Back/Options on the Sundial: hold ring keys for the same actions. */
    private val sundialHold = HoldToAct(onHold = { action, source -> performSundialHold(action, source) })

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

    override fun onPause() {
        input.reset()
        sundialHold.reset()
        super.onPause()
    }

    // Overriding dispatchKeyEvent is the only way to see a keypad event before Compose or the
    // system consumes it; lint flags the override because ComponentActivity marks it @RestrictTo.
    @SuppressLint("RestrictedApi")
    final override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val translated = input.translate(event) ?: return true
        // Holds belong to the Sundial ring only. Tab/Enter on a Bluetooth keyboard or Next on a
        // headset keep their normal behaviour, as does a T9 OK if the remembered module is stale.
        val fromSundial = event.device?.name in SUNDIAL_DEVICES ||
            event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD
        val holdModule = keypad.module == KeypadModule.Sundial && keypad.mapper.customModule == null
        when (val hold = sundialHold.handle(translated) { code -> if (fromSundial && holdModule) NavControls.sundialHoldFor[code] else null }) {
            HoldToAct.Result.Pass -> Unit

            HoldToAct.Result.Consume -> return true

            is HoldToAct.Result.Deliver -> {
                hold.events.forEach(::deliver)
                return true
            }
        }
        return deliver(translated)
    }

    private fun deliver(event: KeyEvent): Boolean {
        val handled = input.handleTranslated(event) ?: return true
        return super.dispatchKeyEvent(handled)
    }

    private fun performSundialHold(action: Int, source: KeyEvent) {
        when (action) {
            NavControls.Back -> onBackPressedDispatcher.onBackPressed()

            NavControls.Options -> {
                keypad.finishTyping()
                chrome.toggleOptions()
            }

            NavControls.Home -> onSidekitHome()

            NavControls.Search -> onSidekitSearch()

            else -> dispatchNav(action, source)
        }
    }

    private fun dispatchNav(code: Int, from: KeyEvent) {
        val down = KeyEvent(
            from.downTime, from.eventTime, KeyEvent.ACTION_DOWN, code, 0,
            from.metaState, from.deviceId, from.scanCode, from.flags, from.source
        )
        val up = KeyEvent(
            from.downTime, from.eventTime + 1, KeyEvent.ACTION_UP, code, 0,
            from.metaState, from.deviceId, from.scanCode, from.flags, from.source
        )
        deliver(down)
        deliver(up)
    }

    private companion object {
        /** Input devices that carry the Sundial ring (newer media module, older combined keyboard). */
        val SUNDIAL_DEVICES = setOf("gxa535_media", "gxa535_keyboard")
    }
}
