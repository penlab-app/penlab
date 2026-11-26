package dev.penlab.app.ui

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Axis-aligned crop rectangle in normalized page coordinates (`0f..1f` along width and height).
 *
 * Kept pure Kotlin (no Android/Compose dependencies) so geometry and bounds math can be unit-tested
 * directly on the JVM.
 */
data class NormalizedCropRect(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
) {
  val width: Float get() = right - left
  val height: Float get() = bottom - top

  companion object {
    const val MIN_SPAN = 0.06f
    val FULL = NormalizedCropRect(0f, 0f, 1f, 1f)
    val DEFAULT = NormalizedCropRect(0.10f, 0.15f, 0.90f, 0.55f)

    /** Builds a clamped [NormalizedCropRect] from any two drag endpoints, expanding below [MIN_SPAN]. */
    fun fromDrag(ax: Float, ay: Float, bx: Float, by: Float): NormalizedCropRect {
      val cax = ax.coerceIn(0f, 1f)
      val cbx = bx.coerceIn(0f, 1f)
      val cay = ay.coerceIn(0f, 1f)
      val cby = by.coerceIn(0f, 1f)

      var l = minOf(cax, cbx)
      var r = maxOf(cax, cbx)
      if (r - l < MIN_SPAN) {
        if (l + MIN_SPAN <= 1f) {
          r = l + MIN_SPAN
        } else {
          l = 1f - MIN_SPAN
          r = 1f
        }
      }

      var t = minOf(cay, cby)
      var b = maxOf(cay, cby)
      if (b - t < MIN_SPAN) {
        if (t + MIN_SPAN <= 1f) {
          b = t + MIN_SPAN
        } else {
          t = 1f - MIN_SPAN
          b = 1f
        }
      }

      return NormalizedCropRect(l, t, r, b)
    }
  }
}

enum class CropHandle {
  TOP_LEFT,
  TOP_RIGHT,
  BOTTOM_LEFT,
  BOTTOM_RIGHT,
  TOP,
  BOTTOM,
  LEFT,
  RIGHT,
  MOVE,
  NEW_DRAG,
}

/** Hit-tests [normX], [normY] against corners, then edges, then interior, else [CropHandle.NEW_DRAG]. */
fun hitTestCropHandle(
  normX: Float,
  normY: Float,
  rect: NormalizedCropRect,
  hitRadiusX: Float,
  hitRadiusY: Float,
): CropHandle {
  val nearLeft = abs(normX - rect.left) <= hitRadiusX
  val nearRight = abs(normX - rect.right) <= hitRadiusX
  val nearTop = abs(normY - rect.top) <= hitRadiusY
  val nearBottom = abs(normY - rect.bottom) <= hitRadiusY

  if (nearLeft && nearTop) return CropHandle.TOP_LEFT
  if (nearRight && nearTop) return CropHandle.TOP_RIGHT
  if (nearLeft && nearBottom) return CropHandle.BOTTOM_LEFT
  if (nearRight && nearBottom) return CropHandle.BOTTOM_RIGHT

  val inYBand = normY in (rect.top - hitRadiusY)..(rect.bottom + hitRadiusY)
  if (inYBand && (nearLeft || nearRight)) {
    return if (nearLeft && (!nearRight || abs(normX - rect.left) <= abs(normX - rect.right))) {
      CropHandle.LEFT
    } else {
      CropHandle.RIGHT
    }
  }

  val inXBand = normX in (rect.left - hitRadiusX)..(rect.right + hitRadiusX)
  if (inXBand && (nearTop || nearBottom)) {
    return if (nearTop && (!nearBottom || abs(normY - rect.top) <= abs(normY - rect.bottom))) {
      CropHandle.TOP
    } else {
      CropHandle.BOTTOM
    }
  }

  if (normX in rect.left..rect.right && normY in rect.top..rect.bottom) {
    return CropHandle.MOVE
  }
  return CropHandle.NEW_DRAG
}

