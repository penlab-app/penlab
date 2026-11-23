package dev.penlab.app.ink

import androidx.ink.strokes.StrokeInput
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Cuts the part of a stroke that lies under the eraser's path, keeping the rest as separate
 * strokes with the same brush.
 *
 * Points are first filled in so none are further apart than half the cut width (a fast line
 * has few points, and a gap between two far-apart points must still be cut). Then every point
 * closer than [reach] to the eraser's path (the segment a→b) is dropped, and each run of at
 * least two remaining points becomes a piece.
 */
object PartialEraser {

  /** The pieces left, or null if the path does not come within [reach] of the stroke. */
  fun cut(stroke: InkStroke, ax: Float, ay: Float, bx: Float, by: Float, reach: Float): List<InkStroke>? {
    val (points, times) = filledIn(stroke, maxStep = reach / 2f)
    val n = times.size
    val f = InkStroke.FLOATS_PER_POINT
    val keep = BooleanArray(n) { distanceToSegment(points[it * f], points[it * f + 1], ax, ay, bx, by) > reach }
    if (keep.all { it }) return null

    val pieces = ArrayList<InkStroke>()
    var runStart = -1
    for (i in 0..n) {
      val kept = i < n && keep[i]
      if (kept && runStart < 0) runStart = i
      if (!kept && runStart >= 0) {
        if (i - runStart >= 2) {
          // Each piece's time starts at 0, like a freshly drawn stroke.
          val first = times[runStart]
          pieces += InkStroke(
            stroke.brush,
            points.copyOfRange(runStart * f, i * f),
            LongArray(i - runStart) { times[runStart + it] - first },
            stroke.toolType,
            stroke.unitLengthCm,
            stroke.isHighlighter,
            stroke.layer,
            stroke.mediaMark,
          )
        }
        runStart = -1
      }
    }
    return pieces
  }

  /** The stroke's points with extra ones in between wherever two are more than [maxStep] apart. */
  private fun filledIn(stroke: InkStroke, maxStep: Float): Pair<FloatArray, LongArray> {
    val f = InkStroke.FLOATS_PER_POINT
    val src = stroke.points
    val outPoints = ArrayList<Float>(src.size * 2)
    val outTimes = ArrayList<Long>(stroke.pointCount * 2)
    for (i in 0 until stroke.pointCount) {
      if (i > 0 && maxStep > 0f) {
        val gap = hypot(src[i * f] - src[(i - 1) * f], src[i * f + 1] - src[(i - 1) * f + 1])
        if (gap > maxStep) {
          val steps = ceil(gap / maxStep).toInt()
          for (k in 1 until steps) {
            val t = k / steps.toFloat()
            for (c in 0 until f) {
              val a = src[(i - 1) * f + c]
              val b = src[i * f + c]
              // Keep Ink's "no value" markers as they are; blend real values.
              val missing = c >= 2 && (a == StrokeInput.NO_PRESSURE || a == StrokeInput.NO_TILT || a == StrokeInput.NO_ORIENTATION)
              outPoints += if (missing || c == 4) a else a + (b - a) * t
            }
            outTimes += stroke.times[i - 1] + ((stroke.times[i] - stroke.times[i - 1]) * t).toLong()
          }
        }
      }
      for (c in 0 until f) outPoints += src[i * f + c]
      outTimes += stroke.times[i]
    }
    return outPoints.toFloatArray() to outTimes.toLongArray()
  }

  private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0f) return hypot(px - ax, py - ay)
    val t = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
  }
}
