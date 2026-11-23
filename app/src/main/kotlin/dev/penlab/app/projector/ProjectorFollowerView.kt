package dev.penlab.app.projector

import android.content.Context
import android.graphics.Canvas
import android.view.View
import dev.penlab.app.ink.InkCanvasView

/**
 * The page on the projector, drawn live: every frame it asks the tablet's canvas to draw the scene
 * into this view ([InkCanvasView.drawForProjector]) from the same objects the tablet uses, so it
 * pans, zooms and shows the stroke under the pen as smoothly as the display allows.
 *
 * It shares the UI thread with the tablet's own interface, so it must stay cheap: it holds no
 * pictures, builds nothing and allocates nothing per frame. It draws only when something asks
 * (the canvas calls postInvalidateOnAnimation after each of its own draws, and the controller on
 * each change), at most once per frame, and keeps asking for itself only while a stroke is live.
 *
 * Needs a hardware-accelerated window (the stroke recordings are RenderNodes); the presentation
 * requests one.
 */
internal class ProjectorFollowerView(context: Context) : View(context) {
  /** The notebook whose page is shown; null shows only the empty background. */
  var source: InkCanvasView? = null
    set(value) {
      field = value
      invalidate()
    }

  override fun onDraw(canvas: Canvas) {
    val canvasView = source
    if (canvasView == null) {
      canvas.drawColor(EMPTY_COLOR)
      return
    }
    val t0 = System.nanoTime() // TEMP timing
    val live = canvasView.drawForProjector(canvas, width, height)
    val ms = (System.nanoTime() - t0) / 1e6
    val now = android.os.SystemClock.uptimeMillis()
    if (lastFrameAt > 0 && (ms > 4 || now - lastFrameAt > 25)) android.util.Log.d("PenLabTime", "draw=%.1fms gap=%dms live=%b".format(ms, now - lastFrameAt, live))
    lastFrameAt = now
    if (live) postInvalidateOnAnimation()
  }

  private var lastFrameAt = 0L

  private companion object {
    const val EMPTY_COLOR = 0xFF0C0D0E.toInt()
  }
}