/** Applies total normalized drag delta ([dx], [dy]) from [start] for [handle], keeping bounds valid. */
fun applyCropDrag(
  start: NormalizedCropRect,
  handle: CropHandle,
  dx: Float,
  dy: Float,
): NormalizedCropRect =
  when (handle) {
    CropHandle.MOVE -> {
      val mx = dx.coerceIn(-start.left, 1f - start.right)
      val my = dy.coerceIn(-start.top, 1f - start.bottom)
      NormalizedCropRect(start.left + mx, start.top + my, start.right + mx, start.bottom + my)
    }
    CropHandle.NEW_DRAG -> start
    CropHandle.TOP_LEFT,
    CropHandle.TOP_RIGHT,
    CropHandle.BOTTOM_LEFT,
    CropHandle.BOTTOM_RIGHT,
    CropHandle.TOP,
    CropHandle.BOTTOM,
    CropHandle.LEFT,
    CropHandle.RIGHT,
    -> {
      val movesLeft = handle == CropHandle.LEFT || handle == CropHandle.TOP_LEFT || handle == CropHandle.BOTTOM_LEFT
      val movesRight = handle == CropHandle.RIGHT || handle == CropHandle.TOP_RIGHT || handle == CropHandle.BOTTOM_RIGHT
      val movesTop = handle == CropHandle.TOP || handle == CropHandle.TOP_LEFT || handle == CropHandle.TOP_RIGHT
      val movesBottom = handle == CropHandle.BOTTOM || handle == CropHandle.BOTTOM_LEFT || handle == CropHandle.BOTTOM_RIGHT

      val l = if (movesLeft) (start.left + dx).coerceIn(0f, start.right - NormalizedCropRect.MIN_SPAN) else start.left
      val r = if (movesRight) (start.right + dx).coerceIn(start.left + NormalizedCropRect.MIN_SPAN, 1f) else start.right
      val t = if (movesTop) (start.top + dy).coerceIn(0f, start.bottom - NormalizedCropRect.MIN_SPAN) else start.top
      val b = if (movesBottom) (start.bottom + dy).coerceIn(start.top + NormalizedCropRect.MIN_SPAN, 1f) else start.bottom
      NormalizedCropRect(l, t, r, b)
    }
  }

data class PixelCropBounds(
  val renderWidth: Int,
  val renderHeight: Int,
  val cropLeft: Int,
  val cropTop: Int,
  val cropWidth: Int,
  val cropHeight: Int,
)

/**
 * Computes full-page render dimensions and exact pixel crop bounds so the cropped output aims for
 * [targetLongSidePx] on its longest edge without exceeding [maxRenderDimPx] on the full page.
 */
fun computeHighResCropBounds(
  pageWidthPt: Int,
  pageHeightPt: Int,
  crop: NormalizedCropRect,
  targetLongSidePx: Int = 2000,
  maxRenderDimPx: Int = 3600,
): PixelCropBounds {
  val wPt = pageWidthPt.coerceAtLeast(1)
  val hPt = pageHeightPt.coerceAtLeast(1)
  val cropWpt = wPt * crop.width
  val cropHpt = hPt * crop.height
  val desiredScale = targetLongSidePx / maxOf(cropWpt, cropHpt).coerceAtLeast(1f)
  val maxScale = maxRenderDimPx / maxOf(wPt, hPt).toFloat()
  val scale = minOf(desiredScale, maxScale).coerceAtLeast(1f / maxOf(wPt, hPt))

  val renderWidth = (wPt * scale).roundToInt().coerceAtLeast(1)
  val renderHeight = (hPt * scale).roundToInt().coerceAtLeast(1)
  val cropLeft = (crop.left * renderWidth).roundToInt().coerceIn(0, renderWidth - 1)
  val cropTop = (crop.top * renderHeight).roundToInt().coerceIn(0, renderHeight - 1)
  val cropRight = (crop.right * renderWidth).roundToInt().coerceIn(cropLeft + 1, renderWidth)
  val cropBottom = (crop.bottom * renderHeight).roundToInt().coerceIn(cropTop + 1, renderHeight)
  return PixelCropBounds(
    renderWidth = renderWidth,
    renderHeight = renderHeight,
    cropLeft = cropLeft,
    cropTop = cropTop,
    cropWidth = cropRight - cropLeft,
    cropHeight = cropBottom - cropTop,
  )
}
