# Sidekit Spotify Sub-App — Planning Document

**Status:** Draft plan — Milestone 0.5 landed; UI mockup delivered; SPEC and PROVENANCE drafted  
**Working name:** Sidetracks (temporary; internal package remains `spotify`)  
**Target workspace:** `/home/manuel/Projects/Sidekit`  
**Reference application:** `.sidespot/` (behavior reference only)  
**Native dependency:** librespot (MIT), used directly  
**Goal:** Add Spotify playback, library browsing, search, and queue management to Sidekit as a first-class sub-app, implemented with Sidekit's design system and architecture.

---

## 1. Decision

Build the sub-app with this split:

| Area | Approach |
|---|---|
| Spotify protocol, session, audio, metadata | Reuse **librespot** directly. |
| JNI bridge and native Android glue | **Write new code** against the librespot API. |
| Kotlin Spotify management layer | **Clean-room reimplementation** using new interfaces and Sidekit conventions. |
| UI | **Build entirely new** with Sidekit components and navigation. |
| Sidespot source code | **Do not copy, vendor, or link.** |

This avoids carrying Sidespot's GPLv3 implementation into Sidekit while keeping the expensive, well-tested part: librespot and the Spotify protocol.

This is not a tiny wrapper around Sidespot. It is a new Sidekit app with a new native bridge, a new process model, and a new UI. The work is substantial, but well bounded.

---

## 2. Why This Strategy

### Reuse librespot

librespot already provides:

- Spotify authentication and session management
- Spotify Connect/player protocol
- Audio fetching and decryption
- Track, album, artist, playlist, show, and episode metadata
- Search
- Saved-library operations
- Playback events and volume control

That is the risky, protocol-heavy part of a Spotify client. Reimplementing it would be unnecessary and fragile.

librespot is MIT-licensed, so it can be used in Sidekit without forcing Sidekit to adopt GPLv3. Preserve its copyright and license notice.

### Clean-room the management layer

The Kotlin layer still needs:

- OAuth flow and credential storage
- Session and player lifecycle
- Queue, shuffle, repeat, autoplay
- Library and search repositories
- Playback history
- Foreground playback service and media session
- Error handling and recovery
- UI state

Those concerns are application-specific. Writing them fresh gives us:

- Sidekit-compatible lifecycle and state management
- No dependency on Sidespot's unmaintained patterns
- No GPL contamination from Sidespot's Kotlin or Rust code
- Freedom to use Kotlin coroutines/Flow, Sidekit `PrivateSettings`, and Sidekit UI primitives
- Better testability through explicit repository interfaces

### No direct port of Sidespot UI

Sidespot's UI is Material 3 with bottom navigation, a mini-player, and a full-screen playback overlay. Sidekit has different chrome, navigation, focus behavior, and screen constraints. Reusing those screens would import exactly the inconsistencies we want to avoid.

---

## 3. Licensing and Clean-Room Rules

### Licenses

- **librespot:** MIT. Include the license text and attribution in the repository and any distributed notice file.
- **Sidespot:** GPLv3. Do not copy its Kotlin, Rust, resources, or documentation into Sidekit.
- **Sidekit:** MIT (root `LICENSE`, © 2026 Harlen Postill). Recorded here as the confirmed license; no change is required, and Spotify sub-app code and the new native bridge are contributed under the same MIT terms. Because no Sidespot code is reused, MIT remains compatible with the MIT librespot dependency and no GPLv3 obligation is introduced.

### Clean-room implementation rules

1. Do not add the Sidespot repository as a Git remote, submodule, or source dependency.
2. Do not copy source files, identifiers, comments, strings, drawables, or build files from Sidespot.
3. Do not copy Sidespot's `DESIGN.md` into Sidekit. Produce a new feature specification from:
   - publicly observable app behavior,
   - Spotify's public platform documentation,
   - librespot's public API and examples,
   - Sidekit's existing design system.
4. The person implementing the Kotlin/native bridge should work from the new specification, not from open Sidespot source.
5. If strict legal clean-room is required, use two roles:
   - a **specification author** who may inspect behavior and public documentation,
   - an **implementer** who has not read Sidespot source and works only from the specification.
6. Record provenance in `docs/spotify/PROVENANCE.md`:
   - date,
   - author,
   - sources consulted,
   - confirmation that no Sidespot code was copied.
7. Before merging, run a review for suspicious similarity:
   - no matching file names,
   - no copied JNI symbol names,
   - no copied comments or error strings,
   - no identical private helper structure where an independent design would differ.

> Practical note: a feasibility audit has already looked at Sidespot's structure. Treat those findings as high-level architectural observations only. The implementation must be independently written and must not reproduce Sidespot source.

### Spotify account risk

A third-party Spotify client can break when Spotify changes its protocol, and use may conflict with Spotify's terms. This risk is inherited from librespot and cannot be eliminated by this plan. Document it and test against a dedicated account where possible.

---

## 4. Target Architecture

