package dev.penlab.app.notes

import dev.penlab.app.ink.PageLayout
import dev.penlab.app.ink.PageStyle
import dev.penlab.plugin.ImportedNotebook
import kotlin.math.floor

/** True when [imported] actually references at least one PDF background page alongside a PDF file. */
internal fun importedHasPdfBackground(imported: ImportedNotebook): Boolean =
  imported.backgroundPdf != null && imported.pages.any { it.backgroundPdfPage >= 0 }

/** Returns per-page PDF page numbers only when [imported] actually uses a background PDF; empty otherwise. */
internal fun resolveImportedPdfPages(imported: ImportedNotebook): List<Int> =
  if (importedHasPdfBackground(imported)) imported.pages.map { it.backgroundPdfPage } else emptyList()

/**
 * Merges live canvas metadata into [info] without clobbering identity/organisation fields such as
 * [NotebookInfo.title], [NotebookInfo.folder], or [NotebookInfo.recordings].
 */
internal fun syncNotebookMetadata(
  info: NotebookInfo,
  pageStyle: PageStyle,
  rulingMm: Float,
  layerNames: List<String>,
  hiddenLayers: Set<Int>,
  currentLayer: Int,
  pageCount: Int,
  bookmarks: Set<Int>,
  pdfPages: List<Int>,
  now: Long,
): NotebookInfo {
  val safeCount = pageCount.coerceAtLeast(1)
  val safeNames = layerNames.ifEmpty { listOf("Layer 1") }
  return info.copy(
    modified = now,
    pageStyle = pageStyle,
    rulingMm = rulingMm,
    layerNames = safeNames.toList(),
    hiddenLayers = hiddenLayers.filter { it in safeNames.indices }.sorted(),
    currentLayer = currentLayer.coerceIn(0, safeNames.lastIndex),
    pageCount = safeCount,
    bookmarks = bookmarks.filter { it in 0 until safeCount }.sorted(),
    pdfPages = pdfPages.toList(),
  )
}

/**
 * Returns [info] with swapped portrait/landscape page dimensions if it is a plain A4 notebook, or
 * null if it cannot be turned (infinite canvas or PDF-backed notebook).
 */
internal fun turnNotebookInfo(info: NotebookInfo, now: Long): NotebookInfo? {
  if (info.kind != NotebookKind.A4 || info.pdfPages.isNotEmpty()) return null
  return info.copy(
    pageWidthMm = info.pageHeightMm,
    pageHeightMm = info.pageWidthMm,
    modified = now,
  )
}

/**
 * Vertical shift (page units) needed for a stroke or item whose centre is at [centerY] so it stays
 * at the same distance below its page's top edge after turning a paged notebook.
 */
internal fun turnShiftDy(
  centerY: Float,
  oldHeightMm: Float,
  newHeightMm: Float,
  pageCount: Int,
): Float {
  val unitsPerMm = PageLayout.UNITS_PER_MM
  val gap = 12f * unitsPerMm
  val oldPitch = oldHeightMm * unitsPerMm + gap
  val newPitch = newHeightMm * unitsPerMm + gap
  val maxPage = (pageCount - 1).coerceAtLeast(0)
  val page = floor(centerY / oldPitch).toInt().coerceIn(0, maxPage)
  return page * (newPitch - oldPitch)
}
