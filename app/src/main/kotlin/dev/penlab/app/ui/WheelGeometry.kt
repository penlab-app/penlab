package dev.penlab.app.ui

import androidx.compose.ui.geometry.Offset

/**
 * The corner tool wheel's pure geometry: sizes, where the wheel and its round button sit, how the
 * ring is laid out so every visible cell stays clear of the system gesture zones, which screen
 * rectangles must be excluded from the Back gesture, and which ring cell a pull is pointing at.
 * No Compose state and no Android classes, so it is unit-tested on the JVM (WheelGeometryTest).
 *
 * The disc is a true quarter circle centred on the real screen corner; the screen edges cut it.
 * Only what is interactive keeps out of the gesture strips: the button's touch centre, and the
 * ring cells (see [ring]).
 *
 * All sizes are in dp (as plain floats) and every position is in root pixels.
 */
internal object WheelGeometry {
  // ---- Sizes. About 25% larger than the first wheel (cell 40, ring 124, disc 152, button 60). ----

  /** A ring cell: bigger than the 48 dp touch-target minimum. */
  const val CELL_DP = 50f

  /**
   * The tool ring's smallest radius round the screen corner. [ring] grows it when the gesture
   * strips, the button or the spacing of the cells need more room.
   */
  const val RING_RADIUS_DP = 155f

  /** The disc reaches this far past the ring's cells (half a cell plus this margin). */
  const val DISC_MARGIN_DP = 10f

  /** Least clear space between two neighbouring ring cells. */
  const val CELL_GAP_DP = 6f

  /** Least clear space between the round button and the nearest visible ring cell. */
  const val HUB_CLEARANCE_DP = 8f

  /** How many tools are visible on the arc at once. */
  const val VISIBLE_CELLS = 4

  /** The round button in the corner. */
  const val HUB_DP = 72f

  /** Space between the button and the (gesture-safe) edges. */
  const val EDGE_GAP_DP = 12f

  /** Android honours at most this much exclusion height per screen edge. */
  const val MAX_EXCLUSION_HEIGHT_DP = 200f

  /** A reported inset may never take more than this fraction of the screen's extent. */
  const val MAX_INSET_FRACTION = 0.2f

  /**
   * Immersive mode (system bars hidden): the cells keep only this much from a screen edge, so the
   * rounded screen corner (about 28 px on the Tab S9) never clips them.
   */
  const val CORNER_MARGIN_DP = 8f

  // ---- Edge safety ----

  /**
   * How far the wheel is kept from a screen edge: the larger of the system gesture inset there and
   * whatever else already covers that edge (cutout, bars), never negative and never more than
   * [MAX_INSET_FRACTION] of [extent], so an odd value from the system cannot push it off-screen.
   * With [immersive] (system bars hidden, so a swipe from the edge only reveals them) the gesture
   * inset is ignored and only what physically covers the edge (a cutout) counts.
   */
  fun edgeInset(gesture: Float, covered: Float, extent: Float, immersive: Boolean = false): Float =
    maxOf(if (immersive) 0f else gesture, covered, 0f).coerceAtMost((extent * MAX_INSET_FRACTION).coerceAtLeast(0f))

  /**
   * The wheel's centre (the real screen corner, where the disc is drawn from) and its round
   * button's touch centre, root pixels.
   */
  data class Corner(val originX: Float, val originY: Float, val hubX: Float, val hubY: Float) {
    /** How far the button's centre is from the corner along the bottom/top edge. */
    val hubU: Float get() = kotlin.math.abs(hubX - originX)

    /** How far the button's centre is from the corner along the side edge. */
    val hubV: Float get() = kotlin.math.abs(hubY - originY)
  }

  /**
   * Where the wheel turns round and where its button sits. The wheel's centre is the screen corner
   * itself, so the disc runs off both screen edges. The button is the interactive part: its centre
   * sits [gap] + [hubHalf] in from the gesture insets (bottom/sides) or from [topReserve] (the top
   * buttons), so it is clear of every gesture zone.
   */
  fun corner(
    onLeft: Boolean,
    onTop: Boolean,
    width: Float,
    height: Float,
    insetLeft: Float,
    insetRight: Float,
    insetBottom: Float,
    topReserve: Float,
    gap: Float,
    hubHalf: Float,
  ): Corner {
    val ox = if (onLeft) 0f else width
    val oy = if (onTop) 0f else height
    val d = gap + hubHalf
    val hx = if (onLeft) insetLeft + d else width - insetRight - d
    val hy = if (onTop) topReserve + d else height - insetBottom - d
    return Corner(ox, oy, hx, hy)
  }

  // ---- The ring ----

