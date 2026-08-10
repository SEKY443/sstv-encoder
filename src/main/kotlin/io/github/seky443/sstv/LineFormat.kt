package io.github.seky443.sstv

import kotlin.math.roundToInt

/**
 * How one transmitted scan line of a given mode is laid out on air.
 *
 * Every SSTV family slices a line differently — Martin puts the sync pulse at the head, Scottie
 * buries it before the red sweep, the Robot colour modes send luminance plus one alternating
 * chrominance channel, PD packs two picture rows into a single line, and AVT drops the per-line sync
 * pulse entirely. Rather than branch on the mode everywhere, each [SstvMode] carries the format that
 * knows how to emit its own line, and [SstvEncoder] just drives it.
 *
 * Implementations are immutable value objects and are shared between modes where the layout is
 * identical and only the timings differ.
 */
internal sealed interface LineFormat {

    /**
     * Picture rows carried by one transmitted line. 1 for almost everything; the PD family
     * interleaves two rows per sync pulse.
     */
    val rowsPerLine: Int get() = 1

    /** Duration of one transmitted line, from one sync pulse to the next. */
    fun lineSeconds(mode: SstvMode): Double

    /** Emits one transmitted line, starting at picture row [row]. */
    fun writeLine(writer: ToneWriter, mode: SstvMode, pixels: IntArray, row: Int)
}

// --- Colour helpers ---------------------------------------------------------------------------

private fun red(pixel: Int): Int = (pixel shr 16) and 0xFF

private fun green(pixel: Int): Int = (pixel shr 8) and 0xFF

private fun blue(pixel: Int): Int = pixel and 0xFF

/**
 * Full-range (JPEG convention) Y'CbCr, which is what every reference SSTV implementation uses.
 * Studio-swing coefficients would wash out the received picture by about 7%.
 */
private fun luma(pixel: Int): Int =
    (0.299 * red(pixel) + 0.587 * green(pixel) + 0.114 * blue(pixel))
        .roundToInt().coerceIn(0, 255)

private fun chromaBlue(pixel: Int): Int =
    (128.0 - 0.168736 * red(pixel) - 0.331264 * green(pixel) + 0.5 * blue(pixel))
        .roundToInt().coerceIn(0, 255)

private fun chromaRed(pixel: Int): Int =
    (128.0 + 0.5 * red(pixel) - 0.418688 * green(pixel) - 0.081312 * blue(pixel))
        .roundToInt().coerceIn(0, 255)

/** Sweeps one channel of picture row [row] across the full width, one sample per pixel. */
private inline fun ToneWriter.sweep(
    mode: SstvMode,
    pixels: IntArray,
    row: Int,
    pixelSeconds: Double,
    channel: (Int) -> Int
) {
    val offset = row * mode.width
    for (x in 0 until mode.width) {
        level(channel(pixels[offset + x]), pixelSeconds)
    }
}

// --- RGB sequential families ------------------------------------------------------------------

/**
 * Martin: sync pulse, porch, then green, blue and red sweeps each closed by a separator.
 *
 * Timings from N7CXI (2000). M1 and M3 share a pixel time, as do M2 and M4; the pairs differ only
 * in how many lines they send.
 */
internal data class Martin(
    val pixelSeconds: Double,
    val syncSeconds: Double = 0.004862,
    val porchSeconds: Double = 0.000572,
    val separatorSeconds: Double = 0.000572
) : LineFormat {

    override fun lineSeconds(mode: SstvMode): Double =
        syncSeconds + porchSeconds + 3 * (mode.width * pixelSeconds + separatorSeconds)

    override fun writeLine(writer: ToneWriter, mode: SstvMode, pixels: IntArray, row: Int) {
        writer.tone(SstvMode.FREQ_SYNC, syncSeconds)
        writer.tone(SstvMode.FREQ_BLACK, porchSeconds)
        for (channel in listOf(::green, ::blue, ::red)) {
            writer.sweep(mode, pixels, row, pixelSeconds, channel)
            writer.tone(SstvMode.FREQ_BLACK, separatorSeconds)
        }
    }
}

