package io.github.seky443.sstv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Verifies the generated waveform, not just the timing constants.
 *
 * The audio is the entire product: if the tones are wrong, no external decoder can read the
 * picture. These tests measure the actual frequency of each part of the signal by counting zero
 * crossings.
 */
class SstvEncoderTest {

    private val mode = SstvMode.MARTIN_M1
    private val sampleRate = SstvEncoder.DEFAULT_SAMPLE_RATE

    /** Leader, break, leader, then ten 30 ms VIS bit slots. */
    private val headerSeconds = SstvEncoder.HEADER_SECONDS

    private fun solidImage(color: Int, mode: SstvMode = this.mode): IntArray =
        IntArray(mode.pixelCount) { color }

    /**
     * Estimates the dominant frequency of a slice by counting zero crossings. Accurate to a few Hz
     * over a window of this length, which is far tighter than the 200 Hz spacing between the tones
     * that have to be distinguished.
     */
    private fun frequencyOf(pcm: ShortArray, fromSeconds: Double, toSeconds: Double): Double {
        val from = (fromSeconds * sampleRate).toInt()
        val to = (toSeconds * sampleRate).toInt().coerceAtMost(pcm.size - 1)
        require(to > from) { "Empty window" }

        var crossings = 0
        for (i in (from + 1)..to) {
            val previous = pcm[i - 1]
            val current = pcm[i]
            if ((previous < 0 && current >= 0) || (previous >= 0 && current < 0)) crossings++
        }
        return crossings / 2.0 / (toSeconds - fromSeconds)
    }

    private fun assertFrequency(expected: Double, actual: Double, toleranceHz: Double) {
        assertTrue(
            "Expected ~${expected}Hz but measured ${"%.1f".format(actual)}Hz",
            abs(actual - expected) <= toleranceHz
        )
    }

    @Test
    fun `header length matches the published leader and vis timings`() {
        assertEquals(0.91, headerSeconds, 1e-9)
    }

    @Test
    fun `signal length matches the mode timings`() {
        val signal = SstvEncoder.encode(solidImage(BLACK), mode)
        val expectedSeconds =
            headerSeconds + mode.pictureSeconds + signal.trailingSilenceSeconds

        assertEquals(expectedSeconds, signal.totalSeconds, 0.001)
        assertEquals(sampleRate, signal.sampleRate)
    }

    @Test
    fun `transmission opens with the 1900Hz leader tone`() {
        val signal = SstvEncoder.encode(solidImage(BLACK), mode)
        assertFrequency(1900.0, frequencyOf(signal.pcm, 0.02, 0.28), 20.0)
    }

    @Test
    fun `vis code identifies martin 1`() {
        val signal = SstvEncoder.encode(solidImage(BLACK), mode)
        // Bit slots start after leader, break, leader and the 30 ms start bit.
        val firstBitStart = 0.61 + 0.03

        // Martin 1 is VIS 44 = 0b0101100, sent LSB first. A 1 bit is 1100 Hz, a 0 bit is 1300 Hz.
        val expectedBits = listOf(0, 0, 1, 1, 0, 1, 0)
        expectedBits.forEachIndexed { index, bit ->
            val slotStart = firstBitStart + 0.03 * index
            // Sample the middle of the slot to stay clear of the transitions.
            val measured = frequencyOf(signal.pcm, slotStart + 0.005, slotStart + 0.025)
            val expectedHz = if (bit == 1) 1100.0 else 1300.0
            assertFrequency(expectedHz, measured, 60.0)
        }
    }

    @Test
    fun `vis code identifies scottie 1`() {
        val scottie = SstvMode.SCOTTIE_S1
        val signal = SstvEncoder.encode(solidImage(BLACK, scottie), scottie)
        val firstBitStart = 0.61 + 0.03

        // Scottie 1 is VIS 60 = 0b0111100, sent LSB first.
        val expectedBits = listOf(0, 0, 1, 1, 1, 1, 0)
        expectedBits.forEachIndexed { index, bit ->
            val slotStart = firstBitStart + 0.03 * index
            val measured = frequencyOf(signal.pcm, slotStart + 0.005, slotStart + 0.025)
            assertFrequency(if (bit == 1) 1100.0 else 1300.0, measured, 60.0)
        }
    }

