package dev.penlab.app.ink

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A point on the page. */
data class Point(val x: Float, val y: Float)

/**
 * A clean shape that replaces a hand-drawn one. [points] is the outline to draw, in order;
 * for closed shapes the last point repeats the first.
 */
data class RecognizedShape(val kind: Kind, val points: List<Point>) {
  enum class Kind { LINE, ARC, TRIANGLE, RECTANGLE, SQUARE, ELLIPSE, CIRCLE }
}

/**
 * Decides whether a hand-drawn stroke was meant as a line, arc, triangle, rectangle or ellipse,
 * and returns the clean version, or null for anything else (handwriting stays as it is).
 *
 * The tests run in order, cheapest first:
 *  1. Line: the path is nearly as short as the straight distance between its ends.
 *  2. Arc: the ends stay apart and the path follows a circle through its start, middle and end.
 *  3. Closed shapes: the ends meet (an overshoot past the start is trimmed off). The outline is
 *     simplified to its corners; 3 corners make a triangle, 4 a rectangle.
 *  4. Otherwise, a closed path that stays close to an ellipse through its bounding box.
 *
 * Nearly-right shapes are tidied the way you probably meant them: lines within a few degrees
 * of horizontal, vertical or 45 degrees become exact, a rectangle with nearly equal sides
 * becomes a square, an ellipse with nearly equal axes becomes a circle.
 *
 * Pure logic with no Android types, so it is unit-tested on the laptop.
 */
object ShapeRecognizer {

  fun recognize(stroke: List<Point>): RecognizedShape? {
    if (stroke.size < 3) return null
    val length = pathLength(stroke)
    val minX = stroke.minOf { it.x }
    val maxX = stroke.maxOf { it.x }
    val minY = stroke.minOf { it.y }
    val maxY = stroke.maxOf { it.y }
    val diagonal = hypot(maxX - minX, maxY - minY)
    if (length <= 0f || diagonal <= 0f) return null

    val start = stroke.first()
    val end = stroke.last()
    val gap = distance(start, end)

    // 1. Line.
    if (gap / length > LINE_STRAIGHTNESS && maxDistanceToSegment(stroke, start, end) < LINE_WOBBLE * gap) {
      return RecognizedShape(RecognizedShape.Kind.LINE, listOf(start, snapLineEnd(start, end)))
    }

    // Where the pen came back closest to its start, near the end of the stroke. Anything drawn
    // after that is an overshoot (very common when closing a shape quickly) and is ignored.
    val closure = closurePoint(stroke)
    val closedGap = distance(start, stroke[closure])

    // 2. Arc: open, and close to a circle.
    if (closedGap > CLOSED_GAP * length) return arc(stroke, length)

    // 3 and 4: closed shapes.
    val outline = stroke.subList(0, closure + 1)
    val corners = corners(outline, diagonal)
    val polygon =
      when (corners.size) {
        3 -> RecognizedShape(RecognizedShape.Kind.TRIANGLE, closed(corners))
        4 -> rectangle(corners)
        else -> null
      }
    if (polygon != null && fitsOutline(outline, polygon.points, POLYGON_FIT * diagonal)) return polygon

    return ellipse(outline, outline.minOf { it.x }, outline.maxOf { it.x }, outline.minOf { it.y }, outline.maxOf { it.y })
  }

  /** Index of the point in the last part of the stroke that is closest to its start. */
  private fun closurePoint(stroke: List<Point>): Int {
    val start = stroke.first()
    val from = (stroke.size * (1 - CLOSURE_SEARCH)).toInt().coerceAtLeast(1)
    var best = stroke.lastIndex
    var bestDistance = distance(start, stroke.last())
    for (i in from until stroke.size) {
      val d = distance(start, stroke[i])
      if (d < bestDistance) {
        best = i
        bestDistance = d
      }
    }
    return best
  }

  // ---- Arc -----------------------------------------------------------------------------

