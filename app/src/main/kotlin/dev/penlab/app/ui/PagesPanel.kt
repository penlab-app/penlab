package dev.penlab.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.penlab.app.ui.theme.PenLabColors
import dev.penlab.app.ui.theme.softShadow

/** "Page 3 / 10" next to the back button in an A4 notebook. Tap for the page grid. */
@Composable
fun PageCounter(page: () -> Int, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(12.dp)
  val current = page() + 1
  Row(
    modifier
      .softShadow(shape).clip(shape)
      .background(PenLabColors.toolbar)
      .clickable(onClick = onClick)
      .heightIn(min = 36.dp)
      .padding(horizontal = 12.dp)
      .semantics { contentDescription = "Page $current of $count. Double tap for all pages." },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(PagesIcon, contentDescription = null, tint = PenLabColors.icon, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text("$current / $count", color = PenLabColors.icon, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
  }
}

/**
 * Every page as a thumbnail. Tap to go there, tap the ribbon to bookmark, long-press for
 * insert before / after and delete. "Bookmarked" shows only bookmarked pages.
 *
 * [version] changes whenever the notebook's content changes, so thumbnails are redrawn.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagesPanel(
  count: Int,
  currentPage: Int,
  bookmarks: Set<Int>,
  version: Int,
  thumbnail: (page: Int, widthPx: Int) -> Bitmap?,
  onJump: (Int) -> Unit,
  onToggleBookmark: (Int) -> Unit,
  onInsert: (at: Int) -> Unit,
  onDelete: (Int) -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
) {
  var onlyBookmarked by remember { mutableStateOf(false) }
  val pages = remember(count, bookmarks, onlyBookmarked) {
    (0 until count).filter { !onlyBookmarked || it in bookmarks }
  }
  val shape = RoundedCornerShape(16.dp)
  val thumbWidthPx = with(LocalDensity.current) { 112.dp.roundToPx() }
  Column(
    modifier
      .softShadow(shape).clip(shape)
      .background(PenLabColors.toolbar)
      .ignorePalm(isPalm)
      .padding(12.dp)
      .width(560.dp)
      .heightIn(max = 620.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Pages", color = PenLabColors.icon, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
      TextButton(onClick = { onlyBookmarked = !onlyBookmarked }) {
        Text(
          if (onlyBookmarked) "All pages" else "Bookmarked",
          color = PenLabColors.icon,
          style = MaterialTheme.typography.labelLarge,
        )
      }
      TextButton(onClick = { onInsert(count) }) {
        Text("Add page", color = PenLabColors.icon, style = MaterialTheme.typography.labelLarge)
      }
    }
    if (pages.isEmpty()) {
      Text(
        "No bookmarked pages. Tap the ribbon on a page to bookmark it.",
        color = PenLabColors.iconDisabled,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 24.dp),
      )
    }
    LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = 112.dp),
      contentPadding = PaddingValues(vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      items(pages, key = { it }, contentType = { "page" }) { page ->
        PageTile(
          page = page,
          current = page == currentPage,
          bookmarked = page in bookmarks,
          image = remember(page, version) { thumbnail(page, thumbWidthPx) },
          onJump = { onJump(page) },
          onToggleBookmark = { onToggleBookmark(page) },
          onInsertBefore = { onInsert(page) },
          onInsertAfter = { onInsert(page + 1) },
          onDelete = if (count > 1) ({ onDelete(page) }) else null,
        )
      }
    }
    Text("Long-press a page to insert a page before or after it, or to delete it.", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.bodySmall)
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageTile(
  page: Int,
  current: Boolean,
  bookmarked: Boolean,
  image: Bitmap?,
  onJump: () -> Unit,
  onToggleBookmark: () -> Unit,
  onInsertBefore: () -> Unit,
  onInsertAfter: () -> Unit,
  onDelete: (() -> Unit)?,
) {
  var menu by remember { mutableStateOf(false) }
  val shape = RoundedCornerShape(6.dp)
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box {
      Box(
        Modifier.fillMaxWidth()
          .aspectRatio(210f / 297f)
          .clip(shape)
          .border(if (current) 2.dp else 1.dp, if (current) PenLabColors.icon else PenLabColors.hairline, shape)
          .combinedClickable(onClick = onJump, onLongClick = { menu = true })
          .semantics {
            contentDescription = "Page ${page + 1}. Long press for options."
            if (current) stateDescription = "Current page"
          }
      ) {
        image?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(210f / 297f)) }
      }
      IconButton(
        onClick = onToggleBookmark,
        modifier =
          Modifier.align(Alignment.TopEnd).size(40.dp).semantics {
            contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark page ${page + 1}"
          },
      ) {
        Icon(
          if (bookmarked) BookmarkIcon else BookmarkOutlineIcon,
          contentDescription = null,
          tint = if (bookmarked) BookmarkColor else PenLabColors.iconDisabled,
          modifier = Modifier.size(20.dp),
        )
      }
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(text = { Text("Insert page before") }, onClick = { menu = false; onInsertBefore() })
        DropdownMenuItem(text = { Text("Insert page after") }, onClick = { menu = false; onInsertAfter() })
        if (onDelete != null) DropdownMenuItem(text = { Text("Delete page") }, onClick = { menu = false; onDelete() })
      }
    }
    Text(
      "${page + 1}",
      color = if (current) PenLabColors.icon else PenLabColors.iconDisabled,
      style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
      modifier = Modifier.padding(top = 4.dp),
    )
  }
}

/** The ribbon colour of a bookmarked page: a muted amber that reads on the graphite UI. */
private val BookmarkColor = androidx.compose.ui.graphics.Color(0xFFE8B04A)