```text
Sidekit/
├── app/
│   └── src/main/java/hrln/interactive/sidekit/apps/spotify/
│       ├── SpotifyGraph.kt
│       ├── SpotifyScreens.kt
│       ├── SpotifyViewModel.kt
│       ├── SpotifyHomeScreen.kt
│       ├── SpotifySearchScreen.kt
│       ├── SpotifyNowPlayingScreen.kt
│       ├── SpotifyQueueScreen.kt
│       ├── SpotifySettingsScreen.kt
│       ├── auth/
│       │   ├── SpotifyAuth.kt
│       │   └── SpotifyCredentialStore.kt
│       ├── bridge/
│       │   ├── NativeBridge.kt
│       │   ├── NativeModels.kt
│       │   └── PlaybackEvent.kt
│       ├── data/
│       │   ├── SpotifySession.kt
│       │   ├── SpotifyApi.kt
│       │   ├── SpotifyLibraryRepository.kt
│       │   ├── SpotifySearchRepository.kt
│       │   ├── SpotifyQueueRepository.kt
│       │   └── SpotifyHistoryRepository.kt
│       ├── playback/
│       │   ├── SpotifyPlayback.kt
│       │   ├── SpotifyPlaybackService.kt
│       │   ├── SpotifyAudioSink.kt
│       │   └── PlaybackCoordinator.kt
│       └── ui/
│           ├── RemoteArtwork.kt
│           └── SpotifyRows.kt
├── native/
│   ├── librespot/              # upstream submodule, pinned
│   └── spotify/
│       ├── Cargo.toml
│       ├── build.rs
│       └── src/
│           ├── lib.rs
│           ├── jni_api.rs
│           ├── session.rs
│           ├── player.rs
│           ├── metadata.rs
│           ├── library.rs
│           ├── audio.rs
│           ├── events.rs
│           ├── models.rs
│           └── error.rs
└── docs/spotify/
    ├── SPEC.md
    ├── PROVENANCE.md
    └── TEST_PLAN.md
```

Use package namespace:

```text
hrln.interactive.sidekit.apps.spotify
```

Native library name:

```text
libsidekit_spotify.so
```

JNI symbols:

```text
Java_hrln_interactive_sidekit_apps_spotify_bridge_NativeBridge_*
```

Do not retain `com.sidespot` or `sidespot` package names.

### 4.1 Upstream files this app needs (Milestone 0.5)

These are app-agnostic Sidekit changes, not Spotify code. They are listed here so the tree above is understood as *additive* to `app/`, not self-contained.

| Path | Change |
|---|---|
| `apps/media/MediaSessionOwner.kt` | New neutral interface: session id, channel id, notification id, `isActive`, `pause()`. |
| `apps/media/AudioFocus.kt` | Extended into a process-level arbiter granting focus to one owner at a time. |
| `apps/media/NativeLibraries.kt` | New guarded loader exposing availability instead of throwing from `init`. |
| `apps/music/MusicPlayback.kt` | Implements `MediaSessionOwner`; local `PlaybackState` stays app-private. |
| `apps/music/MusicService.kt` | Uses the session/notification registry instead of hardcoded constants. |
| `apps/integrations/KeystoreCipher.kt` | Moved from `apps/banking/BankingRepository.kt`; `PrivateSettings` follows. |
| `design/RemoteArtwork.kt` *or* dependency decision | Remote HTTPS artwork with memory + disk cache, placeholders, cancellation. |
| `navigation/SubApp.kt`, `navigation/SidekitNavHost.kt` | `Sidetracks` tile immediately after `Music` and `sidetracksGraph(...)` wiring, together, in Milestone 2. |

---

## 5. Native Layer

### 5.1 librespot source

Prefer the official upstream project:

```text
https://github.com/librespot-org/librespot
```

Pin a specific release tag or commit in `native/librespot`. Do not automatically track a branch.

If librespot requires a modification for Android or for exposing internals:

- keep the patch as small as possible,
- document it in `docs/spotify/LIBRESPOT_PATCHES.md`,
- prefer an in-tree patch or a dedicated Sidekit fork over inheriting an unrelated fork,
- record the upstream commit and patch reason.

Do not reuse Sidespot's `librespot-local` fork unless the same change is independently required and can be justified as an upstream-compatible patch.

### 5.2 Native modules

| Module | Responsibility |
|---|---|
| `session.rs` | Create/connect/disconnect a librespot session; expose connection state. |
| `player.rs` | Create/configure player, load/play/pause/stop/seek/volume, queue prefetch hooks. |
| `audio.rs` | Custom librespot sink delivering PCM to Kotlin or native AudioTrack. |
| `events.rs` | Bridge librespot player events into a pollable/queued event stream. |
| `metadata.rs` | Track, album, artist, playlist, show, episode, search, and paging queries. |
| `library.rs` | Liked songs, saved albums/shows/playlists, followed artists, playlist mutation. |
| `models.rs` | JSON-serializable DTOs shared with Kotlin. |
| `jni_api.rs` | Thin JNI entrypoints only; no business logic. |
| `error.rs` | Stable error codes and messages safe to surface to Kotlin. |

### 5.3 JNI surface

Design the JNI API around operations, not around Sidespot's existing method names.

Suggested v1 surface:

