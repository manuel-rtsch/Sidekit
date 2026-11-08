package hrln.interactive.sidekit.apps.spotify

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import hrln.interactive.sidekit.ui.SidekitPreviewFrame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SidetracksScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { SidekitPreviewFrame(content) }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    private val recent = MockSpotifyDataSource.recentlyPlayed
    private val current = MockSpotifyDataSource.recentlyPlayed.first()
    private val state = SpotifyUiState(
        current = current,
        upcoming = recent.drop(1),
        history = recent.drop(3),
        recent = recent,
        libraryItems = MockSpotifyDataSource.library(LibraryFilter.Albums),
        searchResults = MockSpotifyDataSource.search("night", SearchScope.entries.toSet()),
        isPlaying = true,
        positionMs = 68_000L,
        savedTrackIds = setOf(current.id),
        filter = LibraryFilter.Albums
    )

    @Test fun home() = shoot("sidetracks_home") {
        SidetracksHomeScreen(
            state,
            onNowPlaying = {},
            onLibrary = {},
            onSearch = {},
            onSettings = {},
            onPlay = {},
            onDismissNotice = {}
        )
    }

    @Test fun library() = shoot("sidetracks_library_albums") {
        LibraryScreen(
            state,
            onBack = {},
            onSearch = {},
            onFilter = {},
            onTrack = {},
            onPlaylist = {},
            onAlbum = {},
            onArtist = {},
            onShow = {},
            onLoadMore = {},
            onDismissNotice = {}
        )
    }

    @Test fun album() = shoot("sidetracks_album") {
        CollectionScreen(
            MockSpotifyDataSource.collection(SpotifyCollectionType.Album, "night_signals")!!,
            state,
            onBack = {},
            onPlay = {},
            onItem = {},
            onSave = {},
            onArtist = {},
            onQueue = {},
            onNowPlaying = {},
            onLoadMore = {},
            onDismissNotice = {}
        )
    }

    @Test fun artist() = shoot("sidetracks_artist") {
        val artist = MockSpotifyDataSource.artists.first()
        ArtistScreen(
            artist,
            MockSpotifyDataSource.albumsBy(artist),
            MockSpotifyDataSource.topTracks(artist),
            state,
            onBack = {},
            onPlay = {},
            onTrack = {},
            onAlbum = {},
            onFollow = {},
            onNowPlaying = {},
            onDismissNotice = {}
        )
    }

    @Test fun search() = shoot("sidetracks_search") {
        SpotifySearchScreen(
            state.copy(query = "night"),
            onBack = {},
            onQuery = {},
            onClear = {},
            onToggleScope = {},
            onTrack = {},
            onArtist = {},
            onAlbum = {},
            onPlaylist = {},
            onShow = {},
            onDismissNotice = {}
        )
    }

    @Test fun nowPlaying() = shoot("sidetracks_now_playing") {
        SpotifyNowPlayingScreen(
            state,
            onBack = {},
            onPlayPause = {},
            onPrevious = {},
            onNext = {},
            onSeek = {},
            onShuffle = {},
            onRepeat = {},
            onSave = {},
            onQueue = {},
            onAlbum = {},
            onArtist = {},
            onAddToPlaylist = {},
            onDismissNotice = {}
        )
    }

    @Test fun queue() = shoot("sidetracks_queue") {
        QueueScreen(
            state,
            onBack = {},
            onNowPlaying = {},
            onJump = {},
            onPlayNext = {},
            onRemove = {},
            onClearUpcoming = { emptyList() },
            onRestoreUpcoming = {},
            onShuffle = {},
            onDismissNotice = {}
        )
    }

    @Test fun settings() = shoot("sidetracks_settings") {
        SpotifySettingsScreen(
            state,
            onBack = {},
            onAudioQuality = {},
            onVolume = {},
            onClearCache = {},
            onClearHistory = {},
            onSignOut = {},
            onNotice = {},
            onDismissNotice = {}
        )
    }

    @Test fun signIn() = shoot("sidetracks_sign_in") {
        SpotifySignInScreen(
            SpotifyUiState(signedIn = false),
            onBack = {},
            onLogIn = {},
            onCopyLink = {},
            onDismissNotice = {}
        )
    }
}
