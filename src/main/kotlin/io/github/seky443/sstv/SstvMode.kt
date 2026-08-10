package io.github.seky443.sstv

/** The lineage a mode belongs to. Modes within a family share a line layout and differ in timing. */
public enum class SstvFamily {
    MARTIN,
    SCOTTIE,
    ROBOT,
    PD,
    PASOKON,
    WRAASE,
    AVT,
    LEGACY
}

/**
 * The SSTV transmission modes this encoder can send.
 *
 * Every mode carries the VIS code a receiving decoder (Robot36, MMSSTV, QSSTV, an FT-8xx radio, ...)
 * locks onto, its frame size, and the [LineFormat] that knows how to lay out one scan line.
 *
 * Timings are taken from JL Barber N7CXI, *Proposal for SSTV Mode Specifications* (Dayton, 2000),
 * supplemented by Dave Jones KB4YZ, *SSTV modes - line timing* (1999) where N7CXI is silent. Each
 * one reconstructs the published line time exactly, which is what [SstvModeTimingTest] guards.
 *
 * A handful of modes are marked [isExperimental]: no public timing specification for them could be
 * found, so their timings are derived from the nominal on-air duration and their VIS codes taken
 * from unassigned slots. They will very likely not decode in stock software — see the README.
 */
public enum class SstvMode(
    /** Human readable name, as a receiving operator would know the mode. */
    public val displayName: String,
    /** VIS identification code broadcast in the header. */
    public val visCode: Int,
    /** Picture width in pixels. */
    public val width: Int,
    /** Picture height in pixels. */
    public val height: Int,
    /** The lineage this mode belongs to. */
    public val family: SstvFamily,
    /** How one transmitted scan line is laid out on air. */
    internal val format: LineFormat,
    /** True when the timings are reconstructed rather than taken from a published specification. */
    public val isExperimental: Boolean = false
) {
    // --- Martin (Martin Emmerson G3OQD) — GBR sequential, sync at the head of the line ---------

    MARTIN_M1("Martin 1", 44, 320, 256, SstvFamily.MARTIN, Martin(pixelSeconds = 0.0004576)),
    MARTIN_M2("Martin 2", 40, 320, 256, SstvFamily.MARTIN, Martin(pixelSeconds = 0.0002288)),
    MARTIN_M3("Martin 3", 36, 320, 128, SstvFamily.MARTIN, Martin(pixelSeconds = 0.0004576)),
    MARTIN_M4("Martin 4", 32, 320, 128, SstvFamily.MARTIN, Martin(pixelSeconds = 0.0002288)),

    // --- Scottie (Eddie Murphy GM3SBC) — GBR sequential, sync buried before the red sweep ------

    SCOTTIE_S1("Scottie 1", 60, 320, 256, SstvFamily.SCOTTIE, Scottie(pixelSeconds = 0.000432)),
    SCOTTIE_S2("Scottie 2", 56, 320, 256, SstvFamily.SCOTTIE, Scottie(pixelSeconds = 0.0002752)),
    SCOTTIE_DX("Scottie DX", 76, 320, 256, SstvFamily.SCOTTIE, Scottie(pixelSeconds = 0.00108)),

    // --- Robot Research — monochrome and Y/C colour -------------------------------------------

    ROBOT_8_BW("Robot 8 B/W", 2, 320, 120, SstvFamily.ROBOT, Monochrome(scanSeconds = 0.0599)),
    ROBOT_12_BW("Robot 12 B/W", 6, 320, 120, SstvFamily.ROBOT, Monochrome(scanSeconds = 0.093)),
    ROBOT_24_BW("Robot 24 B/W", 10, 320, 240, SstvFamily.ROBOT, Monochrome(scanSeconds = 0.093)),

    ROBOT_36(
        "Robot 36", 8, 320, 240, SstvFamily.ROBOT,
        RobotYc420(luminanceSeconds = 0.088, chrominanceSeconds = 0.044)
    ),
    ROBOT_72(
        "Robot 72", 12, 320, 240, SstvFamily.ROBOT,
        RobotYc422(luminanceSeconds = 0.138, chrominanceSeconds = 0.069)
    ),

    // --- PD (Paul Turner G4IJE) — Y/C, two picture rows per transmitted line -------------------

    PD_50("PD 50", 93, 320, 256, SstvFamily.PD, Pd(pixelSeconds = 0.000286)),
    PD_90("PD 90", 99, 320, 256, SstvFamily.PD, Pd(pixelSeconds = 0.000532)),
    PD_120("PD 120", 95, 640, 496, SstvFamily.PD, Pd(pixelSeconds = 0.00019)),
    PD_160("PD 160", 98, 512, 400, SstvFamily.PD, Pd(pixelSeconds = 0.000382)),
    PD_180("PD 180", 96, 640, 496, SstvFamily.PD, Pd(pixelSeconds = 0.000286)),
    PD_240("PD 240", 97, 640, 496, SstvFamily.PD, Pd(pixelSeconds = 0.000382)),
    PD_290("PD 290", 94, 800, 616, SstvFamily.PD, Pd(pixelSeconds = 0.000286)),

    // --- Pasokon TV (John Langner WB2OSZ) — RGB on a 1965-unit line ----------------------------

    PASOKON_P3("Pasokon P3", 113, 640, 496, SstvFamily.PASOKON, Pasokon(timeUnitSeconds = 1.0 / 4800)),
    PASOKON_P5("Pasokon P5", 114, 640, 496, SstvFamily.PASOKON, Pasokon(timeUnitSeconds = 1.0 / 3200)),
    PASOKON_P7("Pasokon P7", 115, 640, 496, SstvFamily.PASOKON, Pasokon(timeUnitSeconds = 1.0 / 2400)),

    // --- Wraase (Volker Wraase DL2RZ) — RGB, no separators between sweeps ----------------------

    WRAASE_SC2_120(
        "Wraase SC-2 120", 63, 320, 256, SstvFamily.WRAASE,
        WraaseSc2(scanSeconds = 0.156, porchBeforeEachChannel = true)
    ),
    WRAASE_SC2_180(
        "Wraase SC-2 180", 55, 320, 256, SstvFamily.WRAASE,
        WraaseSc2(scanSeconds = 0.235)
    ),

    // --- Experimental: reconstructed timings, unassigned VIS codes -----------------------------

    /** Experimental — see [isExperimental]. Sweep derived to land the nominal 60 s frame. */
    WRAASE_SC2_60(
        "Wraase SC-2 60", 59, 320, 256, SstvFamily.WRAASE,
        WraaseSc2(
            scanSeconds = (60.0 / 256 - 0.0055225 - 4 * 0.0005) / 3,
            porchBeforeEachChannel = true
        ),
        isExperimental = true
    ),

    /** Experimental — see [isExperimental]. */
    WRAASE_SC1_24(
        "Wraase SC-1 24", 33, 256, 240, SstvFamily.WRAASE,
        WraaseSc1(scanSeconds = (24.0 / 240 - 0.0055225 - 0.0005) / 3), isExperimental = true
    ),

    /** Experimental — see [isExperimental]. */
    WRAASE_SC1_48(
        "Wraase SC-1 48", 37, 256, 240, SstvFamily.WRAASE,
        WraaseSc1(scanSeconds = (48.0 / 240 - 0.0055225 - 0.0005) / 3), isExperimental = true
    ),

    /** Experimental — see [isExperimental]. */
    WRAASE_SC1_96(
        "Wraase SC-1 96", 41, 256, 240, SstvFamily.WRAASE,
        WraaseSc1(scanSeconds = (96.0 / 240 - 0.0055225 - 0.0005) / 3), isExperimental = true
    ),

    /** Experimental — see [isExperimental]. The digital sync header is not implemented. */
    AVT_24(
        "AVT 24", 64, 320, 240, SstvFamily.AVT,
        Avt(pixelSeconds = 24.0 / 240 / 3 / 320), isExperimental = true
    ),

    /** Experimental — see [isExperimental]. The digital sync header is not implemented. */
    AVT_90(
        "AVT 90", 68, 320, 240, SstvFamily.AVT,
        Avt(pixelSeconds = 90.0 / 240 / 3 / 320), isExperimental = true
    ),

    /** Experimental — see [isExperimental]. The digital sync header is not implemented. */
    AVT_125(
        "AVT 125", 72, 320, 240, SstvFamily.AVT,
        Avt(pixelSeconds = 125.0 / 240 / 3 / 320), isExperimental = true
    ),

    /** Experimental — see [isExperimental]. The digital sync header is not implemented. */
    AVT_188(
        "AVT 188", 73, 320, 240, SstvFamily.AVT,
        Avt(pixelSeconds = 188.0 / 240 / 3 / 320), isExperimental = true
    ),

    /** Experimental — see [isExperimental]. The original 8-second monochrome format. */
    MONO_8(
        "8 s B/W", 1, 120, 120, SstvFamily.LEGACY,
        Monochrome(scanSeconds = 8.0 / 120 - 0.005, syncSeconds = 0.005), isExperimental = true
    ),

    /** Experimental — see [isExperimental]. */
    MONO_16(
        "16 s B/W", 3, 120, 120, SstvFamily.LEGACY,
        Monochrome(scanSeconds = 16.0 / 120 - 0.005, syncSeconds = 0.005), isExperimental = true
    ),

    /** Experimental — see [isExperimental]. */
    MONO_32(
        "32 s B/W", 5, 256, 240, SstvFamily.LEGACY,
        Monochrome(scanSeconds = 32.0 / 240 - 0.005, syncSeconds = 0.005), isExperimental = true
    );

    init {
        require(height % format.rowsPerLine == 0) {
            "$displayName sends ${format.rowsPerLine} picture rows per line, so its height " +
                "($height) must be a multiple of ${format.rowsPerLine}"
        }
        require(visCode in 0..127) { "$displayName has a VIS code outside the 7-bit range: $visCode" }
    }

    /** Picture rows carried by one transmitted line — 1 everywhere except the PD family, which sends 2. */
    public val rowsPerLine: Int get() = format.rowsPerLine

    /** Number of scan lines actually transmitted, which is [height] except in the PD family. */
    public val lineCount: Int get() = height / format.rowsPerLine

    /** Duration of a single transmitted scan line, including sync and separator overhead. */
    public val lineSeconds: Double get() = format.lineSeconds(this)

    /** Time on air for the picture alone, excluding the header and any trailing silence. */
    public val pictureSeconds: Double get() = lineSeconds * lineCount

    /** Number of pixels one frame of this mode holds. */
    public val pixelCount: Int get() = width * height

    public companion object {
        /** Frequency representing black in the luminance sub-carrier. */
        public const val FREQ_BLACK: Double = 1500.0

        /** Frequency representing white in the luminance sub-carrier. */
        public const val FREQ_WHITE: Double = 2300.0

        /** Sync / VIS start-stop tone. */
        public const val FREQ_SYNC: Double = 1200.0

        /** Leader tone that opens a transmission, and the porch ahead of a chrominance sweep. */
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

        /** Looks a mode up by the VIS code a receiver would have decoded, or null if unassigned. */
        @JvmStatic
        public fun fromVisCode(visCode: Int): SstvMode? =
            entries.firstOrNull { it.visCode == visCode }

        /** The modes of one [family], in declaration order. */
        @JvmStatic
        public fun inFamily(family: SstvFamily): List<SstvMode> =
            entries.filter { it.family == family }
    }
}
