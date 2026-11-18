package ai.aethra.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PreparedMediaInput(
    val contents: Contents,
    private val temporaryFiles: List<File>,
    val preparationNote: String,
) : Closeable {
    override fun close() { temporaryFiles.forEach { it.delete() } }
}

/** Converts Android content URIs into LiteRT-LM-supported file inputs. */
object MediaInputProcessor {
    private const val MAX_TEXT_CHARS = 100_000

    suspend fun prepare(
        context: Context,
        uri: Uri,
        mimeType: String?,
        prompt: String,
    ): PreparedMediaInput = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val maxVideoFrames = MediaProcessingPreferences.maxVideoFrames(context)
        val maxPdfPages = MediaProcessingPreferences.maxPdfPages(context)
        val maxImageDimension = MediaProcessingPreferences.maxImageDimension(context)
        val files = mutableListOf<File>()
        val contents = mutableListOf<Content>()
        if (prompt.isNotBlank()) contents += Content.Text(prompt)
        val note: String

        try {
            when {
                mimeType?.startsWith("image/") == true -> {
                    if (prompt.isBlank()) contents += Content.Text("Describe this image in detail.")
                    val file = stageImage(context, uri, maxImageDimension)
                    files += file
                    contents += Content.ImageFile(file.absolutePath)
                    note = "Image attached for visual analysis."
                }
                mimeType?.startsWith("audio/") == true -> {
                    if (prompt.isBlank()) contents += Content.Text("Transcribe and summarize this audio.")
                    val file = AudioTranscoder.toMono16KhzWav(
                        context, uri, MediaProcessingPreferences.maxVideoAudioSeconds(context)
                    )
                    files += file
                    contents += Content.AudioFile(file.absolutePath)
                    note = "Audio decoded and normalized to mono 16 kHz WAV for model audio understanding."
                }
                mimeType?.startsWith("video/") == true -> {
                    val frames = extractVideoFrames(context, uri, maxVideoFrames)
                    check(frames.isNotEmpty()) { "Could not decode frames from this video." }
                    files += frames
                    val audioTrack = runCatching {
                        AudioTranscoder.toMono16KhzWav(
                            context, uri, MediaProcessingPreferences.maxVideoAudioSeconds(context), "aethra_video_audio_"
                        )
                    }.getOrNull()
                    if (audioTrack != null) files += audioTrack
                    contents.clear()
                    contents += Content.Text(
                        (if (prompt.isBlank()) "Summarize this video using its sampled frames" else prompt) +
                            if (audioTrack != null) ". A separate audio track converted to mono 16 kHz WAV for up to ${MediaProcessingPreferences.maxVideoAudioSeconds(context)} seconds is also attached." else ". Video soundtrack could not be extracted; analyze the sampled frames only."
                    )
                    frames.forEach { contents += Content.ImageFile(it.absolutePath) }
                    if (audioTrack != null) contents += Content.AudioFile(audioTrack.absolutePath)
                    note = if (audioTrack != null) {
                        "Video sampled into ${frames.size} image frames; audio converted to mono 16 kHz WAV for up to ${MediaProcessingPreferences.maxVideoAudioSeconds(context)} seconds is attached separately."
                    } else {
                        "Video sampled into ${frames.size} image frames. No usable audio track was extracted."
                    }
                }
                mimeType == "application/pdf" -> {
                    val pages = renderPdfPages(context, uri, maxPdfPages)
                    check(pages.isNotEmpty()) { "Could not render pages from this PDF." }
                    files += pages
                    contents.clear()
                    contents += Content.Text((if (prompt.isBlank()) "Analyze this PDF." else prompt) + " PDF input is represented by up to $maxPdfPages rendered pages.")
                    pages.forEach { contents += Content.ImageFile(it.absolutePath) }
                    note = "PDF rendered into ${pages.size} page images for visual analysis."
                }
                mimeType?.startsWith("text/") == true -> {
                    val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(MAX_TEXT_CHARS) }
                        ?: error("Could not read this text document.")
                    contents += Content.Text((if (prompt.isBlank()) "Analyze this document." else prompt) + "\n\nDocument content:\n" + text)
                    note = "Text document content added to the prompt."
                }
                else -> error("Unsupported file type: ${mimeType ?: "unknown"}. Use image, audio, video, PDF or plain text.")
            }
            check(contents.isNotEmpty()) { "No model input was prepared." }
            PreparedMediaInput(Contents.of(*contents.toTypedArray()), files, note)
        } catch (error: Throwable) {
            files.forEach { it.delete() }
            throw error
        }
    }

    private fun stageImage(context: Context, uri: Uri, maxDimension: Int): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("Could not inspect selected image.")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Could not decode selected image.")
        var resized: Bitmap? = null
        var file: File? = null
        try {
            val scale = minOf(1f, maxDimension.toFloat() / maxOf(decoded.width, decoded.height).toFloat())
            val outputBitmap = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true) else decoded
            resized = outputBitmap
            val outputFile = File.createTempFile("aethra_image_", ".jpg", context.cacheDir)
            file = outputFile
            FileOutputStream(outputFile).use { output ->
                check(outputBitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) { "Could not encode selected image." }
            }
            return outputFile
        } catch (error: Throwable) {
            file?.delete()
            throw error
        } finally {
            if (resized != null && resized !== decoded) resized.recycle()
            decoded.recycle()
        }
    }

    private fun extractVideoFrames(context: Context, uri: Uri, maxFrames: Int): List<File> {
        val retriever = MediaMetadataRetriever()
        val output = mutableListOf<File>()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frameCount = minOf(maxFrames, maxOf(1, (durationMs / 3000L).toInt() + 1))
            for (index in 0 until frameCount) {
                val fraction = if (frameCount == 1) 0.5 else index.toDouble() / (frameCount - 1)
                val timeUs = (durationMs * 1000.0 * fraction).toLong()
                val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                val file = File.createTempFile("aethra_video_frame_", ".jpg", context.cacheDir)
                try {
                    FileOutputStream(file).use {
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it)) { "Could not encode video frame." }
                    }
                    output += file
                } catch (error: Throwable) {
                    file.delete()
                    throw error
                } finally {
                    bitmap.recycle()
                }
            }
        } catch (error: Throwable) {
            output.forEach { it.delete() }
            throw error
        } finally {
            retriever.release()
        }
        return output
    }

    private fun renderPdfPages(context: Context, uri: Uri, maxPages: Int): List<File> {
        val descriptor: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Could not open this PDF.")
        val output = mutableListOf<File>()
        try {
            descriptor.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val pageCount = minOf(renderer.pageCount, maxPages)
                    for (index in 0 until pageCount) {
                        renderer.openPage(index).use { page ->
                            val scale = minOf(1.0, 1400.0 / maxOf(page.width, page.height))
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val file = File.createTempFile("aethra_pdf_page_", ".jpg", context.cacheDir)
                                try {
                                    FileOutputStream(file).use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
                                    output += file
                                } catch (error: Throwable) {
                                    file.delete()
                                    throw error
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
            return output
        } catch (error: Throwable) {
            output.forEach { it.delete() }
            throw error
        }
    }

}
