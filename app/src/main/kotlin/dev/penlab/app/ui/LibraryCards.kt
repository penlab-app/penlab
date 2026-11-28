package dev.penlab.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.penlab.app.ink.InkColors
import dev.penlab.app.ink.PageBackground
import dev.penlab.app.ink.PageStyle
import dev.penlab.app.notes.NotebookInfo
import dev.penlab.app.notes.NotebookKind
import dev.penlab.app.ui.components.PenLabIconButton
import dev.penlab.app.ui.components.PenLabMenuItem
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.PenLabLayout
import dev.penlab.app.ui.theme.PenLabShapes
import dev.penlab.app.ui.theme.PenLabSpacing
import dev.penlab.app.ui.theme.PenLabTextStyles
import dev.penlab.app.ui.theme.flatCardSurface

/*
 * The library's cards: NotebookCard, FolderCard and CourseShelf. One depth cue each (a 1dp
 * hairline on the cover, no shadow, no card inside a card), a visible three-dot button that opens
 * the same menu a long-press does, and tokens only.
 */

/** Overflow menu surface shared by every card and the header: panel colour, medium radius. */
@Composable
internal fun LibraryMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
  DropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismiss,
    containerColor = PenLabComponentTokens.panelBg,
    shape = PenLabShapes.medium,
    modifier = Modifier.widthIn(min = PenLabLayout.menuMinWidth),
  ) {
    content()
  }
}

/** The visible 48dp three-dot button; [menu] is the menu, anchored here. Long-press shares [open]. */
@Composable
private fun CardMenuButton(label: String, open: Boolean, onOpen: () -> Unit, onDismiss: () -> Unit, menu: @Composable () -> Unit) {
  Box {
    PenLabIconButton(
      icon = PenLabIcons.MoreVert,
      contentDescription = label,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      onClick = onOpen,
    )
    LibraryMenu(open, onDismiss, menu)
  }
}

