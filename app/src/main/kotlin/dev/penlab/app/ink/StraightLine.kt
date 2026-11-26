package dev.penlab.app.ink

import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Straight lines as plain geometry (no Android or Ink types, so it is unit-tested on the laptop):
 * deciding that a selection is one straight line, moving one end of it, and the points of the
 * rebuilt line. The canvas turns the result back into strokes (see `InkCanvasView`).
 *
 * Coordinates are page units; a "xy" array is x0, y0, x1, y1, ...
 */
object StraightLine {

  /** Both ends of a line. [ax] and [ay] are the end nearer where the pen started, so a pattern keeps its phase. */
  data class Ends(val ax: Float, val ay: Float, val bx: Float, val by: Float) {
    val length: Float
      get() = hypot(bx - ax, by - ay)
  }

  /** How far points may stray from the line between the ends: 2% of its length, but at most 1.5 mm. */
  const val MAX_DEVIATION_FRACTION = 0.02f
  const val MAX_DEVIATION_MM = 1.5f

  /** Shorter than this is a tick, a dot or a letter stroke, not something worth line handles. */
  const val MIN_LENGTH_MM = 8f

  /** A rebuilt line never has more points than this (a very long line gets wider spacing). */
  const val MAX_POINTS = 4000

  /**
   * The ends of the line that [xy] (all the points of one stroke, or of every dash of one patterned
   * line) lie on, or null if they are not "obviously a line": too short, or any point further from
   * the segment between the ends than the tolerance above. [unitsPerMm] converts mm to page units.
   */
  fun detect(xy: FloatArray, unitsPerMm: Float): Ends? {
    val n = xy.size / 2
    if (n < 2 || unitsPerMm <= 0f) return null
    // The two points furthest apart. From any point the furthest one is an end of a line of
    // points, and the furthest from that one is the other end (exact when they are collinear).
    val first = farthestFrom(xy, xy[0], xy[1])
    val second = farthestFrom(xy, xy[first * 2], xy[first * 2 + 1])
    var ends = Ends(xy[first * 2], xy[first * 2 + 1], xy[second * 2], xy[second * 2 + 1])
    // Keep the direction the pen drew in: A is the end nearer the first point.
    if (hypot(ends.bx - xy[0], ends.by - xy[1]) < hypot(ends.ax - xy[0], ends.ay - xy[1])) {
      ends = Ends(ends.bx, ends.by, ends.ax, ends.ay)
    }
    val length = ends.length
    if (length < MIN_LENGTH_MM * unitsPerMm) return null
    val allowed = minOf(MAX_DEVIATION_FRACTION * length, MAX_DEVIATION_MM * unitsPerMm)
    for (i in 0 until n) {
      if (distanceToSegment(xy[i * 2], xy[i * 2 + 1], ends) > allowed) return null
    }
    return ends
  }

  /**
   * [ends] with end [which] (0 = A, 1 = B) put at (x, y): a longer, shorter or re-angled line. The
   * other end stays put. Null if the result would be shorter than [minLength] (the handle has
   * been dragged onto the other end), so the caller keeps showing the last good line.
   */
  fun moveEnd(ends: Ends, which: Int, x: Float, y: Float, minLength: Float): Ends? {
    val moved = if (which == 0) Ends(x, y, ends.bx, ends.by) else Ends(ends.ax, ends.ay, x, y)
    return if (moved.length < minLength) null else moved
  }

  /**
   * Points along the line from A to B, no further apart than [spacing] (like a shape that snaps:
   * enough points for the brush, at most [MAX_POINTS]). Both ends are exact.
   */
  fun pointsAlong(ends: Ends, spacing: Float): FloatArray {
    val steps = ceil(ends.length / spacing.coerceAtLeast(1e-3f)).toInt().coerceIn(1, MAX_POINTS - 1)
    val out = FloatArray((steps + 1) * 2)
    for (i in 0..steps) {
      val t = i / steps.toFloat()
      out[i * 2] = ends.ax + (ends.bx - ends.ax) * t
      out[i * 2 + 1] = ends.ay + (ends.by - ends.ay) * t
    }
    out[steps * 2] = ends.bx
    out[steps * 2 + 1] = ends.by
    return out
  }

  fun distanceToSegment(px: Float, py: Float, ends: Ends): Float {
    val dx = ends.bx - ends.ax
    val dy = ends.by - ends.ay
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0f) return hypot(px - ends.ax, py - ends.ay)
    val t = (((px - ends.ax) * dx + (py - ends.ay) * dy) / lengthSquared).coerceIn(0f, 1f)
    return hypot(px - (ends.ax + t * dx), py - (ends.ay + t * dy))
  }

  private fun farthestFrom(xy: FloatArray, x: Float, y: Float): Int {
    var best = 0
    var bestDistance = -1f
    for (i in 0 until xy.size / 2) {
      val d = hypot(xy[i * 2] - x, xy[i * 2 + 1] - y)
      if (d > bestDistance) {
        bestDistance = d
        best = i
      }
    }
    return best
  }
}

/**
 * Where the dashes and dots of a line type fall along a line of length [total]: pairs of (from, to)
 * distances, a dot being from == to. [pattern] is the line type's lengths in multiples of [unit]
 * (dash, gap, dash, gap...). Pure, so the dash layout is tested without strokes.
 */
internal fun dashRanges(total: Float, pattern: FloatArray, unit: Float): List<Pair<Float, Float>> {
  if (total <= 0f || unit <= 0f || pattern.isEmpty()) return emptyList()
  val ranges = ArrayList<Pair<Float, Float>>()
  var pos = 0f
  var k = 0
  var guard = 0
  while (pos <= total && guard++ < 100_000) {
    val length = pattern[k] * unit
    if (k % 2 == 0) ranges += pos to minOf(pos + length, total)
    pos += length
    k = (k + 1) % pattern.size
  }
  return ranges
}

/**
 * Strokes that belong together (the dashes of one patterned line share a non-zero group id): given
 * the [selected] ones, the other members of their groups found in [all]. Ungrouped (0) strokes
 * stand alone. Generic so it is tested without Ink.
 */
internal fun <T> withGroupMates(selected: Collection<T>, all: List<T>, groupOf: (T) -> Long): List<T> {
  val groups = HashSet<Long>()
  for (s in selected) groupOf(s).takeIf { it != 0L }?.let(groups::add)
  if (groups.isEmpty()) return emptyList()
  val have = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<T, Boolean>()).apply { addAll(selected) }
  return all.filter { groupOf(it) in groups && it !in have }
}
