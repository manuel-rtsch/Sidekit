# Changelog

## Unreleased

- **T9 and the mini QWERTY reach Search and Playing Now from their two free holds.** Neither module
  has a key for either screen, and **hold OK (T9) / Enter (mini QWERTY) opens Playing Now** while
  **hold Delete (T9) / SYM (mini QWERTY) opens Search**. Their taps keep their printed role — OK /
  Enter select, take a suggestion or insert a new line, Delete erases, SYM steps through symbols —
  both holds stand down while a text field owns the keypad, and both follow the rules of every other
  default: a custom mapping replaces them and they only apply to the keypad's own input devices.
  Home cannot carry a hold: the keypad sends `KEY_HOMEPAGE`, which the system turns into
  `KEYCODE_HOME` and consumes before any app sees it. `NavControls.holdFor` is now the per-module
  hold table (the Sundial's ring keeps its entries; the controller has none because Y and Select
  already act on a single press), `Keypad.holdAction` resolves a press against the module, the custom
  mapping, a focused field and the device, and the input controller performs every module's hold in
  one place. The keypad guide and the mapping hints say the same thing.
- **A hold that belonged to typing can no longer open Playing Now on its release.** Holding OK in a
  text field is the field's own hold: it finishes the word and leaves. That cleared the keypad's
  session, so by the time the key came up the same key looked like a hold nobody had made, and
  Playing Now opened as the finger lifted. `HoldToAct` now remembers that a press was passed to
  typing and passes its release with it.
- **The Options key puts the cursor on the bar it opens.** A tap swapped the bar for the screen's
  options but left focus in the content, so a keypad user had to walk down before they could move
  between the options. The keypad loop now asks for `toggleOptions(focusBar = true)`, the first
  option takes focus after the buttons are composed, and Enter activates it. The request waits for
  the bar host's next composition, so a screen whose bar was empty still lands on the first option;
  a touch press keeps the old behaviour because there is no cursor to place. `toggleOptions()`
  defaults to no focus, so apps that open options from a tap are unchanged. The Sundial's hold on
  Enter and the controller's X key use the same path.
- **The game controller's top-right key opens Search again.** The SDK mapped Search onto
  `BUTTON_L1`/`BUTTON_R1`, but the Sidephone's 5x3 gamepad tile has no shoulder: its small
  top-right key arrives as `BUTTON_SELECT` (measured with `getevent`: `BTN_SELECT` top-right,
  `BTN_START` bottom-left), and Select was bound to Options — the same action as X. Search was
  therefore unreachable from the tile while Options had two keys. Select now means Search, X keeps
  Options (and its hold), Start stays Home, and the shoulder codes remain mapped for controllers
  that actually have them. The keypad mapping list and guide now say the same thing.
- **A game controller's Y key opens Playing Now.** Y used to jump to the action bar; that is now
  what the Options hold does (hold X), which every module has. `NavControls.PlayingNow`
  is a new canonical chrome key (`KEYCODE_MUSIC`), `SidekitInputController` takes an
  `onPlayingNow` callback, and `SidekitActivity` grew a matching `onSidekitPlayingNow()` hook so a
  host can open its now-playing screen. The Sundial keeps its printed roles on press and on hold,
  and has no spare hold, so it is the one module with no Playing Now key.
- **The key guide is the module in your hand.** Each tile draws what its cap prints — a glyph on
  CALL, END CALL, Home, Delete, Enter, Search, the media keys and the D-pad, the shell's **Y / X /
  B / A** on the controller's face buttons, a dash on the two blank caps beside Home — while the
  translated name moves to the detail card and the screen reader. `KeypadGuideKey` grew a
  `printed` field (the legend on the cap, null when the cap has no text), so hosts no longer have
  to re-derive the capsule layout. The mini QWERTY guide now folds its top and bottom rows into
  the five keys the module really has (call, Back, Home, Recents, END CALL; rocker, SYM, Space,
  aA, Enter) and the game controller keeps its five-column shell. `KeypadGuide(module, modifier)`
  takes a modifier and lets its card grow to fit a long tap or hold sentence instead of clipping
  it at two lines, so a host does not need its own copy of the diagram — Sidetrack's was deleted.
  Five Material Symbols Rounded drawables (`arrow_downward`, `backspace`, `call_end`, `home`,
  `keyboard_return`) join `SidekitIcon`, cut from the upstream Symbols set in the same
  negative-coordinate form as the rest of the icon set.

