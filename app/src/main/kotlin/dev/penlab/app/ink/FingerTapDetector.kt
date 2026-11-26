package dev.penlab.app.ink

import android.view.MotionEvent
import kotlin.math.hypot

/**
 * Spots a quick tap with several fingers at once (2 fingers = undo, 3 = redo in the app).
 *
 * Feed it every touch event. A gesture runs from the first contact going down to the last one
 * lifting, and counts as a tap only if all of this holds:
 *  - no pen touched the screen during it,
 *  - no finger slid further than [maxMovePx],
 *  - it was over within [TAP_TIMEOUT_MS],
 *  - Android did not flag any contact as a palm.
 *
 * [onTap] receives the largest number of fingers that were down together.
 */
class FingerTapDetector(private val maxMovePx: Float, private val onTap: (fingerCount: Int) -> Unit) {

  private val startPositions = HashMap<Int, Pair<Float, Float>>()
  private var firstDownTime = 0L
  private var maxFingers = 0
  private var isTapCandidate = false

  fun onTouchEvent(event: MotionEvent) {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        startPositions.clear()
        firstDownTime = event.eventTime
        maxFingers = 0
        isTapCandidate = true
        trackNewPointer(event)
      }

      MotionEvent.ACTION_POINTER_DOWN -> trackNewPointer(event)

      MotionEvent.ACTION_MOVE ->
        for (index in 0 until event.pointerCount) {
          val (startX, startY) = startPositions[event.getPointerId(index)] ?: continue
          if (hypot(event.getX(index) - startX, event.getY(index) - startY) > maxMovePx) {
            isTapCandidate = false
          }
        }

      MotionEvent.ACTION_POINTER_UP -> {
        startPositions.remove(event.getPointerId(event.actionIndex))
        if (event.wasCanceled()) isTapCandidate = false
      }

      MotionEvent.ACTION_UP -> {
        val quick = event.eventTime - firstDownTime <= TAP_TIMEOUT_MS
        if (isTapCandidate && quick && !event.wasCanceled() && maxFingers >= 2) onTap(maxFingers)
        isTapCandidate = false
      }

      MotionEvent.ACTION_CANCEL -> isTapCandidate = false
    }
  }

  private fun trackNewPointer(event: MotionEvent) {
    val index = event.actionIndex
    if (event.getToolType(index) != MotionEvent.TOOL_TYPE_FINGER) {
      isTapCandidate = false // pen (or palm/mouse) involved: not a finger tap
      return
    }
    startPositions[event.getPointerId(index)] = event.getX(index) to event.getY(index)
    maxFingers = maxOf(maxFingers, startPositions.size)
  }

  private fun MotionEvent.wasCanceled() =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
      flags and MotionEvent.FLAG_CANCELED != 0

  private companion object {
    /** Longest a tap may last, first finger down to last finger up. */
    const val TAP_TIMEOUT_MS = 300L
  }
}
