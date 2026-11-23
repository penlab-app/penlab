package dev.penlab.app.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Draws the notebook's pages (paper, ruling, PDF slides, thin edge) for the projector's follower
 * view. The tablet's own version is FinishedStrokesView.drawPages in InkCanvasView, which also
 * draws the pull-to-add-page hint and starts PDF work; this one only looks things up, so the
 * follower can call it every frame without side effects.
 *
 * Holds its paints and rectangles so a frame allocates nothing.
 */
internal class ProjectorPages {
  private val white = Paint().apply { color = android.graphics.Color.WHITE }
  private val paper = Paint()
  private val slidePaint = Paint(Paint.FILTER_BITMAP_FLAG)
  private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
  private val page = RectF()
  private val ruled = RectF()

  /**
   * Draws the pages that touch [visible] (page units) onto [canvas], which already maps page
   * units to the projector. [zoom] is projector pixels per page unit. [slideFor] returns a PDF
   * page's picture, or null if it is not drawn yet; [isPdfPage] says which pages are slides.
   */
  fun draw(
    canvas: Canvas,
    layout: PageLayout,
    visible: RectF,
    zoom: Float,
    style: PageStyle,
    background: PageBackground,
    paperColor: Int,
    edgeColor: Int,
    edgeWidthPx: Float,
    isPdfPage: (Int) -> Boolean,
    slideFor: (Int) -> Bitmap?,
  ) {
    paper.color = paperColor
    edge.color = edgeColor
    edge.strokeWidth = edgeWidthPx / zoom
    val first = layout.pageAt(visible.top)
    val last = layout.pageAt(visible.bottom)
    for (index in first..last) {
      layout.rect(index, page)
      if (!RectF.intersects(page, visible)) continue
      if (isPdfPage(index)) {
        // A PDF page: white paper and the slide's picture once it is drawn, no ruling.
        canvas.drawRect(page, white)
        slideFor(index)?.let { canvas.drawBitmap(it, null, page, slidePaint) }
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
