package hrln.interactive.sidekit.ui

import android.view.KeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import hrln.interactive.sidekit.MainActivity
import hrln.interactive.sidekit.sdk.input.SharedPreferencesKeypadPreferences
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The keypad's top keys: top-right (ENDCALL) is Options, top-left (CALL) is Back. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class KeypadKeysTest {
    @get:Rule(order = 0)
    val completedSetup = object : org.junit.rules.ExternalResource() {
        override fun before() {
            val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            hrln.interactive.sidekit.apps.settings.SidekitPreferences(context).apply {
                setupComplete = true
                // Earlier tests may persist launcher state; these assertions use grid tiles.
                listLayout = false
                hiddenApps = emptySet()
            }
            hrln.interactive.sidekit.home.HomePreferences(context).reset()
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun press(code: Int): Boolean {
        var handled = false
        compose.runOnIdle {
            handled = compose.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)) and
                compose.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        compose.waitForIdle()
        return handled
    }

    @Test fun holdingOptionsFocusesTheFirstAction() {
        compose.runOnIdle {
            compose.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENDCALL))
        }
        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        looper.idleFor(java.time.Duration.ofMillis(1_000))
        looper.idle()
        compose.runOnIdle {
            compose.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENDCALL))
        }
        compose.waitForIdle()

        // The dock is the launcher's action bar; the app tile shares the label, so take the last.
        compose.onAllNodes(hasContentDescription("Sidemessage")).onLast().assertIsFocused()
        compose.onNodeWithContentDescription("Close options").assertDoesNotExist()
    }

    @Test fun optionsKeyShowsTheScreensOptionsAndBackClosesThem() {
        assertTrue(press(KeyEvent.KEYCODE_ENDCALL))
        compose.onNodeWithContentDescription("Switch grid or list").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close options").assertIsDisplayed()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("Close options").assertDoesNotExist()
        compose.onNodeWithText("List").assertDoesNotExist()
    }

    @Test fun choosingAnOptionRunsItAndClosesTheBar() {
        press(KeyEvent.KEYCODE_ENDCALL)
        compose.onNodeWithContentDescription("Switch grid or list").performClick()
        compose.onNodeWithContentDescription("Close options").assertDoesNotExist()
        // The launcher switched to its list layout.
        compose.onNodeWithText("Sidemessage").assertIsDisplayed()
    }

    @Test fun optionsKeyAgainClosesTheBar() {
        press(KeyEvent.KEYCODE_ENDCALL)
        press(KeyEvent.KEYCODE_ENDCALL)
        compose.onNodeWithContentDescription("Close options").assertDoesNotExist()
    }

    @Test fun screensWithoutOptionsSaySo() {
        // The dial pad has no options until a number is entered.
        val phonebook = hasContentDescription("Phonebook") or hasText("Phonebook")
        compose.waitUntilAtLeastOneExists(phonebook, 15_000)
        compose.onAllNodes(phonebook).onLast().performClick()
        compose.onNodeWithContentDescription("Dial pad").performClick()
        press(KeyEvent.KEYCODE_MENU)
        compose.onNodeWithText("No options here").assertIsDisplayed()
    }

    @Test fun remappedKeysActAsTheControlsTheyWereLearnedFor() {
        // A keypad Sidekit doesn't know: its F1 is learned as Options. Set up before the
        // activity reads it, as after a restart.
        compose.runOnIdle {
            val keypad = hrln.interactive.sidekit.design.input.Keypad.forTesting(
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
                hrln.interactive.sidekit.design.input.T9Words.Empty,
                mapper = hrln.interactive.sidekit.design.input.KeyMapper.create(SharedPreferencesKeypadPreferences(compose.activity, "sidekit_keypad"))
            )
            keypad.useCustomMapping(hrln.interactive.sidekit.design.input.KeypadModule.T9)
            keypad.mapper.bind(
                hrln.interactive.sidekit.design.input.KeypadModule.T9,
                KeyEvent.KEYCODE_ENDCALL,
                // Test events come from the virtual keyboard device.
                hrln.interactive.sidekit.design.input.PhysicalKey("Virtual", KeyEvent.KEYCODE_F1)
            )
        }
        assertTrue(press(KeyEvent.KEYCODE_F1))
        compose.onNodeWithContentDescription("Close options").assertIsDisplayed()
        compose.runOnIdle {
            compose.activity.getSharedPreferences("sidekit_keypad", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun theCallKeyNeverReachesTheSystemFromSidekit() {
        assertTrue(press(KeyEvent.KEYCODE_CALL))
    }
}