    @Test
    fun `white pixels transmit at the 2300Hz white frequency`() {
        val signal = SstvEncoder.encode(solidImage(WHITE), mode)
        // Inside the green sweep of the first line, clear of sync and separator pulses.
        assertFrequency(2300.0, frequencyOf(signal.pcm, 0.93, 1.05), 30.0)
    }

    @Test
    fun `black pixels transmit at the 1500Hz black frequency`() {
        val signal = SstvEncoder.encode(solidImage(BLACK), mode)
        assertFrequency(1500.0, frequencyOf(signal.pcm, 0.93, 1.05), 30.0)
    }

    @Test
    fun `mid grey lands halfway up the luminance sub-carrier`() {
        val grey = 0xFF808080.toInt()
        val signal = SstvEncoder.encode(solidImage(grey), mode)
        // 1500 + 800 * (128 / 255)
        assertFrequency(1901.6, frequencyOf(signal.pcm, 0.93, 1.05), 30.0)
    }

    @Test
    fun `colour channels are sent in green blue red order`() {
        // Pure green: the green sweep should read as white, the blue and red sweeps as black.
        val signal = SstvEncoder.encode(solidImage(0xFF00FF00.toInt()), mode)

        val scanStart = headerSeconds + mode.syncPulseSeconds + mode.syncPorchSeconds
        val step = mode.scanSeconds + mode.separatorSeconds

        // Sample the middle 60% of each sweep.
        fun sweepFrequency(index: Int): Double {
            val start = scanStart + step * index
            return frequencyOf(
                signal.pcm,
                start + mode.scanSeconds * 0.2,
                start + mode.scanSeconds * 0.8
            )
        }

        assertFrequency(2300.0, sweepFrequency(0), 40.0) // green
        assertFrequency(1500.0, sweepFrequency(1), 40.0) // blue
        assertFrequency(1500.0, sweepFrequency(2), 40.0) // red
    }

    @Test
    fun `scottie puts the sync pulse mid line, before the red sweep`() {
        val scottie = SstvMode.SCOTTIE_S1
        val signal = SstvEncoder.encode(solidImage(WHITE, scottie), scottie)

        // separator + green scan + separator + blue scan, then the 9 ms sync pulse.
        val syncStart = headerSeconds +
            2 * (scottie.separatorSeconds + scottie.scanSeconds)
        assertFrequency(
            1200.0,
            frequencyOf(signal.pcm, syncStart + 0.001, syncStart + scottie.syncPulseSeconds - 0.001),
            60.0
        )
    }

    @Test
    fun `the alpha byte is ignored`() {
        val opaque = SstvEncoder.encode(solidImage(0xFF7F3311.toInt()), mode)
        val transparent = SstvEncoder.encode(solidImage(0x007F3311), mode)
        assertTrue("Alpha must not change the waveform", opaque.pcm.contentEquals(transparent.pcm))
    }

    @Test
    fun `trailing silence closes the frame`() {
        val signal = SstvEncoder.encode(solidImage(WHITE), mode)
        assertEquals(0, signal.pcm.last().toInt())
    }

    @Test
    fun `a custom sample rate produces a signal of the same length in seconds`() {
        val signal = SstvEncoder(sampleRate = 44100).encode(solidImage(BLACK), mode)
        val expectedSeconds =
            headerSeconds + mode.pictureSeconds + signal.trailingSilenceSeconds

        assertEquals(44100, signal.sampleRate)
        assertEquals(expectedSeconds, signal.totalSeconds, 0.001)
    }

    @Test
    fun `amplitude scales the peak sample level`() {
        val quiet = SstvEncoder(amplitude = 0.25).encode(solidImage(WHITE), mode)
        val peak = quiet.pcm.maxOf { abs(it.toInt()) }
        assertEquals(0.25 * Short.MAX_VALUE, peak.toDouble(), 200.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects an image of the wrong size`() {
        SstvEncoder.encode(IntArray(10), mode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a sample rate that would alias the tones`() {
        SstvEncoder(sampleRate = 4000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects an out of range amplitude`() {
        SstvEncoder(amplitude = 1.5)
    }

    private companion object {
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}