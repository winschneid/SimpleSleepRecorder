package com.example.simplesleeprecorder.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class AiffToWavConverterTest {

    @Test
    fun convertsUncompressedAiffToLittleEndianWav() {
        // Minimal mono / 16-bit / 44100 Hz AIFF with two big-endian samples.
        val aiff = buildAiff(
            channels = 1,
            sampleSizeBits = 16,
            frames = 2,
            pcmBigEndian = byteArrayOf(0x12, 0x34, 0x56, 0x78),
        )

        val out = ByteArrayOutputStream()
        AiffToWavConverter.convert(aiff.inputStream(), out)
        val wav = out.toByteArray()

        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(wav, 12, 4, Charsets.US_ASCII))
        assertEquals(1, leShort(wav, 20))       // PCM
        assertEquals(1, leShort(wav, 22))       // channels
        assertEquals(44100, leInt(wav, 24))     // sample rate (decoded from 80-bit extended)
        assertEquals(88200, leInt(wav, 28))     // byte rate = 44100 * 1 * 2
        assertEquals(2, leShort(wav, 32))       // block align
        assertEquals(16, leShort(wav, 34))      // bits per sample
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        assertEquals(4, leInt(wav, 40))         // data length

        // 16-bit samples must be byte-swapped big-endian -> little-endian.
        val data = wav.copyOfRange(44, wav.size)
        assertArrayEquals(byteArrayOf(0x34, 0x12, 0x78, 0x56), data)
    }

    private fun buildAiff(channels: Int, sampleSizeBits: Int, frames: Int, pcmBigEndian: ByteArray): ByteArray {
        val comm = ByteArrayOutputStream().apply {
            writeShortBE(channels)
            writeIntBE(frames)
            writeShortBE(sampleSizeBits)
            // 44100 Hz as an 80-bit IEEE 754 extended float.
            write(byteArrayOf(0x40, 0x0E, 0xAC.toByte(), 0x44, 0, 0, 0, 0, 0, 0))
        }.toByteArray()

        val ssnd = ByteArrayOutputStream().apply {
            writeIntBE(0) // offset
            writeIntBE(0) // blockSize
            write(pcmBigEndian)
        }.toByteArray()

        val body = ByteArrayOutputStream().apply {
            write("AIFF".toByteArray(Charsets.US_ASCII))
            write("COMM".toByteArray(Charsets.US_ASCII)); writeIntBE(comm.size); write(comm)
            write("SSND".toByteArray(Charsets.US_ASCII)); writeIntBE(ssnd.size); write(ssnd)
        }.toByteArray()

        return ByteArrayOutputStream().apply {
            write("FORM".toByteArray(Charsets.US_ASCII))
            writeIntBE(body.size)
            write(body)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeIntBE(v: Int) {
        write((v ushr 24) and 0xff); write((v ushr 16) and 0xff); write((v ushr 8) and 0xff); write(v and 0xff)
    }

    private fun ByteArrayOutputStream.writeShortBE(v: Int) {
        write((v ushr 8) and 0xff); write(v and 0xff)
    }

    private fun leShort(b: ByteArray, o: Int) = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)
    private fun leInt(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or
            ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)
}
