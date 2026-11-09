# UI Coherence — Remaining Work

**Status:** working branch `feat/ui-coherence`, based on `integration` at `84fa60a` (which includes `main` at `3debe06`).
**Source:** the UI pattern audit in `docs/ui-patterns-report.md` and `docs/ui-patterns-issue.md` on this branch. This document extracts only the work that is still open so it can be scheduled and reviewed independently.

## Base

This branch now contains the settled UI work from `integration`: the two-slot bar grammar, `undoBar`, the tone conventions, the visible-time undo window, and the convention tests. It also contains the Sidetracks/Sideplay work that is currently on `integration`.

When `integration` lands on `main`, rebase this branch onto `main` before opening the PR so the diff contains only the coherence work.

## Already settled — do not reopen

- Two-slot bar grammar: left = continuation, right = the next thing; Undo sits on the right.
- Undo is owned by `SideAction.undo` / `undoBar`, with a visible-time window.
- Tones: Primary = theme colour, Secondary = commit, Destructive = delete/clear/remove, Muted = cancel/undo/back-out/alternatives.
- Cancel is always Muted.
- Guards: `ActionToneConventionTest`, `UndoConventionTest`, `NoticeConventionTest`, `UndoWindowTest`, `SurfaceConventionTest`.
- Arrange Home: `←Settings | Done→` with `Reset` in Options.
- Transient messages: `withNotice` / `SideAction.notice`; bar notice for transient feedback, body row for persistent explanation. Success notices auto-dismiss after 2 seconds, other notices after 5; tapping dismisses early.
- F2: rows navigate and change in-place settings; the bar acts and reports; Options holds secondary actions. No in-body line buttons in Settings or Sideport; `SurfaceConventionTest` guards it.

## Remaining work, in recommended order

### 1. F5 — Options scope

**Problem:** Options mixes navigation, creation, toggles, destructive actions, state, and error logs. Some screens have no Options at all (dead key); some hide their only destructive action there without confirmation.

**Scope:** all screens with Options; Health, Settings, Placeholder, the Sidenotes list, and the Mail folders list.

**Decision:** define what Options is for; require confirmation for destructive Options actions; decide whether every screen needs an entry.

**Acceptance:** a written Options contract; destructive Options actions route through the same confirm bar; dead Options keys are either given an entry or accepted and documented.

### 2. Fake busy states

**Problem:** Calendar `Saving…` and Vault `Working…` are clickable buttons with empty handlers. Trip, Bank Up, and Mail Account keep their active handler while showing a busy label, so the action can re-fire.

**Scope:** Calendar, Vault, Trip, Bank Up, Mail Account.

**Decision:** add a real disabled/busy state to `SideAction` (or a shared busy action) and use it.

**Acceptance:** busy actions cannot be re-triggered; no empty handlers; labels reflect real work.

### 3. F6 — motion contract

**Problem:** `ActionMotion` is used semantically but not enforced; some screens use it decoratively.

**Scope:** shared chrome and screens with custom motion.

**Decision:** make motion part of the action contract or document it as advisory.

**Acceptance:** either a test/guard or a documented rule; no contradictory motion.

### 4. Tier D outliers

- **Camera** — three unlabelled icon-only wide buttons; the error is in Options with a no-op handler. Use `SideAction.square`; move the error to a notice.
- **Translate** — silent history delete with no undo or confirm. Route it through the delete contract.
- **Vault** — busy state (see item 2).
- **Dead Options keys** — Health, Settings, Placeholder, Sidenotes list, Mail folders (see item 1).
- **Sideweather** — `Locate` in a three-action bar; decide the two-slot rule.
- **Files / Vault swipe-to-confirm** — align with the 10-app swipe-to-undo majority, or document the exception.
- **Mail Compose** — only 3-wide text form; only Muted Save; no green Send. Use two slots + Options, or make the third shape official.
- **Calculator** — private `CalcKey.color()` language; map keys onto tones.
- **Calendar** — Delete and Save have no tones; delete is only in Options without confirmation.
- **Bank Up sync** — the only connector action without an icon.
- **Launcher** — the only `trailingAction` user; document it as the pattern, not the exception.

### 5. Verification

- Run `./gradlew spotlessCheck :app:testDebugUnitTest`.
- Add or extend a guard for any new contract.
- Update `docs/ui-patterns-report.md` §9 and the `docs/ui-patterns-issue.md` checklists as items land.

## Checklist

- [ ] Rebase onto `main` once `integration` lands
- [x] F3 notice channel
- [x] F2 bar vs rows rule
- [ ] F5 Options contract
- [ ] Fake busy states
- [ ] F6 motion contract
- [ ] Tier D outliers
- [ ] Files / Vault swipe confirm
- [ ] Update the report and issue docs
