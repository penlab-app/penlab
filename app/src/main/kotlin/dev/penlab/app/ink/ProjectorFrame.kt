package dev.penlab.app.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke

/**
 * One picture of the page for the projector: what the tablet shows, drawn off-screen at the
 * projector's resolution, with no toolbar, selection box, handles or other interface.
 *
 * Made on the main thread by InkCanvasView.projectorSnapshot (which only copies references and
 * looks things up, so it is cheap), then drawn on a background thread with [render]. It uses the
 * same pieces as the thumbnails and the PDF export: PageBackground for the ruling, ItemPainter for
 * photos and text, CanvasStrokeRenderer for ink, the PDF slide bitmaps the screen already has.
 *
 * Live in-progress strokes are NOT mirrored (version 1): they appear on the projector when the pen
 * lifts and the stroke is committed.
 */
class ProjectorFrame internal constructor(
  private val widthPx: Int,
  private val heightPx: Int,
  /** Page units to projector pixels: the tablet's own view, re-fitted to the projector's shape. */
  private val pageToProjector: Matrix,
  /** The part of the page that is on the projector, in page units (bounding box if turned). */
  private val visible: RectF,
  /** A copy of the notebook's page layout, or null on an endless canvas. */
  private val layout: PageLayout?,
  private val style: PageStyle,
  private val darkPage: Boolean,
  /** Colour around the pages (the desk) or, on an endless canvas, the paper. */
  val backgroundColor: Int,
  private val edgeColor: Int,
  private val edgeWidthPx: Float,
  /** For each PDF-backed visible page: its picture, or null if it is not drawn yet. */
  private val slides: Map<Int, Bitmap?>,
  /** The strokes on screen in drawing order, not yet turned into screen-coloured strokes. */
  private val inks: List<InkStroke>,
  private val items: List<Pair<PageItem, (Int) -> Int>>,
  /** Makes a screen-coloured stroke on any thread (InkCanvasView.buildForPicture). */
  private val screenStroke: (InkStroke) -> Stroke,
  private val background: PageBackground,
  private val itemPainter: ItemPainter,
  private val renderer: CanvasStrokeRenderer,
) {
  /** Draws the picture. Call on a background thread, one at a time per canvas. */
  fun render(): Bitmap {
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(backgroundColor)
    canvas.save()
    canvas.concat(pageToProjector)
    val zoom = pageToProjector.mapRadius(1f)
    if (layout == null) {
      background.draw(canvas, style, visible, zoom)
    } else {
      drawPages(canvas, layout, zoom)
    }
    for ((item, colour) in items) itemPainter.draw(canvas, item, colour)
    for (ink in inks) renderer.draw(canvas, screenStroke(ink), pageToProjector)
    canvas.restore()
    bitmap.prepareToDraw()
    return bitmap
  }

  private fun drawPages(canvas: Canvas, layout: PageLayout, zoom: Float) {
    val paper = Paint().apply { color = InkColors.pageOnScreen(darkPage) }
    val white = Paint().apply { color = android.graphics.Color.WHITE }
    val slidePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      color = edgeColor
      strokeWidth = edgeWidthPx / zoom
    }
    val page = RectF()
    val ruled = RectF()
    val first = layout.pageAt(visible.top)
    val last = layout.pageAt(visible.bottom)
    for (index in first..last) {
      layout.rect(index, page)
      if (!RectF.intersects(page, visible)) continue
      if (slides.containsKey(index)) {
        // A PDF page: white paper and the slide's picture once it is drawn, no ruling.
        canvas.drawRect(page, white)
        slides[index]?.let { canvas.drawBitmap(it, null, page, slidePaint) }
      } else {
        canvas.drawRect(page, paper)
        canvas.save()
        canvas.clipRect(page)
        canvas.translate(0f, page.top)
        ruled.set(visible)
        ruled.intersect(page)
        ruled.offset(0f, -page.top)
        background.draw(canvas, style, ruled, zoom)
        canvas.restore()
      }
      canvas.drawRect(page, edge)
    }
  }
}
