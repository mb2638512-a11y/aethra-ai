package ai.aethra.mobile

import android.content.Context

object MediaProcessingPreferences {
    private const val PREFS = "aethra_media_processing"
    fun maxVideoFrames(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("video_frames", 4).coerceIn(1, 4)
    fun maxPdfPages(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("pdf_pages", 4).coerceIn(1, 4)
    fun maxImageDimension(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("image_dimension", 1600).coerceIn(768, 2048)
    fun save(context: Context, videoFrames: Int, pdfPages: Int, imageDimension: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("video_frames", videoFrames.coerceIn(1, 4))
            .putInt("pdf_pages", pdfPages.coerceIn(1, 4))
            .putInt("image_dimension", imageDimension.coerceIn(768, 2048))
            .apply()
    }
}