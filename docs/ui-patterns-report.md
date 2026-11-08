# Sidekit Sub-Apps — UI Pattern Consistency Report

**Focus:** action-bar behaviour, button types, how actions are initiated, and how the UI responds to user actions.
**Scope:** the 25 `SubApp`s in `app/src/main/java/hrln/interactive/sidekit/apps/`, plus the launcher (its dock *is* an action bar) and the shared chrome in `sidekit/src/main/java/hrln/interactive/sidekit/design/`.
**Repo state:** `~/Projects/Sidekit`, branch `fix/action-tone-pass` at `eebc3c0` (Nov 2026). The audit below describes the tree as it was found; sections marked **settled** have since been decided and shipped, and the current state is recorded in §9.
**Method:** read the shared chrome (`ActionBarHost`, `SideAction`, `Scaffold`, `SidekitList`, `Buttons`, `Item`, `Controls`, `SwipeReveal`, `SidekitTextField`, `SidekitIndication`, `SidekitMotion`) and only the UI/behaviour parts of each app's `*Screen(s).kt` / `*Graph.kt`. Repositories, connectors, parsers and media engines were skipped. Line references are `path:line`.

---

## 1. The shared contract (what every app is supposed to inherit)

Five pieces of chrome are owned by the shell, not by the screens:

| Layer | File | What it does |
|---|---|---|
| Canvas | `design/canvas/SidekitCanvas.kt:96` | Draws `HeaderHost` + `ActionBarHost` over the screens, on a fixed 480×640 canvas. Also owns `BackHandler(optionsOpen)`. |
| Publish | `design/action/SideAction.kt:207` (`PublishChrome`) | A screen hands over `header`, `actions`, `options`, `trailingAction`; last attached publisher wins. |
| Scaffold | `design/components/Scaffold.kt:76` | The only intended entry point: `SidekitScaffold(title, actions, options, header, trailingAction)`. |
| Action bar | `design/action/ActionBarHost.kt:230` | Morphs between the previous screen's buttons and the new one's. |
| Typing | `design/components/SidekitTextField.kt:107` | While T9 typing is live, an *override* publisher replaces the bar with suggestions + Done + `trailingAction`. |

### 1.1 The button type system

One type, `SideAction` (`SideAction.kt:74`), with:

- **Tone** — `Primary` (**the user's chosen theme colour**, not a fixed blue; it is sage on the reference device), `Secondary` (dark green, "commit"), `Destructive` (red), `Muted` (grey). Documented intent: *Secondary = save/send/done, Destructive = destroy, Muted = cancel/undo/back-out/alternatives*. Content that needs emphasis uses the separate `emphasisA1..3` ramp so it never changes with the theme.
- **Shape** — `square = true` ⇒ fixed-width, icon-only, height-sized; otherwise flexible width shared by `weight`.
- **Motion** — `Forward` (enters from right), `Back` (from left), `Up` (rises, for in-place state change), `None`.
- **Density** — `dense` for T9 suggestion bars.
- **Art** — custom composable content (album art) instead of an icon.
- Factories that name the act and own its colour: `forward`, `back`, `square`, plus `save` (Secondary + check), `send` (Secondary + trailing arrow), `delete` (Destructive), `cancel` (Muted + left arrow, or in-place dismiss with `icon = null`), `undo` (Muted + undo glyph) (`SideAction.kt:112-165`). Screens call a role, not a tone.

Single wide button, two wide buttons, or `n` square buttons are the three shapes the layout actually supports well (`layoutSlots`, `ActionBarHost.kt:322`).

### 1.2 The morph rules (the important part)

`ActionBarModel.reconcile` (`ActionBarHost.kt:118`) reuses buttons **by `key`**, then hands surplus slots to the *button the user just pressed* first, then left-to-right. Consequences that every app inherits:

- A tapped button becomes the next screen's button (the dock button you tap turns into that app's first action).
- Reusing a key across screens means "same button, new label" — the label slides according to `motion`.
- Fewer actions than slots ⇒ merge away; more ⇒ split out. Nothing flies off screen.
- Empty action list ⇒ the whole bar slides down (`ActionBarHost.kt:116`).

### 1.3 Non-bar interaction primitives that all apps share

- **Options key (keypad top-right)** — `ChromeState.toggleOptions` (`SideAction.kt:168`): swaps the bar for `options` + a square close button. Every option closes the bar *before* running (`optionsBar`, `SideAction.kt:262`). If a screen has no options, the bar says "No options here" for 1.2 s.
- **System/keypad Back** — `ChromeState` closes the options bar first (`SidekitCanvas.kt:110`); screens add `BackHandler` for their own sub-states.
- **Feedback** — `SidekitIndication` (`design/theme/SidekitIndication.kt:44`): 12 % white press veil + white focus ring for D-pad. Action buttons instead squash to 96 % (`PRESS_SCALE`, `ActionBarHost.kt:311`) and fire haptics by tone: Secondary → `CONFIRM`, Destructive → `REJECT`, else `VIRTUAL_KEY` (`ActionBarHost.kt:449`).
- **Motion tokens** — springs only (`SidekitMotion`): `morph` for splits, `content` for label swaps, `enter` for the bar sliding, `press`, `tone`.
- **No dialogs, no toasts, no snackbars anywhere in the app tree** (verified: zero `AlertDialog`, `Toast`, `Snackbar` hits under `apps/`).

That last point is the single most consequential design decision in this codebase: **every confirmation, error and undo has to be expressed in the action bar or in a list row.**

---

## 2. Cross-app matrix

`W` = wide text button, `S` = square icon button, `—` = none. `Opt` = has an Options list. Order is left → right as rendered.

