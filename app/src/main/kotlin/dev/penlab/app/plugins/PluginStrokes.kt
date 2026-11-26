package dev.penlab.app.plugins

import androidx.ink.brush.InputToolType
import androidx.ink.strokes.StrokeInput
import dev.penlab.app.ink.Brushes
import dev.penlab.app.ink.InkStroke
import dev.penlab.app.ink.PenBrushes
import dev.penlab.plugin.PluginStroke
import kotlin.math.max

/**
 * Turns the strokes a plugin returns (plain data, millimetres of paper) into the app's own
 * [InkStroke]s: the same objects the pen makes, so they save in the normal page file and erase,
 * select, move and are searched like handwriting. The numbers are worked out in [pack] (plain
 * Kotlin, unit-tested); [toInk] only adds the brush.
 */
object PluginStrokes {
  /** Smallest and largest pen width accepted (millimetres of paper): Ink rejects a zero size, and a plugin cannot paint the whole page. */
  const val MIN_WIDTH_MM = 0.05f
  const val MAX_WIDTH_MM = 50f

  /** Ink's "no value" for pressure, tilt and orientation (all -1 in Ink 1.0). */
  const val NONE = -1f

  /** The opacity PenLab's own highlighters have (the 0x66 of their colours). */
  const val HIGHLIGHT_ALPHA = 0x66

  /** Milliseconds between points when a plugin gave no times: a calm, even pace. */
  private const val EVEN_PACE_MS = 8L

  /** Simplification detail of an imported stroke (page units; matches what the pen asks of Ink at 10 units per mm). */
  private const val DETAIL = 0.1f

  /** A stroke's numbers in the layout [InkStroke] keeps: `FLOATS_PER_POINT` floats per point, and the time of each point. */
  class Packed(val points: FloatArray, val times: LongArray)

  /**
   * [s]'s points in page units: millimetres times [unitsPerMm], moved by ([dx], [dy]). A point whose
   * x or y is not a finite number is dropped. Pressure is clamped to 0..1 (a negative one means "none");
   * times never go backwards and are invented at an even pace if the plugin sent none. Null if no point is left.
   */
  fun pack(s: PluginStroke, unitsPerMm: Float, dx: Float = 0f, dy: Float = 0f): Packed? {
    val n = s.pointCount
    if (s.ys.size != n) return null
    val keep = (0 until n).filter { s.xs[it].isFinite() && s.ys[it].isFinite() }
    if (keep.isEmpty()) return null
    val f = InkStroke.FLOATS_PER_POINT
    val points = FloatArray(keep.size * f)
    val times = LongArray(keep.size)
    var last = 0L
    keep.forEachIndexed { j, i ->
      val b = j * f
      points[b] = s.xs[i] * unitsPerMm + dx
      points[b + 1] = s.ys[i] * unitsPerMm + dy
      val pressure = if (i < s.pressures.size) s.pressures[i] else NONE
      points[b + 2] = if (pressure.isFinite() && pressure >= 0f) pressure.coerceAtMost(1f) else NONE
      points[b + 3] = NONE
      points[b + 4] = NONE
      last = if (s.times.size == n) max(last, s.times[i]) else j * EVEN_PACE_MS
      times[j] = last
    }
    return Packed(points, times)
  }

  /** [argb] for a stroke of [family]: a highlighter given with no transparency is drawn at PenLab's 40 %. */
  fun colorFor(family: String, argb: Int): Int = if (family == "highlighter" && (argb ushr 24) == 0xFF) (argb and 0x00FFFFFF) or (HIGHLIGHT_ALPHA shl 24) else argb

  /** The pen width in page units. */
  fun widthUnits(widthMm: Float, unitsPerMm: Float): Float = (if (widthMm.isFinite()) widthMm else MIN_WIDTH_MM).coerceIn(MIN_WIDTH_MM, MAX_WIDTH_MM) * unitsPerMm

  /** The layer a stroke goes on: its own (clamped to the notebook's layers) or [defaultLayer] when it has none. */
  fun layerFor(requested: Int, defaultLayer: Int, layerCount: Int): Int = (if (requested >= 0) requested else defaultLayer).coerceIn(0, (layerCount - 1).coerceAtLeast(0))

  /**
   * The [InkStroke] for [s], or null if it has no usable point. [unitsPerMm] is how many page units make a
   * millimetre (10 on A4 notebooks); ([dx], [dy]) move it in page units.
   */
  fun toInk(s: PluginStroke, unitsPerMm: Float, dx: Float, dy: Float, defaultLayer: Int, layerCount: Int): InkStroke? {
    val packed = pack(s, unitsPerMm, dx, dy) ?: return null
    val family = if (s.tool == dev.penlab.plugin.Tool.HIGHLIGHTER) "highlighter" else s.family
    val brush = Brushes.get(PenBrushes.familyById(family), colorFor(family, s.argb), widthUnits(s.widthMm, unitsPerMm), DETAIL)
    return InkStroke(
      brush, packed.points, packed.times, InputToolType.STYLUS, StrokeInput.NO_STROKE_UNIT_LENGTH,
      isHighlighter = family == "highlighter", layer = layerFor(s.layer, defaultLayer, layerCount),
    )
  }

  /**
   * Strokes in the order the page keeps them (layer 0 at the bottom; within a layer the highlights go under
   * the ink), which is what [dev.penlab.app.ink.InkCanvasView]'s insertion assumes. Stable, so a plugin's own
   * order is kept among strokes of one kind.
   */
  fun inDrawOrder(strokes: List<InkStroke>): List<InkStroke> = strokes.sortedWith(compareBy<InkStroke> { it.layer }.thenBy { !it.isHighlighter })

  /** The area the strokes cover in millimetres as left, top, right, bottom (their points), or null if there are none. */
  fun boundsMm(strokes: List<PluginStroke>): FloatArray? {
    var box: FloatArray? = null
    for (s in strokes) {
      for (i in 0 until s.pointCount) {
        val x = s.xs[i]
        val y = s.ys[i]
        if (!x.isFinite() || !y.isFinite()) continue
        val b = box
        if (b == null) {
          box = floatArrayOf(x, y, x, y)
        } else {
          b[0] = minOf(b[0], x)
          b[1] = minOf(b[1], y)
          b[2] = maxOf(b[2], x)
          b[3] = maxOf(b[3], y)
        }
      }
    }
    return box
  }
}
