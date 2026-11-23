package dev.penlab.app.projector

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Display
import android.view.View
import android.view.WindowManager
import kotlin.math.min

/**
 * The window on the projector: one view that shows the latest picture of the page, centred and
 * letterboxed on the page's background colour. All the work of making the picture happens
 * elsewhere (see ProjectorController and InkCanvasView.projectorSnapshot); this only shows it.
 *
 * It cannot be unit-tested (it needs a real second display); it was checked on a tablet with an
 * external monitor.
 */
class ProjectorPresentation(
  context: Context,
  display: Display,
  /** Called once the window is laid out, and again if its size changes, with its size in pixels. */
  private val onSize: (width: Int, height: Int) -> Unit,
  /** Called when the system closes the window (the display went away). */
  private val onGone: () -> Unit,
) : Presentation(context, display) {

  private lateinit var picture: PictureView

  override fun onCreate(savedInstanceState: android.os.Bundle?) {
    super.onCreate(savedInstanceState)
    picture = PictureView(context)
    setContentView(picture)
    window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setOnDismissListener { onGone() }
  }

  /** Shows [bitmap] (drawn at this window's size) over [background]. Main thread. */
  fun show(bitmap: Bitmap?, background: Int) {
    picture.background = background
    picture.bitmap = bitmap
    picture.invalidate()
  }

  private inner class PictureView(context: Context) : View(context) {
    var bitmap: Bitmap? = null
    var background: Int = 0xFF000000.toInt()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val target = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
      if (w > 0 && h > 0) onSize(w, h)
    }

    override fun onDraw(canvas: Canvas) {
      canvas.drawColor(background)
      val b = bitmap ?: return
      // Letterbox: the picture is made at this window's shape, so this normally fills it.
      val scale = min(width.toFloat() / b.width, height.toFloat() / b.height)
      val w = b.width * scale
      val h = b.height * scale
      target.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
      canvas.drawBitmap(b, null, target, paint)
    }
  }
}