## v1.13.0 — 2026-11-24

- **`ChromeState.focusActionBar(key)` can name the button it wants.** Until now only the first
  visible button could be asked for, so an app that wanted the cursor on the close square had to
  focus the bar and then move right a frame later — a double jump that could land while the bar was
  still morphing. Every button now carries its own `FocusRequester`, and the call resolves the key
  the button is showing, so one request is enough. `focusActionBar()` still focuses the first slot.
- **`ChromeState.saveContentFocus()` and `restoreContentFocus()` are public.** An app can remember
  the element the user was on before a transient bar takes the cursor, and put it back when the bar
  goes away, instead of leaving the keypad on a bar that is no longer showing.
- **The key-cap card grows with its sentences.** The card that shows one key's tap and long press
  was pinned to 112 dp, so a key whose sentences wrapped lost the second line; it now keeps 112 dp
  as a floor and grows, and takes its text colours from the tile surface it paints rather than the
  page beneath it.

SDK artifacts for this tag are published to the GitHub Pages Maven repo (`sidekit-design` and
`sidekit-sdk`, version `1.13.0`).

## v1.12.0 — 2026-11-23

- **The compact header is one title line again.** It keeps a 4 dp top inset above the 61 dp line
  (65 dp total) instead of the 14 dp gap, so the body starts 10 dp higher while the title stays off
  the top edge.
