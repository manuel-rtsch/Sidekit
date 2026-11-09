# Sidetracks

**Updated:** 2026-11-09
**Branch:** `feat/sidetracks`
**Phase:** feature-complete for daily use; pre-merge hardening and parity work remain

Sidetracks is Sidekit's Spotify sub-app: one account's library, search, playback, and queue,
drawn with Sidekit's chrome and operated keypad-first on a 480 × 640 phone. It is not a Spotify
client reproduction and not a web wrapper.

The implementation has moved past the porting/mockup stage. This document is the single source
of truth for what exists, what must not regress, and what happens next. Older clean-room specs
and status logs were folded into it; the full pre-consolidation text remains in Git history.

---

## 1. Guardrails

These are constraints, not preferences. A change that violates them is wrong even if it matches
Spotify.

- **Chrome belongs to the shell.** `SidekitScaffold` draws title, body, and action bar. No app
  bottom bar, FAB, tabs, mini-player, sheets, dialogs, toasts, or snackbars.
- **Action bar grammar.** Left = continuation (primary action, else Back), right = next/undo.
  Secondary actions live in keypad Options. Destructive actions swap the bar for confirmation.
- **The body is the control surface.** Play, save, and transport are tiles/rows; the bar navigates.
- **One column, explicit pagination.** No carousels, infinite scroll, or algorithmic shelves.
  Long lists end with a "Load more" row.
- **Keypad first.** Every interactive row is focusable, has a visible focus ring, and a 48 dp
  minimum target. Do not fade the focused element.
- **Content never wears the theme colour.** Selection, progress, and saved state use emphasis;
  the case colour belongs to primary actions.
- **No new dependencies** for images, DI, persistence, or media. Reuse `RemoteArtwork`,
  `PrivateSettings`, `NativeLibraries`, `MediaSessions`, plain repositories, and manual wiring.
- **No new icons** unless design decides; v1 needed none.
- **Explicit non-goal:** Spotify feature parity. Sidekit parity is the bar.

Rejected on purpose: algorithmic home feed, horizontal shelves, persistent mini-player, bottom
navigation, full-screen drag-dismiss player, queue bottom sheet, Spotify Connect device switching,
lyrics/Canvas/video, social features, account management, crossfade/EQ, and track-seeded radio.

---

## 2. Shipped

**Architecture**

- Playback: `SpotifySession` → `NativeSpotifyGateway` → `native/sidekit-spotify` (librespot 0.8.0)
  → `SpotifyAudioSink` (streaming `AudioTrack`).
- Catalog: `NativeSpotifyCatalog` reads through the authenticated librespot session
  (SpClient/Pathfinder). `SpotifyWebCatalog` remains a fallback when native is unavailable.
- UI: `SpotifyViewModel` maps catalog responses, queue/history repositories, and native player
  events into one immutable `SpotifyUiState`.
- Screens: Home, Library, Collection (album/playlist/show), Artist, Search, Now Playing, Queue,
  Settings, Sign in. Mock data is confined to the screenshot-test source set.

**Behaviour**

- Library, collection, artist, and grouped search reads use first-party endpoints, avoiding the
  public Web API quota and the old HTTP 429 failures.
- Native events (`loading`, `playing`, `paused`, `position`, `ended`, `unavailable`) drive Now
  Playing and advance the local queue exactly once. The one-second simulation runs only when
  native is absent.
- Library write-back is optimistic with rollback: like/unlike, save/remove album and show,
  follow/unfollow artist, add/remove playlist. Saves and follows offer Undo for five visible
  seconds; queue actions are silent.
- Playback is process-wide. Queue and local history live on `SpotifySession`; a recreated
  ViewModel adopts the live item, position, and playing state. Starting a new item releases the
  previous `AudioTrack`.
- Queue, queue history, shuffle order, repeat mode, and recently played persist across process
  death in atomic binary files (`sidetracks-queue.bin`, `sidetracks-history.bin`). Playback
  itself does not resume on launch.
- Foreground `mediaPlayback` service and platform `MediaSession` keep librespot alive with the
  screen off. Notification shows artwork, title, artist, and previous/play-pause/next. When the
  ViewModel is gone, next/previous fall back to advancing the process-wide queue directly.
  Tapping the notification opens Sidetracks directly at Now Playing rather than the launcher.
  A native `stopped` event clears Now Playing and the queue so notification/lock-screen stop
  cannot leave stale UI or restore the stopped track.
- Sidetracks and local Music pause each other through `MediaSessions`; Sidetracks takes and
  abandons `AUDIOFOCUS_GAIN`, including transient loss and headphone changes.
- OAuth tokens rotate into an existing native session. Sign-out stops the decoder synchronously,
  releases the media session, removes the notification, then clears credentials.
- Audio quality persists encrypted across process death and maps to 96/160/320 kbps. Playback
  volume is the device output volume; there is no separate attenuation.
- Artwork is memory-safe on device: 300 px URL preference, `RGB_565` + `inSampleSize`, 1024 px
  cap, `OutOfMemoryError` treated as a cache miss.
- Home's action bar opens the queue; track rows across the app expose Add to queue on left swipe.
  Upcoming and History rows in the queue swipe left to remove; removing queue history also drops
  the item from recently played. Library uses the standard single-line header; Settings has one
  sign-out action in the bar.