```text
nativeInit(tmpDir)
setLogLevel(level)

sessionConnect(credentialsJson) -> result
sessionDisconnect()
sessionIsConnected()
sessionGetAccessToken() -> token

playerCreate()
playerDestroy()
playerLoad(uri, startPositionMs, autoplay)
playerPlay()
playerPause()
playerStop()
playerSeek(positionMs)
playerSetVolume(volume)
playerGetVolume()
playerSetShuffle(enabled)
playerSetRepeatMode(mode)

pollPlaybackEvent(timeoutMs) -> jsonOrNull

registerAudioSink(callback)
unregisterAudioSink()

metadataGetTrack(uri)
metadataGetAlbum(uri)
metadataGetArtist(uri)
metadataGetPlaylist(uri)
metadataGetShow(uri)
metadataGetShowEpisodes(uri, offset, limit)
metadataSearch(query, types, limit)
metadataGetLikedSongs(offset, limit)
metadataGetUserPlaylists(offset, limit)
metadataGetSavedAlbums(offset, limit)
metadataGetSavedShows(offset, limit)
metadataGetFollowedArtists(offset, limit)
metadataGetAutoplayTracks(seedUri, limit)

librarySaveAlbum(uri)
libraryUnsaveAlbum(uri)
librarySaveShow(uri)
libraryUnsaveShow(uri)
libraryFollowArtist(uri)
libraryUnfollowArtist(uri)
libraryAddToLikedSongs(uri)
libraryRemoveFromLikedSongs(uri)
libraryCreatePlaylist(name, description)
libraryAddToPlaylist(playlistUri, trackUri)
```

Add or remove methods only after the spike confirms librespot's capabilities.

### 5.4 Concurrency model

- One process-wide Spotify session and player.
- Guard native mutable state with `Mutex` or an equivalent.
- All JNI methods must be safe from arbitrary Kotlin threads.
- Metadata calls are blocking; Kotlin calls them on `Dispatchers.IO` with bounded concurrency.
- Playback events are delivered through a queue:
  - librespot event thread pushes into the queue,
  - Kotlin drains with a dedicated coroutine or blocking poll on an IO dispatcher,
  - no UI-thread polling.
- Audio callback must avoid blocking the librespot decode thread indefinitely.
- Establish a single owner for player lifecycle; do not create a player per screen.

### 5.5 Audio output

Start with the simplest reliable approach:

- librespot custom `Sink` receives decoded PCM.
- JNI callback delivers a byte array to Kotlin.
- Kotlin writes to `AudioTrack` in streaming mode.
- Configure sample rate/channels from librespot's negotiated `AudioFormat`, not from a hardcoded guess.
- Handle `AudioTrack` recreation after route changes or invalidation.
- Apply backpressure so the decode thread is not starved or allowed to race far ahead.

If JNI allocation or `AudioTrack` latency causes glitches, evaluate a native AAudio/Oboe sink as a follow-up. Do not start with Oboe unless the simple sink fails acceptance testing.

### 5.6 Native error model

Return structured JSON:

```json
{
  "ok": false,
  "code": "SESSION_NOT_CONNECTED",
  "message": "Connect to Spotify before loading a track.",
  "retryable": false
}
```

Kotlin should map codes to user-facing messages. Do not expose raw librespot debug strings directly in the UI.

---

## 6. Kotlin Domain Layer

### 6.1 Core interfaces

Keep the UI dependent on interfaces, not JNI:

```kotlin
interface SpotifyGateway {
    suspend fun connect(credentials: SpotifyCredentials)
    suspend fun disconnect()
    suspend fun load(uri: String, positionMs: Long, autoplay: Boolean)
    suspend fun play()
    suspend fun pause()
    suspend fun seek(positionMs: Long)
    suspend fun setVolume(volume: Int)
    suspend fun search(query: String, types: Set<SpotifyItemType>): SpotifySearchResults
    suspend fun likedSongs(offset: Int, limit: Int): SpotifyPage<SpotifyTrack>
    suspend fun savedAlbums(offset: Int, limit: Int): SpotifyPage<SpotifyAlbum>
    // ...
}
```

`NativeSpotifyGateway` implements this with `NativeBridge`. A fake implementation supports unit tests and screenshot tests.

### 6.2 Session

`SpotifySession` owns:

- native initialization,
- connect/disconnect,
- access-token retrieval for Web API calls,
- connection state as `StateFlow`,
- cleanup when the user signs out.

It must not be owned by a screen ViewModel. It should live at application/process scope, alongside the existing music playback infrastructure.

### 6.3 Authentication

Use librespot's OAuth support.

Sidekit already has the storage primitive this needs: `apps/integrations/PrivateSettings` writes to `noBackupFilesDir` through `AtomicFile` and encrypts with a non-exportable Android Keystore key. `SpotifyCredentialStore` should use it directly rather than introducing a second secret store. The cipher it depends on, `KeystoreCipher`, currently lives in `apps/banking/BankingRepository.kt`; promoting it to a shared infrastructure package is one of the upstream seams (Milestone 0.5).

Requirements:

- Open the system browser with `Intent.ACTION_VIEW`.
- Prefer a loopback redirect handled by the app; validate the exact redirect mechanism supported by the pinned librespot version.
- Handle the no-browser case with a clear fallback, such as copyable URL and manual callback entry.
- Never collect the Spotify password inside the app.
- Store credentials encrypted at rest.
- Store only the minimum reusable credential/token material.
- Clear credentials on sign-out and on unrecoverable auth failure.

`SpotifyCredentialStore` must be Keystore-backed (via `PrivateSettings`). Do not store Spotify tokens in plain `SharedPreferences`.

### 6.4 Library and search repositories

