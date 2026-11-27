package com.sonylut.studio

import java.io.DataOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/** Streaming RGB PNG encoder using unsigned 16-bit samples (network byte order). */
object Png16Encoder {
    fun writeRgb(
        width: Int,
        height: Int,
        output: OutputStream,
        writeRow: (row: Int, scanline: ByteArray) -> Unit
    ) {
        require(width > 0 && height > 0)
        val data = DataOutputStream(output)
        data.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        val ihdr = byteArrayOf(
            (width ushr 24).toByte(), (width ushr 16).toByte(), (width ushr 8).toByte(), width.toByte(),
            (height ushr 24).toByte(), (height ushr 16).toByte(), (height ushr 8).toByte(), height.toByte(),
            16, 2, 0, 0, 0
        )
        writeChunk(data, "IHDR", ihdr)
        // Explicitly tag the encoded RGB values as sRGB (rendering intent: perceptual).
        writeChunk(data, "sRGB", byteArrayOf(0))

        val idat = IdatChunkOutputStream(data)
        val deflater = Deflater(6)
        val compressed = DeflaterOutputStream(idat, deflater, 32 * 1024)
        val scanline = ByteArray(1 + width * 6)
        try {
            for (y in 0 until height) {
                scanline[0] = 0 // PNG filter: None
                writeRow(y, scanline)
                compressed.write(scanline)
            }
            compressed.finish()
            compressed.flush()
            idat.finish()
        } finally {
            deflater.end()
        }
        writeChunk(data, "IEND", ByteArray(0))
        data.flush()
    }

    private fun writeChunk(out: DataOutputStream, type: String, payload: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.writeInt(payload.size)
        out.write(typeBytes)
        out.write(payload)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(payload)
        out.writeInt(crc.value.toInt())
    }

    private class IdatChunkOutputStream(private val out: DataOutputStream) : OutputStream() {
        private val buffer = ByteArray(32 * 1024)
        private var count = 0
        override fun write(value: Int) {
            buffer[count++] = value.toByte()
            if (count == buffer.size) flushChunk()
        }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            var start = offset
            var remaining = length
            while (remaining > 0) {
                val copied = minOf(remaining, buffer.size - count)
                System.arraycopy(bytes, start, buffer, count, copied)
                count += copied
                start += copied
                remaining -= copied
                if (count == buffer.size) flushChunk()
            }
        }
        fun finish() { if (count > 0) flushChunk() }
        private fun flushChunk() {
            if (count == 0) return
            val type = "IDAT".toByteArray(Charsets.US_ASCII)
            out.writeInt(count)
            out.write(type)
            out.write(buffer, 0, count)
            val crc = CRC32()
            crc.update(type)
            crc.update(buffer, 0, count)
            out.writeInt(crc.value.toInt())
            count = 0
        }
    }
}