| App | Home bar | Wide/Square | Options | Confirm style | Feedback channel |
|---|---|---|---|---|---|
| Sidemessage | `←Allow / New→` (or `Make default / New→`) | W/W | yes | — | `Text|Undo` in-bar undo, inline `InfoCard` |
| Sidenotes | `New Sidenote→` or `New|Undo` | W/W | editor only | in-bar `Cancel|Delete` | in-bar undo |
| Sideticks | `Cancel|Confirm` while drafting, else `New Sidetick→` or `New|Undo` | W/W | yes | none | rows + undo |
| Phonebook | `Clear / Call` or `Calls→ / Dial→` | W/W, +S | yes | — | body text |
| Photos | `Albums→` / viewer `Back / ♥ / 🗑` | W/S/S | yes | in-bar `Cancel|Delete` | in-bar |
| Translate | `←History / Translate→` | W/W | yes | none needed | bar becomes notice |
| Calculator | `Clear / =` | W/W | yes | in-bar `Cancel|Clear history` (red) | display |
| Mail | `←Folders / Compose→` | W/W | yes | — | `Compose|Undo` or notice |
| Files | `←Browse / Search→` | W/W | yes (sort) | in-bar `Cancel|Remove` | bar becomes notice |
| Maps | `←Search / Go / Locate` | W/W/W | yes | — | status text in body |
| Music | `[art] / Search→` (S/W) | S/W | yes | — | body |
| Clock | per-tab: `New alarm→` or `New alarm|Undo`, `Pause / Reset` | W/W | yes | in-bar `Cancel|Delete` | in-bar undo (Alarms tab only) |
| Video | player: `[←] / Play / [skip]` | S/W/S | yes | — | bar auto-hides |
| Vault | `Lock / New note→` | W/W | yes | in-bar `Cancel|Delete` | label swap; Options Delete is red |
| Trip Planning | `←Saved / Plan→` | W/W | yes | — | `Plan|Undo` on the list the delete happened on |
| Banking | `←Accounts / Pay→` | W/W | yes | in-bar `Cancel|Erase` | `Pay|Undo` (exit steps aside) |
| Settings | `←Home / About→` | W/W | no | none | row secondary text |
| Web browser | `Tabs / Search→` (+`[←]`) | S+W/W | yes | in-bar `Cancel|Clear history` | `New tab|Undo`, `Tabs|Undo`, or notice |
| Apps | `←Home` | W | yes | none (uninstall unconfirmed) | hint card |
| Camera | `[Photos] [Shutter] [Record]` | W/W/W (icon-only) | error only | — | options entry |
| Sideweather | `←Hourly / 5 days→` | W/W | yes | — | `Add|Undo` on Locations (Locate hidden for the window) |
| Calendar | `Day / New` | W/W | yes | in-bar `Cancel|Delete` (red) | error row |
| Compass | `Calibrate / Save→` | W/W | yes | — | `Back|Undo`, status text in body |
| Health | `←History / Goals→` (+Allow) | W/W/W | **no** | none | message row |
| Sideport | `←Home / Add app→` | W/W | yes | in-bar `Cancel|Remove` | toast-equivalent cards |

---

## 3. Per-app walkthroughs

*These describe the tree as audited, with the line references of that moment. The tone, Cancel and Undo clauses are the "before" picture — see §9 for the shapes that shipped.*

### 3.1 Sidemessage
Bar is state-derived (`SidemessageScreens.kt:80`): no SMS permission → `Allow` (Primary) + `New→`; not default handler → `Make default` (Muted, `weight = 1.2`) + `New→`; otherwise a single `New Message→`. Keys are fixed `left`/`new`, so the left button morphs in place between "Allow", "Make default" and (in other states) "Clear".
Thread screen (`:186`) is richer: square `Back`, then `Send` (Secondary, arrow at end) **or** `Text→` **or** `Undo`; draft + undo yields three actions. Options: attach/pin/call/make-default (`:202`).
Initiation: rows open threads; screen tap starts typing; the draft row is where text is entered.
Response: delivered-state per bubble, retry by tapping a failed bubble, undo in bar, attach/pin only via Options.
**Inconsistent with itself:** "Make default" is a Muted bar action on home *and* an Options entry on the same screen (`:86` vs `:95`).

### 3.2 Sidenotes
List (`:72`): `Undo | New` when something was deleted, else one `New Sidenote→`. Editor (`:180`) has three modes: **Clean** = wide `Back` + square `Delete` (Destructive); **Dirty** = square `Back` labelled "Save and go back" + `Save` (Secondary, check); **ConfirmDelete** = `Cancel` (Muted) | `Delete` (Destructive, `weight 1.2`). Delete is also in Options so it stays reachable when the bin button is dropped in Clean mode (`:203`).
Back is handled locally (`BackHandler`, `:178`) and also *is* an action, twice over (wide label on Clean, square on Dirty) — the widest variation of the back affordance in the suite.

### 3.3 Sideticks
`SideticksScreen.kt:64`: **main action first**, then secondary ones — the reverse of nearly every other app: `Confirm` (Secondary, check) or `New Sidetick→`, then `Cancel`, else `Undo` (Muted), then `Allow` when notifications are needed. Options: due-time toggle while drafting, else sort toggle.
Destructive "Clear All Ticked" is a **plain list row** (`:99`) — no tone, no confirm, no undo. Deletion of a single tick is a swipe → immediate delete + in-bar Undo.

### 3.4 Phonebook
The cleanest example of "the bar narrates the current mode" (`PhonebookScreens.kt:75`): dial-pad with a number → `Clear` (Muted) | `Call` (Secondary, `weight 1.6` in the dialer at `:264`); without contacts permission → `Allow` (Primary) | `Dial→`; default → `Calls→` | `Dial→`.
Contact detail (`:173`): square `Back` + `Call` (Secondary) + `Text→` — three actions, one square.
Options are mode-specific (Recents / Dial pad / "Text this number" / "System dialer").
Initiation is almost entirely rows and tiles; the bar is the commit path.

### 3.5 Photos
Grid (`:114`) has one action: `Albums→`. Albums (`:227`) one: `Back`. Viewer (`:333`, published via raw `PublishChrome` with `header = null`): wide `Back` + square `♥` (Primary when favourited, Muted otherwise) + square `🗑` (Destructive). Confirm is in-bar. Options: share/favourite/play.
The gallery's permission state swaps the *entire* action list for a single `Allow` (Secondary) or `Open settings` (`:212`) — no Back on that screen, since it is the only route.

### 3.6 Translate
`TranslateScreen.kt:72`: translating → single Muted `Cancel`; idle → `←History | Translate→`; History/Large → single `←Translate`.
Errors replace the whole bar with a dense Muted `notice` action that dismisses on tap (`:80`) — the same idiom Files/Web/Mail use, implemented a third time.
Mic permission prompt = `Cancel` (Muted) | `Allow` (Secondary).
Options: From/To cycling, swap, large text, clear; History mode puts `Clear history` (Destructive) in Options.
Only screen where "Cancel" is the sole button with no exit affordance — the bar becomes a one-way door for the duration of a translation.

### 3.7 Calculator
The bar *is* the calculator's commit keys: `Clear | =` (`CalculatorScreen.kt:83`), both default Primary, no icons, no motion. History page: single `Back`. Clearing history: `Cancel | Clear` (both Primary — no Destructive).
Options are page-dependent (History/Keypad, Clear history).
Initiation: the 4×n grid of `SidekitTile`s plus raw hardware digits (`calculatorHardwareKey`). Tiles are colour-coded by role (`CalcKey.color()`) — a second, parallel colour language that doesn't use `ActionTone`.

### 3.8 Mail
Home (`MailGraph.kt:62`) delegates to `mailActions` (`:146`): **notice** (dense Muted) *or* `Undo | Compose→` *or* `←Folders | Compose→` — note the left slot is dropped entirely when an undo is pending. Folder (`:118`) `←Folders | Compose→`; message (`:81`) `←Back | Reply`; folders list (`:93`) single Back; account (`MailAccountScreen.kt:24`) `Back | Connect` with a label swap to "Connecting…".
Compose (`:192`) is the only 3-wide-button bar in a form: `Cancel | Save (Muted) | Send→`. No green tone for Send, no icon, and the middle button is a save — the one place where the "two slots, one commit" rule is visibly overrun.
Initiation: rows; text fields do **not** grab the keypad (only `To` gets `focusOnKeypad`, `:218`), so typing needs a tap on each field.