/**
 * Scottie: separator, green, separator, blue, then the sync pulse and porch before the red sweep.
 * The sync sitting mid-line rather than at the head is what distinguishes Scottie from Martin.
 *
 * Timings from N7CXI (2000).
 */
internal data class Scottie(
    val pixelSeconds: Double,
    val syncSeconds: Double = 0.009,
    val porchSeconds: Double = 0.0015,
    val separatorSeconds: Double = 0.0015
) : LineFormat {

    override fun lineSeconds(mode: SstvMode): Double {
        val scan = mode.width * pixelSeconds
        return 2 * (separatorSeconds + scan) + syncSeconds + porchSeconds + scan
    }

    override fun writeLine(writer: ToneWriter, mode: SstvMode, pixels: IntArray, row: Int) {
        writer.tone(SstvMode.FREQ_BLACK, separatorSeconds)
        writer.sweep(mode, pixels, row, pixelSeconds, ::green)

        writer.tone(SstvMode.FREQ_BLACK, separatorSeconds)
        writer.sweep(mode, pixels, row, pixelSeconds, ::blue)

        writer.tone(SstvMode.FREQ_SYNC, syncSeconds)
        writer.tone(SstvMode.FREQ_BLACK, porchSeconds)
        writer.sweep(mode, pixels, row, pixelSeconds, ::red)
    }
}

/**
 * Pasokon TV: everything is a multiple of a single time unit derived from the mode's sample clock
 * (4800, 3200 and 2400 Hz for P3, P5 and P7). Sync is 25 units, each gap 5 units, each sweep one
 * unit per pixel — 1965 units per line in every Pasokon mode.
 *
 * Timings from N7CXI (2000).
 */
internal data class Pasokon(val timeUnitSeconds: Double) : LineFormat {

    override fun lineSeconds(mode: SstvMode): Double =
        (SYNC_UNITS + GAP_UNITS + 3 * (mode.width + GAP_UNITS)) * timeUnitSeconds

    override fun writeLine(writer: ToneWriter, mode: SstvMode, pixels: IntArray, row: Int) {
        writer.tone(SstvMode.FREQ_SYNC, SYNC_UNITS * timeUnitSeconds)
        writer.tone(SstvMode.FREQ_BLACK, GAP_UNITS * timeUnitSeconds)
        for (channel in listOf(::red, ::green, ::blue)) {
            writer.sweep(mode, pixels, row, timeUnitSeconds, channel)
            writer.tone(SstvMode.FREQ_BLACK, GAP_UNITS * timeUnitSeconds)
        }
    }

    private companion object {
        const val SYNC_UNITS = 25
        const val GAP_UNITS = 5
    }
}

/**
 * Wraase SC-2: sync, a short porch, then straight RGB with no separators between sweeps.
 *
 * SC-2 180 timings are from N7CXI (2000), SC-2 120 from KB4YZ (1999). SC-2 120 additionally needs a
 * porch at the head of *every* channel — without it QSSTV and slowrx lose sync and the picture
 * slants badly, a quirk pySSTV documents from experiment rather than from any published spec.
 */
internal data class WraaseSc2(
    val scanSeconds: Double,
    val porchBeforeEachChannel: Boolean = false,
    val syncSeconds: Double = 0.0055225,
    val porchSeconds: Double = 0.0005
) : LineFormat {

    override fun lineSeconds(mode: SstvMode): Double =
        syncSeconds + porchSeconds + 3 * scanSeconds +
            (if (porchBeforeEachChannel) 3 * porchSeconds else 0.0)

    override fun writeLine(writer: ToneWriter, mode: SstvMode, pixels: IntArray, row: Int) {
        val pixelSeconds = scanSeconds / mode.width
        writer.tone(SstvMode.FREQ_SYNC, syncSeconds)

        // Both variants porch before red; SC-2 120 porches before every channel, so red gets two.
        writer.tone(SstvMode.FREQ_BLACK, porchSeconds)
        if (porchBeforeEachChannel) writer.tone(SstvMode.FREQ_BLACK, porchSeconds)
        writer.sweep(mode, pixels, row, pixelSeconds, ::red)

        for (channel in listOf(::green, ::blue)) {
            if (porchBeforeEachChannel) writer.tone(SstvMode.FREQ_BLACK, porchSeconds)
            writer.sweep(mode, pixels, row, pixelSeconds, channel)
        }
    }
}

