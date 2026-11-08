# Sidetracks — Feature Specification

**Status:** Draft for implementation review
**App label:** Sidetracks (temporary; see §12)
**Package:** `hrln.interactive.sidekit.apps.spotify`
**Route root:** `app/sidetracks`
**Native library:** `libsidekit_spotify.so`
**Reference plan:** `SIDESPOT_SIDEKIT_PLAN.md` (decision, milestones, native layer)
**Provenance:** `docs/spotify/PROVENANCE.md`

This document specifies **behaviour and UI**, not the native bridge. The librespot/JNI surface,
process model, and clean-room rules live in the plan. Where the two disagree, this document wins
for anything the user can see, and the plan wins for licensing and native architecture.

---

## 1. What this app is

Sidetracks gives one Spotify account a Sidekit-shaped client: **your library, search, playback,
and a queue**. It is not a reproduction of Spotify's home feed, and it does not reproduce another
Spotify client's screen-for-screen layout.

The organising question for every screen is:

> What is the smallest surface on which a keypad-driven 480 × 640 phone can find a track and play it?

Everything that does not serve that question is deferred (§3).

**Primary user journeys** (v1 must do all four end to end):

1. Sign in, see recently played, play something.
2. Find something by search or by browsing the library, and play it.
3. Control playback while the phone is locked or another app is open.
4. Sign out, and be certain playback stopped and credentials were removed.

---

## 2. Design law inherited from Sidekit

These are constraints, not preferences. A design that violates them is wrong even if it looks
like Spotify.

| # | Law | Consequence for Sidetracks |
|---|---|---|
| 1 | **Chrome belongs to the shell.** Title at top, body in the middle, action bar at the bottom, drawn by `SidekitCanvas`. | No app-owned bottom bar, no floating action button, no custom top app bar. |
| 2 | **No dialogs, toasts, or snackbars.** No Compose Sidekit app uses one; the only dialogs in the repo are in the emulator's ported `Activity`, which is not a Sidekit screen. | Confirmations and errors are an action-bar notice (replacing the bar, dismissed by tap/Back) or a list row. Never a popup. |
| 3 | **The action bar has two slots**: left = the continuation (primary action, else Back), right = the next thing (often Undo). | A screen never has three wide buttons. Extra actions go to Options or the body. |
| 4 | **Options key (keypad top-right)** holds the secondary actions. Options close before the action runs. | Queue, shuffle/repeat, album/artist, sign-out live here where they are not worth a permanent button. |
| 5 | **The body is the control surface.** Rows and tiles initiate; the bar confirms or navigates. | Play/pause/seek/transport are tiles in the body (as in Music), not bar buttons. |
| 6 | **No persistent mini-player, no bottom navigation, no tabs.** | Now Playing is reached from the Home art tile; the home screen carries the only "now playing" affordance. |
| 7 | **480 × 640, compact.** Fixed canvas; every screen must fit with the action bar. | No horizontal carousels, no hero banners, no multi-column shelves. Lists are one column. |
| 8 | **Keypad and D-pad first.** Focus ring, 48 dp minimum targets, focus is never faded out. | Every row/tile is focusable; the first focusable element is deterministic per screen (§5). |
| 9 | **Content never wears the theme colour.** Selection/progress use `emphasisA1..3`. | Playing row, progress, saved state use emphasis; the user's case colour appears only on Primary actions. |
| 10 | **Motion is spring-based and purposeful** (`SidekitMotion`, `BodyEntrance`, `ActionMotion`). | No shared-element transitions, no page swipes, no animated artwork. |
| 11 | **One app, one job.** Sidekit apps are small. | No social, no recommendations feed, no account management (§3). |
| 12 | **Text may be interrupted.** Titles truncate at two lines with ellipsis; secondary text at one. | Long track/album names never push the seek row or transport off screen. |

If a Spotify pattern requires a Material component that Sidekit does not have (chips, tabs,
bottom sheets, snackbars, carousels), it is **out of scope by definition**, not a design choice.

