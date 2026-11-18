package ai.aethra.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * Thin coroutine wrapper over the process-wide llama.cpp/libmtmd JNI session.
 * Native generation streams decoded token pieces through a JNI callback into Kotlin Flow.
 * This session is not exposed by Model Library until device validation passes.
 */
class NativeGgufModelSession(
    private val modelPath: String,
    private val projectorPath: String? = null,
    private val maxTokens: Int = 256,
) : Closeable {
    @Volatile private var opened = false

    suspend fun open() = withContext(Dispatchers.IO) {
        check(!opened) { "GGUF model session is already open." }
        check(File(modelPath).isFile) { "GGUF model file is missing." }
        projectorPath?.let { check(File(it).isFile) { "Multimodal projector file is missing." } }
        // If another session is generating, stop it before the process-wide model is replaced.
        NativeGgufRuntime.nativeCancel()
        check(NativeGgufRuntime.nativeLoadModel(modelPath, projectorPath)) {
            "The native GGUF runtime did not load the selected model."
        }
        opened = true
    }

    fun generate(prompt: String, mediaPaths: List<String> = emptyList()): Flow<String> = callbackFlow {
        check(opened) { "GGUF model is not loaded." }
        val worker = launch(Dispatchers.IO) {
            try {
                NativeGgufRuntime.nativeGenerateStream(
                    prompt,
                    mediaPaths.toTypedArray(),
                    maxTokens.coerceIn(1, 1024),
                    NativeTokenCallback { token -> trySend(token) }
                )
                close()
            } catch (error: Throwable) {
                close(error)
            }
        }
        awaitClose {
            NativeGgufRuntime.nativeCancel()
            worker.cancel()
        }
    }

    fun cancel() {
        if (opened) NativeGgufRuntime.nativeCancel()
    }

    override fun close() {
        if (opened) {
            NativeGgufRuntime.nativeCancel()
            NativeGgufRuntime.nativeUnload()
            opened = false
        }
    }
}
