package ai.aethra.mobile

import android.os.Bundle
import android.net.Uri
import android.provider.OpenableColumns
import android.content.Intent
import android.speech.RecognizerIntent
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF090B16)
private val Panel = Color(0xFF14182B)
private val Violet = Color(0xFF9B83FF)
private val Muted = Color(0xFF9AA2BD)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AethraTheme { AethraChatScreen() } }
    }
}

@Composable
private fun AethraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet,
            secondary = Color(0xFF65D8D0),
            background = Night,
            surface = Panel,
            onBackground = Color(0xFFF4F2FF),
            onSurface = Color(0xFFF4F2FF)
        ),
        content = content
    )
}

private data class ChatMessage(val text: String, val fromUser: Boolean)
private data class MediaAttachment(val uri: Uri, val name: String, val mimeType: String?)

private fun describeAttachment(context: Context, uri: Uri): MediaAttachment {
    val mime = context.contentResolver.getType(uri)
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Selected file"
    return MediaAttachment(uri, name, mime)
}

/** Only expose models that are both integrated with a real chat runtime and fully present locally. */
private fun availableChatModels(context: Context): List<ModelCandidate> {
    val integratedModelIds = setOf("gemma-4-e2b")
    val modelDirectory = File(context.getExternalFilesDir(null), "models")
    return ModelCatalogue.candidates.filter { candidate ->
        if (candidate.id !in integratedModelIds) return@filter false
        val assets = ModelAssets.byModel[candidate.id].orEmpty()
        assets.isNotEmpty() && assets.all { asset ->
            File(modelDirectory, asset.fileName).let { it.isFile && it.length() > 0L }
        }
    }
}

private fun modelFileFor(context: Context, candidate: ModelCandidate): File? {
    val primaryAsset = ModelAssets.byModel[candidate.id]?.firstOrNull() ?: return null
    return File(File(context.getExternalFilesDir(null), "models"), primaryAsset.fileName)
}

private fun modelMonogram(modelId: String): String = when (modelId) {
    "gemma-4-e2b" -> "G"
    "lfm3-omni-2b" -> "L"
    "minicpm-v-4-6" -> "M"
    else -> "AI"
}

private object ChatHistoryStore {
    private const val PREFS = "aethra_local_chat"
    private const val KEY_MESSAGES = "messages"