- **The D-pad focus band follows the measured row, not the 104 dp token.** A list with its own row
  height (Sidetrack's 100 dp and 80 dp rows) no longer keeps a sliver of the previous row under the
  title when focus scrolls; the band measures the focused item plus the gap.
- **Build:** AGP 9.5.2 and Gradle 9.9.2, with the matching wrapper checksum.

SDK artifacts for this tag are published to the GitHub Pages Maven repo (`sidekit-design` and
`sidekit-sdk`, version `1.12.0`).

## v1.11.0 — 2026-11-21

- **The touch Options button takes space in the top band instead of floating over it.** The three
  dots are drawn above the screens, and until now only a published title reserved room for them: a
  custom `SidekitScaffold` header (a search field, the launcher's clock) and any body that starts
  in that band ran underneath the button, so a long query slid under the dots. `SidekitScaffold`
  now keeps its `header` slot clear of the corner, and the reservation is published as
  `LocalHeaderOptionsInset` so body content drawn in the band can opt in. In keypad mode the button
  is still hidden — and now reserves nothing at all, so the band keeps its full width.
- **The keypad typing bar no longer takes over while the on-screen keyboard is up.** The ABC /
  Done strip belongs to a keypad that types; when the IME is the input, the screen keeps its own
  (or empty) action bar instead of publishing the keypad's strip over it.
- **Sidekit speaks German, Spanish and French, and the language is a setting.** Every string the
  app and the design library show now lives in `res/values/strings_<area>.xml` (and its
  `values-de` / `values-es` / `values-fr` translations), and Settings → Language picks the app's
  language independently of the phone's. Code that runs without a Context — the Options bar, the
  keypad guide — reads the same resources. This is `upstream/main` (PR #59) merged in. Where the
  fork's own work added text to a shared surface (the touch Options button, the case colour Cloud,
  the keypad's Home key and controller shoulders, the App names) it went into the same files in all
  four languages; fork-only screens that are still English-until-translated are the Emulator and a
  few of the newer side apps, which keep their literals for now.

SDK artifacts for this tag are published to the GitHub Pages Maven repo (`sidekit-design` and
`sidekit-sdk`, version `1.11.0`).

## v1.10.1 — 2026-11-21

- **A one-line text field can be left with the keypad again.** D-pad Up / Down now move focus
  instead of the caret on `singleLine` fields: the text field used to eat Up to move the caret to
  the line start, so with a T9 / mini QWERTY attached (and the field auto-focused) there was no way
  out. Multi-line fields keep vertical caret movement, and modified arrows are untouched.
- **The mini QWERTY's suggestion rocker is an exit.** While a word is composing a tap still cycles
  completions; with nothing to complete it leaves the field, and holding it finishes the word and
  leaves. The module's T / D / B / J cross has to type letters while a field owns the keypad, so
  the rocker was the only direction key left.

## v1.10.0 — 2026-11-21

- **`SidekitTextField` takes a keyboard type, an IME action, an autofill hint and a `Next` target.**
  A form can now open the URI or password keyboard, hand focus to the next field without leaving
  it (the action bar and Enter both honour it), and let a password manager fill a field.
- **`SidekitTextField(masked = true)` is a secret field.** It renders dots, marks itself as a
  password to the platform (so Android keeps the text out of accessibility nodes), and hides T9
  suggestions, the composing highlight and dictation. Masked fields are single-line.

## v1.9.3 — 2026-11-20

- **Keypad detection reads the device name.** Each module names itself as its own input device, so a press identifies the attached keypad on its first key - the D-pad included - instead of only on a button unique to one module. Older firmware, which used a single name for every module, still falls back to the keys.
- **T9 is recognised from the phone's key device.** On firmware where the T9 slides report as `gxa535_phone`, its digits identify T9 - no other module types one - while the D-pad, call and Home keys it shares stay silent. T9 used to be the one module that could not be detected at all.
- **`Keypad.observeAttached()`** applies the attached module at launch, so an app no longer has to wait for a key press before it knows what is plugged in.
- **`Keypad.chooseModule()`** applies a module chosen in a UI (a settings row, an onboarding cycle). Detection keeps running but leaves it alone while the same keypad reports itself, so pressing the D-pad afterwards does not undo the choice; a different module replaces it.

SDK artifacts for this tag are published to the GitHub Pages Maven repo (`sidekit-design` and `sidekit-sdk`, version `1.9.3`).

## v1.9.2 — 2026-11-20

- **Sundial:** holding the Previous/Next edges no longer turns them into left/right. Their taps stay media commands, and the corner arrows already provide left/right. The keypad guide and the keypad-mapping docs match.

SDK artifacts for this tag are published to the GitHub Pages Maven repo (`sidekit-design` and `sidekit-sdk`, version `1.9.2`).

## v1.9.1 — 2026-11-20

Fixes wrapped (two-line) action-button labels so they no longer fill the button edge to edge.

- **Action bar:** a label that wraps to a second row now steps down one type notch (body → label, label → caption) and keeps the tighter 1.15 leading, whole-word breaks and word-boundary cutting. A single-line label keeps its own size. This stops wrapped labels from overflowing the button's rounded border.

SDK artifacts for this tag are published to the GitHub Pages Maven repo (`sidekit-design` and `sidekit-sdk`, version `1.9.1`).

## v1.9.0 — 2026-11-20

Sidekit 1.9.0 publishes `sidekit-design` and `sidekit-sdk` 1.9.0 to the GitHub Pages Maven repository. Sidetrack builds against these coordinates.

**Highlights since 1.8.0**

- **Keypad:** the mini QWERTY's centre **5 / GH** key now confirms, completing the T / D / B / J navigation cross. The game controller's **Select** and **R1** buttons are back in the keypad-mapping list, and T9's **Home** soft key can be remapped.
- **Keypad guide and navigation:** shared Home and Search actions, Sundial holds for Confirm and Options, and the keypad guide available in the SDK and onboarding.
- **Sideport:** installed apps get their own page, app options survive the integration, and install progress stays on screen.
- **UI patterns:** action-bar consolidation, visible-time Undo, a shared notice channel, and consistent destructive and commit tones.
- **Header:** in touch mode the Options button now sits level with the title, its touch target grows to 56 dp without enlarging the icon, and it becomes a cross while the bar is open.
- **Build:** Kotlin 2.4.21, Gradle 9.7.0 and AndroidX updates.

**Maven**

```kotlin
repositories {
    google()
    mavenCentral()
    maven("https://manuel-rtsch.github.io/Sidekit/maven")
}

dependencies {
    implementation("hrln.interactive.sidekit:sidekit-sdk:1.9.0")
}
```

**Full changelog**: https://github.com/manuel-rtsch/Sidekit/compare/v1.8.0...v1.9.0

## v1.8.0 — 2026-11-18

Changes since [v1.7.2](https://github.com/manuel-rtsch/Sidekit/releases/tag/v1.7.2).

### New features

- **Touch-mode Options button:** the header now shows an Options button when the device is in touch mode, so Options can be reached without the keypad. The button carries its own label while options are open, rather than borrowing the action bar's "Close options", which collided with the action bar's own close button and left screen readers — and the action-bar tests — ambiguous about which button they meant.
- **`playlist_add` icon:** `SidekitIcon.PlaylistAdd` is available to apps built against the SDK.

### Fixes

- **Options hold from touch:** holding Options and then continuing with keys now works. A clickable action button is `Focusability.SystemDefined`, so it cannot take focus while Compose still thinks the last input was touch, and the first key after a touch is swallowed by Android's touch-mode transition. The action bar host now registers the focus move, requests keyboard input mode (which leaves touch mode) and then focuses the first button. A hold whose DOWN was lost is recovered from ACTION_UP, and the canvas focus fallback is posted so the hold lands first.

### For developers

- **Published artifacts:** `hrln.interactive.sidekit:sidekit-design:1.8.0` and `hrln.interactive.sidekit:sidekit-sdk:1.8.0` are on the Maven repository at <https://manuel-rtsch.github.io/Sidekit/maven>. This is the last release before the `upstream/main` (PR #58) merge, so apps pinning the pre-merge SDK should use `1.8.0`.

[Full changelog: v1.7.2...v1.8.0](https://github.com/manuel-rtsch/Sidekit/compare/v1.7.2...v1.8.0)

## v1.7.3 — 2026-11-18

Changes since [v1.7.2](https://github.com/manuel-rtsch/Sidekit/releases/tag/v1.7.2).

### New features

- **Touch-mode Options button:** the header shows an Options button when the device is in touch mode, so Options can be reached without the keypad. This is the first cut of the button; v1.8.0 gives it its own label and fixes the focus path for a hold that continues with keys.

### For developers

- **Published artifacts:** `sidekit-design` and `sidekit-sdk` `1.7.3` are on the Maven repository at <https://manuel-rtsch.github.io/Sidekit/maven>.
- **Tag note:** `v1.7.3` was cut from a pre-rebase branch (`a73addf`), so the tag is not an ancestor
  of `main`; the same change landed on `main` as `c57ec95` and shipped again in v1.8.0. See
  [Tagging](docs/ci.md#tagging) — the tag is deliberately left where it is.

## v1.7.2 — 2026-11-17

Changes since [v1.7.1](https://github.com/manuel-rtsch/Sidekit/releases/tag/v1.7.1).

### New features

- **`swap_vert` icon:** `SidekitIcon.SwapVert` is available to apps built against the SDK.

### For developers

- **Published artifacts:** `sidekit-design` and `sidekit-sdk` `1.7.2` are on the Maven repository at <https://manuel-rtsch.github.io/Sidekit/maven>.

## v1.7.1 — 2026-11-16

Changes since [v1.7.0](https://github.com/manuel-rtsch/Sidekit/releases/tag/v1.7.0).

### Fixes

- **Artwork cache misses no longer look like failures:** `clear()` wipes the cache directory on sign-out and `trim()` evicts entries after every write, so a file can disappear between the lookup and the decode. Handing that missing path to `BitmapFactory` logged `Unable to decode stream: … ENOENT` for what is an ordinary cache miss, which reads like broken artwork in logcat. A miss is not an error and is no longer reported as one.

### For developers

- **Published artifacts:** `sidekit-design` and `sidekit-sdk` `1.7.1` are on the Maven repository at <https://manuel-rtsch.github.io/Sidekit/maven>.

## v1.7.0 — 2026-11-15

Changes since [v1.6.0](https://github.com/manuel-rtsch/Sidekit/releases/tag/v1.6.0).

### New features

- **Action-bar zones:** a bar now has an explicit trailing zone instead of an `alignEnd` flag. All-square bars put the Options close button and the screen's trailing actions on the right edge, while leading-only bars keep their existing left alignment.

### For developers

- **Breaking API change:** `SidekitScaffold`, `PublishChrome` and `PublishActionOverride` take `trailingActions: List<SideAction>` instead of a single `trailingAction`. Apps pinning an earlier SDK need a one-line change when they move to 1.7.0.
- **Published artifacts:** `hrln.interactive.sidekit:sidekit-design:1.7.0` and `hrln.interactive.sidekit:sidekit-sdk:1.7.0` are on the Maven repository at <https://manuel-rtsch.github.io/Sidekit/maven>.

## v1.6.0 — 2026-11-15

### Changes since v1.5.1

- **Case colours refreshed**: a new **Cloud** swatch, and the swatches are regrouped into visual pairs with the two blues together. The grid is now eight choices with the original blue in the first row, and the final cell stays empty. Settings, onboarding and the SDK now share one picker implementation, so the chooser looks the same everywhere.
- **Toolchain updates**: Kotlin 2.2.10 → 2.4.21, Gradle 9.5.0 → 9.7.0, activity-compose 1.13.0, navigation-compose 2.10.2, lifecycle 2.11.0, coroutines 1.11.0, Robolectric 4.18 and Roborazzi 1.79.0. The Android Gradle plugin stays at 9.3.3 on purpose, because AGP 9.4+ would move Gradle outside the range Kotlin tests against.

### Install

Download `Sidekit-1.6.0.apk` below and install it over an earlier Sidekit release. Android 12 or later is required.

The APK is signed with the Manuel Sidekit release key (`CN=Sidekit, O=manuel-rtsch, OU=Release, C=DE`), SHA-256 certificate fingerprint `c7834fd5a5204d2f334a77aab56506b317afa4001b96d8e71b0321c5a0f8cd03`.

`SHA256SUMS.txt` contains the APK checksum.

### SDK

`hrln.interactive.sidekit:sidekit-sdk:1.6.0` and `sidekit-design:1.6.0` are published to the GitHub Pages Maven repository, alongside the older versions:

```
https://manuel-rtsch.github.io/Sidekit/maven
```

Sideplay 0.2.9 builds against these artifacts.

**Full changelog:** https://github.com/manuel-rtsch/Sidekit/compare/v1.5.1...v1.6.0

## v1.5.1 — 2026-11-14

### Changes since v1.5.0

- **Voice dictation restored** in `SidekitTextField`.
- **Keypad hold hints** now show on the keypad mapping and guide pages.

### Install

Download `Sidekit-1.5.1.apk` below and install it over an earlier Sidekit release. Android 12 or later is required.

The APK is signed with the Manuel Sidekit release key (`CN=Sidekit, O=manuel-rtsch, OU=Release, C=DE`), SHA-256 certificate fingerprint `c7834fd5a5204d2f334a77aab56506b317afa4001b96d8e71b0321c5a0f8cd03`.

`SHA256SUMS.txt` contains the APK checksum.

**Full changelog:** https://github.com/manuel-rtsch/Sidekit/compare/v1.5.0...v1.5.1

## v1.5.0 — 2026-11-14

Sidekit 1.5.0 consolidates the app and SDK work onto one release line and includes the fixes from the 1.4.2–1.4.5 pre-releases.

### SDK

- Adds generic **Home** and **Search** actions to the SDK activity shell. A controller's Start goes home and either shoulder opens search; on the Sundial, hold Down for Home and hold Up for Search.
- Moves the shared keypad guide into `sidekit-sdk`. The guide is now interactive: select a key to read its tap and hold actions, and switch modules without leaving the page.
- Adds Action Bar option toggles that can stay open after being pressed (`keepOptionsOpen`). Sideplay's Queue uses it for the Shuffle toggle.
- Holding the Options key focuses the first action-bar button.
- Includes the 1.4.2–1.4.5 release fixes: certificate-based config access, trust for Sideplay's release certificate, host permission ownership, Pages publishing and the outlined artist icon.
- Publishes `hrln.interactive.sidekit:sidekit-design:1.5.0` and `hrln.interactive.sidekit:sidekit-sdk:1.5.0` to GitHub Pages.

### App

- Settings > Keyboard is now a short list of module, typing, Back key and mapping rows; the key guide has its own page.
- The onboarding keypad tour uses the same interactive guide, with a compact Use action.
- Home and Search work from the controller and Sundial holds.
- Includes the installed-app page and the action-bar, notice and undo work from the release line.
- `versionName 1.5.0`, `versionCode 346`.
- Signed with the same release key as 1.4.5, so it upgrades in place.

### Notes

Maven repository: `https://manuel-rtsch.github.io/Sidekit/maven`

The attached `Sidekit-1.5.0.apk` is the release-signed app build.

## v1.4.5 — 2026-11-13

Sidekit 1.4.5 fixes the install-order problem for trusted third-party apps.

### SDK

- The configuration provider now gates `call` on the caller's signing certificate alone.
- The `normal` `READ_APP_CONFIG` permission remains declared and requested as the client's intent, but it is no longer part of the access decision. Android does not enforce `readPermission` for provider calls, and a client installed before Sidekit defined the permission could stay permanently ungranted.
- Security is unchanged: certificate trust still decides access. Apps without a trusted signing certificate are refused with `HostStatus.NotPermitted`.

### App

- Added `Sidekit-1.4.5.apk` for app installations and updates.
- `versionName 1.4.5`, `versionCode 337`.
- Signed with the same release key as 1.4.4, so it upgrades in place.
- Still trusts Sideplay's release certificate.

### Notes

Published to GitHub Pages under `https://manuel-rtsch.github.io/Sidekit/maven`.

## v1.4.4 — 2026-11-13

Sidekit 1.4.4 trusts Sideplay's dedicated release certificate, so the released Sideplay app can read the host case colour directly from Sidekit.

### SDK

- Added Sideplay's release certificate SHA-256 to `res/values/trusted_apps.xml`.
- Sideplay remains a standalone app signed with its own certificate; the shared Sidekit certificate is not used.
- No debug certificate is trusted.

### App

- Added `Sidekit-1.4.4.apk` for app installations and updates.
- `versionName 1.4.4`, `versionCode 336`.
- Signed with the same release key as 1.4.3, so it upgrades in place.

### Notes

Published to GitHub Pages under `https://manuel-rtsch.github.io/Sidekit/maven`.

## v1.4.3 — 2026-11-13

Sidekit 1.4.3 is a small release for Sideplay.

### App

- Added `Sidekit-1.4.3.apk` for app installations and updates.
- App code is unchanged from 1.4.2; this is a rebuild at tag v1.4.3 (versionName 1.4.3, versionCode 335).
- Signed with the same release key as 1.4.2, so it upgrades in place.

### SDK

- Added `SidekitIcon.ArtistBorder` and `sk_ic_artist_border.xml`, so a consumer can show an
  outlined artist glyph and fill it in the followed state without shipping local drawables.
- `SidekitIcon.Artist` remains the filled glyph.

### Notes

Published to GitHub Pages under `https://manuel-rtsch.github.io/Sidekit/maven`. The app is
unchanged from 1.4.2; the SDK adds the outlined artist icon.

## v1.4.2 — 2026-11-12

Sidekit 1.4.2 carries the third-party SDK security work onto the 1.4.1 line, so a
release-signed build can be paired with a real SDK consumer for a regression test.

### SDK

- **Access is decided by the signing certificate**, not by the `normal` read permission
  (Android grants that to any app that asks). An app is trusted when it is signed with
  Sidekit's own certificate or its certificate digest is listed in
  `res/values/trusted_apps.xml`; the list ships empty, so only Sidekit-signed apps read the
  configuration. The same list gates native launcher tiles, a `builtin:` app id is refused
  and duplicate app ids collapse to one tile.
- **The read permission is defined by the host app and named after the application id**
  (`hrln.interactive.sidekit.permission.READ_APP_CONFIG` here, a `.dev` name for the dev
  build), so two differently signed Sidekit builds install side by side and a client merges
  only the request.
- **A refused client sees `HostStatus.NotPermitted`** instead of "unsupported schema", and
  hosts advertise `CAPABILITY_TRUST`. Clients keep using their own local settings.
- **Artifacts move to GitHub Pages** (`-PsidekitPublishPages=true` stages `build/pages-repo`,
  the workflow pushes it to `gh-pages`) instead of GitHub Packages, which needs a token even
  for public artifacts.
- The SDK's `KeypadAccessibilityService` is `open`, so a consumer can extend it. Sidekit
  keeps its own service under the unchanged component name.

### App

- Includes the emulator lint fix (a back-navigation false positive in the controller
  mapping dialog) that 1.4.2 is the first release to enforce.

### Tests

Unit suites for `:app`, `:sidekit` and `:sidekit-sdk` pass, including the trust-policy tests,
the gated native discovery tests and the provider round-trip; lint and Spotless are clean.

### Notes

This is a backport onto the `release/1.4.1` line, not the full merge with
`pr/third-party-apps`: that branch does not have the directly pinned installed apps this line
carries, so merging the two lines is a separate change.

## v1.4.1 — 2026-11-12

Sidekit 1.4.1 is the first release that pairs the current app line with the third-party SDK.

### SDK

- Publishes `hrln.interactive.sidekit:sidekit-design:1.4.1` and `sidekit-sdk:1.4.1` to GitHub Packages.
- The keypad detector trusts the Sidephone module devices, including `gxa535_qwerty`. This is the fix that lets standalone Sidekit-native apps recognise the physical keypad instead of falling back to T9.
- Adds the read-only host configuration provider, shared keypad/theme stores, native-app launcher support and the SDK activity/input shell.

### App

- Includes the latest app fixes from the integration line.
- Keeps the current Sundial hold handling, compact layout and settings behaviour.
- Adds native Sidekit-app launcher entries alongside pinned installed apps.

The attached APK is the release-signed build, signed with the same key as Sidekit 1.3.0 so it installs as an update. Builds installed from the earlier debug-signed 1.4.1 asset must be uninstalled first.

## v1.3.0 — 2026-11-11

Sidekit 1.3.0 is the third-party-apps build. It keeps the launcher and app suite, and adds the
published SDK split, the read-only host configuration contract, and native launcher entries for
standalone Sidekit apps such as Sideplay.

### New features

- **Standalone-friendly SDK:** `:sidekit` is published as `sidekit-design`, and `:sidekit-sdk`
  provides the shared activity shell, input loop, keypad and theme stores, and the host
  configuration contract.
- **Third-party launcher entries:** apps that declare the Sidekit `NATIVE_APP` manifest contract
  appear on the home grid, list, search, dock and Arrange/Hide screens, and launch with the host
  config authority.
- **Host configuration contract:** SDK apps can read theme, compact layout, input mode, keypad
  module, custom mapping and per-module profiles through a read-only, permission-guarded provider.
  Hidden apps, home order, dock, messages, banking, credentials and T9 words stay private.
- **Best-effort host integration:** `SidekitStores.forHostOrLocal` uses the host only when it can
  read a complete compatible configuration. An absent, older, or unreadable Sidekit falls back to
  the app's own stores.
- **Keyboard settings:** keypad preferences (input mode, layout and mapping) are consolidated on
  Settings → Keyboard, with compact system actions and right-aligned toggle values.
- **Sundial keypad:** detection, holds (Tab/Enter for Back/Options, Previous/Next edges for
  left/right) and the real ring layout are supported by the shared input loop.
- **Arrange apps, launcher and D-pad polish:** smoother reordering and scrolling, restored dock
  labels, aligned app-list and launcher search, a dedicated Apps tile icon, and Up from the action
  bar returns to the last focused content row.

### Removed

- **Embedded Sideplay:** the in-process `apps/spotify` sub-app, its librespot native bridge,
  playback service, screenshots and Spotify-specific docs are removed. Sideplay is now a
  standalone app at [manuel-rtsch/Sideplay](https://github.com/manuel-rtsch/Sideplay).

### For developers

- `sidekit-design` and `sidekit-sdk` publish with git-derived versions, and `:sample-app` is a
  consumer smoke module built in CI.
- The Sidekit config provider exposes user theme and keypad setup to SDK apps through a read-only,
  permission-guarded contract.
- The keypad mapping, D-pad navigation, Arrange apps, launcher, third-party apps and provider paths
  have unit/Robolectric coverage.

The GB/GBC/GBA emulator, guided setup, hidden apps and alarm Undo from earlier releases remain
included. Android 12 or later is still required.

---

**Download:** `Sidekit-1.3.0.apk` (`versionCode 287`).

Signed with the Sidekit release key (APK Signature Scheme v3, RSA-4096).

SHA-256: `f86674d8e62ff813514d9d8e0131b94a2232aa85e3396deae94fe863ba2cb389`

Signing certificate SHA-256: `c7834fd5a5204d2f334a77aab56506b317afa4001b96d8e71b0321c5a0f8cd03`

**Dev build:** `Sidekit-1.3.0-dev.apk` (`hrln.interactive.sidekit.dev`, `1.3.0-dev`, versionCode 287)
installs alongside the stable app and the upstream Sidekit. Signed with the local debug key.

SHA-256: `87d093f16d629e37f48dc0f733ac799c679181763771eb35221e3811b08b2e86`

## v1.2.3 — 2026-11-07

Changes since [v1.2.2](https://github.com/HarlenPostill/Sidekit/releases/tag/v1.2.2):

### New features

- **Arrange apps:** Settings → Arrange apps sets one order for the home grid, list and dock. Select an app, move it up or down with the on-row arrows or D-pad, then press Done; Back cancels and Reset restores the default. The dock is the first four apps, so moving an app into or out of the top four docks or undocks it. Hidden apps stay out of the list. [#16](https://github.com/HarlenPostill/Sidekit/pull/16)
- **Keypad navigation defaults:** Sidekit's own screens navigate with each keypad's natural keys: Mini QWERTY T/D/B/J move and Enter confirms, and a game controller's D-pad, A and B move, confirm and go back. T9 and Sundial keep their native keys, custom mappings still take priority, and letters keep typing while a text field or search screen has the keypad. [#16](https://github.com/HarlenPostill/Sidekit/pull/16)
- **System settings page:** device settings (Wi-Fi, Bluetooth, Display, Sounds, Battery, Storage, Date & time, Privacy) move to Settings → System settings, leaving the Settings home for Sidekit's own settings. [#17](https://github.com/HarlenPostill/Sidekit/pull/17)
- **Sideport app hub:** holding a ported app shows its icon, installed version and package (or *Not installed*), an **Open as** Sideport/Original choice, and confirmed **Remove from Sideport** and **Uninstall app** buttons, so apps can now be removed by touch. Options keeps **App info**. [#19](https://github.com/HarlenPostill/Sidekit/pull/19)
- **Redesigned Now Playing:** Previous, Play/Pause and Next are one row of full-size tiles; back, Album, Artist and Save sit in the action bar; and the album art grows to fill the screen. [#22](https://github.com/HarlenPostill/Sidekit/pull/22)

### Fixes

- **No more flicker while music plays:** action-button labels no longer blank for a frame whenever a screen refreshes, which made Now Playing's back button flicker twice a second. The progress bar keeps a fixed width as the elapsed time counts up, so the bar no longer appears to jump back. [#22](https://github.com/HarlenPostill/Sidekit/pull/22)
- **Action bar order:** a reordered dock shows in the requested order on returning home. [#16](https://github.com/HarlenPostill/Sidekit/pull/16)

### For developers

- **Dev build:** `./gradlew :app:installDev` installs **SideKit Dev** (`hrln.interactive.sidekit.dev`) alongside the stable app, built like a release (R8, baseline profile) so on-device testing doesn't stutter. [#15](https://github.com/HarlenPostill/Sidekit/pull/15)
- **Mail tests on every host:** the IMAP/SMTP TLS tests now pass on Linux and CI, not just Apple-silicon Macs. [#20](https://github.com/HarlenPostill/Sidekit/pull/20)

The GB/GBC/GBA emulator, custom keypad mappings and alarm Undo from earlier releases remain included. Android 12 or later is still required.

### Contributors

Thanks to everyone who contributed to this release:

- **@manuel-rtsch**: Arrange apps and keypad navigation defaults (#16), System settings page (#17), Sideport app hub (#19), dev build (#15) and mail test fix (#20)
- **@HarlenPostill**: Now Playing redesign and flicker fixes (#22)

[Full changelog: v1.2.2...v1.2.3](https://github.com/HarlenPostill/Sidekit/compare/v1.2.2...v1.2.3)

## v1.2.2 — 2026-11-06

Changes since [v1.2.1](https://github.com/HarlenPostill/Sidekit/releases/tag/v1.2.1):

- **Custom keypad mappings:** Settings → Keypad mapping supports unrecognised keypads and custom profiles for T9, Mini QWERTY, Sundial and Game controller layouts. Learn keys by pressing them, inspect events in the key tester, and reset mappings or return to automatic detection. Profiles persist across restarts. [#13](https://github.com/HarlenPostill/Sidekit/pull/13)
- **Alarm deletion with Undo:** swipe an alarm to reveal Delete, or delete from its editor, then restore it within five seconds. Removal is saved immediately so deleted alarms cannot ring during the Undo window or return after an app restart. Clock actions also gain icons. [#12](https://github.com/HarlenPostill/Sidekit/pull/12)
- **Precise location:** Maps, Weather Current location and Compass can request GPS access. Coarse and fine permissions are requested together; approximate network location remains usable. Network and GPS lookups run concurrently so a slow network provider cannot block GPS. [#11](https://github.com/HarlenPostill/Sidekit/pull/11)
- **Consistent Undo colour:** Undo uses the muted action tone across messaging, reminders, saved headings, weather and trip planning. [#10](https://github.com/HarlenPostill/Sidekit/pull/10)
- **Readable action labels:** long labels wrap to two lines at a smaller type size, keeping icons visible and preserving accessible text labels. [#8](https://github.com/HarlenPostill/Sidekit/pull/8)
- **Clearer green case themes:** Sage and Lime accents use a distinct teal confirmation colour so primary actions and confirmations remain distinguishable. [#7](https://github.com/HarlenPostill/Sidekit/pull/7)

The GB/GBC/GBA emulator, guided setup, hidden-app controls and launcher keypad search from v1.2.1 remain included. Android 12 or later is still required; this release does not add Android 11 support.

Thanks to all contributors: @manuel-rtsch and @HarlenPostill.

[Full changelog: v1.2.1...v1.2.2](https://github.com/HarlenPostill/Sidekit/compare/v1.2.1...v1.2.2)
