package dev.penlab.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.penlab.app.search.HandwritingIndex
import dev.penlab.app.ui.components.PenLabPopoverSurface
import dev.penlab.app.ui.theme.LocalPenLabColors
import dev.penlab.app.ui.theme.PenLabColors
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.PenLabLayout
import dev.penlab.app.ui.theme.PenLabSpacing
import dev.penlab.app.ui.theme.flatCardSurface
import dev.penlab.app.ui.theme.PenLabShapes
import dev.penlab.app.ui.theme.PenLabTextStyles
import dev.penlab.app.ui.theme.floatingChromeSurface

/**
 * Handwriting search: a box, what the reader is doing, and the lines that matched.
 * Tapping a result jumps there (the panel closes; [SearchStepper] then steps through the rest).
 */
@Composable
fun SearchPanel(
  query: String,
  onQueryChange: (String) -> Unit,
  hits: List<HandwritingIndex.Hit>,
  status: HandwritingIndex.Status,
  paged: Boolean,
  onOpen: (index: Int) -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
) {
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { focus.requestFocus() }
  PenLabPopoverSurface(
    isPalm = isPalm,
    modifier = modifier.width(320.dp),
    verticalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
  ) {
    Text("Search handwriting", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    OutlinedTextField(
      value = query,
      onValueChange = onQueryChange,
      singleLine = true,
      placeholder = { Text("A word you wrote…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions = KeyboardActions(onSearch = { if (hits.isNotEmpty()) onOpen(0) }),
      colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        cursorColor = MaterialTheme.colorScheme.primary,
      ),
      modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    statusText(status)?.let { Text(it, color = PenLabColors.iconDisabled, style = MaterialTheme.typography.bodySmall) }
    if (query.isNotBlank()) {
      Text(
        when (hits.size) {
          0 -> "No matches"
          1 -> "1 match"
          else -> "${hits.size} matches"
        },
        color = PenLabColors.icon,
        style = MaterialTheme.typography.labelLarge,
      )
    }
    LazyColumn(Modifier.heightIn(max = 360.dp)) {
      itemsIndexed(hits) { index, hit ->
        Row(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(index) }.heightIn(min = PenLabSpacing.touchTarget).padding(horizontal = PenLabSpacing.sm, vertical = 10.dp)
            .semantics { contentDescription = (if (paged) "Page ${hit.line.page + 1}: " else "") + hit.line.text },
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (paged) {
            Text("p. ${hit.line.page + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = PenLabTextStyles.tabularMedium, modifier = Modifier.width(44.dp))
          }
          Text(highlighted(hit), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

/** After jumping to a result: "2 of 7" with previous / next, and close. */
@Composable
fun SearchStepper(index: Int, count: Int, onStep: (Int) -> Unit, onClose: () -> Unit, isPalm: () -> Boolean, modifier: Modifier = Modifier) {
  Row(
    modifier
      .floatingChromeSurface(
        shape = PenLabShapes.pill,
        background = PenLabComponentTokens.chromePillBg,
        elevation = 8.dp,
        subtleLightHairline = true,
        borderColor = PenLabComponentTokens.chromePillBorder,
        isDark = LocalPenLabColors.current.isDark,
      )
      .ignorePalm(isPalm)
      .padding(horizontal = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    StepperIconButton(PenLabIcons.Back, "Previous match", enabled = index > 0) { onStep(index - 1) }
    Text("${index + 1} of $count", color = MaterialTheme.colorScheme.onSurface, style = PenLabTextStyles.tabularLabel, modifier = Modifier.padding(horizontal = PenLabSpacing.sm))
    StepperIconButton(PenLabIcons.ChevronRight, "Next match", enabled = index < count - 1) { onStep(index + 1) }
    StepperIconButton(PenLabIcons.Close, "Close search", enabled = true, onClick = onClose)
  }
}

@Composable
private fun StepperIconButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
  androidx.compose.foundation.layout.Box(
    modifier = Modifier
      .size(PenLabSpacing.touchTarget)
      .clip(PenLabShapes.pill)
      .clickable(enabled = enabled, onClick = onClick)
      .semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (enabled) MaterialTheme.colorScheme.onSurface else PenLabComponentTokens.toolIconDisabled,
      modifier = Modifier.size(PenLabLayout.iconCompact),
    )
  }
}

/** The line above the results: what was found, in titles and lines of handwriting. */
internal fun searchSummary(query: String, titles: Int, lines: Int, notebooksWithLines: Int): String {
  val parts = buildList {
    if (titles > 0) add(if (titles == 1) "1 notebook title" else "$titles notebook titles")
    if (lines > 0) add((if (lines == 1) "1 line" else "$lines lines") + " of handwriting in $notebooksWithLines notebook" + if (notebooksWithLines == 1) "" else "s")
  }
  return if (parts.isEmpty()) "No matches for “$query”" else parts.joinToString(" · ")
}

private fun statusText(status: HandwritingIndex.Status): String? = when (status) {
  HandwritingIndex.Status.Ready -> null
  HandwritingIndex.Status.Downloading -> "Downloading the handwriting reader (once, about 20 MB)…"
  is HandwritingIndex.Status.Reading -> "Reading your handwriting… ${status.done} of ${status.total} lines"
  is HandwritingIndex.Status.Failed -> status.message
}

/** The line's text with the matched words in bold (a match found only in a lesser guess shows plain). */
private fun highlighted(hit: HandwritingIndex.Hit) = buildAnnotatedString {
  val text = hit.line.text.ifEmpty { "(unreadable line)" }
  val match = hit.match
  if (match == null || match.last >= text.length) {
    append(text)
    return@buildAnnotatedString
  }
  append(text.substring(0, match.first))
  withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = PenLabColors.searchMatch)) { append(text.substring(match.first, match.last + 1)) }
  append(text.substring(match.last + 1))
}

/**
 * The library's search box: a 48dp pill that searches every notebook as you type. While the
 * handwriting of older notebooks is still being read ([reading] = notebooks read to notebooks
 * to read) a small spinner and the count sit at the end, so the user knows more matches may come.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PenLabSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  reading: Pair<Int, Int>? = null,
) {
  // Only a touch may focus this field. When a notebook closes and the library comes back, Android
  // hands focus to the first focusable thing on the screen, which is this field: the cursor
  // blinked in it and the keyboard could pop up. So the field can't take focus at all until it is
  // touched (and forgets the touch once focus leaves). Clearing focus after it arrived instead
  // made Android hand it straight back: an endless loop and a StackOverflowError (26 Nov).
  var touched by remember { mutableStateOf(false) }
  // BACK only hides the keyboard; the field would keep focus and its cursor. So once the keyboard
  // that this focus brought up goes away again, let go of focus. This runs in an effect after the
  // keyboard change, never inside onFocusChanged (see above).
  var focused by remember { mutableStateOf(false) }
  var keyboardSeen by remember { mutableStateOf(false) }
  val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
  val imeVisible = WindowInsets.isImeVisible
  LaunchedEffect(imeVisible, focused) {
    if (imeVisible) keyboardSeen = true
    else if (focused && keyboardSeen) {
      keyboardSeen = false
      focusManager.clearFocus()
    }
    if (!focused) keyboardSeen = false
  }
  androidx.compose.foundation.text.BasicTextField(
    value = query,
    onValueChange = onQueryChange,
    singleLine = true,
    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    modifier = modifier
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.any { it.pressed }) touched = true
          }
        }
      }
      .focusProperties { canFocus = touched }
      .onFocusChanged {
        focused = it.isFocused
        if (!it.isFocused && !it.hasFocus) touched = false
      }
      .semantics { contentDescription = "Search all notebooks" },
    decorationBox = { field ->
      Row(
        Modifier
          .fillMaxWidth()
          .heightIn(min = PenLabSpacing.touchTarget)
          .flatCardSurface(
            shape = PenLabShapes.pill,
            background = PenLabComponentTokens.cardBg,
            borderColor = PenLabComponentTokens.cardBorder,
          )
          .padding(start = PenLabSpacing.lg, end = PenLabSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          SearchIcon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(end = PenLabSpacing.sm).size(PenLabLayout.iconMedium),
        )
        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
          if (query.isEmpty()) Text("Search handwriting", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
          field()
        }
        if (reading != null) {
          // Indexing: spinner plus "3/12" (tabular so it doesn't jiggle as it counts).
          androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(PenLabLayout.iconSmall).semantics { contentDescription = "Reading handwriting" },
            strokeWidth = PenLabLayout.ringSelected,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            "${minOf(reading.first + 1, reading.second)}/${reading.second}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = PenLabTextStyles.tabularSmall,
            modifier = Modifier.padding(horizontal = PenLabSpacing.sm),
          )
        }
        if (query.isNotEmpty()) {
          androidx.compose.foundation.layout.Box(
            modifier = Modifier
              .size(PenLabSpacing.touchTarget)
              .clip(PenLabShapes.pill)
              .clickable(role = androidx.compose.ui.semantics.Role.Button) { onQueryChange("") }
              .semantics { contentDescription = "Clear search" },
            contentAlignment = Alignment.Center,
          ) {
            Icon(PenLabIcons.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(PenLabLayout.iconMedium))
          }
        }
      }
    },
  )
}

/**
 * Matches from every notebook: first the notebooks whose title matches ([titleHits], tap to open
 * the notebook), then the lines of handwriting, grouped under the notebook's name (tap one to
 * open it there).
 */
@Composable
fun LibrarySearchResults(
  query: String,
  titleHits: List<dev.penlab.app.notes.NotebookInfo>,
  results: List<dev.penlab.app.search.LibrarySearch.Result>,
  reading: Pair<Int, Int>?,
  onOpen: (dev.penlab.app.search.LibrarySearch.Result) -> Unit,
  onOpenNotebook: (dev.penlab.app.notes.NotebookInfo) -> Unit,
) {
  val groups = remember(results) { results.groupBy { it.notebook.id }.values.toList() }
  LazyColumn(Modifier.fillMaxWidth().padding(horizontal = PenLabLayout.libraryGutter)) {
    item {
      val count = searchSummary(query, titleHits.size, results.size, groups.size)
      Column(Modifier.padding(vertical = PenLabSpacing.sm)) {
        Text(count, color = PenLabColors.icon, style = MaterialTheme.typography.titleSmall)
        if (reading != null) {
          Text(
            "Still reading handwriting: notebook ${reading.first + 1} of ${reading.second}. More matches may appear.",
            color = PenLabColors.iconDisabled,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }
    if (titleHits.isNotEmpty()) {
      if (showTitlesHeading(titleHits.size, results.size)) {
        item(key = "titles-heading") {
          Text(
            "Notebook titles",
            color = PenLabColors.iconDisabled,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = PenLabSpacing.md, bottom = PenLabSpacing.xs),
          )
        }
      }
      items(titleHits, key = { "title-" + it.id }) { notebook ->
        Row(
          Modifier.fillMaxWidth().heightIn(min = PenLabSpacing.touchTarget).clip(PenLabShapes.small).clickable(role = Role.Button) { onOpenNotebook(notebook) }
            .padding(horizontal = PenLabSpacing.sm, vertical = PenLabSpacing.sm),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(PenLabIcons.Paper, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = PenLabSpacing.md).size(PenLabLayout.iconMedium))
          Text(notebook.title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
          if (notebook.folder.isNotEmpty()) {
            Text(
              notebook.folder.replace("/", " › "),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.labelMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(start = PenLabSpacing.md),
            )
          }
        }
      }
    }
    for (group in groups) {
      val notebook = group.first().notebook
      item(key = "nb-" + notebook.id) {
        Text(
          (if (notebook.folder.isNotEmpty()) notebook.folder.replace("/", " › ") + " › " else "") + notebook.title,
          color = PenLabColors.iconDisabled,
          style = MaterialTheme.typography.labelLarge,
          modifier = Modifier.padding(top = PenLabSpacing.lg, bottom = PenLabSpacing.xs),
        )
      }
      itemsIndexed(group, key = { i, r -> notebook.id + "/" + i + "/" + r.hit.line.signature }) { _, result ->
        val paged = result.notebook.kind == dev.penlab.app.notes.NotebookKind.A4
        Row(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(result) }.heightIn(min = PenLabSpacing.touchTarget).padding(horizontal = PenLabSpacing.sm, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(if (paged) "p. ${result.hit.line.page + 1}" else "canvas", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
          Text(highlighted(result.hit), color = PenLabColors.icon, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}
