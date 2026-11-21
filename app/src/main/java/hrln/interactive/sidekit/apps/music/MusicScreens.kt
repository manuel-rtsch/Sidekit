package hrln.interactive.sidekit.apps.music

import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.res.stringResource
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
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.apps.media.Access
import hrln.interactive.sidekit.apps.media.PermissionRationale
import hrln.interactive.sidekit.apps.media.formatDuration
import hrln.interactive.sidekit.design.action.ActionMotion
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
import hrln.interactive.sidekit.sdk.media.Thumbnail
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
        title = stringResource(R.string.music_title),
        actions = listOf(
            SideAction("nowPlaying", square = true, contentDescription = stringResource(R.string.music_now_playing), artKey = artId, art = { AlbumArt(SMALL_ART, artUri) }, onClick = onNowPlaying),
            SideAction("search", stringResource(R.string.music_search), SidekitIcon.Search, motion = ActionMotion.Forward, onClick = onSearch)
        ),
        options = listOf(SideAction("new", stringResource(R.string.music_new_playlist), SidekitIcon.Add, onClick = onNewPlaylist))
    ) { padding ->
        val listState = rememberLazyListState()
        SidekitList(padding, state = listState) {
            item(key = "tiles") {
                SidekitItemRow(Modifier.listFade(listState, 0)) {
                    IconTile(SidekitIcon.QueueMusic, stringResource(R.string.music_playlists), onPlaylists)
                    IconTile(SidekitIcon.Album, stringResource(R.string.music_albums), onAlbums)
                    IconTile(SidekitIcon.Artist, stringResource(R.string.music_artists), onArtists)
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
    val newPlaylist = stringResource(R.string.music_new_playlist)
    SidekitScaffold(
        title = title,
        actions = backToMusic(onBack),
        options = onNewPlaylist?.let { listOf(SideAction("new", newPlaylist, SidekitIcon.Add, onClick = it)) }.orEmpty()
    ) { padding ->
        val listState = rememberLazyListState()
        SidekitList(padding, state = listState) { libraryRows(items, listState, 0, actions) }
    }
}

@Composable
fun AlbumScreen(album: Album, tracks: List<Track>, onArtist: () -> Unit, onPlay: () -> Unit, onTrack: (Track) -> Unit, onBookmark: () -> Unit, onBack: () -> Unit) {
    SidekitScaffold(title = album.title, actions = backToMusic(onBack)) { padding ->
        val listState = rememberLazyListState()
        SidekitList(padding, state = listState) {
            item(key = "tiles") {
                SidekitItemRow(Modifier.listFade(listState, 0)) {
                    SidekitTile(Modifier.weight(1f)) { AlbumArt(SMALL_ART, album.artUri) }
                    IconTile(SidekitIcon.Artist, stringResource(R.string.music_artist), onArtist)
                    IconTile(SidekitIcon.Bookmark, stringResource(R.string.music_save), onBookmark)
                    IconTile(SidekitIcon.PlayArrow, stringResource(R.string.music_play), onPlay, PlayTileColour.Light)
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
                SidekitItemRow(Modifier.listFade(listState, 0)) { IconTile(SidekitIcon.PlayArrow, stringResource(R.string.music_play), onPlay, PlayTileColour.Light) }
            }
            libraryRows(albums.map(LibraryItem::AlbumItem), listState, 1, MusicActions(openAlbum = onAlbum))
        }
    }
}

@Composable
fun NowPlayingScreen(
    playback: PlaybackState,
    onAlbum: () -> Unit,
    onArtist: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onBookmark: () -> Unit,
    onBack: () -> Unit
) {
    val track = playback.track
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playback.isPlaying) {
        while (playback.isPlaying) {
            tick++
            delay(500)
        }
    }
    val position = playback.positionAt(SystemClock.elapsedRealtime() + tick * 0)
    val gap = SidekitTheme.spacing.itemOuterGap
    // Back plus the track's destinations live in the action bar, so the body has room for the art
    // and one row of full-size playback controls.
    val backLabel = stringResource(R.string.music_back_to_music)
    val albumLabel = stringResource(R.string.music_album)
    val artistLabel = stringResource(R.string.music_artist)
    val saveLabel = stringResource(R.string.music_save)
    // An empty queue keeps Back only: Album, Artist and Save would be dead buttons without a track.
    val actions = remember(track, onBack, onAlbum, onArtist, onBookmark, backLabel, albumLabel, artistLabel, saveLabel) {
        buildList {
            add(SideAction.square("back", SidekitIcon.ArrowLeftAlt, backLabel, onBack, motion = ActionMotion.Back))
            if (track != null) {
                add(SideAction("album", icon = SidekitIcon.Album, contentDescription = albumLabel, onClick = onAlbum))
                add(SideAction("artist", icon = SidekitIcon.Artist, contentDescription = artistLabel, onClick = onArtist))
                add(SideAction("save", icon = SidekitIcon.Bookmark, contentDescription = saveLabel, onClick = onBookmark))
            }
        }
    }
    val shuffleLabel = stringResource(if (playback.queue.shuffle) R.string.music_shuffle_on else R.string.music_shuffle_off)
    val repeatLabel = stringResource(playback.queue.repeat.label)
    val options = remember(shuffleLabel, repeatLabel, onShuffle, onRepeat) {
        listOf(
            SideAction("shuffle", shuffleLabel, SidekitIcon.Shuffle, onClick = onShuffle),
            SideAction("repeat", repeatLabel, SidekitIcon.Undo, onClick = onRepeat)
        )
    }
    val artUri = remember(track?.albumId) { track?.let { albumArtUri(it.albumId) } }
    SidekitScaffold(title = stringResource(R.string.music_now_playing), actions = actions, options = options) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
            // The art takes whatever height the text, seek row and controls leave, as a square.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AlbumArt(minOf(maxWidth, maxHeight), artUri, SidekitTheme.shapes.item, false)
            }
            SidekitText(track?.title ?: stringResource(R.string.music_nothing_playing), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 2)
            SidekitText(track?.artist.orEmpty(), style = SidekitTheme.typography.label, color = SidekitTheme.colors.surfaceA6, maxLines = 1)
            MusicSeek(position, playback.durationMs.takeIf { it > 0 } ?: track?.durationMs ?: 0L, onSeek)
            // Nothing playing shows no transport: the three tiles would only be dead buttons.
            if (track != null) {
                SidekitItemRow {
                    IconTile(SidekitIcon.SkipPrevious, stringResource(R.string.music_previous), onPrevious)
                    IconTile(if (playback.isPlaying) SidekitIcon.Pause else SidekitIcon.PlayArrow, stringResource(if (playback.isPlaying) R.string.music_pause else R.string.music_play), onPlayPause, PlayTileColour.Strong)
                    IconTile(SidekitIcon.SkipNext, stringResource(R.string.music_next), onNext)
                }
            }
        }
    }
}

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