  /** The circular arc through the stroke's start, middle and end, if the stroke follows it. */
  private fun arc(stroke: List<Point>, length: Float): RecognizedShape? {
    val start = stroke.first()
    val end = stroke.last()
    val middle = pointAtDistance(stroke, length / 2)
    val center = circumcenter(start, middle, end) ?: return null
    val radius = distance(center, start)
    val chord = distance(start, end)

    val error = stroke.map { abs(distance(center, it) - radius) }.average()
    if (error > ARC_FIT * chord) return null

    // Sweep from the start angle to the end angle, in the direction that passes the middle.
    val twoPi = (2 * PI).toFloat()
    fun turn(angle: Float) = ((angle % twoPi) + twoPi) % twoPi
    val a0 = atan2(start.y - center.y, start.x - center.x)
    val a1 = atan2(middle.y - center.y, middle.x - center.x)
    val a2 = atan2(end.y - center.y, end.x - center.x)
    val forward = turn(a2 - a0)
    val sweep = if (turn(a1 - a0) < forward) forward else forward - twoPi
    if (abs(sweep) > MAX_ARC_SWEEP) return null

    val segments = (abs(sweep) / ARC_STEP).toInt().coerceAtLeast(4)
    val points = List(segments + 1) { i ->
      val angle = a0 + sweep * i / segments
      Point(center.x + radius * cos(angle), center.y + radius * sin(angle))
    }
    return RecognizedShape(RecognizedShape.Kind.ARC, points)
  }

  private fun pointAtDistance(stroke: List<Point>, target: Float): Point {
    var travelled = 0f
    for ((a, b) in stroke.zipWithNext()) {
      val step = distance(a, b)
      if (travelled + step >= target && step > 0f) {
        val t = (target - travelled) / step
        return Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
      }
      travelled += step
    }
    return stroke.last()
  }

  /** Centre of the circle through three points, or null if they are in a straight line. */
  private fun circumcenter(a: Point, b: Point, c: Point): Point? {
    val d = 2 * (a.x * (b.y - c.y) + b.x * (c.y - a.y) + c.x * (a.y - b.y))
    if (abs(d) < 1e-3f) return null
    val a2 = a.x * a.x + a.y * a.y
    val b2 = b.x * b.x + b.y * b.y
    val c2 = c.x * c.x + c.y * c.y
    return Point(
      (a2 * (b.y - c.y) + b2 * (c.y - a.y) + c2 * (a.y - b.y)) / d,
      (a2 * (c.x - b.x) + b2 * (a.x - c.x) + c2 * (b.x - a.x)) / d,
    )
  }

  // ---- Line ----------------------------------------------------------------------------

  /** Moves the end so the line is exactly horizontal, vertical or diagonal when it is close. */
  private fun snapLineEnd(start: Point, end: Point): Point {
    val angle = atan2(end.y - start.y, end.x - start.x)
    val step = (PI / 4).toFloat()
    val nearest = (angle / step).roundToInt() * step
    if (abs(angle - nearest) > LINE_ANGLE_SNAP) return end
    val length = distance(start, end)
    return Point(start.x + cos(nearest) * length, start.y + sin(nearest) * length)
  }

  // ---- Polygons ------------------------------------------------------------------------

  /**
   * The outline's corners: the path is simplified (Ramer-Douglas-Peucker), then points where
   * the outline barely turns are dropped, so a stroke that starts mid-edge does not count its
   * starting point as a corner.
   */
  internal fun corners(stroke: List<Point>, diagonal: Float): List<Point> {
    val closedPath = stroke + stroke.first()
    var simplified = simplify(closedPath, CORNER_TOLERANCE * diagonal).dropLast(1)
    var changed = true
    while (changed && simplified.size > 2) {
      changed = false
      for (i in simplified.indices) {
        val previous = simplified[(i - 1 + simplified.size) % simplified.size]
        val next = simplified[(i + 1) % simplified.size]
        if (turnDegrees(previous, simplified[i], next) < MIN_CORNER_TURN_DEGREES) {
          // Barely turns: not a corner.
          simplified = simplified.filterIndexed { index, _ -> index != i }
          changed = true
          break
        }
        if (distance(simplified[i], next) < CORNER_MERGE * diagonal) {
          // Two corners right next to each other are one rounded corner drawn in two turns.
          val merged = Point((simplified[i].x + next.x) / 2, (simplified[i].y + next.y) / 2)
          val nextIndex = (i + 1) % simplified.size
          simplified = simplified.mapIndexedNotNull { index, p ->
            when (index) {
              i -> merged
              nextIndex -> null
              else -> p
            }
          }
          changed = true
          break
        }
      }
    }
    return simplified
  }

