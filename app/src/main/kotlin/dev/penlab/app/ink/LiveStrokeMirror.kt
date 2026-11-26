package dev.penlab.app.ink

import androidx.ink.brush.Brush

/**
 * A stroke whose pen has lifted but whose committed version has not reached the projector yet.
 * Immutable copy of what the projector drew live, so it keeps showing until the page's own
 * stroke takes over (Ink's hand-off is delayed and batched, so fast hatching would otherwise
 * lose its last strokes for a moment). [committedVersion] is -1 until Ink reports the stroke
 * finished; then it is the page's change counter after the stroke was inserted.
 */
class PendingLine(
  val points: FloatArray,
  val count: Int,
  val color: Int,
  val size: Float,
  val liftedAt: Long,
) {
  var committedVersion = -1
}


/**
 * The stroke under the pen right now, as the projector shows it.
 *
 * PERFORMANCE CONTRACT. The pen's own path (InkCanvasView.onTouchEvent -> Jetpack Ink's front
 * buffer) is the app's first priority and this class must never slow it. So the pen side does
 * almost nothing:
 *  - [begin] and [end] set a couple of fields;
 *  - [add] stores one pressure and one time into arrays that are already allocated (they grow by
 *    doubling, so a long stroke allocates a handful of times, never per event);
 *  - the points themselves are NOT copied while the pen is down: the tablet already keeps them for
 *    shape recognition (InkCanvasView.strokePoints, page units) and this class only reads that list.
 * The canvas copies them with [copyPoints] into each projector snapshot (at most once per frame)
 * and the projector's render thread draws the line; nothing here draws.
 *
 * Everything here runs on the main thread, which is also where the pen's events are handled, so
 * nothing needs locking. The line is drawn in the brush colour the tablet shows the live stroke
 * with (screen colours); tilt and orientation are not carried over.
 */
internal class LiveStrokeMirror(
  /** The current stroke's points in page units, appended by the tablet; read-only here. */
  private val points: List<Point>,
) {
  /** The pen is down on a stroke (it is the one line still growing). */
  var active = false
    private set

  /** Lifted, not yet committed lines, oldest first, keyed by Ink's stroke id. Main thread only. */
  private val pending = LinkedHashMap<Any, PendingLine>()

  /** Anything the projector still has to draw by itself: the live line or a waiting one. */
  val needsFrames: Boolean get() = active || pending.isNotEmpty()

  private var owner: Any? = null
  private var brush: Brush? = null
  private var pressures = FloatArray(INITIAL)
  private var times = LongArray(INITIAL)
  private var count = 0

  /** A stroke started; [id] identifies it for [end]. Pen path: only sets fields. */
  fun begin(id: Any, liveBrush: Brush) {
    owner = id
    brush = liveBrush
    count = 0
    active = true
  }

  /** One more point was added to the list given to the constructor. Pen path: two array stores. */
  fun add(pressure: Float, timeMs: Long) {
    if (count == pressures.size) {
      pressures = pressures.copyOf(count * 2)
      times = times.copyOf(count * 2)
    }
    pressures[count] = pressure
    times[count] = timeMs
    count++
  }

  /**
   * Pen-up: the stroke [id] is finished. Its points, colour and size are copied (once, here) into a
   * pending line that the projector keeps drawing until the committed stroke replaces it.
   */
  fun lift(id: Any?, nowMs: Long) {
    if (id == null || owner !== id || !active) return
    pending[id] = PendingLine(copyPoints(), minOf(points.size, count), colorArgb, size, nowMs)
    active = false
  }

  /**
   * Ink reported the stroke [id] finished and the page has taken it ([version] is the page's
   * change counter after that). The pending line is dropped by [dropCommitted] once a snapshot
   * built at or after [version] has been published.
   */
  fun committed(id: Any, version: Int) {
    val line = pending[id]
    if (line != null) line.committedVersion = version
    else if (owner === id) active = false // never lifted (no projector at pen-up): just stop
  }

  /** Cancelled, replaced by a clean shape, or otherwise gone without a commit: forget the stroke [id]. */
  fun end(id: Any?) {
    if (id == null) return
    pending.remove(id)
    if (owner === id) active = false
  }

  /**
   * Drops lines whose committed stroke is in a snapshot built at [builtVersion] or later, and any
   * line that never got committed within [TIMEOUT_MS] (safety net). Returns the lines still pending.
   */
  fun dropCommitted(builtVersion: Int, nowMs: Long): List<PendingLine> {
    if (pending.isEmpty()) return emptyList()
    val it = pending.values.iterator()
    while (it.hasNext()) {
      val line = it.next()
      val done = line.committedVersion in 0..builtVersion
      if (done || nowMs - line.liftedAt > TIMEOUT_MS) it.remove()
    }
    return if (pending.isEmpty()) emptyList() else ArrayList(pending.values)
  }

  /** Brush colour of the live stroke (ARGB), or 0 when there is none. */
  val colorArgb: Int get() = brush?.colorIntArgb ?: 0

  /** Brush size of the live stroke in page units. */
  val size: Float get() = brush?.size ?: 1f

  /** A copy of the live points as x0,y0,x1,y1... (page units); main thread. */
  fun copyPoints(): FloatArray {
    val n = minOf(points.size, count)
    val out = FloatArray(n * 2)
    for (i in 0 until n) {
      val p = points[i]
      out[2 * i] = p.x
      out[2 * i + 1] = p.y
    }
    return out
  }

  /** Stops whatever is live or pending, whichever stroke it is (page cleared, notebook closed). */
  fun endAll() {
    active = false
    pending.clear()
  }

  private companion object {
    const val INITIAL = 512
    /** A pending line that never got committed (should not happen) is dropped after this long. */
    const val TIMEOUT_MS = 5000L
  }
}
