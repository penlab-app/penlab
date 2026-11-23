package dev.penlab.app.ink

import android.graphics.Canvas
import android.graphics.Matrix
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.InProgressStroke
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.StrokeInput

/**
 * The stroke under the pen right now, as the projector's follower view draws it.
 *
 * PERFORMANCE CONTRACT. The pen's own path (InkCanvasView.onTouchEvent -> Jetpack Ink's front
 * buffer) is the app's first priority and this class must never slow it. So the pen side does
 * almost nothing:
 *  - [begin] and [end] set a couple of fields;
 *  - [add] stores one pressure and one time into arrays that are already allocated (they grow by
 *    doubling, so a long stroke allocates a handful of times, never per event);
 *  - the points themselves are NOT copied: the tablet already keeps them for shape recognition
 *    (InkCanvasView.strokePoints, page units) and this class only reads that list.
 * Everything else (turning points into a stroke shape and drawing it) happens in [draw], which
 * runs from the follower view's onDraw, once per projector frame, after the pen's own frame work.
 * The follower asks for its next frame itself while [active], so the pen path never invalidates
 * anything on the projector.
 *
 * [draw] feeds only the points that arrived since the last frame into an [InProgressStroke]
 * (Ink's incremental stroke builder), so a long stroke does not cost more per frame than a short
 * one. Everything here runs on the main thread, which is also where the pen's events are handled,
 * so nothing needs locking.
 *
 * It uses the brush the tablet draws the live stroke with (screen colours), so the live stroke
 * looks like the tablet's. Tilt and orientation are not carried over, so a brush that reacts to
 * them could differ slightly until the pen lifts and the committed stroke takes over.
 */
internal class LiveStrokeMirror(
  /** The current stroke's points in page units, appended by the tablet; read-only here. */
  private val points: List<Point>,
) {
  /** A stroke is being drawn (or has been lifted but not yet committed to the page). */
  var active = false
    private set

  private var owner: Any? = null
  private var brush: Brush? = null
  private var pressures = FloatArray(INITIAL)
  private var times = LongArray(INITIAL)
  private var count = 0

  /** Bumped by [begin]; [draw] restarts its stroke when it sees a new one. */
  private var generation = 0
  private var drawnGeneration = -1
  private var fed = 0

  // Made on first use, in [draw], never on the pen's path.
  private var stroke: InProgressStroke? = null
  private var batch: MutableStrokeInputBatch? = null
  private var noPredicted: MutableStrokeInputBatch? = null
  private var renderer: CanvasStrokeRenderer? = null
  private var lastTime = 0L

  /** A stroke started; [id] identifies it for [end]. Pen path: only sets fields. */
  fun begin(id: Any, liveBrush: Brush) {
    owner = id
    brush = liveBrush
    count = 0
    generation++
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

  /** The stroke [id] is on the page now (or was cancelled): stop drawing it. A newer stroke is left alone. */
  fun end(id: Any?) {
    if (id != null && owner === id) active = false
  }

  /** Stops whatever is live, whichever stroke it is (page cleared). */
  fun endAll() {
    active = false
  }

  /**
   * Draws the stroke so far onto [canvas], which already maps page units to the projector through
   * [pageToProjector]. Does nothing when no stroke is live.
   */
  fun draw(canvas: Canvas, pageToProjector: Matrix) {
    val liveBrush = brush ?: return
    if (!active) return
    val inputs = batch ?: MutableStrokeInputBatch().also { batch = it }
    val empty = noPredicted ?: MutableStrokeInputBatch().also { noPredicted = it }
    try {
      if (drawnGeneration != generation || stroke == null) {
        // A fresh builder for each stroke (cheap, and no state to reset).
        stroke = InProgressStroke().also { it.start(liveBrush) }
        drawnGeneration = generation
        fed = 0
        lastTime = 0L
      }
      val live = stroke ?: return
      val available = minOf(points.size, count)
      if (available > fed) {
        inputs.clear()
        val base = times[0]
        for (i in fed until available) {
          // Ink wants times that never go backwards.
          val t = maxOf(times[i] - base, lastTime)
          lastTime = t
          // add(tool, x, y, time, strokeUnitLengthCm, pressure): the 5th argument is NOT pressure
          // (passing pressure there made Ink reject the batch for mismatched unit lengths).
          inputs.add(InputToolType.STYLUS, points[i].x, points[i].y, t, StrokeInput.NO_STROKE_UNIT_LENGTH, pressures[i].coerceIn(0f, 1f))
        }
        fed = available
        live.enqueueInputs(inputs, empty)
      }
      if (live.getInputCount() == 0) return
      live.updateShape(lastTime)
      (renderer ?: CanvasStrokeRenderer.create().also { renderer = it }).draw(canvas, live, pageToProjector)
    } catch (e: RuntimeException) {
      // A bad point must not take the projector down: show the committed stroke at pen-up instead.
      android.util.Log.w("PenLabProjector", "Live stroke not drawn", e)
      active = false
    }
  }

  private companion object {
    const val INITIAL = 512
  }
}