`SpotifyLibraryRepository`:

- cached page results,
- refresh and invalidation,
- saved-state mutations with optimistic update and rollback on failure,
- mapping native DTOs to UI models,
- pagination tokens.

`SpotifySearchRepository`:

- debounced queries,
- cancellation of stale searches,
- grouped results: tracks, albums, artists, playlists, shows, episodes,
- empty and error states.

`SpotifyHistoryRepository`:

- recently played tracks with timestamps,
- bounded storage,
- atomic writes,
- clear-history action.

### 6.5 Queue

`SpotifyQueueRepository` owns:

- current item,
- upcoming items,
- history/previous items,
- shuffle order,
- repeat mode: off / context / track,
- "play next" and "add to queue",
- autoplay append when the queue ends.

librespot generally plays one item at a time; Sidekit must drive the queue. The repository should expose an immutable queue snapshot and a declarative `advance()` operation. Keep this independent from the UI.

### 6.6 Playback history and persistence

Use Sidekit-style atomic file persistence or an existing Sidekit store abstraction. Do not introduce Room unless Sidekit already uses it and the volume of history justifies it.

Persist:

- encrypted credentials,
- playback history,
- preferred audio quality/volume,
- last queue only if it can be restored safely.

Do not persist transient playback position on every tick.

---

## 7. Playback Service and Process Lifecycle

### 7.1 Service

`SpotifyPlaybackService` is a foreground service with:

- `mediaPlayback` foreground service type,
- a `MediaSession` compatible with system media controls,
- play/pause/next/previous/seek,
- album art and metadata,
- notification channel separate from local Music,
- correct cleanup on task removal and sign-out.

### 7.2 Process-wide playback owner

`SpotifyPlayback` is a process-scoped singleton responsible for:

- native session/player lifetime,
- queue advancement,
- event collection,
- audio focus,
- media-session state,
- service start/stop coordination.

It must survive Activity recreation and Sidekit navigation. Screens observe it; they do not own it.

### 7.3 Coordination with Sidekit's local Music app

Sidekit already has `MusicService`, `MusicPlayback`, and local-file playback. For v1, keep the two providers separate but mutually exclusive:

- starting Spotify pauses local Music,
- starting local Music pauses Spotify,
- only one active media session at a time,
- distinct notification IDs and channels,
- no shared queue state.

Introduce a small `PlaybackCoordinator` interface for this. Do not attempt a unified local/remote media model in the first release; that would expand the scope into Sidekit's existing music app and its tests.

The interface cannot be added cleanly at the last minute: `MusicPlayback` is a private-constructor process singletons that creates its own `MediaSession`, `AudioFocus`, notification channel, and `PlaybackState` typed around the MediaStore `Track`. The neutral `MediaSessionOwner` boundary and the audio-focus arbiter described in §12, Milestone 0.5 must exist *before* Spotify playback code is written, otherwise the sub-app imports music types and the seam is never cut.

A later phase may unify both behind a `MediaProvider` abstraction, but only after the Spotify app is stable.

### 7.4 Audio focus and interruptions

Handle:

- audio focus gain/loss,
- transient duck/pause,
- headphone unplug,
- Bluetooth route changes,
- incoming calls,
- network loss during playback,
- Spotify session expiry.

These behaviors must be tested on a device, not only in unit tests.

---

## 8. UI and Information Architecture

### 8.1 Screens

All screens use Sidekit's scaffold, typography, colors, focus handling, and action system.

| Screen | Contents |
|---|---|
| **Spotify home** | Now-playing summary, recently played, playlists, saved albums, followed artists, podcasts, liked songs. |
| **Search** | `SidekitTextField`, debounced results, grouped result rows. |
| **Playlist/Album/Show** | Header art, metadata, play action, track/episode list. |
| **Artist** | Artist header, top tracks, albums, related artists if available. |
| **Now Playing** | Artwork, title/artist, seek row, transport controls, shuffle/repeat, queue action. |
| **Queue** | Current item, upcoming items, history, play-next/add-to-queue actions. |
| **Library** | Saved items and followed artists, with filters if needed. |
| **Settings** | Audio quality, normalization, sign-out, about, cache/history controls. |
| **Sign-in** | Explanation, "Log in with Spotify", auth status, retry/error. |

### 8.2 Sidekit-specific interaction rules

- No Material bottom navigation.
- No persistent mini-player overlay. Use a now-playing row on the app home and a transport action in the action bar.
- Use `SideAction` and the existing options mechanism rather than custom floating buttons.
- Every interactive row must have a focus state and at least a 48dp touch target.
- Long lists must remain usable with D-pad/Sundial input.
- Respect Sidekit's compact 480×640 layout; do not port Sidespot's larger-screen spacing.
- Use Sidekit's `SidekitList`, `SidekitItem`, `SidekitTile`, `SidekitScaffold`, and `SidekitIcon` where possible.
- Add new icons only if the existing set cannot express the action. Prefer `PlayArrow`, `Pause`, `SkipNext`, `SkipPrevious`, `Shuffle`, `QueueMusic`, `Album`, `Artist`, `MusicNote2`, `Favorite`.

### 8.3 Artwork

Decided and implemented (Milestone 0.5 item 5): `apps/media/RemoteArtwork.kt`.

Sidekit's `Thumbnails` helper is for local `content://` images and stays that way. Remote artwork uses:

