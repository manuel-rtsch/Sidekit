# Issue: Align UI patterns and action bar behavior across sub-apps

An audit of the action bar, button types, action triggers, and system responses across the 25 sub-apps identified several inconsistencies. While most of the suite agrees on basic mechanics, settled conventions are currently mixed with unresolved product questions and one-off component deviations.

Sorting out these patterns will help maintain a consistent user experience and make future work smoother.

## Summary — read this first

Twenty-five sub-apps, one action bar, and no shared answer to a handful of small questions: which tone a delete gets, where `Undo` appears, whether a message replaces the bar, and what belongs in Options. Each app answered on its own, so the same intent ends up looking different depending on which screen you are on.

The drift is two different kinds of work, and they must not be mixed up:

- **Unresolved product questions** — does `Undo` take over the bar or add a button, and is the bar or the list the primary control surface. These split the suite roughly in half, so any fix applied first just moves the inconsistency.
- **Real outliers** — one or two instances each, safe to fix mechanically once the questions are answered.

The facts worth knowing before reading further:

- **Deleting is defended three ways:** `Undo` (10 apps), bar confirmation (Files, Vault), or nothing at all (Translate).
- **Tone is applied inconsistently:** 17 destructive actions are red, 5 are not — including Calendar's `Delete` and Apps' `Uninstall`, neither of which asks for confirmation. 20 of 31 commit actions are green, the rest are plain Primary.
- **`Undo` either removes the exit or grows the bar to three:** 4 apps vs 6, and `Cancel` is Muted in 8 apps and Primary in 8.
- ~~**Four private copies of the same notice/undo helper**~~ — settled: one shared `withNotice` / `SideAction.notice`, guarded by `NoticeConventionTest`.
- **Busy state is faked:** `Saving…`/`Working…` are clickable no-ops in Calendar and Vault; in Trip, Bank Up and Mail Account the busy label keeps its live handler and re-fires.

Risk, in order: Calendar/Apps delete without confirmation, Translate's silent history delete, and the four screens where a pending `Undo` hides the way out. Everything else is consistency, not safety.

**What is needed:** settle the remaining decisions (§3 — F2, F5, F6), then run the mechanical passes: tones, outliers, shared helpers, lint guards (§5). F1 (undo shape), F3 (message channel) and F4 (cancel tone) are settled. If you only read one more section, read §3.

---

## 1. Key Inconsistencies Across Apps

- **Deletion & Confirmation:** Swipe-to-delete provides `Undo` in 10 apps, while Files and Vault require a `Cancel | Remove` bar confirmation. Translate deletes history with neither undo nor confirmation.

- **Destructive Tones:** 17 destructive actions use the red tone. Five do not: Calendar `Delete` (in Options), Calculator clear-history confirm, Apps `Uninstall`, Vault Options `Delete` (though its bar `Delete` _is_ red), and Sideticks "Clear All Ticked" (a plain list row with no confirmation).

- **"Cancel" Visual Weight:** "Cancel" is Muted in eight apps (Files, Banking, Photos, Sidenotes, Web, Sideport, Vault, Translate) but Primary in eight others (Calendar, Calculator, Clock, Keypad mapping, Sideticks, Banking Pay screen, Vault "Keep editing").

- **Commit Actions & Tones:** 20 commit actions use green with a check icon. Six omit the green tone or icon (Calendar, Health, Compass, Trip, Vault, Mail `Send`). "Allow" permissions alternate arbitrarily between green (Photos/Files/Video) and blue (Camera/Phonebook/Sidemessage/Health).

- **Back Button Navigation:** Back buttons take five different shapes across the suite. Multiple screens use a left arrow pointing at deeper destinations (e.g., Mail `←Folders`, Health `←History`, Web `Tabs`, Translate `←History`, Trip `←Saved`).

- **Undo Behavior:** Four apps (Banking, Mail, Web, Sidenotes) replace `Back` with `Undo | Main` when an action is pending, preventing users from exiting the screen. Six other apps append `Undo`, growing the bar to three buttons.

- **Message Delivery Channels:** ~~Apps currently use three separate methods to display transient messages (bar takeover notices, body cards/rows, or bare status strings). Four private implementations of notice helpers exist.~~ Settled: transient messages use the bar notice; body cards/rows are persistent explanation only.

