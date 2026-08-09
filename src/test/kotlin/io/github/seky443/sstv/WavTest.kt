package io.github.seky443.sstv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem

/**
 * The WAV header is hand-rolled to keep the library dependency free, so it is checked both
 * byte-by-byte and by handing the result to a real audio parser.
 */
class WavTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val signal = SstvEncoder.encode(IntArray(SstvMode.MARTIN_M1.pixelCount))

    private fun ByteArray.ascii(offset: Int, length: Int) =
        String(this, offset, length, Charsets.US_ASCII)

    private fun ByteArray.intLe(offset: Int) =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)

    private fun ByteArray.shortLe(offset: Int) =
        (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

    @Test
    fun `header describes mono 16-bit pcm at the signal sample rate`() {
        val wav = signal.toWavBytes()
        val audioBytes = signal.pcm.size * 2

        assertEquals("RIFF", wav.ascii(0, 4))
        assertEquals(36 + audioBytes, wav.intLe(4))
        assertEquals("WAVE", wav.ascii(8, 4))

        assertEquals("fmt ", wav.ascii(12, 4))
        assertEquals(16, wav.intLe(16))
        assertEquals(1, wav.shortLe(20))                       // PCM
        assertEquals(1, wav.shortLe(22))                       // mono
        assertEquals(signal.sampleRate, wav.intLe(24))
        assertEquals(signal.sampleRate * 2, wav.intLe(28))     // byte rate
        assertEquals(2, wav.shortLe(32))                       // block align
        assertEquals(16, wav.shortLe(34))                      // bits per sample

        assertEquals("data", wav.ascii(36, 4))
        assertEquals(audioBytes, wav.intLe(40))
        assertEquals(44 + audioBytes, wav.size)
    }

    @Test
    fun `javax sound reads the file back with the right format and length`() {
        val stream = AudioSystem.getAudioInputStream(ByteArrayInputStream(signal.toWavBytes()))
        stream.use {
            assertEquals(signal.sampleRate.toFloat(), it.format.sampleRate, 0f)
            assertEquals(16, it.format.sampleSizeInBits)
            assertEquals(1, it.format.channels)
            assertEquals(signal.pcm.size.toLong(), it.frameLength)
        }
    }

    @Test
    fun `writes a playable file to disk`() {
        val file = temp.newFolder("out").resolve("transmission.wav")
        signal.writeWav(file)

        assertTrue("File was not created", file.isFile)
        assertEquals(44L + signal.pcm.size * 2, file.length())
        assertTrue(signal.toWavBytes().contentEquals(file.readBytes()))
    }
}