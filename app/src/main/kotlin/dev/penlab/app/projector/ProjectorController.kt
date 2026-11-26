package dev.penlab.app.projector

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.penlab.app.ink.InkCanvasView

/**
 * Projector view: while a projector or monitor is cabled to the tablet, show only the page on it,
 * while the tablet keeps its normal interface. Android lists such a screen as a "presentation
 * display"; a Presentation window on it holds the picture.
 *
 * One controller lives with the activity ([start] and [stop] follow onStart and onStop). It
 * watches for a presentation display, offers the feature once per connection, and, while on,
 * points the presentation's surface at the open notebook's canvas. The canvas publishes immutable
 * snapshots to the surface's own render thread (see [ProjectorSurfaceView]); this class only asks
 * for a new snapshot when something changes ([markDirty], one cheap call, coalesced to once per
 * frame) and never renders anything itself.
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
    presentation?.follow(canvas)
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
    presentation?.follow(null)
  }

  private val layoutListener = android.view.View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
    if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) markDirty()
  }

  /** Something on the page changed: the projector redraws at the next frame (cheap). */
  fun markDirty() {
    presentation?.redraw()
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
    val window = ProjectorPresentation(activity, target, ::onWindowGone)
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
    window.follow(canvas)
  }

  /** Ends Projector view; the projector goes back to mirroring the tablet. */
  fun end() {
    closeWindow()
  }

  private fun closeWindow() {
    val window = presentation
    presentation = null
    active = false
    window?.setOnDismissListener(null)
    if (window?.isShowing == true) window.dismiss()
  }

  /** Android closed the window itself (display unplugged): end quietly. */
  private fun onWindowGone() {
    if (presentation == null) return // we closed it ourselves
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
}
