package hrln.interactive.sidekit.apps.music

import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hrln.interactive.sidekit.apps.media.Access
import hrln.interactive.sidekit.apps.media.PermissionRationale
import hrln.interactive.sidekit.apps.media.Thumbnail
import hrln.interactive.sidekit.apps.media.formatDuration
import hrln.interactive.sidekit.design.action.ActionMotion
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.SidekitIconItem
import hrln.interactive.sidekit.design.components.SidekitItem
import hrln.interactive.sidekit.design.components.SidekitItemRow
import hrln.interactive.sidekit.design.components.SidekitList
import hrln.interactive.sidekit.design.components.SidekitScaffold
import hrln.interactive.sidekit.design.components.SidekitText
import hrln.interactive.sidekit.design.components.SidekitTextField
import hrln.interactive.sidekit.design.components.SidekitTile
import hrln.interactive.sidekit.design.components.listFade
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.ui.SidekitPreviewFrame
import hrln.interactive.sidekit.ui.SidephonePreview
import kotlinx.coroutines.delay

class MusicActions(
    val openPlaylist: (Playlist) -> Unit = {},
    val openAlbum: (Album) -> Unit = {},
    val openArtist: (Artist) -> Unit = {},
    val openTrack: (Track) -> Unit = {}
) {
    fun open(item: LibraryItem) = when (item) {
        is LibraryItem.PlaylistItem -> openPlaylist(item.playlist)
        is LibraryItem.AlbumItem -> openAlbum(item.album)
        is LibraryItem.ArtistItem -> openArtist(item.artist)
        is LibraryItem.TrackItem -> openTrack(item.track)
    }
}

/** Everything Now Playing can do, grouped so the screen keeps one positional argument. */
class NowPlayingActions(
    val onPlayPause: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    val onShuffle: () -> Unit = {},
    val onRepeat: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onAlbum: () -> Unit = {},
    val onArtist: () -> Unit = {},
    val onBack: () -> Unit = {}
)

@Composable
fun MusicHomeScreen(
    items: List<LibraryItem>,
    actions: MusicActions,
    onPlaylists: () -> Unit,
    onAlbums: () -> Unit,
    onArtists: () -> Unit,
    onNowPlaying: () -> Unit,
    onSearch: () -> Unit,
    artUri: Uri? = null,
    artId: Long = 0,
    onNewPlaylist: () -> Unit = {}
) {
    SidekitScaffold(
        title = "Music",
        actions = listOf(
            SideAction("nowPlaying", square = true, contentDescription = "Now Playing", artKey = artId, art = { AlbumArt(SMALL_ART, artUri) }, onClick = onNowPlaying),
            SideAction("search", "Search", SidekitIcon.Search, motion = ActionMotion.Forward, onClick = onSearch)
        ),
        options = listOf(SideAction("new", "New playlist", SidekitIcon.Add, onClick = onNewPlaylist))
    ) { padding ->
        val listState = rememberLazyListState()
        SidekitList(padding, state = listState) {
            item(key = "tiles") {
                SidekitItemRow(Modifier.listFade(listState, 0)) {
                    IconTile(SidekitIcon.QueueMusic, "Playlists", onPlaylists)
                    IconTile(SidekitIcon.Album, "Albums", onAlbums)
                    IconTile(SidekitIcon.Artist, "Artists", onArtists)
                }
            }
            libraryRows(items, listState, 1, actions)
        }
    }
}

@Composable
fun MusicLibraryListScreen(
    title: String,
    items: List<LibraryItem>,
    actions: MusicActions,
    onBack: () -> Unit,
    onNewPlaylist: (() -> Unit)? = null
) {
    SidekitScaffold(
        title = title,
        actions = backToMusic(onBack),
        options = onNewPlaylist?.let { listOf(SideAction("new", "New playlist", SidekitIcon.Add, onClick = it)) }.orEmpty()
    ) { padding ->
        val listState = rememberLazyListState()
        SidekitList(padding, state = listState) { libraryRows(items, listState, 0, actions) }
    }
}

