package ai.aethra.mobile

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * First real on-device inference adapter.
 *
 * This adapter currently targets LiteRT-LM compatible .litertlm bundles
 * (initially Gemma 4 E2B). GGUF models use a different native runtime and
 * are deliberately not routed through this class.
 */
class LiteRtModelSession(
    private val modelPath: String,
    private val systemInstruction: String = "You are AETHRA, a helpful, honest and precise AI assistant. If you are unsure, say so clearly.",
) : Closeable {
    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null

    suspend fun open() = withContext(Dispatchers.IO) {
        check(engine == null) { "Model session is already open." }
        val created = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
            )
        )
        try {
            created.initialize()
            engine = created
            conversation = created.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(systemInstruction),
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7),
                )
            )
        } catch (error: Throwable) {
            if (created.isInitialized()) created.close()
            throw error
        }
    }

    fun generate(prompt: String): Flow<String> = flow {
        require(prompt.isNotBlank()) { "Prompt cannot be empty." }
        val activeConversation = conversation
            ?: error("Model is not loaded. Open the session first.")
        activeConversation.sendMessageAsync(prompt).collect { message ->
            emit(message.toString())
        }
    }.flowOn(Dispatchers.IO)

    override fun close() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
    }
}
