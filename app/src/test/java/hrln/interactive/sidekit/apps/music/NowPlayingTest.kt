package hrln.interactive.sidekit.apps.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** The parts of Now Playing that are behaviour rather than looks: saving, and seeking. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NowPlayingTest {
    @get:Rule val compose = createComposeRule()

    private val track = Track(1, "Our Mission", 1, 3, "Yoshiruma Terui", "Hidden Inventory", 180_000)
    private val playing = PlaybackState(PlayQueue.of(listOf(track), 0, false, RepeatMode.Off), true, 42_000, durationMs = 180_000)

    /** Paused at 42s, so the position doesn't tick between a key press and its assertion. */
    private val paused = playing.copy(isPlaying = false)

    @Test fun saveIsOneBarButtonThatReportsWhetherTheTrackIsSaved() {
        compose.setContent {
            var saved by remember { mutableStateOf(false) }
            SidekitPreviewFrame { NowPlayingScreen(playing, saved, NowPlayingActions(onSave = { saved = !saved })) }
        }
        compose.onNodeWithContentDescription("Save").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Saved").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Save").assertIsDisplayed()
    }

    @Test fun theBarHoldsOnlyBackAndSave() {
        compose.setContent { SidekitPreviewFrame { NowPlayingScreen(playing, false, NowPlayingActions()) } }
        // Album and Artist left the bar for the art, the artist line and Options.
        compose.onNodeWithContentDescription("Album").assertDoesNotExist()
        compose.onNodeWithContentDescription("Artist").assertDoesNotExist()
        compose.onNodeWithContentDescription("Music").assertIsDisplayed()
    }

    @Test fun optionsHoldTheWayToTheAlbumAndTheArtist() {
        lateinit var chrome: ChromeState
        val calls = mutableListOf<String>()
        compose.setContent {
            SidekitPreviewFrame {
                chrome = LocalChrome.current!!
                NowPlayingScreen(playing, false, NowPlayingActions(onAlbum = { calls += "album" }, onArtist = { calls += "artist" }))
            }
        }
        compose.runOnUiThread { chrome.toggleOptions() }
        compose.onNodeWithContentDescription("Album").assertIsDisplayed().performClick()
        assertEquals(listOf("album"), calls)
        compose.runOnUiThread { chrome.toggleOptions() }
        compose.onNodeWithContentDescription("Artist").assertIsDisplayed().performClick()
        assertEquals(listOf("album", "artist"), calls)
    }

    @Test fun seekIsASliderThatCanBeScrubbed() {
        var sought = -1L
        compose.setContent { SidekitPreviewFrame { NowPlayingScreen(paused, false, NowPlayingActions(onSeek = { sought = it })) } }
        val seek = compose.onNodeWithContentDescription("Seek")
        val info = seek.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0f..1f, info.range)
        assertEquals(42_000f / 180_000f, info.current, 0.001f)
        seek.performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        assertEquals(90_000L, sought)
    }

    @Test fun theSeekBarStepsWithTheDpad() {
        var sought = -1L
        compose.setContent { SidekitPreviewFrame { NowPlayingScreen(paused, false, NowPlayingActions(onSeek = { sought = it })) } }
        compose.onNodeWithContentDescription("Seek").requestFocus()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(42_000L + 5_000L, sought)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        assertEquals(42_000L - 5_000L, sought)
    }

    @Test fun emptyQueueHasNoTransportOrSave() {
        var played = 0
        compose.setContent { SidekitPreviewFrame { NowPlayingScreen(PlaybackState(), false, NowPlayingActions(onPlayPause = { played++ })) } }
        compose.onNodeWithText("Nothing playing").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play").assertDoesNotExist()
        compose.onNodeWithContentDescription("Save").assertDoesNotExist()
        compose.onNodeWithContentDescription("Seek").assertDoesNotExist()
        assertEquals(0, played)
    }
}