private const val SEEK_STEP_MS = 5_000L
private val SEEK_TOUCH_HEIGHT = 36.dp
private val SEEK_KNOB = 9.dp

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
    SidekitScaffold(title = stringResource(R.string.music_title), actions = backToMusic(onBack)) { padding ->
        PermissionRationale(stringResource(R.string.music_permission_title), stringResource(R.string.music_permission_reason), access, padding)
    }
}

private val SMALL_ART = 44.dp
private enum class PlayTileColour { None, Light, Strong }

@Composable
private fun RowScope.IconTile(icon: SidekitIcon, label: String, onClick: () -> Unit, colour: PlayTileColour = PlayTileColour.None) {
    val colors = SidekitTheme.colors
    SidekitIconItem(
        icon,
        label,
        Modifier.weight(1f),
        color = when (colour) {
            PlayTileColour.None -> colors.surfaceA3
            PlayTileColour.Light -> colors.emphasisA2
            PlayTileColour.Strong -> colors.emphasisA1
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
private fun AlbumArt(size: Dp, uri: Uri? = null, shape: RoundedCornerShape = SidekitTheme.shapes.control, bordered: Boolean = true) {
    val description = stringResource(R.string.music_album_art)
    Box(
        Modifier.size(size).clip(shape).background(SidekitTheme.colors.surfaceA2).then(if (bordered) Modifier.border(1.dp, SidekitTheme.colors.light, shape) else Modifier).semantics {
            contentDescription = description
            role = Role.Image
        }
    ) {
        Thumbnail(uri, size, size, Modifier.fillMaxSize())
    }
}

@Composable
private fun backToMusic(onBack: () -> Unit) = listOf(SideAction.back("back", stringResource(R.string.music_title), onBack))

@Composable
private fun SearchHeader(query: String, onQueryChange: (String) -> Unit, focusRequester: FocusRequester) {
    val spacing = SidekitTheme.spacing
    val description = stringResource(R.string.music_search_description)
    Box(Modifier.fillMaxWidth().height(spacing.headerHeight).padding(horizontal = spacing.itemPadding).padding(top = spacing.headerPaddingTop)) {
        SidekitTextField(query, onQueryChange, Modifier.fillMaxWidth().semantics { contentDescription = description }, textStyle = SidekitTheme.typography.title, placeholder = stringResource(R.string.music_search_placeholder), focusRequester = focusRequester, focusOnKeypad = true, singleLine = true, autoFocus = true)
    }
}

@SidephonePreview
@Composable
private fun MusicHomePreview() = SidekitPreviewFrame { MusicHomeScreen(emptyList(), MusicActions(), {}, {}, {}, {}, {}) }
