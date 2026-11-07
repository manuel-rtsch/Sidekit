package hrln.interactive.sidekit.apps.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import hrln.interactive.sidekit.apps.media.MediaPermissions
import hrln.interactive.sidekit.apps.media.allowAction
import hrln.interactive.sidekit.apps.media.rememberPermissionState
import hrln.interactive.sidekit.navigation.SubApp

private val ROOT = SubApp.Music.route
private val HOME = "$ROOT/home"
private val PLAYLISTS = "$ROOT/playlists"
private val ALBUMS = "$ROOT/albums"
private val ARTISTS = "$ROOT/artists"
private val ALBUM = "$ROOT/album/{id}"
private val ARTIST = "$ROOT/artist/{id}"
private val NOW_PLAYING = "$ROOT/now-playing"
private val SEARCH = "$ROOT/search"
private fun albumRoute(id: Long) = "$ROOT/album/$id"
private fun artistRoute(id: Long) = "$ROOT/artist/$id"
private val idArg = listOf(navArgument("id") { type = NavType.LongType })

fun NavGraphBuilder.musicGraph(nav: NavHostController) {
    val backToMusic: () -> Unit = { nav.popBackStack(HOME, inclusive = false) }
    navigation(startDestination = HOME, route = ROOT) {
        composable(HOME) { entry ->
            val vm = entry.musicViewModel(nav)
            val permission = rememberPermissionState(MediaPermissions.audio)
            if (!permission.granted) {
                MusicPermissionScreen(permission.access, backToMusic)
                val allow = allowAction(permission, "music")
                MusicPermissionBar(allow)
                return@composable
            }
            val state by vm.state.collectAsStateWithLifecycle()
            MusicHomeScreen(state.home, vm.actions(nav), { nav.navigate(PLAYLISTS) }, { nav.navigate(ALBUMS) }, { nav.navigate(ARTISTS) }, { nav.navigate(NOW_PLAYING) }, {
                vm.clearSearch()
                nav.navigate(SEARCH)
            }, state.nowPlaying?.let { albumArtUri(it.albumId) }, state.nowPlaying?.albumId ?: 0, vm::createPlaylist)
        }
        composable(PLAYLISTS) { entry ->
            val vm = entry.musicViewModel(nav)
            val state by vm.state.collectAsStateWithLifecycle()
            MusicLibraryListScreen("Playlists", state.library.playlists.map(LibraryItem::PlaylistItem), vm.actions(nav), backToMusic, vm::createPlaylist)
        }
        composable(ALBUMS) { entry ->
            val vm = entry.musicViewModel(nav)
            val state by vm.state.collectAsStateWithLifecycle()
            MusicLibraryListScreen("Albums", state.library.albums.map(LibraryItem::AlbumItem), vm.actions(nav), backToMusic)
        }
        composable(ARTISTS) { entry ->
            val vm = entry.musicViewModel(nav)
            val state by vm.state.collectAsStateWithLifecycle()
            MusicLibraryListScreen("Artists", state.library.artists.map(LibraryItem::ArtistItem), vm.actions(nav), backToMusic)
        }
        composable(ALBUM, arguments = idArg) { entry ->
            val vm = entry.musicViewModel(nav)
            val state by vm.state.collectAsStateWithLifecycle()
            val album = state.library.album(entry.arguments?.getLong("id") ?: -1) ?: return@composable
            val tracks = state.library.tracksOf(album)
            AlbumScreen(
                album,
                tracks,
                state.library.areSaved(tracks.map { it.id }),
                { nav.navigate(artistRoute(album.artistId)) },
                {
                    vm.playAlbum(album.id)
                    nav.navigate(NOW_PLAYING)
                },
                {
                    vm.playTrack(it.id)
                    nav.navigate(NOW_PLAYING)
                },
                { vm.toggleSaved(tracks) },
                backToMusic
            )
        }
        composable(ARTIST, arguments = idArg) { entry ->
            val vm = entry.musicViewModel(nav)
            val state by vm.state.collectAsStateWithLifecycle()
            val artist = state.library.artist(entry.arguments?.getLong("id") ?: -1) ?: return@composable
            ArtistScreen(artist, state.library.albumsBy(artist), {
                vm.playArtist(artist.id)
                nav.navigate(NOW_PLAYING)
            }, { nav.navigate(albumRoute(it.id)) }, backToMusic)
        }
        composable(NOW_PLAYING) { entry ->
            val vm = entry.musicViewModel(nav)
            val state by vm.state.collectAsStateWithLifecycle()
            val track = state.nowPlaying
            val album = track?.let { state.library.album(it.albumId) }
            NowPlayingScreen(
                state.playback,
                track != null && state.library.isSaved(track.id),
                NowPlayingActions(
                    onPlayPause = vm::togglePlayPause,
                    onPrevious = vm::skipPrevious,
                    onNext = vm::skipNext,
                    onSeek = vm::seekTo,
                    onShuffle = { vm.setShuffle(!state.playback.queue.shuffle) },
                    onRepeat = vm::cycleRepeat,
                    onSave = { track?.let { vm.toggleSaved(it) } },
                    onAlbum = { album?.let { nav.navigate(albumRoute(it.id)) } },
                    onArtist = { track?.let { nav.navigate(artistRoute(it.artistId)) } },
                    onBack = backToMusic
                )
            )
        }
        composable(SEARCH) { entry ->
            val vm = entry.musicViewModel(nav)
            val state by vm.state.collectAsStateWithLifecycle()
            MusicSearchScreen(state.query, state.results, vm::updateQuery, vm.actions(nav), backToMusic)
        }
    }
}

@Composable
private fun MusicPermissionBar(action: hrln.interactive.sidekit.design.action.SideAction) {
    hrln.interactive.sidekit.design.action.PublishChrome(hrln.interactive.sidekit.design.action.HeaderSpec("Music"), listOf(action))
}

private fun MusicViewModel.actions(nav: NavHostController) = MusicActions(
    openPlaylist = {
        playPlaylist(it.id)
        nav.navigate(NOW_PLAYING)
    },
    openAlbum = { nav.navigate(albumRoute(it.id)) },
    openArtist = { nav.navigate(artistRoute(it.id)) },
    openTrack = {
        playTrack(it.id)
        nav.navigate(NOW_PLAYING)
    }
)

@Composable
private fun NavBackStackEntry.musicViewModel(nav: NavHostController): MusicViewModel {
    val parent = remember(this) { nav.getBackStackEntry(ROOT) }
    val context = LocalContext.current.applicationContext
    return viewModel(
        parent,
        factory = remember(context) {
            viewModelFactory { initializer { MusicViewModel(MediaStoreMusicSource(context.contentResolver), Playlists.repository(context), MusicPlayback.get(context)) } }
        }
    )
}