// --- Monochrome -------------------------------------------------------------------------------

/**
 * Monochrome: a sync pulse followed by a single luminance sweep, with no porch. Covers the Robot
 * black-and-white modes and the historic 8/16/32-second formats.
 */
internal data class Monochrome(
    val scanSeconds: Double,
    val syncSeconds: Double = 0.007
) : LineFormat {

    override fun lineSeconds(mode: SstvMode): Double = syncSeconds + scanSeconds

    override fun writeLine(writer: ToneWriter, mode: SstvMode, pixels: IntArray, row: Int) {
        writer.tone(SstvMode.FREQ_SYNC, syncSeconds)
        writer.sweep(mode, pixels, row, scanSeconds / mode.width, ::luma)
    }
}

// --- Robot colour (Y/C) -----------------------------------------------------------------------

/**
 * Robot 36: luminance every line, but only one chrominance channel per line — R-Y on even lines and
 * B-Y on odd ones, so full chroma costs two lines (4:2:0). The separator tone tells the decoder
 * which of the two it is getting: 1500 Hz ahead of R-Y, 2300 Hz ahead of B-Y.
 *
 * Timings from N7CXI (2000); confirmed against the Robot36 decoder, which reconstructs a 150 ms line
 * from exactly these components.
 */
internal data class RobotYc420(
    val luminanceSeconds: Double,
    val chrominanceSeconds: Double,
    val syncSeconds: Double = 0.009,
    val syncPorchSeconds: Double = 0.003,
    val separatorSeconds: Double = 0.0045,
    val porchSeconds: Double = 0.0015
) : LineFormat {

    override fun lineSeconds(mode: SstvMode): Double =
        syncSeconds + syncPorchSeconds + luminanceSeconds +
            separatorSeconds + porchSeconds + chrominanceSeconds

    override fun writeLine(writer: ToneWriter, mode: SstvMode, pixels: IntArray, row: Int) {
        writer.tone(SstvMode.FREQ_SYNC, syncSeconds)
        writer.tone(SstvMode.FREQ_BLACK, syncPorchSeconds)
        writer.sweep(mode, pixels, row, luminanceSeconds / mode.width, ::luma)

        val even = row % 2 == 0
        writer.tone(
            if (even) SstvMode.FREQ_BLACK else SstvMode.FREQ_WHITE,
            separatorSeconds
        )
        writer.tone(SstvMode.FREQ_LEADER, porchSeconds)
        writer.sweep(
            mode,
            pixels,
            row,
            chrominanceSeconds / mode.width,
            if (even) ::chromaRed else ::chromaBlue
        )
    }
}

/**
 * Robot 72: luminance plus both chrominance channels on every line, each at half the luminance
 * width (4:2:2). R-Y leads B-Y, each preceded by its own separator and porch.
 *
 * Timings from N7CXI (2000); these components sum to the published 300 ms line exactly.
 */
internal data class RobotYc422(
    val luminanceSeconds: Double,
    val chrominanceSeconds: Double,
    val syncSeconds: Double = 0.009,
    val syncPorchSeconds: Double = 0.003,
    val separatorSeconds: Double = 0.0045,
    val porchSeconds: Double = 0.0015
) : LineFormat {

    override fun lineSeconds(mode: SstvMode): Double =
        syncSeconds + syncPorchSeconds + luminanceSeconds +
            2 * (separatorSeconds + porchSeconds + chrominanceSeconds)

    override fun writeLine(writer: ToneWriter, mode: SstvMode, pixels: IntArray, row: Int) {
        writer.tone(SstvMode.FREQ_SYNC, syncSeconds)
        writer.tone(SstvMode.FREQ_BLACK, syncPorchSeconds)
        writer.sweep(mode, pixels, row, luminanceSeconds / mode.width, ::luma)

        val chromaPixelSeconds = chrominanceSeconds / mode.width
        writer.tone(SstvMode.FREQ_BLACK, separatorSeconds)
        writer.tone(SstvMode.FREQ_LEADER, porchSeconds)
        writer.sweep(mode, pixels, row, chromaPixelSeconds, ::chromaRed)

        writer.tone(SstvMode.FREQ_WHITE, separatorSeconds)
        writer.tone(SstvMode.FREQ_LEADER, porchSeconds)
        writer.sweep(mode, pixels, row, chromaPixelSeconds, ::chromaBlue)
    }
}