@Composable
fun AlbumScreen(
    album: Album,
    tracks: List<Track>,
    saved: Boolean,
    onArtist: () -> Unit,
    onPlay: () -> Unit,
    onTrack: (Track) -> Unit,
    onBookmark: () -> Unit,
    onBack: () -> Unit
) {
    SidekitScaffold(title = album.title, actions = backToMusic(onBack)) { padding ->
        val listState = rememberLazyListState()
        SidekitList(padding, state = listState) {
            item(key = "tiles") {
                SidekitItemRow(Modifier.listFade(listState, 0)) {
                    SidekitTile(Modifier.weight(1f)) { AlbumArt(SMALL_ART, album.artUri) }
                    IconTile(SidekitIcon.Artist, "Artist", onArtist)
                    IconTile(if (saved) SidekitIcon.Check else SidekitIcon.Bookmark, if (saved) "Saved" else "Save", onBookmark, if (saved) TileTone.On else TileTone.Neutral)
                    IconTile(SidekitIcon.PlayArrow, "Play", onPlay, TileTone.Play)
                }
            }
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                SidekitItem(track.title, Modifier.listFade(listState, index + 1), secondary = formatDuration(track.durationMs), onClick = { onTrack(track) })
            }
        }
    }
}

@Composable
fun ArtistScreen(artist: Artist, albums: List<Album>, onPlay: () -> Unit, onAlbum: (Album) -> Unit, onBack: () -> Unit) {
    SidekitScaffold(title = artist.name, actions = backToMusic(onBack)) { padding ->
        val listState = rememberLazyListState()
        SidekitList(padding, state = listState) {
            item(key = "tiles") {
                SidekitItemRow(Modifier.listFade(listState, 0)) { IconTile(SidekitIcon.PlayArrow, "Play", onPlay, TileTone.Play) }
            }
            libraryRows(albums.map(LibraryItem::AlbumItem), listState, 1, MusicActions(openAlbum = onAlbum))
        }
    }
}

