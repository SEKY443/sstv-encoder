package io.github.seky443.sstv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The timeline mapping is what lets a caller draw a picture in step with the audio. */
class SstvSignalTest {

    private val mode = SstvMode.MARTIN_M1
    private val signal = SstvEncoder.encode(IntArray(mode.pixelCount), mode)

    @Test
    fun `no line is on air during the header`() {
        assertNull(signal.lineIndexAt(0.0))
        assertNull(signal.lineIndexAt(SstvEncoder.HEADER_SECONDS - 0.001))
        assertEquals(0f, signal.linePositionAt(0.5), 0f)
    }

    @Test
    fun `the first line starts as soon as the header ends`() {
        assertEquals(0, signal.lineIndexAt(SstvEncoder.HEADER_SECONDS))
    }

    @Test
    fun `line index tracks the elapsed line time`() {
        val into = SstvEncoder.HEADER_SECONDS + mode.lineSeconds * 42.5
        assertEquals(42, signal.lineIndexAt(into))
        assertEquals(42.5f, signal.linePositionAt(into), 0.01f)
    }

    @Test
    fun `no line is on air during the trailing silence`() {
        val afterPicture = SstvEncoder.HEADER_SECONDS + mode.pictureSeconds + 0.1
        assertNull(signal.lineIndexAt(afterPicture))
        assertEquals(mode.height.toFloat(), signal.linePositionAt(afterPicture), 0f)
    }

    @Test
    fun `pcm converts to little endian bytes`() {
        val bytes = SstvSignal(
            pcm = shortArrayOf(0, 1, -1, Short.MAX_VALUE, Short.MIN_VALUE),
            sampleRate = 22050,
            mode = mode,
            headerSeconds = 0.0,
            trailingSilenceSeconds = 0.0
        ).toByteArray()

        assertEquals(10, bytes.size)
        assertEquals(listOf(0, 0, 1, 0, -1, -1, -1, 127, 0, -128), bytes.map { it.toInt() })
    }
}