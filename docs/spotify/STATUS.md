# Sidetracks implementation status

**Updated:** 2026-11-09
**Branch:** `feat/sidetracks`

## Architecture

- **Playback** — `SpotifySession` → `NativeSpotifyGateway` → `native/sidekit-spotify` (librespot
  0.8.0) → `SpotifyAudioSink` (streaming `AudioTrack`).
- **Catalog** — `SpotifyCatalog` is implemented by `NativeSpotifyCatalog`, which uses the same
  authenticated librespot session for SpClient/Pathfinder reads. `SpotifyWebCatalog` remains a
  fallback for builds without the native library.
- **UI state** — `SpotifyViewModel` maps catalog responses, the local queue/history repositories,
  and native player events into one immutable `SpotifyUiState`.

## Landed

- Mock-only content has been removed from production. `MockSpotifyDataSource` lives in the
  screenshot-test source set only.
- Library reads (liked songs, playlists, albums, artists, shows), collections, artist albums/top
  tracks, and grouped search go through librespot's authenticated first-party endpoints:
  - collection/rootlist SpClient calls;
  - Pathfinder `searchDesktop` with a context-resolve fallback;
  - SpClient metadata for tracks, albums, artists, shows, episodes, and playlists.
- This avoids the public Web API quota and removes the HTTP 429 failures that previously left the
  library, search, and collection screens empty.
- Native player events drive the Now Playing state: `loading`, `playing`, `paused`, `position`,
  `ended`, and `unavailable` update the seek bar and advance the Sidekit queue exactly once.
  The local one-second simulation now runs only when the native library is absent.
- Catalog calls clone the session/token before doing network work, so a long catalog request can
  no longer block playback-event polling behind the native state lock.
- The stored access token is refreshed into the native session before catalog calls when the OAuth
  token changes.
- Remote artwork is memory-safe on the 480×640 device: cover URLs prefer the 300 px variant, decode
  as `RGB_565` with `inSampleSize`, cap requested dimensions at 1024 px, and treat `OutOfMemoryError`
  as a cache miss. This fixes the SIGABRT seen while artwork loaded during back navigation.
- Podcast episode dates are returned as ISO dates by the native layer and rendered as
  `d MMM uuuu` instead of librespot's raw timestamp string.
- Rootlist playlists without a name are skipped; the user's own playlists are labelled `You`.
- Home → Options → Refresh reloads the account/library. Failed loads surface a notice rather than
  a permanent empty screen.
- Removed fake UI actions: copy-login-link, add-to-playlist, "load more" terminal rows and the
  mockup About copy.

## Verification

- `./gradlew :app:testDebugUnitTest` — passes.
- `./gradlew :app:assembleDebug` — passes; APK contains arm64-v8a and x86_64 `libsidekit_spotify.so`.
- On `SP01GE260600886` (480×640):
  - liked songs, playlist, album, artist, and show libraries load real data;
  - search returns tracks from Pathfinder;
  - a liked track starts playback, the position advances from native events, pause freezes it;
  - show collections render episodes with formatted dates;
  - no FATAL/AndroidRuntime crash after the artwork fix.

## Known issues

1. **No recently-played protocol endpoint.** `NativeSpotifyCatalog.recentlyPlayed()` returns an
   empty list, so Home falls back to Sidetracks' local listening history. A server-backed history
   would need a supported first-party endpoint or the Web API (and its quota).
2. Save/follow toggles are optimistic local state; they do not write back to Spotify yet.
3. Queue, shuffle and repeat are driven locally by Sidekit, not by librespot.
4. Playlist subtitles show the owner's Spotify username for playlists owned by other users.
5. No foreground Spotify playback service, media notification or audio-focus integration yet.

## Native notes

- `native/sidekit-spotify` is built with `cargo-ndk` (NDK 27.1.12297006).
- Playback temp files use the Android `cacheDir` (`NativeBridge.setTmpDir`) to avoid
  `PermissionDenied` under `/data/local/tmp`.
- One documented librespot-core patch is applied via `native/vendor/librespot-core`; see
  `docs/spotify/LIBRESPOT_PATCHES.md`.

## Remaining work

Ordered by user-visible impact. Each item names the seam it belongs to so the next change does not
turn into another cross-cutting refactor.

### P0 — required for a daily-usable build

1. **Foreground playback service + media notification.**
   - Wire `SpotifyPlayback` into a `Service` using the existing `MediaSessionOwner` ids
     (`sidetracks` / `0x6D76`) and the `MediaSession`/`PlaybackState` pattern from `MusicService`.
   - Request and abandon `AUDIOFOCUS_GAIN` explicitly; verify two-way hand-off with local Music.
   - Keep the librespot player alive when the activity is backgrounded or the screen is locked.
   - Acceptance: playback continues through Home/Back/screen-off; notification shows title,
     artist, play/pause, and next; local Music pauses Spotify and vice versa.

2. **Saved-state write-back.**
   - Like/unlike tracks, save/remove albums, follow/unfollow artists, save/remove shows, and
     add/remove playlists through the authenticated collection SpClient write endpoints.
   - Replace the optimistic `savedTrackIds` / `savedCollectionKeys` / `followedArtistIds` updates
     with optimistic updates plus rollback on failure.
   - Acceptance: changes survive an app restart and are visible in another Spotify client.

3. **Playback lifecycle hardening.**
   - Reconnect when the token or librespot session expires while the app is open.
   - Apply the Settings volume at session/player creation (currently `0.72` is UI state until the
     user moves the slider).
   - Map the Settings audio quality to `PlayerConfig.bitrate` instead of the fixed 320 kbps.
   - Acceptance: leave the app open past token expiry, start playback, and recover without a
     manual sign-out.

### P1 — parity and completeness

4. **Pagination.**
   - Add paged loading for liked songs, playlists, albums, artists, shows, playlist tracks, show
     episodes, artist albums, and search.
   - Current hard limits: collection 500, playlist tracks 100, show episodes 50, artist albums 50,
     search 20. Remove the silent truncation or surface "showing N of M".
   - Acceptance: a 202-track playlist can be scrolled to its last track.

5. **Recently played.**
   - Decide between a supported first-party history endpoint and keeping local history only.
   - If the Web API remains the only source, make it opt-in and degrade to local history when the
     quota is exhausted. Document the trade-off in `SPEC.md`.

6. **Queue/shuffle/repeat ownership.**
   - Decide whether librespot's player state or Sidekit's `SpotifyQueueRepository` is authoritative.
   - If Sidekit stays authoritative, mirror shuffle/repeat/autoplay into the native player and add
     an explicit "Play next" native path. Otherwise remove the duplicate local state.
   - Acceptance: the state shown in Now Playing matches what the native player is doing.

7. **Playlist owner names.**
   - Followed playlists still show the owner's username in collection headers. Resolve it through
     the authenticated user profile endpoint and cache it, rather than fetching on every open.

8. **Test coverage.**
   - Unit-test `NativeSpotifyCatalog` JSON parsing and error mapping (auth, network, malformed
     response).
   - Unit-test the `NativeSpotifyCatalog`/`SpotifyViewModel` event state machine (`loading`,
     `playing`, `paused`, `position`, `ended`, `unavailable`).
   - Add a device smoke checklist for sign-in, library filters, search, playback, queue, lock
     screen, and token expiry.
   - Acceptance: the above run in CI without the device; device checklist remains manual.

### P2 — polish

9. Clear artwork and credential caches on sign-out and expose cache size in Settings.
10. Accessibility/focus review for long playlist/show collections and the Options filter bar.
11. APK-size review after the vendored librespot patch is no longer needed.
