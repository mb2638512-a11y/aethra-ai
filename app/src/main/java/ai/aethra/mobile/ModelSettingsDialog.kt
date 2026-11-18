package ai.aethra.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ModelSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val initial = remember { ModelExecutionPolicy.requestedMaxModels(context) }
    val selected = remember { mutableIntStateOf(initial) }
    val videoFrames = remember { mutableIntStateOf(MediaProcessingPreferences.maxVideoFrames(context)) }
    val pdfPages = remember { mutableIntStateOf(MediaProcessingPreferences.maxPdfPages(context)) }
    val imageDimension = remember { mutableIntStateOf(MediaProcessingPreferences.maxImageDimension(context)) }
    val videoAudioSeconds = remember { mutableIntStateOf(MediaProcessingPreferences.maxVideoAudioSeconds(context)) }
    val device = remember { ModelExecutionPolicy.inspect(context) }
    val effective = minOf(selected.intValue, device.safeConcurrentLimit, 1)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Model & Performance Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Device hardware", style = MaterialTheme.typography.titleSmall)
                Text("RAM: %.1f GB total • %.1f GB available".format(device.totalRamGb, device.availableRamGb))
                Text("Free storage: %.1f GB".format(device.freeStorageGb))
                Text("Thermal status: " + (device.thermalStatus?.toString() ?: "Not available"))
                Text("Maximum simultaneous models", style = MaterialTheme.typography.titleSmall)
                Text("${selected.intValue} requested • ${effective} currently allowed")
                Slider(
                    value = selected.intValue.toFloat(),
                    onValueChange = { selected.intValue = it.toInt().coerceIn(1, 4) },
                    valueRange = 1f..4f,
                    steps = 2
                )
                Text(
                    "Default is 1. AETHRA currently has one validated Android model runtime, so the effective limit stays at 1 until additional engines are integrated and stress-tested.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Media processing limits", style = MaterialTheme.typography.titleSmall)
                Text("Video frames per request: ${videoFrames.intValue}")
                Slider(value = videoFrames.intValue.toFloat(), onValueChange = { videoFrames.intValue = it.toInt().coerceIn(1, 4) }, valueRange = 1f..4f, steps = 2)
                Text("Maximum audio duration per request: ${videoAudioSeconds.intValue} seconds")
                Slider(value = videoAudioSeconds.intValue.toFloat(), onValueChange = { videoAudioSeconds.intValue = it.toInt().coerceIn(15, 180) }, valueRange = 15f..180f, steps = 10)
                Text("PDF pages per request: ${pdfPages.intValue}")
                Slider(value = pdfPages.intValue.toFloat(), onValueChange = { pdfPages.intValue = it.toInt().coerceIn(1, 4) }, valueRange = 1f..4f, steps = 2)
                Text("Maximum image dimension: ${imageDimension.intValue}px")
                Slider(value = imageDimension.intValue.toFloat(), onValueChange = { imageDimension.intValue = it.toInt().coerceIn(768, 2048) }, valueRange = 768f..2048f, steps = 3)
                Text("Lower limits reduce preprocessing memory and may reduce visual detail.", style = MaterialTheme.typography.bodySmall)
                if (device.warnings.isNotEmpty()) {
                    Text("Safety notices", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    device.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                Text(
                    "AETHRA checks available RAM, free storage and thermal state before loading. Android can still stop any app under extreme system pressure; no app can guarantee zero hangs on every device.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ModelExecutionPolicy.saveRequestedMaxModels(context, selected.intValue)
                MediaProcessingPreferences.save(context, videoFrames.intValue, pdfPages.intValue, imageDimension.intValue, videoAudioSeconds.intValue)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
