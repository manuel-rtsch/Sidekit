# Sidetracks — Provenance and Clean-Room Record

**Purpose:** record where the Sidetracks specification and implementation came from, so the
project can show that no GPLv3 code from the Sidespot reference application entered Sidekit.

Companion: `docs/spotify/SPEC.md`. Licensing and native decisions:
`SIDESPOT_SIDEKIT_PLAN.md` §3 and §5.

---

## 1. The two licences that matter

| Project | Licence | How Sidetracks uses it |
|---|---|---|
| **Sidekit** | MIT (`/LICENSE`, © 2026 Harlen Postill) | Sidetracks is part of Sidekit and ships under MIT. |
| **librespot** | MIT | Used directly as a pinned upstream submodule. Copyright and licence notice preserved; see §6. |
| **Sidespot** | GPLv3 | **Not used.** No source, resources, build files, or documentation are copied, vendored, linked, or consulted during implementation. |

MIT is compatible with the MIT dependency. Because no Sidespot code is reused, Sidekit does not
acquire GPLv3 obligations.

---

## 2. Roles

| Role | Responsibility | May read Sidespot source? |
|---|---|---|
| Specification author | Write `SPEC.md` from public behaviour, public documentation, and Sidekit itself. | **No** |
| Implementer (Kotlin/native) | Implement from `SPEC.md` and `SIDESPOT_SIDEKIT_PLAN.md`. | **No** |
| Reviewer | Check that no copied material entered the tree. | Only for the similarity review in §5, after implementation, and must not feed observations back into implementation. |

One person may hold more than one role, but the clean-room rule is the same: **the implementer
works from the specification, not from the other app's source.**

---

## 3. Sources consulted for this specification

| Source | What was taken |
|---|---|
| `SIDESPOT_SIDEKIT_PLAN.md` | Scope, licensing strategy, architecture, milestones, native surface. |
| Sidekit source (this repository) | Design law (§2 of the spec), component APIs, existing screen shapes, naming, test harnesses. |
| `docs/ui-patterns-report.md`, `docs/ui-patterns-issue.md` | Action-bar grammar, options contract, focus/feedback rules, known divergences to avoid. |
| Spotify public platform documentation | Item types, library/search capability names, OAuth flow shape, `spotify:` URI form. |
| librespot public documentation/API | Capability boundaries referenced by the plan (metadata, session, player, sink). |
| Publicly observable behaviour of Spotify clients | The general shape of "library, search, now playing, queue" — no specific client's layout, strings, or assets. |

**Date of specification:** 2026-11-08.

**Not consulted:** the Sidespot repository (source, resources, build files, documentation), or
any fork of it. No Sidespot screenshots, strings, or assets were used.

---

## 4. Clean-room rules for implementation

1. Do not add the Sidespot repository as a Git remote, submodule, package, or source dependency.
2. Do not copy source files, identifiers, symbol names, comments, error strings, drawables,
   layouts, or build configuration from Sidespot.
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

- [ ] No Sidespot file names or directory names in the tree.
- [ ] No `sidespot`, `com.sidespot`, or `SideSpot` identifiers in code, resources, comments, or
  test names (`git grep -i sidespot` should match only this document and the plan's licensing
  text).
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

When the native library is added:

- Copy librespot's MIT licence text into the repository (e.g. `native/spotify/LICENSE-librespot`)
  and reference it from the app's notices/about screen if Sidekit has one.
- Pin the librespot commit in `native/librespot` and record it in `docs/spotify/LIBRESPOT_PATCHES.md`
  together with any patch.
- Do not describe Sidetracks as an official Spotify client. Document the account/protocol risk
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