@Composable
fun NowPlayingScreen(
    playback: PlaybackState,
    saved: Boolean,
    actions: NowPlayingActions
) {
    val track = playback.track
    // Only back and Save are permanent in the bar; Album and Artist live in Options, because the art
    // and the artist line already go there and most tracks are played without leaving this screen.
    val bar = buildList {
        add(SideAction.back("back", "Music", actions.onBack))
        if (track != null) {
            add(
                SideAction.square(
                    "save",
                    if (saved) SidekitIcon.Check else SidekitIcon.Bookmark,
                    if (saved) "Saved" else "Save",
                    actions.onSave,
                    tone = if (saved) ActionTone.Secondary else ActionTone.Muted,
                    motion = ActionMotion.Up
                )
            )
        }
    }
    val options = if (track == null) {
        emptyList()
    } else {
        listOf(
            SideAction("album", "Album", SidekitIcon.Album, onClick = actions.onAlbum),
            SideAction("artist", "Artist", SidekitIcon.Artist, onClick = actions.onArtist)
        )
    }
    // No header: the track's own title is this screen's heading, and the height the shared title
    // takes is the difference between a thumbnail and an album cover.
    SidekitScaffold(title = null, actions = bar, options = options) { padding ->
        if (track == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { NothingPlaying() }
        } else {
            NowPlayingBody(playback, track, actions, Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun NowPlayingBody(playback: PlaybackState, track: Track, actions: NowPlayingActions, modifier: Modifier) {
    val queue = playback.queue
    val duration = playback.durationMs.takeIf { it > 0 } ?: track.durationMs
    val artUri = remember(track.albumId) { albumArtUri(track.albumId) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SidekitTheme.spacing.pageGap)) {
        // The art takes whatever height the text, seek row and transport leave, as a square.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AlbumArt(minOf(maxWidth, maxHeight), artUri, SidekitTheme.shapes.item, false, onClick = actions.onAlbum, onClickLabel = "Open album")
        }
        // Title and artist are one block: they read as one caption, so they sit closer together
        // than the sections around them.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SidekitText(
                track.title,
                Modifier.fillMaxWidth(),
                style = SidekitTheme.typography.headline,
                textAlign = TextAlign.Center,
                minLines = 1,
                maxLines = 2
            )
            if (track.artist.isNotBlank()) {
                // Tapping the artist is how you get to the artist, so the bar needs no button for it.
                SidekitText(
                    track.artist,
                    Modifier.fillMaxWidth().clickable(onClickLabel = "Open artist") { actions.onArtist() },
                    style = SidekitTheme.typography.label,
                    color = SidekitTheme.colors.surfaceA6,
                    textAlign = TextAlign.Center
                )
            }
        }
        TickingPosition(playback) { position -> MusicSeek(position, duration, actions.onSeek) }
        SidekitItemRow {
            IconTile(SidekitIcon.Shuffle, if (queue.shuffle) "Shuffle on" else "Shuffle off", actions.onShuffle, if (queue.shuffle) TileTone.On else TileTone.Neutral, MODE_WEIGHT)
            IconTile(SidekitIcon.SkipPrevious, "Previous", actions.onPrevious, weight = SKIP_WEIGHT)
            IconTile(
                if (playback.isPlaying) SidekitIcon.Pause else SidekitIcon.PlayArrow,
                if (playback.isPlaying) "Pause" else "Play",
                actions.onPlayPause,
                TileTone.Play,
                PLAY_WEIGHT
            )
            IconTile(SidekitIcon.SkipNext, "Next", actions.onNext, weight = SKIP_WEIGHT)
            IconTile(
                SidekitIcon.Undo,
                queue.repeat.label,
                actions.onRepeat,
                if (queue.repeat == RepeatMode.Off) TileTone.Neutral else TileTone.On,
                MODE_WEIGHT
            )
        }
    }
}

@Composable
private fun NothingPlaying() {
    val colors = SidekitTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SidekitTheme.spacing.pageGap)) {
        SidekitText("Nothing playing", style = SidekitTheme.typography.headline, color = colors.surfaceA6)
        SidekitText("Pick a song from your library", style = SidekitTheme.typography.caption, color = colors.surfaceA6)
    }
}

/**
 * Re-publishes the playback position while it plays. Its own composable so the half-second tick
 * recomposes the seek row only: the art and the transport don't redraw twice a second, and the
 * screen as a whole only recomposes when the state actually changes.
 */
@Composable
private fun TickingPosition(playback: PlaybackState, content: @Composable (Long) -> Unit) {
    val now = { SystemClock.elapsedRealtime() }
    val position by produceState(playback.positionAt(now()), playback) {
        value = playback.positionAt(now())
        while (playback.isPlaying) {
            delay(POSITION_TICK_MS)
            value = playback.positionAt(now())
        }
    }
    content(position)
}

/**
 * The seek bar: tap or drag the track, or step it with D-pad left/right. Progress semantics make
 * it announce and adjust like any slider, so TalkBack can scrub what a finger can.
 */
