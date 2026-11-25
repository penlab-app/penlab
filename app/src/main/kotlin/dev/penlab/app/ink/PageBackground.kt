package dev.penlab.app.ink

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** What is printed on the paper under the ink. */
enum class PageStyle(val label: String) {
  BLANK("Blank"),
  LINED("Lined"),
  DOTTED("Dotted"),
  /** 5 mm squares. */
  GRID("Grid"),
  /** 5 mm squares with a heavier line every 25 mm, like an engineering pad. */
  ENGINEERING("Engineering"),
  /** 1 mm graph paper, heavier every 5 mm and every 10 mm. */
  GRAPH("Graph"),
  /**
   * Cornell notes: a title band at the top, a cue column on the left for questions and key
   * words, the notes on the right, and a summary box at the bottom; all lined.
   */
  CORNELL("Cornell"),
  /** Isometric grid (lines at 30°, 150° and upright, 5 mm apart) for 3D sketches. */
  ISOMETRIC("Isometric"),
  /**
   * A light 5 mm grid for worked problems, tables and graphs, a band at the top for a heading, and a
   * margin on the left for notes.
   */
  CP("Worksheet"),
}

/**
 * Draws the paper's ruling (lines or dots) for the part of the page that is on screen.
 *
 * The ruling is part of the page, so it pans and zooms with the ink. Only what is visible is
 * drawn, and when zoomed far out every second (fourth, ...) line or dot is skipped so the
 * screen never fills with thousands of marks.
 *
 * [unitsPerDp] is how many page units one dp of ruling spacing takes: screen pixels per dp on
 * an infinite canvas, a fixed paper scale in an A4 notebook (so ruling is the same on every
 * device and in exports).
 */
class PageBackground(unitsPerDp: Float, darkPage: Boolean) {

  private val lineSpacing = LINE_SPACING_DP * unitsPerDp
  private val dotSpacing = DOT_SPACING_DP * unitsPerDp
  private val dotSize = DOT_SIZE_DP * unitsPerDp

  /** Millimetres in page units (dp are 1/160 inch). Exact on notebook paper. */
  private val mm = unitsPerDp * 160f / 25.4f

