package hrln.interactive.sidekit.apps.spotify

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SidetracksLinksTest {
    @Test fun `the playback notification action asks for now playing`() {
        assertTrue(SidetracksLinks.handle(Intent(SidetracksLinks.ACTION_OPEN_NOW_PLAYING)))
    }

    @Test fun `unrelated intents are ignored`() {
        assertFalse(SidetracksLinks.handle(Intent(Intent.ACTION_VIEW)))
        assertFalse(SidetracksLinks.handle(null))
    }
}
