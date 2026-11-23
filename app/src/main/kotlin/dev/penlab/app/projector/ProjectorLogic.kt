package dev.penlab.app.projector

/**
 * The parts of Projector view that are plain logic, free of Android so they can be unit-tested:
 * fitting the tablet's view to the projector's shape, deciding when to offer the feature, and the
 * scheduler that keeps the projector's picture up to date without redrawing it too often.
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

/** How long the projector waits after the last change before it redraws. */
const val PROJECTOR_DEBOUNCE_MS = 100L

/**
 * Decides when the projector's picture is redrawn. Changes arrive in bursts (a pan is hundreds of
 * calls), so [markDirty] only notes the time; one timer waits until [debounceMs] have passed with
 * no new change, then [startRender] runs. Only one render is ever in flight: changes that arrive
 * meanwhile are coalesced into one more render after it finishes.
 *
 * Everything runs on one thread (the main thread). Time and timers are passed in so tests can
 * drive them.
 *
 * @param startRender starts a render and must call its argument when the render has finished
 *   (on the same thread as the rest).
 */
class RenderScheduler(
  private val debounceMs: Long,
  private val now: () -> Long,
  private val postDelayed: (delayMs: Long, action: () -> Unit) -> Unit,
  private val startRender: (done: () -> Unit) -> Unit,
) {
  private var dirty = false
  private var lastChange = 0L
  private var timerPending = false
  private var rendering = false

  /** Bumped by [cancel], so timers and renders from before it do nothing when they fire. */
  private var generation = 0

  /** Something changed on the page: draw it again soon. Cheap, safe to call very often. */
  fun markDirty() {
    lastChange = now()
    dirty = true
    if (!timerPending && !rendering) schedule(debounceMs)
  }

  /** Forget everything pending (Projector view ended). */
  fun cancel() {
    generation++
    dirty = false
    timerPending = false
    rendering = false
  }

  /** Whether a render is running right now. */
  val isRendering: Boolean get() = rendering

  private fun schedule(delay: Long) {
    timerPending = true
    val mine = generation
    postDelayed(delay.coerceAtLeast(0L)) {
      if (mine == generation) onTimer()
    }
  }

  private fun onTimer() {
    timerPending = false
    if (!dirty || rendering) return
    val wait = lastChange + debounceMs - now()
    if (wait > 0L) {
      schedule(wait) // more changes came in meanwhile: wait for them to stop
      return
    }
    dirty = false
    rendering = true
    val mine = generation
    startRender {
      if (mine != generation) return@startRender
      rendering = false
      if (dirty && !timerPending) schedule(lastChange + debounceMs - now())
    }
  }
}