  /**
   * Four corners → the rectangle they were aiming for: its angle is the average direction of
   * the edges (exactly level when close), its size the extent of the corners at that angle.
   */
  private fun rectangle(corners: List<Point>): RecognizedShape {
    // Average edge direction, folded into one quarter turn so the four edges agree.
    var sumCos = 0f
    var sumSin = 0f
    for (i in corners.indices) {
      val a = corners[i]
      val b = corners[(i + 1) % corners.size]
      val angle = atan2(b.y - a.y, b.x - a.x) * 4 // ×4: edges 90° apart become the same angle
      sumCos += cos(angle)
      sumSin += sin(angle)
    }
    var tilt = atan2(sumSin, sumCos) / 4
    if (abs(tilt) < RECTANGLE_LEVEL_SNAP) tilt = 0f

    // Measure the corners in a frame turned by the tilt.
    val centerX = corners.map { it.x }.average().toFloat()
    val centerY = corners.map { it.y }.average().toFloat()
    val c = cos(-tilt)
    val s = sin(-tilt)
    val turned = corners.map {
      val dx = it.x - centerX
      val dy = it.y - centerY
      Point(dx * c - dy * s, dx * s + dy * c)
    }
    var halfWidth = (turned.maxOf { it.x } - turned.minOf { it.x }) / 2
    var halfHeight = (turned.maxOf { it.y } - turned.minOf { it.y }) / 2
    val midX = (turned.maxOf { it.x } + turned.minOf { it.x }) / 2
    val midY = (turned.maxOf { it.y } + turned.minOf { it.y }) / 2

    val square = abs(halfWidth - halfHeight) < SQUARE_TOLERANCE * maxOf(halfWidth, halfHeight)
    if (square) {
      halfWidth = (halfWidth + halfHeight) / 2
      halfHeight = halfWidth
    }

    // Back to the page, corners in drawing order.
    val back = cos(tilt) to sin(tilt)
    val outline =
      listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (sx, sy) ->
        val x = midX + sx * halfWidth
        val y = midY + sy * halfHeight
        Point(centerX + x * back.first - y * back.second, centerY + x * back.second + y * back.first)
      }
    val kind = if (square) RecognizedShape.Kind.SQUARE else RecognizedShape.Kind.RECTANGLE
    return RecognizedShape(kind, closed(outline))
  }

  // ---- Ellipse -------------------------------------------------------------------------

  private fun ellipse(stroke: List<Point>, minX: Float, maxX: Float, minY: Float, maxY: Float): RecognizedShape? {
    val centerX = (minX + maxX) / 2
    val centerY = (minY + maxY) / 2
    var radiusX = (maxX - minX) / 2
    var radiusY = (maxY - minY) / 2
    if (radiusX <= 0f || radiusY <= 0f) return null

    // How far each point is from the ellipse, as a share of its size (0 = exactly on it).
    val error =
      stroke.map {
        val dx = (it.x - centerX) / radiusX
        val dy = (it.y - centerY) / radiusY
        abs(sqrt(dx * dx + dy * dy) - 1f)
      }.average()
    if (error > ELLIPSE_FIT) return null

    val circle = abs(radiusX - radiusY) < CIRCLE_TOLERANCE * maxOf(radiusX, radiusY)
    if (circle) {
      radiusX = (radiusX + radiusY) / 2
      radiusY = radiusX
    }
    val around =
      List(ELLIPSE_SEGMENTS) { i ->
        val angle = 2 * PI.toFloat() * i / ELLIPSE_SEGMENTS
        Point(centerX + radiusX * cos(angle), centerY + radiusY * sin(angle))
      }
    val outline = closed(around) // exactly back to the start, not "nearly" (float rounding)
    val kind = if (circle) RecognizedShape.Kind.CIRCLE else RecognizedShape.Kind.ELLIPSE
    return RecognizedShape(kind, outline)
  }

  // ---- Geometry helpers ----------------------------------------------------------------

  private fun closed(points: List<Point>) = points + points.first()

  private fun distance(a: Point, b: Point) = hypot(b.x - a.x, b.y - a.y)

  private fun pathLength(points: List<Point>): Float =
    points.zipWithNext().sumOf { (a, b) -> distance(a, b).toDouble() }.toFloat()

  private fun distanceToSegment(p: Point, a: Point, b: Point): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0f) return distance(p, a)
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    return distance(p, Point(a.x + t * dx, a.y + t * dy))
  }

  private fun maxDistanceToSegment(points: List<Point>, a: Point, b: Point): Float =
    points.maxOf { distanceToSegment(it, a, b) }

  /** True if the stroke stays, on average, within [tolerance] of the outline. */
  private fun fitsOutline(stroke: List<Point>, outline: List<Point>, tolerance: Float): Boolean {
    val edges = outline.zipWithNext()
    val mean = stroke.map { p -> edges.minOf { (a, b) -> distanceToSegment(p, a, b) } }.average()
    return mean < tolerance
  }

  /** How sharply the path turns at [b], in degrees: 0 = straight on, 180 = doubles back. */
  private fun turnDegrees(a: Point, b: Point, c: Point): Float {
    val first = atan2(b.y - a.y, b.x - a.x)
    val second = atan2(c.y - b.y, c.x - b.x)
    var turn = abs(second - first)
    if (turn > PI) turn = (2 * PI).toFloat() - turn
    return turn * 180f / PI.toFloat()
  }

  /** Ramer-Douglas-Peucker: keeps only the points needed to stay within [tolerance]. */
  private fun simplify(points: List<Point>, tolerance: Float): List<Point> {
    if (points.size < 3) return points
    val first = points.first()
    val last = points.last()
    var farthest = 0
    var farthestDistance = 0f
    for (i in 1 until points.lastIndex) {
      val d = distanceToSegment(points[i], first, last)
      if (d > farthestDistance) {
        farthest = i
        farthestDistance = d
      }
    }
    if (farthestDistance <= tolerance) return listOf(first, last)
    val left = simplify(points.subList(0, farthest + 1), tolerance)
    val right = simplify(points.subList(farthest, points.size), tolerance)
    return left.dropLast(1) + right
  }

  // ---- Tuning (all relative to the stroke's own size, so zoom does not matter) ----------

  /** Straight distance between the ends ÷ path length, above which a stroke can be a line. */
  private const val LINE_STRAIGHTNESS = 0.92f

  /** Furthest a line may stray from straight, as a share of its length. */
  private const val LINE_WOBBLE = 0.07f

  /** Within this of horizontal, vertical or 45°, a line is made exact (about 6 degrees). */
  private val LINE_ANGLE_SNAP = (6 * PI / 180).toFloat()

  /** The ends count as meeting when the gap is under this share of the path length. */
  private const val CLOSED_GAP = 0.2f

  /** Simplification tolerance for finding corners, as a share of the bounding-box diagonal. */
  private const val CORNER_TOLERANCE = 0.06f

  /** A point turns less than this and is not a corner. */
  private const val MIN_CORNER_TURN_DEGREES = 25f

  /** Corners closer than this share of the diagonal are merged into one. */
  private const val CORNER_MERGE = 0.12f

  /** Mean distance from a polygon allowed, as a share of the diagonal. */
  private const val POLYGON_FIT = 0.05f

  /** Share of the stroke, from its end, searched for the point where it closes. */
  private const val CLOSURE_SEARCH = 0.35f

  /** Mean distance from the arc's circle allowed, as a share of the distance between its ends. */
  private const val ARC_FIT = 0.04

  /** Arcs sweeping more than this are open circles, which look like a mistake (300 degrees). */
  private val MAX_ARC_SWEEP = (300 * PI / 180).toFloat()

  /** One point per this much of an arc (4 degrees). */
  private val ARC_STEP = (4 * PI / 180).toFloat()

  /** Within this of level (about 7 degrees), a rectangle is made exactly level. */
  private val RECTANGLE_LEVEL_SNAP = (7 * PI / 180).toFloat()

  /** Sides within this share of each other make a square; axes within it make a circle. */
  private const val SQUARE_TOLERANCE = 0.12f
  private const val CIRCLE_TOLERANCE = 0.12f

  /** Mean distance from the ellipse allowed, as a share of its size. */
  private const val ELLIPSE_FIT = 0.1

  private const val ELLIPSE_SEGMENTS = 72
}
