package hrln.interactive.sidekit.apps.spotify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import hrln.interactive.sidekit.design.action.ActionMotion
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.SidekitItem
import hrln.interactive.sidekit.design.components.SidekitItemRow
import hrln.interactive.sidekit.design.components.SidekitList
import hrln.interactive.sidekit.design.components.SidekitScaffold
import hrln.interactive.sidekit.design.components.SidekitText
import hrln.interactive.sidekit.design.components.listFade
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.theme.SidekitTheme

@Composable
fun SidetracksHomeScreen(
    state: SpotifyUiState,
    onNowPlaying: () -> Unit,
    onLibrary: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onPlay: (SpotifyTrack) -> Unit,
    onDismissNotice: () -> Unit
) {
    val recent = state.recent.take(10)
    val bar = withNotice(
        state.notice,
        onDismissNotice,
        listOf(
            SideAction(
                "nowPlaying",
                square = true,
                contentDescription = "Now Playing",
                artKey = state.current?.id,
                art = {
                    if (state.current != null) {
                        SpotifyCover(state.current.artRes, Modifier.height(SidekitTheme.spacing.tileIconSize))
                    } else {
                        hrln.interactive.sidekit.design.components.SidekitIconImage(SidekitIcon.MusicNote2)
                    }
                },
                onClick = onNowPlaying
            ),
            SideAction("search", "Search", SidekitIcon.Search, motion = ActionMotion.Forward, onClick = onSearch)
        )
    )
    SidekitScaffold(
        title = "Sidetracks",
        actions = bar,
        options = listOf(SideAction("settings", "Settings", SidekitIcon.Settings, onClick = onSettings))
    ) { padding ->
        val listState = rememberLazyListState()
        SidekitList(padding, state = listState) {
            item(key = "tiles") {
                SidekitItemRow(Modifier.listFade(listState, 0)) {
                    NowPlayingTile(state.current, onNowPlaying, Modifier.weight(1.38f))
                    LibraryTile(onLibrary, Modifier.weight(1f))
                }
            }
            item(key = "recentLabel") { SectionLabel("Recently played") }
            if (recent.isEmpty()) {
                item(key = "empty") { EmptyMessage("Nothing played yet", "Search to find your first track") }
            } else {
                itemsIndexed(recent, key = { _, track -> track.id }) { index, track ->
                    SpotifyMediaRow(
                        track,
                        Modifier.listFade(listState, index + 2),
                        secondary = track.artist,
                        onClick = { onPlay(track) }
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    state: SpotifyUiState,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onFilter: (LibraryFilter) -> Unit,
    onTrack: (SpotifyTrack) -> Unit,
    onPlaylist: (SpotifyPlaylist) -> Unit,
    onAlbum: (SpotifyAlbum) -> Unit,
    onArtist: (SpotifyArtist) -> Unit,
    onShow: (SpotifyShow) -> Unit,
    onLoadMore: () -> Unit,
    onDismissNotice: () -> Unit
) {
    val colors = SidekitTheme.colors
    val actions = withNotice(
        state.notice,
        onDismissNotice,
        listOf(spotifyBack("Sidetracks", onBack).first(), SideAction("search", "Search", SidekitIcon.Search, motion = ActionMotion.Forward, onClick = onSearch))
    )
    val filters = LibraryFilter.entries.map { filter ->
        SideAction(
            "filter.${filter.name}",
            filter.label,
            tone = if (filter == state.filter) ActionTone.Primary else ActionTone.Muted,
            onClick = { onFilter(filter) }
        )
    }
    SidekitScaffold(
        title = null,
        header = {
            Column(
                Modifier.fillMaxWidth().height(SidekitTheme.spacing.headerHeight)
                    .padding(horizontal = SidekitTheme.spacing.itemPadding).padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                SidekitText("Library", style = SidekitTheme.typography.headline)
                SidekitText(state.filter.label, style = SidekitTheme.typography.caption, color = colors.surfaceA6)
            }
        },
        actions = actions,
        options = filters
    ) { padding ->
        val listState = rememberLazyListState()
        val first = remember { FocusRequester() }
        LaunchedEffect(state.filter) { runCatching { first.requestFocus() } }
        SidekitList(padding, state = listState) {
            val items = state.libraryItems
            if (items.isEmpty()) {
                item(key = "empty") { EmptyMessage(state.filter.empty, "Search to find something to save") }
            }
            itemsIndexed(items, key = { index, item -> "$index:${libraryKey(item)}" }) { index, item ->
                val modifier = Modifier.listFade(listState, index).then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                when (item) {
                    is SpotifyTrack -> SpotifyMediaRow(item, modifier, secondary = item.artist, onClick = { onTrack(item) })
                    is SpotifyPlaylist -> SpotifyLibraryRow(item.artRes, item.name, "${item.owner} · ${item.tracks.size} tracks", modifier) { onPlaylist(item) }
                    is SpotifyAlbum -> SpotifyLibraryRow(item.artRes, item.title, "${item.artist} · ${item.year}", modifier) { onAlbum(item) }
                    is SpotifyArtist -> SpotifyLibraryRow(item.artRes, item.name, "${item.albumIds.size} albums", modifier) { onArtist(item) }
                    is SpotifyShow -> SpotifyLibraryRow(item.artRes, item.name, item.publisher, modifier) { onShow(item) }
                    else -> Unit
                }
            }
            if (items.isNotEmpty()) {
                item(key = "more") {
                    TerminalRow(
                        "Load more",
                        secondary = when (state.filter) {
                            LibraryFilter.LikedSongs -> "${items.size} liked songs"
                            LibraryFilter.Playlists -> "${items.size} playlists"
                            LibraryFilter.Albums -> "${items.size} albums"
                            LibraryFilter.Artists -> "${items.size} artists"
                            LibraryFilter.Shows -> "${items.size} shows"
                        },
                        onClick = onLoadMore
                    )
                }
            }
        }
    }
}

@Composable
fun CollectionScreen(
    collection: SpotifyCollection,
    state: SpotifyUiState,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onItem: (Int) -> Unit,
    onSave: () -> Unit,
    onArtist: () -> Unit,
    onQueue: () -> Unit,
    onNowPlaying: () -> Unit,
    onLoadMore: () -> Unit,
    onDismissNotice: () -> Unit
) {
    val colors = SidekitTheme.colors
    val saved = "${collection.type.route}:${collection.id}" in state.savedCollectionKeys
    val actions = withNotice(state.notice, onDismissNotice, spotifyBack("Back", onBack))
    val options = buildList {
        add(
            SideAction(
                "save",
                if (saved) "Saved" else "Save",
                if (saved) SidekitIcon.Check else SidekitIcon.Bookmark,
                tone = if (saved) ActionTone.Secondary else ActionTone.Primary,
                onClick = onSave
            )
        )
        if (collection.artistId != null) add(SideAction("artist", "Artist", SidekitIcon.Artist, onClick = onArtist))
        collection.items.firstOrNull()?.let { add(SideAction("queue", "Add to queue", SidekitIcon.QueueMusic, onClick = onQueue)) }
        add(SideAction("nowPlaying", "Go to now playing", SidekitIcon.MusicNote2, onClick = onNowPlaying))
    }
    SidekitScaffold(title = collection.title, actions = actions, options = options) { padding ->
        val listState = rememberLazyListState()
        val playFocus = remember { FocusRequester() }
        LaunchedEffect(collection.type, collection.id) { runCatching { playFocus.requestFocus() } }
        SidekitList(padding, state = listState) {
            item(key = "tiles") {
                SidekitItemRow(Modifier.listFade(listState, 0)) {
                    ArtworkTile(collection.artRes, "${collection.title} artwork", Modifier.weight(1f))
                    PlayTile(onPlay, Modifier.weight(1f).focusRequester(playFocus))
                    SpotifyIconTile(
                        if (saved) SidekitIcon.Check else SidekitIcon.Bookmark,
                        if (saved) "Saved" else "Save",
                        onSave,
                        if (saved) SpotifyTileTone.On else SpotifyTileTone.Neutral
                    )
                    if (collection.artistId != null) {
                        SpotifyIconTile(SidekitIcon.Artist, "Artist", onArtist)
                    }
                }
            }
            item(key = "meta") {
                SidekitText(
                    collection.subtitle,
                    Modifier.padding(start = 18.dp, end = 18.dp),
                    style = SidekitTheme.typography.caption,
                    color = colors.surfaceA6,
                    maxLines = 1
                )
            }
            itemsIndexed(collection.items, key = { _, item -> item.id }) { index, item ->
                SpotifyMediaRow(
                    item,
                    Modifier.listFade(listState, index + 2),
                    index = index,
                    showArt = false,
                    secondary = if (item is SpotifyEpisode) item.published else item.creator,
                    onClick = { onItem(index) }
                )
            }
            item(key = "more") { TerminalRow("Load more", onClick = onLoadMore) }
        }
    }
}

@Composable
fun ArtistScreen(
    artist: SpotifyArtist,
    albums: List<SpotifyAlbum>,
    tracks: List<SpotifyTrack>,
    state: SpotifyUiState,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onTrack: (SpotifyTrack) -> Unit,
    onAlbum: (SpotifyAlbum) -> Unit,
    onFollow: () -> Unit,
    onNowPlaying: () -> Unit,
    onDismissNotice: () -> Unit
) {
    val followed = artist.id in state.followedArtistIds
    val actions = withNotice(state.notice, onDismissNotice, spotifyBack("Back", onBack))
    SidekitScaffold(
        title = artist.name,
        actions = actions,
        options = listOf(
            SideAction("follow", if (followed) "Following" else "Follow", tone = if (followed) ActionTone.Secondary else ActionTone.Primary, onClick = onFollow),
            SideAction("nowPlaying", "Go to now playing", SidekitIcon.MusicNote2, onClick = onNowPlaying)
        )
    ) { padding ->
        val listState = rememberLazyListState()
        SidekitList(padding, state = listState) {
            item(key = "tiles") {
                SidekitItemRow(Modifier.listFade(listState, 0)) {
                    PlayTile(onPlay, Modifier.weight(1f))
                    ArtworkTile(artist.artRes, "${artist.name} artwork", Modifier.weight(1f))
                }
            }
            item(key = "topLabel") { SectionLabel("Top tracks") }
            itemsIndexed(tracks.take(10), key = { _, track -> track.id }) { index, track ->
                SpotifyMediaRow(track, Modifier.listFade(listState, index + 2), secondary = track.artist, onClick = { onTrack(track) })
            }
            item(key = "albumsLabel") { SectionLabel("Albums") }
            itemsIndexed(albums, key = { _, album -> album.id }) { index, album ->
                SpotifyLibraryRow(
                    album.artRes,
                    album.title,
                    "${album.year} · ${album.tracks.size} tracks",
                    Modifier.listFade(listState, index + tracks.size + 3)
                ) { onAlbum(album) }
            }
        }
    }
}

private fun libraryKey(item: Any): String = when (item) {
    is SpotifyTrack -> "t${item.id}"
    is SpotifyPlaylist -> "p${item.id}"
    is SpotifyAlbum -> "a${item.id}"
    is SpotifyArtist -> "r${item.id}"
    is SpotifyShow -> "s${item.id}"
    else -> item.toString()
}
