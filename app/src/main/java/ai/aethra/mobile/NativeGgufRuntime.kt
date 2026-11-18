package ai.aethra.mobile

/**
 * JNI facade for the pinned llama.cpp + libmtmd runtime.
 *
 * This is a separate GGUF runtime; .litertlm files must continue to use LiteRtModelSession.
 * The native adapter remains hidden from Run until the exact model/projector combination
 * passes physical-device validation.
 */
object NativeGgufRuntime {
    init {
        System.loadLibrary("aethra_llama_jni")
    }

    external fun nativeLoadModel(modelPath: String, projectorPath: String?): Boolean
    external fun nativeGenerate(prompt: String, mediaPaths: Array<String>, maxTokens: Int = 256): String
    external fun nativeCancel()
    external fun nativeUnload()
    external fun nativeCapabilities(): String
    external fun nativeSystemInfo(): String
}