  private val minorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = InkColors.onScreen(MINOR_PAPER_COLOR, darkPage) }
  private val majorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = InkColors.onScreen(RULING_PAPER_COLOR, darkPage) }

  private val paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = InkColors.onScreen(RULING_PAPER_COLOR, darkPage)
      strokeCap = Paint.Cap.ROUND
    }

  /** Updates ruling paints when switching between light and dark paper live. */
  fun setDarkPage(darkPage: Boolean) {
    minorPaint.color = InkColors.onScreen(MINOR_PAPER_COLOR, darkPage)
    majorPaint.color = InkColors.onScreen(RULING_PAPER_COLOR, darkPage)
    paint.color = InkColors.onScreen(RULING_PAPER_COLOR, darkPage)
  }

  /** Reused between frames so drawing allocates nothing. */
  private var points = FloatArray(0)

  /** Call with [canvas] already transformed to page coordinates. */
  fun draw(canvas: Canvas, style: PageStyle, visiblePage: RectF, zoom: Float) {
    when (style) {
      PageStyle.BLANK -> Unit
      PageStyle.LINED -> drawLines(canvas, visiblePage, zoom)
      PageStyle.DOTTED -> drawDots(canvas, visiblePage, zoom)
      PageStyle.GRID -> drawGrid(canvas, visiblePage, zoom, step = spacingUnits(style), majorEvery = 0, midEvery = 0)
      PageStyle.ENGINEERING -> drawGrid(canvas, visiblePage, zoom, step = spacingUnits(style), majorEvery = 5, midEvery = 0)
      PageStyle.GRAPH -> drawGrid(canvas, visiblePage, zoom, step = 1 * mm, majorEvery = 10, midEvery = 5)
      PageStyle.CORNELL -> drawCornell(canvas, visiblePage, zoom)
      PageStyle.ISOMETRIC -> drawIsometric(canvas, visiblePage, zoom)
      PageStyle.CP -> drawCp(canvas, visiblePage, zoom)
    }
  }

  /**
   * Spacing chosen for this notebook, in millimetres: line pitch, dot pitch, grid square or
   * isometric triangle side (0 = the paper's usual spacing, [defaultSpacingMm]).
   */
  var spacingMm = 0f

  private fun spacingUnits(style: PageStyle): Float = (if (spacingMm > 0f) spacingMm else defaultSpacingMm(style)) * mm

  /** The page's size in page units, for layouts that depend on it (Cornell). 0 = endless. */
  var pageWidth = 0f
  var pageHeight = 0f

  private fun drawCp(canvas: Canvas, visible: RectF, zoom: Float) {
    drawGrid(canvas, visible, zoom, step = spacingUnits(PageStyle.CP), majorEvery = 0, midEvery = 1000)
    val w = if (pageWidth > 0f) pageWidth else 210 * mm
    val band = 25 * mm
    val margin = 25 * mm
    majorPaint.strokeWidth = MIN_MARK_PX * 1.6f / zoom
    canvas.drawLine(maxOf(visible.left, 0f), band, minOf(visible.right, w), band, majorPaint)
    canvas.drawLine(margin, maxOf(visible.top, band), margin, visible.bottom, majorPaint)
  }

  private fun drawCornell(canvas: Canvas, visible: RectF, zoom: Float) {
    drawLines(canvas, visible, zoom)
    val w = if (pageWidth > 0f) pageWidth else 210 * mm
    val h = if (pageHeight > 0f) pageHeight else Float.MAX_VALUE
    val title = 22 * mm
    val cue = minOf(63 * mm, w * 0.3f) // the classic 2.5-inch cue column
    val summary = if (h < Float.MAX_VALUE) h - minOf(50 * mm, h * 0.2f) else Float.MAX_VALUE
    majorPaint.strokeWidth = MIN_MARK_PX * 1.6f / zoom
    canvas.drawLine(maxOf(visible.left, 0f), title, minOf(visible.right, w), title, majorPaint)
    canvas.drawLine(cue, maxOf(visible.top, title), cue, minOf(visible.bottom, summary), majorPaint)
    if (summary < Float.MAX_VALUE) canvas.drawLine(maxOf(visible.left, 0f), summary, minOf(visible.right, w), summary, majorPaint)
  }

  /**
   * A true isometric grid: equilateral triangles with 5 mm sides, made of upright lines and lines
   * at +30° and -30°, all three passing through the same points, so a cube's edges lie on the grid.
   * For triangle side s: upright lines are s·cos30° apart, and each sloped family steps s up the
   * page (that keeps every crossing on the same lattice).
   */
  private fun drawIsometric(canvas: Canvas, visible: RectF, zoom: Float) {
    var side = spacingUnits(PageStyle.ISOMETRIC)
    while (side * zoom < MIN_GAP_PX) side *= 2f
    majorPaint.strokeWidth = MIN_MARK_PX / zoom
    val cos30 = 0.8660254f
    val tan30 = 0.57735027f
    val dx = side * cos30
    var x = floor(visible.left / dx) * dx
    while (x <= visible.right) {
      canvas.drawLine(x, visible.top, x, visible.bottom, majorPaint)
      x += dx
    }
    for (sign in listOf(1f, -1f)) {
      // Lines y = sign·tan30·x + c, with c a whole number of sides.
      val cMin = minOf(visible.top - sign * tan30 * visible.left, visible.top - sign * tan30 * visible.right)
      val cMax = maxOf(visible.bottom - sign * tan30 * visible.left, visible.bottom - sign * tan30 * visible.right)
      var c = floor(cMin / side) * side
      var guard = 0
      while (c <= cMax && guard++ < 4000) {
        canvas.drawLine(visible.left, sign * tan30 * visible.left + c, visible.right, sign * tan30 * visible.right + c, majorPaint)
        c += side
      }
    }
  }


  /**
   * Squares of [step], with every [midEvery]-th line a little stronger and every [majorEvery]-th
   * line strongest (0 = none). Lines stay hairlines on screen, and fine lines are left out when
   * they would be closer than [MIN_GAP_PX] on screen (zoomed far out).
   */
  private fun drawGrid(canvas: Canvas, visible: RectF, zoom: Float, step: Float, majorEvery: Int, midEvery: Int) {
    val hairline = MIN_MARK_PX / zoom
    val showMinor = step * zoom >= MIN_GAP_PX
    val showMid = midEvery > 0 && step * midEvery * zoom >= MIN_GAP_PX
    fun paintFor(index: Int): Paint? =
      when {
        majorEvery > 0 && index % majorEvery == 0 -> majorPaint.also { it.strokeWidth = hairline * 1.4f }
        showMid && index % midEvery == 0 -> majorPaint.also { it.strokeWidth = hairline }
        showMinor -> (if (majorEvery == 0 && midEvery == 0) majorPaint else minorPaint).also { it.strokeWidth = hairline }
        else -> null
      }
    if (!showMinor && !showMid && majorEvery > 0 && step * majorEvery * zoom < MIN_GAP_PX) return
    val first = Math.floor((visible.left / step).toDouble()).toInt()
    val last = Math.ceil((visible.right / step).toDouble()).toInt()
    for (i in first..last) paintFor(i)?.let { canvas.drawLine(i * step, visible.top, i * step, visible.bottom, it) }
    val top = Math.floor((visible.top / step).toDouble()).toInt()
    val bottom = Math.ceil((visible.bottom / step).toDouble()).toInt()
    for (j in top..bottom) paintFor(j)?.let { canvas.drawLine(visible.left, j * step, visible.right, j * step, it) }
  }

  private fun drawLines(canvas: Canvas, visible: RectF, zoom: Float) {
    val step = spacingFor(spacingUnits(PageStyle.LINED), zoom)
    paint.strokeWidth = MIN_MARK_PX / zoom // always a hairline on screen
    var y = ceil(visible.top / step) * step
    while (y <= visible.bottom) {
      canvas.drawLine(visible.left, y, visible.right, y, paint)
      y += step
    }
  }

  private fun drawDots(canvas: Canvas, visible: RectF, zoom: Float) {
    val step = spacingFor(spacingUnits(PageStyle.DOTTED), zoom)
    paint.strokeWidth = max(dotSize, MIN_MARK_PX / zoom)

    val firstX = ceil(visible.left / step) * step
    val firstY = ceil(visible.top / step) * step
    val columns = floor((visible.right - firstX) / step).toInt() + 1
    val rows = floor((visible.bottom - firstY) / step).toInt() + 1
    if (columns <= 0 || rows <= 0) return

    val needed = columns * rows * 2
    if (points.size < needed) points = FloatArray(needed)
    var i = 0
    for (row in 0 until rows) {
      val y = firstY + row * step
      for (column in 0 until columns) {
        points[i++] = firstX + column * step
        points[i++] = y
      }
    }
    canvas.drawPoints(points, 0, needed, paint)
  }

  /** Doubles the spacing until marks are at least [MIN_GAP_PX] apart on screen. */
  private fun spacingFor(spacing: Float, zoom: Float): Float {
    var step = spacing
    while (step * zoom < MIN_GAP_PX) step *= 2f
    return step
  }

  companion object {
    /** The usual spacing of each paper, in millimetres (what a new notebook gets). */
    fun defaultSpacingMm(style: PageStyle): Float =
      when (style) {
        PageStyle.LINED, PageStyle.CORNELL -> LINE_SPACING_DP * 25.4f / 160f
        PageStyle.DOTTED -> 8f // 8 mm dots by default
        PageStyle.GRAPH -> 1f
        else -> 5f
      }

    /** Spacing choices offered for each paper (mm), or empty if it has none (blank, graph). */
    fun spacingPresetsMm(style: PageStyle): List<Float> =
      when (style) {
        PageStyle.LINED, PageStyle.CORNELL -> listOf(6f, 7f, 8f, 10f)
        PageStyle.DOTTED -> listOf(5f, 6f, 8f, 10f)
        PageStyle.GRID, PageStyle.ENGINEERING, PageStyle.ISOMETRIC -> listOf(4f, 5f, 7f, 10f)
        else -> emptyList()
      }

    /**
     * Light grey on paper; shown inverted (a dark grey a little lighter than the page) on the
     * dark page. Opaque on purpose: see-through colours are faded on the dark page.
     */
    const val RULING_PAPER_COLOR = 0xFFB4B8BF.toInt()

    /** Fine grid lines: lighter than the ruling. */
    const val MINOR_PAPER_COLOR = 0xFFDCDFE3.toInt()

    /** Line pitch: about 8 mm on a typical tablet, a normal ruled notebook. */
    private const val LINE_SPACING_DP = 40f

    /** Dot pitch: 8 mm (50.4 dp at 160 dp per inch). */
    private const val DOT_SPACING_DP = 8f * 160f / 25.4f
    private const val DOT_SIZE_DP = 2.6f // a bit bigger than the first 1.8

    private const val MIN_GAP_PX = 14f
    private const val MIN_MARK_PX = 1.5f
  }
}
