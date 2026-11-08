package hrln.interactive.sidekit.apps.spotify

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import hrln.interactive.sidekit.apps.spotify.data.SpotifySessions
import hrln.interactive.sidekit.design.components.SidekitScaffold
import hrln.interactive.sidekit.navigation.LAUNCHER_ROUTE
import hrln.interactive.sidekit.navigation.SubApp

private val ROOT = SubApp.Sidetracks.route
private val HOME = "$ROOT/home"
private val LIBRARY = "$ROOT/library"
private val COLLECTION = "$ROOT/collection/{type}/{id}"
private val ARTIST = "$ROOT/artist/{id}"
private val SEARCH = "$ROOT/search"
private val NOW_PLAYING = "$ROOT/now-playing"
private val QUEUE = "$ROOT/queue"
private val SETTINGS = "$ROOT/settings"
private val SIGN_IN = "$ROOT/sign-in"

private fun collectionRoute(type: SpotifyCollectionType, id: String) = "$ROOT/collection/${type.route}/$id"
private fun artistRoute(id: String) = "$ROOT/artist/$id"

private val collectionArgs = listOf(
    navArgument("type") { type = NavType.StringType },
    navArgument("id") { type = NavType.StringType }
)
private val artistArgs = listOf(navArgument("id") { type = NavType.StringType })