@Composable
private fun MusicSeek(position: Long, duration: Long, onSeek: (Long) -> Unit) {
    val seekable = duration > 0
    val fraction = if (seekable) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val trackColor = SidekitTheme.colors.surfaceA4
    val progressColor = SidekitTheme.colors.light
    // The bar takes whatever width the two labels leave, so the elapsed label must not change width
    // as it counts up: otherwise the bar resizes each second and its fill can appear to slip back.
    // Tabular digits make every digit the same width, and the slot is sized for the longest time
    // this track can show (its duration's shape, e.g. "00:00" for a 12-minute track).
    val labelStyle = SidekitTheme.typography.label
    val timeStyle = remember(labelStyle) { labelStyle.copy(fontFeatureSettings = "tnum") }
    val widest = remember(duration) { formatDuration(duration).replace(DIGIT, "0") }
    val interaction = remember { MutableInteractionSource() }
    fun seekTo(target: Float) {
        if (seekable) onSeek((target.coerceIn(0f, 1f) * duration).toLong())
    }
    val step = if (seekable) SEEK_STEP_MS.toFloat() / duration else 0f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(contentAlignment = Alignment.CenterEnd) {
            SidekitText(widest, Modifier.alpha(0f).clearAndSetSemantics {}, style = timeStyle)
            SidekitText(formatDuration(position), style = timeStyle)
        }
        Box(
            Modifier
                .weight(1f)
                .height(SEEK_TOUCH_HEIGHT)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            seekTo(fraction - step)
                            true
                        }

                        Key.DirectionRight -> {
                            seekTo(fraction + step)
                            true
                        }

                        else -> false
                    }
                }
                .indication(interaction, LocalIndication.current)
                .focusable(interactionSource = interaction)
                .pointerInput(duration) { detectTapGestures { offset -> seekTo(offset.x / size.width) } }
                .pointerInput(duration) { detectHorizontalDragGestures { change, _ -> seekTo(change.position.x / size.width) } }
                .semantics {
                    if (seekable) {
                        contentDescription = "Seek"
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                        setProgress { target ->
                            seekTo(target)
                            true
                        }
                    }
                }
                .drawBehind {
                    val h = 6.dp.toPx()
                    val y = (size.height - h) / 2
                    drawRoundRect(trackColor, Offset(0f, y), Size(size.width, h), CornerRadius(h / 2))
                    if (fraction > 0f) {
                        val filled = size.width * fraction
                        drawRoundRect(progressColor, Offset(0f, y), Size(filled, h), CornerRadius(h / 2))
                        // The knob is what says "this is draggable" without a label.
                        drawCircle(progressColor, SEEK_KNOB.toPx(), Offset(filled, size.height / 2))
                    }
                }
        )
        SidekitText(formatDuration(duration), style = timeStyle)
    }
}

private val DIGIT = Regex("\\d")

@Composable
fun MusicSearchScreen(query: String, results: List<LibraryItem>, onQueryChange: (String) -> Unit, actions: MusicActions, onBack: () -> Unit) {
    val focus = remember { FocusRequester() }
    SidekitScaffold(title = null, header = { SearchHeader(query, onQueryChange, focus) }, actions = backToMusic(onBack)) { padding ->
        val listState = rememberLazyListState()
        SidekitList(padding, state = listState) { libraryRows(results, listState, 0, actions) }
    }
}

@Composable
fun MusicPermissionScreen(access: Access, onBack: () -> Unit) {
    SidekitScaffold(title = "Music", actions = backToMusic(onBack)) { padding ->
        PermissionRationale("Play your music", "Music reads the songs saved on this phone. Playback and your playlists stay on the device.", access, padding)
    }
}

private val SMALL_ART = 44.dp

/** How far one D-pad step or one share of the seek bar moves playback. */
private const val SEEK_STEP_MS = 5_000L
private val SEEK_TOUCH_HEIGHT = 36.dp
private val SEEK_KNOB = 9.dp
private const val POSITION_TICK_MS = 500L

// The transport row shares its width by weight, so play is a wide pill and the mode toggles are
// narrow: the row reads as play with things around it, not as five equal buttons.
private const val MODE_WEIGHT = 0.85f
private const val SKIP_WEIGHT = 0.85f
private const val PLAY_WEIGHT = 1.2f

