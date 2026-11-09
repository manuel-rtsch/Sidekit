# Sideplay — Provenance and Clean-Room Record

**Purpose:** record where the Sideplay specification and implementation came from, and how the
Sidespot reference application is kept outside Sidekit's version-controlled source, so no GPLv3
code enters Sidekit.

Companion: `docs/spotify/SPEC.md`. Licensing and native decisions:
`docs/spotify/PLAN.md` §3 and §5.

---

## 1. The two licences that matter

| Project | Licence | How Sideplay uses it |
|---|---|---|
| **Sidekit** | MIT (`/LICENSE`, © 2026 Harlen Postill) | Sideplay is part of Sidekit and ships under MIT. |
| **librespot** | MIT | Used directly as a pinned upstream submodule. Copyright and licence notice preserved; see §6. |
| **Sidespot** | GPLv3 | **Reference only.** Included locally at `.sidespot/` as a clean-room implementation reference, but excluded from Git and never committed. Not linked, imported, or shipped. |

MIT is compatible with the MIT dependency. Because Sidespot is never committed or linked into
Sidekit, and no Sidespot code is copied into the implementation, Sidekit does not acquire GPLv3
obligations.

---

## 2. Roles

| Role | Responsibility | May read the `.sidespot/` reference? |
|---|---|---|
| Specification author | Write `SPEC.md` from public behaviour, public documentation, and Sidekit itself. | **No** |
| Implementer (Kotlin/native) | Implement from `SPEC.md` and `docs/spotify/PLAN.md`. | **No** |
| Reviewer | Check that no copied material entered the tree. | Only for the similarity review in §5, after implementation, and must not feed observations back into implementation. |

One person may hold more than one role, but the clean-room rule is the same: **the implementer
works from the specification, not from the reference application's source.** The `.sidespot/`
directory exists so a reviewer can compare behaviour and check for copying; it is not an
implementation input.

---

## 3. Sources consulted for this specification

| Source | What was taken |
|---|---|
| `docs/spotify/PLAN.md` | Scope, licensing strategy, architecture, milestones, native surface. |
| Sidekit source (this repository) | Design law (§2 of the spec), component APIs, existing screen shapes, naming, test harnesses. |
| `docs/ui-patterns-report.md`, `docs/ui-patterns-issue.md` | Action-bar grammar, options contract, focus/feedback rules, known divergences to avoid. |
| Spotify public platform documentation | Item types, library/search capability names, OAuth flow shape, `spotify:` URI form. |
| librespot public documentation/API | Capability boundaries referenced by the plan (metadata, session, player, sink). |
| Publicly observable behaviour of Spotify clients | The general shape of "library, search, now playing, queue" — no specific client's layout, strings, or assets. |

**Date of specification:** 2026-11-08.

**Not consulted while writing the specification:** the Sidespot repository (source, resources,
build files, documentation), or any fork of it. No Sidespot screenshots, strings, or assets were
used.

**Reference availability:** `.sidespot/` is present in the working tree for review and behavioural
comparison. It is listed in `.gitignore`, is never staged or committed, and is not a Git remote,
submodule, package, or build dependency. Delete it at any time without affecting the build.

---

## 4. Clean-room rules for implementation

1. Keep `.sidespot/` untracked: it is in `.gitignore`, is never committed, and must not be added
   as a Git remote, submodule, package, source dependency, Gradle include, or Cargo path.
2. Do not copy source files, identifiers, symbol names, comments, error strings, drawables,
   layouts, or build configuration from Sidespot. Consult the specification, not its source.
3. Do not copy its design document. `docs/spotify/SPEC.md` is written from Sidekit's design system
   and public behaviour; where it differs from another client, **Sidekit wins**.
4. JNI symbols follow Sidekit's own scheme:
   `Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_*`. No `sidespot`/`com.sidespot`
   identifiers anywhere, including tests, logs, and comments.
5. Package layout follows the plan (`hrln.interactive.sidekit.apps.spotify`), not another app's.
6. Library and class names are chosen from Sidekit vocabulary (`SpotifyGateway`, `SpotifyQueue`,
   `SpotifyPlaybackService`), not translated from another codebase.
7. Native code is written against librespot's public API. If librespot needs a patch, it is a
   minimal upstream-compatible patch documented in `docs/spotify/LIBRESPOT_PATCHES.md`, not a
   copy of a third-party fork.
8. Do not reproduce another client's screen order, icon choices, or copy text. The spec's screens
   are derived from Sidekit's own Music app and shared chrome.
9. Provenance questions are resolved **before** merging, not after.

---

## 5. Similarity review (run before merge)

- [ ] `.sidespot/` is absent from `git ls-files` and appears in `.gitignore`.
- [ ] No `sidespot`, `com.sidespot`, or `SideSpot` identifiers in committed code, resources,
  comments, or test names (`git grep -i sidespot` should match only provenance/plan licensing
  text). The untracked `.sidespot/` directory is excluded from this check.
- [ ] No identical JNI symbol names, class names, or private helper structure where an
  independent design would plausibly differ.
- [ ] No copied comments, log lines, error strings, or user-facing copy.
- [ ] No drawable, font, or audio asset of unclear origin.
- [ ] Queue/search/auth logic is explainable from `SPEC.md` and librespot's API alone.
- [ ] librespot `LICENSE` and attribution present in the repository and any distributed notice.

Record the result:

| Date | Reviewer | Result | Notes |
|---|---|---|---|
| | | | |

---

## 6. Third-party notices

The native library is `native/sidekit-spotify`:

- librespot 0.8.0 is consumed from crates.io and pinned by `native/sidekit-spotify/Cargo.lock`.
  Its MIT licence is copied to `native/sidekit-spotify/LICENSE-librespot`.
- No librespot patches are applied, so there is no `docs/spotify/LIBRESPOT_PATCHES.md`.
- Do not describe Sideplay as an official Spotify client. Document the account/protocol risk
  from the plan (§3, "Spotify account risk").

---

## 7. Attestation

Complete at each hand-off (spec → implementation → merge):

```text
Change:        ______________________________________________
Role:          [ ] specification author   [ ] implementer   [ ] reviewer
Date:          ______________________________________________
I confirm that:
  - I did not read or copy Sidespot source, resources, or documentation
    while producing this change.
  - The work was derived from docs/spotify/SPEC.md, Sidekit's own code,
    librespot's public API, and Spotify's public documentation.
  - I found no copied identifiers, strings, or assets in the change.
Signed:        ______________________________________________
```

---

## 8. Change log

| Date | Change |
|---|---|
| 2026-11-08 | Initial spec and provenance record; Sidekit MIT confirmed; `MediaSessionOwner`, `NativeLibraries`, `RemoteArtwork`, `SecretCipher` landed as upstream seams. |
| 2026-11-09 | Sidespot moved to untracked `.sidespot/` as a review-only clean-room reference. It is gitignored and never committed; no Sidespot source or assets enter Sidekit. |
| 2026-11-08 | Added the clean-room librespot 0.8.0 native crate, pinned by `Cargo.lock`, with upstream MIT notice. No Sidespot native code used. |
