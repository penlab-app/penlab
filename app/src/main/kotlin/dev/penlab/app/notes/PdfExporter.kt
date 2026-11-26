package dev.penlab.app.notes

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import dev.penlab.app.ink.InkStroke
import dev.penlab.app.ink.PageBackground
import dev.penlab.app.ink.PageLayout
import dev.penlab.app.ink.PageStyle
import java.io.File
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Writes a notebook as a PDF: white paper, ink in its paper colours (strokes are stored that
 * way), highlights under the ink (page order is kept), ruling, and imported PDF pages under the
 * writing. Pages are their real size, so the PDF prints at the size it was written.
 *
 * Ink stays vector (sharp at any zoom). Imported PDF pages are included as pictures, because
 * Android can only draw PDF pages as pictures; they are drawn at 200 dots per inch.
 *
 * Runs on any thread (strokes never change, so reading them is safe).
 */
object PdfExporter {
  // The photos and text boxes of the notebook being exported (set by export()).
  private var currentItems: List<dev.penlab.app.ink.PageItem> = emptyList()
  private var currentPainter: dev.penlab.app.ink.ItemPainter? = null

  private const val POINTS_PER_MM = 72f / 25.4f
  private const val SLIDE_DPI = 200f

  /** Export as the dark page looks on screen: charcoal paper, ink lightness-inverted. */
  private var dark = false

  private val paperColor get() = if (dark) dev.penlab.app.ink.InkColors.pageOnScreen(true) else Color.WHITE

  /** Ink colour as exported: its paper colour, or the lightness-inverted one on a dark export. */
  private fun inkColor(paper: Int) = if (dark) dev.penlab.app.ink.InkColors.onScreen(paper, true) else paper

  private const val BATCH = 1500

  private var slideMs = 0L
  private var inkMs = 0L
  private var buildNs = 0L
  private var rebuilt = 0

  /** Strokes built for this export (those the screen hadn't built), keyed by identity. */
  private var prebuilt: Map<InkStroke, androidx.ink.strokes.Stroke> = emptyMap()

  /**
   * Builds the strokes the screen hasn't built yet on several cores at once. One at a time this
   * was 4.8 s of a 6.8 s export for the major task (20 Nov: 4,741 strokes at ~1 ms each).
   */
  private fun prebuild(strokes: List<InkStroke>) {
    val t = System.nanoTime()
    prebuilt = emptyMap()
    val missing = strokes.filter { dev.penlab.app.ink.StrokeMeshes.ifBuilt(it) == null }
    val result = java.util.IdentityHashMap<InkStroke, androidx.ink.strokes.Stroke>(missing.size * 2)
    if (missing.isNotEmpty()) {
      val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
      val pool = java.util.concurrent.Executors.newFixedThreadPool(threads)
      try {
        val parts = missing.chunked((missing.size + threads - 1) / threads)
        val futures = parts.map { part -> pool.submit<List<androidx.ink.strokes.Stroke>> { part.map { it.build() } } }
        parts.forEachIndexed { i, part -> part.zip(futures[i].get()).forEach { (ink, built) -> result[ink] = built } }
      } finally {
        pool.shutdown()
      }
    }
    prebuilt = result
    rebuilt += missing.size
    buildNs += System.nanoTime() - t
  }

  private fun built(stroke: InkStroke): androidx.ink.strokes.Stroke {
    // The open notebook has its strokes built already (on screen, or warmed at opening): reuse
    // them. Others are built just for the PDF and not cached, so exporting never pushes out
    // what the screen needs.
    val s = prebuilt[stroke] ?: dev.penlab.app.ink.StrokeMeshes.ifBuilt(stroke) ?: stroke.build()
    return if (dark) s.copy(s.brush.copyWithColorIntArgb(inkColor(s.brush.colorIntArgb))) else s
  }

  fun export(
    info: NotebookInfo,
    strokes: List<InkStroke>,
    pageStyle: PageStyle,
    pdfSource: File?,
    out: OutputStream,
    items: List<dev.penlab.app.ink.PageItem> = emptyList(),
    imagesDir: File? = null,
    dark: Boolean = false,
  ) {
    this.dark = dark
    slideMs = 0L
    inkMs = 0L
    buildNs = 0L
    rebuilt = 0
    val painter = dev.penlab.app.ink.ItemPainter(imagesDir)
    currentItems = items
    currentPainter = painter
    val document = PdfDocument()
    val renderer = CanvasStrokeRenderer.create()
    try {
      val t0 = android.os.SystemClock.uptimeMillis()
      if (info.kind == NotebookKind.A4) {
        writePages(document, renderer, info, strokes, pageStyle, pdfSource)
      } else {
        writeCanvas(document, renderer, strokes)
      }
      val t1 = android.os.SystemClock.uptimeMillis()
      document.writeTo(out)
      android.util.Log.i("PenLabPerf", "export: pages ${t1 - t0} ms (PDF pictures $slideMs ms, building $rebuilt strokes the screen hadn't ${buildNs / 1_000_000} ms, ink $inkMs ms), writing ${android.os.SystemClock.uptimeMillis() - t1} ms")
    } finally {
      document.close()
      prebuilt = emptyMap() // let the meshes go
    }
  }

