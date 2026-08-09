package io.github.seky443.sstv

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Encodes an image into an analogue SSTV audio waveform.
 *
 * There is no maintained SSTV *encoder* library on Maven Central (the well known projects are
 * decoders, and the C++ ones are GPL), so this is a direct implementation of the mode timings in
 * [SstvMode]. It is deliberately dependency free: the whole encoder is a frequency generator
 * driven by the per-mode line structure, and it touches nothing but the Kotlin standard library,
 * which is what lets the same code run on the JVM and on Android.
 *
 * Output is 16-bit signed mono PCM, ready to hand to `javax.sound`, an Android `AudioTrack`, or a
 * WAV file via [writeWav].
 *
 * An instance is immutable and safe to share between threads.
 *
 * ```
 * val signal = SstvEncoder.encode(pixels, SstvMode.MARTIN_M1)
 * signal.writeWav(File("transmission.wav"))
 * ```
 *
 * @param sampleRate output sample rate in Hz. 22050 is plenty: the highest tone on air is 2300 Hz.
 * @param amplitude peak level as a fraction of full scale. The default leaves headroom so a
 *   speaker does not clip the tones.
 * @param trailingSilenceSeconds silence appended after the picture so a receiver sees a clean gap
 *   before a looped transmission repeats.
 */