### 3.9 Files
`FilesScreen.kt:96` is the reference implementation for this suite: Browse/Search, plus `Allow` (Secondary, check at end) when media access is missing, plus `Cancel | Remove` when a swipe asked for confirmation (`confirmActions`, `:66`), plus `notice` replacement (`withNotice`, `:64`).
Options: one entry per sort mode, current one toned Primary (`:198`).
Swipe → **confirm in bar** → delete. (Banking/Sideweather do the same gesture with no confirm.) `BackHandler` cancels the pending delete (`:94`).

### 3.10 Maps
`MapsLive.kt:53`: `←Search | Go | Locate` — three wide buttons, where the third is a *dynamically labelled permission action* (`locationAction`, `OutdoorUi.kt:54`) that reads "Allow"/"Enable precise"/"Locate". Options: Zoom +, Zoom −, Centre — plain actions with no state shown.
`MapsScreen.kt:55` (the mock variant used by previews) instead uses `backAndGoActions`, giving a **green** Go. Same screen, two different commit colours depending on which file you open.

### 3.11 Music
Home (`MusicScreens.kt:102`): square album-art button (art, not icon) + `Search→`; Options `New playlist`. Library lists/albums/artists: single `←Music`. Now Playing (`:198`): square `Back` + three icon-only *wide* buttons (Album, Artist, Save) — no tones at all, so nothing reads as the primary.
Permission gate publishes chrome directly (`MusicGraph.kt:88`).
Options: shuffle/repeat as state-carrying labels ("Shuffle: on").
Initiation: rows and tiles; play/pause/seek live in the body, not the bar.

### 3.12 Clock
One bar per tab (`ClockGraph.kt:98`): Alarms `New alarm→`; Timer `Pause/Resume/Start` (icon at end, `Up` motion) + `Reset`; Stopwatch `Pause/Start` + `Reset/Lap`; `Undo` appended at the right when an alarm was deleted (`:130`).
Permission page: `Allow→` + `Back` (backward motion).
Alarm editor (`:195`): `Cancel | Delete` (Destructive) while confirming, else `Cancel | Save` (Secondary, check) — both `Cancel`s carry a left-arrow icon and `Back` motion. Delete lives in Options.
Ringing screen (`RingingActivity.kt:28`): `Snooze | Dismiss`, **both default Primary** — the one screen where the two-button layout has no visual hierarchy at all, and it is the screen a half-awake user meets.

### 3.13 Compass
`CompassLive.kt:49`: `Calibrate | Save`, both default Primary, no icons, no motion. Options: the injected `locationAction` + Bookmarks.
Saved headings (`:78`): `←Back | Undo` — Undo appended *after* Back, Muted, no icon.
Feedback is body text (`status` string), not bar or row state.

### 3.14 Health
`HealthLive.kt:57`: home = `←History | Goals→` (+`Allow`); goals = `←Cancel | Save` (both Primary); history = single Back.
**No Options anywhere in Health** — the only app besides Settings/Placeholder, so its Options key always answers "No options here".
Validation and errors are list rows (`message`), not bar notices. Save is neither green nor check-marked.

### 3.15 Video
Gallery (`VideoScreens.kt:94`): square `Back` + `Allow` (Secondary) or `Open settings→`. Player (`:226`): square `Back`, wide `Play/Pause` (`Up`), square `Skip` (Muted); when the controls auto-hide the action list goes **empty** and the bar slides away — the only screen that uses "no bar" as a UI state. Options: `Back 10 s`, `Restart`.

### 3.16 Vault
`VaultGraph.kt:85`: busy → single `Working…` with a no-op handler (*Back disappears while working*); locked → `←Home | Unlock`; confirm delete → `Cancel | Delete` (Destructive); confirm discard → `Keep editing | Discard` (Destructive); editing → `←Notes | Save` (Secondary, no icon); list → `Lock | New note→`.
Options: `Delete` (no tone) while editing.
Typing gets the keypad (`focusOnKeypad = true`, `:141`).

### 3.17 Trip Planning
`TripLive.kt:120`: per-page bar, all with `back("back","Back")` and a plain `SideAction("save"/"add"/"refresh"/"replan")` as the right-hand commit — Save is Primary, never Secondary. Undo is appended at the *end* of the list (`:128`), so it appears right of the primary.
Options are page-dependent and do most of the navigation work (`:129`: Find From/To, journeys, departures) while the bar keeps the exit.
`TripPlanningScreen.kt:48` (mock) again uses `dualArrowActions`.

### 3.18 Banking
The most elaborate bar behaviours in the suite:
- Home (`BankingScreen.kt:100`): `←Accounts | Pay→`, or `Undo | Pay→` with undo pending (the back slot is sacrificed, `withUndo` at `:74`), or `Add account→` when empty.
- Pay / New account: **one button until the form is valid**, then it splits into square `Cancel` + green `Record`/`Save` (`:275`, `:338`) — the bar doubles as a form's validity meter.
- Locked (`:382`): square Back + `Unlock`/`Open` (Primary).
- Unreadable (`:410`): Options `Erase and start over` (Destructive) → in-bar `Cancel | Erase`.
Swipe → immediate delete + Undo (no confirm), unlike Files.

### 3.19 Settings
Home (`SettingsScreen.kt:76`): `←Home | About→`. Sub-screens: single Back. Rows change value **in place** on tap (list/grid, keypad, input mode) and show a secondary line — no bar involvement, no confirmation.
`ArrangeHomeScreen.kt:95` is the only 3-wide bar with a genuine hierarchy problem: `←Settings | Done→ | Reset`, i.e. the commit sits in the middle and the reset trailing — and it is the only screen where a destructive-ish reset is last.
`SidekitLineButton` (the in-body full-width button, `design/components/Buttons.kt:31`) appears only in Settings and Sideport: "Original blue" (`CaseColourScreen.kt:97`), "Show all apps" (`HiddenAppsScreen.kt:46`), "Reset … keys" (`KeypadMappingScreen.kt:103`), two in `SideportGraph.kt:387,396`, and ported-app `ButtonBlock`s (`ProjectionScreens.kt:244`).
Keypad mapping (`:73`): Back + Options (`Reset keys` Destructive, `Use automatic` Muted); the learn-a-key flow is `Cancel` / `Try again`(Muted) + `Save`(Secondary).

### 3.20 Web browser
Highest-density bar: idle → `Tabs | Search→`; while typing in the address field → `Cancel | Go` (Secondary, arrow at end); with page history → **square `←page` + `Tabs | Search→`** (`:283`), the only place a square back and a labelled "Tabs" coexist. Options: forward, reload/stop, bookmark, text zoom.
Tabs (`:421`): `Page | New tab→` (`Add` icon *at the end* of a forward action). Bookmarks (`:474`): `Undo | ←Tabs`. History (`:500`): `Cancel | Clear` (Destructive) else Back; Clear history also in Options.
Loading state renders `SidekitScaffold("Web browser") {}` (`:167`) — empty actions *and* empty options, so the Options key stalls on "No options here".