/**
 * Fill of a tile, and the ladder is deliberate:
 *
 * - [Play] takes the calculator's operator green (`successA1`, the colour of its AC and ÷). Never
 *   the action bar's primaryA1: a tile painted with the bar's tone reads as a seventh action
 *   button, which is what this screen used to be.
 * - [On] is a mode switched on or a track saved. It is the same green one step brighter instead of
 *   a second hue, so the row still has exactly one accent: play.
 * - [Neutral] is everything the user hasn't switched on.
 */
private enum class TileTone { Neutral, Play, On }

@Composable
private fun RowScope.IconTile(icon: SidekitIcon, label: String, onClick: () -> Unit, tone: TileTone = TileTone.Neutral, weight: Float = 1f) {
    val colors = SidekitTheme.colors
    SidekitIconItem(
        icon,
        label,
        Modifier.weight(weight),
        color = when (tone) {
            TileTone.Neutral -> colors.surfaceA3
            TileTone.Play -> colors.emphasisA1
            TileTone.On -> colors.surfaceA5
        },
        iconSize = SidekitTheme.spacing.tileIconSize,
        onClick = onClick
    )
}

private fun LazyListScope.libraryRows(items: List<LibraryItem>, listState: LazyListState, firstIndex: Int, actions: MusicActions) {
    itemsIndexed(items, key = { i, item -> "$i:${item.key()}" }) { index, item ->
        SidekitItem(item.title(), Modifier.listFade(listState, firstIndex + index), icon = item.icon(), onClick = { actions.open(item) })
    }
}

private fun LibraryItem.key() = when (this) {
    is LibraryItem.PlaylistItem -> "p${playlist.id}"
    is LibraryItem.AlbumItem -> "a${album.id}"
    is LibraryItem.ArtistItem -> "r${artist.id}"
    is LibraryItem.TrackItem -> "t${track.id}"
}
private fun LibraryItem.title() = when (this) {
    is LibraryItem.PlaylistItem -> playlist.name
    is LibraryItem.AlbumItem -> album.title
    is LibraryItem.ArtistItem -> artist.name
    is LibraryItem.TrackItem -> track.title
}
private fun LibraryItem.icon() = when (this) {
    is LibraryItem.PlaylistItem -> SidekitIcon.QueueMusic
    is LibraryItem.AlbumItem -> SidekitIcon.Album
    is LibraryItem.ArtistItem -> SidekitIcon.Artist
    is LibraryItem.TrackItem -> SidekitIcon.MusicNote2
}

@Composable
private fun AlbumArt(
    size: Dp,
    uri: Uri? = null,
    shape: RoundedCornerShape = SidekitTheme.shapes.control,
    bordered: Boolean = true,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null
) {
    Box(
        Modifier.size(size).clip(shape).background(SidekitTheme.colors.surfaceA2)
            .then(if (bordered) Modifier.border(1.dp, SidekitTheme.colors.light, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .semantics {
                contentDescription = "Album art"
                if (onClick == null) role = Role.Image
            }
    ) {
        Thumbnail(uri, size, size, Modifier.fillMaxSize())
    }
}

private fun backToMusic(onBack: () -> Unit) = listOf(SideAction.back("back", "Music", onBack))

@Composable
private fun SearchHeader(query: String, onQueryChange: (String) -> Unit, focusRequester: FocusRequester) {
    val spacing = SidekitTheme.spacing
    Box(Modifier.fillMaxWidth().height(spacing.headerHeight).padding(horizontal = spacing.itemPadding).padding(top = spacing.headerPaddingTop)) {
        SidekitTextField(query, onQueryChange, Modifier.fillMaxWidth().semantics { contentDescription = "Search music" }, textStyle = SidekitTheme.typography.title, placeholder = "Search here", focusRequester = focusRequester, focusOnKeypad = true, singleLine = true, autoFocus = true)
    }
}

@SidephonePreview
@Composable
private fun MusicHomePreview() = SidekitPreviewFrame { MusicHomeScreen(emptyList(), MusicActions(), {}, {}, {}, {}, {}) }