---

## 3. Feature triage

Spotify's client and typical third-party clients expose far more than Sidekit should. Each row
below is a deliberate decision, with the Sidekit reason.

### 3.1 In scope for v1

| Capability | Shape in Sidetracks |
|---|---|
| Sign in with Spotify | System browser + return; no password field (§5.9). |
| Recently played | Home list, recency-ordered, capped (§5.1). |
| Library browse | One Library screen with a filter in Options, not five tabs (§5.2). |
| Albums, playlists, shows | One generic Collection screen (§5.3). |
| Artists | Artist screen with top tracks + albums (§5.4). |
| Search | One field, grouped result rows (§5.5). |
| Playback | Now Playing screen + foreground service (§5.6, §7). |
| Queue | A screen reached from Now Playing Options (§5.7). |
| Save / like / follow | Row or header tile, optimistic with rollback (§6.4). |
| Settings | Audio quality, volume, clear cache/history, sign out (§5.8). |
| Background + lock screen | Platform `MediaSession`, one active session (§7). |

### 3.2 Deliberately rejected (not "later")

| Spotify norm | Why Sidetracks does not have it |
|---|---|
| Algorithmic home with curated shelves ("Made for you", "Jump back in") | It is an infinite personalised feed. Sidekit home screens are short, static, and predictable; a feed cannot be made minimal by shrinking it. Home shows **your** recency, nothing inferred. |
| Horizontal carousels of covers | Violates the 480 px single-column rule and the focus model; every carousel is a second scroll axis. Albums/playlists are rows. |
| Persistent mini-player above the bar | No persistent overlays; the launcher's app frame is fixed. Playback state lives on Home's art tile and in Options. |
| Bottom tab bar (Home / Search / Library) | The action bar is the only bar. Home already exposes Search and Library as tiles. |
| Full-screen "now playing" overlay with drag-to-dismiss | Navigation is the shell's; no gesture-owned screens. Now Playing is a normal route. |
| Queue as a modal bottom sheet | Queue is a normal screen (§5.7); the shell has no sheets. |
| Device picker / Spotify Connect output switching | Out of scope: the target is one phone, its speaker, headphones, and Bluetooth routes chosen by Android. Connect would add a second session model. |
| Lyrics, Canvas, video podcasts | Media surfaces Sidekit cannot draw minimally on 480 × 640; text-only lyrics add a licensing dependency. |
| Social: friend activity, shared listening, profiles | Not the app's job; needs another account/permission surface. |
| Account management (plan, profile, payment) | Deep-links out of scope; the app should not become a web wrapper. |
| Crossfade, gapless tuning, EQ, normalisation UI | Native/audio complexity with no visible Sidekit equivalent. Normalisation is a single Settings toggle only if librespot exposes it cheaply; otherwise omitted. |
| Recommendations / radio seeded from a track | A recommendation feed in disguise. Autoplay appends from the same context only (§7.3). |
| Infinite scroll | Lists end with an explicit "Load more" row (existing Sidekit pattern), so focus and the action bar never fight a moving target. |

### 3.3 Deferred, with entry conditions

| Capability | Entry condition |
|---|---|
| Shows and episodes | Ships only if metadata lands cleanly in the native spike. Episodes use the same Collection screen with a duration + progress row; no separate podcast UI. |
| Offline downloads | Only if librespot caching is verified on device. Otherwise Settings says "Streaming only" and the feature is not advertised. |
| Playlist editing (add/remove/reorder) | v1 is read-only except "Add to playlist" from a track's Options, which is a save mutation, not an editor. |
| Sleep timer | Deferred until playback service exists; would be one Options entry on Now Playing. |
| Sharing / copying a link | Deferred; no share sheet in v1. |

**Explicit non-goal:** feature parity with Spotify. Parity with *Sidekit* is the bar.

---

## 4. Information architecture

### 4.1 Navigation tree

