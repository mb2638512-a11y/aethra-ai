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
    private const val MAX_VIDEO_FRAMES = 4
    private const val MAX_PDF_PAGES = 4
    private const val MAX_TEXT_CHARS = 100_000

    suspend fun prepare(
        context: Context,
        uri: Uri,
        mimeType: String?,
        prompt: String,
    ): PreparedMediaInput = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val files = mutableListOf<File>()
        val contents = mutableListOf<Content>()
        if (prompt.isNotBlank()) contents += Content.Text(prompt)
        val note: String

        when {
            mimeType?.startsWith("image/") == true -> {
                if (prompt.isBlank()) contents += Content.Text("Describe this image in detail.")
                val file = stageImage(context, uri)
                files += file
                contents += Content.ImageFile(file.absolutePath)
                note = "Image attached for visual analysis."
            }
            mimeType?.startsWith("audio/") == true -> {
                if (prompt.isBlank()) contents += Content.Text("Transcribe and summarize this audio.")
                val file = copyToCache(context, uri, extensionFor(mimeType))
                files += file
                contents += Content.AudioFile(file.absolutePath)
                note = "Audio attached for model audio understanding."
            }
            mimeType?.startsWith("video/") == true -> {
                val frames = extractVideoFrames(context, uri)
                check(frames.isNotEmpty()) { "Could not decode frames from this video." }
                files += frames
                frames.forEach { contents += Content.ImageFile(it.absolutePath) }
                contents.clear()
                contents += Content.Text((if (prompt.isBlank()) "Summarize this video using the sampled frames." else prompt) + " Video input is represented by up to $MAX_VIDEO_FRAMES sampled frames; the audio track is not included yet.")
                note = "Video sampled into ${frames.size} image frames. Video audio-track analysis is not included yet."
            }
            mimeType == "application/pdf" -> {
                val pages = renderPdfPages(context, uri)
                check(pages.isNotEmpty()) { "Could not render pages from this PDF." }
                files += pages
                pages.forEach { contents += Content.ImageFile(it.absolutePath) }
                contents.clear()
                contents += Content.Text((if (prompt.isBlank()) "Analyze this PDF." else prompt) + " PDF input is represented by up to $MAX_PDF_PAGES rendered pages.")
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
    }

    private fun stageImage(context: Context, uri: Uri): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("Could not inspect selected image.")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Could not decode selected image.")
        val scale = minOf(1f, 1600f / maxOf(decoded.width, decoded.height).toFloat())
        val resized = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true) else decoded
        val file = File.createTempFile("aethra_image_", ".jpg", context.cacheDir)
        FileOutputStream(file).use { output ->
            check(resized.compress(Bitmap.CompressFormat.JPEG, 88, output)) { "Could not encode selected image." }
        }
        if (resized !== decoded) resized.recycle()
        decoded.recycle()
        return file
    }

    private fun copyToCache(context: Context, uri: Uri, extension: String): File {
        val output = File.createTempFile("aethra_input_", ".$extension", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(output).use { destination -> input.copyTo(destination) }
        } ?: error("Could not open selected media file.")
        check(output.length() > 0L) { "Selected media file is empty." }
        return output
    }

    private fun extractVideoFrames(context: Context, uri: Uri): List<File> {
        val retriever = MediaMetadataRetriever()
        val output = mutableListOf<File>()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frameCount = minOf(MAX_VIDEO_FRAMES, maxOf(1, (durationMs / 3000L).toInt() + 1))
            for (index in 0 until frameCount) {
                val fraction = if (frameCount == 1) 0.5 else index.toDouble() / (frameCount - 1)
                val timeUs = (durationMs * 1000.0 * fraction).toLong()
                val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                val file = File.createTempFile("aethra_video_frame_", ".jpg", context.cacheDir)
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
                bitmap.recycle()
                output += file
            }
        } finally {
            retriever.release()
        }
        return output
    }

    private fun renderPdfPages(context: Context, uri: Uri): List<File> {
        val descriptor: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Could not open this PDF.")
        val output = mutableListOf<File>()
        descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                val pageCount = minOf(renderer.pageCount, MAX_PDF_PAGES)
                for (index in 0 until pageCount) {
                    renderer.openPage(index).use { page ->
                        val scale = minOf(1.0, 1400.0 / maxOf(page.width, page.height))
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val file = File.createTempFile("aethra_pdf_page_", ".jpg", context.cacheDir)
                        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                        bitmap.recycle()
                        output += file
                    }
                }
            }
        }
        return output
    }

    private fun extensionFor(mime: String): String = when (mime.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/mpeg" -> "mp3"
        "audio/mp4", "audio/aac" -> "m4a"
        "audio/ogg" -> "ogg"
        else -> "bin"
    }
}