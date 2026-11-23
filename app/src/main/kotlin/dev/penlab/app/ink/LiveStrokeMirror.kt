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
    endingFrames = -1
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
    // Not off at once: the committed stroke reaches the projector on its next frame, so the
    // preview stays for a few more projector frames and the two overlap instead of leaving a
    // one-frame gap (the flash at pen-up). They sit in the same place, so the overlap is invisible.
    if (id != null && owner === id && active && endingFrames < 0) endingFrames = HANDOFF_FRAMES
  }

  /** Frames left before the preview stops after pen-up; -1 while the pen is down. */
  private var endingFrames = -1

  /**
   * PROTOTYPE (render-thread projector): one main-thread frame step. Counts down the pen-up
   * overlap exactly like [draw] does, but without drawing. Call once per published frame, before
   * reading [active].
   */
  fun tick() {
    if (active && endingFrames >= 0 && endingFrames-- == 0) active = false
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

  /** Stops whatever is live, whichever stroke it is (page cleared). */
  fun endAll() {
    active = false
    endingFrames = -1
  }

  /**
   * Draws the stroke so far onto [canvas], which already maps page units to the projector through
   * [pageToProjector]. Does nothing when no stroke is live.
   */
  fun draw(canvas: Canvas, pageToProjector: Matrix) {
    val liveBrush = brush ?: return
    if (!active) return
    // Drawn as a plain round-capped line in the brush's colour and size (page units; the canvas
    // already maps page units to the projector). Jetpack Ink's InProgressStroke drew nothing on
    // the Presentation's canvas (verified on a Tab S9, 23 Nov), so the projector shows this
    // preview while the pen is down and the real stroke from pen-up on. Only the points that
    // arrived since the last frame are appended, so a long stroke costs no more per frame.
    if (drawnGeneration != generation) {
      path.rewind()
      drawnGeneration = generation
      fed = 0
    }
    val available = minOf(points.size, count)
    for (i in fed until available) {
      val p = points[i]
      if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    fed = available
    if (available == 0) return
    linePaint.color = liveBrush.colorIntArgb
    linePaint.strokeWidth = liveBrush.size
    if (available == 1) {
      val p = points[0]
      canvas.drawPoint(p.x, p.y, linePaint)
    } else {
      canvas.drawPath(path, linePaint)
    }
    if (endingFrames >= 0 && endingFrames-- == 0) active = false
  }

  private val path = android.graphics.Path()
  private val linePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    style = android.graphics.Paint.Style.STROKE
    strokeCap = android.graphics.Paint.Cap.ROUND
    strokeJoin = android.graphics.Paint.Join.ROUND
  }

  private companion object {
    const val INITIAL = 512
    /** Projector frames the preview overlaps the committed stroke after pen-up. */
    const val HANDOFF_FRAMES = 3
  }
}
