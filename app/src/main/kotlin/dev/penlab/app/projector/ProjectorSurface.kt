package dev.penlab.app.projector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import dev.penlab.app.ink.InkCanvasView

/**
 * PROTOTYPE. Immutable hand-off from the tablet's main thread to the projector's render thread.
 * Everything here is either a primitive, a never-mutated array/list, or an immutable ink [Stroke],
 * so the render thread can read it without locks.
 */
class ProjectorSnapshot(
  /** page -> projector pixels (9 values), null until the surface has a size. */
  val matrix: FloatArray?,
  val deskColor: Int,
  /** Paper colour for [pageRects]; 0 on an endless canvas (the desk colour is the page). */
  val pageColor: Int,
  /** left,top,right,bottom per visible page, page units. */
  val pageRects: FloatArray?,
  val strokes: List<Stroke>,
  /** Live stroke points x,y,x,y... (page units) or null when no stroke is live. */
  val livePoints: FloatArray?,
  val liveCount: Int,
  val liveColor: Int,
  val liveSize: Float,
)

/** What the canvas publishes to. */
interface ProjectorSink {
  val targetWidth: Int
  val targetHeight: Int
  fun onSnapshot(s: ProjectorSnapshot)
}

/**
 * The projector's own surface with its own render thread (HandlerThread + Choreographer on that
 * thread), so projector frames never wait for the tablet's UI thread or RenderThread queue.
 * Draws only when a new snapshot arrived or a stroke is live.
 *
 * Committed ink is redrawn stroke by stroke every frame; caching it (a Picture or bitmap of
 * committed ink) is the next step.
 */
internal class ProjectorSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, ProjectorSink {
  @Volatile override var targetWidth = 0
  @Volatile override var targetHeight = 0
  @Volatile private var snapshot: ProjectorSnapshot? = null

  /** The tablet canvas being followed; main thread only. Used to ask for a republish on resize. */
  var source: InkCanvasView? = null

  private val thread = HandlerThread("PenLabProjector").also { it.start() }
  private val handler = Handler(thread.looper)
  private var choreographer: Choreographer? = null // created ON the render thread
  private var surface: Surface? = null // render thread only
  private var scheduled = false // render thread only
  private var lastDrawn: ProjectorSnapshot? = null // render thread only
  private var lastFrameAt = 0L // render thread only

  private val renderer = CanvasStrokeRenderer.create()
  private val matrix = Matrix()
  private val paint = Paint()
  private val rect = RectF()
  private val liveStroke = android.graphics.Path()
  private val livePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }

  init {
    holder.addCallback(this)
    handler.post { choreographer = Choreographer.getInstance() }
  }

  override fun onSnapshot(s: ProjectorSnapshot) {
    snapshot = s
    handler.post(poke)
  }

  private val poke = Runnable { schedule() }

  private fun schedule() {
    val c = choreographer ?: return
    if (scheduled || surface == null) return
    scheduled = true
    c.postFrameCallback(frame)
  }

  private val frame = Choreographer.FrameCallback {
    scheduled = false
    val s = snapshot ?: return@FrameCallback
    val surf = surface ?: return@FrameCallback
    val live = s.livePoints != null
    if (!live && s === lastDrawn) {
      lastFrameAt = 0
      return@FrameCallback
    }
    val now = SystemClock.uptimeMillis()
    // TEMP timing
    if (live && lastFrameAt > 0 && now - lastFrameAt > 25) Log.d("PenLabTime", "projector gap=${now - lastFrameAt}ms")
    lastFrameAt = if (live) now else 0
    draw(surf, s)
    lastDrawn = s
    if (live) schedule()
  }

  private fun draw(surf: Surface, s: ProjectorSnapshot) {
    val canvas: Canvas = try {
      surf.lockHardwareCanvas()
    } catch (e: Exception) {
      return
    }
    try {
      canvas.drawColor(s.deskColor)
      val m = s.matrix ?: return
      matrix.setValues(m)
      val rects = s.pageRects
      if (rects != null && s.pageColor != 0) {
        paint.color = s.pageColor
        canvas.save()
        canvas.concat(matrix)
        var i = 0
        while (i + 3 < rects.size) {
          rect.set(rects[i], rects[i + 1], rects[i + 2], rects[i + 3])
          canvas.drawRect(rect, paint)
          i += 4
        }
        canvas.restore()
      }
      for (stroke in s.strokes) renderer.draw(canvas, stroke, matrix)
      val pts = s.livePoints
      if (pts != null && s.liveCount > 0) {
        canvas.save()
        canvas.concat(matrix)
        livePaint.color = s.liveColor
        livePaint.strokeWidth = s.liveSize
        if (s.liveCount == 1) {
          canvas.drawPoint(pts[0], pts[1], livePaint)
        } else {
          liveStroke.rewind()
          liveStroke.moveTo(pts[0], pts[1])
          for (i in 1 until s.liveCount) liveStroke.lineTo(pts[2 * i], pts[2 * i + 1])
          canvas.drawPath(liveStroke, livePaint)
        }
        canvas.restore()
      }
    } finally {
      surf.unlockCanvasAndPost(canvas)
    }
  }

  override fun surfaceCreated(holder: SurfaceHolder) {
    val surf = holder.surface
    handler.post { surface = surf; lastDrawn = null; schedule() }
  }

  override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
    targetWidth = w
    targetHeight = h
    source?.requestProjectorPublish()
    handler.post { lastDrawn = null; schedule() }
  }

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    // Wait until the render thread let go of the surface before it is torn down.
    val latch = java.util.concurrent.CountDownLatch(1)
    handler.post { surface = null; latch.countDown() }
    latch.await(200, java.util.concurrent.TimeUnit.MILLISECONDS)
  }

  /** Called by the presentation when it stops. */
  fun shutdown() {
    handler.removeCallbacksAndMessages(null)
    thread.quitSafely()
  }
}
