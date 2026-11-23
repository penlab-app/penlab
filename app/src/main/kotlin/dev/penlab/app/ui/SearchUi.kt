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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import dev.penlab.app.ui.theme.PenLabColors
import dev.penlab.app.ui.theme.softShadow

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
  val shape = RoundedCornerShape(16.dp)
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { focus.requestFocus() }
  Column(
    modifier.softShadow(shape).clip(shape).background(PenLabColors.toolbar).ignorePalm(isPalm).padding(12.dp).width(320.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Search handwriting", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.labelMedium)
    OutlinedTextField(
      value = query,
      onValueChange = onQueryChange,
      singleLine = true,
      placeholder = { Text("A word you wrote…", color = PenLabColors.iconDisabled) },
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions = KeyboardActions(onSearch = { if (hits.isNotEmpty()) onOpen(0) }),
      colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = PenLabColors.icon,
        unfocusedTextColor = PenLabColors.icon,
        focusedBorderColor = PenLabColors.icon,
        unfocusedBorderColor = PenLabColors.hairline,
        cursorColor = PenLabColors.icon,
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
          Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(index) }.padding(horizontal = 8.dp, vertical = 10.dp)
            .semantics { contentDescription = (if (paged) "Page ${hit.line.page + 1}: " else "") + hit.line.text },
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (paged) {
            Text("p. ${hit.line.page + 1}", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp))
          }
          Text(highlighted(hit), color = PenLabColors.icon, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

/** After jumping to a result: "2 of 7" with previous / next, and close. */
@Composable
fun SearchStepper(index: Int, count: Int, onStep: (Int) -> Unit, onClose: () -> Unit, isPalm: () -> Boolean, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(20.dp)
  Row(
    modifier.softShadow(shape).clip(shape).background(PenLabColors.toolbar).ignorePalm(isPalm).padding(horizontal = 6.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    StepperButton("‹", "Previous match", enabled = index > 0) { onStep(index - 1) }
    Text("${index + 1} of $count", color = PenLabColors.icon, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
    StepperButton("›", "Next match", enabled = index < count - 1) { onStep(index + 1) }
    StepperButton("✕", "Close search", enabled = true, onClick = onClose)
  }
}

@Composable
private fun StepperButton(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
  Text(
    glyph,
    color = if (enabled) PenLabColors.icon else PenLabColors.iconDisabled,
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick)
      .semantics { contentDescription = label }.padding(horizontal = 12.dp, vertical = 6.dp),
  )
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

/** The library's search box: a pill that searches every notebook as you type. */
@Composable
fun LibrarySearchField(query: String, onQueryChange: (String) -> Unit) {
  val shape = RoundedCornerShape(20.dp)
  androidx.compose.foundation.text.BasicTextField(
    value = query,
    onValueChange = onQueryChange,
    singleLine = true,
    textStyle = MaterialTheme.typography.bodyMedium.copy(color = PenLabColors.icon),
    cursorBrush = androidx.compose.ui.graphics.SolidColor(PenLabColors.icon),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    modifier = Modifier.width(260.dp).semantics { contentDescription = "Search all notebooks" },
    decorationBox = { field ->
      Row(
        Modifier.softShadow(shape).clip(shape).background(PenLabColors.toolbar).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        androidx.compose.material3.Icon(SearchIcon, contentDescription = null, tint = PenLabColors.iconDisabled, modifier = Modifier.padding(end = 8.dp).size(18.dp))
        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
          if (query.isEmpty()) Text("Search handwriting", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.bodyMedium)
          field()
        }
        if (query.isNotEmpty()) {
          Text(
            "✕",
            color = PenLabColors.iconDisabled,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onQueryChange("") }
              .semantics { contentDescription = "Clear search" }.padding(horizontal = 4.dp),
          )
        }
      }
    },
  )
}

/** Matches from every notebook, grouped under the notebook's name; tap one to open it there. */
@Composable
fun LibrarySearchResults(
  query: String,
  results: List<dev.penlab.app.search.LibrarySearch.Result>,
  reading: Pair<Int, Int>?,
  onOpen: (dev.penlab.app.search.LibrarySearch.Result) -> Unit,
) {
  val groups = remember(results) { results.groupBy { it.notebook.id }.values.toList() }
  LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
    item {
      val count = when (results.size) {
        0 -> "No matches for “$query”"
        1 -> "1 match"
        else -> "${results.size} matches in ${groups.size} notebook" + if (groups.size == 1) "" else "s"
      }
      Column(Modifier.padding(vertical = 8.dp)) {
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
    for (group in groups) {
      val notebook = group.first().notebook
      item(key = "nb-" + notebook.id) {
        Text(
          (if (notebook.folder.isNotEmpty()) notebook.folder.replace("/", " › ") + " › " else "") + notebook.title,
          color = PenLabColors.iconDisabled,
          style = MaterialTheme.typography.labelLarge,
          modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
      }
      itemsIndexed(group, key = { i, r -> notebook.id + "/" + i + "/" + r.hit.line.signature }) { _, result ->
        val paged = result.notebook.kind == dev.penlab.app.notes.NotebookKind.A4
        Row(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(result) }.padding(horizontal = 8.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(if (paged) "p. ${result.hit.line.page + 1}" else "canvas", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
          Text(highlighted(result.hit), color = PenLabColors.icon, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}
