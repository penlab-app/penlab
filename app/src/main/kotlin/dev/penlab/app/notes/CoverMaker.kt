package dev.penlab.app.notes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import dev.penlab.app.ink.Brushes
import dev.penlab.app.ink.InkColors
import dev.penlab.app.ink.ItemPainter
import dev.penlab.app.ink.PageLayout
import dev.penlab.app.ink.PageStore
import java.io.File

/**
 * Library covers for notebooks that don't have one yet (QC 22 Nov: most cards were blank dark
 * rectangles, so notebooks couldn't be told apart). Draws the first page (an endless canvas:
 * everything on it) in paper or dark-paper colours, matching the cover saved when closed.
 * Background thread only.
 */
object CoverMaker {
  private const val WIDTH = 360

  fun make(info: NotebookInfo, store: PageStore, pdf: File?, imagesDir: File? = null, darkPage: Boolean = false): Bitmap? {
    val page = store.readNow()
    val strokes = page?.strokes.orEmpty()
    val items = page?.items.orEmpty()
    val hasPdf = info.pdfPages.isNotEmpty() && pdf?.exists() == true
    if (strokes.isEmpty() && items.isEmpty() && !hasPdf) return null // nothing to show yet: the paper style stays
    val effectiveDark = darkPage && !hasPdf
    val area: RectF
    if (info.kind == NotebookKind.A4) {
      val layout = PageLayout.paged(info.pageWidthMm, info.pageHeightMm, info.pageCount, info.bookmarks, info.pdfPages)
      area = layout.rect(0, RectF())
    } else {
      area = (strokes.map { it.bounds } + items.map { it.bounds(RectF()) }).fold(null as RectF?) { acc, b -> acc?.apply { union(b) } ?: RectF(b) }!!
        .apply { inset(-width() * 0.05f, -height() * 0.05f) }
      // Covers are portrait-ish cards: show the top of a tall canvas, not a thin strip.
      if (area.height() > area.width() * 1.4f) area.bottom = area.top + area.width() * 1.4f
    }
    val scale = WIDTH / area.width()
    val bitmap = Bitmap.createBitmap(WIDTH, (area.height() * scale).toInt().coerceIn(1, WIDTH * 2), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(InkColors.pageOnScreen(effectiveDark))
    if (hasPdf) {
      try {
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
          PdfRenderer(fd).use { r ->
            val slide = info.pdfPages.firstOrNull() ?: 0
            if (slide in 0 until r.pageCount) r.openPage(slide).use { page ->
              page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
          }
        }
      } catch (e: Exception) {
        android.util.Log.w("Cover", "PDF page not drawn", e)
      }
    }
    val matrix = Matrix().apply { setTranslate(-area.left, -area.top); postScale(scale, scale) }
    val renderer = CanvasStrokeRenderer.create()
    canvas.save()
    canvas.concat(matrix)
    // Photos, text boxes and diagrams under the ink; photos stay un-inverted on dark paper.
    val painter = ItemPainter(imagesDir)
    val colorMap: (Int) -> Int = { InkColors.onScreen(it, effectiveDark) }
    for (item in items) if (item.cy >= area.top && item.cy <= area.bottom) painter.draw(canvas, item, colorMap)
    for (s in strokes) {
      if (s.layer in info.hiddenLayers) continue
      val c = s.bounds.centerY()
      if (c < area.top || c > area.bottom) continue
      val brush = if (effectiveDark) Brushes.recoloured(s.brush, InkColors.onScreen(s.brush.colorIntArgb, true)) else s.brush
      renderer.draw(canvas, Stroke(brush, s.inputBatch()), matrix)
    }
    canvas.restore()
    return bitmap
  }
}
