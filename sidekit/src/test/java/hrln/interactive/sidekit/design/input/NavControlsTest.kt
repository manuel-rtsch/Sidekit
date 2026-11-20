package hrln.interactive.sidekit.design.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1 contract: each module's native keys map to canonical navigation in Sidekit UI. */
class NavControlsTest {

    private fun mapped(module: KeypadModule, code: Int): Int? = NavControls.applyDefault(module, code)

    private fun isDirection(code: Int) = code in NavControls.all.take(4)

    @Test fun t9AndSundialAlreadySendCanonicalKeys() {
        // T9's D-pad / OK are already what focus and select act on: nothing to rewrite.
        for (code in listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ENDCALL
        )) {
            assertNull(mapped(KeypadModule.T9, code))
            assertNull(mapped(KeypadModule.Sundial, code))
        }
    }

    @Test fun gamePadAConfirms() {
        assertEquals(KeyEvent.KEYCODE_ENTER, mapped(KeypadModule.GamePad, KeyEvent.KEYCODE_BUTTON_A))
        // A must become a real confirm key (Compose activates focused controls on it).
        assertEquals(NavControls.Confirm, mapped(KeypadModule.GamePad, KeyEvent.KEYCODE_BUTTON_A))
    }

    @Test fun gamePadBGoesBack() {
        assertEquals(KeyEvent.KEYCODE_BACK, mapped(KeypadModule.GamePad, KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(NavControls.Back, mapped(KeypadModule.GamePad, KeyEvent.KEYCODE_BUTTON_B))
    }

    @Test fun gamePadMenuButtonsOpenOptions() {
        for (code in listOf(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_SELECT)) {
            assertEquals(KeyEvent.KEYCODE_ENDCALL, mapped(KeypadModule.GamePad, code))
            assertEquals(NavControls.Options, mapped(KeypadModule.GamePad, code))
        }
    }

    @Test fun gamePadStartGoesHomeAndShouldersSearch() {
        assertEquals(NavControls.Home, mapped(KeypadModule.GamePad, KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(NavControls.Search, mapped(KeypadModule.GamePad, KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(NavControls.Search, mapped(KeypadModule.GamePad, KeyEvent.KEYCODE_BUTTON_R1))
    }

    @Test fun gamePadLeavesActionBarKeyAndCanonicalKeysAlone() {
        // Y is a direct action-bar jump, handled by SidekitInputController.
        assertNull(mapped(KeypadModule.GamePad, KeyEvent.KEYCODE_BUTTON_Y))
        assertNull(mapped(KeypadModule.GamePad, KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test fun qwertyLetterCrossNavigates() {
        // On-device measurement: T/D/B/J around the printed 5 / GH key are the directional cross.
        assertEquals(NavControls.Up, mapped(KeypadModule.Qwerty, KeyEvent.KEYCODE_T))
        assertEquals(NavControls.Down, mapped(KeypadModule.Qwerty, KeyEvent.KEYCODE_B))
        assertEquals(NavControls.Left, mapped(KeypadModule.Qwerty, KeyEvent.KEYCODE_D))
        assertEquals(NavControls.Right, mapped(KeypadModule.Qwerty, KeyEvent.KEYCODE_J))
        // The centre of that cross is Confirm, like a D-pad's centre.
        assertEquals(NavControls.Confirm, mapped(KeypadModule.Qwerty, KeyEvent.KEYCODE_G))
    }

    @Test fun qwertyModifierKeysAndConfirmStayTyping() {
        // The modifier keys and Enter keep their jobs: only the letter cross navigates.
        for (code in listOf(
            QwertyLayout.SUGGEST,
            QwertyLayout.SYM,
            QwertyLayout.CASE,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_A
        )) {
            assertNull(mapped(KeypadModule.Qwerty, code))
        }
    }

    @Test fun sundialHoldTargetsAreCanonicalNavKeys() {
        NavControls.sundialHoldFor.forEach { (_, target) ->
            assertTrue("unexpected Sundial hold target $target", target in NavControls.all)
        }
        assertEquals(NavControls.Back, NavControls.sundialHoldFor[KeyEvent.KEYCODE_TAB])
        assertEquals(NavControls.Options, NavControls.sundialHoldFor[KeyEvent.KEYCODE_ENTER])
        assertEquals(NavControls.Confirm, NavControls.sundialHoldFor[KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE])
        // Previous/Next are media keys; their holds must not turn them into left/right.
        assertNull(NavControls.sundialHoldFor[KeyEvent.KEYCODE_MEDIA_PREVIOUS])
        assertNull(NavControls.sundialHoldFor[KeyEvent.KEYCODE_MEDIA_NEXT])
        // The arrows must keep moving on press and repeating while held.
        for (arrow in listOf(NavControls.Up, NavControls.Down, NavControls.Left, NavControls.Right)) {
            assertNull("arrow $arrow must not be a hold key", NavControls.sundialHoldFor[arrow])
        }
    }

    @Test fun everyDefaultTargetIsACanonicalNavKey() {
        NavControls.defaultFor.forEach { (module, profile) ->
            profile.forEach { (_, target) ->
                assertTrue("$module maps to non-canonical $target", target in NavControls.all)
            }
        }
    }

    @Test fun everyGamePadDefaultTargetIsACanonicalNavKey() {
        NavControls.defaultFor.getValue(KeypadModule.GamePad).values.forEach {
            assertTrue("unexpected canonical target $it", it in NavControls.all)
        }
    }

    @Test fun everyDefaultNavigationKeyIsARemappableControl() {
        // Custom mapping skips these defaults, so a default key missing from the module's control
        // list could neither be remapped nor keep its navigation role.
        NavControls.defaultFor.forEach { (module, profile) ->
            val controls = KeypadControls.of(module).map { it.keyCode }.toSet()
            profile.keys.forEach { code ->
                assertTrue("$module maps $code but KeypadControls.of() does not list it", code in controls)
            }
        }
    }
}
