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

/** Pure padded cover bounds for endless canvases (unit-tested). */
internal data class EndlessCoverBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
  val width: Float get() = right - left
  val height: Float get() = bottom - top
}

/**
 * Expands tight stroke/item bounds on an endless canvas with generous breathing room and clamps
 * aspect ratio so wide handwritten lines (e.g. a single name) aren't cropped by 4:3 card frames.
 */
internal fun computeEndlessCoverBounds(left: Float, top: Float, right: Float, bottom: Float): EndlessCoverBounds {
  val rawW = (right - left).coerceAtLeast(1f)
  val rawH = (bottom - top).coerceAtLeast(1f)
  val padX = maxOf(rawW * 0.12f, 32f)
  val padY = maxOf(rawH * 0.14f, 32f)
  val paddedLeft = left - padX
  val paddedRight = right + padX
  var paddedTop = top - padY
  var paddedBottom = bottom + padY
  val paddedW = paddedRight - paddedLeft
  val minAspectHeight = paddedW / (4f / 3f)
  val maxAspectHeight = paddedW * 1.4f
  val currentH = paddedBottom - paddedTop
  if (currentH < minAspectHeight) {
    val extra = (minAspectHeight - currentH) / 2f
    paddedTop -= extra
    paddedBottom += extra
  } else if (currentH > maxAspectHeight) {
    // Covers are portrait-ish cards: show the top of a tall canvas, not a thin strip.
    paddedBottom = paddedTop + maxAspectHeight
  }
  return EndlessCoverBounds(paddedLeft, paddedTop, paddedRight, paddedBottom)
}

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
      val raw = (strokes.map { it.bounds } + items.map { it.bounds(RectF()) }).fold(null as RectF?) { acc, b -> acc?.apply { union(b) } ?: RectF(b) }!!
      val padded = computeEndlessCoverBounds(raw.left, raw.top, raw.right, raw.bottom)
      area = RectF(padded.left, padded.top, padded.right, padded.bottom)
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
