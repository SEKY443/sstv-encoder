package io.github.seky443.sstv.image

import io.github.seky443.sstv.SstvMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage

/** Resampling has to hand the encoder exactly one frame of pixels, whatever came in. */
class AwtImagesTest {

    private val mode = SstvMode.MARTIN_M1

    private fun solid(width: Int, height: Int, color: Color): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { image ->
            val g = image.createGraphics()
            g.color = color
            g.fillRect(0, 0, width, height)
            g.dispose()
        }

    private fun IntArray.at(x: Int, y: Int) = this[y * mode.width + x]

    @Test
    fun `output is always exactly one frame of pixels`() {
        for (source in listOf(solid(64, 64, Color.RED), solid(1920, 1080, Color.RED))) {
            for (scaling in Scaling.entries) {
                assertEquals(mode.pixelCount, source.toSstvPixels(mode, scaling).size)
            }
        }
    }

    @Test
    fun `fit pads the frame with the background colour`() {
        // A tall image in a 320x256 frame leaves bars on the left and right.
        val pixels = solid(100, 400, Color.RED).toSstvPixels(mode, Scaling.FIT)

        assertEquals(0xFF000000.toInt(), pixels.at(0, mode.height / 2))
        assertEquals(0xFFFF0000.toInt(), pixels.at(mode.width / 2, mode.height / 2))
    }

    @Test
    fun `fit honours a custom background`() {
        val blue = 0xFF0000FF.toInt()
        val pixels = solid(100, 400, Color.RED).toSstvPixels(mode, Scaling.FIT, background = blue)

        assertEquals(blue, pixels.at(0, mode.height / 2))
    }

    @Test
    fun `fill covers the whole frame`() {
        val pixels = solid(100, 400, Color.RED).toSstvPixels(mode, Scaling.FILL)

        assertEquals(0xFFFF0000.toInt(), pixels.at(0, mode.height / 2))
        assertEquals(0xFFFF0000.toInt(), pixels.at(mode.width - 1, mode.height / 2))
    }

    @Test
    fun `pixels come back opaque so the encoder sees clean rgb`() {
        val pixels = solid(320, 256, Color.GREEN).toSstvPixels(mode, Scaling.STRETCH)

        assertEquals(0xFF, (pixels.at(10, 10) ushr 24) and 0xFF)
        assertEquals(0xFF00FF00.toInt(), pixels.at(10, 10))
    }
}