- **Options Menu Scope:** Options is inconsistently split between navigation/modes and object destruction/actions. Three screens hide their _only_ destructive action in Options without confirmation; four others confirm the same action in the bar.

- **Fake Busy States:** `Saving…` and `Working…` are implemented as clickable buttons with empty handlers in Calendar and Vault. In Trip, Bank Up, and Mail Account, the busy label keeps its active handler, allowing re-triggers. No component supports a true visual disabled state.

- **Input & Component Drift:** Keypad focus is lost when moving between fields in several forms (Mail, Banking, Calendar, Sideticks). Icon-only button shapes vary inconsistently between `square` and wide formats.

## 2. How the Behaviors Split Today

The same behaviours, counted across the suite. Each line is a behaviour with its camps; the size of the camp is the argument for the recommendation that follows.

- **Bar grammar.** Two slots with exit left and commit right in about 20 of 25 apps. Three wide buttons in six places (Camera, Mail compose, Sideticks, Maps, Phonebook detail, Arrange Home, Web). One button only in the browse screens of Photos, Apps, Music library and Vault list.

- **Bar or rows.** Bar-driven in eight apps (Camera, Video, Calculator, Clock timer, Banking lock, Photos viewer, Music Now Playing, Web address), where the body is nearly inert. Row-driven in nine (Settings, Apps, Sideport menus, Music library, Trip, Files, Mail, Phonebook, Vault list), where the bar holds one `Back`. Mixed in the rest.

- **Back shape.** Wide labelled back is the default (73 uses of the factory). Square back in roughly twenty places, Muted only in Sideport and projected apps. "Save and go back" as a square in Sidenotes. Left arrow pointing at a deeper screen in Mail, Health, Web, Translate and Trip.

- **Undo, effect.** Takeover — `Undo | Main` and `Back` removed — in Banking, Mail, Web and the Sidenotes list (4). Appended, bar grows to three, in Clock, Compass, Sidemessage, Sideticks, Sideweather and Trip (6).

- **Undo, position.** Before the main action in Banking, Mail, Web and Sidenotes list (4). After it in Clock, Compass, Sidemessage, Sideticks and Trip (5). In the middle, `Back | Undo | Add`, in Sideweather (1).

- **Delete safety.** Swipe → delete + `Undo` in ten apps (Banking, Clock, Compass, Mail, Sidemessage, Sidenotes, Sideticks, Sideweather, Trip, Web). Swipe → `Cancel | Remove` in Files and Vault (2). Nothing in Translate (1). Bar-confirmed deletes elsewhere: Banking erase, Photos, Sidenotes editor, Clock editor, Web history, Sideport, Vault.

- **Commit tone.** Green with a check in 20 of 31 commit actions. Plain Primary in Calendar, Health, Compass, Trip and Vault; plain Primary plus a forward arrow in Mail compose. Permission `Allow` is green in Photos, Files and Video but blue in Camera, Phonebook, Sidemessage and Health.

- **Cancel tone.** Muted in eight apps, Primary in eight apps, listed under Key Inconsistencies.

- **Destructive tone.** Red in 17 actions, untinted in the five listed above. The swipe row paints its own red outside the tone system.

- **Transient messages.** Bar takeover in Files, Mail, Translate and Web (4). Body cards in Apps, Sideport, Banking and Trip. Body rows or status strings in Calendar, Health, Compass, Sideticks and Mail (4 private helper implementations).

- **Options role.** Navigation and modes in Calendar, Trip, Music, Clock, Sideticks and Keypad mapping (6). Object actions and destruction in Banking, Photos, Vault, Apps, Web and Sideport (6). Nothing at all in Health, Settings, Placeholder and the loading state of Web (4).

- **Busy state.** Button with an empty handler in Calendar and Vault (2, and Vault also drops `Back`). Busy label with a live handler in Trip, Bank Up and Mail Account (3). No visual disabled state anywhere.

- **Keypad capture.** Taken automatically by about 20 of 30 text fields. Not taken in Mail subject/body, Banking amount/note, Calendar times, Sideticks due-time and every ported-app form.

- **Icon-only buttons.** `square` in Photos, Video, Music home, Sideport, Phonebook detail and Banking Pay (23 uses). Icon-only but wide in Camera (3) and Music Now Playing (3).

