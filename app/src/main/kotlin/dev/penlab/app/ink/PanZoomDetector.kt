package dev.penlab.app.ink

import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Turns finger movement into "move the page by this much, zoom by this much".
 *
 * One finger pans. Two or more pan, zoom and turn together: the page follows the middle point
 * of the fingers, zooms by how much they spread apart or pinch in, and turns as the first two
 * fingers turn. Turning starts only after the fingers have turned [ROTATE_START_DEGREES], so a
 * plain pinch never tilts the page by accident.
 *
 * Palm rejection: a finger that lands while the pen is near the screen is taken for the writing
 * hand and ignored until it lifts. [cancel] (called when the pen touches down) drops the fingers
 * already being followed for the same reason.
 *
 * Nothing moves until a finger has travelled [startSlopPx], so a multi-finger tap (undo/redo)
 * never nudges the page. [FingerTapDetector] uses the same distance, which makes a touch either
 * a tap or a pan, never both.
 */
class PanZoomDetector(private val startSlopPx: Float, private val listener: Listener) {

  fun interface Listener {
    /**
     * Move the page by ([dx], [dy]) pixels, then turn it by [rotationDegrees] and scale it by
     * [scaleFactor] around the screen point ([focusX], [focusY]).
     */
    fun onPanZoom(dx: Float, dy: Float, scaleFactor: Float, rotationDegrees: Float, focusX: Float, focusY: Float)
  }

  /** Fingers being followed: pointer id → where it first touched. */
  private val fingerStarts = HashMap<Int, Pair<Float, Float>>()
  private var isMoving = false

  // Middle point and spread of the fingers at the previous event. Invalid after the set of
  // fingers changes, so adding or lifting a finger does not make the page jump.
  private var hasPrevious = false
  private var previousX = 0f
  private var previousY = 0f
  private var previousSpread = 0f
  private var previousAngle = 0f
  private var turning = false
  private var unusedTurn = 0f

  fun onTouchEvent(event: MotionEvent, penNearby: Boolean) {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        cancel()
        follow(event, penNearby)
      }

      MotionEvent.ACTION_POINTER_DOWN -> follow(event, penNearby)

      MotionEvent.ACTION_MOVE -> onMove(event)

      MotionEvent.ACTION_POINTER_UP -> {
        fingerStarts.remove(event.getPointerId(event.actionIndex))
        hasPrevious = false
      }

      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> cancel()
    }
  }

  /**
   * Starts following the finger at [index] from where it is now. Used when a finger that was
   * drawing becomes part of a pinch because a second finger landed.
   */
  fun adopt(event: MotionEvent, index: Int) {
    fingerStarts[event.getPointerId(index)] = event.getX(index) to event.getY(index)
    hasPrevious = false
  }

  /** Stops the current pan/zoom and ignores the fingers that are down until they lift. */
  fun cancel() {
    fingerStarts.clear()
    isMoving = false
    hasPrevious = false
    turning = false
    unusedTurn = 0f
  }

  private fun follow(event: MotionEvent, penNearby: Boolean) {
    val index = event.actionIndex
    if (penNearby || event.getToolType(index) != MotionEvent.TOOL_TYPE_FINGER) return
    fingerStarts[event.getPointerId(index)] = event.getX(index) to event.getY(index)
    hasPrevious = false
  }

  private fun onMove(event: MotionEvent) {
    if (fingerStarts.isEmpty()) return

    // Middle point of the followed fingers.
    var count = 0
    var sumX = 0f
    var sumY = 0f
    for (index in 0 until event.pointerCount) {
      val (startX, startY) = fingerStarts[event.getPointerId(index)] ?: continue
      val x = event.getX(index)
      val y = event.getY(index)
      if (!isMoving && hypot(x - startX, y - startY) > startSlopPx) isMoving = true
      sumX += x
      sumY += y
      count++
    }
    if (!isMoving || count == 0) return
    val middleX = sumX / count
    val middleY = sumY / count

    // Spread: average distance of the fingers from their middle point.
    var spread = 0f
    if (count >= 2) {
      for (index in 0 until event.pointerCount) {
        if (event.getPointerId(index) !in fingerStarts) continue
        spread += hypot(event.getX(index) - middleX, event.getY(index) - middleY)
      }
      spread /= count
    }

    // Angle of the line through the first two followed fingers.
    var angle = previousAngle
    if (count >= 2) {
      var first = -1
      var second = -1
      for (index in 0 until event.pointerCount) {
        if (event.getPointerId(index) !in fingerStarts) continue
        if (first < 0) first = index else if (second < 0) second = index
      }
      angle = Math.toDegrees(atan2((event.getY(second) - event.getY(first)).toDouble(), (event.getX(second) - event.getX(first)).toDouble())).toFloat()
    }

    if (hasPrevious) {
      val scale = if (previousSpread > MIN_SPREAD_PX && spread > MIN_SPREAD_PX) spread / previousSpread else 1f
      var turn = 0f
      if (count >= 2) {
        val delta = ((angle - previousAngle + 540f) % 360f) - 180f
        if (turning) {
          turn = delta
        } else {
          unusedTurn += delta
          if (abs(unusedTurn) > ROTATE_START_DEGREES) {
            turning = true
            turn = unusedTurn
          }
        }
      }
      listener.onPanZoom(middleX - previousX, middleY - previousY, scale, turn, middleX, middleY)
    }
    previousX = middleX
    previousY = middleY
    previousSpread = spread
    previousAngle = angle
    hasPrevious = true
  }

  private companion object {
    /** Below this spread the fingers are too close for a stable zoom ratio. */
    const val MIN_SPREAD_PX = 10f

    /** Fingers must turn this far before the page starts turning with them. */
    const val ROTATE_START_DEGREES = 8f
  }
}