- `RemoteArtwork.load(context, url, w, h)`: HTTPS fetch through `HttpURLConnection`, memory LRU (1/32 of heap, shared), disk cache under `cacheDir/artwork` keyed by a SHA-256 of url+size, trimmed to 16 MB oldest-first, at most two concurrent fetches, 10 s timeouts, 8 MB response cap, cancellation that disconnects the socket, bounds + `inSampleSize` decode then crop to RGB_565.
- `RemoteArtworkImage(url, width, height, placeholder = SidekitIcon.Album)`: draws the bitmap in the draw phase and shows the placeholder icon until it arrives or if it never does. Requests cancel when the row leaves composition.
- Failures are not cached, so scrolling back retries. `RemoteArtwork.clear(context)` supports sign-out / "clear cache".

No Coil/Glide/OkHttp is added. The component is app-neutral; Music can adopt it later for remote sources but must not regress `Thumbnails` for local files.

### 8.4 Navigation

Register the app through Sidekit's existing conventions:

1. Add `Sidetracks("Sidetracks", SidekitIcon.QueueMusic)` to `navigation/SubApp.kt` immediately after `Music`. The constant name sets the route (`app/sidetracks`), so it and the label move together if the temporary name changes.
2. Add a `sidetracksGraph(navController)` function in `apps/spotify/`.
3. Register it in `SidekitNavHost` in the same commit as the enum entry, so the tile is never a dead route.
4. No Home tile work: the launcher reads `SubApp` directly. Do not add a dock entry.
5. Add screenshot tests for the new screens and regenerate launcher baselines once, in that same commit.

Use nested routes for home → detail → track list and home → search → detail. Keep navigation depth within Sidekit's established limits.

---

## 9. Domain Models

Define new Sidekit-owned models. Do not reuse Sidekit's local `Track` model, which is MediaStore-specific.

Suggested models:

```kotlin
data class SpotifyTrack(
    val uri: String,
    val name: String,
    val artists: List<SpotifyArtistRef>,
    val album: SpotifyAlbumRef?,
    val durationMs: Long,
    val artworkUrl: String?,
    val explicit: Boolean,
    val isPlayable: Boolean,
    val isEpisode: Boolean,
)

data class SpotifyAlbum(...)
data class SpotifyArtist(...)
data class SpotifyPlaylist(...)
data class SpotifyShow(...)
data class SpotifyEpisode(...)

data class SpotifyPage<T>(
    val items: List<T>,
    val nextOffset: Int?,
    val total: Int?,
)

data class SpotifySearchResults(
    val tracks: List<SpotifyTrack>,
    val albums: List<SpotifyAlbum>,
    val artists: List<SpotifyArtist>,
    val playlists: List<SpotifyPlaylist>,
    val shows: List<SpotifyShow>,
    val episodes: List<SpotifyEpisode>,
)
```

Use immutable state and Kotlin `Flow`/`StateFlow` for UI consumption. Use `kotlinx.serialization` for JNI payloads instead of introducing Gson.

Handle nullable fields deliberately: Spotify metadata is inconsistent, especially for local files, unavailable tracks, and podcast episodes.

---

## 10. Build Integration

### 10.1 Rust toolchain

Required:

- Rust stable with Android targets:
  - `aarch64-linux-android`
  - `x86_64-linux-android` for emulator support
- `cargo-ndk`
- Sidekit's Android NDK version

### 10.2 Gradle tasks

Add a task that:

1. Runs `cargo ndk` for the requested ABI(s).
2. Writes `libsidekit_spotify.so` into the generated native-libs directory.
3. Makes `merge*NativeLibs` depend on it.
4. Is configuration-cache compatible.
5. Is skipped cleanly when Rust is unavailable, with a clear diagnostic.

Sidekit currently uses a single `externalNativeBuild { cmake { ... } }` block for `libsidekit_emulator` in `app/build.gradle.kts` (`ndkVersion = "27.1.12297006"`). The Rust task must coexist with it as a separate native build step; do not try to force Cargo through CMake.

Note that `EmulatorCore` calls `System.loadLibrary("sidekit_emulator")` unconditionally in `init`. There is no graceful "native unavailable" convention in the codebase yet; establishing one is part of Milestone 0.5 so that an ABI without `libsidekit_spotify.so` degrades to a message rather than a crash.

### 10.3 ABI and packaging

- Device release: `arm64-v8a`.
- Emulator/debug: `x86_64` if feasible.
- If an ABI lacks the library, the app must show a clear "Spotify playback is unavailable in this build" state instead of crashing at `System.loadLibrary`.
- Verify 16 KB page-size alignment for Android 15+ compatibility.
- Check APK size impact and native symbol stripping.

### 10.4 Reproducibility

- Pin the librespot commit.
- Pin Rust dependencies with `Cargo.lock`.
- Document the exact NDK and Rust versions.
- Avoid machine-specific paths such as a hardcoded Homebrew NDK location.
- Add a CI job that builds the native library for at least `arm64-v8a`.

---

## 11. Testing Strategy

Sidespot has no test suite. This sub-app should meet Sidekit's standards instead.

### Unit tests