    fun load(context: Context): List<ChatMessage> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MESSAGES, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(ChatMessage(item.optString("text"), item.optBoolean("fromUser", true)))
            }
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, messages: List<ChatMessage>) {
        runCatching {
            val array = JSONArray()
            messages.forEach { message ->
                array.put(JSONObject().put("text", message.text).put("fromUser", message.fromUser))
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_MESSAGES, array.toString()).apply()
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_MESSAGES).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AethraChatScreen() {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    var selectedAttachment by remember { mutableStateOf<MediaAttachment?>(null) }
    var speechNotice by remember { mutableStateOf<String?>(null) }
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            selectedAttachment = describeAttachment(context, uri)
            speechNotice = null
        }
    }
    val speechInput = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == android.app.Activity.RESULT_OK && !spoken.isNullOrBlank()) {
            prompt = listOf(prompt, spoken).filter { it.isNotBlank() }.joinToString(" ")
            speechNotice = "Voice input added to your prompt."
        } else {
            speechNotice = "Voice input was cancelled or unavailable."
        }
    }
    var selectedModel by remember { mutableStateOf(availableChatModels(context).firstOrNull()?.displayName ?: "No model installed") }
    var modelMenu by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showModelLibrary by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sessionState = remember { mutableStateOf<LiteRtModelSession?>(null) }
    val jobState = remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            jobState.value?.cancel()
            sessionState.value?.close()
            sessionState.value = null
        }
    }
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply { addAll(ChatHistoryStore.load(context)) }
    }

    LaunchedEffect(messages.size) {
        ChatHistoryStore.save(context, messages.toList())
    }

    // Start the default model only if it is already installed and passes device safety checks.
    LaunchedEffect(Unit) {
        val defaultModel = availableChatModels(context).firstOrNull()
        if (defaultModel != null && sessionState.value == null) {
            runCatching {
                ModelExecutionPolicy.assertCanLoad(context, defaultModel.minimumRamGb ?: 6, 0.25)
                val modelFile = modelFileFor(context, defaultModel) ?: error("No model artifact manifest is available.")
                LiteRtModelSession(modelFile.absolutePath).also { it.open() }
            }.onSuccess { sessionState.value = it }
        }
    }

    Scaffold(
        containerColor = Night,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AETHRA", fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Text("INTELLIGENCE, WITHOUT BOUNDARIES.", fontSize = 9.sp, color = Muted, letterSpacing = 1.1.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { infoDialog = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { modelMenu = true }) {
                            Text(selectedModel, color = Violet, fontSize = 11.sp)
                        }
                        DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                            val selectableModels = availableChatModels(context)
                            if (selectableModels.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No downloaded, supported models") },
                                    enabled = false,
                                    onClick = {}
                                )
                            }
                            selectableModels.forEach { candidate ->
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Surface(color = Violet.copy(alpha = 0.18f), shape = CircleShape) {
                                            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                                                Text(modelMonogram(candidate.id), color = Violet, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    },
                                    text = { Text(candidate.displayName) },
                                    onClick = {
                                        modelMenu = false
                                        jobState.value?.cancel()
                                        selectedModel = "No model running"
                                        scope.launch {
                                            sessionState.value?.close()
                                            sessionState.value = null
                                            try {
                                                ModelExecutionPolicy.assertCanLoad(context, candidate.minimumRamGb ?: 6, 0.25)
                                                val modelFile = modelFileFor(context, candidate) ?: error("No model artifact manifest is available.")
                                                val expectedHash = ModelAssets.byModel[candidate.id]?.firstOrNull()?.expectedSha256
                                                if (expectedHash != null) {
                                                    val actualHash = withContext(Dispatchers.IO) { ModelDownloadManager(context).sha256(modelFile) }
                                                    check(actualHash.equals(expectedHash, ignoreCase = true)) { "Model checksum mismatch." }
                                                }
                                                val session = LiteRtModelSession(modelFile.absolutePath)
                                                session.open()
                                                sessionState.value = session
                                                selectedModel = candidate.displayName
                                            } catch (error: Throwable) {
                                                messages.add(ChatMessage("Model not started: ${error.message ?: "device check failed"}", false))
                                            }
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Stop running model") },
                                enabled = sessionState.value != null,
                                onClick = {
                                    sessionState.value?.close()
                                    sessionState.value = null
                                    modelMenu = false
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Model Library • downloads") },
                                onClick = { showModelLibrary = true; modelMenu = false }
                            )
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Performance settings", tint = Color.White)
                    }
                    IconButton(onClick = { messages.clear(); ChatHistoryStore.clear(context) }) {
                        Icon(Icons.Default.Add, contentDescription = "New chat", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Night)
            )
        },
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().background(Night).padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                selectedAttachment?.let { attachment ->
                    Surface(
                        color = Panel,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(attachment.name, color = Color.White, fontSize = 12.sp, maxLines = 1)
                                Text(attachment.mimeType ?: "Unknown file type", color = Muted, fontSize = 10.sp)
                            }
                            TextButton(onClick = { selectedAttachment = null }) { Text("Remove") }
                        }
                    }
                }
                speechNotice?.let { notice ->
                    Text(notice, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 5.dp))
                }
                Row(
                    Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(24.dp))
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    IconButton(onClick = { attachmentPicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach image, audio, video or document", tint = Muted)
                    }
                    TextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message AETHRA...", color = Muted) },
                        maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Violet
                        )
                    )
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to AETHRA")
                        }
                        runCatching { speechInput.launch(intent) }
                            .onFailure { speechNotice = "Speech recognition is not available on this device." }
                    }) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice input", tint = Muted)
                    }
                    IconButton(
                        onClick = {
                            if (generating) {
                                jobState.value?.cancel()
                            } else if (selectedAttachment != null) {
                                val attachment = selectedAttachment!!
                                messages.add(ChatMessage("Attachment selected: ${attachment.name} (${attachment.mimeType ?: "unknown type"}). The current Gemma adapter is text-only, so this file is not yet passed into model inference. Multimodal runtime integration is still in progress.", false))
                                selectedAttachment = null
                            } else if (prompt.isNotBlank()) {
                                val requestText = prompt.trim()
                                messages.add(ChatMessage(requestText, true))
                                prompt = ""
                                jobState.value = scope.launch {
                                    generating = true
                                    val responseIndex = messages.size
                                    messages.add(ChatMessage("", false))
                                    try {
                                        val candidate = ModelCatalogue.candidates.firstOrNull { it.displayName == selectedModel }
                                        if (candidate?.id != "gemma-4-e2b") {
                                            messages[responseIndex] = ChatMessage(
                                                "This model's Android inference runtime is not connected yet. Gemma 4 E2B is the first integrated runtime target.",
                                                false
                                            )
                                        } else {
                                            val modelFile = modelFileFor(context, candidate) ?: error("No model artifact manifest is available.")
                                            if (!modelFile.exists()) {
                                                messages[responseIndex] = ChatMessage(
                                                    "Gemma 4 E2B is not downloaded yet. Open Model Library and download it first.",
                                                    false
                                                )
                                            } else {
                                                var session = sessionState.value
                                                if (session == null) {
                                                    ModelExecutionPolicy.assertCanLoad(context, candidate.minimumRamGb ?: 6, 0.25)
                                                    val expectedHash = ModelAssets.byModel["gemma-4-e2b"]?.firstOrNull()?.expectedSha256
                                                    if (expectedHash != null) {
                                                        val actualHash = withContext(Dispatchers.IO) {
                                                            ModelDownloadManager(context).sha256(modelFile)
                                                        }
                                                        check(actualHash.equals(expectedHash, ignoreCase = true)) {
                                                            "Gemma model checksum mismatch. Delete the model and download it again."
                                                        }
                                                    }
                                                    session = LiteRtModelSession(modelFile.absolutePath)
                                                    session.open()
                                                    sessionState.value = session
                                                }
                                                session.generate(requestText).collect { chunk ->
                                                    val current = messages[responseIndex]
                                                    messages[responseIndex] = current.copy(text = current.text + chunk)
                                                }
                                            }
                                        }
                                    } catch (error: Throwable) {
                                        if (error is CancellationException) throw error
                                        messages[responseIndex] = ChatMessage(
                                            "Model error: ${error.message ?: "unknown inference failure"}",
                                            false
                                        )
                                    } finally {
                                        generating = false
                                        jobState.value = null
                                    }
                                }
                            }
                        },
                        modifier = Modifier.background(Violet, CircleShape).size(42.dp)
                    ) {
                        Icon(
                            if (generating) Icons.Default.Stop else Icons.Default.ArrowUpward,
                            contentDescription = if (generating) "Stop generation" else "Send",
                            tint = Night
                        )
                    }
                }
                Text(
                    "Local-first • Gemma 4 E2B runs on-device when installed",
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = Muted, fontSize = 10.sp
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f).padding(22.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(92.dp).background(
                                Brush.linearGradient(listOf(Color(0xFF5140A7), Color(0xFF171D43))),
                                CircleShape
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
                        }
                        Spacer(Modifier.height(26.dp))
                        Text("A new kind of intelligence.", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Your private AI workspace. Select a model to explore the roadmap.",
                            color = Muted, fontSize = 13.sp
                        )
                        Spacer(Modifier.height(22.dp))
                        SuggestionChip("Explore models") { modelMenu = true }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(messages) { message ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                color = if (message.fromUser) Color(0xFF29234A) else Panel,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.widthIn(max = 310.dp)
                            ) {
                                Text(message.text, Modifier.padding(14.dp), fontSize = 14.sp, lineHeight = 21.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showModelLibrary) {
        ModelLibraryDialog(onDismiss = { showModelLibrary = false })
    }

    if (showSettings) {
        ModelSettingsDialog(onDismiss = { showSettings = false })
    }

    if (infoDialog) {
        AlertDialog(
            onDismissRequest = { infoDialog = false },
            title = { Text("AETHRA AI") },
            text = { Text("AETHRA uses local model inference when a supported model is installed. Gemma 4 E2B is the first LiteRT-LM target. LFM3 and MiniCPM still need their Android inference runtime. Downloads are verified before Gemma is loaded.") },
            confirmButton = { TextButton(onClick = { infoDialog = false }) { Text("Understood") } },
            containerColor = Panel
        )
    }
}

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(label, Modifier.padding(horizontal = 16.dp, vertical = 11.dp), color = Color(0xFFDCD6FF), fontSize = 12.sp)
    }
}