/** A notebook: its cover (first page, or a sheet of its paper), PDF badge, title, pages and date. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NotebookCard(
  notebook: NotebookInfo,
  darkPage: Boolean,
  coverFile: (NotebookInfo, Boolean) -> java.io.File,
  onOpen: () -> Unit,
  onRename: () -> Unit,
  onMove: () -> Unit,
  onDelete: () -> Unit,
  onExport: () -> Unit,
  onShare: () -> Unit,
  onTurn: () -> Unit,
  modifier: Modifier = Modifier,
  /** Shown before the date, e.g. the folder on a Continue card. */
  place: String? = null,
  /** The folder's colour, as a small mark beside the place. */
  accent: Int? = null,
  /** When set (e.g. Continue strip), locks every card thumbnail to the same top-aligned aspect ratio. */
  uniformAspect: Float? = null,
) {
  var menu by remember { mutableStateOf(false) }
  val scheme = MaterialTheme.colorScheme
  val date = remember(notebook.modified) { cardDateLabel(System.currentTimeMillis(), notebook.modified) }
  val isPdf = notebook.pdfPages.isNotEmpty()
  val endless = notebook.kind == NotebookKind.INFINITE
  val cleanPlace = place?.trim()?.ifEmpty { null }
  val detail = cardMeta(null, endless, notebook.pageCount, date)
  Column(
    modifier
      .clip(PenLabShapes.mediumTop)
      .combinedClickable(role = Role.Button, onClick = onOpen, onLongClick = { menu = true })
      .semantics { contentDescription = "${notebook.title}, $detail. Long press or use the menu for options." }
  ) {
    val aspect = if (notebook.kind == NotebookKind.A4) notebook.pageWidthMm / notebook.pageHeightMm else PenLabLayout.canvasAspect
    val outerModifier =
      if (uniformAspect != null) Modifier.fillMaxWidth().aspectRatio(uniformAspect)
      else Modifier.fillMaxWidth().aspectRatio(COVER_BOX_ASPECT)
    // Top-align covers so adjacent thumbnails share a crisp horizontal top edge; when uniformAspect
    // is provided (Continue strip), every card fills the exact same thumbnail rectangle.
    Box(outerModifier, contentAlignment = Alignment.TopCenter) {
      val innerModifier =
        if (uniformAspect != null) Modifier.fillMaxSize()
        else (if (coverFillsBoxWidth(aspect)) Modifier.fillMaxWidth() else Modifier.fillMaxHeight()).aspectRatio(aspect)
      Box(innerModifier) {
        // The real first page once there is a cover; the paper style until then.
        val cover by produceState(
          Covers.cachedVariant(notebook.id, darkPage, isPdf),
          notebook.id,
          notebook.modified,
          darkPage,
          Covers.revision(notebook.id),
        ) {
          value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Covers.loadVariant(notebook.id, darkPage, isPdf) { dark -> coverFile(notebook, dark) }
          }
        }
        val resolved = cover
        if (resolved != null) {
          Image(
            resolved.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            // Fallback for older notebooks without cover-dark.jpg yet; newly saved dark covers already match.
            colorFilter = if (resolved.invertFallback) InvertCover else null,
            modifier = Modifier.fillMaxSize()
              .flatCardSurface(PenLabShapes.medium, PenLabComponentTokens.cardBg, PenLabComponentTokens.cardBorder),
          )
        } else {
          PaperPreview(notebook.pageStyle, darkPage, pdf = isPdf, modifier = Modifier.fillMaxSize(), shape = PenLabShapes.medium)
        }
        if (isPdf) {
          Text(
            "PDF",
            color = scheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.TopStart).padding(PenLabSpacing.sm)
              .clip(PenLabShapes.extraSmall).background(scheme.primaryContainer)
              .padding(horizontal = PenLabSpacing.sm, vertical = PenLabSpacing.xs),
          )
        }
      }
    }
    // Title beside the menu button; the grey meta line below gets the card's whole width
    // (beside the button a shelf card had room for only ~100dp and cut the date off).
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
      Text(
        breakableTitle(notebook.title),
        color = scheme.onSurface,
        // Heading line breaking prefers the word boundary; a word longer than the card still has to give.
        style = MaterialTheme.typography.titleSmall.copy(lineBreak = LineBreak.Heading, hyphens = Hyphens.None),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(top = PenLabSpacing.sm),
      )
      CardMenuButton("Options for ${notebook.title}", menu, onOpen = { menu = true }, onDismiss = { menu = false }) {
        PenLabMenuItem("Rename", { menu = false; onRename() }, icon = PenLabIcons.Rename)
        PenLabMenuItem("Move to folder", { menu = false; onMove() }, icon = PenLabIcons.Move)
        PenLabMenuItem("Export PDF", { menu = false; onExport() }, icon = PenLabIcons.Pdf)
        PenLabMenuItem("Share PDF", { menu = false; onShare() }, icon = PenLabIcons.Export)
        // Plain A4 notebooks can be turned; a PDF's pages keep their own shape.
        if (notebook.kind == NotebookKind.A4 && !isPdf) {
          val toLandscape = notebook.pageWidthMm < notebook.pageHeightMm
          PenLabMenuItem(if (toLandscape) "Turn to landscape" else "Turn to portrait", { menu = false; onTurn() }, icon = PenLabIcons.Rotate)
        }
        PenLabMenuItem("Delete", { menu = false; onDelete() }, icon = PenLabIcons.Delete, destructive = true)
      }
    }
    // A little air under a two-line title before the meta line.
    Row(Modifier.padding(top = PenLabSpacing.xs), verticalAlignment = Alignment.Top) {
      if (cleanPlace != null) {
        Box(Modifier.padding(top = PenLabSpacing.xs).size(PenLabLayout.dot).background(accent?.let { Color(it) } ?: scheme.onSurfaceVariant, CircleShape))
        Spacer(Modifier.width(PenLabSpacing.sm))
      }
      Text(
        cardMeta(cleanPlace, endless, notebook.pageCount, date),
        color = scheme.onSurfaceVariant,
        style = PenLabTextStyles.tabularMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/** The folder menu entries, shared by [FolderCard] and [CourseShelf]. */
@Composable
private fun FolderMenuItems(
  onDismiss: () -> Unit,
  onOpen: (() -> Unit)?,
  onRename: () -> Unit,
  onColor: () -> Unit,
  onMove: () -> Unit,
  onDelete: () -> Unit,
) {
  if (onOpen != null) PenLabMenuItem("Open folder", { onDismiss(); onOpen() }, icon = PenLabIcons.Folder)
  PenLabMenuItem("Rename", { onDismiss(); onRename() }, icon = PenLabIcons.Rename)
  PenLabMenuItem("Colour", { onDismiss(); onColor() }, icon = PenLabIcons.Palette)
  PenLabMenuItem("Move to folder", { onDismiss(); onMove() }, icon = PenLabIcons.Move)
  PenLabMenuItem("Delete (if empty)", { onDismiss(); onDelete() }, icon = PenLabIcons.Delete, destructive = true)
}

/** A folder inside a folder: a tinted tile with its icon, name, notebook count and a menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderCard(
  name: String,
  count: Int,
  color: Int?,
  onOpen: () -> Unit,
  onRename: () -> Unit,
  onMove: () -> Unit,
  onColor: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var menu by remember { mutableStateOf(false) }
  val scheme = MaterialTheme.colorScheme
  val accent = color?.let { Color(it) }
  val body = accent?.let { lerp(PenLabComponentTokens.cardBg, it, 0.14f) } ?: PenLabComponentTokens.cardBg
  val edge = accent?.copy(alpha = 0.5f) ?: PenLabComponentTokens.cardBorder
  Column(
    modifier
      .clip(PenLabShapes.mediumTop)
      .combinedClickable(role = Role.Button, onClick = onOpen, onLongClick = { menu = true })
      .semantics { contentDescription = "Folder $name, ${notebooksLabel(count)}. Long press or use the menu for options." }
  ) {
    Box(
      Modifier.fillMaxWidth().aspectRatio(PenLabLayout.canvasAspect).flatCardSurface(PenLabShapes.medium, body, edge),
      contentAlignment = Alignment.Center,
    ) {
      Icon(PenLabIcons.Folder, contentDescription = null, tint = accent ?: scheme.onSurfaceVariant, modifier = Modifier.size(PenLabLayout.iconFolder))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
      Column(Modifier.weight(1f).padding(top = PenLabSpacing.sm), verticalArrangement = Arrangement.spacedBy(PenLabSpacing.xs)) {
        Text(name, color = scheme.onSurface, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(notebooksLabel(count), color = scheme.onSurfaceVariant, style = PenLabTextStyles.tabularMedium, maxLines = 1)
      }
      CardMenuButton("Options for folder $name", menu, onOpen = { menu = true }, onDismiss = { menu = false }) {
        FolderMenuItems({ menu = false }, null, onRename, onColor, onMove, onDelete)
      }
    }
  }
}

/** One course folder as a shelf: colour dot, name, count, an Open action, a menu, and its newest notebooks. */
@Composable
internal fun CourseShelf(
  name: String,
  parent: String,
  count: Int,
  color: Int?,
  onOpenFolder: () -> Unit,
  onRename: () -> Unit,
  onMove: () -> Unit,
  onColor: () -> Unit,
  onDelete: () -> Unit,
  books: @Composable () -> Unit,
) {
  var menu by remember { mutableStateOf(false) }
  val scheme = MaterialTheme.colorScheme
  Column(Modifier.fillMaxWidth().padding(top = PenLabSpacing.sm), verticalArrangement = Arrangement.spacedBy(PenLabSpacing.sm)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Row(
        Modifier.weight(1f).heightIn(min = PenLabSpacing.touchTarget).clip(PenLabShapes.small)
          .clickable(role = Role.Button, onClick = onOpenFolder).padding(horizontal = PenLabSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(PenLabLayout.dotLarge).background(color?.let { Color(it) } ?: scheme.onSurfaceVariant, CircleShape))
        Spacer(Modifier.width(PenLabSpacing.md))
        Text(name, color = scheme.onSurface, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (parent.isNotBlank()) {
          Spacer(Modifier.width(PenLabSpacing.sm))
          Text(parent, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(PenLabSpacing.md))
        Text(notebooksLabel(count), color = scheme.onSurfaceVariant, style = PenLabTextStyles.tabularMedium, maxLines = 1)
      }
      TextButton(onClick = onOpenFolder, modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget)) {
        Text("Open", color = scheme.primary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Icon(PenLabIcons.ChevronRight, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(PenLabLayout.iconSmall))
      }
      CardMenuButton("Options for folder $name", menu, onOpen = { menu = true }, onDismiss = { menu = false }) {
        FolderMenuItems({ menu = false }, onOpenFolder, onRename, onColor, onMove, onDelete)
      }
    }
    if (count == 0) {
      Text("Empty", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = PenLabSpacing.xs))
    } else {
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.lg)) { books() }
    }
  }
}

