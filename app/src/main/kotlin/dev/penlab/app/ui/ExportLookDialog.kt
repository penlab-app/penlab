package dev.penlab.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Asked before exporting a dark note: white paper with dark ink (for printing or handing in), or
 * the dark page as it looks on screen. PDF notebooks are light already and skip this.
 */
@Composable
fun ExportLookDialog(onPick: (dark: Boolean) -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    title = { Text("Export as", color = MaterialTheme.colorScheme.onSurface) },
    text = {
      Text(
        "Light: white paper, dark ink, best for printing and handing in.\nDark: as it looks on screen.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
      )
    },
    confirmButton = { TextButton(onClick = { onPick(false) }) { Text("Light", color = MaterialTheme.colorScheme.primary) } },
    dismissButton = { TextButton(onClick = { onPick(true) }) { Text("Dark", color = MaterialTheme.colorScheme.onSurface) } },
  )
}
