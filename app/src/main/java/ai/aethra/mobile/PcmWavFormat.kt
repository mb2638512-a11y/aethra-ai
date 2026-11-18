package ai.aethra.mobile

/** RIFF/WAVE header for mono, signed 16-bit little-endian PCM at 16 kHz. */
object PcmWavFormat {
    const val SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 32
    const val BLOCK_ALIGN = CHANNELS * BITS_PER_SAMPLE / 8
    const val BYTE_RATE = SAMPLE_RATE * BLOCK_ALIGN

    fun header(dataBytes: Int): ByteArray {
        require(dataBytes >= 0) { "WAV data length cannot be negative." }
        require(dataBytes <= Int.MAX_VALUE - 36) { "WAV data is too large." }
        val result = ByteArray(44)
        fun ascii(offset: Int, value: String) {
            value.toByteArray(Charsets.US_ASCII).copyInto(result, offset)
        }
        fun littleEndian(offset: Int, value: Int) {
            result[offset] = value.toByte()
            result[offset + 1] = (value ushr 8).toByte()
            result[offset + 2] = (value ushr 16).toByte()
            result[offset + 3] = (value ushr 24).toByte()
        }
        fun littleEndianShort(offset: Int, value: Int) {
            result[offset] = value.toByte()
            result[offset + 1] = (value ushr 8).toByte()
        }
        ascii(0, "RIFF")
        littleEndian(4, 36 + dataBytes)
        ascii(8, "WAVE")
        ascii(12, "fmt ")
        littleEndian(16, 16)
        littleEndianShort(20, 3)
        littleEndianShort(22, CHANNELS)
        littleEndian(24, SAMPLE_RATE)
        littleEndian(28, BYTE_RATE)
        littleEndianShort(32, BLOCK_ALIGN)
        littleEndianShort(34, BITS_PER_SAMPLE)
        ascii(36, "data")
        littleEndian(40, dataBytes)
        return result
    }
}