- Queue advancement, shuffle, repeat, autoplay.
- Search debounce and stale-result cancellation.
- Library optimistic updates and rollback.
- Playback state reducer/event mapping.
- Credential storage behavior with a fake Keystore.
- Pagination and deduplication.
- Error-code to user-message mapping.

### Fake gateway tests

Create `FakeSpotifyGateway` for:

- ViewModel tests.
- Screenshot tests without network or Spotify credentials.
- Empty, loading, error, and long-list states.

### Screenshot tests

Use Sidekit's existing screenshot infrastructure for:

- Spotify home with and without a current track.
- Search results.
- Album/track list.
- Now Playing.
- Queue.
- Sign-in and auth error.
- Settings.
- D-pad focus states and selected rows.

### Native tests

- Rust unit tests for JSON DTOs and error mapping.
- A device/instrumentation smoke test that initializes native code and verifies failure behavior without credentials.
- Do not require a real Spotify account in CI.

### Manual device tests

- Login, token refresh, sign-out, login again.
- Play/pause/seek/next/previous.
- Background playback and notification controls.
- Screen off, lock screen, headphones, Bluetooth.
- Incoming call/audio focus loss.
- Network loss and recovery.
- Process death and relaunch.
- Local Music ↔ Spotify mutual pause.
- Emulator build without the native library.

### Acceptance criteria for v1

- A user can sign in with Spotify.
- Library, playlists, saved albums, liked songs, and search load.
- Tracks and episodes play through the device audio output.
- Playback continues in the background with a working media notification.
- Queue, shuffle, repeat, seek, and volume work.
- Now Playing is fully usable with D-pad/Sundial input.
- Sign-out clears credentials and stops playback.
- Local Music and Spotify do not play simultaneously.
- No crash occurs when the native library is unavailable.
- The app follows Sidekit screen/navigation/design conventions.
- Tests cover the queue, repositories, ViewModels, and key screens.

---

## 12. Milestones

Estimates are order-of-magnitude for one experienced Android/Rust developer. Clean-room implementation is slower than copying Sidespot code.

### Milestone 0 — Decisions and specification (2–4 days)

- ~~Confirm Sidekit licensing.~~ Done: Sidekit is MIT (`LICENSE`), and the sub-app ships under it.
- Confirm app name and package.
- Write `docs/spotify/SPEC.md`.
- Write clean-room provenance rules.
- Decide whether upstream librespot is sufficient or a small patch is needed.

### Milestone 0.5 — Upstream Sidekit seams (2–4 days)

Small, app-agnostic changes to Sidekit that the Spotify app depends on. Land these before any Spotify code; each one is cheaper now than after a second playback owner exists. No file in this milestone may mention Spotify beyond a neutral interface.

**Branch:** `refactor/media-session-seams` (branched from `integration`).
**Gate:** `./gradlew :app:testDebugUnitTest` green; existing Music tests and screenshot baselines unchanged.

1. ✅ **Playback ownership boundary.** New `apps/media/MediaSessionOwner.kt`: `MediaSessionOwner` (`sessionId`, `notificationChannelId`, `notificationChannelName`, `notificationId`, `isActive`, `pause()`) and `MediaSessionRegistry` + process-wide `MediaSessions`. Activating one owner pauses every other playing owner. `MusicPlayback` implements it and claims/releases the session in `publish()`; `MusicPlayer` and the track-shaped `PlaybackState` are untouched, so Music UI sees no change.
2. ✅ **Audio focus.** Android already arbitrates: a second `AUDIOFOCUS_GAIN` request delivers `AUDIOFOCUS_LOSS` to the previous holder, whose `AudioFocus` listener pauses it. With the registry making in-process order deterministic, a separate focus-arbiter class would duplicate `AudioManager` and risk changing Music's audible behaviour. **Decision: no arbiter class.** The requirement for Spotify is only that it uses `AudioFocus` with `AUDIOFOCUS_GAIN` and calls `abandon()` when it stops; verify the two-way transfer on device (Milestone 1/4).
3. ✅ **Service/notification seams.** `MusicService` no longer owns `CHANNEL`/`NOTIFICATION_ID` constants: it reads `notificationChannelId`, `notificationChannelName`, and `notificationId` from the owner and creates the channel from them. One-active-session enforcement lives in the registry, not in the service.
4. ✅ **Secret store promotion.** `KeystoreCipher` and the cipher interface moved to `apps/integrations/SecretCipher.kt` (`SecretCipher` + `KeystoreCipher`). Banking keeps `typealias BookCipher = SecretCipher`; the default alias remains `sidekit_banking` so existing sealed files open. `PrivateSettings` now imports from its own package.
5. ✅ **Remote image decision.** Decision: Sidekit-owned bounded cache, no new image dependency. New `apps/media/RemoteArtwork.kt`: memory LRU sized like `Thumbnails`, 16 MB disk cache in `cacheDir/artwork` keyed by url+size and trimmed oldest-first, two concurrent fetches with 10 s timeouts and an 8 MB cap, cancellation disconnects the socket, bounds + `inSampleSize` decode cropped into RGB_565. `RemoteArtworkImage` composable with a Sidekit icon placeholder. Spotify rows use this; Music keeps `Thumbnails`.
6. ✅ **Native availability convention.** New `system/NativeLibraries.kt` (`load`/`isAvailable`, caches success and failure, thread-safe). `EmulatorCore` exposes `available` instead of throwing from `init`, and `EmulatorActivity` shows "not available in this build". Spotify's bridge must use the same loader — never `System.loadLibrary` from an initialiser.
7. ✅ **Registration conventions.** Decided (see §14, Resolved launcher placement): label **Sidetracks** (temporary), enum constant `Sidetracks`, route `app/sidetracks`, grid position immediately after `Music`, not in the dock, `designed = true`. `SubApp` remains the single source of launcher order, route (`"app/${name.lowercase()}"`), dock list, and placeholder filter; no enum refactor needed. In Milestone 2 the enum entry, `sidetracksGraph(navController)`, `SidekitNavHost` registration, and regenerated launcher baselines must land together so no placeholder tile is ever reachable. Note this couples display name to route: renaming the enum later changes the deep-link route, which is acceptable pre-release.