public class SstvEncoder @JvmOverloads constructor(
    public val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    public val amplitude: Double = DEFAULT_AMPLITUDE,
    public val trailingSilenceSeconds: Double = DEFAULT_TRAILING_SILENCE_SECONDS
) {
    init {
        require(sampleRate >= MIN_SAMPLE_RATE) {
            "Sample rate must be at least $MIN_SAMPLE_RATE Hz to carry a ${SstvMode.FREQ_WHITE} Hz tone"
        }
        require(amplitude > 0.0 && amplitude <= 1.0) { "Amplitude must be in (0, 1]" }
        require(trailingSilenceSeconds >= 0.0) { "Trailing silence cannot be negative" }
    }

    /**
     * Encodes raw pixels in row-major order, each packed as `0xAARRGGBB` (the alpha byte is
     * ignored). The array must hold exactly [SstvMode.pixelCount] entries for [mode].
     *
     * @throws IllegalArgumentException if [pixels] is not the right size for [mode].
     */
    @JvmOverloads
    public fun encode(pixels: IntArray, mode: SstvMode = SstvMode.MARTIN_M1): SstvSignal {
        require(pixels.size == mode.pixelCount) {
            "Expected ${mode.pixelCount} pixels (${mode.width}x${mode.height}) for " +
                "${mode.displayName}, got ${pixels.size}"
        }

        val totalSeconds = HEADER_SECONDS + mode.pictureSeconds + trailingSilenceSeconds
        // One extra sample of slack absorbs rounding on the final segment.
        val writer = ToneWriter(sampleRate, amplitude, (totalSeconds * sampleRate).roundToInt() + 1)

        writeHeader(writer, mode.visCode)

        for (y in 0 until mode.height) {
            when (mode) {
                SstvMode.MARTIN_M1 -> writeMartinLine(writer, mode, pixels, y)
                SstvMode.SCOTTIE_S1 -> writeScottieLine(writer, mode, pixels, y)
            }
        }

        writer.silence(trailingSilenceSeconds)

        return SstvSignal(
            pcm = writer.finish(),
            sampleRate = sampleRate,
            mode = mode,
            headerSeconds = HEADER_SECONDS,
            trailingSilenceSeconds = trailingSilenceSeconds
        )
    }

    /**
     * Leader tone, break, leader tone, then the VIS code as a start bit, 7 data bits (LSB first),
     * an even parity bit and a stop bit. This is what tells the receiving decoder which mode and
     * therefore which line timings to expect.
     */
    private fun writeHeader(writer: ToneWriter, visCode: Int) {
        writer.tone(SstvMode.FREQ_LEADER, LEADER_SECONDS)
        writer.tone(SstvMode.FREQ_SYNC, BREAK_SECONDS)
        writer.tone(SstvMode.FREQ_LEADER, LEADER_SECONDS)

        writer.tone(SstvMode.FREQ_SYNC, VIS_BIT_SECONDS) // start bit

        var oneBits = 0
        for (bit in 0 until 7) {
            val isOne = (visCode shr bit) and 1 == 1
            if (isOne) oneBits++
            writer.tone(
                if (isOne) SstvMode.FREQ_VIS_ONE else SstvMode.FREQ_VIS_ZERO,
                VIS_BIT_SECONDS
            )
        }
        // Even parity: the parity bit makes the number of 1 bits even.
        writer.tone(
            if (oneBits % 2 == 1) SstvMode.FREQ_VIS_ONE else SstvMode.FREQ_VIS_ZERO,
            VIS_BIT_SECONDS
        )

        writer.tone(SstvMode.FREQ_SYNC, VIS_BIT_SECONDS) // stop bit
    }

    /** Martin M1 line: sync, porch, then green, blue and red sweeps each followed by a separator. */
    private fun writeMartinLine(writer: ToneWriter, mode: SstvMode, pixels: IntArray, y: Int) {
        writer.tone(SstvMode.FREQ_SYNC, mode.syncPulseSeconds)
        writer.tone(SstvMode.FREQ_BLACK, mode.syncPorchSeconds)

        writeScan(writer, mode, pixels, y, Channel.GREEN)
        writer.tone(SstvMode.FREQ_BLACK, mode.separatorSeconds)

        writeScan(writer, mode, pixels, y, Channel.BLUE)
        writer.tone(SstvMode.FREQ_BLACK, mode.separatorSeconds)

        writeScan(writer, mode, pixels, y, Channel.RED)
        writer.tone(SstvMode.FREQ_BLACK, mode.separatorSeconds)
    }

    /**
     * Scottie S1 line: separator, green, separator, blue, then the sync pulse and porch before the
     * red sweep. The sync sitting mid-line rather than at the start is what distinguishes Scottie
     * from Martin.
     */
    private fun writeScottieLine(writer: ToneWriter, mode: SstvMode, pixels: IntArray, y: Int) {
        writer.tone(SstvMode.FREQ_BLACK, mode.separatorSeconds)
        writeScan(writer, mode, pixels, y, Channel.GREEN)

        writer.tone(SstvMode.FREQ_BLACK, mode.separatorSeconds)
        writeScan(writer, mode, pixels, y, Channel.BLUE)

        writer.tone(SstvMode.FREQ_SYNC, mode.syncPulseSeconds)
        writer.tone(SstvMode.FREQ_BLACK, mode.syncPorchSeconds)
        writeScan(writer, mode, pixels, y, Channel.RED)
    }

    private enum class Channel { RED, GREEN, BLUE }

    /** Sweeps one colour channel of row [y] across the full image width. */
    private fun writeScan(
        writer: ToneWriter,
        mode: SstvMode,
        pixels: IntArray,
        y: Int,
        channel: Channel
    ) {
        val rowOffset = y * mode.width
        for (x in 0 until mode.width) {
            val pixel = pixels[rowOffset + x]
            val value = when (channel) {
                Channel.RED -> (pixel shr 16) and 0xFF
                Channel.GREEN -> (pixel shr 8) and 0xFF
                Channel.BLUE -> pixel and 0xFF
            }
            writer.tone(frequencyFor(value), mode.pixelSeconds)
        }
    }

    /**
     * Phase-continuous tone generator.
     *
     * Sample counts are derived from cumulative elapsed time rather than per-segment rounding: a
     * Martin 1 frame contains over 245,000 segments, so rounding each one independently would drift
     * the line timing far enough to shear the received picture.
     */
    private class ToneWriter(
        private val sampleRate: Int,
        private val amplitude: Double,
        capacity: Int
    ) {
        private val buffer = ShortArray(capacity)
        private var written = 0
        private var elapsedSeconds = 0.0
        private var phase = 0.0

        fun tone(frequencyHz: Double, seconds: Double) {
            val count = advance(seconds)
            val phaseStep = 2.0 * PI * frequencyHz / sampleRate
            repeat(count) {
                buffer[written++] = (sin(phase) * amplitude * Short.MAX_VALUE).toInt().toShort()
                phase += phaseStep
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
        }

        fun silence(seconds: Double) {
            val count = advance(seconds)
            repeat(count) { buffer[written++] = 0 }
            phase = 0.0
        }

        /** Returns how many samples this segment gets, keeping the timeline drift-free. */
        private fun advance(seconds: Double): Int {
            elapsedSeconds += seconds
            val target = (elapsedSeconds * sampleRate).roundToInt().coerceAtMost(buffer.size)
            return (target - written).coerceAtLeast(0)
        }

        fun finish(): ShortArray = if (written == buffer.size) buffer else buffer.copyOf(written)
    }

    public companion object {
        /** Default output sample rate. The highest tone on air is 2300 Hz, so this is ample. */
        public const val DEFAULT_SAMPLE_RATE: Int = 22050

        /** 0.75 of full scale leaves headroom so a speaker does not clip the tones. */
        public const val DEFAULT_AMPLITUDE: Double = 0.75

        /** Default gap after the picture, so a looped transmission has a clean frame boundary. */
        public const val DEFAULT_TRAILING_SILENCE_SECONDS: Double = 0.75

        /** Length of the leader tone that opens the transmission, sent twice. */
        public const val LEADER_SECONDS: Double = 0.300

        /** Break between the two leader tones. */
        public const val BREAK_SECONDS: Double = 0.010

        /** Length of one VIS bit slot. */
        public const val VIS_BIT_SECONDS: Double = 0.030

        /** Total length of the leader plus VIS header that precedes the first scan line. */
        public const val HEADER_SECONDS: Double =
            LEADER_SECONDS + BREAK_SECONDS + LEADER_SECONDS + VIS_BIT_SECONDS * 10

        /** Below this the 2300 Hz white tone would alias. */
        private const val MIN_SAMPLE_RATE = 8000

        /**
         * Shared encoder using the default settings. Java callers should go through this rather
         * than the companion shorthand: `SstvEncoder.Default.encode(pixels, mode)`.
         */
        @JvmField
        public val Default: SstvEncoder = SstvEncoder()

        /**
         * Encodes with the default settings — the Kotlin shorthand for
         * `SstvEncoder.Default.encode(pixels, mode)`.
         */
        @JvmOverloads
        public fun encode(pixels: IntArray, mode: SstvMode = SstvMode.MARTIN_M1): SstvSignal =
            Default.encode(pixels, mode)

        /** Maps an 8-bit intensity onto the 1500 Hz (black) to 2300 Hz (white) sub-carrier. */
        @JvmStatic
        public fun frequencyFor(value: Int): Double =
            SstvMode.FREQ_BLACK +
                (SstvMode.FREQ_WHITE - SstvMode.FREQ_BLACK) * (value.coerceIn(0, 255) / 255.0)
    }
}