  /**
   * Where the tools sit. Cell centres lie on a circle of [radius] round the screen corner, at
   * [startDeg] + i * [stepDeg] (0 = along the bottom/top edge, 90 = along the side edge); the
   * [VISIBLE_CELLS] visible ones run from [startDeg] to [endDeg], evenly spaced. [disc] is the
   * visible disc's radius.
   */
  data class Ring(val radius: Float, val startDeg: Float, val endDeg: Float, val stepDeg: Float, val disc: Float)

  /**
   * Lays the ring out so every visible cell is fully on screen and clear of the gesture strips.
   * [insetU] is the strip along the side edge and [insetV] the one along the bottom (or top) edge,
   * so a cell at (u, v) from the corner needs u - cell/2 >= insetU and v - cell/2 >= insetV. The
   * angular range is exactly what that allows at the chosen radius, and the radius is the
   * smallest one (not under [minRadius]) at which neighbouring cells keep [cellGap] between them
   * and the button (centre [hubU], [hubV] from the corner, radius [hubHalf]) keeps [hubClearance]
   * from every visible cell.
   */
  fun ring(
    minRadius: Float,
    cell: Float,
    discMargin: Float,
    insetU: Float,
    insetV: Float,
    hubU: Float,
    hubV: Float,
    hubHalf: Float,
    cellGap: Float,
    hubClearance: Float,
    maxRadius: Float = 700f,
  ): Ring {
    val loU = insetU + cell / 2f
    val loV = insetV + cell / 2f
    fun at(radius: Float): Ring? {
      if (radius <= loU || radius <= loV) return null
      val start = Math.toDegrees(Math.asin((loV / radius).toDouble())).toFloat()
      val end = Math.toDegrees(Math.acos((loU / radius).toDouble())).toFloat()
      val step = (end - start) / (VISIBLE_CELLS - 1)
      if (step <= 0f) return null
      return Ring(radius, start, end, step, radius + cell / 2f + discMargin)
    }
    fun fits(r: Ring): Boolean {
      val chord = 2f * r.radius * Math.sin(Math.toRadians(r.stepDeg / 2.0)).toFloat()
      if (chord < cell + cellGap) return false
      return (0 until VISIBLE_CELLS).all { i ->
        val s = ringSlot(r.radius, r.startDeg + i * r.stepDeg)
        Math.hypot((s.x - hubU).toDouble(), (s.y - hubV).toDouble()) >= cell / 2f + hubHalf + hubClearance
      }
    }
    var radius = minRadius
    var best: Ring? = null
    while (radius <= maxRadius) {
      val r = at(radius)
      if (r != null) {
        best = r
        if (fits(r)) return r
      }
      radius += 1f
    }
    return best ?: Ring(maxRadius, 45f, 45f, 0f, maxRadius + cell / 2f + discMargin)
  }

  /** Where a ring slot at [angleDeg] (0 = along the bottom/top edge, 90 = along the side edge) sits. */
  fun ringSlot(radius: Float, angleDeg: Float): Offset {
    val a = Math.toRadians(angleDeg.toDouble())
    return Offset((radius * Math.cos(a)).toFloat(), (radius * Math.sin(a)).toFloat())
  }

  /** Slots count as visible (and so as pull targets) between the ring's ends, give or take rounding. */
  fun slotOnScreen(angleDeg: Float, ring: Ring): Boolean =
    angleDeg >= ring.startDeg - 0.5f && angleDeg <= ring.endDeg + 0.5f

  /**
   * How visible a cell at [angleDeg] is: 1 on the arc, fading to 0 over 0.6 of a step beyond
   * either end, so tools parked past the ends (or passing the ends while it spins) never show
   * half cut by a screen edge or a gesture strip.
   */
  fun ringAlpha(angleDeg: Float, ring: Ring): Float {
    val over = maxOf(ring.startDeg - angleDeg, angleDeg - ring.endDeg, 0f)
    if (ring.stepDeg <= 0f) return if (over <= 0f) 1f else 0f
    return (1f - over / (0.6f * ring.stepDeg)).coerceIn(0f, 1f)
  }

  /** True if [p] lies in the wheel's quarter: inside the disc and on the screen side of both axes. */
  fun inQuarter(p: Offset, originX: Float, originY: Float, onLeft: Boolean, onTop: Boolean, radius: Float): Boolean {
    val u = if (onLeft) p.x - originX else originX - p.x
    val v = if (onTop) p.y - originY else originY - p.y
    return u >= 0f && v >= 0f && u * u + v * v <= radius * radius
  }

  // ---- System gesture exclusion ----

