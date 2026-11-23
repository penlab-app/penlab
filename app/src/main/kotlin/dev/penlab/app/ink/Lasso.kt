package dev.penlab.app.ink

import android.graphics.RectF

/**
 * The eight resize handles round a selection, as fractions of its box: four corners (resize in
 * proportion) and four edge midpoints (stretch one way). Each resizes about the opposite point.
 */
internal enum class ResizeHandle(val fx: Float, val fy: Float) {
  TOP_LEFT(0f, 0f),
  TOP(0.5f, 0f),
  TOP_RIGHT(1f, 0f),
  RIGHT(1f, 0.5f),
  BOTTOM_RIGHT(1f, 1f),
  BOTTOM(0.5f, 1f),
  BOTTOM_LEFT(0f, 1f),
  LEFT(0f, 0.5f);

  val isCorner: Boolean
    get() = fx != 0.5f && fy != 0.5f

  /** Left and right edges stretch sideways only; top and bottom up and down only. */
  val stretchesX: Boolean
    get() = fx != 0.5f

  val stretchesY: Boolean
    get() = fy != 0.5f
}

/**
 * True if at least a fifth of the stroke's points lie inside the loop [lasso] (page units).
 * Not all: a loop drawn quickly round a word often clips a letter's tail; a fifth since 14 Nov
 * (user request: a stroke the loop only partly covers should be selected too). Not "any point", so a
 * neighbouring line the loop just grazes stays out.
 */
internal fun InkStroke.isMostlyInside(lasso: List<Point>, lassoBounds: RectF): Boolean {
  if (!RectF.intersects(lassoBounds, bounds)) return false
  val n = pointCount
  if (n == 0) return false
  val step = (n / MAX_SAMPLES).coerceAtLeast(1) // long strokes: test a sample
  var tested = 0
  var inside = 0
  var i = 0
  while (i < n) {
    tested++
    if (contains(lasso, x(i), y(i))) inside++
    i += step
  }
  return inside * 5 >= tested
}

/** Point in polygon, by counting how many edges a ray to the right crosses. */
internal fun contains(polygon: List<Point>, x: Float, y: Float): Boolean {
  var inside = false
  var j = polygon.lastIndex
  for (i in polygon.indices) {
    val a = polygon[i]
    val b = polygon[j]
    if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
    j = i
  }
  return inside
}

private const val MAX_SAMPLES = 40

/**
 * True when the lasso was drawn as a line rather than a loop: its two ends are far apart compared
 * with its size (user request: "if I draw only one line and not a closed area it should also
 * select"). A line selects everything it crosses instead of what it surrounds.
 */
internal fun isOpenLine(path: List<Point>, bounds: RectF): Boolean {
  if (path.size < 2) return false
  val gap = kotlin.math.hypot(path.last().x - path.first().x, path.last().y - path.first().y)
  if (gap <= kotlin.math.hypot(bounds.width(), bounds.height()) * 0.5f) return false
  // A loop left open (a "C") still travels much further than its ends are apart; a line, even a
  // wavy one, barely does (user request: the lasso sometimes refused to close).
  var length = 0f
  for (i in 1 until path.size) length += kotlin.math.hypot(path[i].x - path[i - 1].x, path[i].y - path[i - 1].y)
  return length < gap * OPEN_LINE_MAX_DETOUR
}

/** How much longer than its end-to-end gap a drawn path may be and still count as a line. */
internal const val OPEN_LINE_MAX_DETOUR = 1.6f

/** True if the stroke passes within [tolerance] of the drawn line [line] (page units). */
internal fun InkStroke.crossesLine(line: List<Point>, lineBounds: RectF, tolerance: Float): Boolean {
  val b = bounds
  if (b.right + tolerance < lineBounds.left || b.left - tolerance > lineBounds.right ||
    b.bottom + tolerance < lineBounds.top || b.top - tolerance > lineBounds.bottom) return false
  val n = pointCount
  if (n == 1) return distanceToPath(line, x(0), y(0)) <= tolerance
  for (i in 1 until n) {
    val ax = x(i - 1); val ay = y(i - 1); val bx = x(i); val by = y(i)
    for (k in 1 until line.size) {
      val p = line[k - 1]; val q = line[k]
      if (segmentsCross(ax, ay, bx, by, p.x, p.y, q.x, q.y)) return true
    }
    if (distanceToPath(line, bx, by) <= tolerance) return true
  }
  return distanceToPath(line, x(0), y(0)) <= tolerance
}

/** True if the loop covers any part of [rect]: a corner inside the loop, or the loop inside or across the box. */
internal fun loopTouchesRect(loop: List<Point>, rect: RectF): Boolean {
  if (contains(loop, rect.centerX(), rect.centerY())) return true
  for ((x, y) in listOf(rect.left to rect.top, rect.right to rect.top, rect.right to rect.bottom, rect.left to rect.bottom)) {
    if (contains(loop, x, y)) return true
  }
  if (loop.any { rect.contains(it.x, it.y) }) return true
  return pathCrossesRect(loop + loop.first(), rect)
}

/** True if the drawn line passes through (or within [tolerance] of) [rect]. */
internal fun lineTouchesRect(line: List<Point>, rect: RectF, tolerance: Float): Boolean {
  val grown = RectF(rect).apply { inset(-tolerance, -tolerance) }
  return line.any { grown.contains(it.x, it.y) } || pathCrossesRect(line, grown)
}

private fun pathCrossesRect(path: List<Point>, r: RectF): Boolean {
  val edges = arrayOf(
    floatArrayOf(r.left, r.top, r.right, r.top), floatArrayOf(r.right, r.top, r.right, r.bottom),
    floatArrayOf(r.right, r.bottom, r.left, r.bottom), floatArrayOf(r.left, r.bottom, r.left, r.top),
  )
  for (k in 1 until path.size) {
    val p = path[k - 1]; val q = path[k]
    for (e in edges) if (segmentsCross(p.x, p.y, q.x, q.y, e[0], e[1], e[2], e[3])) return true
  }
  return false
}

private fun segmentsCross(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float): Boolean {
  fun side(px: Float, py: Float, qx: Float, qy: Float, rx: Float, ry: Float) = (qx - px) * (ry - py) - (qy - py) * (rx - px)
  val d1 = side(cx, cy, dx, dy, ax, ay)
  val d2 = side(cx, cy, dx, dy, bx, by)
  val d3 = side(ax, ay, bx, by, cx, cy)
  val d4 = side(ax, ay, bx, by, dx, dy)
  return ((d1 > 0) != (d2 > 0)) && ((d3 > 0) != (d4 > 0))
}

private fun distanceToPath(path: List<Point>, x: Float, y: Float): Float {
  var best = Float.MAX_VALUE
  if (path.size == 1) return kotlin.math.hypot(x - path[0].x, y - path[0].y)
  for (k in 1 until path.size) {
    val p = path[k - 1]; val q = path[k]
    val vx = q.x - p.x; val vy = q.y - p.y
    val len2 = vx * vx + vy * vy
    val t = if (len2 == 0f) 0f else (((x - p.x) * vx + (y - p.y) * vy) / len2).coerceIn(0f, 1f)
    best = minOf(best, kotlin.math.hypot(x - (p.x + t * vx), y - (p.y + t * vy)))
  }
  return best
}
