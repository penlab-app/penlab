package dev.penlab.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import dev.penlab.app.notes.Request
import dev.penlab.app.ui.components.PenLabDialog
import dev.penlab.app.ui.components.PenLabSegmentedControl
import dev.penlab.app.ui.theme.PenLabColors
import dev.penlab.app.ui.theme.PenLabSpacing

/** How many of the earlier notes the form lists (the rest is in requests.md). */
private const val LISTED_REQUESTS = 12

/**
 * The quick form: bug or feature, one line about it, Save. Underneath, the running list (open
 * items first), so it is clear what is already noted. Built on [PenLabDialog] like Rename and
 * New folder: pill primary (Save), text secondary (Cancel).
 */
@Composable
fun RequestDialog(where: String, existing: List<Request>, onDismiss: () -> Unit, onSave: (kind: String, text: String) -> Unit) {
  var kind by remember { mutableStateOf("Bug") }
  var text by remember { mutableStateOf("") }
  val open = existing.count { !it.done }
  PenLabDialog(
    title = "Note a bug or idea",
    onDismiss = onDismiss,
    primaryLabel = "Save",
    onPrimaryClick = { onSave(kind, text) },
    primaryEnabled = text.isNotBlank(),
  ) {
    PenLabSegmentedControl(options = listOf("Bug", "Feature"), selected = kind, onSelect = { kind = it }, label = { it })
    PenLabTextField(text, { text = it }, if (kind == "Bug") "What went wrong?" else "What should it do?", minLines = 3)
    Text("Saved with: $where", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.bodySmall)
    if (existing.isNotEmpty()) {
      Text("Your list · $open open", color = PenLabColors.icon, style = MaterialTheme.typography.titleSmall)
      // Plain rows: the dialog already scrolls, and a scrolling list inside it cannot size itself.
      val shown = existing.sortedBy { it.done }.take(LISTED_REQUESTS)
      Column(verticalArrangement = Arrangement.spacedBy(PenLabSpacing.xs)) {
        for (item in shown) {
          Text(
            "${item.kind} · ${item.text}",
            color = if (item.done) PenLabColors.iconDisabled else PenLabColors.icon,
            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = if (item.done) TextDecoration.LineThrough else null),
          )
        }
        if (existing.size > shown.size) {
          Text("and ${existing.size - shown.size} more", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.bodySmall)
        }
      }
    }
  }
}

/** Typing or editing a text box. Saving empty text removes the box. */
@Composable
fun TextBoxDialog(initial: String, isNew: Boolean, onDismiss: () -> Unit, onDone: (String) -> Unit) {
  var text by remember { mutableStateOf(initial) }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    title = { Text(if (isNew) "Add text" else "Edit text", color = MaterialTheme.colorScheme.onSurface) },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        minLines = 3,
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
          ),
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = { TextButton(onClick = { onDone(text) }) { Text(if (isNew) "Add" else "Save", color = MaterialTheme.colorScheme.primary) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
  )
}

/** A one-line name, e.g. renaming a layer. */
@Composable
fun RenameDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
  var text by remember { mutableStateOf(initial) }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
          ),
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = { TextButton(onClick = { onDone(text) }) { Text("Save", color = MaterialTheme.colorScheme.primary) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
  )
}
