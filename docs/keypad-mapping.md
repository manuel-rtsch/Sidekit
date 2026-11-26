# Keypad mapping

Settings → Keypad mapping lets you use a keypad that Sidekit doesn't recognise, or move Sidephone keys. It is off by default: Sidekit receives keys as they arrive and detects the module from them, as before.

## Custom mapping

Tap **Mapping** to switch from Automatic to Custom. Custom mapping uses the module that is currently detected, and **Layout** chooses a different one: T9, Mini QWERTY, Sundial or Game controller. While Custom is on, detection is paused, so a key can't switch the module.

Each module has its own list of controls, which you remap separately. The list includes Back, Options, the D-pad and every typing key. Choose a control, then press the button you want to use for it. Press the button a second time to save it. Keys you haven't changed keep their default role, so a partial mapping doesn't disable working keys.

A button can have only one role. If you choose a button that already has a role, Sidekit shows the old role and removes it when you save. That control then returns to its default key. To move the Back or Options button, tap **Save**. Pressing Back or Options again doesn't save it, so trying to leave doesn't remap it by accident.

While Sidekit waits for a key, it captures every press, including the release, so a key you press doesn't trigger anything when the screen closes. You can leave the wait in these ways:

- Tap **Cancel**.
- Press the Sidephone's top-left key. With the keypad accessibility service on, it sends the system Back.
- Wait 10 seconds.

**Reset keys**, in Options or at the bottom of the list, clears the module's mapping. **Use automatic** in Options turns custom mapping off. Touch always works on these screens.

## Key tester

The key tester shows the key code and scan code for each press, the input device that sent it, and the control it becomes. Use it to set up a new keypad or to report how a device's keys behave.

Sidekit only treats the keypad modules' own input devices as keypads, and reads the module from the device name when the name carries one. A headset button or an external keyboard is ignored for module detection, even when it sends the same key code. The exception is the phone's own key device, which carries the T9 slides on some firmware: its digits identify T9, because no other module types one, while the keys it shares with the others say nothing.

## Sundial holds

The Sundial has no Back or Options key. Hold **Tab** for Back; hold **Enter** for the options when the screen has any, and for the main action bar when it has none, so a held Enter always lands focus in some bar. Hold the **centre Play/Pause** key for Confirm when a revision reports Enter as the centre media key; a tap still controls media. The **two upper corners** are the module's spare holds: holding **←** opens **Search** and holding **→** opens **Playing Now**. Each corner's hold outranks its tap: the press is held until the release or the long-press timeout, so a held corner opens the screen instead of rattling the focus left or right; a short tap still moves left or right once. The **Previous** / **Next** edges and **Up** / **Down** keep their printed roles both on press and when held. Keypad holds follow Android's long-press timeout (`ViewConfiguration.getLongPressTimeout()`, 400 ms by default), so a device or accessibility tool that changes the system value also changes them. The Back-to-dialer hold stays at 600 ms.

## Delete / SYM and OK holds

T9 and the mini QWERTY have no Search or Playing Now key, and their Home key cannot stand in: the
keypad sends `KEY_HOMEPAGE`, which the system turns into Home and consumes before any app sees it.
Two keys per module are free — every other key either types (and its hold already types the printed
digit or symbol) or moves with key repeat:

- **Hold Delete** (T9) or **hold SYM** (mini QWERTY) **for Search.** A tap still erases, or steps
  through the symbols.
- **Hold OK** (T9) or **hold Enter** (mini QWERTY) **for Playing Now.** A tap still selects, takes a
  suggestion or inserts a new line.

Both holds stand down while a text field owns the keypad, so OK and Enter keep their typing role —
and the field's own hold on them, which finishes the word and leaves. A custom mapping replaces
them like every other default. A Bluetooth keyboard's Home, Enter and Tab keep their normal
behaviour: holds only apply to the keypad's own devices.

## Game controller

The controller's D-pad moves focus, **A** confirms, **B** goes back, **X** opens Options and focuses the first option (hold for the action bar), **Start** goes home, the small top-right key opens Search and **Y** opens Playing Now. On the 5x3 gamepad tile that top-right key reports as **Select** (`BTN_SELECT`) rather than as a shoulder; `L1` / `R1` stay mapped to Search for controller revisions that really carry them.

## Scope

- Mappings are saved for each input device, by device name. A key from another device isn't remapped, even if it sends the same code. Examples are an external keyboard and a headset button. Device names, unlike device ids, stay the same across restarts and reconnections.
- Keys without an Android key code are recognised by their scan code.
- Remapping applies in Sidekit and in Sideport projections. Other apps receive the keys unchanged.
- The Sidephone's top-left key (CALL) becomes Back through the keypad accessibility service, before Sidekit receives it. That key can't be remapped. You can make another button act as Back.

## Verification

`./gradlew :app:testDebugUnitTest --tests '*KeypadMapping*' --tests '*KeypadKeysTest' --tests '*SettingsUiTest' :app:assembleDebug`

The screenshots in `app/screenshots/keypad_mapping_*.png` are Compose and Roborazzi captures at the Sidephone canvas size. They aren't photos of the hardware.
