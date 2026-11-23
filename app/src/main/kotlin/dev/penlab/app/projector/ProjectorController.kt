package dev.penlab.app.projector

import android.app.Activity
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.penlab.app.ink.InkCanvasView
import java.util.concurrent.Executors

/**
 * Projector view: while a projector or monitor is cabled to the tablet, show only the page on it,
 * while the tablet keeps its normal interface. Android lists such a screen as a "presentation
 * display"; a Presentation window on it holds the picture.
 *
 * One controller lives with the activity ([start] and [stop] follow onStart and onStop). It
 * watches for a presentation display, offers the feature once per connection, and, while on,
 * redraws the picture from the open notebook's canvas whenever something changes (see
 * [RenderScheduler]). It never works inside the touch or draw code: the canvas only tells it
 * "something changed" ([markDirty]) and it draws later, on its own thread.
 *
 * All methods are for the main thread.
 *
 * Not unit-tested: it needs a real second display. The pure parts are in ProjectorLogic.kt.
 */
class ProjectorController(private val activity: Activity) {
  private val displays = activity.getSystemService(DisplayManager::class.java)
  private val main = Handler(Looper.getMainLooper())

  /** A presentation display is connected right now (the "Projector view" menu entry shows). */
  var available by mutableStateOf(false)
    private set

  /** Projector view is on (the screen is kept awake). */
  var active by mutableStateOf(false)
    private set

  /** The "Projector connected" offer should be shown now; the screen calls [offerShown] when it has. */
  var offerPending by mutableStateOf(false)
    private set

  private var canvas: InkCanvasView? = null
  private var started = false
  /** Projector view was on when the activity stopped: it comes back at the next start. */
  private var resumeAtStart = false
  private var offered = false
  private var display: Display? = null
  private var presentation: ProjectorPresentation? = null
  private var sizeW = 0
  private var sizeH = 0

  // One render at a time, on a thread of its own; it gives way to the page work (low priority).
  private val renderThread = Executors.newSingleThreadExecutor { Thread(it, "penlab-projector").apply { priority = Thread.MIN_PRIORITY; isDaemon = true } }

  private val scheduler = RenderScheduler(
    PROJECTOR_DEBOUNCE_MS,
    SystemClock::uptimeMillis,
    { delay, action -> main.postDelayed(action, delay) },
    ::renderNow,
  )

  private val listener = object : DisplayManager.DisplayListener {
    override fun onDisplayAdded(displayId: Int) = refresh()
    override fun onDisplayRemoved(displayId: Int) = refresh()
    override fun onDisplayChanged(displayId: Int) = refresh()
  }

  /** The activity is visible: start watching for a projector. */
  fun start() {
    if (started) return
    started = true
    displays.registerDisplayListener(listener, main)
    refresh()
    if (resumeAtStart) {
      resumeAtStart = false
      begin()
    }
  }

  /** The activity is leaving the screen: stop watching and take the window down (back on at [start] if it was on). */
  fun stop() {
    if (!started) return
    started = false
    resumeAtStart = active
    displays.unregisterDisplayListener(listener)
    scheduler.cancel()
    closeWindow()
    available = false
    offerPending = false
  }

  /** A notebook's canvas is on screen: it is what the projector shows. */
  fun attach(canvas: InkCanvasView) {
    this.canvas?.onViewMoved = {}
    this.canvas = canvas
    canvas.onViewMoved = ::markDirty
    canvas.addOnLayoutChangeListener(layoutListener)
    evaluateOffer()
    if (active) markDirty()
  }

  /**
   * The notebook closed (back to the library). Projector view stays on, now showing only the empty
   * background, so the class never sees the library; the menu entry in the next notebook turns it off.
   */
  fun detach(canvas: InkCanvasView) {
    if (this.canvas !== canvas) return
    canvas.onViewMoved = {}
    canvas.removeOnLayoutChangeListener(layoutListener)
    this.canvas = null
    scheduler.cancel()
    presentation?.show(null, 0xFF0C0D0E.toInt())
  }

  private val layoutListener = android.view.View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
    if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) markDirty()
  }

  /** Something on the page changed: draw the projector's picture again soon (cheap). */
  fun markDirty() {
    if (active) scheduler.markDirty()
  }

  /** The screen has shown the offer. */
  fun offerShown() {
    offerPending = false
    offered = true
  }

  /** Turns Projector view on or off from the menu or the offer. */
  fun toggle() {
    if (active) end() else begin()
  }

  fun begin() {
    val target = display ?: return
    if (active) return
    val window = ProjectorPresentation(activity, target, ::onWindowSize, ::onWindowGone)
    try {
      window.show()
    } catch (e: android.view.WindowManager.InvalidDisplayException) {
      android.util.Log.w("PenLabProjector", "The projector went away before it could be used", e)
      return
    }
    presentation = window
    active = true
    offered = true
    offerPending = false
    markDirty()
  }

  /** Ends Projector view; the projector goes back to mirroring the tablet. */
  fun end() {
    scheduler.cancel()
    closeWindow()
  }

  private fun closeWindow() {
    val window = presentation
    presentation = null
    active = false
    sizeW = 0
    sizeH = 0
    window?.setOnDismissListener(null)
    if (window?.isShowing == true) window.dismiss()
  }

  /** The projector's window was laid out (or resized): draw at its size. */
  private fun onWindowSize(w: Int, h: Int) {
    sizeW = w
    sizeH = h
    markDirty()
  }

  /** Android closed the window itself (display unplugged): end quietly. */
  private fun onWindowGone() {
    if (presentation == null) return // we closed it ourselves
    scheduler.cancel()
    presentation = null
    active = false
    refresh()
  }

  /** Looks at which presentation displays exist and brings everything in line. */
  private fun refresh() {
    val found = displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
    display = found
    available = found != null
    if (found == null) {
      offered = false // a display plugged in later is offered again
      offerPending = false
      if (active) end()
    } else if (active && presentation?.display?.displayId != found.displayId) {
      end() // a different screen now: start again from the menu
    }
    evaluateOffer()
  }

  private fun evaluateOffer() {
    offerPending = offerPending || shouldOfferProjector(available, canvas != null, active, offered)
  }

  /** Takes the snapshot on the main thread, draws it on the render thread, shows it back on the main thread. */
  private fun renderNow(done: () -> Unit) {
    val target = canvas
    val window = presentation
    if (target == null || window == null || sizeW <= 0 || sizeH <= 0) {
      done()
      return
    }
    // The window's own size, but never more than 4K on the long side (memory and time).
    val limit = 3840f / maxOf(sizeW, sizeH).coerceAtLeast(1)
    val scale = if (limit < 1f) limit else 1f
    val w = (sizeW * scale).toInt().coerceAtLeast(1)
    val h = (sizeH * scale).toInt().coerceAtLeast(1)
    val frame = target.projectorSnapshot(w, h)
    if (frame == null) {
      done()
      return
    }
    renderThread.execute {
      val bitmap: Bitmap? =
        try {
          frame.render()
        } catch (e: Exception) {
          android.util.Log.e("PenLabProjector", "Could not draw the projector picture", e)
          null
        }
      main.post {
        if (bitmap != null && presentation === window) window.show(bitmap, frame.backgroundColor)
        done()
      }
    }
  }
}
