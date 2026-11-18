package ai.aethra.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    val device = remember { ModelExecutionPolicy.inspect(context) }
    val effective = minOf(selected.intValue, device.safeConcurrentLimit, 1)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Model & Performance Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