- **Motion.** Set on 78 of 294 actions — mostly by the three factories; hand-built actions almost never set it.

## 3. Architectural Decisions to Settle

These splits should be decided first so that subsequent refactoring follows a single specification.

1. **Does `Undo` take over the bar or add a button?**
   - _Recommendation:_ Additive approach (`Undo` on the left, `Back` is never removed). Losing the exit path on a two-button bar must be avoided.

2. **Is the bar or the list the primary control surface?**
   - _Recommendation:_ The action bar owns commit and mode actions; body rows own list navigation.

3. ✅ **Where does a transient message go?** — settled: single-line transient messages use a bar notice; detailed or persistent explanations use a body card. Implemented as `withNotice` / `SideAction.notice`.

4. **Is "Cancel" always muted?**
   - _Recommendation:_ "Cancel" is always Muted (`grey` backs out, `green` commits, `red` destroys).

5. **What is Options for?**
   - _Recommendation:_ Options never handles primary navigation; it acts strictly on the current object.

6. **Is `ActionMotion` a real contract?**
   - _Recommendation:_ Enforce `ActionMotion` across all factory and hand-built action bars.

**Target Decision Dependency Chain:** `2 → 1` and `3 → 5 → 4, 6`.

## 4. Per-App Task Breakdown

Each app starts with a short note on the patterns it currently uses, then the concrete work.

### Banking & Finance

**Banking** — The busiest bar in the suite. Home is `←Accounts | Pay→`, becomes `Undo | Pay→` while an undo is pending (dropping the exit), or `Add account→` when empty. Pay and New account stay a single button until the form validates, then split into `Cancel | Record`. Erase is confirmed in the bar; account and transaction swipes delete immediately with `Undo`. Options hold account editing.

- [ ] Fix `Undo` replacing `Back` during pending operations (Decision 1).
- [ ] Keep the Pay screen's validate-then-split bar as the reference implementation for conditional bars.
- [ ] Unify account swipe-delete and "Erase" confirmation flows.
- [ ] Standardize `Cancel` button weight between the erase bar and the Pay screen.

### Messaging & People

**Sidemessage** — Home bar is state-driven: `Allow | New→`, then `Make default | New→`, then a single `New Message→`. Thread bar grows to three with `Send` and a suffix `Undo`. Options hold pin/attach/call. Swipe deletes with `Undo`.

- [ ] Keep only one route to "Make default": it is both a Muted bar action and an Options entry on the same screen.
- [ ] Move `Undo` to the left once Decision 1 is settled.

**Sidenotes** — List bar is `Undo | New`. Editor has three modes: wide `Back` + square `Delete`, square "Save and go back" + `Save`, and `Cancel | Delete` while confirming. Delete is also in Options. Swipe deletes with `Undo`.

- [ ] Reduce the three back affordances to one behaviour.
- [ ] Give the Options delete the same confirmation as the bar delete.

**Sideticks** — The only reversed bar order: main action first, then `Cancel`, then `Undo`. Options carry sort and due-time. Swipe deletes with `Undo`; "Clear All Ticked" is a plain row.

- [ ] Restore standard order (`Cancel`/`Undo` left, `Confirm` right).
- [ ] Route "Clear All Ticked" through the chosen delete contract with a destructive tone.

**Phonebook** — Mode-driven bars: `Clear | Call` on the dial pad, `Allow | Dial→` without permission, `Calls→ | Dial→` by default. Contact detail uses three actions with a square back. Everything else is rows.

- [ ] Move `Text→` from the detail bar into Options, or make the third shape official.
- [ ] Check the `weight = 1.6` Call button against the standard weights.

### Files, Vault & Storage

**Files** — The reference implementation: `Browse | Search→`, plus `Allow` when access is missing, plus a bar notice or `Cancel | Remove` depending on state. Options hold the sort modes. Swipe asks to confirm.

- [ ] Align the swipe confirmation with Decision 1 (currently the minority pattern).
- [x] Promote its private notice helper into the design system (`withNotice`).
- [ ] Promote its private confirm helper into the design system.

**Vault** — `Working…` is a no-op with `Back` removed. Locked bar `Home | Unlock`. In-bar confirms for delete and discard. Options `Delete` is untinted. Swipe asks to confirm. Keypad is captured while typing.

