package dev.penlab.app.ink

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pure-Kotlin helpers for [InkCanvasView] so clear-page rules, zoom limits, layer undo/redo
 * state transitions, line-extension snapping, velocity-scaled erasing, plugin diagram scaling,
 * and tap-to-select hit-testing can be unit-tested on the JVM without an Android View.
 */
internal inline fun <S, I> canClearPage(
  isPaged: Boolean,
  pageTop: Float,
  pageBottom: Float,
  strokes: List<S>,
  items: List<I>,
  hiddenLayers: Set<Int>,
  strokeLayer: (S) -> Int,
  strokeCenterY: (S) -> Float,
  itemCenterY: (I) -> Float,
): Boolean {
  for (i in strokes.indices) {
    val s = strokes[i]
    if (strokeLayer(s) in hiddenLayers) continue
    if (!isPaged) return true
    val y = strokeCenterY(s)
    if (y >= pageTop && y < pageBottom) return true
  }
  for (i in items.indices) {
    if (!isPaged) return true
    val y = itemCenterY(items[i])
    if (y >= pageTop && y < pageBottom) return true
  }
  return false
}

internal const val BASE_MIN_ZOOM = 0.25f
internal const val BASE_MAX_ZOOM = 8f

/**
 * Minimum raw `pageToScreen` scale for a canvas whose 100% physical zoom is [trueSizeZoom].
 * Scales down below [baseMinZoom] on low-DPI screens so 25% true-size remains reachable.
 */
internal fun minZoomFor(trueSizeZoom: Float, baseMinZoom: Float = BASE_MIN_ZOOM): Float =
  baseMinZoom * minOf(1f, trueSizeZoom.coerceAtLeast(1e-3f))

/**
 * Maximum raw `pageToScreen` scale for a canvas whose 100% physical zoom is [trueSizeZoom].
 * Scales linearly with [trueSizeZoom] on high-DPI tablets (e.g. 340 dpi Tab S9) so deep zoom
 * headroom stays >= 800% of true paper size rather than capping early.
 */
internal fun maxZoomFor(trueSizeZoom: Float, baseMaxZoom: Float = BASE_MAX_ZOOM): Float =
  baseMaxZoom * maxOf(1f, trueSizeZoom)

internal fun addLayerSaved(current: SavedLayers): SavedLayers {
  val names = current.names + "Layer ${current.names.size + 1}"
  return SavedLayers(names = names, hidden = current.hidden, current = names.lastIndex)
}

internal fun renameLayerSaved(current: SavedLayers, index: Int, name: String): SavedLayers? {
  if (index !in current.names.indices || name.isBlank()) return null
  val names = current.names.toMutableList().also { it[index] = name.trim() }
  return current.copy(names = names)
}

internal fun deleteLayerSaved(current: SavedLayers, index: Int): SavedLayers? {
  if (current.names.size <= 1 || index !in current.names.indices) return null
  val names = current.names.toMutableList().also { it.removeAt(index) }
  val hidden = current.hidden.filter { it != index }.map { if (it > index) it - 1 else it }.distinct().sorted()
  val active =
    when {
      current.current > index -> current.current - 1
      current.current == index -> (index - 1).coerceAtLeast(0)
      else -> current.current
    }.coerceIn(0, names.lastIndex)
  return SavedLayers(names = names, hidden = hidden, current = active)
}

internal fun applySavedLayers(current: SavedLayers, state: SavedLayers): SavedLayers {
  val names = state.names.ifEmpty { listOf("Layer 1") }
  val legacy = state.current == HistoryFile.LEGACY_LAYER_CURRENT
  val hidden = (if (legacy) current.hidden else state.hidden).filter { it in names.indices }.distinct().sorted()
  val active = (if (legacy) current.current else state.current).coerceIn(0, names.lastIndex)
  return SavedLayers(names = names, hidden = hidden, current = active)
}

// ---- Line-extension axis snapping --------------------------------------------------------------

internal const val LINE_SNAP_COS_THRESHOLD = 0.9945219f // cos(6°)
internal const val LINE_SNAP_PERP_SLOP_DP = 14f

/**
 * Snaps a dragged line endpoint ([rawX], [rawY]) onto the forward ray from ([anchorX], [anchorY])
 * through ([origEndX], [origEndY]) when within [cosThreshold] (~±6°) or within
 * [screenPerpSlopPage] perpendicular page units. Never snaps backward behind the anchor.
 */
