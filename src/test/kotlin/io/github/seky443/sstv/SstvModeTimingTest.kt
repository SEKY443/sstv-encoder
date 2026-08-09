package io.github.seky443.sstv

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the published mode timings.
 *
 * These numbers are the contract with every external decoder, and a drift of a few milliseconds
 * per line shears the received picture. A unit test is cheap insurance against someone "tidying
 * up" a constant.
 */
class SstvModeTimingTest {

    @Test
    fun `martin m1 line time matches the specification`() {
        // 4.862 ms sync + 0.572 ms porch + 3 x (146.432 ms scan + 0.572 ms separator)
        assertEquals(0.446446, SstvMode.MARTIN_M1.lineSeconds, 1e-9)
    }

    @Test
    fun `martin m1 scan time matches the specification`() {
        assertEquals(0.146432, SstvMode.MARTIN_M1.scanSeconds, 1e-9)
    }

    @Test
    fun `scottie s1 line time matches the specification`() {
        // 2 x (1.5 ms separator + 138.24 ms scan) + 9 ms sync + 1.5 ms porch + 138.24 ms scan
        assertEquals(0.42822, SstvMode.SCOTTIE_S1.lineSeconds, 1e-9)
    }

    @Test
    fun `a full martin m1 frame is about 114 seconds`() {
        assertEquals(114.29, SstvMode.MARTIN_M1.pictureSeconds, 0.01)
    }

    @Test
    fun `a full scottie s1 frame is about 110 seconds`() {
        assertEquals(109.62, SstvMode.SCOTTIE_S1.pictureSeconds, 0.01)
    }

    @Test
    fun `vis codes identify the modes`() {
        assertEquals(44, SstvMode.MARTIN_M1.visCode)
        assertEquals(60, SstvMode.SCOTTIE_S1.visCode)
    }

    @Test
    fun `every mode holds one frame worth of pixels`() {
        for (mode in SstvMode.entries) {
            assertEquals(mode.width * mode.height, mode.pixelCount)
        }
    }

    @Test
    fun `fromName is case insensitive and falls back to martin`() {
        assertEquals(SstvMode.SCOTTIE_S1, SstvMode.fromName("SCOTTIE_S1"))
        assertEquals(SstvMode.SCOTTIE_S1, SstvMode.fromName("scottie_s1"))
        assertEquals(SstvMode.MARTIN_M1, SstvMode.fromName(null))
        assertEquals(SstvMode.MARTIN_M1, SstvMode.fromName("nonsense"))
    }
}