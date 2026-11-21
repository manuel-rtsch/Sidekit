package hrln.interactive.sidekit.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import hrln.interactive.sidekit.design.action.LocalHeaderOptionsInset
import hrln.interactive.sidekit.design.theme.SidekitSpacing
import hrln.interactive.sidekit.design.theme.SidekitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A custom header is drawn in the body, but the touch Options button is drawn above the screens:
 * the scaffold has to hand the header the band minus that corner, or a search field's text runs
 * under the three dots. With no button on screen the same header keeps the whole band.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [31])
class ScaffoldHeaderInsetTest {
    @get:Rule val compose = createComposeRule()

    private fun showHeader(inset: Dp) {
        compose.setContent {
            SidekitTheme {
                CompositionLocalProvider(LocalHeaderOptionsInset provides inset) {
                    SidekitScaffold(
                        title = null,
                        header = {
                            Box(
                                Modifier.fillMaxWidth()
                                    .height(SidekitTheme.spacing.headerHeight)
                                    .testTag("header")
                            )
                        }
                    ) {}
                }
            }
        }
        compose.waitForIdle()
    }

    /** The band the header is laid out in: the root, less the scaffold's page padding. */
    private fun panel(spacing: SidekitSpacing) = compose.onRoot().getUnclippedBoundsInRoot().width - spacing.pagePadding * 2

    @Test
    fun `the header stops where the options button starts`() {
        showHeader(76.dp)

        val spacing = SidekitSpacing()

        assertEquals(
            panel(spacing) - 76.dp,
            compose.onNodeWithTag("header").getUnclippedBoundsInRoot().width
        )
    }

    @Test
    fun `without the button the header keeps the full band`() {
        showHeader(0.dp)

        val spacing = SidekitSpacing()

        assertEquals(
            panel(spacing),
            compose.onNodeWithTag("header").getUnclippedBoundsInRoot().width
        )
    }
}