/**
 * PD (Paul Turner): one sync pulse covers two picture rows. The line carries the first row's
 * luminance, then R-Y and B-Y averaged across both rows, then the second row's luminance — so
 * chroma is shared vertically while luminance keeps full resolution.
 *
 * Timings from N7CXI (2000). PD-120 is the mode the ISS transmits on.
 */
internal data class Pd(val pixelSeconds: Double) : LineFormat {

    override val rowsPerLine: Int get() = 2

    override fun lineSeconds(mode: SstvMode): Double =
        SYNC_SECONDS + PORCH_SECONDS + 4 * mode.width * pixelSeconds

    override fun writeLine(writer: ToneWriter, mode: SstvMode, pixels: IntArray, row: Int) {
        writer.tone(SstvMode.FREQ_SYNC, SYNC_SECONDS)
        writer.tone(SstvMode.FREQ_BLACK, PORCH_SECONDS)

        writer.sweep(mode, pixels, row, pixelSeconds, ::luma)

        val top = row * mode.width
        val bottom = (row + 1) * mode.width
        for (channel in listOf(::chromaRed, ::chromaBlue)) {
            for (x in 0 until mode.width) {
                val average = (channel(pixels[top + x]) + channel(pixels[bottom + x])) / 2
                writer.level(average, pixelSeconds)
            }
        }

        writer.sweep(mode, pixels, row + 1, pixelSeconds, ::luma)
    }

    private companion object {
        const val SYNC_SECONDS = 0.020
        const val PORCH_SECONDS = 0.00208
    }
}

// --- Experimental ------------------------------------------------------------------------------

/**
 * AVT (Amiga Video Transceiver): RGB sequential with **no per-line sync pulse** — synchronisation is
 * meant to come from a digital header sent once at the start, which is what makes AVT resilient to
 * noise bursts that would tear a sync-per-line mode.
 *
 * **Experimental.** No public timing specification for AVT could be found; the pixel times here are
 * derived from each mode's nominal on-air duration and frame size, and the digital sync header is
 * *not* implemented. Expect these to be rejected by stock decoders. See the README.
 */
internal data class Avt(val pixelSeconds: Double) : LineFormat {

    override fun lineSeconds(mode: SstvMode): Double = 3 * mode.width * pixelSeconds

    override fun writeLine(writer: ToneWriter, mode: SstvMode, pixels: IntArray, row: Int) {
        for (channel in listOf(::red, ::green, ::blue)) {
            writer.sweep(mode, pixels, row, pixelSeconds, channel)
        }
    }
}

/**
 * Wraase SC-1: the earlier, pre-SC-2 Wraase colour format, modelled here on the SC-2 line structure
 * — sync, porch, then RGB with no separators.
 *
 * **Experimental.** No public timing specification for SC-1 could be found; the sweep lengths are
 * derived from each mode's nominal on-air duration, and the VIS codes are picked from unassigned
 * slots in the KB4YZ table. See the README.
 */
internal data class WraaseSc1(
    val scanSeconds: Double,
    val syncSeconds: Double = 0.0055225,
    val porchSeconds: Double = 0.0005
) : LineFormat {

    override fun lineSeconds(mode: SstvMode): Double =
        syncSeconds + porchSeconds + 3 * scanSeconds

    override fun writeLine(writer: ToneWriter, mode: SstvMode, pixels: IntArray, row: Int) {
        val pixelSeconds = scanSeconds / mode.width
        writer.tone(SstvMode.FREQ_SYNC, syncSeconds)
        writer.tone(SstvMode.FREQ_BLACK, porchSeconds)
        for (channel in listOf(::red, ::green, ::blue)) {
            writer.sweep(mode, pixels, row, pixelSeconds, channel)
        }
    }
}
