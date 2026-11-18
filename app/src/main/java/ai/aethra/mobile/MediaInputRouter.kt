package ai.aethra.mobile

data class MediaInputCheck(
    val modality: ModelModality?,
    val acceptedByModel: Boolean,
    val acceptedByAndroidRuntime: Boolean,
    val explanation: String,
)

object MediaInputRouter {
    fun modalityForMime(mimeType: String?): ModelModality? = when {
        mimeType == null -> null
        mimeType.startsWith("image/") -> ModelModality.IMAGE
        mimeType.startsWith("audio/") -> ModelModality.AUDIO
        mimeType.startsWith("video/") -> ModelModality.VIDEO
        mimeType == "application/pdf" || mimeType.startsWith("text/") -> ModelModality.DOCUMENT
        else -> null
    }

    fun check(modelId: String, mimeType: String?): MediaInputCheck {
        val modality = modalityForMime(mimeType)
        if (modality == null) {
            return MediaInputCheck(null, false, false, "This file type has no configured media adapter.")
        }
        val modelSupports = modality in ModelRuntimeRegistry.capabilitiesFor(modelId).inputs
        val adapter = ModelRuntimeRegistry.adapterFor(modelId)
        val runtimeSupports = adapter?.integratedOnAndroid == true && modality in adapter.supportedInputs
        val explanation = when {
            !modelSupports -> "The selected model does not advertise $modality input."
            !runtimeSupports -> "The model catalogue lists $modality input, but this Android runtime adapter is not integrated and validated yet."
            else -> "The selected Android runtime accepts $modality input."
        }
        return MediaInputCheck(modality, modelSupports, runtimeSupports, explanation)
    }
}