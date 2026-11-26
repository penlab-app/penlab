package dev.penlab.app.ui

import androidx.compose.ui.geometry.Offset

/**
 * The corner tool wheel's pure geometry: sizes, where the wheel and its round button sit, how the
 * ring is laid out so every visible cell stays clear of the system gesture zones, which screen
 * rectangles must be excluded from the Back gesture, and which ring cell a pull is pointing at.
 * No Compose state and no Android classes, so it is unit-tested on the JVM (WheelGeometryTest).
 *
 * The disc is centred on the round button (the hub), and the hub is pulled in from both screen
 * edges far enough that the ring of tools round it has a visible arc of 190
 * degrees ([ARC_CUT_DEG]); the screen edges still clip the disc, but only its outer rim. The ring cells are placed
 * so every visible one is fully on screen and clear of the gesture strips (see [ring]).
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

  /**
   * How far (in degrees, seen from the hub) each screen edge's cut reaches into the ring on both
   * sides of the edge's direction: the visible arc is 270 - 2 * this = 190 degrees, so it is always
   * more than a half circle (a quarter disc would be 90).
   */
  const val ARC_CUT_DEG = 68f // Akram 26 Nov sketch: hub closer to the corner (same distance from both edges), ~146° of ring visible

  /** A colour dot's cell on the small inner arc (smaller than a tool cell so it fits between button and ring). */
  const val COLOR_CELL_DP = 38f

  /** The round button, at the centre of the disc. */
  const val HUB_DP = 72f

  /** Space between the folded button's exclusion rectangle and the button. */
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
   * The wheel's centre, which is also the round button's centre ([hubX], [hubY], root pixels), and
   * the real screen corner it hangs off ([originX], [originY]).
   */
  data class Corner(val originX: Float, val originY: Float, val hubX: Float, val hubY: Float) {
    /** How far the hub's centre is from the side edge (along the bottom/top edge). */
    val hubU: Float get() = kotlin.math.abs(hubX - originX)

    /** How far the hub's centre is from the bottom/top edge (along the side edge). */
    val hubV: Float get() = kotlin.math.abs(hubY - originY)
  }

  /**
   * Where the hub (and so the disc's centre) sits for a corner: [hubU] in from the side edge and
   * [hubV] in from the bottom/top edge, both taken from [Ring.hubU] and [Ring.hubV].
   */
  fun corner(onLeft: Boolean, onTop: Boolean, width: Float, height: Float, hubU: Float, hubV: Float): Corner {
    val ox = if (onLeft) 0f else width
    val oy = if (onTop) 0f else height
    return Corner(ox, oy, if (onLeft) hubU else width - hubU, if (onTop) hubV else height - hubV)
  }

  // ---- The ring ----

  /**
   * Where the tools sit. Cell centres lie on a circle of [radius] round the hub, at
   * [startDeg] + i * [stepDeg] (0 = along the bottom/top edge into the screen, 90 = along the side
   * edge into the screen); the [count] visible ones run from [startDeg] to [endDeg], evenly spaced.
   * [disc] is the disc's radius. [hubU] and [hubV] are the disc centre's distances from the side
   * edge and from the bottom/top edge.
   */
  data class Ring(
    val radius: Float,
    val startDeg: Float,
    val endDeg: Float,
    val stepDeg: Float,
    val disc: Float,
    val count: Int = 4,
    val hubU: Float = 0f,
    val hubV: Float = 0f,
  ) {
    /** The visible arc's size in degrees. */
    val spanDeg: Float get() = endDeg - startDeg
  }

  /**
   * Lays the ring out round the hub. [insetU] is the strip along the side edge and [insetV] the one
   * along the bottom (or top) edge; a cell at (u, v) from the corner needs u - cell/2 >= insetU and
   * v - cell/2 >= insetV. The hub is put exactly where the two edges cut the circle of cell
   * centres [ARC_CUT_DEG] either side of their directions, so the cells that stay visible run along
   * one arc of 270 - 2 * [ARC_CUT_DEG] degrees (190), from just above the bottom/top edge round the
   * far side to just clear of the side edge, each fully on screen. As many cells as keep
   * [cellGap] between neighbours are spread evenly along the arc, and the radius is never so small
   * that a cell touches the button (radius [hubHalf], kept [hubClearance] away).
   */
  fun ring(
    minRadius: Float,
    cell: Float,
    discMargin: Float,
    insetU: Float,
    insetV: Float,
    hubHalf: Float,
    cellGap: Float,
    hubClearance: Float,
    arcCutDeg: Float = ARC_CUT_DEG,
  ): Ring {
    val radius = maxOf(minRadius, cell / 2f + hubHalf + hubClearance)
    val cut = Math.cos(Math.toRadians(arcCutDeg.toDouble())).toFloat() * radius
    val hubU = insetU + cell / 2f + cut
    val hubV = insetV + cell / 2f + cut
    val start = arcCutDeg - 90f
    val end = 180f - arcCutDeg
    val span = end - start
    val minStep = Math.toDegrees(2.0 * Math.asin(((cell + cellGap) / (2f * radius)).toDouble().coerceAtMost(1.0))).toFloat()
    val count = (Math.floor((span / minStep + 1e-4f).toDouble()).toInt() + 1).coerceAtLeast(2)
    return Ring(radius, start, end, span / (count - 1), radius + cell / 2f + discMargin, count, hubU, hubV)
  }

  /**
   * The colour arc: a small ring of [colorCell]-sized cells inside the [tool] ring, round the same
   * hub, at the radius halfway between the button's edge (plus [hubClearance]) and the tool
   * ring's inner edge, so it touches neither. Its cells must be fully on screen too: a cell
   * centre may go as far past the hub towards a screen edge as the tool ring's own cells do, less
   * the difference in half-cell size, so the arc runs from -a to 90 + a with sin(a) = that depth
   * over the radius. [Ring.disc] is the tool ring's: the disc does not grow.
   */
  fun colorRing(tool: Ring, toolCell: Float, colorCell: Float, hubHalf: Float, hubClearance: Float, cellGap: Float): Ring {
    val innerEdge = hubHalf + hubClearance
    val radius = maxOf((innerEdge + tool.radius - toolCell / 2f) / 2f, innerEdge + colorCell / 2f)
    val toolCut = (tool.radius * Math.sin(Math.toRadians((-tool.startDeg).toDouble()))).toFloat().coerceAtLeast(0f)
    val depth = (toolCell / 2f + toolCut - colorCell / 2f).coerceAtLeast(0f)
    val a = Math.toDegrees(Math.asin((depth / radius).toDouble().coerceIn(0.0, 1.0))).toFloat()
    val start = -a
    val end = 90f + a
    val span = end - start
    val minStep = Math.toDegrees(2.0 * Math.asin(((colorCell + cellGap) / (2f * radius)).toDouble().coerceAtMost(1.0))).toFloat()
    val count = (Math.floor((span / minStep + 1e-4f).toDouble()).toInt() + 1).coerceAtLeast(2)
    return Ring(radius, start, end, span / (count - 1), tool.disc, count, tool.hubU, tool.hubV)
  }

  /** A press nearer the hub than this is on the colour arc, farther on the tool ring. */
  fun ringSplitRadius(colors: Ring, tools: Ring): Float = (colors.radius + tools.radius) / 2f

  /** How far (degrees) a ring holding [n] cells can be scrolled: until the last cell reaches the arc's end. */
  fun scrollSpan(n: Int, ring: Ring): Float = ((n - 1) * ring.stepDeg - (ring.endDeg - ring.startDeg)).coerceAtLeast(0f)

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

  /** True if [p] lies inside the wheel's disc (centre [hub], radius [radius]). */
  fun inDisc(p: Offset, hub: Offset, radius: Float): Boolean = (p - hub).getDistance() <= radius

  // ---- System gesture exclusion ----

  /** A rectangle in root pixels (kept free of android.graphics so tests need no Android). */
  data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = bottom - top
  }

  /**
   * The rectangles to hand to `View.setSystemGestureExclusionRects` while the wheel is open, so a
   * drag on it never starts the Back gesture. The rect runs from the side edge (the gesture zone
   * itself) across the disc's bounding box (centre = the hub, [radius]), clipped to the screen,
   * as tall as the disc but at most [maxHeight]; when it has to be cut, the window stays on the
   * hub's height, where the pen is.
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
    val x0 = if (onLeft) 0f else (corner.hubX - radius).coerceIn(0f, width)
    val x1 = if (onLeft) (corner.hubX + radius).coerceIn(0f, width) else width
    val y0 = (corner.hubY - radius).coerceIn(0f, height)
    val y1 = (corner.hubY + radius).coerceIn(0f, height)
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
