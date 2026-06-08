package com.example.simplesleeprecorder.audio

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Converts uncompressed AIFF / AIFF-C (PCM) audio into a WAV file.
 *
 * Android's MediaPlayer can't decode AIFF, but plain AIFF is just big-endian PCM
 * wrapped in IFF chunks, so we repackage it as little-endian WAV — which
 * MediaPlayer plays natively. Only PCM is supported: AIFF (compression "NONE",
 * big-endian) and AIFF-C "sowt" (little-endian). Genuinely compressed AIFF-C
 * (e.g. ALAC, μ-law) throws [UnsupportedAiffException].
 */
object AiffToWavConverter {

    class UnsupportedAiffException(message: String) : IOException(message)

    private data class CommonChunk(
        val channels: Int,
        val sampleSizeBits: Int,
        val sampleRate: Int,
        val bigEndian: Boolean,
    )

    /**
     * Reads AIFF bytes from [input] and writes a WAV stream to [output].
     * Assumes the COMM chunk precedes the SSND chunk (the standard layout).
     */
    fun convert(input: InputStream, output: OutputStream) {
        val data = DataInputStream(BufferedInputStream(input))
        val out = BufferedOutputStream(output)

        if (readTag(data) != "FORM") throw UnsupportedAiffException("Not an AIFF file (missing FORM)")
        data.skipBytes(4) // FORM size
        val formType = readTag(data)
        if (formType != "AIFF" && formType != "AIFC") {
            throw UnsupportedAiffException("Unsupported form type: $formType")
        }

        var common: CommonChunk? = null
        while (true) {
            val id = readTagOrNull(data) ?: throw UnsupportedAiffException("No SSND chunk found")
            val size = data.readInt() // big-endian
            when (id) {
                "COMM" -> {
                    common = readCommonChunk(data, size, isAifc = formType == "AIFC")
                }
                "SSND" -> {
                    val comm = common ?: throw UnsupportedAiffException("COMM chunk missing before SSND")
                    data.skipBytes(8) // offset + blockSize
                    val pcmLength = size - 8
                    writeWavHeader(out, comm, pcmLength)
                    streamPcm(data, out, pcmLength, comm)
                    out.flush()
                    return
                }
                else -> {
                    skipFully(data, size.toLong())
                    if (size % 2 == 1) data.skipBytes(1) // chunks are word-aligned
                }
            }
        }
    }

    private fun readCommonChunk(data: DataInputStream, size: Int, isAifc: Boolean): CommonChunk {
        val channels = data.readUnsignedShort()
        data.skipBytes(4) // numSampleFrames
        val sampleSize = data.readUnsignedShort()
        val rateBytes = ByteArray(10)
        data.readFully(rateBytes)
        val sampleRate = extended80ToInt(rateBytes)

        var bigEndian = true
        var consumed = 18
        if (isAifc) {
            when (val compression = readTag(data)) {
                "NONE", "twos" -> bigEndian = true
                "sowt" -> bigEndian = false
                else -> throw UnsupportedAiffException("Compressed AIFF-C not supported: $compression")
            }
            consumed += 4
            // Skip the rest of the COMM chunk (pascal-string compression name).
            skipFully(data, (size - consumed).toLong())
        }
        if (size % 2 == 1) data.skipBytes(1)
        return CommonChunk(channels, sampleSize, sampleRate, bigEndian)
    }

    private fun streamPcm(data: DataInputStream, out: OutputStream, pcmLength: Int, comm: CommonChunk) {
        val bytesPerSample = comm.sampleSizeBits / 8
        val frameSize = (bytesPerSample * comm.channels).coerceAtLeast(1)
        // Keep the buffer aligned to whole frames so samples never span buffers.
        val buffer = ByteArray((64 * 1024 / frameSize).coerceAtLeast(1) * frameSize)
        var remaining = pcmLength
        while (remaining > 0) {
            val want = minOf(buffer.size, remaining)
            var read = 0
            while (read < want) {
                val r = data.read(buffer, read, want - read)
                if (r < 0) break
                read += r
            }
            if (read <= 0) break
            convertSamplesInPlace(buffer, read, bytesPerSample, comm.bigEndian)
            out.write(buffer, 0, read)
            remaining -= read
        }
    }

    /** Converts AIFF samples to WAV little-endian PCM in place. */
    private fun convertSamplesInPlace(buffer: ByteArray, length: Int, bytesPerSample: Int, bigEndian: Boolean) {
        when {
            bytesPerSample == 1 -> {
                // AIFF 8-bit is signed; WAV 8-bit is unsigned.
                for (i in 0 until length) buffer[i] = (buffer[i].toInt() xor 0x80).toByte()
            }
            bigEndian -> {
                var i = 0
                while (i + bytesPerSample <= length) {
                    var lo = i
                    var hi = i + bytesPerSample - 1
                    while (lo < hi) {
                        val tmp = buffer[lo]; buffer[lo] = buffer[hi]; buffer[hi] = tmp
                        lo++; hi--
                    }
                    i += bytesPerSample
                }
            }
            // else: already little-endian ("sowt"), no swap needed.
        }
    }

    private fun writeWavHeader(out: OutputStream, comm: CommonChunk, dataLength: Int) {
        val bytesPerSample = comm.sampleSizeBits / 8
        val byteRate = comm.sampleRate * comm.channels * bytesPerSample
        val blockAlign = comm.channels * bytesPerSample
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, 36 + dataLength)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, 16)
        writeShortLE(out, 1) // PCM
        writeShortLE(out, comm.channels)
        writeIntLE(out, comm.sampleRate)
        writeIntLE(out, byteRate)
        writeShortLE(out, blockAlign)
        writeShortLE(out, comm.sampleSizeBits)
        out.write("data".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, dataLength)
    }

    /** Decodes an 80-bit IEEE 754 extended float (used for AIFF sample rate). */
    private fun extended80ToInt(b: ByteArray): Int {
        val exponent = ((b[0].toInt() and 0x7f) shl 8) or (b[1].toInt() and 0xff)
        if (exponent == 0) return 0
        val mantissaHi32 =
            ((b[2].toLong() and 0xff) shl 24) or
                ((b[3].toLong() and 0xff) shl 16) or
                ((b[4].toLong() and 0xff) shl 8) or
                (b[5].toLong() and 0xff)
        val value = mantissaHi32.toDouble() * Math.pow(2.0, (exponent - 16383 - 31).toDouble())
        return Math.round(value).toInt()
    }

    private fun writeIntLE(out: OutputStream, v: Int) {
        out.write(v and 0xff)
        out.write((v ushr 8) and 0xff)
        out.write((v ushr 16) and 0xff)
        out.write((v ushr 24) and 0xff)
    }

    private fun writeShortLE(out: OutputStream, v: Int) {
        out.write(v and 0xff)
        out.write((v ushr 8) and 0xff)
    }

    private fun readTag(data: DataInputStream): String {
        return readTagOrNull(data) ?: throw UnsupportedAiffException("Unexpected end of file")
    }

    private fun readTagOrNull(data: DataInputStream): String? {
        val b = ByteArray(4)
        var read = 0
        while (read < 4) {
            val r = data.read(b, read, 4 - read)
            if (r < 0) return if (read == 0) null else throw UnsupportedAiffException("Truncated chunk id")
            read += r
        }
        return String(b, Charsets.US_ASCII)
    }

    private fun skipFully(data: DataInputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = data.skip(remaining)
            if (skipped <= 0) {
                if (data.read() < 0) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }
}
