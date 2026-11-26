package dev.penlab.app.ui

import android.content.Context
import androidx.core.content.edit
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.penlab.app.ink.PageStyle
import dev.penlab.app.notes.NotebookKind
import dev.penlab.app.ui.components.PenLabDialog
import dev.penlab.app.ui.components.PenLabSegmentedControl
import dev.penlab.app.ui.theme.Accent
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.PenLabLayout
import dev.penlab.app.ui.theme.PenLabShapes
import dev.penlab.app.ui.theme.PenLabSpacing

private const val PAPER_COLUMNS = 3

/**
 * Title, format (A4 portrait / landscape / endless) and a grid of every paper drawn in the active
 * Light or Dark paper tint. The notebook goes into the folder that is open; each folder
 * remembers the format and paper last chosen in it (or inherits its parent's), see
 * [paperDefaultsFor] and the `folder_paper` preferences, whose stored format is unchanged.
 */
@Composable
internal fun NewNotebookDialog(
  folder: String,
  onDismiss: () -> Unit,
  onCreate: (String, NotebookKind, PageStyle, Boolean) -> Unit,
) {
  val prefs = LocalContext.current.getSharedPreferences("folder_paper", Context.MODE_PRIVATE)
  val remembered = remember(folder) { paperDefaultsFor(folder) { prefs.getString(it, null) } }
  var title by remember { mutableStateOf("") }
  var format by remember { mutableStateOf(NotebookFormat.of(remembered.kind, remembered.landscape)) }
  var style by remember { mutableStateOf(remembered.style) }
  val where = folder.substringAfterLast("/")
  // Choosing a format or paper puts the keyboard away, so the whole grid is on screen to look at.
  val focus = LocalFocusManager.current

  PenLabDialog(
    title = if (folder.isEmpty()) "New notebook" else "New notebook in $where",
    onDismiss = onDismiss,
    primaryLabel = "Create",
    onPrimaryClick = {
      prefs.edit { putString(paperPrefKey(folder), encodePaperDefaults(PaperDefaults(style, format.kind, format.landscape))) }
      onCreate(title, format.kind, style, format.landscape)
    },
    maxWidth = PenLabLayout.wideDialogMaxWidth,
  ) {
    PenLabTextField(title, { title = it }, "Title")
    DialogLabel("Format")
    PenLabSegmentedControl(
      options = NotebookFormat.entries,
      selected = format,
      onSelect = { format = it; focus.clearFocus() },
      label = { it.label },
    )
    DialogLabel(if (folder.isEmpty()) "Paper" else "Paper (remembered for $where)")
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(PenLabSpacing.sm)) {
      for (row in PageStyle.entries.chunked(PAPER_COLUMNS)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.sm)) {
          for (option in row) PaperTile(option, selected = option == style, onClick = { style = option; focus.clearFocus() }, modifier = Modifier.weight(1f))
          // Keep the last row's tiles the same width as the rest.
          repeat(PAPER_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
        }
      }
    }
  }
}

@Composable
private fun DialogLabel(text: String) {
  Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = PenLabSpacing.xs))
}

/** One paper: a miniature sheet with its ruling, the name underneath, a ring and tint when chosen. */
@Composable
private fun PaperTile(style: PageStyle, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  Column(
    modifier
      .clip(PenLabShapes.medium)
      .background(if (selected) PenLabComponentTokens.chipSelectedBg else Color.Transparent)
      .then(if (selected) Modifier.border(PenLabLayout.ringSelected, scheme.primary, PenLabShapes.medium) else Modifier)
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
      .padding(PenLabSpacing.sm)
      .semantics { contentDescription = "${style.label} paper" },
    verticalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
  ) {
    PaperPreview(style, Accent.isDarkPage, modifier = Modifier.fillMaxWidth().aspectRatio(PenLabLayout.paperPreviewAspect))
    Text(
      style.label,
      color = if (selected) PenLabComponentTokens.chipSelectedContent else scheme.onSurface,
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
