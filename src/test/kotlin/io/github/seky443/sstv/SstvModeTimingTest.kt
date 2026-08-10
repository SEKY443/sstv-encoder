package io.github.seky443.sstv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the published mode timings.
 *
 * These numbers are the contract with every external decoder, and a drift of a few milliseconds per
 * line shears the received picture. The expected values are transcribed from the specifications
 * themselves — JL Barber N7CXI, *Proposal for SSTV Mode Specifications* (2000), and Dave Jones
 * KB4YZ, *SSTV modes - line timing* (1999) — not derived from the encoder, so the test catches a
 * component timing being "tidied up" as well as an arithmetic slip.
 */
class SstvModeTimingTest {

    /** Published line time in milliseconds, for every mode with a specification behind it. */
    private val publishedLineMillis = mapOf(
        SstvMode.MARTIN_M1 to 446.446,
        SstvMode.MARTIN_M2 to 226.7986,
        SstvMode.MARTIN_M3 to 446.446,
        SstvMode.MARTIN_M4 to 226.7986,

        SstvMode.SCOTTIE_S1 to 428.22,
        SstvMode.SCOTTIE_S2 to 277.692,
        SstvMode.SCOTTIE_DX to 1050.3,

        SstvMode.ROBOT_8_BW to 66.9,
        SstvMode.ROBOT_12_BW to 100.0,
        SstvMode.ROBOT_24_BW to 100.0,
        SstvMode.ROBOT_36 to 150.0,
        SstvMode.ROBOT_72 to 300.0,

        SstvMode.PD_50 to 388.16,
        SstvMode.PD_90 to 703.04,
        SstvMode.PD_120 to 508.48,
        SstvMode.PD_160 to 804.416,
        SstvMode.PD_180 to 754.24,
        SstvMode.PD_240 to 1000.0,
        SstvMode.PD_290 to 937.28,

        SstvMode.PASOKON_P3 to 409.375,
        SstvMode.PASOKON_P5 to 614.0625,
        SstvMode.PASOKON_P7 to 818.75,

        SstvMode.WRAASE_SC2_120 to 475.5225,
        SstvMode.WRAASE_SC2_180 to 711.0225
    )

    /** Nominal on-air time the mode is named for, in seconds. */
    private val nominalFrameSeconds = mapOf(
        SstvMode.MARTIN_M1 to 114.0,
        SstvMode.MARTIN_M2 to 58.0,
        SstvMode.MARTIN_M3 to 57.0,
        SstvMode.MARTIN_M4 to 29.0,

        SstvMode.SCOTTIE_S1 to 110.0,
        SstvMode.SCOTTIE_S2 to 71.0,
        SstvMode.SCOTTIE_DX to 269.0,

        SstvMode.ROBOT_8_BW to 8.0,
        SstvMode.ROBOT_12_BW to 12.0,
        SstvMode.ROBOT_24_BW to 24.0,
        SstvMode.ROBOT_36 to 36.0,
        SstvMode.ROBOT_72 to 72.0,

        SstvMode.PD_50 to 50.0,
        SstvMode.PD_90 to 90.0,
        SstvMode.PD_120 to 126.0,
        SstvMode.PD_160 to 161.0,
        SstvMode.PD_180 to 187.0,
        SstvMode.PD_240 to 248.0,
        SstvMode.PD_290 to 289.0,

        SstvMode.PASOKON_P3 to 203.0,
        SstvMode.PASOKON_P5 to 305.0,
        SstvMode.PASOKON_P7 to 406.0,

        SstvMode.WRAASE_SC2_120 to 121.0,
        SstvMode.WRAASE_SC2_180 to 182.0
    )

    /** VIS codes from Dave Jones KB4YZ, *List of SSTV Modes with VIS Codes* (1998). */
    private val publishedVisCodes = mapOf(
        SstvMode.ROBOT_8_BW to 0x02,
        SstvMode.ROBOT_12_BW to 0x06,
        SstvMode.ROBOT_36 to 0x08,
        SstvMode.ROBOT_24_BW to 0x0A,
        SstvMode.ROBOT_72 to 0x0C,

        SstvMode.MARTIN_M4 to 0x20,
        SstvMode.MARTIN_M3 to 0x24,
        SstvMode.MARTIN_M2 to 0x28,
        SstvMode.MARTIN_M1 to 0x2C,

        SstvMode.WRAASE_SC2_180 to 0x37,
        SstvMode.SCOTTIE_S2 to 0x38,
        SstvMode.SCOTTIE_S1 to 0x3C,
        SstvMode.WRAASE_SC2_120 to 0x3F,
        SstvMode.SCOTTIE_DX to 0x4C,

        SstvMode.PD_50 to 0x5D,
        SstvMode.PD_290 to 0x5E,
        SstvMode.PD_120 to 0x5F,
        SstvMode.PD_180 to 0x60,
        SstvMode.PD_240 to 0x61,
        SstvMode.PD_160 to 0x62,
        SstvMode.PD_90 to 0x63,

        SstvMode.PASOKON_P3 to 0x71,
        SstvMode.PASOKON_P5 to 0x72,
        SstvMode.PASOKON_P7 to 0x73
    )

