package dev.penlab.app.plugins

import dev.penlab.app.ink.InkStroke
import dev.penlab.app.ink.PageItem
import dev.penlab.app.ink.PageStyle
import dev.penlab.app.notes.NotebookInfo
import dev.penlab.plugin.ExportSource
import dev.penlab.plugin.PluginItem
import dev.penlab.plugin.PluginStroke
import java.io.File

/**
 * The app's [ExportSource]. It also carries the app's own strokes and items, which only the
 * built-in PDF exporter uses (it needs the exact brushes); other exporters use [strokes] and
 * [items], the plain copies in millimetres.
 */
class AppExportSource(
  val info: NotebookInfo,
  val inkStrokes: List<InkStroke>,
  val pageItems: List<PageItem>,
  val pageStyle: PageStyle,
  val pdfFile: File?,
  val imagesDir: File?,
  override val dark: Boolean,
  private val unitsPerMm: Float,
) : ExportSource {
  override val notebookId get() = info.id
  override val title get() = info.title
  override val pageWidthMm get() = info.pageWidthMm
  override val pageHeightMm get() = info.pageHeightMm
  override val pageCount get() = info.pageCount

  override fun strokes(): List<PluginStroke> = inkStrokes.map { PluginConvert.stroke(it, unitsPerMm) }

  override fun items(): List<PluginItem> = pageItems.map { PluginConvert.item(it, unitsPerMm) }
}