  /** A rectangle in root pixels (kept free of android.graphics so tests need no Android). */
  data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = bottom - top
  }

  /**
   * The rectangles to hand to `View.setSystemGestureExclusionRects` while the wheel is open, so a
   * drag on it never starts the Back gesture. The rect runs from the side edge (the gesture zone
   * itself) across the wheel, as tall as the wheel but at most [maxHeight]; when it has to be
   * cut, the window stays on the button's height, where the pen is.
   */
  fun exclusionRects(
    onLeft: Boolean,
    onTop: Boolean,
    width: Float,
    height: Float,
    corner: Corner,
    radius: Float,
    maxHeight: Float,
  ): List<PxRect> {
    val x0 = if (onLeft) 0f else (corner.originX - radius).coerceIn(0f, width)
    val x1 = if (onLeft) (corner.originX + radius).coerceIn(0f, width) else width
    // Vertically: the quarter's span, running on to the screen edge it sits against.
    val y0 = if (onTop) 0f else (corner.originY - radius).coerceIn(0f, height)
    val y1 = if (onTop) (corner.originY + radius).coerceIn(0f, height) else height
    if (x1 <= x0 || y1 <= y0) return emptyList()
    var top = y0
    var bottom = y1
    if (bottom - top > maxHeight) {
      top = (corner.hubY - maxHeight / 2f).coerceIn(y0, y1 - maxHeight)
      bottom = top + maxHeight
    }
    return listOf(PxRect(x0.toInt(), top.toInt(), x1.toInt(), bottom.toInt()))
  }

  /**
   * The folded wheel: just its round button. With the system bars hidden the button sits close to
   * the side edge, inside the Back gesture strip, so this rectangle (the side edge to the button's
   * far side, as tall as the button plus [gap] each way) keeps a press on it from starting Back.
   */
  fun hubExclusionRects(onLeft: Boolean, width: Float, height: Float, corner: Corner, hubHalf: Float, gap: Float): List<PxRect> {
    val x0 = if (onLeft) 0f else (corner.hubX - hubHalf - gap).coerceIn(0f, width)
    val x1 = if (onLeft) (corner.hubX + hubHalf + gap).coerceIn(0f, width) else width
    val y0 = (corner.hubY - hubHalf - gap).coerceIn(0f, height)
    val y1 = (corner.hubY + hubHalf + gap).coerceIn(0f, height)
    if (x1 <= x0 || y1 <= y0) return emptyList()
    return listOf(PxRect(x0.toInt(), y0.toInt(), x1.toInt(), y1.toInt()))
  }

  /**
   * The rectangles that keep the side rail's drag grips out of the system Back gesture (a drag
   * on a grip goes inward from the edge, which Back would otherwise take). The rail is as tall
   * as the screen, more than Android's [maxHeight] per edge, so the budget is split between the
   * grips: each gets a window centred on it (clamped to the screen), and windows that touch merge.
   * A grip centre off the screen (the rail is scrolled) gets none.
   */
  fun railExclusionRects(
    onLeft: Boolean,
    width: Float,
    height: Float,
    railWidth: Float,
    gripCentersY: List<Float>,
    maxHeight: Float,
  ): List<PxRect> {
    val ys = gripCentersY.filter { it in 0f..height }.sorted()
    if (ys.isEmpty() || width <= 0f || railWidth <= 0f || height <= 0f) return emptyList()
    val x0 = if (onLeft) 0f else (width - railWidth).coerceAtLeast(0f)
    val x1 = if (onLeft) railWidth.coerceAtMost(width) else width
    val half = minOf(maxHeight / ys.size, height) / 2f
    val merged = ArrayList<Pair<Float, Float>>()
    for (y in ys) {
      val top = (y - half).coerceIn(0f, height - 2 * half)
      val span = top to top + 2 * half
      val last = merged.lastOrNull()
      if (last != null && span.first <= last.second) merged[merged.size - 1] = last.first to maxOf(last.second, span.second)
      else merged.add(span)
    }
    return merged.map { PxRect(x0.toInt(), it.first.toInt(), x1.toInt(), it.second.toInt()) }
  }

  /**
   * The cell a pull at [at] is pointing at: nothing inside the dead zone round the button
   * ([dead]), otherwise the nearest cell centre within [reach]. [targets] are cell centres by key.
   */
  fun pickTarget(targets: Map<Int, Offset>, at: Offset, hub: Offset, dead: Float, reach: Float): Int? {
    if ((at - hub).getDistance() < dead) return null
    val best = targets.entries.minByOrNull { (it.value - at).getDistance() } ?: return null
    return if ((best.value - at).getDistance() < reach) best.key else null
  }
}