Deliberately **not** in this milestone: a unified `MediaProvider` abstraction, any change to the MediaStore `Track` model, or any Spotify-named type in shared code.

**Conditional for Spotify:** its playback owner must call `MediaSessions.register(...)` once and `activate/deactivate` as it starts and stops; its notification ids/channel must not collide with `0x6D75`/`"music"`. Its service should follow the same shape as `MusicService` rather than adding a parallel registry.

> Formatting note: `spotlessCheck` ratchets from `origin/main`, so it flags two pre-existing files on this branch (`build.gradle.kts`, `EmulatorCoreTest.kt`) that the seam work does not touch. The files changed here pass `spotlessKotlinCheck`.

### Milestone 1 — Native spike (4–7 days)

- Add librespot submodule.
- Build a minimal `libsidekit_spotify.so` for arm64.
- Load it from Sidekit.
- Connect a session.
- Play one track through `AudioTrack`.
- Receive playback events.
- Prove Gradle/Cargo/NDK integration.

**Exit criterion:** sound comes out of the device, and the build is repeatable.

### Milestone 2 — Kotlin skeleton and auth (1–1.5 weeks)

- Add `SubApp` entry and graph.
- Implement native bridge and DTOs.
- Implement encrypted credential storage.
- Implement OAuth browser flow.
- Add sign-in, loading, and error screens.
- Establish process-wide session ownership.

### Milestone 3 — Library and search (1.5–2 weeks)

- Fetch liked songs, playlists, saved albums, followed artists, saved shows.
- Implement pagination.
- Implement search with debounce/cancellation.
- Build Sidekit list screens and detail screens.
- Add remote artwork loading.
- Add repository and screenshot tests.

### Milestone 4 — Playback, queue, and service (2 weeks)

- Implement queue, shuffle, repeat, autoplay.
- Implement process-wide playback owner.
- Implement foreground service and media session.
- Add notification controls and audio focus.
- Add coordination with local Music.
- Build Now Playing and Queue screens.
- Add playback tests and device smoke tests.

### Milestone 5 — Parity and hardening (1.5–2.5 weeks)

- Saved-state mutations: like, save, follow, add to playlist.
- Podcast/show/episode support.
- History.
- Settings and sign-out.
- Network/error recovery.
- Offline and unavailable-track states.
- Screenshot baselines and accessibility/focus review.
- Performance and APK-size review.

### Overall estimate

- **Usable Spotify sub-app:** roughly **5–8 focused weeks** (plus the 2–4 day upstream seam milestone).
- **Full parity and Sidekit-quality hardening:** roughly **8–12 focused weeks**.

The largest schedule risk is not UI; it is debugging real Spotify playback, OAuth, background lifecycle, and native audio on the device.

---

## 13. Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| librespot API changes or Spotify protocol changes | Playback/metadata breaks | Pin librespot; isolate behind native modules; keep patches small; test early. |
| Clean-room contamination | GPL exposure | No Sidespot source in workspace; independent spec; provenance log; similarity review. |
| OAuth redirect/browser issues on Sidephone | Users cannot sign in | Spike early; support system browser and manual fallback; document redirect behavior. |
| Activity/process lifecycle kills playback | Music stops when navigating | Process-wide playback owner + foreground service; test process recreation. |
| Duplicate media sessions with local Music | Confusing controls, overlapping audio | Playback coordinator; mutual exclusion; unique notification channels. |
| Audio glitches or latency | Poor playback experience | Correct AudioTrack format and backpressure; native AAudio/Oboe only if needed. |
| arm64-only library | Emulator/CI failure | Build x86_64; graceful unavailable state; CI native build. |
| 16 KB page-size requirement | App rejected/crashes on new Android | Verify linker flags and packaging. |
| Spotify ToS/account risk | Account restrictions or breakage | Dedicated test account; document risk; do not encourage credential sharing. |
| Scope creep into unifying local/remote music | Delays v1 | Keep providers separate in v1; unify only after release. |
| Upstream seams deferred until after Spotify playback exists | Spotify imports Music types; refactor becomes a fork | Milestone 0.5 lands the `MediaSessionOwner`, focus arbiter and service registry first, with existing Music tests as the gate. |
| Spec written without checking current Sidekit code | Spec describes components that already exist or do not | SPEC.md cites real paths (`PrivateSettings`, `Thumbnails`, `MusicPlayback`, `MusicService`) and marks each as reuse, extend, or new. |
| Large native dependency size | APK bloat | Strip symbols, size-optimized release profile, check ABI splits. |

