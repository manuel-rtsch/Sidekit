package hrln.interactive.sidekit

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import hrln.interactive.sidekit.apps.settings.SidekitPreferences
import hrln.interactive.sidekit.apps.sidemessage.IncomingLinks
import hrln.interactive.sidekit.apps.sideport.SideportLinks
import hrln.interactive.sidekit.design.input.Keypad
import hrln.interactive.sidekit.navigation.SidekitNavHost
import hrln.interactive.sidekit.onboarding.OnboardingScreen
import hrln.interactive.sidekit.sdk.SidekitStores
import hrln.interactive.sidekit.sdk.app.SidekitActivity

/**
 * Sidekit itself, on the shared SDK shell.
 *
 * The theme, canvas, keypad and key loop live in [SidekitActivity]; this class keeps only what is
 * Sidekit's own: the home intent, the message/projection links and the screens to show.
 */
class MainActivity : SidekitActivity() {
    /** Bumped when Home is pressed while SideKit is already in front; the nav host returns to the launcher. */
    private val homeRequests = mutableIntStateOf(0)

    /** Bumped to focus the launcher's app search, returning to the launcher first if needed. */
    private val searchRequests = mutableIntStateOf(0)

    /**
     * Sidekit is the host, so it keeps its own writable settings. Reading them back through its own
     * read-only provider would drop every keypad change made in Settings.
     */
    override val stores: SidekitStores.Stores by lazy { SidekitStores.local(this) }

    override fun onSidekitCreate(savedInstanceState: Bundle?) {
        // The launcher saves the last Search request it handled; restore the counter too, or a
        // recreated activity would ignore the next few Search presses. Home needs no restore: its
        // nav host acts on any non-zero count, so restoring it would jump to the launcher.
        savedInstanceState?.let { searchRequests.intValue = it.getInt(STATE_SEARCH_REQUESTS) }
        // Opened from an sms: link or a message notification: go to that conversation.
        if (savedInstanceState == null && !SideportLinks.handle(intent)) IncomingLinks.handle(intent)
    }

    @Composable
    override fun SidekitContent() {
        val prefs = remember { SidekitPreferences(this) }
        var setupComplete by remember { mutableStateOf(prefs.setupComplete) }
        if (setupComplete) {
            SidekitNavHost(homeRequests = homeRequests.intValue, searchRequests = searchRequests.intValue)
        } else {
            OnboardingScreen(onFinish = { setupComplete = true })
        }
    }

    override fun onSidekitResume(keypad: Keypad) {
        SidekitPreferences(this).applyInputDefault(keypad)
    }

    override fun onSidekitHome() {
        homeRequests.intValue++
    }

    override fun onSidekitSearch() {
        homeRequests.intValue++
        searchRequests.intValue++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SEARCH_REQUESTS, searchRequests.intValue)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: pressing Home while an app is open delivers the HOME intent here.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeRequests.intValue++
        } else if (!SideportLinks.handle(intent)) {
            IncomingLinks.handle(intent)
        }
    }

    private companion object {
        const val STATE_SEARCH_REQUESTS = "sidekit.searchRequests"
    }
}
