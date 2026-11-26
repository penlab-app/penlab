package dev.penlab.app.ui

import androidx.compose.ui.geometry.Offset

/**
 * The corner tool wheel's pure geometry: sizes, where the wheel and its round button sit once the
 * system gesture zones are kept clear, which screen rectangles must be excluded from the Back
 * gesture, and which ring cell a pull is pointing at. No Compose state and no Android classes, so
 * it is unit-tested on the JVM (WheelGeometryTest).
 *
 * All sizes are in dp (as plain floats) and every position is in root pixels.
 */
internal object WheelGeometry {
  // ---- Sizes. About 25% larger than the first wheel (cell 40, ring 124, disc 152, button 60). ----

  /** A ring cell: bigger than the 48 dp touch-target minimum. */
  const val CELL_DP = 50f

  /** The tool ring's radius round the wheel's centre. */
  const val RING_RADIUS_DP = 155f

  /** The disc: the ring plus half a cell plus a small margin. */
  const val DISC_RADIUS_DP = 190f

  /** The round button in the corner. */
  const val HUB_DP = 72f

  /** Space between the button and the (gesture-safe) edges. */
  const val EDGE_GAP_DP = 12f

  /** Android honours at most this much exclusion height per screen edge. */
  const val MAX_EXCLUSION_HEIGHT_DP = 200f

  /** A reported inset may never take more than this fraction of the screen's extent. */
  const val MAX_INSET_FRACTION = 0.2f

  // ---- Edge safety ----

  /**
   * How far the wheel is kept from a screen edge: the larger of the system gesture inset there and
   * whatever else already covers that edge (cutout, bars), never negative and never more than
   * [MAX_INSET_FRACTION] of [extent], so an odd value from the system cannot push it off-screen.
   */
  fun edgeInset(gesture: Float, covered: Float, extent: Float): Float =
    maxOf(gesture, covered, 0f).coerceAtMost((extent * MAX_INSET_FRACTION).coerceAtLeast(0f))

  /** The wheel's centre and its round button, root pixels. */
  data class Corner(val originX: Float, val originY: Float, val hubX: Float, val hubY: Float)

  /**
   * Where the wheel turns round and where its button sits. The wheel's centre is the screen corner
   * pulled in by the gesture insets (bottom/sides) or by [topReserve] (the top buttons); the
   * button sits [gap] + [hubHalf] in from that point on both axes, so it is clear of every gesture
   * zone and centred on the corner the ring turns round.
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
    val ox = if (onLeft) insetLeft else width - insetRight
    val oy = if (onTop) topReserve else height - insetBottom
    val d = gap + hubHalf
    return Corner(ox, oy, if (onLeft) ox + d else ox - d, if (onTop) oy + d else oy - d)
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

  // ---- The ring ----

  /** Where a ring slot at [angleDeg] (0 = along the bottom/top edge, 90 = along the side edge) sits. */
  fun ringSlot(radius: Float, angleDeg: Float): Offset {
    val a = Math.toRadians(angleDeg.toDouble())
    return Offset((radius * Math.cos(a)).toFloat(), (radius * Math.sin(a)).toFloat())
  }

  /** Slots count as on screen (and so as pull targets) between these angles. */
  fun slotOnScreen(angleDeg: Float): Boolean = angleDeg in 4f..86f

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
