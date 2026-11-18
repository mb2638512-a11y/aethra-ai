package ai.aethra.mobile

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
fun ModelLibraryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val downloader = remember { ModelDownloadManager(context) }
    val activeDownloads = remember { mutableStateMapOf<String, List<Long>>() }
    val snapshots = remember { mutableStateMapOf<Long, DownloadSnapshot>() }
    var showSettings by remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(Unit) {
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
                ModelCatalogue.candidates.forEach { model ->
                    val assets = ModelAssets.byModel[model.id].orEmpty()
                    val ids = activeDownloads[model.id].orEmpty()
                    val modelSnapshots = ids.mapNotNull { snapshots[it] }
                    val done = modelSnapshots.count { it.status == DownloadManager.STATUS_SUCCESSFUL }
                    val total = assets.size
                    val allDone = total > 0 && done == total
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
                            Text("Runtime: ${model.runtime}", style = MaterialTheme.typography.bodySmall)
                            Text("Package: ${model.approximateDownload}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "RAM guidance: ${model.minimumRamGb?.let { "at least ${it} GB" } ?: "not independently specified"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(model.notes, style = MaterialTheme.typography.bodySmall)
                            if (ids.isNotEmpty()) {
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                Text(
                                    if (allDone) "Assets downloaded. Runtime loading and integrity checks still required."
                                    else "${done}/${total} files completed",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                modelSnapshots.filter { it.status == DownloadManager.STATUS_FAILED }.forEach {
                                    Text("Download failed (reason ${it.reason}).", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    enabled = assets.isNotEmpty() && ids.isEmpty(),
                                    onClick = {
                                        runCatching {
                                            val device = ModelExecutionPolicy.inspect(context)
                                            check(device.freeStorageGb >= 3.0) { "At least 3 GB free storage is required for this download." }
                                            downloader.enqueueModel(model.id)
                                        }.onSuccess { activeDownloads[model.id] = it }
                                    }
                                ) {
                                    Text(
                                        when {
                                            allDone -> "Downloaded"
                                            assets.isEmpty() -> "Download integration pending"
                                            ids.isEmpty() -> "Download"
                                            else -> "Queued"
                                        }
                                    )
                                }
                                OutlinedButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(model.officialModelUrl)))
                                }) { Text("Official source") }
                                if (ids.isNotEmpty() && !allDone) {
                                    TextButton(onClick = {
                                        downloader.cancel(*ids.toLongArray())
                                        activeDownloads.remove(model.id)
                                    }) { Text("Cancel") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )

    if (showSettings) {
        ModelSettingsDialog(onDismiss = { showSettings = false })
    }
}
