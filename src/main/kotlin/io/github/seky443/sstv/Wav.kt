@file:JvmName("Wav")

package io.github.seky443.sstv

import java.io.File
import java.io.OutputStream

/**
 * WAV export for an encoded transmission.
 *
 * A canonical 44-byte RIFF/WAVE header in front of the raw PCM is all a `.wav` file is, so this
 * stays dependency free and works the same on the JVM and on Android.
 */

/** Size of a canonical RIFF/WAVE header for uncompressed PCM. */
private const val WAV_HEADER_BYTES = 44

private const val PCM_FORMAT = 1
private const val CHANNELS = 1
private const val BITS_PER_SAMPLE = 16

/** The whole transmission as the bytes of a mono 16-bit PCM `.wav` file. */
public fun SstvSignal.toWavBytes(): ByteArray {
    val audio = toByteArray()
    val out = ByteArray(WAV_HEADER_BYTES + audio.size)
    writeWavHeader(out, sampleRate, audio.size)
    audio.copyInto(out, WAV_HEADER_BYTES)
    return out
}

/** Writes the transmission to [stream] as a mono 16-bit PCM `.wav`. The stream is not closed. */
public fun SstvSignal.writeWav(stream: OutputStream) {
    stream.write(toWavBytes())
    stream.flush()
}

/** Writes the transmission to [file] as a mono 16-bit PCM `.wav`, replacing anything already there. */
public fun SstvSignal.writeWav(file: File) {
    file.parentFile?.mkdirs()
    file.outputStream().buffered().use { writeWav(it) }
}

private fun writeWavHeader(out: ByteArray, sampleRate: Int, audioBytes: Int) {
    val byteRate = sampleRate * CHANNELS * BITS_PER_SAMPLE / 8
    val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8

    out.putAscii(0, "RIFF")
    out.putIntLe(4, 36 + audioBytes)      // size of everything after this field
    out.putAscii(8, "WAVE")

    out.putAscii(12, "fmt ")
    out.putIntLe(16, 16)                  // fmt chunk size for PCM
    out.putShortLe(20, PCM_FORMAT)
    out.putShortLe(22, CHANNELS)
    out.putIntLe(24, sampleRate)
    out.putIntLe(28, byteRate)
    out.putShortLe(32, blockAlign)
    out.putShortLe(34, BITS_PER_SAMPLE)

    out.putAscii(36, "data")
    out.putIntLe(40, audioBytes)
}

private fun ByteArray.putAscii(offset: Int, text: String) {
    for (i in text.indices) this[offset + i] = text[i].code.toByte()
}

private fun ByteArray.putIntLe(offset: Int, value: Int) {
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value shr 8) and 0xFF).toByte()
    this[offset + 2] = ((value shr 16) and 0xFF).toByte()
    this[offset + 3] = ((value shr 24) and 0xFF).toByte()
}

private fun ByteArray.putShortLe(offset: Int, value: Int) {
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value shr 8) and 0xFF).toByte()
}