  private fun writePages(
    document: PdfDocument,
    renderer: CanvasStrokeRenderer,
    info: NotebookInfo,
    strokes: List<InkStroke>,
    pageStyle: PageStyle,
    pdfSource: File?,
  ) {
    val layout = PageLayout.paged(info.pageWidthMm, info.pageHeightMm, info.pageCount, info.bookmarks, info.pdfPages)
    val pointsPerUnit = POINTS_PER_MM / PageLayout.UNITS_PER_MM
    val width = (layout.pageWidth * pointsPerUnit).roundToInt()
    val height = (layout.pageHeight * pointsPerUnit).roundToInt()
    val ruling = PageBackground(PageLayout.UNITS_PER_MM * 25.4f / 160f, darkPage = dark).apply {
      pageWidth = layout.pageWidth
      pageHeight = layout.pageHeight
      spacingMm = info.rulingMm
    }

    val descriptor = pdfSource?.takeIf { it.exists() }?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }
    val source = descriptor?.let { PdfRenderer(it) }
    try {
      for (pageIndex in 0 until layout.count) {
        val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, pageIndex + 1).create())
        val canvas = page.canvas
        canvas.drawColor(paperColor)

        val slide = layout.backgroundOf(pageIndex)
        val slideStart = android.os.SystemClock.uptimeMillis()
        if (slide >= 0 && source != null && slide < source.pageCount) {
          source.openPage(slide).use { pdfPage ->
            val pixelsWide = (width / 72f * SLIDE_DPI).roundToInt()
            val bitmap = Bitmap.createBitmap(pixelsWide, (pixelsWide.toLong() * pdfPage.height / pdfPage.width).toInt(), Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            bitmap.recycle()
          }
        } else {
          canvas.save()
          canvas.scale(pointsPerUnit, pointsPerUnit)
          ruling.draw(canvas, pageStyle, RectF(0f, 0f, layout.pageWidth, layout.pageHeight), pointsPerUnit)
          canvas.restore()
        }

        val inkStart = android.os.SystemClock.uptimeMillis()
        slideMs += inkStart - slideStart
        // The strokes whose middle is on this page, in page order.
        val top = layout.top(pageIndex)
        val matrix = Matrix().apply {
          setTranslate(0f, -top)
          postScale(pointsPerUnit, pointsPerUnit)
        }
        canvas.save()
        canvas.concat(matrix)
        // Photos and text under the ink, text in its paper colour.
        for (item in currentItems) if (item.cy >= top && item.cy < top + layout.pitch) currentPainter?.draw(canvas, item) { inkColor(it) }
        val onPage = strokes.filter { val middle = it.bounds.centerY(); middle >= top && middle < top + layout.pitch }
        drawStrokes(canvas, renderer, onPage, matrix)
        canvas.restore()
        inkMs += android.os.SystemClock.uptimeMillis() - inkStart
        document.finishPage(page)
      }
    } finally {
      source?.close()
      descriptor?.close()
    }
  }

  /**
   * Draws [strokes] in batches: each batch's missing strokes are built on several cores, drawn,
   * then let go, so a huge notebook never holds all its meshes at once.
   */
  private fun drawStrokes(canvas: android.graphics.Canvas, renderer: CanvasStrokeRenderer, strokes: List<InkStroke>, matrix: Matrix) {
    for (batch in strokes.chunked(BATCH)) {
      prebuild(batch)
      for (stroke in batch) renderer.draw(canvas, built(stroke), matrix)
    }
    prebuilt = emptyMap()
  }

  /** An endless canvas becomes one page just big enough for everything on it, with a margin. */
  private fun writeCanvas(document: PdfDocument, renderer: CanvasStrokeRenderer, strokes: List<InkStroke>) {
    val bounds = RectF()
    var first = true // not bounds.isEmpty: a zero-width first stroke would make every later one restart it
    for (stroke in strokes) {
      val r = stroke.bounds
      if (first) bounds.set(r) else bounds.union(r)
      first = false
    }
    if (first || bounds.isEmpty) bounds.set(0f, 0f, 595f, 842f)
    val margin = 40f
    bounds.inset(-margin, -margin)
    // Canvas units are screen pixels; scale so the page is at most 2000 points on its long side.
    val scale = minOf(1f, 2000f / maxOf(bounds.width(), bounds.height()))
    val page = document.startPage(PdfDocument.PageInfo.Builder((bounds.width() * scale).roundToInt(), (bounds.height() * scale).roundToInt(), 1).create())
    val canvas = page.canvas
    canvas.drawColor(paperColor)
    val matrix = Matrix().apply {
      setTranslate(-bounds.left, -bounds.top)
      postScale(scale, scale)
    }
    canvas.concat(matrix)
    for (item in currentItems) currentPainter?.draw(canvas, item) { inkColor(it) }
    drawStrokes(canvas, renderer, strokes, matrix)
    document.finishPage(page)
  }
}