### 3.21 Apps
`AppsGraph.kt:55`: single `←Home`. Behavioural mode is carried by Options: long-press a row selects it (`:65`), then Options offers `App info` / `Uninstall` (`:49`) — with no visible selection state beyond a hint card. `Uninstall` has **no Destructive tone**.
The row uses raw `combinedClickable` rather than `SidekitTile`/`SidekitItem` click handling, so it is the only list in the suite whose long-press is invisible until read.

### 3.22 Camera
`CameraScreen.kt:120` publishes chrome directly (`header = null`), then builds a **three-wide-icon-only** bar: `Photos` (Muted) / `Shutter` / `Record` — none are `square`, all `weight = 1`, no labels. While recording the bar collapses to a single Destructive button whose *label* is the elapsed time (`:122`).
Permission gate launches a one-button bar (`:106`): `Allow`/`Open settings` with a check icon, Primary.
Errors are pushed into **Options** (`:128`), where the option's label is the raw error text and its click handler does nothing — the only time Options is used as a log.

### 3.23 Sideweather
Home (`SideweatherScreens.kt:51`): `←Hourly | 5 days→`; five-day: `Back | Locations→`; locations (`SideweatherGraph.kt:68`): `←Back | Add→` **or** `Undo` **or** `locate`, i.e. three actions with Undo in the middle.
Options: Locations, `Use °C/°F` toggle expressed as a label.
Swipe → delete + Undo. `OutdoorInfo` rows handle navigation.

### 3.24 Calendar
`CalendarScreen.kt:41`: `Day | New` — the view toggle and the create action, *both* default Primary, no icons, no motion. Options: Agenda, Today.
Editor (`CalendarEditor.kt:80`): `Cancel | Save` (both Primary), `Saving…` no-op while busy, `Cancel | Delete` while deleting — Delete is **not** Destructive, and Delete is only reachable through Options in the first place. Calendar choice is a mode with a single `Back` action.
Initiation is grid-of-tiles plus the bar; the month grid is a 7×6 of `SidekitTile`s.

### 3.25 Sideport
Host app for ported apps, so it has two button systems. Its own screens (`SideportGraph.kt:136`, `:224`, `:344`) follow the house style: `←Home | Add app→`, single Back, `Cancel | Remove` (Destructive, `weight 1.2`), app screen = square Muted `Back` + `Open→`, chat = square Back + green `Send`, now-playing = square Back + `[prev] Play [next]`.
In-body buttons in the Add-app screen use `SidekitLineButton` (`:387`).
Projected apps (`ProjectionScreens.kt:289`) build the bar from the ported app's own `ActionSpec` list: square Muted Back + up to two `motion = Forward` buttons, tones mapped 1:1 from the source app. Their forms never set `focusOnKeypad` (`:270`), and in-body button blocks map to `SidekitLineButton` with `Primary/Secondary/Destructive` (`:244`).

### 3.26 Launcher (the chrome reference)
`LauncherScreen.kt:174`: the **dock is the action bar** — `dockActions(dock, hiddenApps, …)` — so opening an app morphs the tapped dock tile into that app's first button. In search mode the bar becomes a single dense query chip plus a `trailingAction` square clear button (`:207-215`) — the only use of `trailingAction` in the codebase. Options: grid/list toggle + Settings. The clock title is a tap/long-press gesture surface (`clockGestures`, `:255`), not a button.

---

## 4. What is consistent (and worth protecting)

1. **One chrome, one entry point.** Every app publishes through `SidekitScaffold`; only four full-bleed/media surfaces bypass it (camera, photos viewer, video player, music permission bar) and they all use the same `PublishChrome` contract.
2. **Two-slot grammar.** Left = back/alternative, right = forward/main, in 20 of 25 apps. Where it is broken (Sideticks, Camera, Music Now Playing, 3-wide forms) it is immediately noticeable.
3. **Key discipline.** `back`, `forward`, `main`, `left`, `right` are reused deliberately so buttons morph rather than move. `main` is used for "the screen's one evolving action" (Banking Record, Clock Start/Pause, Calendar Save, Sideticks Confirm) — a genuinely nice convention.
4. **The bar narrates.** Banking Pay/Lock, Sidenotes Clean/Dirty, Photos viewer, Vault locked/editing, Translate busy/error, Clock deleting, web address typing — the same two slots change tone, shape and label to describe state. No modals.
5. **Options is the universal escape hatch.** 22 of the 25 apps attach something to it on at least one screen, and the `optionsBar` wrapper guarantees a close button and closes-before-running semantics.
6. **Confirmations cancel with Back.** Where in-bar confirmation exists, `BackHandler` un-confirms (Files, Photos, Sidenotes, Sideport, Web, Calendar, Vault, Calculator, Banking).
7. **Motion is semantic, not decorative.** `Forward`/`Back`/`Up` encode direction of change; `Up` is repeatedly used for in-place state changes (Banking, Clock, Notes, Sideticks, Video).
8. **No notifications outside the chrome.** Zero dialogs/toasts/snackbars; nothing interrupts.
9. **Busy is a label swap**, not a spinner: "Saving…", "Connecting…", "Loading…", elapsed recording time.
10. **Focus and press feedback are shared**: the white focus ring and 12 % press veil come from one `SidekitIndication`, so D-pad navigation looks identical everywhere; only action buttons add squash + haptics.

---

## 5. Inconsistencies

Grouped by symptom, ordered by how visible they are. Severity: **H** = users will notice and misread, **M** = noticeable on comparison, **L** = polish.

### A. Three different feedback channels for "something happened" — **H**
**Settled in part.** Undo is no longer one of the three shapes: every app routes it through `undoBar` (§9), so the divergence left in this section is the *notice* channel (`notice` action vs body card vs body row), which is fork F3 and still open. The pre-work picture:

The same concept is implemented four times:

| Channel | Where | Shape |
|---|---|---|
| Bar replaced by a `notice` action | Files `:64`, Web `:145`, Mail `:148`, Translate `:80` | one dense Muted button, tap to dismiss, no other actions available |
| ~~Bar replaced by `Undo` + main~~ | ~~Banking, Mail, Web, Clock, Sidenotes, Sideweather~~ | superseded: `continueWith|Undo`, Undo always last |
| In-body `SidekitCard` message | Apps `:59`, Sideport `:143`, Trip `:209`, Banking `NoticeCard` | tap to dismiss |
| Bare body row / status string | Health `message`, Calendar `error` row, Compass `status`, Sideticks `error` row | not dismissible or dismiss-by-navigation |

Consequences: a Files user learns "the bar tells me what happened"; a Health user learns nothing. The four `withNotice`/`withUndo` copies are duplicated code with subtly different behaviour (`withUndo` in Banking takes `List<SideAction>` and keeps only `main.last()`; in Web it takes a single `SideAction`).

