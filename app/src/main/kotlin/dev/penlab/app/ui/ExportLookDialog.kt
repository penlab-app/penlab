package dev.penlab.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.penlab.app.ui.theme.PenLabColors

/**
 * Asked before exporting a dark note: white paper with dark ink (for printing or handing in), or
 * the dark page as it looks on screen. PDF notebooks are light already and skip this.
 */
@Composable
fun ExportLookDialog(onPick: (dark: Boolean) -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = PenLabColors.toolbar,
    title = { Text("Export as", color = PenLabColors.icon) },
    text = {
      Text(
        "Light: white paper, dark ink, best for printing and handing in.\nDark: as it looks on screen.",
        color = PenLabColors.iconDisabled,
        style = MaterialTheme.typography.bodyMedium,
      )
    },
    confirmButton = { TextButton(onClick = { onPick(false) }) { Text("Light", color = PenLabColors.icon) } },
    dismissButton = { TextButton(onClick = { onPick(true) }) { Text("Dark", color = PenLabColors.icon) } },
  )
}
