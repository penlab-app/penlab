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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.penlab.app.ui.components.PenLabPopoverSurface
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.PenLabLayout
import dev.penlab.app.ui.theme.PenLabShapes
import dev.penlab.app.ui.theme.PenLabSpacing
import dev.penlab.app.ui.theme.PenLabTextStyles

/**
 * Every page as a thumbnail. Tap to go there, tap the ribbon to bookmark, long-press for
 * insert before / after and delete. The "Bookmarks only" toggle filters to bookmarked pages.
 *
 * [version] changes whenever the notebook's content changes, so thumbnails are redrawn.
 */
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
  PenLabPopoverSurface(
    isPalm = isPalm,
    modifier = modifier.width(440.dp).heightIn(max = 620.dp),
    verticalArrangement = Arrangement.Top,
  ) {
    PagesPanelContent(
      count = count,
      currentPage = currentPage,
      bookmarks = bookmarks,
      version = version,
      thumbnail = thumbnail,
      onJump = onJump,
      onToggleBookmark = onToggleBookmark,
      onInsert = onInsert,
      onDelete = onDelete,
    )
  }
}

/**
 * Embeddable Pages grid content used by both [PagesPanel] and [PageConfigPopover] (avoids nested
 * popover surfaces).
 */
@Composable
fun PagesPanelContent(
  count: Int,
  currentPage: Int,
  bookmarks: Set<Int>,
  version: Int,
  thumbnail: (page: Int, widthPx: Int) -> Bitmap?,
  onJump: (Int) -> Unit,
  onToggleBookmark: (Int) -> Unit,
  onInsert: (at: Int) -> Unit,
  onDelete: (Int) -> Unit,
  showHeading: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  var onlyBookmarked by remember { mutableStateOf(false) }
  val pages =
    remember(count, bookmarks, onlyBookmarked) {
      (0 until count).filter { !onlyBookmarked || it in bookmarks }
    }
  val thumbWidthPx = with(LocalDensity.current) { 128.dp.roundToPx() }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
  ) {
    // Top action bar: count/heading + Bookmarked filter chip + Add page primary pill.
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
    ) {
      if (showHeading) {
        Text(
          text = "Pages",
          color = scheme.onSurface,
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f),
        )
      } else {
        Text(
          text = if (count == 1) "1 page" else "$count pages",
          color = scheme.onSurfaceVariant,
          style = PenLabTextStyles.tabularMedium,
          modifier = Modifier.weight(1f),
        )
      }

      // Bookmarked filter chip with tonal active state
      val filterBg = if (onlyBookmarked) PenLabComponentTokens.chipSelectedBg else PenLabComponentTokens.chipIdleBg
      val filterFg = if (onlyBookmarked) PenLabComponentTokens.chipSelectedContent else scheme.onSurface
      Row(
        modifier =
          Modifier
            .heightIn(min = PenLabSpacing.touchTarget)
            .clip(PenLabShapes.pill)
            .background(filterBg)
            .then(
              if (onlyBookmarked) Modifier.border(1.5.dp, scheme.primary, PenLabShapes.pill)
              else Modifier
            )
            .clickable(role = Role.Switch) { onlyBookmarked = !onlyBookmarked }
            .padding(horizontal = PenLabSpacing.md)
            .semantics {
              contentDescription = if (onlyBookmarked) "Showing bookmarked pages. Tap for all pages." else "Bookmarks only: show only bookmarked pages"
              selected = onlyBookmarked
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = if (onlyBookmarked) PenLabIcons.BookmarkFilled else PenLabIcons.BookmarkOutline,
          contentDescription = null,
          tint = if (onlyBookmarked) scheme.tertiary else scheme.onSurfaceVariant,
          modifier = Modifier.size(PenLabLayout.iconSmall),
        )
        Text(
          text = "Bookmarks only",
          color = filterFg,
          style =
            MaterialTheme.typography.labelLarge.copy(
              fontWeight = if (onlyBookmarked) FontWeight.SemiBold else FontWeight.Medium,
            ),
          maxLines = 1,
          softWrap = false,
        )
      }

      // Primary Add page pill
      Button(
        onClick = { onInsert(count) },
        shape = PenLabShapes.pill,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary,
          ),
        modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = PenLabSpacing.sm),
      ) {
        Icon(
          imageVector = PenLabIcons.Plus,
          contentDescription = null,
          modifier = Modifier.size(PenLabLayout.iconSmall),
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = "Add page",
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
          maxLines = 1,
          softWrap = false,
        )
      }
    }

    if (pages.isEmpty()) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = PenLabSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
      ) {
        Icon(
          imageVector = PenLabIcons.BookmarkOutline,
          contentDescription = null,
          tint = scheme.onSurfaceVariant,
          modifier = Modifier.size(28.dp),
        )
        Text(
          text = "No bookmarked pages yet. Tap the bookmark ribbon on any page to pin it here.",
          color = scheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
        )
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(vertical = PenLabSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.md),
        verticalArrangement = Arrangement.spacedBy(PenLabSpacing.md),
        modifier = Modifier.fillMaxWidth().heightIn(max = 432.dp),
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
    }

    Text(
      text = "Long-press a page to insert before/after or delete.",
      color = scheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodySmall,
    )
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
  val scheme = MaterialTheme.colorScheme
  var menu by remember { mutableStateOf(false) }
  val shape = RoundedCornerShape(8.dp)

  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Box(
        Modifier
          .fillMaxWidth()
          .aspectRatio(210f / 297f)
          .clip(shape)
          .background(scheme.surface)
          .border(
            width = if (current) 2.dp else 1.dp,
            color = if (current) scheme.primary else scheme.outlineVariant,
            shape = shape,
          )
          .combinedClickable(onClick = onJump, onLongClick = { menu = true })
          .semantics {
            contentDescription = "Page ${page + 1}. Long press for options."
            if (current) stateDescription = "Current page"
          },
      ) {
        image?.let {
          Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }

      // High-contrast bookmark toggle button in the top-right corner of the thumbnail.
      Box(
        modifier =
          Modifier
            .align(Alignment.TopEnd)
            .size(PenLabSpacing.touchTarget)
            .clip(CircleShape)
            .clickable(role = Role.Switch, onClick = onToggleBookmark)
            .semantics {
              contentDescription = if (bookmarked) "Remove bookmark from page ${page + 1}" else "Bookmark page ${page + 1}"
              selected = bookmarked
            },
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier =
            Modifier
              .size(PenLabLayout.controlVisualSmall)
              .clip(CircleShape)
              .background(scheme.surface.copy(alpha = 0.85f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = if (bookmarked) PenLabIcons.BookmarkFilled else PenLabIcons.BookmarkOutline,
            contentDescription = null,
            tint = if (bookmarked) scheme.tertiary else scheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
          )
        }
      }

      DropdownMenu(
        expanded = menu,
        onDismissRequest = { menu = false },
        containerColor = PenLabComponentTokens.panelBg,
      ) {
        DropdownMenuItem(
          text = { Text("Insert page before", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge) },
          leadingIcon = { Icon(PenLabIcons.Plus, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(PenLabLayout.iconCompact)) },
          onClick = {
            menu = false
            onInsertBefore()
          },
        )
        DropdownMenuItem(
          text = { Text("Insert page after", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge) },
          leadingIcon = { Icon(PenLabIcons.Plus, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(PenLabLayout.iconCompact)) },
          onClick = {
            menu = false
            onInsertAfter()
          },
        )
        if (onDelete != null) {
          DropdownMenuItem(
            text = { Text("Delete page", color = scheme.error, style = MaterialTheme.typography.labelLarge) },
            leadingIcon = { Icon(PenLabIcons.Delete, contentDescription = null, tint = scheme.error, modifier = Modifier.size(PenLabLayout.iconCompact)) },
            onClick = {
              menu = false
              onDelete()
            },
          )
        }
      }
    }

    Spacer(Modifier.height(PenLabSpacing.xs))
    Text(
      text = "${page + 1}",
      color = if (current) scheme.primary else scheme.onSurfaceVariant,
      style =
        PenLabTextStyles.tabularMedium.copy(
          fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
        ),
    )
  }
}
