package dev.penlab.app.ink

import kotlin.math.hypot

/**
 * Line types for technical drawing (ISO 128 proportions, in multiples of the line width d):
 *  - dotted: dots 3d apart;
 *  - dashed (hidden edges): 12d dashes, 3d gaps;
 *  - centre line (axes, symmetry): 24d long dash, gap, dot, gap;
 *  - phantom (alternate positions): long dash, then two dots.
 *
 * A dashed line is stored as one ordinary stroke per dash or dot, made when the pen lifts, so
 * it saves, exports, erases and selects like any ink. Shapes (hold to perfect) and lines along
 * the ruler get the pattern too.
 */
enum class LineType(val label: String, internal val pattern: FloatArray?) {
  SOLID("Solid", null),
  DOTTED("Dotted", floatArrayOf(0f, 3f)),
  DASHED("Dashed", floatArrayOf(12f, 3f)),
  CENTRE("Centre", floatArrayOf(24f, 3f, 0f, 3f)),
  PHANTOM("Phantom", floatArrayOf(24f, 3f, 0f, 3f, 0f, 3f)),
}

object LinePattern {

  /**
   * Cuts [stroke] into the dashes and dots of [type]. [unit] is the line width d in page units
   * (callers keep it from getting so small that dashes vanish). Solid lines come back whole.
   */
  fun split(stroke: InkStroke, type: LineType, unit: Float): List<InkStroke> {
    val pattern = type.pattern ?: return listOf(stroke)
    val n = stroke.pointCount
    if (n < 2 || unit <= 0f) return listOf(stroke)
    val along = FloatArray(n)
    for (i in 1 until n) along[i] = along[i - 1] + hypot(stroke.x(i) - stroke.x(i - 1), stroke.y(i) - stroke.y(i - 1))
    val total = along[n - 1]
    if (total <= 0f) return listOf(stroke)

    val pieces = ArrayList<InkStroke>()
    var pos = 0f
    var k = 0
    var guard = 0
    while (pos <= total && guard++ < 100_000) {
      val length = pattern[k] * unit
      if (k % 2 == 0) pieces += piece(stroke, along, pos, minOf(pos + length, total))
      pos += length
      k = (k + 1) % pattern.size
    }
    return pieces
  }

  /** The part of the stroke between distances [from] and [to] along it (a dot if equal). */
  private fun piece(stroke: InkStroke, along: FloatArray, from: Float, to: Float): InkStroke {
    val f = InkStroke.FLOATS_PER_POINT
    val points = ArrayList<Float>()
    val times = ArrayList<Long>()
    fun addAt(distance: Float, nudge: Float = 0f) {
      var i = along.indexOfFirst { it >= distance }.let { if (it < 0) along.lastIndex else it }
      if (i == 0) i = 1
      val span = along[i] - along[i - 1]
      val t = if (span > 0f) ((distance - along[i - 1]) / span).coerceIn(0f, 1f) else 0f
      for (c in 0 until f) {
        val a = stroke.points[(i - 1) * f + c]
        val b = stroke.points[i * f + c]
        points += if (c >= 3 && (a < 0f || b < 0f)) a else a + (b - a) * t
      }
      points[points.size - f] += nudge
      times += stroke.times[i - 1] + ((stroke.times[i] - stroke.times[i - 1]) * t).toLong()
    }
    addAt(from)
    if (to <= from) {
      addAt(from, nudge = 0.01f) // a dot: two points almost on top of each other
    } else {
      for (i in along.indices) {
        if (along[i] > from && along[i] < to) {
          for (c in 0 until f) points += stroke.points[i * f + c]
          times += stroke.times[i]
        }
      }
      addAt(to)
    }
    val start = times.first()
    return InkStroke(
      stroke.brush,
      points.toFloatArray(),
      LongArray(times.size) { times[it] - start },
      stroke.toolType,
      stroke.unitLengthCm,
      stroke.isHighlighter,
      stroke.layer,
      stroke.mediaMark,
    )
  }
}