### B. Destructive tone is not applied consistently — **H → settled**
All 22 destructive actions now carry `ActionTone.Destructive`, enforced by `ActionToneConventionTest`. What the audit found:

Correctly red: Files `Cancel|Remove`, Banking `Cancel|Erase`, Photos `Cancel|Delete`, Sidenotes `Cancel|Delete`, Clock `Cancel|Delete`, Web `Cancel|Clear history`, Sideport `Cancel|Remove`, Vault `Delete`/`Discard`, Translate `Clear history` option, Keypad `Reset keys` option, Camera stop-recording.

Not red at the time, all fixed since:
- Calendar `Delete` / `Delete series` — now Destructive in the confirm bar **and** in Options.
- Calculator `Clear history` — now Destructive in the confirm bar and in Options (and renamed from "Clear").
- Apps `Uninstall` — now Destructive; still unconfirmed (it relies on the platform dialog).
- Vault `Delete` option — now Destructive, matching its bar counterpart.
- Clock `Delete` option — untinted when the audit was written and missed by it; now Destructive.
- Sideticks `Clear All Ticked` — **still** a plain row with no tone, no confirm and no undo: rows have no tone variant.
- Sidenotes/Web/Mail `Delete` via swipe is red in the bar but the *revealed* swipe button is drawn by `SidekitSwipeRow` with its own red, outside `ActionTone`.

Inverse: Sidemessage `Make default` uses **Muted** for a system-level privilege change; Camera's `Photos` uses Muted inside a bar where it is not a fallback but the navigation into the gallery.

### C. "Commit green" (Secondary) is optional — **H → settled for commits**
`SideAction.save` and `SideAction.send` now fix green + check + motion, and the guard test rejects a commit label with any other tone. 26 of 31 commit-labelled actions are green; the five that are not are permission `Allow` prompts. What the audit found:

Applied in 20 places (Banking, Clock, Notes, Sideticks, Phonebook call, Sidemessage send, Sideport send, Keypad save, Photos/Files/Video allow, Translate allow).

Missing at the time on the *primary commit* of six form screens: Calendar `Save`, Health `Save`, Compass `Save`, Trip `Save`, Vault `Save` (Secondary but no icon), Mail `Send` (forward arrow instead). All six now go through `save`/`send`. `Settings` `Done` remains a forward action by decision — it leaves the screen, it does not commit.

Still open: `Allow` on permission gates is Secondary in Photos/Files/Video but Primary in Camera/Phonebook/Sidemessage/Health and Primary-with-dynamic-label in Maps/Compass. Green therefore still means "permission" in some screens and the theme colour in others — a decision, not a tint, so the guard test deliberately does not touch it.

### D. The back affordance has four appearances — **M**
| Appearance | Example |
|---|---|
| Wide `SideAction.back` + label + left arrow | `←Accounts`, `←Folders`, `←Music` |
| Wide `back()` reused for a *forward* destination | Mail `←Folders`, Health `←History`, Web `Tabs`, Translate `←History`, Trip `←Saved` — arrow points backwards, motion is `Back`, but the destination is deeper |
| Square `Back` (Primary) | Bank Pay when valid, Notes Dirty, Video player, Music Now Playing, Phonebook detail |
| Square `Back` with `tone = Muted` | Sideport, Projected apps |
| "Save and go back" square | Sidenotes Dirty |

Same gesture, five visual weights, and two contradictory meanings for the left-arrow icon.

### E. Primary position flips — **M → settled for Undo**
Primary last (right) everywhere now, and Undo has one position: right, next to it. Sideticks' reversed bar is gone (`Cancel | Confirm` while drafting, `New Sidetick→` otherwise) and Banking no longer drops the back action. Still open:
- Arrange Home puts `Done` in the middle: `Settings | Done | Reset` (`ArrangeHomeScreen.kt:95`).
- Sideweather's Locations bar is `Back | Add | Locate` when no undo is pending — three actions, the last whole-bar candidate for a two-slot rule.
- Camera, Mail compose, Phonebook detail, Maps and Web still run three or four actions for reasons unrelated to Undo.

### F. Button count/shape: square is under-used and over-used — **M**
- Camera: three icon-only **wide** buttons with no labels (`CameraScreen.kt:126`) where `SideAction.square` exists and is used for the identical shape in Photos/Video/Music.
- Music Now Playing: square back + three wide icon-only buttons — four buttons, none green, none labelled.
- 3-wide text buttons appear in Mail Compose, Arrange Home, Sideticks, Maps (Search/Go/Locate), Phonebook detail, Web (3–4), Trip locations. At 480 px with `actionButtonPadding = 34.dp` these labels are the ones most likely to wrap to two lines (the `ActionLabel` fallback silently steps down the type scale).
- Square buttons also carry `.weight` nowhere, so a bar of `square` + 2 wide gives the wide buttons 50/50 regardless of label length (Bank Pay, Video player, Photos viewer).

### G. Options coverage and overlap — **H**
- No Options at all: the entire Health app, the entire Settings app, Placeholder, the Sidenotes list (only the editor has one), and the Mail folders list. On those screens the Options key answers "No options here" for 1.2 s — a dead key.
- Options is used for **different kinds of things** with no rule: navigation (Calendar Agenda, Trip Find From/To), creation (Music New playlist), toggles (Web zoom, Sideweather units, Music shuffle, Maps Centre), destructive (Banking erase, Translate clear, Keypad reset), state (Clock alarm access, Files sort), and an error log (Camera).
- Three screens hide their *only* destructive action in Options (Calendar Delete, Banking Erase, Apps Uninstall) with **no confirmation**; four others require an in-bar confirmation for the same operation. Confirmation therefore depends on which screen you happened to open, not on the risk.
- Options entries never show selection state except by Primary tone (Files sort, T9 input mode) — Maps `Centre`, Music `Shuffle: on`, Sideweather `Use °C` are inconsistent about it.

### H. Delete gesture: confirm vs undo — **H (gesture unchanged)**
The *bar shape* that follows a delete is now identical everywhere (`continueWith | Undo`, §9). What is not unified is what the gesture itself asks for:

Same gesture (`SidekitSwipeRow`), three contracts — and one clear majority:

| Swipe → | Apps | Count |
|---|---|---|
| immediate delete, `Undo` offered in bar | Banking, Clock alarms, Compass, Mail, Sidemessage, Sidenotes, Sideticks, Sideweather, Trip, Web browser | 10 |
| delete deferred, bar asks `Cancel | Remove`/`Delete` | Files, Vault | 2 |
| immediate delete, **no** undo and no confirm | Translate (history) | 1 |

