package ai.aethra.mobile

enum class ModelModality(val label: String) {
    TEXT("Text"),
    IMAGE("Image"),
    AUDIO("Audio"),
    VIDEO("Video"),
    DOCUMENT("Documents"),
}

data class ModelCapabilities(
    val inputs: Set<ModelModality>,
    val outputs: Set<ModelModality>,
    val notes: String,
)

data class RuntimeAdapterInfo(
    val runtimeName: String,
    val supportedInputs: Set<ModelModality>,
    val supportedOutputs: Set<ModelModality>,
    val integratedOnAndroid: Boolean,
)

/**
 * Central capability and adapter registry.
 *
 * Catalogue capability is descriptive metadata. A model is runnable only when an actual Android adapter is registered. Physical-device validation is tracked separately; do not infer runnability from weights being downloaded.
 */
object ModelRuntimeRegistry {
    private val text = setOf(ModelModality.TEXT)
    private val capabilities = mapOf(
        "gemma-4-e2b" to ModelCapabilities(setOf(ModelModality.TEXT, ModelModality.IMAGE, ModelModality.AUDIO, ModelModality.VIDEO, ModelModality.DOCUMENT), text, "LiteRT-LM vision/audio inputs; video is sampled into image frames and PDFs are rendered into page images."),
        "lfm3-omni-2b" to ModelCapabilities(
            setOf(ModelModality.TEXT, ModelModality.IMAGE, ModelModality.AUDIO, ModelModality.VIDEO),
            setOf(ModelModality.TEXT, ModelModality.AUDIO),
            "Multimodal understanding and speech output; not an image/video generator."
        ),
        "minicpm-v-4-6" to ModelCapabilities(
            setOf(ModelModality.TEXT, ModelModality.IMAGE, ModelModality.VIDEO, ModelModality.DOCUMENT),
            text,
            "Vision and document understanding; not an image/video generator."
        ),
        "qwen3-omni" to ModelCapabilities(
            setOf(ModelModality.TEXT, ModelModality.IMAGE, ModelModality.AUDIO, ModelModality.VIDEO),
            setOf(ModelModality.TEXT, ModelModality.AUDIO),
            "Omni understanding and speech generation."
        ),
        "qwen3-vl-8b" to ModelCapabilities(
            setOf(ModelModality.TEXT, ModelModality.IMAGE, ModelModality.VIDEO, ModelModality.DOCUMENT),
            text,
            "Vision-language understanding."
        ),
        "qwen-image-2-1" to ModelCapabilities(text, setOf(ModelModality.IMAGE), "Text-to-image generation."),
        "qwen-image-edit-20b" to ModelCapabilities(setOf(ModelModality.TEXT, ModelModality.IMAGE), setOf(ModelModality.IMAGE), "Instruction-based image editing."),
        "flux-kontext-dev" to ModelCapabilities(setOf(ModelModality.TEXT, ModelModality.IMAGE), setOf(ModelModality.IMAGE), "Reference-guided image editing."),
        "sd35-large" to ModelCapabilities(text, setOf(ModelModality.IMAGE), "Text-to-image generation."),
        "wan22-ti2v-5b" to ModelCapabilities(setOf(ModelModality.TEXT, ModelModality.IMAGE), setOf(ModelModality.VIDEO), "Text/image-to-video generation."),
        "hunyuan-video-13b" to ModelCapabilities(text, setOf(ModelModality.VIDEO), "Text-to-video generation."),
        "ltx-25" to ModelCapabilities(text, setOf(ModelModality.VIDEO, ModelModality.AUDIO), "Video generation with audio workflow."),
        "cogvideox-5b" to ModelCapabilities(setOf(ModelModality.TEXT, ModelModality.IMAGE), setOf(ModelModality.VIDEO), "Text/image-to-video generation."),
        "wan21-1-3b" to ModelCapabilities(setOf(ModelModality.TEXT, ModelModality.IMAGE), setOf(ModelModality.VIDEO), "Text/image-to-video generation."),
        "ace-step-1-5" to ModelCapabilities(text, setOf(ModelModality.AUDIO), "Music and audio generation."),
        "neutts" to ModelCapabilities(text, setOf(ModelModality.AUDIO), "Text-to-speech; voice cloning requires permission."),
        "whisper-large-v3-turbo" to ModelCapabilities(setOf(ModelModality.AUDIO), text, "Speech recognition and transcription."),
        "kokoro-82m" to ModelCapabilities(text, setOf(ModelModality.AUDIO), "Text-to-speech."),
        "stable-audio-open" to ModelCapabilities(text, setOf(ModelModality.AUDIO), "Sound and audio generation."),
        "sam3" to ModelCapabilities(setOf(ModelModality.IMAGE, ModelModality.VIDEO), setOf(ModelModality.IMAGE, ModelModality.VIDEO), "Segmentation and tracking component."),
        "openjutsu" to ModelCapabilities(setOf(ModelModality.TEXT, ModelModality.IMAGE, ModelModality.VIDEO), setOf(ModelModality.VIDEO), "Multi-model character-editing workflow."),
        "facefusion" to ModelCapabilities(setOf(ModelModality.IMAGE, ModelModality.VIDEO), setOf(ModelModality.IMAGE, ModelModality.VIDEO), "Consent-based face editing workflow."),
        "animatediff" to ModelCapabilities(setOf(ModelModality.TEXT, ModelModality.IMAGE), setOf(ModelModality.VIDEO), "Image animation workflow."),
        "viggle-image-swap" to ModelCapabilities(setOf(ModelModality.TEXT, ModelModality.IMAGE), setOf(ModelModality.IMAGE), "Reference-guided image editing workflow.")
    )

    private val adapters = mapOf(
        "gemma-4-e2b" to RuntimeAdapterInfo("LiteRT-LM Android", setOf(ModelModality.TEXT, ModelModality.IMAGE, ModelModality.AUDIO, ModelModality.VIDEO, ModelModality.DOCUMENT), text, true)
    )

    fun capabilitiesFor(modelId: String): ModelCapabilities =
        capabilities[modelId] ?: ModelCapabilities(text, text, "Capabilities have not been verified.")

    fun adapterFor(modelId: String): RuntimeAdapterInfo? = adapters[modelId]

    fun isRunnableOnAndroid(modelId: String): Boolean =
        adapters[modelId]?.validatedOnAndroid == true

    fun runnableModelIds(): Set<String> =
        adapters.filterValues { it.integratedOnAndroid }.keys
}