- [ ] Restore `Back` during `Working…` and make the busy state non-clickable.
- [ ] Tone the Options `Delete` red to match the bar button.
- [ ] Align the swipe confirmation with Decision 1.

### Calendar, Clock, Compass & Health

**Calendar** — `Day | New`, both untinted. Editor: `Cancel | Save`, `Saving…` with an empty handler, `Cancel | Delete` while deleting. Delete is only reachable through Options. Grid of tiles does the input.

- [ ] Tint `Save` green and `Delete` red.
- [ ] Move the unconfirmed Options delete into the standard confirm flow.
- [ ] Fix the fake `Saving…` button.

**Clock** — One bar per tab: `New alarm→`, `Pause/Resume | Reset`, with a suffix `Undo` after deletion. Alarm editor is `Cancel | Save`, or `Cancel | Delete` (Delete also in Options). Ringing screen is `Snooze | Dismiss`, both Primary.

- [ ] Establish hierarchy on the ringing screen — it currently has none.
- [ ] Tint editor `Cancel` Muted and `Save` green; confirm the Options delete.

**Compass** — `Calibrate | Save`, both Primary and untinted. Saved headings: `Back | Undo`. Feedback is a status string in the body. Options carry the permission action and bookmarks.

- [ ] Tint the commit and give it an icon.
- [x] Decide whether the status string becomes a bar notice (Decision 3) — it stays a body status because it is persistent.

**Health** — `History→ | Goals→` plus `Allow`. Goals screen is `Cancel | Save`, both untinted. History is a single `Back`. There is no Options anywhere.

- [ ] Add at least one Options entry, or accept the dead key explicitly.
- [ ] Tint the commit and move validation out of the message row.

### Mail, Translate & Web

**Mail** — One builder produces notice, then `Undo | Compose→`, then `←Folders | Compose→`; the Undo variant drops the back action. Message screen is `←Back | Reply`. Compose is the only three-wide text form, with a Muted `Save`. Only the `To` field takes the keypad.

- [ ] Resolve the three-wide compose bar (`Cancel | Save | Send`) and make `Send` the green commit or move `Save` to Options.
- [ ] Make subject and body take the keypad.
- [ ] Fix the left-arrow labels on deeper screens (`←Folders`).

**Translate** — `←History | Translate→` at rest, a single Muted `Cancel` while translating, and an error notice that replaces the whole bar. Options hold language pair, swap, large text and clear. History swipes delete with nothing.

- [ ] Add undo or confirmation to the history swipe — currently the only silent data loss.
- [ ] Keep `History` reachable while translating and while an error is shown.

**Web Browser** — Up to four bar entries: square back, `Tabs`, `Search→`, or a notice. Typing turns it into `Cancel | Go`. Tabs `Page | New tab→`. Bookmarks `Undo | ←Tabs`. History `Cancel | Clear`. Loading publishes no actions and no options.

- [ ] Enforce a density limit on bar entries.
- [ ] Fix the dead Options key and empty bar on the loading state.
- [ ] Unify `Cancel | Clear` across Tabs, Bookmarks and History.

### Camera, Photos, Music & Video

**Camera** — Publishes raw chrome, then three unlabelled icon-only wide buttons: `Photos | Shutter | Record`. While recording it collapses to one button whose label is the elapsed time. Errors are pushed into Options. Permission gate is a one-button bar.

- [ ] Convert the three wide icon buttons to `square`.
- [ ] Handle errors outside Options entries that do nothing when clicked.
- [ ] Stop re-rendering the bar every second from the recording label.

**Photos** — Grid has one action (`Albums→`), albums have one (`Back`). The viewer publishes raw chrome with no title: wide `Back` + square favourite + square delete, with an in-bar confirm. Permission state replaces the entire action list.

- [ ] Check whether the viewer should keep a header (`header = null`).
- [ ] Consider album creation/rename from the albums bar.

**Music** — Home is a square album-art action plus `Search→`. Library screens are a single `←Music`. Now Playing has four buttons, none labelled and none toned. Options carry shuffle/repeat as state labels. Permission state bypasses the scaffold.

- [ ] Give Now Playing a shape and a primary: `square` transport controls plus one green action.
- [ ] Decide whether album art belongs in `trailingAction` rather than the action list.
- [ ] Check playlist creation: it currently exists only as an Options entry on the home screen.