So "swipe = delete + Undo" is the established contract (10 of 13 swipe screens); Files and Vault are the outliers, and Translate is the only silent data loss in the suite. `SidekitSwipeRow` still exposes a single `onDelete` callback (`SwipeReveal.kt:90`) which the undo camp uses to *delete* and the confirm camp uses to *ask*. A full-swipe drag past `FULL_SWIPE_FRACTION = 0.6` (`:195`) calls the same callback as tapping the revealed button (`:224`), so in Files a fast swipe lands on a confirm bar and reads as the gesture having failed. The component makes the ambiguity worse by exposing a single `onDelete` callback (`SwipeReveal.kt:90`) that fires from *two* different user gestures — releasing past `FULL_SWIPE_FRACTION = 0.6` of the row (`:195`), and tapping the revealed button (`:224`) — while Files and Sideport use it to *ask* for confirmation rather than to delete. The callback is therefore misnamed for half its callers, and a full-swipe drag in those screens lands on a confirm bar, which reads as the gesture having failed.

### I. Busy/disabled states are ad hoc — **M**
- `Saving…`/`Working…` with `onClick = {}` in Calendar and Vault; Vault's version removes the Back action entirely.
- `Saving…`/`Loading…`/`Connecting…` that **keep the live handler** in Trip (`:122`), Bank Up (`:48`), Mail Account (`:24`) — pressing again re-invokes the operation.
- No action is ever visually disabled, greyed or given a progress indicator; `SideAction` has no `enabled` flag, so the bar cannot express "not now" except by replacing the whole list.
- Only Banking uses "validity gates the bar" deliberately; every other form keeps a live commit button and reports failure afterwards (Health message row, Calendar error row, Translate notice, Compass status).

### J. How actions are initiated varies by app family — **M**
- **Bar-driven apps** (Camera, Video, Calculator, Clock timer, Banking lock): the body is nearly inert; the bar is the only input.
- **Row-driven apps** (Settings, Apps, Sideport menu, Trip, Music library): the bar holds one Back; everything happens in rows, so the bar is decorative.
- **Mixed** (Files, Mail, Phonebook, Banking): rows navigate, bar commits.
- **Gesture-only affordances**: launcher clock (tap = search, long-press = grid/list), Apps long-press → Options, swipe-to-delete, video poke-to-show-controls. None of these is discoverable from the bar; two of them (launcher clock, Apps selection) are the *only* way to reach the corresponding action.
- **TextField keypad capture**: `focusOnKeypad` is set on 20 of 30 fields. Omitted on Mail subject/body, Banking amount/note (only payee and account name opt in), Calendar start/end, Sideticks due-time, and all Sideport-projected fields. In those, pressing a keypad letter with nothing focused does nothing, while on the *previous* screen it started typing.
- **Trailing action**: used once (launcher search). Camera's "Photos" and Music's album art are effectively trailing actions hard-coded into `actions`.

### K. Parallel duplicate screens drift — **M**
Everyday "mock" screens coexist with live ones and already disagree:
- `MapsScreen.kt:55` (`backAndGoActions`, green Go) vs `MapsLive.kt:53` (plain Primary Go).
- `MailScreen.kt:33`, `SideweatherScreens.kt`, `CompassScreen.kt`, `HealthScreen.kt`, `TripPlanningScreen.kt` use `dualArrowActions` with no-op handlers, while the live graphs hand-build different action lists.
These are preview/screenshot surfaces, but they are the ones a reader compares against the Figma file, so they are where the drift becomes canon.

### L. Smaller divergences — **L**
- Title alignment: Calculator switches `titleAlign = End` for the display (`CalculatorScreen.kt:76`) — the only right-aligned header.
- `header = null` (no title) on Camera, Photos viewer, Video player, and Sidenotes while the keyboard is open — the bar stays, the title doesn't.
- Empty action lists: Video player (controls hidden) and Web browser (loading) → bar slides away or Options stalls.
- Bank Up "sync" button uses `SideAction("sync", label)` with no icon while sibling connectors use icons.
- `SidekitLineButton` (in-body full-width button) has only six call sites in the whole suite; sidebar actions in Settings/Add-app otherwise use plain `SidekitItem` rows, so "this is a button, not a row" is inconsistently signalled.
- Camera's elapsed-time-as-label means the bar's text changes every second — the only action whose label is a live clock.

---

## 6. Recommended rules (smallest set that removes most of the divergence)

1. **One feedback primitive.** Undo is done (`undoBar`); the notice half is not. Promote `withNotice` into `design/` and require it: bar-replacement notice for transient success/error, `continueWith | Undo` for reversible deletion, body row only for persistent explanation. Delete the three private copies.
2. ✅ **Tone is a contract, not a suggestion.** Implemented as `ActionToneConventionTest` rather than a linter: it scans app sources for destructive, commit, cancel and undo labels and fails on the wrong tone. Roles (`save`, `send`, `delete`, `cancel`, `undo`) make the tone implicit, so new code cannot forget it. A Muted back is expressed as `SideAction.cancel`, not by hand-building `back`.
3. **Give `SideAction` an `enabled`/`busy` state** so "Saving…" is not a no-op button and does not have to change the button count.
4. ✅ **Two slots, decided: left = what you are doing, right = what comes next.** Undo is always right and is only ever added, never substituted (`undoBar`, §9). Primary rightmost. Sideticks, Arrange Home and Trip's undo-position split are fixed by that rule; Arrange Home's `Done` slot is still open.
5. **One delete contract.** Either "swipe = confirm in bar" everywhere (and disable the pass-the-threshold short-circuit when a confirm is configured), or "swipe = delete + Undo" everywhere. Not both.
6. **Confirmation for irreversible operations regardless of entry point** — Options-invoked deletes (Calendar, Banking erase, Apps uninstall, Keypad reset, Translate clear) should route through the same `ConfirmBar` as Files/Photos.
7. **Either populate Options or remove the key per screen.** Health, Settings, Placeholder, Sidenotes list and Mail folders should either gain one entry ("About", "Sort", "Refresh") or the shell should not advertise an empty bar.
8. **`focusOnKeypad` on every field that is the screen's main input**, and `trailingAction` for screen-scoped utilities (Camera "Photos", Music album art) instead of positional hacks.
9. **Square = icon-only, always.** Migrate Camera's and Music Now Playing's icon-only wide buttons to `SideAction.square`, or give them labels. Never a third shape.
10. **Retire the duplicate mock screens** (or generate them from the live action lists) so previews cannot drift from shipped behaviour.
11. ✅ **Roles, not tones.** A screen names the act (`save`, `send`, `delete`, `cancel`, `undo`) and the design system decides colour, icon and motion. Extending the vocabulary is one companion edit.
12. ✅ **Content never wears the theme colour.** Selection, progress, routes, markers and highlights use `emphasisA1..3`; `primaryA1..3` is reserved for Primary actions and the colour picker.
13. ✅ **A pending undo never removes a capability.** The primary keeps its slot; if the screen has no primary, `Back` does.
14. ✅ **An undo offer belongs to the screen that made it.** Five visible seconds, paused while backgrounded, committed when you leave that screen or app — never a wall-clock timer that outlives what it can restore.

