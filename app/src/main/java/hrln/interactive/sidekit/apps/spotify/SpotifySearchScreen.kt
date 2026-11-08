package hrln.interactive.sidekit.apps.spotify

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import hrln.interactive.sidekit.design.action.ActionMotion
import hrln.interactive.sidekit.design.action.ActionTone
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.SidekitList
import hrln.interactive.sidekit.design.components.SidekitScaffold
import hrln.interactive.sidekit.design.components.SidekitTextField
import hrln.interactive.sidekit.design.components.listFade
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.theme.SidekitTheme

@Composable
fun SpotifySearchScreen(
    state: SpotifyUiState,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onClear: () -> Unit,
    onToggleScope: (SearchScope) -> Unit,
    onTrack: (SpotifyTrack) -> Unit,
    onArtist: (SpotifyArtist) -> Unit,
    onAlbum: (SpotifyAlbum) -> Unit,
    onPlaylist: (SpotifyPlaylist) -> Unit,
    onShow: (SpotifyShow) -> Unit,
    onDismissNotice: () -> Unit
) {
    var expandedTracks by remember(state.query) { mutableStateOf(false) }
    val results = MockSpotifyDataSource.search(state.query, state.searchScopes)
    val actions = withNotice(
        state.notice,
        onDismissNotice,
        buildList {
            add(spotifyBack("Back", onBack).first())
            if (state.query.isNotEmpty()) add(SideAction("clear", "Clear", SidekitIcon.Close, tone = ActionTone.Muted, onClick = onClear))
        }
    )
    val scopes = SearchScope.entries.map { scope ->
        SideAction(
            "scope.${scope.name}",
            scope.label,
            tone = if (scope in state.searchScopes) ActionTone.Primary else ActionTone.Muted,
            onClick = { onToggleScope(scope) }
        )
    }
    val focus = remember { FocusRequester() }
    SidekitScaffold(
        title = null,
        header = {
            Box(
                Modifier.fillMaxWidth().height(SidekitTheme.spacing.headerHeight)
                    .padding(horizontal = SidekitTheme.spacing.itemPadding).padding(top = SidekitTheme.spacing.headerPaddingTop)
            ) {
                SidekitTextField(
                    state.query,
                    onQuery,
                    Modifier.fillMaxWidth().semantics { contentDescription = "Search Spotify" },
                    textStyle = SidekitTheme.typography.title,
                    placeholder = "Search Spotify",
                    focusRequester = focus,
                    focusOnKeypad = true,
                    singleLine = true,
                    autoFocus = true
                )
            }
        },
        actions = actions,
        options = scopes
    ) { padding ->
        val listState = rememberLazyListState()
        SidekitList(padding, state = listState) {
            when {
                state.query.isBlank() -> item(key = "idle") {
                    EmptyMessage("Search your library and Spotify", "Tracks, artists, albums, playlists and shows")
                }

                state.query.trim().length < 2 -> item(key = "short") {
                    EmptyMessage("Keep typing", "Type at least two letters to search")
                }

                results.isEmpty -> item(key = "noResults") {
                    EmptyMessage("No results for \"${state.query.trim()}\"", "Try another spelling or turn on more scopes in Options")
                }

                else -> {
                    var slot = 0
                    if (results.tracks.isNotEmpty()) {
                        item(key = "tracksLabel") { SectionLabel("Tracks") }
                        val shown = if (expandedTracks) results.tracks else results.tracks.take(5)
                        itemsIndexed(shown, key = { _, track -> "t${track.id}" }) { index, track ->
                            SpotifyMediaRow(track, Modifier.listFade(listState, slot + index), secondary = track.artist) { onTrack(track) }
                        }
                        slot += shown.size
                        if (!expandedTracks && results.tracks.size > 5) {
                            item(key = "moreTracks") { TerminalRow("More tracks", secondary = "${results.tracks.size - 5} more", onClick = { expandedTracks = true }) }
                        }
                    }
                    if (results.artists.isNotEmpty()) {
                        item(key = "artistsLabel") { SectionLabel("Artists") }
                        itemsIndexed(results.artists, key = { _, artist -> "r${artist.id}" }) { index, artist ->
                            SpotifyLibraryRow(artist.artRes, artist.name, "${artist.albumIds.size} albums", Modifier.listFade(listState, slot + index)) { onArtist(artist) }
                        }
                        slot += results.artists.size
                    }
                    if (results.albums.isNotEmpty()) {
                        item(key = "albumsLabel") { SectionLabel("Albums") }
                        itemsIndexed(results.albums, key = { _, album -> "a${album.id}" }) { index, album ->
                            SpotifyLibraryRow(album.artRes, album.title, "${album.artist} · ${album.year}", Modifier.listFade(listState, slot + index)) { onAlbum(album) }
                        }
                        slot += results.albums.size
                    }
                    if (results.playlists.isNotEmpty()) {
                        item(key = "playlistsLabel") { SectionLabel("Playlists") }
                        itemsIndexed(results.playlists, key = { _, playlist -> "p${playlist.id}" }) { index, playlist ->
                            SpotifyLibraryRow(playlist.artRes, playlist.name, "${playlist.owner} · ${playlist.tracks.size} tracks", Modifier.listFade(listState, slot + index)) { onPlaylist(playlist) }
                        }
                        slot += results.playlists.size
                    }
                    if (results.shows.isNotEmpty()) {
                        item(key = "showsLabel") { SectionLabel("Shows") }
                        itemsIndexed(results.shows, key = { _, show -> "s${show.id}" }) { index, show ->
                            SpotifyLibraryRow(show.artRes, show.name, show.publisher, Modifier.listFade(listState, slot + index)) { onShow(show) }
                        }
                    }
                }
            }
        }
    }
}