    @Test
    fun `line times match the published specifications`() {
        for ((mode, expectedMillis) in publishedLineMillis) {
            // 1 us of slack. Most modes reconstruct their published line time exactly, but the
            // tables round: Martin M2 and M4 are quoted at 226.7986 ms, which does not reconcile
            // with their own 0.2288 ms pixel time — that sums to 226.798 ms. The component timings
            // are the specification and the quoted line time is a rounded restatement of them, so
            // the components win. A real timing error is hundreds of microseconds at minimum.
            assertEquals(
                "${mode.displayName} line time",
                expectedMillis / 1000.0,
                mode.lineSeconds,
                1e-6
            )
        }
    }

    @Test
    fun `frame times land on the duration each mode is named for`() {
        for ((mode, expectedSeconds) in nominalFrameSeconds) {
            // Mode names are rounded to the nearest second or two, so this is a sanity check that
            // the line time and line count agree, not a precise assertion.
            assertEquals(
                "${mode.displayName} frame time",
                expectedSeconds,
                mode.pictureSeconds,
                1.2
            )
        }
    }

    @Test
    fun `vis codes match the published assignments`() {
        for ((mode, expected) in publishedVisCodes) {
            assertEquals("${mode.displayName} VIS code", expected, mode.visCode)
        }
    }

    @Test
    fun `every non-experimental mode has a published line time and vis code`() {
        val specified = SstvMode.entries.filterNot { it.isExperimental }
        for (mode in specified) {
            assertTrue(
                "${mode.displayName} is not marked experimental but has no published line time",
                mode in publishedLineMillis
            )
            assertTrue(
                "${mode.displayName} is not marked experimental but has no published VIS code",
                mode in publishedVisCodes
            )
        }
    }

    @Test
    fun `vis codes are unique and fit the 7-bit field`() {
        val seen = mutableMapOf<Int, SstvMode>()
        for (mode in SstvMode.entries) {
            val clash = seen.put(mode.visCode, mode)
            assertEquals(
                "${mode.displayName} and ${clash?.displayName} share VIS code ${mode.visCode}",
                null,
                clash
            )
            assertTrue("${mode.displayName} VIS code out of range", mode.visCode in 0..127)
        }
    }

    @Test
    fun `pd modes carry two picture rows per transmitted line`() {
        for (mode in SstvMode.inFamily(SstvFamily.PD)) {
            assertEquals("${mode.displayName} rows per line", 2, mode.rowsPerLine)
            assertEquals("${mode.displayName} line count", mode.height / 2, mode.lineCount)
        }
    }

    @Test
    fun `every other mode sends one picture row per line`() {
        for (mode in SstvMode.entries.filter { it.family != SstvFamily.PD }) {
            assertEquals("${mode.displayName} rows per line", 1, mode.rowsPerLine)
            assertEquals("${mode.displayName} line count", mode.height, mode.lineCount)
        }
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
        assertEquals(SstvMode.PD_120, SstvMode.fromName("pd_120"))
        assertEquals(SstvMode.MARTIN_M1, SstvMode.fromName(null))
        assertEquals(SstvMode.MARTIN_M1, SstvMode.fromName("nonsense"))
    }

    @Test
    fun `fromVisCode round trips every mode`() {
        for (mode in SstvMode.entries) {
            assertEquals(mode, SstvMode.fromVisCode(mode.visCode))
        }
        assertEquals(null, SstvMode.fromVisCode(0x7F))
    }

    @Test
    fun `every family has at least one mode`() {
        for (family in SstvFamily.entries) {
            assertTrue("$family has no modes", SstvMode.inFamily(family).isNotEmpty())
        }
        assertNotNull(SstvMode.inFamily(SstvFamily.PD))
    }
}