```text
app/sidetracks                     Home
├── library                        Library (filter in Options)
│   ├── collection/{type}/{id}     Album | Playlist | Show
│   └── artist/{id}                Artist
├── search                         Search
│   ├── collection/{type}/{id}
│   └── artist/{id}
├── now-playing                    Now Playing
│   ├── collection/{type}/{id}
│   ├── artist/{id}
│   └── queue                      Queue
├── settings                       Settings
└── sign-in                        Sign in (also the gate's destination)
```

Rules:

- **IDs, not URIs, in routes.** Spotify URIs contain `:` and base62 IDs; pass `type` (`album`,
  `playlist`, `show`, `artist`) plus the bare ID. The gateway rehydrates `spotify:<type>:<id>`.
  Never interpolate a raw URI into a route.
- Depth is at most three screens (Home → Library → Collection, or Home → Now Playing → Queue).
  Tapping a track row **starts playback in place**; it never navigates to Now Playing, so
  browsing cannot build a long back stack.
- Back always pops one entry. The action bar's left slot is `Back` on every non-Home screen,
  labelled with the destination the user came from where it is unambiguous ("Library", "Search").
- No deep links in v1 beyond the app's root route. A Spotify share URL is **not** a supported
  entry point.

### 4.2 Launch and gating

1. The launcher opens `app/sidetracks`.
2. If no credential is stored → `sign-in`.
3. If a credential exists but the session cannot connect → Home in an **offline/error state**
   (§6.5), not a redirect to sign-in. Only an unrecoverable auth failure clears credentials and
   returns to `sign-in`.
4. If the native library is unavailable (`NativeLibraries.load("sidekit_spotify") == false`) →
   a single explanatory screen with the Spotify content unavailable. No retry loop, no crash.

---

## 5. Screens

Notation: **Bar** = action bar slots (left | right). **Options** = keypad Options list.
Every screen uses `SidekitScaffold`, `SidekitList`, `SidekitItem`/`SidekitTile`, and
`Modifier.listFade(state, index)` exactly as existing apps do.

### 5.1 Home — "Sidetracks"

**Purpose:** resume what you were doing, and get to Library/Search in one press.

**Body** (top to bottom, one list):

| Row | Content | Tap |
|---|---|---|
| Tiles (1 row of 2) | Square now-playing art (or `MusicNote2` placeholder), `Library` | Art → Now Playing; Library → Library |
| Section label | "Recently played" | — |
| Track rows (≤ 10) | Title, artist · duration | Play that track immediately, in the recently-played order |
| Terminal row | "Nothing here yet" when empty | — |

- The art tile shows the current track's cover, and its label is the track title. It is the only
  persistent "now playing" affordance in the app.
- No section for "Made for you", "New releases", "Charts", or anything fetched from a
  recommendation endpoint.
- Recently played is local history (bounded, §6.3), not Spotify's play history.

**Bar:** `Now Playing` (square, art) | `Search` (forward). Search is a bar action, not a third
tile, matching how Music keeps Search in the bar.
**Options:** `Settings`.
**States:** empty ("Nothing played yet" + "Search" hint), offline (§6.5), signed out (never
rendered; the gate shows Sign in).
**Initial focus:** first tile.

### 5.2 Library

**Purpose:** browse the account's saved items without five separate screens.

**Body:** one list of the currently filtered item type. Filter is a single value, not a tab row.

| Filter | Rows |
|---|---|
| Liked songs (default) | Track: title, artist · duration |
| Playlists | Playlist: name, "N tracks" |
| Albums | Album: name, artist · year |
| Artists | Artist: name, "N albums" if known |
| Shows | Show: name, publisher |

- The filter's current value is visible as the **subtitle under the title** (one line), never as
  selectable chips.
- Pagination is an explicit final row: "Load more" (or "Loading…" while busy). No infinite scroll.
- Saved-state changes made elsewhere (e.g. unsaving an album from its Collection) are reflected
  when the list is next shown; no live cross-screen animation.

