package ai.aethra.mobile

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Decodes Android-supported compressed audio to the mono 16 kHz float32 WAV input
 * expected by the current LiteRT-LM audio path.
 *
 * Decoding is streamed to a temporary raw PCM file; the full decoded track is
 * never held in memory. The source duration is bounded by the caller.
 */
object AudioTranscoder {
    private const val TARGET_SAMPLE_RATE = PcmWavFormat.SAMPLE_RATE

    fun toMono16KhzWav(context: Context, uri: Uri, maxSeconds: Int, prefix: String = "aethra_audio_"): File {
        val raw = File.createTempFile("${prefix}raw_", ".pcm", context.cacheDir)
        var wav: File? = null
        try {
            val decoded = decodeToMonoPcm(context, uri, raw, maxSeconds.coerceIn(1, 30))
            check(decoded.sampleCount > 0L) { "The selected audio contains no decodable samples." }
            wav = File.createTempFile(prefix, ".wav", context.cacheDir)
            writeResampledWav(raw, wav, decoded.sampleCount, decoded.sampleRate)
            check(wav.length() > 44L) { "Audio conversion produced an empty WAV file." }
            return wav
        } catch (error: Throwable) {
            wav?.delete()
            throw error
        } finally {
            raw.delete()
        }
    }

    private data class DecodedPcm(val sampleRate: Int, val sampleCount: Long)

    private fun decodeToMonoPcm(context: Context, uri: Uri, raw: File, maxSeconds: Int): DecodedPcm {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var sampleRate = 0
        var sampleCount = 0L
        var inputEnded = false
        var outputEnded = false
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track was found in the selected media.")
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("Audio codec is unknown.")
            extractor.selectTrack(trackIndex)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            FileOutputStream(raw).use { output ->
                val info = MediaCodec.BufferInfo()
                val maxDurationUs = maxSeconds * 1_000_000L
                while (!outputEnded) {
                    if (!inputEnded) {
                        val inputIndex = codec.dequeueInputBuffer(10_000L)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                                ?: error("Audio decoder returned an empty input buffer.")
                            inputBuffer.clear()
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            val timestamp = extractor.sampleTime
                            if (sampleSize < 0 || timestamp < 0L || timestamp > maxDurationUs) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, timestamp, extractor.sampleFlags)
                                if (!extractor.advance()) {
                                    // The next input request sends EOS.
                                    inputEnded = false
                                    extractor.selectTrack(trackIndex)
                                    // A sentinel is not needed: readSampleData() returns -1 at end.
                                }
                            }
                        }
                    }

                    when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000L)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val format = codec.outputFormat
                            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        else -> if (outputIndex >= 0) {
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                            if (outputBuffer != null && info.size > 0 &&
                                info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                            ) {
                                val format = codec.outputFormat
                                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                                outputBuffer.position(info.offset)
                                outputBuffer.limit(info.offset + info.size)
                                outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                                when (format.getIntegerOrDefault(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)) {
                                    AudioFormat.ENCODING_PCM_16BIT -> {
                                        while (outputBuffer.remaining() >= channels * 2) {
                                            var sum = 0
                                            repeat(channels) { sum += outputBuffer.short.toInt() }
                                            val mono = (sum / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                            output.write(mono and 0xff)
                                            output.write((mono shr 8) and 0xff)
                                            sampleCount++
                                        }
                                    }
                                    AudioFormat.ENCODING_PCM_FLOAT -> {
                                        while (outputBuffer.remaining() >= channels * 4) {
                                            var sum = 0f
                                            repeat(channels) { sum += outputBuffer.float }
                                            val mono = (sum / channels).coerceIn(-1f, 1f)
                                            val pcm = (mono * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                            output.write(pcm and 0xff)
                                            output.write((pcm shr 8) and 0xff)
                                            sampleCount++
                                        }
                                    }
                                    else -> error("Unsupported decoded PCM format.")
                                }
                            }
                            outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }
            check(sampleRate > 0) { "Audio decoder did not report a sample rate." }
            return DecodedPcm(sampleRate, sampleCount)
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
    }

    private fun writeResampledWav(raw: File, wav: File, sourceSamples: Long, sourceRate: Int) {
        val outputSamples = (sourceSamples * TARGET_SAMPLE_RATE + sourceRate - 1L) / sourceRate
        val dataBytes = outputSamples * 4L
        require(dataBytes <= 0x7fffffffL) { "Audio segment is too large to encode as WAV." }
        RandomAccessFile(raw, "r").use { source ->
            FileOutputStream(wav).use { output ->
                writeWavHeader(output, dataBytes.toInt())
                var outputIndex = 0L
                while (outputIndex < outputSamples) {
                    val sourceIndex = (outputIndex * sourceRate / TARGET_SAMPLE_RATE).coerceAtMost(sourceSamples - 1L)
                    source.seek(sourceIndex * 2L)
                    val sample = Short.reverseBytes(source.readShort()).toInt()
                    val normalized = (sample / 32768.0f).coerceIn(-1.0f, 1.0f)
                    val bits = java.lang.Float.floatToIntBits(normalized)
                    output.write(bits and 0xff)
                    output.write((bits shr 8) and 0xff)
                    output.write((bits shr 16) and 0xff)
                    output.write((bits shr 24) and 0xff)
                    outputIndex++
                }
            }
        }
    }

    private fun writeWavHeader(output: FileOutputStream, dataBytes: Int) {
        output.write(PcmWavFormat.header(dataBytes))
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback
}