---

## 7. Adoption: established law, majority, forks, outsiders

Scale of the sample: **294 `SideAction` constructions** across the 25 apps, of which 73 use `SideAction.back`, 31 `forward`, 23 `square` (127 factory-built, 43 %); **78** carry an explicit `ActionMotion` (26 %); 13 files use `SidekitSwipeRow`; 11 `Undo` buttons exist on 10 different lists.

The point of this section is to separate *fixable drift* from *undecided product*. A pattern used by one app is an outlier; a pattern used by half the apps is a decision somebody skipped.

### 7.1 Tier A — de facto law (≥ 90 %, treat as a constraint, not a preference)

| Pattern | Adoption |
|---|---|
| Chrome published through `SidekitScaffold` or, for full-bleed media, `PublishChrome` | 100 % |
| No dialogs, toasts or snackbars; all feedback in chrome or body | 100 % |
| Back is reachable as a bar action, not only as a system key | 100 % of non-media screens (Video's auto-hide, Translate's busy cancel excepted) |
| One `SideAction` type + key-based morphing | 100 % |
| `square` means icon-only | 23/23 square uses |
| Busy = label swap on the acting button | 6/6 busy states |
| Options key exists as the secondary surface | 22/25 apps expose it somewhere |
| Fixed keys `back` / `forward` / `main` / `left` / `right` reused across screens | every app; `back` is the most reused key |
| Tones are semantically named and reachable via one enum | 100 % of coloured actions go through `ActionTone` |
| Destructive labels are red | 22/22 after the tone pass (guard test) |
| Commit labels are green with a check | 26/31; the remainder are permission prompts |
| `Cancel` and `Undo` are Muted | 23/23 cancel sites, 11/11 undo sites (guard test) |
| Undo occupies the right slot and never displaces the primary | 11/11 undo sites via `undoBar` |
| Emphasis content uses `emphasisA*`, never the theme colour | all migrated; `primaryA*` only in `ActionTone.Primary`, `SidekitLineButton` Primary, `CaseColourScreen` |

### 7.2 Tier B — majority (≈ 65–90 %, codify with a named exception list)

| Pattern | Adoption | Exceptions |
|---|---|---|
| Two-slot grammar: exit left, commit right | ~20/25 apps on their main screen | Camera, Sideticks, Music Now Playing, Mail Compose, Arrange Home, Maps (3-wide) |
| Swipe → delete + `Undo` | 10/13 swipe screens | Files, Vault (confirm instead), Translate (nothing) |
| Destructive tone on destruction | ~~17 vs ~8~~ → 22/22 | Sideticks `Clear All Ticked` (row has no tone) |
| Secondary (green) on the commit action | ~~20/31~~ → 26/31 | permission `Allow` prompts |
| Undo is `Muted` | 11/11 | — |
| Notice-style transient message | 4 apps use the bar (Files, Mail, Translate, Web); ~7 use body cards/rows | split — see 7.3 |

### 7.3 Tier C — split down the middle (▶ **decide before touching code**)

These are not outlier bugs. Each has two coherent camps of roughly equal size, and every "fix" applied before the decision just reshuffles which camp is wrong.

**F1 — Is Undo a bar takeover, or an extra button?** ✅ **settled: neither.**
Three shapes were on the table: takeover (4 apps, which dropped the exit), additive (6 apps, which grew a third button and put Undo in the middle in Sideweather), and the one that shipped:

> The bar keeps two slots. The left slot holds the continuation — the primary action if there is one, otherwise `Back`. While a delete can be undone, the right slot shows `Undo`; otherwise it holds nothing, or the screen's forward action.

So `New Sidetick | Undo`, `Compose | Undo`, `Back | Undo`. Undo is an offer, not a mode: it is added and never substituted, it always lands on the right, and it never removes a capability. Where a screen has both `Back` and a primary, the primary wins the left slot and the exit button steps aside for the five seconds — the system back still leaves, and the exit returns with the primary. The old takeover shape is gone, and no bar grows past two actions because of Undo.

Implemented as `SideAction.undo` + `undoBar(continueWith, undo)` (`design/action/UndoBar.kt`), replacing four private helpers; `UndoConventionTest` fails on a hand-built Undo action. The offer is also scoped in time and place: it runs for five visible seconds, pauses while its screen is off-screen, and is committed when that screen or app is left.

**F2 — Is the action bar or the list the primary control surface?** (~8 vs ~9 vs ~8)

| Camp | Apps | Feel |
|---|---|---|
| Bar-driven | Camera, Video, Calculator, Clock timer, Banking lock/pay, Photos viewer, Music Now Playing, Web address | body nearly inert; the bar is the product |
| Row-driven | Settings, Apps, Sideport menus, Music library, Trip, Files, Mail, Phonebook, Vault list | bar holds one `Back`; rows do everything |
| Mixed | Banking, Files, Mail, Phonebook, Photos grid, Translate | rows navigate, bar commits |

Consequence: the same two-slot bar sometimes carries the whole interaction and sometimes just an exit. If Sidekit's identity is "two buttons at the bottom", row-driven screens are off-brand; if rows are the interface, the bar should shrink to a navigation affordance deliberately.

**F3 — Which channel carries a transient message?** (4 vs ~7)

| Camp | Apps |
|---|---|
| Bar takeover (`notice` action, dismiss by tap) | Files (`:64`), Mail (`:148`), Translate (`:80`), Web (`:145`) |
| Body card / row / status string | Apps (`:59`), Sideport (`:143`), Banking (`NoticeCard`), Calendar (`error` row), Health (`message`), Compass (`status`), Sideticks (`error` row), Trip (`TripInfo`) |

The bar camp has the nicer property that the message is where the user is already looking, and the nasty property that it hides the exit for its duration.

**F4 — Is `Cancel` Muted or Primary?** ✅ **settled: Muted, always.**
23 sites, one factory. The grammar is now four colours with one meaning each: **sage advances** (Primary, the theme colour), **green commits**, **red destroys**, **grey takes it back** (Cancel, Undo, back-out, alternatives). `Cancel` is grey because it makes the same promise as Undo — nothing happened — and naming it in the bar must never read as a way forward. A themed confirm ("Keep note", "Keep file") stays grey while dismissing in place. `Back` is deliberately *not* Cancel: it stays Primary, because it advances through the hierarchy and never discards typed work by itself. `SideAction.cancel` is the only constructor, and the guard test rejects any other tone on a `Cancel`/`Undo` label.

**F5 — What is Options *for*?** (navigation vs object actions)

| Camp | Apps |
|---|---|
| Navigation / modes | Calendar (Agenda, Today), Trip (Find From/To, journeys, departures), Music (New playlist), Clock (Alarm access), Sideticks (sort), Keypad mapping (automatic) |
| Object actions | Banking (Edit account), Photos (share, favourite), Vault (Delete note), Apps (App info, Uninstall), Web (bookmark, reload, zoom) |

