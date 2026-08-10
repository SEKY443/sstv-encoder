package io.github.seky443.sstv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Exercises the per-family line layouts through the encoder.
 *
 * [SstvModeTimingTest] checks the arithmetic; this checks that the waveform those timings describe
 * is actually the one that comes out, by measuring frequencies inside specific windows of the
 * signal. Between them, a mode is wrong only if both the spec transcription and the layout are.
 */
class LineFormatTest {

    /** 8 kHz keeps the long modes cheap; the highest tone on air is 2300 Hz, so nothing aliases. */
    private val encoder = SstvEncoder(sampleRate = 8000, trailingSilenceSeconds = 0.0)

    private fun solid(mode: SstvMode, color: Int) = IntArray(mode.pixelCount) { color }

    private fun frequencyOf(
        signal: SstvSignal,
        fromSeconds: Double,
        toSeconds: Double
    ): Double {
        val rate = signal.sampleRate
        val from = (fromSeconds * rate).toInt()
        val to = (toSeconds * rate).toInt().coerceAtMost(signal.pcm.size - 1)
        require(to > from) { "Empty window" }

        var crossings = 0
        for (i in (from + 1)..to) {
            if ((signal.pcm[i - 1] < 0) != (signal.pcm[i] < 0)) crossings++
        }
        return crossings / 2.0 / (toSeconds - fromSeconds)
    }

    private fun assertFrequency(message: String, expected: Double, actual: Double, tolerance: Double) {
        assertTrue(
            "$message: expected ~${expected}Hz but measured ${"%.1f".format(actual)}Hz",
            abs(actual - expected) <= tolerance
        )
    }

    @Test
    fun `every mode encodes to a signal of the length its timings predict`() {
        for (mode in SstvMode.entries) {
            val signal = encoder.encode(solid(mode, BLACK), mode)
            val expected = SstvEncoder.HEADER_SECONDS + mode.pictureSeconds
            assertEquals(
                "${mode.displayName} signal length",
                expected,
                signal.totalSeconds,
                0.002
            )
            assertEquals("${mode.displayName} line count", mode.lineCount, signal.lineCount)
        }
    }

    @Test
    fun `robot 36 alternates the chrominance channel between lines`() {
        val mode = SstvMode.ROBOT_36
        val signal = encoder.encode(solid(mode, BLACK), mode)

        // sync 9ms + porch 3ms + luminance 88ms puts the separator at 100ms into each line.
        val separatorOffset = 0.009 + 0.003 + 0.088
        fun separatorFrequency(line: Int): Double {
            val start = SstvEncoder.HEADER_SECONDS + line * mode.lineSeconds + separatorOffset
            return frequencyOf(signal, start + 0.0005, start + 0.004)
        }

        // 1500 Hz announces R-Y on even lines, 2300 Hz announces B-Y on odd ones.
        assertFrequency("line 0 separator", 1500.0, separatorFrequency(0), 200.0)
        assertFrequency("line 1 separator", 2300.0, separatorFrequency(1), 200.0)
        assertFrequency("line 2 separator", 1500.0, separatorFrequency(2), 200.0)
        assertFrequency("line 3 separator", 2300.0, separatorFrequency(3), 200.0)
    }

    @Test
    fun `pd packs two picture rows into one line`() {
        val mode = SstvMode.PD_50
        // Row 0 white, row 1 black. Both rows are grey-scale, so their chroma is identical and the
        // averaged R-Y and B-Y sweeps must land mid-scale.
        val pixels = IntArray(mode.pixelCount) { if (it < mode.width) WHITE else BLACK }
        val signal = encoder.encode(pixels, mode)

        val sweepSeconds = mode.width * 0.000286
        val firstSweep = SstvEncoder.HEADER_SECONDS + 0.020 + 0.00208
        fun sweepFrequency(index: Int): Double {
            val start = firstSweep + index * sweepSeconds
            return frequencyOf(signal, start + sweepSeconds * 0.2, start + sweepSeconds * 0.8)
        }

        assertFrequency("row 0 luminance", 2300.0, sweepFrequency(0), 40.0)
        // Neutral chroma is 128, which sits at 1500 + 800 * 128/255 = 1901.6 Hz.
        assertFrequency("averaged R-Y", 1901.6, sweepFrequency(1), 40.0)
        assertFrequency("averaged B-Y", 1901.6, sweepFrequency(2), 40.0)
        assertFrequency("row 1 luminance", 1500.0, sweepFrequency(3), 40.0)
    }

    @Test
    fun `monochrome modes send one luminance sweep behind the sync pulse`() {
        val mode = SstvMode.ROBOT_24_BW
        val signal = encoder.encode(solid(mode, WHITE), mode)

        val scanStart = SstvEncoder.HEADER_SECONDS + 0.007
        assertFrequency(
            "luminance sweep",
            2300.0,
            frequencyOf(signal, scanStart + 0.01, scanStart + 0.08),
            40.0
        )
    }

