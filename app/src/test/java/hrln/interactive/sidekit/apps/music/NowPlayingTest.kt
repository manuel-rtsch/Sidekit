package hrln.interactive.sidekit.apps.music

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import hrln.interactive.sidekit.design.action.ChromeState
import hrln.interactive.sidekit.design.action.LocalChrome
import hrln.interactive.sidekit.ui.SidekitPreviewFrame
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The parts of Now Playing that are behaviour rather than looks: the bar, seeking, and the empty queue. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NowPlayingTest {
    @get:Rule val compose = createComposeRule()

    private val track = Track(1, "Our Mission", 1, 3, "Yoshiruma Terui", "Hidden Inventory", 180_000)
    private val playing = PlaybackState(PlayQueue.of(listOf(track), 0, false, RepeatMode.Off), true, 42_000, durationMs = 180_000)

    /** Paused at 42s, so the position doesn't tick between a key press and its assertion. */
    private val paused = playing.copy(isPlaying = false)

    @Composable
    private fun NowPlaying(
        state: PlaybackState = playing,
        onSeek: (Long) -> Unit = {},
        onShuffle: () -> Unit = {},
        onRepeat: () -> Unit = {},
        onPlayPause: () -> Unit = {}
    ) = NowPlayingScreen(
        playback = state,
        onAlbum = {},
        onArtist = {},
        onPlayPause = onPlayPause,
        onPrevious = {},
        onNext = {},
        onSeek = onSeek,
        onShuffle = onShuffle,
        onRepeat = onRepeat,
        onBookmark = {},
        onBack = {}
    )

    @Test
    fun theBarHoldsBackAlbumArtistAndSave() {
        compose.setContent { SidekitPreviewFrame { NowPlaying() } }
        compose.onNodeWithContentDescription("Back to Music").assertIsDisplayed()
        compose.onNodeWithContentDescription("Album").assertIsDisplayed()
        compose.onNodeWithContentDescription("Artist").assertIsDisplayed()
        compose.onNodeWithContentDescription("Save").assertIsDisplayed()
    }

    @Test
    fun optionsHoldShuffleAndRepeat() {
        lateinit var chrome: ChromeState
        val calls = mutableListOf<String>()
        compose.setContent {
            SidekitPreviewFrame {
                chrome = LocalChrome.current!!
                NowPlaying(onShuffle = { calls += "shuffle" }, onRepeat = { calls += "repeat" })
            }
        }
        compose.runOnUiThread { chrome.toggleOptions() }
        compose.onNodeWithContentDescription("Shuffle: off").assertIsDisplayed().performClick()
        assertEquals(listOf("shuffle"), calls)
        compose.runOnUiThread { chrome.toggleOptions() }
        compose.onNodeWithContentDescription("Repeat: off").assertIsDisplayed().performClick()
        assertEquals(listOf("shuffle", "repeat"), calls)
    }

    @Test
    fun seekIsASliderThatCanBeScrubbed() {
        var sought = -1L
        compose.setContent { SidekitPreviewFrame { NowPlaying(paused, onSeek = { sought = it }) } }
        val seek = compose.onNodeWithContentDescription("Seek")
        val info = seek.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0f..1f, info.range)
        assertEquals(42_000f / 180_000f, info.current, 0.001f)
        seek.performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        assertEquals(90_000L, sought)
    }

    @Test
    fun theSeekBarStepsWithTheDpad() {
        var sought = -1L
        compose.setContent { SidekitPreviewFrame { NowPlaying(paused, onSeek = { sought = it }) } }
        compose.onNodeWithContentDescription("Seek").requestFocus()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(42_000L + 5_000L, sought)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        assertEquals(42_000L - 5_000L, sought)
    }

    @Test
    fun emptyQueueHasNoTransportOrSave() {
        var played = 0
        compose.setContent { SidekitPreviewFrame { NowPlaying(PlaybackState(), onPlayPause = { played++ }) } }
        compose.onNodeWithText("Nothing playing").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play").assertDoesNotExist()
        compose.onNodeWithContentDescription("Save").assertDoesNotExist()
        compose.onNodeWithContentDescription("Seek").assertDoesNotExist()
        assertEquals(0, played)
    }
}