/** Covers on the dark page: lightness inverted, hue kept (the same matrix the page uses for ink pictures). */
private val InvertCover =
  ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(0.574f, -1.430f, -0.144f, 0f, 255f, -0.426f, -0.430f, -0.144f, 0f, 255f, -0.426f, -1.430f, 0.856f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))
  )

/**
 * A blank sheet of a notebook's paper with its ruling, in the active Light/Dark paper tint: the
 * cover until page thumbnails exist, and the swatches in the New notebook dialog. Ruling scales with
 * the sheet's width, so a small swatch looks like a shrunken page rather than a different one.
 */
@Composable
internal fun PaperPreview(
  style: PageStyle,
  darkPage: Boolean,
  modifier: Modifier = Modifier,
  pdf: Boolean = false,
  shape: Shape = PenLabShapes.small,
) {
  val paper = if (pdf) Color.White else Color(InkColors.pageOnScreen(darkPage))
  val ruling = Color(InkColors.onScreen(PageBackground.RULING_PAPER_COLOR, darkPage))
  val minor = Color(InkColors.onScreen(PageBackground.MINOR_PAPER_COLOR, darkPage))
  val slideInk = MaterialTheme.colorScheme.outline
  Canvas(modifier.flatCardSurface(shape, paper, PenLabComponentTokens.cardBorder)) {
    val step = size.width / 16f
    val w = size.width
    val h = size.height
    val linePx = 0.7.dp.toPx()
    val accentLinePx = 1.25.dp.toPx()
    if (pdf) {
      // A stand-in for a slide until real covers exist: a title bar and a few text lines.
      drawRect(slideInk, topLeft = Offset(w * 0.12f, h * 0.16f), size = androidx.compose.ui.geometry.Size(w * 0.5f, h * 0.09f))
      for (i in 0 until 4) {
        val y = h * (0.4f + i * 0.12f)
        drawLine(slideInk, Offset(w * 0.12f, y), Offset(w * (0.8f - i * 0.1f), y), strokeWidth = accentLinePx)
      }
      return@Canvas
    }
    when (style) {
      PageStyle.BLANK -> Unit
      PageStyle.LINED -> {
        var y = step * 1.5f
        while (y < h) { drawLine(ruling, Offset(0f, y), Offset(w, y), strokeWidth = linePx); y += step }
      }
      PageStyle.DOTTED -> {
        val dotRadius = 0.9.dp.toPx()
        var y = step
        while (y < h) {
          var x = step
          while (x < w) { drawCircle(ruling, radius = dotRadius, center = Offset(x, y)); x += step }
          y += step
        }
      }
      PageStyle.CORNELL -> {
        var y = step * 1.5f
        while (y < h) { drawLine(minor, Offset(0f, y), Offset(w, y), strokeWidth = 0.6.dp.toPx()); y += step }
        drawLine(ruling, Offset(w * 0.3f, step * 1.5f), Offset(w * 0.3f, h * 0.8f), strokeWidth = accentLinePx)
        drawLine(ruling, Offset(0f, h * 0.8f), Offset(w, h * 0.8f), strokeWidth = accentLinePx)
      }
      PageStyle.CP -> {
        val minorPx = 0.5.dp.toPx()
        var x = 0f
        while (x < w) { drawLine(minor, Offset(x, 0f), Offset(x, h), strokeWidth = minorPx); x += step * 0.7f }
        var y = 0f
        while (y < h) { drawLine(minor, Offset(0f, y), Offset(w, y), strokeWidth = minorPx); y += step * 0.7f }
        drawLine(ruling, Offset(0f, h * 0.12f), Offset(w, h * 0.12f), strokeWidth = accentLinePx)
        drawLine(ruling, Offset(w * 0.14f, h * 0.12f), Offset(w * 0.14f, h), strokeWidth = accentLinePx)
      }
      PageStyle.ISOMETRIC -> {
        val gap = step * 0.9f
        val isoPx = 0.6.dp.toPx()
        val isoColor = ruling.copy(alpha = 0.72f)
        var x = 0f
        while (x <= w) {
          drawLine(isoColor, Offset(x, 0f), Offset(x, h), strokeWidth = isoPx)
          x += gap * 0.866f
        }
        var c = -w
        while (c < h + w) {
          drawLine(isoColor, Offset(0f, c), Offset(w, c + w * 0.577f), strokeWidth = isoPx)
          drawLine(isoColor, Offset(0f, c), Offset(w, c - w * 0.577f), strokeWidth = isoPx)
          c += gap
        }
      }
      PageStyle.GRID, PageStyle.ENGINEERING, PageStyle.GRAPH -> {
        // Distinct density & major/minor hierarchy: Grid (even), Engineering (4-cell heavy pad), Graph (fine 5-cell graph).
        val cell =
          when (style) {
            PageStyle.GRID -> maxOf(step * 0.9f, 7.dp.toPx())
            PageStyle.ENGINEERING -> maxOf(step * 0.65f, 5.dp.toPx())
            PageStyle.GRAPH -> maxOf(step * 0.35f, 2.8.dp.toPx())
            else -> step * 0.65f
          }
        val heavyEvery =
          when (style) {
            PageStyle.GRID -> 1
            PageStyle.ENGINEERING -> 4
            PageStyle.GRAPH -> 5
            else -> 1
          }
        val minorPx = if (style == PageStyle.GRAPH) 0.45.dp.toPx() else 0.55.dp.toPx()
        val majorPx = if (style == PageStyle.GRID) 0.75.dp.toPx() else 1.2.dp.toPx()
        var i = 0
        var x = 0f
        while (x <= w) {
          val heavy = i % heavyEvery == 0
          drawLine(if (heavy) ruling else minor, Offset(x, 0f), Offset(x, h), strokeWidth = if (heavy) majorPx else minorPx)
          x += cell
          i++
        }
        i = 0
        var y = 0f
        while (y <= h) {
          val heavy = i % heavyEvery == 0
          drawLine(if (heavy) ruling else minor, Offset(0f, y), Offset(w, y), strokeWidth = if (heavy) majorPx else minorPx)
          y += cell
          i++
        }
      }
    }
  }
}
