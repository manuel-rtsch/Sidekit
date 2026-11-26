package hrln.interactive.sidekit.design

import hrln.interactive.sidekit.design.canvas.DesignCanvas
import hrln.interactive.sidekit.design.canvas.SidephoneCanvas
import hrln.interactive.sidekit.design.components.ListFade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListFadeTest {
    private val pitch = 107 // 104 row + 3 item-outer-gap

    @Test fun `rows step down 0_2 per slot like the mocks`() {
        assertEquals(1.0f, ListFade.alpha(0, pitch), 0.001f)
        assertEquals(0.8f, ListFade.alpha(pitch, pitch), 0.001f)
        assertEquals(0.6f, ListFade.alpha(2 * pitch, pitch), 0.001f)
        assertEquals(0.4f, ListFade.alpha(3 * pitch, pitch), 0.001f)
    }

    @Test fun `alpha is continuous between slots while scrolling`() {
        assertEquals(0.9f, ListFade.alpha(pitch / 2, pitch), 0.01f)
    }

    @Test fun `rows scrolled above the top stay opaque and deep rows clamp`() {
        assertEquals(1f, ListFade.alpha(-50, pitch), 0f)
        assertEquals(ListFade.MIN_ALPHA, ListFade.alpha(20 * pitch, pitch), 0f)
    }
}

class DesignCanvasTest {
    @Test fun `sidephone panel maps one figma px to one physical px`() {
        assertEquals(1f, DesignCanvas.density(480, 640), 0f)
    }

    @Test fun `higher resolution panels scale the canvas uniformly`() {
        assertEquals(2f, DesignCanvas.density(960, 1280), 0f)
    }

    @Test fun `tall keypad panels keep the design width and gain height`() {
        assertEquals(0.5f, DesignCanvas.density(240, 320), 0f)
        assertEquals(2f / 3f, DesignCanvas.density(320, 480), 0.0001f)
        assertEquals(0.75f, DesignCanvas.density(360, 640), 0f)
        assertEquals(1f, DesignCanvas.density(480, 800), 0f)
        assertEquals(2.25f, DesignCanvas.density(1080, 2400), 0f)

        val tallScale = DesignCanvas.density(360, 640)
        assertEquals(360f, DesignCanvas.WIDTH * tallScale, 0.0001f)
        assertEquals(480f, 360 / tallScale, 0.0001f)
        assertEquals(640f / 0.75f, 640 / tallScale, 0.0001f)
    }

    @Test fun `wide panels scale by height and leave side bars`() {
        val wideScale = DesignCanvas.density(800, 600)
        assertEquals(0.9375f, wideScale, 0f)
        assertEquals(640f, 600 / wideScale, 0.0001f)
        assertTrue(DesignCanvas.WIDTH * wideScale < 800f)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `the old sidephone name forwards to the design canvas`() {
        assertEquals(DesignCanvas.density(360, 640), SidephoneCanvas.density(360, 640), 0f)
        assertEquals(DesignCanvas.WIDTH, SidephoneCanvas.WIDTH)
        assertEquals(DesignCanvas.MIN_HEIGHT, SidephoneCanvas.HEIGHT)
    }
}
