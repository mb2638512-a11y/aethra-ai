package ai.aethra.mobile

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class ModelAsset(
    val id: String,
    val modelId: String,
    val fileName: String,
    val url: String,
    val expectedSha256: String? = null,
)

data class DownloadSnapshot(
    val id: Long,
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val localUri: String?,
    val reason: Int,
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
}

object ModelAssets {
    private const val HF = "https://huggingface.co"
    val byModel = mapOf(
        "gemma-4-e2b" to listOf(
            ModelAsset(
                id = "gemma4-e2b",
                modelId = "gemma-4-e2b",
                fileName = "gemma-4-E2B-it.litertlm",
                url = "$HF/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
                expectedSha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            )
        ),
        "lfm3-omni-2b" to listOf(
            ModelAsset(
                id = "lfm3-q4km",
                modelId = "lfm3-omni-2b",
                fileName = "LFM3-Omni-2B-Q4_K_M.gguf",
                url = "$HF/LiquidAI/LFM3-Omni-2B-GGUF/resolve/main/LFM3-Omni-2B-Q4_K_M.gguf",
            ),
            ModelAsset(
                id = "lfm3-mmproj-q4km",
                modelId = "lfm3-omni-2b",
                fileName = "mmproj-LFM3-Omni-2B-Q4_K_M.gguf",
                url = "$HF/LiquidAI/LFM3-Omni-2B-GGUF/resolve/main/mmproj-LFM3-Omni-2B-Q4_K_M.gguf",
            )
        ),
        "minicpm-v-4-6" to listOf(
            ModelAsset(
                id = "minicpm-v46-q4km",
                modelId = "minicpm-v-4-6",
                fileName = "MiniCPM-V-4_6-Q4_K_M.gguf",
                url = "$HF/openbmb/MiniCPM-V-4.6-gguf/resolve/main/MiniCPM-V-4_6-Q4_K_M.gguf",
            ),
            ModelAsset(
                id = "minicpm-v46-mmproj",
                modelId = "minicpm-v-4-6",
                fileName = "mmproj-model-f16.gguf",
                url = "$HF/openbmb/MiniCPM-V-4.6-gguf/resolve/main/mmproj-model-f16.gguf",
            )
        )
    )
}

/**
 * Uses Android's system DownloadManager so large model downloads can continue
 * outside the app process. Model files stay in the app-specific external folder.
 */
object ModelDownloadReadiness {
    /** Files on disk are not sufficient while DownloadManager may still be writing a partial artifact. */
    fun isReady(filesPresent: Boolean, savedDownloadCount: Int, snapshots: List<DownloadSnapshot>): Boolean {
        if (!filesPresent) return false
        if (savedDownloadCount == 0) return true
        return snapshots.size == savedDownloadCount &&
            snapshots.all { it.status == DownloadManager.STATUS_SUCCESSFUL }
    }
}

object ModelDownloadStore {
    private const val PREFS = "aethra_model_downloads"
    fun save(context: Context, modelId: String, ids: List<Long>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(modelId, ids.joinToString(","))
            .apply()
    }
    fun load(context: Context, modelId: String): List<Long> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(modelId, null)
            ?.split(",")?.mapNotNull { it.toLongOrNull() }.orEmpty()
    fun clear(context: Context, modelId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(modelId).apply()
    }
}

class ModelDownloadManager(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(DownloadManager::class.java)

    fun enqueueModel(modelId: String): List<Long> = enqueueMissingModel(modelId)

    /** Enqueue only assets that are not already present, allowing failed/partial downloads to retry safely. */
    fun enqueueMissingModel(modelId: String): List<Long> {
        val assets = ModelAssets.byModel[modelId]
            ?: throw IllegalArgumentException("Unknown model: $modelId")
        val destination = modelDirectory()
        val missing = assets.filter { asset ->
            val file = File(destination, asset.fileName)
            !file.isFile || file.length() <= 0L
        }
        return missing.map { asset ->
            val target = File(destination, asset.fileName)
            target.delete()
            val request = DownloadManager.Request(Uri.parse(asset.url))
                .setTitle("AETHRA AI • ${asset.fileName}")
                .setDescription("Downloading model asset")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(target))
            manager.enqueue(request)
        }
    }

    fun query(downloadId: Long): DownloadSnapshot? {
        val cursor: Cursor = manager.query(DownloadManager.Query().setFilterById(downloadId)) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            fun column(name: String, fallback: Long = 0L): Long {
                val index = it.getColumnIndex(name)
                return if (index >= 0) it.getLong(index) else fallback
            }
            val uriIndex = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val reasonIndex = it.getColumnIndex(DownloadManager.COLUMN_REASON)
            return DownloadSnapshot(
                id = downloadId,
                status = column(DownloadManager.COLUMN_STATUS).toInt(),
                downloadedBytes = column(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                totalBytes = column(DownloadManager.COLUMN_TOTAL_SIZE_BYTES, -1L),
                localUri = if (uriIndex >= 0) it.getString(uriIndex) else null,
                reason = if (reasonIndex >= 0) it.getInt(reasonIndex) else 0,
            )
        }
    }

    fun cancel(vararg downloadIds: Long) {
        if (downloadIds.isNotEmpty()) manager.remove(*downloadIds)
    }

    fun modelDirectory(): File =
        File(appContext.getExternalFilesDir(null), "models").apply { mkdirs() }

    fun deleteModelAssets(modelId: String): Int {
        val assets = ModelAssets.byModel[modelId].orEmpty()
        val ids = ModelDownloadStore.load(appContext, modelId)
        cancel(*ids.toLongArray())
        var deleted = 0
        assets.forEach { asset ->
            val file = File(modelDirectory(), asset.fileName)
            if (file.exists() && file.delete()) deleted++
        }
        ModelDownloadStore.clear(appContext, modelId)
        return deleted
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
