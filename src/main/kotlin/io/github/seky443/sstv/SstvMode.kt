package io.github.seky443.sstv

/**
 * Timing specification for the supported SSTV transmission modes.
 *
 * All durations are in seconds. The values are the published specs for each mode; a receiving
 * decoder (Robot36, MMSSTV, QSSTV, an FT-8xx radio, ...) locks onto the VIS header and then uses
 * these timings to slice the audio back into scan lines.
 *
 * Both supported modes send colour components in green-blue-red order, but they differ in how the
 * sync pulse and separators are laid out within a line, so the encoder handles them separately.
 */
public enum class SstvMode(
    /** Human readable name, as a receiving operator would know the mode. */
    public val displayName: String,
    /** VIS identification code broadcast in the header. */
    public val visCode: Int,
    /** Picture width in pixels. */
    public val width: Int,
    /** Picture height in scan lines. */
    public val height: Int,
    /** Time on air for a single pixel within one colour sweep. */
    public val pixelSeconds: Double,
    /** Length of the 1200 Hz horizontal sync pulse. */
    public val syncPulseSeconds: Double,
    /** Black porch that follows the sync pulse. */
    public val syncPorchSeconds: Double,
    /** Black separator between colour sweeps. */
    public val separatorSeconds: Double
) {
    MARTIN_M1(
        displayName = "Martin 1",
        visCode = 44,
        width = 320,
        height = 256,
        pixelSeconds = 0.0004576,
        syncPulseSeconds = 0.004862,
        syncPorchSeconds = 0.000572,
        separatorSeconds = 0.000572
    ),

    SCOTTIE_S1(
        displayName = "Scottie 1",
        visCode = 60,
        width = 320,
        height = 256,
        pixelSeconds = 0.0004320,
        syncPulseSeconds = 0.009,
        syncPorchSeconds = 0.0015,
        separatorSeconds = 0.0015
    );

    /** Duration of one colour sweep across the full image width. */
    public val scanSeconds: Double get() = pixelSeconds * width

    /** Total duration of a single scan line, including sync and separator overhead. */
    public val lineSeconds: Double
        get() = when (this) {
            // sync + porch + 3 x (scan + separator)
            MARTIN_M1 -> syncPulseSeconds + syncPorchSeconds + 3 * (scanSeconds + separatorSeconds)
            // 2 x (separator + scan) + sync + porch + scan
            SCOTTIE_S1 -> 2 * (separatorSeconds + scanSeconds) +
                syncPulseSeconds + syncPorchSeconds + scanSeconds
        }

    /** Time on air for the picture alone, excluding the header and any trailing silence. */
    public val pictureSeconds: Double get() = lineSeconds * height

    /** Number of pixels one frame of this mode holds. */
    public val pixelCount: Int get() = width * height

    public companion object {
        /** Frequency representing black in the luminance sub-carrier. */
        public const val FREQ_BLACK: Double = 1500.0

        /** Frequency representing white in the luminance sub-carrier. */
        public const val FREQ_WHITE: Double = 2300.0

        /** Sync / VIS start-stop tone. */
        public const val FREQ_SYNC: Double = 1200.0

        /** Leader tone that opens a transmission. */
        public const val FREQ_LEADER: Double = 1900.0

        /** VIS bit value for a one. */
        public const val FREQ_VIS_ONE: Double = 1100.0

        /** VIS bit value for a zero. */
        public const val FREQ_VIS_ZERO: Double = 1300.0

        /**
         * Looks a mode up by [SstvMode.name], falling back to [MARTIN_M1] for null or unknown
         * input. Useful when the mode arrives from persisted settings or a command line.
         */
        @JvmStatic
        public fun fromName(name: String?): SstvMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MARTIN_M1
    }
}