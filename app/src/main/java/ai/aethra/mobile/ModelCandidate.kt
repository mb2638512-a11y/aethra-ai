package ai.aethra.mobile

/**
 * Catalogue metadata only. A candidate is not installed or runnable until verified
 * against an official artifact and tested on a real Android device.
 */
data class ModelCandidate(
    val id: String,
    val displayName: String,
    val purpose: String,
    val status: ModelStatus = ModelStatus.NOT_INTEGRATED
)

enum class ModelStatus { NOT_INTEGRATED, VERIFIED, INSTALLED, LOADED, ERROR }

object ModelCatalogue {
    val candidates = listOf(
        ModelCandidate("lfm3-omni-2b", "LFM3-Omni-2B", "Multimodal model evaluation"),
        ModelCandidate("gemma-4-e2b", "Gemma 4 E2B", "Lightweight assistant evaluation"),
        ModelCandidate("minicpm-v-4-6", "MiniCPM-V 4.6", "Vision and document evaluation")
    )
}