/** Registers Sidetracks as one Sidekit sub-app. All screens share one mock account state holder. */
fun NavGraphBuilder.sidetracksGraph(nav: NavHostController) {
    navigation(startDestination = HOME, route = ROOT) {
        composable(HOME) { entry ->
            val vm = entry.spotifyViewModel(nav)
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.signedIn) {
                if (!state.signedIn) nav.navigate(SIGN_IN) { launchSingleTop = true }
            }
            SidetracksHomeScreen(
                state,
                onNowPlaying = { nav.navigate(NOW_PLAYING) },
                onLibrary = { nav.navigate(LIBRARY) },
                onSearch = { nav.navigate(SEARCH) },
                onSettings = { nav.navigate(SETTINGS) },
                onPlay = vm::playLibraryTrack,
                onDismissNotice = vm::dismissNotice
            )
        }
        composable(LIBRARY) { entry ->
            val vm = entry.spotifyViewModel(nav)
            val state by vm.state.collectAsStateWithLifecycle()
            LibraryScreen(
                state,
                onBack = { nav.popBackStack() },
                onSearch = { nav.navigate(SEARCH) },
                onFilter = vm::setFilter,
                onTrack = vm::playItem,
                onPlaylist = { nav.navigate(collectionRoute(SpotifyCollectionType.Playlist, it.id)) },
                onAlbum = { nav.navigate(collectionRoute(SpotifyCollectionType.Album, it.id)) },
                onArtist = { nav.navigate(artistRoute(it.id)) },
                onShow = { nav.navigate(collectionRoute(SpotifyCollectionType.Show, it.id)) },
                onLoadMore = { vm.notice("Everything in this filter is already loaded") },
                onDismissNotice = vm::dismissNotice
            )
        }
        composable(COLLECTION, arguments = collectionArgs) { entry ->
            val vm = entry.spotifyViewModel(nav)
            val state by vm.state.collectAsStateWithLifecycle()
            val type = SpotifyCollectionType.entries.firstOrNull { it.route == entry.arguments?.getString("type") }
            val id = entry.arguments?.getString("id").orEmpty()
            val collection = type?.let { vm.collection(it, id) }
            if (collection == null) {
                MissingCollectionScreen(onBack = { nav.popBackStack() })
            } else {
                CollectionScreen(
                    collection,
                    state,
                    onBack = { nav.popBackStack() },
                    onPlay = { vm.playCollection(collection, 0) },
                    onItem = { vm.playCollection(collection, it) },
                    onSave = { vm.toggleSaved(collection) },
                    onArtist = {
                        collection.artistId?.let { nav.navigate(artistRoute(it)) } ?: vm.notice("This collection has no artist page")
                    },
                    onQueue = { collection.items.firstOrNull()?.let(vm::addToQueue) },
                    onNowPlaying = { nav.navigate(NOW_PLAYING) },
                    onLoadMore = { vm.notice("Everything in this collection is already loaded") },
                    onDismissNotice = vm::dismissNotice
                )
            }
        }
        composable(ARTIST, arguments = artistArgs) { entry ->
            val vm = entry.spotifyViewModel(nav)
            val state by vm.state.collectAsStateWithLifecycle()
            val artist = vm.artist(entry.arguments?.getString("id").orEmpty())
            if (artist == null) {
                MissingCollectionScreen(onBack = { nav.popBackStack() })
            } else {
                val albums = vm.albumsBy(artist)
                val tracks = vm.topTracks(artist)
                ArtistScreen(
                    artist,
                    albums,
                    tracks,
                    state,
                    onBack = { nav.popBackStack() },
                    onPlay = { vm.playArtist(artist) },
                    onTrack = { vm.play(tracks, tracks.indexOf(it)) },
                    onAlbum = { nav.navigate(collectionRoute(SpotifyCollectionType.Album, it.id)) },
                    onFollow = { vm.toggleFollow(artist) },
                    onNowPlaying = { nav.navigate(NOW_PLAYING) },
                    onDismissNotice = vm::dismissNotice
                )
            }
        }
        composable(SEARCH) { entry ->
            val vm = entry.spotifyViewModel(nav)
            val state by vm.state.collectAsStateWithLifecycle()
            SpotifySearchScreen(
                state,
                onBack = { nav.popBackStack() },
                onQuery = vm::setQuery,
                onClear = vm::clearQuery,
                onToggleScope = vm::toggleScope,
                onTrack = vm::playItem,
                onArtist = { nav.navigate(artistRoute(it.id)) },
                onAlbum = { nav.navigate(collectionRoute(SpotifyCollectionType.Album, it.id)) },
                onPlaylist = { nav.navigate(collectionRoute(SpotifyCollectionType.Playlist, it.id)) },
                onShow = { nav.navigate(collectionRoute(SpotifyCollectionType.Show, it.id)) },
                onDismissNotice = vm::dismissNotice
            )
        }
        composable(NOW_PLAYING) { entry ->
            val vm = entry.spotifyViewModel(nav)
            val state by vm.state.collectAsStateWithLifecycle()
            SpotifyNowPlayingScreen(
                state,
                onBack = { nav.popBackStack() },
                onPlayPause = vm::togglePlayPause,
                onPrevious = vm::previous,
                onNext = vm::next,
                onSeek = vm::seekTo,
                onShuffle = vm::toggleShuffle,
                onRepeat = vm::cycleRepeat,
                onSave = vm::toggleSaveCurrent,
                onQueue = { nav.navigate(QUEUE) },
                onAlbum = {
                    (state.current as? SpotifyTrack)?.let { track ->
                        nav.navigate(collectionRoute(SpotifyCollectionType.Album, track.albumId))
                    }
                },
                onArtist = {
                    state.current?.artistId?.let { nav.navigate(artistRoute(it)) }
                },
                onAddToPlaylist = vm::addCurrentToPlaylist,
                onDismissNotice = vm::dismissNotice
            )
        }
        composable(QUEUE) { entry ->
            val vm = entry.spotifyViewModel(nav)
            val state by vm.state.collectAsStateWithLifecycle()
            QueueScreen(
                state,
                onBack = { nav.popBackStack() },
                onNowPlaying = { nav.popBackStack(NOW_PLAYING, inclusive = false) },
                onJump = vm::jumpTo,
                onPlayNext = vm::moveToNext,
                onRemove = vm::removeUpcoming,
                onClearUpcoming = vm::clearUpcoming,
                onRestoreUpcoming = vm::restoreUpcoming,
                onShuffle = vm::toggleShuffle,
                onDismissNotice = vm::dismissNotice
            )
        }
        composable(SETTINGS) { entry ->
            val vm = entry.spotifyViewModel(nav)
            val state by vm.state.collectAsStateWithLifecycle()
            SpotifySettingsScreen(
                state,
                onBack = { nav.popBackStack() },
                onAudioQuality = vm::cycleAudioQuality,
                onVolume = vm::setVolume,
                onClearCache = vm::clearCache,
                onClearHistory = vm::clearHistory,
                onSignOut = {
                    vm.signOut()
                    nav.navigate(SIGN_IN) { launchSingleTop = true }
                },
                onNotice = vm::notice,
                onDismissNotice = vm::dismissNotice
            )
        }
        composable(SIGN_IN) { entry ->
            val vm = entry.spotifyViewModel(nav)
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.signedIn) {
                if (state.signedIn) {
                    nav.navigate(HOME) {
                        popUpTo(HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
            SpotifySignInScreen(
                state,
                onBack = { nav.popBackStack(LAUNCHER_ROUTE, inclusive = false) },
                onLogIn = vm::signIn,
                onCopyLink = { vm.notice("Login link copied") },
                onDismissNotice = vm::dismissNotice
            )
        }
    }
}

@Composable
private fun MissingCollectionScreen(onBack: () -> Unit) {
    SidekitScaffold(title = "Sidetracks", actions = spotifyBack("Back", onBack)) { padding ->
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier.padding(padding)
        ) {
            EmptyMessage("This collection isn't available", "Go back and choose another")
        }
    }
}

@Composable
private fun NavBackStackEntry.spotifyViewModel(nav: NavHostController): SpotifyViewModel {
    val parent = remember(this) { nav.getBackStackEntry(ROOT) }
    val context = LocalContext.current.applicationContext
    val session = remember(context) { SpotifySessions.get(context) }
    val openBrowser: suspend (String) -> Unit = remember(context) {
        { url ->
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
    return viewModel(
        parent,
        factory = remember(session, openBrowser) {
            viewModelFactory {
                initializer { SpotifyViewModel(session = session, openBrowser = openBrowser) }
            }
        }
    )
}
