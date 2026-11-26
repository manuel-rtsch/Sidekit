# Fork vs. upstream — what Sidekit has added on top of HarlenPostill/sidekit

> **Working document.** Written to plan the `upstream/main` merge, which is done (`8dce85c`); it now
> doubles as the fork's list of things worth sending upstream. §6 and [Merge
> outcome](#merge-outcome-done) record the current state.

**Fork:** `manuel-rtsch/sidekit`, branch `main` at `8dce85c` (2026-11-21) — the merge described
under [Merge outcome](#merge-outcome-done).
**Upstream:** `HarlenPostill/sidekit`, `main` at `3dc96a2` = "Merge pull request #59 from
HarlenPostill/feat/localisation" (2026-11-21).
**Common base:** `8872305` = "Merge pull request #58 from HarlenPostill/integrate/sdk-54"
(2026-11-16).
**Divergence:** 215 fork-only commits, ~218 files, +6.8k / −0.7k lines, on top of that base.

**After the merge:** `git merge-base HEAD upstream/main` is upstream's own tip `3dc96a2`, the fork is
`0` behind and `218` ahead, and the net contribution against upstream is 215 files,
+6667 / −518 lines.

## How the comparison was made

```bash
git fetch https://github.com/HarlenPostill/sidekit main        # -> FETCH_HEAD
git merge-base HEAD FETCH_HEAD                                 # -> 8872305
git log --oneline 8872305..HEAD                                # 215 fork-only commits
git log --oneline HEAD..FETCH_HEAD                             # 5 upstream-only commits
git diff --stat 8872305..HEAD                                   # the fork's contribution
git diff --stat FETCH_HEAD                                      # net state (includes upstream's work as "absent here")
```

`git diff FETCH_HEAD` is misleading on its own: upstream's localisation moves every string into
per-app `strings_*.xml` files, so ~90 files show as "deleted" that are simply upstream's work the
fork does not have yet. The batches below therefore describe **`8872305..HEAD`**, i.e. the fork's own
contribution as it stood before the merge; the two changes that were uncommitted then are now
`07cc5c9` and `8d25c84`.

---

## 1. Action bar & chrome — `sidekit/src/main/java/hrln/interactive/sidekit/design/action/`

The bar went from "a flat list of buttons" to an explicitly laid-out spec. Largest single batch.

- **Zones** — new `ActionBarSpec.kt`: `ActionPlacement.Leading|Trailing`, `ActionTarget`,
  `ActionBarSpec(leading, trailing)` with `targets()` / `actions()`. `trailingAction: SideAction?`
  became `trailingActions: List<SideAction>` across `PublishChrome`, `PublishActionOverride` and
  `SidekitScaffold` (**breaking API change**, documented as such in the v1.7.0 notes). Leftover
  width is now absorbed *between* the zones, so a trailing group pins to the right edge instead of
  leaving a gap after the leading group.
- **Label wrapping rewritten** (`ActionBarHost.kt`, ~+330 lines):
  - whole-word breaks only, with a shrink search down to `MIN_WRAPPED_LABEL_SCALE` (0.8) before the
    text is cut at all;
  - a wrapped second row steps one notch down the type scale (body→label, label→caption) and keeps
    a tighter `TWO_LINE_LEADING` (1.15) so two rows still fit the fixed bar;
  - `shortestUsefulCut()` prefers a word-boundary cut only when it keeps ≥¾ of what the mid-word
    ellipsis would show;
  - `tightWidth()` makes the layout only as wide as its widest line, so a short two-word label keeps
    its icon beside it instead of pushing both to the button's edges;
  - measured once per content change against the slot's settled width, with
    `rememberTextMeasurer(cacheSize = 24)`.
- **Options that stay open** — `SideAction.keepOptionsOpen`, so a toggle (Shuffle) does not dismiss
  the bar; the close button became a trailing-zone slot.
- **Up from the bar returns to the content** — `ChromeState.saveContentFocus()` /
  `restoreContentFocus()` (`saveFocusedChild`/`restoreFocusedChild` + `FocusDirection.Enter/Down`
  fallbacks) and an `onPreviewKeyEvent(DirectionUp)` on the bar. Replaces geometric focus search,
  which found nothing reliable once the list had scrolled.
- **Options hold from touch** — `registerActionBarFocus` now takes a `() -> Boolean` that first calls
  `inputMode.requestInputMode(InputMode.Keyboard)`: a `clickable` is `Focusability.SystemDefined` and
  cannot take focus while Compose still believes the last input was touch.
- Perf: referential spec comparison (`target.leading === synced?.leading && …`) so a normal
  recomposition does not reconcile the whole bar.

Commits: `206eb04`, `2c164e1`, `46f6ed1`, `79035b0`, `04c2301`, `2df1a06`/`6a72b82`, `512dbf2`.

## 2. Header & the touch-mode Options button

- **New touch-only Options button** in `HeaderHost.kt`: 56 dp target around the standard 40 dp
  `MoreHoriz` glyph, centred on the title's line, `canFocus = false` so D-pad navigation is unchanged,
  and it becomes a cross while the bar is open. Hidden as soon as a keypad press arrives.
- **Its own label** — "Options" / "Hide options", not the bar's "Close options", which collided with
  the action bar's own close button for screen readers and tests.
- **Space reservation** (`07cc5c9`) — `LocalHeaderOptionsInset` + `headerOptionsInset()`. The
  button is drawn **above** the screens, so a custom `SidekitScaffold` header (a search field, the
  launcher's clock) and any body starting in that band used to run underneath it. `SidekitScaffold`
  now pads its `header` slot by the inset, and bodies can opt in with
  `Modifier.padding(end = LocalHeaderOptionsInset.current)`. The value is **0 dp in keypad mode**,
  so the band keeps its full width where the button does not exist.
- `OPTIONS_HIT_SIZE` renamed `OPTIONS_BUTTON_SIZE`; the title's inset is now only the hit target
  (its text box already carries `itemPadding`), which is why the inset has to carry that too.

Commits: `c57ec95`, `5dbfdd1`, `07cc5c9` (the inset).

## 3. Keypad: detection, mappings, and ways out — `sidekit/.../design/input/`

- **Detection reads the input-device name** — `gxa535_t9` / `_qwerty` / `_media` / `_gamepad` map
  straight to a module, so the **D-pad and Enter now identify the keypad on the first key** instead
  of only buttons unique to one module. `gxa535_keyboard` (older firmware used one name for every
  module) still falls back to the key table.
- **T9 is recognised from `gxa535_phone`** — its digits identify T9 (no other module types one) while
  the D-pad, call and Home keys it shares stay silent. T9 was previously the one undetectable module.
- `Keypad.observeAttached()` — applies the attached module at launch, no key press needed.
- `Keypad.chooseModule()` — a module chosen in a UI survives detection while the same hardware keeps
  reporting itself; different hardware replaces it (v1.9.3).
- `KeypadDetector.moduleFor(deviceName, deviceId, code)` split out as a pure, testable overload;
  `KeyEvent.isGamepadButton` spelled out as a table so the mapping tests run without an Android
  runtime.
- **Mapping/guide corrections** — mini QWERTY centre **G (the printed 5 / GH key) = Confirm**,
  completing the T/D/B/J cross; controller **Select** and **R1** restored; T9 **Home** soft key
  remappable; **Sundial Previous/Next holds removed** (their tap is the media command and the corner
  arrows already move left/right); the corner arrows later gained Search/Playing Now holds that
  outrank a repeated left/right move. A first pass made them *additive* (tap and repeats first,
  hold on top), but that let a held corner rattle the focus to the edge before the screen opened,
  so they use the same single deferred hold as the rest of the ring.
- `HoldToAct` recovers a hold whose DOWN was lost to Android's touch-mode transition, using
  `event.eventTime - event.downTime >= holdMs` on the UP.
- **Ways out of a text field** — `T9Session.onEscape` and the QWERTY suggestion rocker: a tap cycles
  completions while a word composes, a tap with nothing to complete leaves the field, a hold finishes
  the word and leaves.

Commits: `1b76400`, `13db3d5`, `7436406`, `bb71039`, `25dde3a`, `d3aad0d`.

## 4. Text field & typing — `sidekit/.../design/components/SidekitTextField.kt`

- **New parameters**: `keyboardType`, `autoCorrectEnabled`, `imeAction`, `onNext`, `contentType`
  (autofill). `ImeAction.Next` degrades to `Done`/`Default` when there is no target, so the action key
  never becomes a no-op.
- **`masked = true`** → `BasicSecureTextField` with `TextObfuscationMode.RevealLastTyped`: dots, single
  line by design, text kept off the semantics tree, and T9 suggestions, the composing highlight and
  dictation all disabled (a predicted word would be a guess about a secret).
- **One-line fields hand D-pad Up/Down to focus navigation** via `onPreviewKeyEvent` (before the text
  field, which otherwise eats Up to jump to the line start); modified arrows stay the field's.
  Multi-line fields keep caret movement.
- Enter/OK and the bar's Done now route through `performAction()`; `Next` keeps the keyboard up while
  `Done` clears focus.
- **Voice dictation restored** (it had been dropped) and gated off for masked fields.
- **The typing bar no longer publishes over a visible IME** (`8d25c84`) — `LocalImeVisible`
  (a `staticCompositionLocalOf<Boolean?>` pinned by tests, reading `WindowInsets.isImeVisible`
  otherwise). The ABC/Done strip belongs to a keypad that types.

Commits: `3b7087a`, `0c2f17d`, `d3aad0d`, `8d25c84` (the IME visibility).

## 5. Media & artwork SDK — `sidekit-sdk/.../media/`

- **`RemoteArtwork` per-request headers** — `load` / `cached` / `fileName` take `Map<String,String>`,
  folded into the cache key and file name as a **SHA-256 fingerprint** (sorted, lower-cased, never
  stored) so an auth token cannot leak into a file name or a log line, and two accounts hitting the
  same URL do not share an entry. `applyRequestHeaders()` lets provider headers win over
  `Accept: image/*`.
- **New `SingleFlight<T>`** — one fetch per `url+size`. Fixes the routine race where two rows want the
  same picture: both missed the memory cache, both downloaded, both wrote the same `AtomicFile` temp
  path, the first rename won and the second failed with `Failed to rename … .img.new`, leaking a temp.
- **A cache miss is no longer reported as an error** — `decodeCached()` checks `file.isFile` before
  handing a path to `BitmapFactory`, so `clear()`/`trim()` evictions stop logging
  `Unable to decode stream: … ENOENT` for what is an ordinary miss.
- `writeDisk` deletes a leftover `.new` temp when `finishWrite`'s rename lost to a concurrent
  `clear()` (`STALE_TEMP_MS` backstop otherwise).
- New `MediaSessionOwner` in the app: one media playback owner at a time.

Commits: `bcedd8b`, `9aa889e`, `016f722`, `4d38d9a`, `73f030b`.

## 6. GB / GBC / GBA emulator — upstream's app, carried by the fork

**Not fork work.** The emulator was written by Harlen Postill and released by upstream; the fork
carries it and maintains it. This matters for attribution, for the MPL notice and for merge
planning, so it is spelled out here instead of appearing in the list of fork additions.

What upstream authored, in `09cb440` "Release Sidekit 1.2 with GB, GBC and GBA emulation"
(2026-11-05) and `4739392` (the instructions wrap):

- `app/src/main/java/hrln/interactive/sidekit/apps/emulator/` — `EmulatorActivity`, `EmulatorCore`,
  `EmulatorLibrary`, `GameLibrary`.
- `app/src/main/cpp/` — `CMakeLists.txt`, `emulator.c` (JNI), vendored `mgba-0.10.5.tar.gz`.
- Four mGBA licence files under `app/src/main/assets/licenses/`, `docs/emulator.md`,
  `EmulatorCoreTest` (androidTest).

Where it lives upstream: on the **release line, not on `main`.** It shipped in `v1.2`, `v1.2.1`,
`v1.2.2`, `v1.2.3`, and again in `v1.4.1`–`v1.7.0`; `v1.3.0` came off a parallel branch and does not
have it. `upstream/main` never received it — the two lines part at `4886a94` ("Merge pull request
#49"), `v1.7.0` is 196 commits that `main` never took, and no commit on `main` ever deleted those
paths, because they were never there. `upstream/release/1.2` … `upstream/release/1.2.3` still exist
on the remote and carry the app.

The fork merged those upstream refs, which is how it arrived: `cf57edb` (tag `v1.2`), `21bb70d`
(`v1.2.1`), `6853612` (`v1.2.2`), then `feat/sidetracks` → `1ba1dd8` / `780131c` into `integration`.
`v1.7.0` is an ancestor of the fork's `main` and is **not** an ancestor of `upstream/main`.

What the fork added on top:

- `app/src/main/java/hrln/interactive/sidekit/system/NativeLibraries.kt` (`5f64a86`) — reports a
  missing native library instead of crashing. The **build** has no NDK-absence guard, though:
  `app/build.gradle.kts` wires `externalNativeBuild` unconditionally, so a build without NDK
  27.1.12297006 fails instead of skipping the native task. Only the runtime degrades.
- `9c0fb91` formats `EmulatorActivity`; `908b079` silences a back-gesture false positive in the
  keypad-mapping dialog.
- The `shell_app_emulator` label in all four languages, the `SubApp.Emulator` launcher entry, and the
  emulator-side conflict resolutions in `1ba1dd8`.
- Still open: the three emulator Kotlin files keep raw English literals and call no
  `stringResource`, which is item 1 of *Still open* below.

## 7. App-level UX polish

- **Now Playing reworked**: one transport row and a Save that saves; then an empty state (Back only,
  no dead Album/Artist/Save or transport), and a seek bar that is keyboard-operable (←/→ ±5 s),
  tappable, drag-scrubbable, with `ProgressBarRangeInfo` / `setProgress` semantics and a knob.
- **Music internals**: `MusicGraph`, `MusicViewModel`, `MusicPlayback`, `MusicService`, `PlayQueue`,
  `PlaylistRepository`, plus 17 new cover assets in `drawable-nodpi`.
- **Settings > Keyboard** is now a short list (module, typing, Back key, mapping) with the key guide on
  its own page; hold hints shown on both mapping and guide pages.
- **Onboarding** uses the same interactive keypad guide, with a compact Use action.
- **Sideport / installed apps**: own page instead of crowded Options, app options restored after the
  integration merge, install progress stays on screen, `ApkInstaller` hardening, cleanup of
  non-exported native activities.
- Smaller: `PhoneAccess`, launcher search + `AppRow` alignment, `ClockGraph`, `WebBrowserScreen`,
  `SidekitNavHost` / `SubApp`.
- **Theme**: case colours refreshed — new **Cloud** swatch, swatches regrouped into visual pairs, one
  shared picker implementation for Settings, onboarding and the SDK.

Commits: `802483a`, `e2ec75a`, `f30f68f`, `5434c7c`, `7bf2400`, `e2a51a3`, `0e4b98b`, `574dead`,
`3786f70`, `cba588b`, `3e1044e`, `523b050`, `9f56886`.

## 8. UI patterns: undo, notices, action tones

- **`UndoWindow`** (new, `app/.../ui/UndoWindow.kt`) — one shared undo, timed in *visible* seconds,
  scoped to its screen, paused while hidden.
- **One shared notice channel** for transient messages, with a single fixed auto-dismiss window;
  `autoDismissMs` is handled in the bar host rather than per screen, so every app gets the same
  duration and the bar always comes back.
- **Action tones settled**: Cancel grey; destructive and commit actions own their colour; Undo sits to
  the right of the action and is never the exit; compact system actions right-aligned; save/send/delete
  keep their tone.
- Recorded in `docs/ui-patterns-report.md`, `-remaining.md`, `-issue.md`.

Commits: `2f86151`, `8f68ccb`, `736941d`, `e96353f`, `2d7a116`, `cead044`, `64b7867`, `d9d9bd1`.

## 9. SDK trust & permissions

- **Access decided by signing certificate** alone, not by the `normal` read permission or install
  order; refused clients get `HostStatus.NotPermitted`; hosts advertise `CAPABILITY_TRUST`.
- Read permission named after the application id, so differently signed Sidekit builds install side by
  side.
- Sideplay's **release certificate** added to `res/values/trusted_apps.xml` (no debug keystore ever).
- `SecretCipher` moved out of Banking; `KeypadAccessibilityService` made `open`; `builtin:` ids
  refused; duplicate app ids collapse to one tile; native tiles gated by the same list.
- Artifacts moved from GitHub Packages to **GitHub Pages Maven** (`-PsidekitPublishPages=true`).

Commits: `ad61e7c`, `3f12012`, `6022b64`, `16ca50a`, `751beae`, `d572442`.

## 10. Build, CI & release engineering

- **New `.github/workflows/release-apk.yml`** — tag-only, `environment: release` with a required
  reviewer and a `v*`-only deployment branch policy; keystore decoded into `$RUNNER_TEMP` (never the
  workspace); full history so `versionName`/`versionCode` stay honest; `apksigner` v2/v3 and **fails
  if the certificate is not the pinned one**; uploads `Sidekit-<version>.apk`,
  `…-release-unsigned.apk` and `SHA256SUMS.txt`.
- **Maven metadata self-check** before pushing `gh-pages`: every version directory must appear in the
  artifact's `maven-metadata.xml`, and `latest`/`release` must name the version being published.
- `versionName`/`versionCode` read from git; dev-build marker in header and About; Spotless reads the
  repo `.editorconfig`.
- Toolchain: Kotlin 2.2.10→2.4.21, Gradle 9.5.0→9.7.0, activity-compose 1.13.0, navigation-compose
  2.10.2, lifecycle 2.11.0, coroutines 1.11.0, Robolectric 4.18, Roborazzi 1.79.0. AGP deliberately
  held at 9.3.3.
- `docs/ci.md` grew ~100 lines (signing, secrets, re-cutting a release, the `site/` deploy).

Commits: `31afc8b`, `369fd01`, `195d6d4`, `ff07760`, `b9e2fde`, `0353837`, `5934aeb`.

## 11. Docs, changelog, screenshots, tests

- **`CHANGELOG.md` is new** (+458 lines, v1.2.2 → v1.10.1). Upstream has no changelog.
- Nine docs updated; `docs/emulator.md` arrived with upstream's emulator and gained the fork's
  provenance and NDK notes; `site/index.html` retargeted to this fork's releases
  (it is a separate Vercel-deployed single page, not on GitHub Pages).
- **79 screenshot baselines** added or refreshed (now-playing, action-label wrapping, onboarding,
  motion, compact layout).
- **19 new test files**, notably: `ActionBarLayoutTest`, `ActionBarZonesTest`,
  `ActionBarUpNavigationTest`, `OptionsBarTest`, `T9SessionTest`, `KeypadDetectorTest`,
  `KeypadChoiceTest`, `UndoWindowTest`, `NowPlayingTest`, `MediaSessionRegistryTest`,
  `NativeLibrariesTest`, `RemoteArtwork{DecodeCached,Headers,Naming}Test`, `SingleFlightTest`,
  `SidekitTextFieldTest`, `EmulatorCoreTest`.

---

## What upstream has that the fork does not (the thing this merge brings in)

PR #59, `3dc96a2` — localisation:

- `d4f1e79` in-app **Language** setting and localisation scaffolding: `locale/AppLanguage.kt`,
  `settings/LanguageScreen.kt`, `SidekitApplication.kt`, `res/resources.properties`.
- `635da39` translations into **German, Spanish and French**.
- `47d2893` Spanish/French `many` plurals, configuration-aware resource reads.
- `0e91596` keep every language in Play bundles, use the KTX prefs editor.

Practical effect on the merge: upstream **relocates strings into per-app `strings_*.xml` files** and
adds `values-de` / `values-es` / `values-fr` (27 files each). That is the main conflict surface, along
with the screenshot baselines and any fork-only string edits.

## Merge-risk notes for the next step

1. **Working tree is not clean.** Two unlanded changes (`LocalHeaderOptionsInset`, `LocalImeVisible`)
   plus their tests must be committed or stashed before `git merge FETCH_HEAD`.
2. **`CHANGELOG.md` does not exist upstream** — no conflict, but the Unreleased section should gain a
   line for the merge.
3. **String resources** — expect conflicts in `app/src/main/res/values/strings*.xml`; take upstream's
   split and re-apply any fork-only strings.
4. **Screenshot baselines** — upstream's localisation and the fork's 79 refreshed PNGs will collide.
   Re-record after the merge rather than hand-merging.
5. **Known API surface to re-verify after the merge**: `SidekitScaffold(… trailingActions …)`,
   `PublishChrome`, `PublishActionOverride`, `SideAction.keepOptionsOpen`,
   `LocalHeaderOptionsInset`, `SidekitTextField(masked/keyboardType/imeAction/onNext/contentType)`.
6. **Version pins** — the fork is at Kotlin 2.4.21 / Gradle 9.7.0; check upstream `build-logic` and
   `gradle/libs.versions.toml` did not move differently.

---

# Merge outcome (done)

The merge described above has been performed: `8dce85c` "Merge upstream/main (PR #59,
localisation) into the fork", parents `8d25c84` (fork) and `3dc96a2` (upstream). `git rev-list
--count HEAD..upstream/main` is now 0.

## What was done first

- The two unlanded changes were committed on their own so their history stays next to the work:
  `07cc5c9` (header options inset) and `8d25c84` (typing bar off the IME).
- An `upstream` remote was added (fetch only; its push URL is disabled, so a stray
  `git push upstream` cannot reach Harlen's repository).

## How the 24 conflicts were resolved

Rule: **keep the fork's behaviour, take upstream's strings.** Concretely —

| Conflict | Resolution |
|---|---|
| `SideAction.kt` | fork's `ActionBarSpec` + `keepOptionsOpen` wrapping, upstream's `SidekitStrings.get(R.string…)` |
| `CaseColour.kt` | fork's Cloud and row order, upstream's `labelRes` / `label(context)` / `displayLabel()` |
| `KeyMapping.kt` | rebuilt on upstream's `Text` helper; re-added the fork's T9 **Home** key, replaced the Sundial "hold for left/right" hints with the Search/Playing Now corner hints, added controller **Select** and both **shoulders** |
| `SwipeReveal.kt` | fork's `SwipeAction`, upstream's `stringResource` default label |
| `SidekitTextField.kt` | both signatures merged: `t9Actions(context, …, suggestions, …)` |
| `Dictation.kt`, `SwipeReveal` body, `MailAccountScreen`, `OutdoorData`, `MusicGraph`, `MusicService`, `PlaylistRepository`, `AppRow`, `WeatherRepository(Test)` | upstream wholesale (the fork's diff there was Spotless formatting only) |
| `MusicScreens.kt` | fork's empty-state gating + upstream's resource labels |
| `CalendarEditor`, `ClockGraph`, `SideticksScreen`, `PhonebookScreens` | **fork** here: upstream passed a new `focusParking` parameter that the fork deliberately removed in favour of its own `LocalChrome` content-focus restore, so those call sites keep the fork's `Modifier.focusRequester(...).focusable()` / `keypadDigits` wiring and take upstream's strings |
| `SubApp.kt` | upstream's `labelRes` table plus the fork's **Emulator** entry |
| `ApkInstaller.kt` | keep `android.os.Build` (used for the API 33 overload), drop upstream's unused `IntentCompat` |
| `screenshots/settings_system_home.png` | fork's baseline; all baselines were then re-recorded |

New strings added in all four languages: `sk_sidekit_colour_cloud`, `sk_sidekit_key_home`,
`sk_sidekit_key_shoulder_left`, `sk_sidekit_key_shoulder_right`, `shell_app_emulator`. The two
Sundial edge hints (`sk_sidekit_hint_next_track`, `sk_sidekit_hint_previous_track`) lost their
"hold for left/right" clause, matching the hold that no longer exists.

## Verification

- `:app:compileDebugKotlin`, `:sidekit:compileDebugKotlin`, `:sidekit-sdk:compileDebugKotlin` — OK
- `spotlessCheck` — clean
- `:sidekit:testDebugUnitTest` 77 tests, `:sidekit-sdk:testDebugUnitTest` 34, `:app:testDebugUnitTest`
  517 — 0 failures, 0 skipped
- `:app:lintDebug` — passes
- Screenshot baselines re-recorded by the test run (the build records on every run), which is why
  ~30 PNGs changed; verified by eye on `settings_system_home.png` (new **Language** row).

## Still open

1. **Fork-only screens are still English.** The Emulator (`EmulatorActivity`,
   `EmulatorLibrary`, `GameLibrary`) and a few newer side apps keep their literals. Moving them to
   `strings_*.xml` + `values-de/-es/-fr` is a separate pass.
2. **`upstream/main` will keep moving.** Pull with `git fetch upstream && git merge upstream/main`;
   the localisation work is now the common base, so those merges should be much smaller.
3. Delete this document, or fold the still-open items into `docs/localisation.md` (upstream's
   guide to adding a string or a language).
