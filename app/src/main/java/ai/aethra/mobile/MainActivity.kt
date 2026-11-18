package ai.aethra.mobile

import android.os.Bundle
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AethraChatScreen() {
    var prompt by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("Select model") }
    var modelMenu by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<ChatMessage>() }

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
                            ModelCatalogue.candidates.forEach { candidate ->
                                DropdownMenuItem(
                                    text = { Text(candidate.displayName) },
                                    onClick = { selectedModel = candidate.displayName; modelMenu = false }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { messages.clear() }) {
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
                Row(
                    Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(24.dp))
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    IconButton(onClick = { infoDialog = true }) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach file", tint = Muted)
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
                    IconButton(onClick = { infoDialog = true }) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice", tint = Muted)
                    }
                    IconButton(
                        onClick = {
                            if (prompt.isNotBlank()) {
                                messages.add(ChatMessage(prompt.trim(), true))
                                prompt = ""
                            }
                        },
                        modifier = Modifier.background(Violet, CircleShape).size(42.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Send", tint = Night)
                    }
                }
                Text(
                    "Local-first workspace • Inference engine not connected",
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

    if (infoDialog) {
        AlertDialog(
            onDismissRequest = { infoDialog = false },
            title = { Text("AETHRA AI") },
            text = { Text("The native Android UI foundation is being built. Model execution, downloads, file import and voice are not connected yet. The app will not pretend to generate AI answers until a real inference runtime is integrated.") },
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