**Video** — Gallery is square `Back` + `Allow` or `Open settings→`. Player is square `Back`, wide `Play/Pause`, square `Skip` — three shapes in one bar. Auto-hiding the controls empties the action list so the bar slides away.

- [ ] Standardize the player bar shapes.
- [ ] Confirm that the disappearing bar is intended rather than a side effect.

### Maps, Trip & Weather

**Maps** — Three wide buttons: `←Search | Go | Locate`, the third being a permission action whose label changes. Options are plain zoom commands with no state. The preview screen uses a green `Go`, the live screen a blue one.

- [ ] Reduce to two buttons or move `Locate` into Options.
- [ ] Show state in the zoom Options, and generate previews from the live action list.

**Trip Planning** — Every page is `Back` plus an untinted commit (`Save`, `Add`, `Refresh`, `Replan`). `Undo` is appended to the right of the primary. Options do nearly all navigation. Location lists run three wide buttons.

- [ ] Tint the commit; settle the Undo position with Decision 1.
- [ ] Decide whether navigation stays in Options (Decision 5).

**Sideweather** — `←Hourly | 5 days→`, then `Back | Locations→`, then `Back | Undo | Add` with Undo in the middle. Options carry locations and the unit toggle. Swipe deletes with `Undo`.

- [ ] Move `Undo` to the left so the bar stops shuffling on delete.
- [ ] Show selection state on the unit toggle.

### Settings, Apps & Utilities

**Settings** — `←Home | About→`, sub-screens are a single `Back`, and rows mutate their value in place. Arrange Home runs `←Settings | Done→` with `Reset` in Options. The only other user of in-body line buttons is Sideport.

- [x] Move `Done` to the right in Arrange Home. (Done: `Reset` moved to Options.)
- [ ] Decide whether sub-screens get an Options entry or an explicit "nothing here".

**Apps** — Single `←Home`. Long-press selects a row, then Options offers `App info` and `Uninstall`. Nothing shows the selection. `Uninstall` is untinted and unconfirmed.

- [ ] Add a visible selected state for long-press.
- [ ] Tone and confirm `Uninstall`, or route it through the platform dialog deliberately.

**Calculator** — The bar is the keypad: `Clear | =`, both untinted. History is a single `Back`; clear-history confirms as `Cancel | Clear` in Primary. The title is the only right-aligned one. Keys use their own `CalcKey.color()` palette.

- [ ] Tint the clear-history confirm red.
- [ ] Decide whether `CalcKey` colours merge with `ActionTone`.

**Sideport** — Own screens follow the house style: `←Home | Add app→`, `Cancel | Remove` in red, square Muted `Back` + `Open→`, and a chat bar with green `Send`. Projected apps rebuild the bar from a foreign action spec with tones mapped one to one.

- [ ] Decide how far ported app bars should be normalized.
- [ ] Set keypad capture on ported forms.
- [ ] Align the in-body line-button styling with Settings.

**Launcher** — The dock is the action bar, so opening an app morphs the tapped tile into that app's first action. Search mode replaces the bar with a dense query chip plus a `trailingAction` clear button. Options toggle grid/list and open Settings. The clock responds to tap and long-press.

- [ ] Document the dock and the trailing action as the system pattern rather than a one-off.
- [ ] Surface the clock gestures somewhere visible.

**Placeholder apps** — No Options, no actions beyond `Back`; the Options key reports "No options here".

- [ ] Either give them a single Options entry or suppress the key.

## 5. Suggested Implementation Checklist

- [ ] **Decisions:** Record and confirm Architectural Decisions 1–6.
- [ ] **Design System:** Add native `enabled`/`busy` state to action models; centralize `withConfirm`; support `back(tone = …)`.
- [x] **Notice helper:** `withNotice` / `SideAction.notice` in `design/action/Notice.kt`, guarded by `NoticeConventionTest`.
- [ ] **Tone Pass:** Execute a pure styling pass to enforce commit (`green`) and destructive (`red`) tones without changing logic.
- [ ] **Outlier Pass:** Resolve specific component outliers (Camera button shapes, Translate delete flow, Vault busy state, dead Options keys).
- [ ] **Lint Guards:** Introduce linter checks for untinted destructive labels and non-square icon-only actions.
