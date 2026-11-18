package ai.aethra.mobile

/**
 * Verified model metadata. "Catalogue" means available for selection/download;
 * it does not mean that the model has been installed or tested on this device.
 */
data class ModelCandidate(
    val id: String,
    val displayName: String,
    val purpose: String,
    val status: ModelStatus = ModelStatus.CATALOG_ONLY,
    val runtime: String,
    val artifactFormat: String,
    val approximateDownload: String,
    val minimumRamGb: Int? = null,
    val officialModelUrl: String,
    val notes: String,
)

enum class ModelStatus {
    CATALOG_ONLY,
    DOWNLOADING,
    DOWNLOADED,
    LOADING,
    LOADED,
    ERROR
}

object ModelCatalogue {
    val candidates = listOf(
        ModelCandidate(
            id = "lfm3-omni-2b",
            displayName = "LFM3-Omni-2B",
            purpose = "Text, image, audio and video understanding; speech output",
            runtime = "llama.cpp Android (GGUF + multimodal projector)",
            artifactFormat = "GGUF Q4_K_M + mmproj Q4_K_M",
            approximateDownload = "1.1 GB + 292 MB",
            officialModelUrl = "https://huggingface.co/LiquidAI/LFM3-Omni-2B-GGUF",
            notes = "Official GGUF card documents llama.cpp support. Android integration and modality parity still require device validation.",
        ),
        ModelCandidate(
            id = "gemma-4-e2b",
            displayName = "Gemma 4 E2B",
            purpose = "General-purpose assistant; text and supported multimodal inputs",
            runtime = "LiteRT-LM Android",
            artifactFormat = "litertlm",
            approximateDownload = "About 2.58 GB",
            minimumRamGb = 8,
            officialModelUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
            notes = "First runtime integration target. Published reference device requirement is 8 GB RAM; performance varies by phone.",
        ),
        ModelCandidate(
            id = "minicpm-v-4-6",
            displayName = "MiniCPM-V 4.6",
            purpose = "Image, multi-image, video and document understanding",
            runtime = "llama.cpp Android (GGUF + vision projector)",
            artifactFormat = "GGUF Q4_K_M + mmproj",
            approximateDownload = "About 1.6 GB total",
            minimumRamGb = 6,
            officialModelUrl = "https://huggingface.co/openbmb/MiniCPM-V-4.6-gguf",
            notes = "Official deployment guide lists llama.cpp support and recommends at least 6 GB device RAM for the Q4 build.",
        ),
    )
}