---

## 14. Open Decisions

Resolve before Milestone 2:

1. ~~**Display name:** "Spotify", "Sidespot", or another name.~~ Resolved: **Sidetracks**, temporary and subject to change. Display-only except for the route: `SubApp` entry `Sidetracks(...)` yields `app/sidetracks`. Package `hrln.interactive.sidekit.apps.spotify`, native `libsidekit_spotify.so`, and JNI symbols are unchanged. If the name changes later, rename the enum constant and label together (the route follows); no file path or JNI symbol changes. Icon: `SidekitIcon.QueueMusic` keeps it visually distinct from Music's `MusicNote2`; swap if design prefers another.
2. ~~**Sidekit license:** choose before distribution.~~ Resolved: MIT, already in the repository root.
3. **Local Music coexistence:** recommended v1 approach is separate providers with mutual pause, not a unified media model.

### Resolved launcher placement

- **Grid position:** immediately after `Music`, before `Clock`. The two media players stay adjacent so the mutual-pause behaviour is discoverable.
- **Dock:** not in the blue dock. The dock stays `Sidemessage, Sidenotes, Sideticks, Phonebook`; no dock layout or baseline changes.
- **Tile:** designed app (`designed = true`, default), so it needs its graph registered in `SidekitNavHost` in the same commit that adds the entry. Do not ship a placeholder tile.
- **Order:** the enum entry is added in Milestone 2, at the reserved position, not before the screens exist.
- **Baseline cost:** inserting a tile shifts every later launcher screenshot, so add the entry once and regenerate launcher baselines in that commit rather than twice.
4. **Audio quality settings:** expose librespot's supported quality options only; do not invent unsupported choices.
5. **Offline cache:** decide whether v1 supports downloaded/cached playback. librespot caching behavior must be verified; otherwise explicitly mark streaming-only.
6. **Podcast scope:** v1 can include shows/episodes if metadata support lands cleanly; otherwise ship music first.
7. **Playlist editing:** v1 may be read-only plus liked/saved mutations; full playlist editing can follow.
8. ~~**Image loader:** use an existing lightweight loader or a Sidekit-owned cache.~~ Resolved: Sidekit-owned `RemoteArtwork` (`apps/media/RemoteArtwork.kt`), no new dependency.

---

## 15. Immediate Next Steps

0. ✅ MIT license recorded in `docs/spotify/SPEC.md`; `docs/spotify/PROVENANCE.md` started. No further license decision needed.
1. ✅ Milestone 0.5 landed on `refactor/media-session-seams` (6 commits): `MediaSessionOwner`/registry, audio-focus decision, service/notification ids from the owner, `KeystoreCipher` promotion, `NativeLibraries` guarded loading, `RemoteArtwork`. See the milestone for what was deliberately *not* built (no focus-arbiter class).
2. ✅ `docs/spotify/SPEC.md` written on `docs/sidetracks-spec`, mapping every component to *reuse / extend / new* and deliberately rejecting Spotify patterns that do not fit Sidekit's minimal UI. `SPEC.md` is the implementation contract; this plan remains authoritative for licensing and native architecture.
3. Add a pinned upstream librespot submodule under `native/librespot`.
4. Build a minimal Rust/JNI library with `nativeInit`, `sessionConnect`, `playerCreate`, `playerLoad`, and an audio sink.
5. Add the Gradle Cargo task for `arm64-v8a`.
6. Play one known Spotify track end-to-end on the Sidephone.
7. Only after that spike, start the Kotlin domain layer and Sidekit screens.

The spike should be treated as a go/no-go checkpoint. If librespot cannot authenticate or deliver stable audio on the target device, stop before building the UI and resolve that first.

---

## 16. UI Mockup Delivered

**Branch:** `feat/sidetracks-mockup`  
**Commits:** `33c4b59 Add the Sidetracks UI mockup`, `c2b3e5c Document the Sidetracks mockup`  
**Installed build:** `hrln.interactive.sidekit.dev` — `1.2.3-68-gc2b3e5c-dev`

- Added Sidetracks as a real Sidekit sub-app: package `apps/spotify`, launcher entry immediately after Music (`SidekitIcon.QueueMusic`), route `app/sidetracks`, and `sidetracksGraph(...)` registration.
- Implemented all specified screens: Home, Library, Collection, Artist, Search, Now Playing, Queue, Settings and Sign In.
- Used Sidekit chrome/components throughout: `SidekitScaffold`, `SidekitList`, `SidekitItem`, `SidekitTile`, `SidekitTextField`, action bars, Options, list fade and focus patterns.
- Added a mock account, library, search data, generated cover art and a shared state holder that simulates queue, history, shuffle, repeat, save/follow, notices and sign-out.
- Added Roborazzi coverage for all nine screens; baselines are in `Sidekit/app/screenshots/sidetracks_*.png`.
- Verified with `:app:compileDebugKotlin` and `:app:testDebugUnitTest --tests ...SidetracksScreenshotTest`, then installed the `dev` APK on the attached Sidephone.
- The device keyguard was active during tap-through, so the installed build was not manually exercised; the same screens were rendered and reviewed through the screenshot harness.

Next implementation step is unchanged: replace `MockSpotifyDataSource`/the mock playback state with the real `SpotifyGateway`, auth and playback service behind the existing UI.