- Catalog lists page in 50-row chunks. Library, collection, artist, and search surfaces load the
  next page as their footer becomes visible, so a 202-track playlist reaches its last track without
  composing every row at once.

**Verified**

- `spotlessCheck`, `:app:lintDebug`, `:app:testDebugUnitTest`, `:app:assembleDebug` pass.
- APK contains `arm64-v8a` and `x86_64` `libsidekit_spotify.so`.
- On `SP01GE260600886` (480 × 640): libraries load real data, search returns Pathfinder tracks,
  playback advances/pauses correctly, show episodes format dates, and artwork no longer crashes.
- On `SP01GE260600886`: instrumented tests cover the foreground-service notification and actions,
  media-session metadata/state, media-owner hand-off, and the headphone-unplug pause path.
- JVM tests cover catalog page offsets, collection totals, and page boundaries.

---

## 3. Known limitations

1. **No recently-played protocol endpoint.** `NativeSpotifyCatalog.recentlyPlayed()` returns
   empty; Home falls back to local history.
2. **Local queue ownership.** librespot plays one item at a time; Sidekit drives queue, shuffle,
   and repeat. Queue, queue history, and recently played persist; playback does not resume
   automatically after process death.
3. **Playlist owner names.** Followed playlists show the owner's Spotify username.

---

## 4. What's next

Ordered by user-visible impact. Each item names its seam so it does not become another
cross-cutting refactor.

### P0 — device verification for daily use

- Verify token rotation on a session older than one hour; record the result in the device
  checklist.
- Verify library write-back on device: like/unlike, save/remove album and show, follow/unfollow
  artist, add/remove playlist; confirm in another Spotify client and after restart.
- Run the existing device checklist for lock screen, notification transport, audio-focus hand-off
  with local Music, and headphone unplug.

### P1 — parity and completeness

1. **Recently played.**
   - Choose a supported first-party history endpoint or keep local history only.
   - If the Web API is the only source, make it opt-in and degrade to local history when the
     quota is exhausted.
2. **Queue/shuffle/repeat ownership.**
   - Decide whether librespot player state or `SpotifyQueueRepository` is authoritative.
   - If Sidekit stays authoritative, mirror shuffle/repeat/autoplay into the native player and
     add an explicit native "Play next" path; otherwise delete the duplicate local state.
   - Acceptance: Now Playing matches what the native player is doing.
3. **Playlist owner names.** Resolve through the authenticated user profile and cache, not on
   every collection open.
4. **Test coverage.**
   - `NativeSpotifyCatalog` JSON parsing and error mapping (auth, network, malformed response).
   - `NativeSpotifyCatalog`/`SpotifyViewModel` event state machine, including `stopped`.
   - Device smoke checklist for sign-in, library filters, search, playback, queue, lock screen,
     and token expiry.
   - Acceptance: JVM tests run in CI; device checklist stays manual.

### P2 — polish

- Clear artwork and credential caches on sign-out; expose cache size in Settings.
- Accessibility/focus review for long playlist/show collections and the Library Options filter.
- APK-size review once the vendored librespot patch can be dropped.

---

## 6. Native build, provenance, and licensing

**Native bridge.** `native/sidekit-spotify` links librespot 0.8.0 and exposes the JNI surface
declared in `apps/spotify/bridge/NativeBridge.kt`.

- Toolchain: Rust stable, `cargo-ndk` 4.1.2, NDK 27.1.12297006, targets
  `aarch64-linux-android` and `x86_64-linux-android`.
- Gradle builds it with `:app:buildSpotifyNative` as part of `assembleDebug`/`assembleRelease`.
  If Rust or the NDK is missing, the task is skipped, the APK has no library, and
  `NativeLibraries.load("sidekit_spotify")` returns false.
- `Cargo.lock` pins the full graph. `vergen` is held at 9.0.6; do not update it without rebuilding
  the Android targets.
- Playback temp files use Android `cacheDir` (`NativeBridge.setTmpDir`).

**librespot patch.** One minimal in-tree patch is applied via
`native/vendor/librespot-core` and `[patch.crates-io]`:

- `src/config.rs`: `OS = "linux"` instead of `std::env::consts::OS`.
- Reason: the stored OAuth credential comes from Spotify's desktop/keymaster client id. Login5
  and client-token requests bind to the platform-implied client id; on Android librespot
  otherwise reports `INVALID_CREDENTIALS`/`BAD_REQUEST`.
- Scope: one constant. No protocol, playback, audio, or auth logic changes.
- Removal: delete the patch once librespot supports overriding platform/client identity, or once
  Sidetracks performs its own Android-client OAuth.
- Verify with `diff -rq ~/.cargo/registry/src/index.crates.io-*/librespot-core-0.8.0
  native/vendor/librespot-core`; only `src/config.rs` (plus registry bookkeeping) should differ.

**Licensing.** Sidekit and librespot are MIT; librespot's notice and `Cargo.lock` are retained.
Sidespot is GPLv3 and remains reference-only at the untracked `.sidespot/` path: never committed,
linked, vendored, or used as a build dependency. No Sidespot source, identifiers, comments,
assets, or documentation enter Sidekit.

**Clean-room boundary.** The implementation was written from Sidekit's design system, public
Spotify/librespot documentation, and observable behaviour. Before merge, run the similarity
review: no `sidespot` identifiers in committed code, no copied strings/assets/symbols, and no
structure that only matches the reference implementation. Record the reviewer and date here.