    @Test
    fun `pasokon sends red green blue in that order`() {
        val mode = SstvMode.PASOKON_P3
        val signal = encoder.encode(solid(mode, RED), mode)

        val unit = 1.0 / 4800
        val scan = mode.width * unit
        // 25 units of sync, then a 5 unit porch, then each sweep closed by a 5 unit gap.
        val firstSweep = SstvEncoder.HEADER_SECONDS + 30 * unit
        fun sweepFrequency(index: Int): Double {
            val start = firstSweep + index * (scan + 5 * unit)
            return frequencyOf(signal, start + scan * 0.2, start + scan * 0.8)
        }

        assertFrequency("red sweep", 2300.0, sweepFrequency(0), 40.0)
        assertFrequency("green sweep", 1500.0, sweepFrequency(1), 40.0)
        assertFrequency("blue sweep", 1500.0, sweepFrequency(2), 40.0)
    }

    @Test
    fun `wraase sc-2 sends red green blue with no separators`() {
        val mode = SstvMode.WRAASE_SC2_180
        val signal = encoder.encode(solid(mode, GREEN), mode)

        val scan = 0.235
        val firstSweep = SstvEncoder.HEADER_SECONDS + 0.0055225 + 0.0005
        fun sweepFrequency(index: Int): Double {
            val start = firstSweep + index * scan
            return frequencyOf(signal, start + scan * 0.2, start + scan * 0.8)
        }

        assertFrequency("red sweep", 1500.0, sweepFrequency(0), 40.0)
        assertFrequency("green sweep", 2300.0, sweepFrequency(1), 40.0)
        assertFrequency("blue sweep", 1500.0, sweepFrequency(2), 40.0)
    }

    @Test
    fun `avt sends no per-line sync pulse`() {
        val mode = SstvMode.AVT_90
        val signal = encoder.encode(solid(mode, WHITE), mode)

        // A sync-per-line mode would put 1200 Hz at the head of the second line. AVT must still be
        // sweeping picture, which for a white frame means 2300 Hz.
        val lineStart = SstvEncoder.HEADER_SECONDS + mode.lineSeconds
        assertFrequency(
            "start of the second AVT line",
            2300.0,
            frequencyOf(signal, lineStart + 0.001, lineStart + 0.02),
            40.0
        )
    }

    @Test
    fun `martin sends green blue red and scottie sends the sync mid line`() {
        // Guards the two families against each other, since they share a GBR channel order and
        // differ only in where the sync pulse sits.
        val martin = SstvMode.MARTIN_M2
        val martinSignal = encoder.encode(solid(martin, BLACK), martin)
        val martinSyncStart = SstvEncoder.HEADER_SECONDS
        assertFrequency(
            "martin opens on sync",
            1200.0,
            frequencyOf(martinSignal, martinSyncStart + 0.0005, martinSyncStart + 0.0044),
            250.0
        )

        val scottie = SstvMode.SCOTTIE_S2
        val scottieSignal = encoder.encode(solid(scottie, BLACK), scottie)
        // Scottie opens on a separator and two sweeps before its sync pulse.
        val scottieSyncStart = SstvEncoder.HEADER_SECONDS + 2 * (0.0015 + 0.088064)
        assertFrequency(
            "scottie syncs mid line",
            1200.0,
            frequencyOf(scottieSignal, scottieSyncStart + 0.001, scottieSyncStart + 0.008),
            150.0
        )
    }

    @Test
    fun `scottie sweeps occupy the full published scan time`() {
        // Scottie's channel boundaries, as the Robot36 decoder computes them from the sync pulse:
        // green 1.5-139.74 ms, blue 141.24-279.48 ms, red 289.98-428.22 ms. pySSTV gets this wrong
        // — it shortens each sweep to 136.74 ms to make room for a trailing gap it inherits from
        // Martin — so the sweep has to be measured at its far edge, not just in the middle, to
        // catch the difference.
        val mode = SstvMode.SCOTTIE_S1
        // The window in dispute is only 1.5 ms wide, and a zero-crossing count resolves no finer
        // than 1/(2T) — about 400 Hz here — so this asserts which side of the 1500/2300 midpoint
        // the tone falls on rather than its exact value.
        val signal = SstvEncoder(sampleRate = 44100, trailingSilenceSeconds = 0.0)
            .encode(solid(mode, GREEN), mode)
        val line = SstvEncoder.HEADER_SECONDS
        val midpoint = 1900.0

        // 138.24-139.74 ms is green picture in the specified layout and separator in pySSTV's.
        val greenEnd = frequencyOf(signal, line + 0.13840, line + 0.13965)
        assertTrue(
            "green sweep should still be running at 139.7 ms but measured ${"%.0f".format(greenEnd)}Hz",
            greenEnd > midpoint
        )

        // Blue starts at 141.24 ms in both layouts, and reads black for a green frame.
        val blueStart = frequencyOf(signal, line + 0.14180, line + 0.14330)
        assertTrue(
            "blue sweep should have started by 141.8 ms but measured ${"%.0f".format(blueStart)}Hz",
            blueStart < midpoint
        )
    }

    private companion object {
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val RED = 0xFFFF0000.toInt()
        const val GREEN = 0xFF00FF00.toInt()
    }
}