internal fun snapPointToLineRay(
  anchorX: Float,
  anchorY: Float,
  origEndX: Float,
  origEndY: Float,
  rawX: Float,
  rawY: Float,
  cosThreshold: Float = LINE_SNAP_COS_THRESHOLD,
  screenPerpSlopPage: Float = 0f,
): Pair<Float, Float> {
  val vx = origEndX - anchorX
  val vy = origEndY - anchorY
  val len = hypot(vx, vy)
  if (len <= 1e-6f) return rawX to rawY
  val ux = vx / len
  val uy = vy / len
  val rx = rawX - anchorX
  val ry = rawY - anchorY
  val rawLen = hypot(rx, ry)
  if (rawLen <= 1e-6f) return rawX to rawY
  val dot = rx * ux + ry * uy
  if (dot <= 0f) return rawX to rawY
  val cosAngle = dot / rawLen
  val perpDist = abs(rx * uy - ry * ux)
  return if (cosAngle >= cosThreshold || perpDist <= screenPerpSlopPage) {
    (anchorX + dot * ux) to (anchorY + dot * uy)
  } else {
    rawX to rawY
  }
}

// ---- Velocity-scaled eraser --------------------------------------------------------------------

internal const val ERASER_VEL_MIN_SPEED_DP_PER_SEC = 250f
internal const val ERASER_VEL_MAX_SPEED_DP_PER_SEC = 1400f
internal const val ERASER_VEL_MAX_FACTOR = 2.2f
private const val ERASER_VEL_EMA_TAU_MS = 80f

/**
 * Computes the EMA-smoothed eraser radius multiplier in `[1.0f, 2.2f]`.
 * Returns `1.0f` immediately when [enabled] is false; target ramps linearly from `1.0f` at
 * `250 dp/s` up to `2.2f` at `1400 dp/s`.
 */
internal fun computeVelocityScaledFactor(
  speedDpPerSec: Float,
  prevFactor: Float,
  dtMs: Float,
  enabled: Boolean,
): Float {
  if (!enabled) return 1.0f
  val cleanPrev = if (prevFactor.isFinite()) prevFactor.coerceIn(1.0f, ERASER_VEL_MAX_FACTOR) else 1.0f
  val target =
    if (!speedDpPerSec.isFinite() || speedDpPerSec <= ERASER_VEL_MIN_SPEED_DP_PER_SEC) {
      1.0f
    } else {
      val t =
        ((speedDpPerSec - ERASER_VEL_MIN_SPEED_DP_PER_SEC) /
          (ERASER_VEL_MAX_SPEED_DP_PER_SEC - ERASER_VEL_MIN_SPEED_DP_PER_SEC))
          .coerceIn(0f, 1f)
      1.0f + t * (ERASER_VEL_MAX_FACTOR - 1.0f)
    }
  if (!dtMs.isFinite() || dtMs <= 0f) return cleanPrev
  val alpha = (dtMs / ERASER_VEL_EMA_TAU_MS).coerceIn(0.05f, 1.0f)
  return (cleanPrev + alpha * (target - cleanPrev)).coerceIn(1.0f, ERASER_VEL_MAX_FACTOR)
}

// ---- Plugin diagram placement scale ------------------------------------------------------------

internal const val PLUGIN_SELECTION_OVERSIZE_RATIO = 1.35f
internal const val PLUGIN_SELECTION_TARGET_RATIO = 1.15f
internal const val PLUGIN_MIN_FLOOR_MM = 40f
internal const val PLUGIN_VIEWPORT_MAX_FRACTION = 0.65f

internal data class PluginPlacementTransform(
  val scale: Float,
  val anchorX: Float,
  val anchorY: Float,
  val dx: Float,
  val dy: Float,
)

/**
 * Computes uniform placement [scale] (around [anchorX], [anchorY]) and translation ([dx], [dy])
 * in page units for plugin items/strokes whose union bounds are [incomingLeft..incomingBottom].
 *
 * - When [wasEmpty] is false and selection bounds are non-empty: if the incoming diagram's max
 *   dimension exceeds `1.35x` of the selection's max dimension, scales it down uniformly toward
 *   `1.15x` of the selection's max dimension (respecting [minFloorPage] so tiny text snippets
 *   still yield readable diagrams).
 * - When [wasEmpty] is true: uniformly clamps oversized diagrams to fit within `65%` of the
 *   visible viewport width/height and centers them in the visible viewport.
 */