**Bar:** `Back` | `Search` (forward).
**Options:** `Liked songs`, `Playlists`, `Albums`, `Artists`, `Shows` — the current one carries
`tone = Primary`, the rest Muted (same pattern as Settings' in-place choices).
**States:** empty per filter ("No saved albums"), loading row, error row with `Try again` in
Options? No — error row carries the retry (`SidekitItem("Couldn't load. Tap to retry")`), because
Options must stay the filter list.
**Initial focus:** first content row.

### 5.3 Collection — album, playlist, or show

**Purpose:** one screen for every "a list of tracks/episodes with a cover".

**Body:**

1. **Header row (tiles):** cover art tile (non-interactive), `Play` tile, `Save`/`Saved` tile
   (`Bookmark`/`Check`). For a playlist, a fourth tile opens the owner/artist if known; for an
   album, `Artist` tile.
2. **Metadata block:** title (headline, max 2 lines), artist/owner + year + track count (caption).
3. **Item rows:** index or duration as trailing secondary; track title + artist.
4. **Terminal row:** "Load more" for paged collections.

Rules:

- Tapping a track row plays **from that row within this collection**, in collection order.
- A non-playable row (unavailable/local-only) is a muted row whose tap shows an action-bar
  notice ("Not available on this account") rather than starting playback.
- Explicit tracks are marked with a trailing "E" in the secondary, not a badge image.
- For shows, the row secondary is the publish date; episodes that are video-only are muted and
  marked "Video" (not playable in v1).

**Bar:** `Back` only. Play is a body tile, the same division Music's Album screen uses: the body
initiates, the bar navigates.
**Options:** `Save`/`Saved`, `Artist` (album), `Add to queue`, `Go to now playing`.
**States:** loading; not found ("This album isn't available"); offline cached header but no
tracks (explain).
**Initial focus:** the `Play` tile.

### 5.4 Artist

**Purpose:** the artist's top tracks and albums, without a biography or social surface.

**Body:**

1. `Play` tile + artist name (headline).
2. Section "Top tracks" (≤ 10): track rows.
3. Section "Albums": album rows.

No related artists, no "fans also like", no monthly listeners, no bio text in v1.

**Bar:** `Back` only; Play is the body tile.
**Options:** `Follow`/`Following`, `Go to now playing`.
**Initial focus:** `Play` tile.

### 5.5 Search

**Purpose:** type a query, get grouped results, play.

**Body:**

- Header: `SidekitCard` containing `SidekitTextField` (`placeholder = "Search Spotify"`,
  `singleLine = true`, `autoFocus = true`, `focusOnKeypad = true`), exactly like Files' search.
- Results grouped by section in this order: **Tracks, Artists, Albums, Playlists, Shows**. Empty
  sections are omitted entirely; no "no results in this category" rows.
- At most 5 rows per section, with a "More tracks" row that expands that section in place (it
  does not navigate to a separate results screen).
- Debounce 300 ms; a new query cancels the previous request; results are replaced, never appended.
- Query shorter than 2 characters → an explanation row, no request.

**Bar:** while the field is focused the shell's T9 override owns the bar (suggestions + Done).
When not typing: `Back` | `Clear` (Muted) if a query exists, otherwise just `Back`.
**Options:** `Tracks`, `Artists`, `Albums`, `Playlists`, `Shows` as **search scope toggles**
(multi-select, `Primary` when on), default all on.
**States:** idle ("Search your library and Spotify"), typing, no results ("No results for
\"x\""), error row with retry.
**Initial focus:** the text field.

### 5.6 Now Playing

**Purpose:** the one full playback surface; also the media notification's detail view.

**Body** (mirrors Music's Now Playing, which is the established Sidekit shape):

1. Square artwork, largest that fits after the text/seek/transport (never smaller than 200 dp).
2. Title (headline, 2 lines max), artist (label, tappable → Artist).
3. Seek row: elapsed / duration, draggable, D-pad adjustable; the position ticks twice a second.
4. Transport row of five weighted tiles: Shuffle, Previous, Play/Pause, Next, Repeat.
5. If the queue has a next item, a single "Up next: <title>" row that opens Queue.

Rules:

- Shuffle and Repeat are **state tiles**: `Primary`/`emphasis` when on, neutral when off. Their
  labels carry the state ("Shuffle on", "Repeat: context"). Repeat reuses `SidekitIcon.Undo` and
  the `Repeat: off / context / track` labels, matching Music, rather than adding an icon.
- Play/Pause is the largest tile (`PLAY_WEIGHT`) with the play tone, as in Music.
- No swipe gestures, no drag-to-expand, no video, no lyrics button.
- Artwork is `RemoteArtworkImage` at the drawn size; placeholder `Album` icon.

**Bar:** `Back` | `Save`/`Saved` (square, Secondary when saved).
**Options:** `Queue`, `Album`, `Artist`, `Add to playlist`, `Go to artist radio`? No: `Queue`,
`Album`, `Artist`, `Add to playlist`.
**States:** nothing playing ("Nothing playing" + "Pick something from your library"), native
unavailable, buffering (the Play tile becomes `Pause`? No — show a Muted "Buffering…" action-bar
notice and keep transport enabled for pause/stop).
**Initial focus:** Play/Pause tile.

### 5.7 Queue

**Purpose:** see what will play, jump, or remove.

**Body:** one list, current item first, marked `Now playing` as its secondary; then upcoming
tracks; then a section "History" capped at 20. No drag-handle reordering.

- Tap a row → jump to it (it becomes current; skipped items move to history).
- Swipe is **not** used. `Play next` and `Remove from queue` in Options act on the **focused** row,
  so the whole screen is operable without a selection mode. The focused row already has the
  standard focus ring; no extra selected state is drawn.
- "Clear upcoming" is an Options action that empties the queue but keeps the current item, with
  the standard Undo bar (`undoBar`) for five visible seconds.

**Bar:** `Back` | `Now playing` (returns to Now Playing).
**Options:** `Play next`, `Remove`, `Clear upcoming`, `Shuffle` (label carries state).
**States:** empty queue ("Nothing queued" + "Search" hint).
**Initial focus:** current row.

### 5.8 Settings

**Purpose:** the account and playback knobs, and sign-out.

**Body** (rows change value in place on tap, like Settings does):

| Row | Values |
|---|---|
| Audio quality | `Normal` / `High` / `Very high` (only what librespot supports) |
| Volume | Slider row, 0–100 %, applies live |
| Streaming only | Static explanation row if offline caching is not implemented |
| Clear artwork cache | Row with `N MB`; tap clears, notice confirms |
| Clear history | Row; destructive confirmation in the bar |
| Account | `Signed in as <name>` or `Not signed in` |
| Sign out | Row → bar confirmation |

**Bar:** `Back` | `Sign out` (Destructive, only when signed in).
**Options:** `About`, `Clear cache`.
**Confirmation:** destructive actions swap the bar to `Cancel` | `Sign out` / `Clear` — no dialog.
**Initial focus:** Audio quality row.

### 5.9 Sign in

**Purpose:** explain and start the OAuth flow without collecting a password.

**Body:**

1. Short explanation: "Sidetracks plays from your Spotify account. Your password is entered in
   the browser, never here."
2. A single `SidekitCard`/`SidekitItem` row: "Log in with Spotify" (link arrow).
3. Status row while waiting: "Waiting for the browser…" then "Connected" or an error row.

Rules:

- Launch the system browser with `Intent.ACTION_VIEW`; return through the app's registered
  redirect. Never render a WebView or a password field.
- If no browser is available, the status row explains, and Options offers `Copy link`.
- Credentials are stored only through `PrivateSettings` (§9).
- On success, replace the sign-in route with Home; Back must not return to sign-in.

**Bar:** `Back` (to launcher) | `Log in` (Primary, forward).
**Options:** `Copy link`, `Sign out` (only if a stale credential exists).
**States:** idle, browser opened, connecting, failed (retry in the bar), unsupported (native
unavailable).
**Initial focus:** the Log in row.

---

## 6. Cross-cutting states

### 6.1 Loading

An explicit `SidekitItem("Loading…")` row. No skeletons, no shimmer, no spinners: Sidekit has
none, and a 480 px list gains nothing from them. The bar stays usable (Back).

### 6.2 Empty

One explanatory row naming the filter, plus a hint where a next action exists
("No saved albums" / "Search"). Never an illustration or a button inside the body.

### 6.3 History

Locally stored, bounded (200 entries), most-recent-first, deduplicated by track URI, written
atomically. "Clear history" empties it. History is only used for Home; it is not uploaded.

### 6.4 Mutations (save, like, follow)

- Optimistic: the tile/row shows the new state immediately.
- On failure: revert and show a notice in the action bar ("Couldn't save. Tap to retry") for the
  same screen. No dialog, no toast.
- The notice replaces the bar (existing `withNotice` shape) and dismisses on tap or Back.

### 6.5 Offline and errors

| Condition | Where it appears |
|---|---|
| No network | Notice in the bar on the action that failed; a persistent muted row at the top of a list that cannot load ("Offline. Showing saved items.") |
| Session expired (recoverable) | Notice: "Spotify session expired. Tap to reconnect." → reconnect in place |
| Session expired (unrecoverable) | Clear credentials → Sign in; a single explanation row, no loop |
| Rate limited | Error row: "Spotify is busy. Tap to retry in a moment." |
| Track unavailable | Muted row; tapping shows a notice, playback skips on automatic advance |
| Native library missing | Whole-app explanation screen; no retry |
| Device route lost (headphones unplugged) | Pause (existing `AudioFocus`/noisy behaviour), no notice |

Errors use the gateway's stable codes mapped to fixed user strings. Raw librespot messages never
reach the UI.

---

## 7. Playback behaviour

### 7.1 One media session

- `SpotifyPlayback` registers a `MediaSessionOwner` in `MediaSessions` once, with
  `sessionId = "spotify"`, channel `"sidetracks"`, and notification id `0x5354` ("ST") —
  deliberately not Music's `0x6D75` ("mu").
- Starting Sidetracks playback pauses Music; starting Music pauses Sidetracks. The registry
  enforces this in-process; Android audio focus enforces it across processes.
- Only one notification and one platform `MediaSession` may be active.

### 7.2 Service and notification

- Foreground service with `mediaPlayback` type, a notification channel separate from Music's,
  and play/pause/next/previous/stop actions.
- Notification art: `RemoteArtwork` at notification size, placeholder `Album`.
- Dismissing the notification while paused stops the service but keeps the queue.
- Task removal does not stop playback while playing; sign-out always does.

### 7.3 Queue semantics (user-visible)

- The app owns the queue; librespot plays one item at a time.
- Playing a row plays from that row within its collection, replacing the queue.
- `Play next` inserts after the current item; `Add to queue` appends.
- Shuffle applies to upcoming items only; it never reorders history or the current item.
- Repeat: `off` (stop at end, keep last track paused), `context` (loop the collection),
  `track` (loop one).
- When the queue ends with autoplay on, append tracks from the same context only. When autoplay
  is off, stop at the end and leave the last track paused at 0.
- Seeking clamps to the item's duration. A seek before the item is ready is applied on prepare.

### 7.4 Interruptions

Audio focus loss pauses; transient loss resumes on regain only if it was playing; headphone
unplug pauses; Bluetooth route change recreates the audio sink without changing position.
These are device-tested, not unit-tested.

---

## 8. Visual and content rules

- **Typography:** title = screen/title token; track/album/artist names use the headline token only
  on Now Playing and Collection headers, otherwise body token. Secondary = caption, one line.
- **Artwork:** always square, `RemoteArtworkImage`, 1:1, placeholder `Album`. Sizes: 48 dp row,
  104 dp tile, Now Playing fills available square. Never a blurred backdrop, never a colour
  extracted from the art.
- **Numbers:** durations as `m:ss`, track counts as "12 tracks", years as `2024`.
- **Explicit/unavailable:** textual markers in the secondary line ("E", "Video", "Unavailable"),
  not icons.
- **Icons:** reuse `SidekitIcon` only. New icons require a design decision; v1 needs none.

---

## 9. Component map

Every component is one of **reuse** (exists, use as-is), **extend** (exists, small addition), or
**new** (this app writes it).

### 9.1 Reuse

| Component | Path | Use |
|---|---|---|
| Chrome, scaffold, action bar | `sidekit/.../design/{action,components}` | All screens |
| `SidekitScaffold`, `SidekitList`, `SidekitItem`, `SidekitTile`, `SidekitItemRow`, `SidekitIconItem`, `SidekitCard` | `sidekit/.../design/components/` | Every list and tile |
| `SidekitTextField` | `sidekit/.../design/components/SidekitTextField.kt` | Search |
| `RemoteArtwork`, `RemoteArtworkImage` | `app/.../apps/media/RemoteArtwork.kt` | All artwork |
| `PrivateSettings` | `app/.../apps/integrations/PrivateSettings.kt` | Encrypted credentials |
| `KeystoreCipher`/`SecretCipher` | `app/.../apps/integrations/SecretCipher.kt` | Credential encryption |
| `NativeLibraries` | `app/.../system/NativeLibraries.kt` | Loading `libsidekit_spotify.so` |
| `MediaSessionOwner`, `MediaSessions` | `app/.../apps/media/MediaSessionOwner.kt` | One active player |
| `AudioFocus` | `app/.../apps/media/MediaPlayerCore.kt` | Focus and pause/resume |
| `undoBar`, `UndoWindow` | `sidekit/.../design/action`, `app/.../ui/` | Queue clear |
| `SidekitPreviewFrame`, `SidephonePreview` | `app/.../ui/` | Previews |
| Roborazzi screenshot harness | `app/src/test/.../*ScreenshotTest.kt` | Baselines |

### 9.2 Extend

| Component | Change |
|---|---|
| `SubApp` | Add `Sidetracks` immediately after `Music` (§12) |
| `SidekitNavHost` | Register `sidetracksGraph(nav)` |
| `MediaSessions` | No change; Spotify registers as another owner |
| `MusicService` shape | Copied **structurally**, not shared: a separate `SpotifyPlaybackService` reads its ids from its own owner |

### 9.3 New

| Area | Files |
|---|---|
| Graph/routes | `SpotifyGraph.kt` (expose `sidetracksGraph`), route constants |
| Screens | `SidetracksHomeScreen.kt`, `LibraryScreen.kt`, `CollectionScreen.kt`, `ArtistScreen.kt`, `SearchScreen.kt`, `NowPlayingScreen.kt`, `QueueScreen.kt`, `SettingsScreen.kt`, `SignInScreen.kt` |
| ViewModel/state | `SpotifyViewModel.kt` or per-screen state holders; `FakeSpotifyGateway` for tests |
| Auth | `auth/SpotifyAuth.kt`, `auth/SpotifyCredentialStore.kt` (wraps `PrivateSettings`) |
| Bridge | `bridge/NativeBridge.kt`, `bridge/NativeModels.kt`, `bridge/PlaybackEvent.kt` |
| Data | `data/SpotifyGateway.kt` (interface), `NativeSpotifyGateway`, `SpotifyLibraryRepository`, `SpotifySearchRepository`, `SpotifyQueueRepository`, `SpotifyHistoryRepository` |
| Playback | `playback/SpotifyPlayback.kt` (implements `MediaSessionOwner`), `SpotifyPlaybackService.kt`, `SpotifyAudioSink.kt` |
| UI helpers | `ui/SpotifyRows.kt`, `ui/Notice.kt` (or promote the existing private `withNotice`) |

**Not used:** Room, Coil/Glide/OkHttp, Hilt/Koin/Dagger, media3, Material3 components. Sidekit
uses manual `viewModelFactory` wiring, Compose, and plain repositories; Sidetracks follows.

---

## 10. Data and domain

Models are the plan's `SpotifyTrack`, `SpotifyAlbum`, `SpotifyArtist`, `SpotifyPlaylist`,
`SpotifyShow`, `SpotifyEpisode`, `SpotifyPage<T>`, `SpotifySearchResults`
(`SIDESPOT_SIDEKIT_PLAN.md` §9). The UI never sees native DTOs.

- `SpotifyGateway` is the only interface the UI depends on; `NativeSpotifyGateway` implements it;
  `FakeSpotifyGateway` drives tests and screenshots.
- Collections are addressed by `(type, id)`. The gateway resolves the rest.
- All maps from gateway results to rows happen in one place (`ui/SpotifyRows.kt`) so a row's
  secondary-line rules (§8) cannot drift between screens.

---

## 11. Testing and acceptance

### 11.1 Unit

- Queue: play-from-row, play next, add, shuffle (upcoming only), repeat modes, end-of-queue with
  autoplay on/off, removal and Undo restore.
- Search: debounce, cancellation of stale results, scope toggles, section ordering, "More".
- Library: filter persistence, pagination, dedupe, optimistic save + rollback.
- History: bound, dedupe, atomic write, clear.
- Auth: credential store round-trip, unrecoverable failure clears, sign-out clears.
- Error mapping: every native code → one fixed string, no raw text.

### 11.2 Screenshot (Roborazzi, existing harness)

Home with/without current track; Library per filter; Collection (album, playlist, show);
Artist; Search idle/results/no-results; Now Playing playing/paused/nothing; Queue with
selection; Settings; Sign in idle/waiting/failed; D-pad focus on the first row of each.

### 11.3 Device

Login/logout/re-login; playback with screen off; notification actions; headphone unplug;
Bluetooth route change; call interruption; network loss mid-track; process death; Music ↔
Sidetracks mutual pause; x86_64 build without the native library.

### 11.4 Acceptance criteria (UI)

- Every screen fits 480 × 640 with the action bar and no scrolling to reach transport controls.
- No dialogs, toasts, snackbars, bottom navigation, carousels, or mini-player exist.
- Every list is operable with D-pad only; focus is visible; nothing focused is faded.
- Starting playback in either app pauses the other within one second.
- Sign-out removes credentials, stops playback, and returns to Sign in.
- A missing native library produces an explanation, not a crash.
- No Spotify feature listed in §3.2 appears in the build.

---

## 12. Decisions and open questions

| # | Decision |
|---|---|
| 1 | Label **Sidetracks** (temporary). Enum `Sidetracks`, route `app/sidetracks`, package stays `spotify`. |
| 2 | Grid position: immediately after Music. Not in the dock. |
| 3 | Home is recency + tiles only; no algorithmic shelves. |
| 4 | Library is one screen with an Options filter; no tabs. |
| 5 | Collections share one screen; artists are separate. |
| 6 | Queue is a screen, reached from Now Playing. |
| 7 | Artwork via `RemoteArtwork`; no new image dependency. |
| 8 | Repeat uses the `Undo` icon and Music's labels; no new icon. |

Open, to settle before Milestone 3:

1. **Show/episode inclusion** depends on the native spike (plan §14.6).
2. **Offline caching** depends on librespot verification (plan §14.5).
3. **Explicit marker** ("E") vs no marker at all, if design prefers less text.
4. **`Add to playlist`** placement: Now Playing Options vs a row action in Collection.
5. **Display name** may change after release; the route follows the enum constant (row 1).

---

## 13. Out of scope for this document

Native/JNI design, librespot patching, Gradle/Rust integration, OAuth redirect mechanics, and
audio-sink implementation are specified in `SIDESPOT_SIDEKIT_PLAN.md` §5, §10. This document
assumes those exist behind `SpotifyGateway`.