Both are internally consistent; they conflict with each other. The rule has to be either "Options never navigates" or "Options is everything that doesn't earn a button", and the suite currently answers both.

**F6 — Is `ActionMotion` a contract or an option?** (26 % adoption)

78 of 294 actions set a motion. The three factories set it correctly; hand-built actions rarely do. Either motion is part of the language (then 74 % of the bar is mute and the direction arrows lie by omission), or it is decorative and the factories should stop implying a rule.

### 7.4 Tier D — complete outsiders (1–2 instances; safe to fix without a decision)

| Outsider | Why it is alone | Fix |
|---|---|---|
| **Camera** (`CameraScreen.kt:124`) | Three unlabelled icon-only *wide* buttons; error pushed into Options with a no-op handler | Use `SideAction.square`, put the error in a notice |
| **Translate swipe** (`TranslateScreen.kt:201`) | Only silent deletion in the suite (no undo, no confirm) | Route through the F1/F3 decision |
| **Files / Vault swipe confirm** (`FilesScreen.kt:187`, `VaultGraph.kt:107`) | The only two swipe-to-confirm screens against a 10-app undo majority | Align after F1 |
| **Sideticks** (`SideticksScreen.kt:64,99`) | Primary action first; `Clear All Ticked` is a plain row with no tone/confirm/undo; the only app whose bar order is reversed | Adopt the Tier A order; route clear through the delete contract |
| **Arrange Home** (`ArrangeHomeScreen.kt:95`) | Only 3-wide bar with `Done` in the middle and reset last; only Settings screen using in-body line buttons | Commit last, reset first |
| **Mail Compose** (`MailGraph.kt:192`) | Only 3-wide text form; the only `Muted` Save; no green Send | Two slots + Options, or make the third shape official |
| **Calculator** (`CalculatorScreen.kt:74,160`) | Only right-aligned title; a private `CalcKey.color()` colour language parallel to `ActionTone` | Keep the title quirk, map keys onto tones |
| **Calendar** (`CalendarEditor.kt:82,93`) | `Delete` with no tone, `Save` with no tone, delete reachable only through Options without confirmation | Tone pass |
| **Health / Settings / Placeholder** | Only apps with no Options anywhere (dead Options key) | Give each one entry or accept the dead key |
| **Bank Up sync** (`UpBankScreen.kt:48`) | Only connector action without an icon | Icon pass |
| **Launcher** (`LauncherScreen.kt:207`) | Only user of `trailingAction`; clock is gesture-only; dock-as-bar is a one-off idea | Document as the pattern, not the exception |
| **Maps mock vs live** (`MapsScreen.kt:55` vs `MapsLive.kt:53`) | Green Go in previews, blue Go in the app | Generate previews from the live action list |

### 7.5 The decision order

```
F2 (bar vs rows)  ──► F3 (message channel) ──► F5 (what Options is for) ──► F6 (motion)
        │
        └────► Tier D fixes (Camera, Translate, busy states, dead Options keys)

F1 (undo shape) and F4 (cancel tone) are decided and shipped, so they no longer gate anything.
```

F2 sets how much the bar has to carry; F3 decides what may temporarily replace a `notice`; F5 decides what is allowed to live in the escape hatch. Only then do the icon, confirmation and busy-state fixes become mechanical, and most of §6's recommendations collapse into three or four edits in `design/`.

## 8. Quick verdict

The shared chrome is unusually disciplined for a suite this size: one action type, one layout engine, one Options contract, one focus/press language, no dialogs. Where apps diverged they diverged in **vocabulary, not architecture**. Two of those vocabulary questions are now answered in code — destructive/commit tone, and where Undo sits relative to Cancel — so the remaining forks are structural: **whether the action bar or the list is the primary control surface** (F2), **which channel carries a transient notice** (F3), and **what Options is for** (F5), plus whether `ActionMotion` is a contract (F6).

**Highest-value remaining fix:** one notice channel (F3), because three implementations of the same idea sit on the most-used screens (Files, Web, Mail, Banking) and the bar-vs-body split is the last thing that makes two apps feel different after a save.

**Highest-value second fix:** end the fake busy states (Calendar `Saving…`, Vault `Working…`, Trip/Bank/Mail labels that re-fire), because those are the only remaining places where the bar lies about what a tap will do.

---

## 9. Current state (branch `fix/action-tone-pass`)

**Bar grammar.** Two slots. Left = the continuation (the primary action if the screen has one, else `Back`); right = the next thing, which while a delete can be undone is `Undo`.

**Undo.** `SideAction.undo` owns the look (Muted, undo glyph, `Up` motion, a description naming what it restores). `undoBar(continueWith, undo)` decides placement and returns one action or two — never three, never zero. All 11 undo sites use it; the four private `withUndo` copies are gone.

**Undo window.** The five seconds are *visible* time, not wall clock: `UndoWindow` (`ui/UndoWindow.kt`) pauses on `ON_STOP`, resumes with what is left, and `UndoWindowLifecycle` cancels it when its screen is disposed. The offer belongs to the screen where the delete happened — leaving for another screen, tab, conversation or app commits it, so it never reappears with an unknown remainder and never restores something out of sight. Sidemessage keys the pending delete to its thread (the bar could previously appear in a different conversation and pull a message back there). Because Home pops the app's nav entry back to the launcher in Sidekit, leaving to the launcher commits rather than pauses: the safety net covers the visible moment, not the app being closed.

**Capability.** A pending undo never removes a capability: `New`, `Compose`, `Pay`, `Add`, `New tab` keep their slot; where the screen also had `Back`, the exit button is what steps aside for five seconds. Clock scopes the window to the Alarms tab and Trip to the list the delete happened on, so navigating can no longer blank a tab or evict `Save` from an editor. A draft keeps its two buttons and shows no Undo.

**Tones.** Primary = the theme colour, and only actions and the colour picker use it. Secondary = commit (`save`, `send`). Destructive = every delete/clear/remove/uninstall (22 of 22). Muted = `cancel`, `undo`, back-out, alternatives. Content emphasis reads `emphasisA1..3` (fixed dark green), so nothing in the body changes colour with the theme.

**Cancel.** Always Muted, 23 sites, `SideAction.cancel`; a themed confirm passes `icon = null, motion = Up` to stay grey while dismissing in place. Navigation stays Primary.

**Guards.** `ActionToneConventionTest` (destructive/commit/cancel/undo labels), `UndoConventionTest` (bar shapes, undo appearance, no hand-built Undo) and `UndoWindowTest` (visible-time pause/resume/expiry) run in `:app:testDebugUnitTest`; the first two caught live drift while the passes were being written.

**Still open, in the order §7.5 recommends:** F2 bar vs rows → F3 notice channel → F5 Options scope → F6 motion → Tier D outliers (Camera shapes, Translate's silent history delete, Vault's busy state, dead Options keys, Arrange Home's commit slot, Sideweather's `Locate`). Files/Vault's swipe-to-confirm still disagrees with the 10-app swipe-to-undo majority.
