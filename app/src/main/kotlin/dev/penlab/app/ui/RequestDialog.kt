package dev.penlab.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.unit.dp
import dev.penlab.app.notes.Request
import dev.penlab.app.ui.theme.PenLabColors

/**
 * The quick form: bug or feature, one line about it, Save. Underneath, the running list (open
 * items first), so it is clear what is already noted.
 */
@Composable
fun RequestDialog(where: String, existing: List<Request>, onDismiss: () -> Unit, onSave: (kind: String, text: String) -> Unit) {
  var kind by remember { mutableStateOf("Bug") }
  var text by remember { mutableStateOf("") }
  val open = existing.count { !it.done }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = PenLabColors.toolbar,
    title = { Text("Note a bug or idea", color = PenLabColors.icon) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          for (option in listOf("Bug", "Feature")) {
            SmallChoice(option, kind == option) { kind = option }
          }
        }
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          label = { Text(if (kind == "Bug") "What went wrong?" else "What should it do?") },
          minLines = 3,
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedTextColor = PenLabColors.icon,
              unfocusedTextColor = PenLabColors.icon,
              focusedBorderColor = PenLabColors.icon,
              unfocusedBorderColor = PenLabColors.hairline,
              focusedLabelColor = PenLabColors.icon,
              unfocusedLabelColor = PenLabColors.iconDisabled,
              cursorColor = PenLabColors.icon,
            ),
          modifier = Modifier.fillMaxWidth(),
        )
        Text("Saved with: $where", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.bodySmall)
        if (existing.isNotEmpty()) {
          Text("Your list · $open open", color = PenLabColors.icon, style = MaterialTheme.typography.titleSmall)
          LazyColumn(Modifier.heightIn(max = 220.dp)) {
            items(existing.sortedBy { it.done }) { item ->
              Text(
                "${item.kind} · ${item.text}",
                color = if (item.done) PenLabColors.iconDisabled else PenLabColors.icon,
                style = MaterialTheme.typography.bodyMedium.copy(textDecoration = if (item.done) TextDecoration.LineThrough else null),
                modifier = Modifier.padding(vertical = 4.dp),
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onSave(kind, text) }, enabled = text.isNotBlank()) {
        Text("Save", color = if (text.isNotBlank()) PenLabColors.icon else PenLabColors.iconDisabled)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = PenLabColors.iconDisabled) } },
  )
}

@Composable
private fun SmallChoice(label: String, selected: Boolean, onClick: () -> Unit) {
  val shape = RoundedCornerShape(10.dp)
  Text(
    label,
    color = if (selected) PenLabColors.onAccent else PenLabColors.icon,
    style = MaterialTheme.typography.labelLarge,
    modifier =
      Modifier.clip(shape)
        .background(if (selected) PenLabColors.accent else PenLabColors.chip, shape)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .heightIn(min = 40.dp)
        .padding(horizontal = 14.dp, vertical = 10.dp),
  )
}

/** Typing or editing a text box. Saving empty text removes the box. */
@Composable
fun TextBoxDialog(initial: String, isNew: Boolean, onDismiss: () -> Unit, onDone: (String) -> Unit) {
  var text by remember { mutableStateOf(initial) }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = PenLabColors.toolbar,
    title = { Text(if (isNew) "Add text" else "Edit text", color = PenLabColors.icon) },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        minLines = 3,
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedTextColor = PenLabColors.icon,
            unfocusedTextColor = PenLabColors.icon,
            focusedBorderColor = PenLabColors.icon,
            unfocusedBorderColor = PenLabColors.hairline,
            cursorColor = PenLabColors.icon,
          ),
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = { TextButton(onClick = { onDone(text) }) { Text(if (isNew) "Add" else "Save", color = PenLabColors.icon) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = PenLabColors.iconDisabled) } },
  )
}

/** A one-line name, e.g. renaming a layer. */
@Composable
fun RenameDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
  var text by remember { mutableStateOf(initial) }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = PenLabColors.toolbar,
    title = { Text(title, color = PenLabColors.icon) },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedTextColor = PenLabColors.icon,
            unfocusedTextColor = PenLabColors.icon,
            focusedBorderColor = PenLabColors.icon,
            unfocusedBorderColor = PenLabColors.hairline,
            cursorColor = PenLabColors.icon,
          ),
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = { TextButton(onClick = { onDone(text) }) { Text("Save", color = PenLabColors.icon) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = PenLabColors.iconDisabled) } },
  )
}
