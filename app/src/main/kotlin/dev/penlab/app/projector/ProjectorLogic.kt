package dev.penlab.app.projector

/**
 * The parts of Projector view that are plain logic, free of Android so they can be unit-tested:
 * fitting the tablet's view to the projector's shape and deciding when to offer the feature.
 */

/** A rectangle as four numbers (android.graphics.RectF cannot be used in plain JVM tests). */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
  val width: Float get() = right - left
  val height: Float get() = bottom - top
  val centerX: Float get() = (left + right) / 2f
  val centerY: Float get() = (top + bottom) / 2f
}

/**
 * [view] widened or heightened (never shrunk) around its centre so that its width / height equals
 * [aspect] (the projector's width / height). The teacher's view is kept whole and the projector
 * shows a little more page beside or above and below it: nothing is ever cropped.
 */
fun fitToAspect(view: Box, aspect: Float): Box {
  if (view.width <= 0f || view.height <= 0f || aspect <= 0f || aspect.isNaN()) return view
  return if (view.width / view.height < aspect) {
    val half = view.height * aspect / 2f
    Box(view.centerX - half, view.top, view.centerX + half, view.bottom)
  } else {
    val half = view.width / aspect / 2f
    Box(view.left, view.centerY - half, view.right, view.centerY + half)
  }
}

/**
 * Whether to show the "Projector connected" offer now: a presentation display exists, a notebook
 * is open, Projector view is not already on, and this display has not been offered already
 * (the offer is made once per connection, not at every notebook switch).
 */
fun shouldOfferProjector(hasDisplay: Boolean, notebookOpen: Boolean, active: Boolean, alreadyOffered: Boolean): Boolean =
  hasDisplay && notebookOpen && !active && !alreadyOffered
