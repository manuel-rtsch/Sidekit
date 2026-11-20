package hrln.interactive.sidekit.design.input

import android.view.KeyCharacterMap
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The keypad is told from other input devices by the module device's name. */
class KeypadDetectorTest {

    private fun moduleFor(name: String?, code: Int, deviceId: Int = 3) = KeypadDetector.moduleFor(name, deviceId, code)

    @Test fun everyModuleNameIsAModuleDevice() {
        for (name in listOf(
            "gxa535_media",
            "gxa535_qwerty",
            "gxa535_t9",
            "gxa535_gamepad",
            "gxa535_keyboard"
        )) {
            assertTrue(name, KeypadDetector.isKeypadDevice(name, deviceId = 3))
        }
    }

    @Test fun rejectsThePhonesOwnKeys() {
        // Same gxa535_ prefix, but this device carries the phone's keys, not a swappable module.
        assertFalse(KeypadDetector.isKeypadDevice("gxa535_phone", deviceId = 3))
    }

    @Test fun rejectsUnknownModuleNames() {
        // A new module must be added to the allowlist deliberately.
        assertFalse(KeypadDetector.isKeypadDevice("gxa535_future", deviceId = 3))
    }

    @Test fun rejectsOtherDevices() {
        assertFalse(KeypadDetector.isKeypadDevice("mt63xx-accdet Headset", deviceId = 4))
        assertFalse(KeypadDetector.isKeypadDevice("External keyboard", deviceId = 7))
    }

    @Test fun allowsVirtualAndUnnamedEvents() {
        assertTrue(KeypadDetector.isKeypadDevice("Injected", KeyCharacterMap.VIRTUAL_KEYBOARD))
        assertTrue(KeypadDetector.isKeypadDevice(null, deviceId = 3))
    }

    @Test fun aModuleThatNamesItselfIsReadFromTheName() {
        assertEquals(KeypadModule.T9, KeypadDetector.moduleNamedBy("gxa535_t9"))
        assertEquals(KeypadModule.Qwerty, KeypadDetector.moduleNamedBy("gxa535_qwerty"))
        assertEquals(KeypadModule.Sundial, KeypadDetector.moduleNamedBy("gxa535_media"))
        assertEquals(KeypadModule.GamePad, KeypadDetector.moduleNamedBy("gxa535_gamepad"))
        // The one name older firmware used for every module says nothing on its own.
        assertNull(KeypadDetector.moduleNamedBy("gxa535_keyboard"))
        assertNull(KeypadDetector.moduleNamedBy("gxa535_phone"))
        assertNull(KeypadDetector.moduleNamedBy(null))
    }

    @Test fun theSharedKeysIdentifyTheKeypadThatSentThem() {
        // The D-pad and Enter are on every module, so only the device name can answer for them.
        assertEquals(KeypadModule.GamePad, moduleFor("gxa535_gamepad", KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(KeypadModule.GamePad, moduleFor("gxa535_gamepad", KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(KeypadModule.Qwerty, moduleFor("gxa535_qwerty", KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(KeypadModule.Sundial, moduleFor("gxa535_media", KeyEvent.KEYCODE_ENTER))
        assertEquals(KeypadModule.T9, moduleFor("gxa535_t9", KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test fun thePhonesOwnDigitsMeanT9() {
        // On this firmware the T9 slides are wired to the phone's key device, so its digits are the
        // only thing that can identify them - and no other module types a digit.
        assertEquals(KeypadModule.T9, moduleFor("gxa535_phone", KeyEvent.KEYCODE_5))
        assertEquals(KeypadModule.T9, moduleFor("gxa535_phone", KeyEvent.KEYCODE_0))
        assertEquals(KeypadModule.T9, moduleFor("gxa535_phone", KeyEvent.KEYCODE_STAR))
        assertEquals(KeypadModule.T9, moduleFor("gxa535_phone", KeyEvent.KEYCODE_POUND))
    }

    @Test fun thePhonesSharedKeysStaySilent() {
        // Everything else that device sends - Back, Home, call, the D-pad - is on every module.
        for (code in listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_CALL,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_DEL
        )) {
            assertNull("key code $code", moduleFor("gxa535_phone", code))
        }
    }

    @Test fun olderFirmwareIsReadFromTheKeys() {
        // `gxa535_keyboard` named every module, so it falls back to the keys only that one has.
        assertEquals(KeypadModule.T9, moduleFor("gxa535_keyboard", KeyEvent.KEYCODE_5))
        assertEquals(KeypadModule.Qwerty, moduleFor("gxa535_keyboard", KeyEvent.KEYCODE_Q))
        assertEquals(KeypadModule.Qwerty, moduleFor("gxa535_keyboard", KeyEvent.KEYCODE_SPACE))
        assertEquals(KeypadModule.Sundial, moduleFor("gxa535_keyboard", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertEquals(KeypadModule.GamePad, moduleFor("gxa535_keyboard", KeyEvent.KEYCODE_BUTTON_X))
        assertNull(moduleFor("gxa535_keyboard", KeyEvent.KEYCODE_DPAD_LEFT))
    }

    @Test fun otherDevicesRevealNothing() {
        // A headset sends Play/Pause and a paired keyboard sends digits; neither is a module.
        assertNull(moduleFor("mt63xx-accdet Headset", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertNull(moduleFor("External keyboard", KeyEvent.KEYCODE_5))
        assertNull(moduleFor("External keyboard", KeyEvent.KEYCODE_Q))
    }
}
