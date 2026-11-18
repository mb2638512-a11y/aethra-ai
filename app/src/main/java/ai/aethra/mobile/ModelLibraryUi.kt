package ai.aethra.mobile

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun ModelLibraryDialog(onDismiss: () -> Unit, onRunModel: (ModelCandidate) -> Unit = {}, onDeleteModel: (ModelCandidate) -> Unit = {}) {
    val context = LocalContext.current
    val downloader = remember { ModelDownloadManager(context) }
    val activeDownloads = remember { mutableStateMapOf<String, List<Long>>() }
    val snapshots = remember { mutableStateMapOf<Long, DownloadSnapshot>() }
    var showSettings by remember { androidx.compose.runtime.mutableStateOf(false) }
    var searchQuery by remember { androidx.compose.runtime.mutableStateOf("") }
    var selectedCategory by remember { androidx.compose.runtime.mutableStateOf("All") }
    var categoryMenu by remember { androidx.compose.runtime.mutableStateOf(false) }
    var pendingDelete by remember { androidx.compose.runtime.mutableStateOf<ModelCandidate?>(null) }
    val categories = listOf("All") + ModelCatalogue.candidates.map { it.category }.distinct().sorted()

    LaunchedEffect(Unit) {
        ModelAssets.byModel.keys.forEach { modelId ->
            val savedIds = ModelDownloadStore.load(context, modelId)
            if (savedIds.isNotEmpty()) {
                val recovered = savedIds.filter { id ->
                    val snapshot = downloader.query(id)
                    if (snapshot != null) snapshots[id] = snapshot
                    snapshot != null
                }
                if (recovered.isNotEmpty()) activeDownloads[modelId] = recovered
                else ModelDownloadStore.clear(context, modelId)
            }
        }
        while (true) {
            activeDownloads.values.flatten().forEach { id ->
                downloader.query(id)?.let { snapshots[id] = it }
            }
            delay(900)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AETHRA Model Library") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "23 multimedia models and workflows are catalogued. Only entries with a verified download manifest can be downloaded here; only models with an integrated runtime and complete local files can appear in the Run selector.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = { showSettings = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Device & performance settings")
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search models and workflows") }
                )
                Box {
                    TextButton(onClick = { categoryMenu = true }) { Text("Category: " + selectedCategory) }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        categories.forEach { category ->
                            DropdownMenuItem(text = { Text(category) }, onClick = { selectedCategory = category; categoryMenu = false })
                        }
                    }
                }
                ModelCatalogue.candidates.filter { model ->
                    val matchesQuery = searchQuery.isBlank() ||
                        model.displayName.contains(searchQuery, ignoreCase = true) ||
                        model.purpose.contains(searchQuery, ignoreCase = true) ||
                        model.category.contains(searchQuery, ignoreCase = true)
                    val matchesCategory = selectedCategory == "All" || model.category == selectedCategory
                    matchesQuery && matchesCategory
                }.forEach { model ->
                    val assets = ModelAssets.byModel[model.id].orEmpty()
                    val ids = activeDownloads[model.id].orEmpty()
                    val modelSnapshots = ids.mapNotNull { snapshots[it] }
                    val done = modelSnapshots.count { it.status == DownloadManager.STATUS_SUCCESSFUL }
                    val total = assets.size
                    val filesPresent = assets.isNotEmpty() && assets.all { asset ->
                        val file = java.io.File(downloader.modelDirectory(), asset.fileName)
                        file.isFile && file.length() > 0L
                    }
                    val hasFailed = modelSnapshots.any { it.status == DownloadManager.STATUS_FAILED }
                    val allDone = ModelDownloadReadiness.isReady(filesPresent, ids.size, modelSnapshots)
                    val hasActive = modelSnapshots.any {
                        it.status == DownloadManager.STATUS_PENDING ||
                            it.status == DownloadManager.STATUS_RUNNING ||
                            it.status == DownloadManager.STATUS_PAUSED
                    }
                    val filesPresentCount = assets.count { asset ->
                        java.io.File(downloader.modelDirectory(), asset.fileName).let { it.isFile && it.length() > 0L }
                    }
                    val progress = if (modelSnapshots.isNotEmpty()) {
                        modelSnapshots.map { it.progressPercent }.average().toFloat() / 100f
                    } else 0f

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(model.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(model.purpose, style = MaterialTheme.typography.bodySmall)
                            val capabilities = ModelRuntimeRegistry.capabilitiesFor(model.id)
                            Text("Input: " + capabilities.inputs.joinToString(" • ") { it.label }, style = MaterialTheme.typography.bodySmall)
                            Text("Output: " + capabilities.outputs.joinToString(" • ") { it.label }, style = MaterialTheme.typography.bodySmall)
                            Text("Runtime: ${model.runtime}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                if (ModelRuntimeRegistry.isRunnableOnAndroid(model.id)) "Android runtime integrated and enabled" else "Catalogue/download entry • Android runtime pending",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ModelRuntimeRegistry.isRunnableOnAndroid(model.id)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                            Text(capabilities.notes, style = MaterialTheme.typography.bodySmall)
                            Text("Package: ${model.approximateDownload}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "RAM guidance: ${model.minimumRamGb?.let { "at least ${it} GB" } ?: "not independently specified"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(model.notes, style = MaterialTheme.typography.bodySmall)
                            if (ids.isNotEmpty()) {
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                Text(
                                    if (allDone) "All ${total} model files present. Integrity checks apply at runtime load."
                                    else "${filesPresentCount}/${total} files present • ${done}/${total} downloads completed",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                modelSnapshots.filter { it.status == DownloadManager.STATUS_FAILED }.forEach {
                                    Text("Download failed (reason ${it.reason}).", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (ModelRuntimeRegistry.isRunnableOnAndroid(model.id) && allDone) {
                                    OutlinedButton(onClick = { onRunModel(model); onDismiss() }) { Text("Run") }
                                }
                                Button(
                                    enabled = assets.isNotEmpty() && !allDone && !hasActive,
                                    onClick = {
                                        runCatching {
                                            val device = ModelExecutionPolicy.inspect(context)
                                            check(device.freeStorageGb >= 3.0) { "At least 3 GB free storage is required for this download." }
                                            val failedIds = modelSnapshots.filter { it.status == DownloadManager.STATUS_FAILED }.map { it.id }
                                            val failedFiles = failedIds.mapNotNull { ModelDownloadStore.assetName(context, it) }.toSet()
                                            if (failedIds.isNotEmpty()) downloader.cancel(*failedIds.toLongArray())
                                            downloader.enqueueMissingModel(model.id, failedFiles)
                                        }.onSuccess { newIds ->
                                            if (newIds.isEmpty()) {
                                                activeDownloads.remove(model.id)
                                                ModelDownloadStore.clear(context, model.id)
                                            } else {
                                                activeDownloads[model.id] = newIds
                                                ModelDownloadStore.save(context, model.id, newIds)
                                            }
                                        }
                                    }
                                ) {
                                    Text(
                                        when {
                                            allDone -> "Downloaded"
                                            assets.isEmpty() -> "Download integration pending"
                                            hasActive -> "Downloading…"
                                            hasFailed -> "Retry failed files"
                                            ids.isNotEmpty() -> "Resume missing files"
                                            else -> "Download"
                                        }
                                    )
                                }
                                OutlinedButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(model.officialModelUrl)))
                                }) { Text("Official source") }
                                if (ids.isNotEmpty() && !allDone && hasActive) {
                                    TextButton(onClick = {
                                        val activeIds = modelSnapshots.filter {
                                            it.status == DownloadManager.STATUS_PENDING ||
                                                it.status == DownloadManager.STATUS_RUNNING ||
                                                it.status == DownloadManager.STATUS_PAUSED
                                        }.map { it.id }
                                        downloader.cancelAndDeletePartial(activeIds)
                                        activeDownloads.remove(model.id)
                                        ModelDownloadStore.clear(context, model.id)
                                    }) { Text("Cancel") }
                                }
                                if (filesPresentCount > 0 || ids.isNotEmpty()) {
                                    TextButton(onClick = { pendingDelete = model }) { Text("Delete files") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )

    pendingDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete downloaded model?") },
            text = { Text("Remove all local files and cancel any downloads for ${model.displayName}? You can download them again later.") },
            confirmButton = {
                TextButton(onClick = {
                    val savedIds = ModelDownloadStore.load(context, model.id)
                    onDeleteModel(model)
                    activeDownloads.remove(model.id)
                    savedIds.forEach { snapshots.remove(it) }
                    ModelDownloadStore.clear(context, model.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Keep files") }
            }
        )
    }

    if (showSettings) {
        ModelSettingsDialog(onDismiss = { showSettings = false })
    }
}