internal fun computePluginPlacementTransform(
  incomingLeft: Float,
  incomingTop: Float,
  incomingRight: Float,
  incomingBottom: Float,
  wasEmpty: Boolean,
  selectionLeft: Float,
  selectionTop: Float,
  selectionRight: Float,
  selectionBottom: Float,
  visibleLeft: Float,
  visibleTop: Float,
  visibleRight: Float,
  visibleBottom: Float,
  minFloorPage: Float = 0f,
): PluginPlacementTransform {
  val inW = (incomingRight - incomingLeft).coerceAtLeast(0f)
  val inH = (incomingBottom - incomingTop).coerceAtLeast(0f)
  val inCx = (incomingLeft + incomingRight) * 0.5f
  val inCy = (incomingTop + incomingBottom) * 0.5f
  if (wasEmpty) {
    val visW = (visibleRight - visibleLeft).coerceAtLeast(0f)
    val visH = (visibleBottom - visibleTop).coerceAtLeast(0f)
    val visCx = (visibleLeft + visibleRight) * 0.5f
    val visCy = (visibleTop + visibleBottom) * 0.5f
    val maxW = visW * PLUGIN_VIEWPORT_MAX_FRACTION
    val maxH = visH * PLUGIN_VIEWPORT_MAX_FRACTION
    val sx = if (inW > 1e-3f && maxW > 0f) maxW / inW else 1f
    val sy = if (inH > 1e-3f && maxH > 0f) maxH / inH else 1f
    val scale = minOf(1f, sx, sy)
    return PluginPlacementTransform(
      scale = scale,
      anchorX = inCx,
      anchorY = inCy,
      dx = visCx - inCx,
      dy = visCy - inCy,
    )
  }
  val selW = (selectionRight - selectionLeft).coerceAtLeast(0f)
  val selH = (selectionBottom - selectionTop).coerceAtLeast(0f)
  val inMax = maxOf(inW, inH)
  val selMax = maxOf(selW, selH)
  if (inMax <= 1e-3f || selMax <= 1e-3f) {
    return PluginPlacementTransform(scale = 1f, anchorX = inCx, anchorY = inCy, dx = 0f, dy = 0f)
  }
  val scale =
    if (inMax > selMax * PLUGIN_SELECTION_OVERSIZE_RATIO) {
      val targetMax = maxOf(selMax * PLUGIN_SELECTION_TARGET_RATIO, minFloorPage.coerceAtLeast(0f))
      (targetMax / inMax).coerceAtMost(1f)
    } else {
      1f
    }
  return PluginPlacementTransform(scale = scale, anchorX = inCx, anchorY = inCy, dx = 0f, dy = 0f)
}

// ---- Tap-to-select hit-testing -----------------------------------------------------------------

internal const val ITEM_TAP_TOLERANCE_DP = 8f

/**
 * Returns the topmost visible [PageItem] whose axis-aligned bounds (expanded by [tolerancePage])
 * contain ([pageX], [pageY]), iterating in reverse z-order. Skips items in [hiddenItemIds].
 */
internal fun hitTestTopItem(
  items: List<PageItem>,
  pageX: Float,
  pageY: Float,
  hiddenItemIds: Set<String> = emptySet(),
  tolerancePage: Float = 0f,
): PageItem? {
  val tol = tolerancePage.coerceAtLeast(0f)
  for (i in items.indices.reversed()) {
    val item = items[i]
    if (item.id in hiddenItemIds) continue
    val r = Math.toRadians(item.rotation.toDouble())
    val c = abs(cos(r)).toFloat()
    val s = abs(sin(r)).toFloat()
    val halfW = (item.width * c + item.height * s) / 2f + tol
    val halfH = (item.width * s + item.height * c) / 2f + tol
    if (pageX >= item.cx - halfW &&
      pageX <= item.cx + halfW &&
      pageY >= item.cy - halfH &&
      pageY <= item.cy + halfH
    ) {
      return item
    }
  }
  return null
}

// ---- Live rubber-band straight line preview ----------------------------------------------------

/**
 * Pure geometry state for a live hold-to-snap straight line preview whose start
 * ([anchorX], [anchorY]) remains fixed while ([endX], [endY]) follows the pen freely in 360°.
 */
internal data class RubberBandLineState(
  val anchorX: Float,
  val anchorY: Float,
  val endX: Float,
  val endY: Float,
)

/** Updates only the moving endpoint of [state], keeping [anchorX] and [anchorY] fixed. */
internal fun updateRubberBandLineEnd(
  state: RubberBandLineState,
  newEndX: Float,
  newEndY: Float,
): RubberBandLineState = state.copy(endX = newEndX, endY = newEndY)

/** Produces the two commit endpoints from [state], nudging coincident endpoints slightly for Ink. */
internal fun finalizeRubberBandLinePoints(state: RubberBandLineState): List<Point> {
  val dx = state.endX - state.anchorX
  val dy = state.endY - state.anchorY
  val endPoint =
    if (hypot(dx, dy) < 1e-3f) Point(state.anchorX + 0.01f, state.anchorY)
    else Point(state.endX, state.endY)
  return listOf(Point(state.anchorX, state.anchorY), endPoint)
}
