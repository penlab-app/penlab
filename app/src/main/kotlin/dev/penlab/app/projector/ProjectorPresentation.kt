package dev.penlab.app.projector

import android.app.Presentation
import android.content.Context
import android.view.Display
import android.view.WindowManager
import dev.penlab.app.ink.InkCanvasView

/**
 * The window on the projector: one [ProjectorFollowerView] that draws the open notebook's page
 * live. All the drawing logic is in InkCanvasView.drawForProjector; this only owns the window.
 *
 * It cannot be unit-tested (it needs a real second display); it was checked on a tablet with an
 * external monitor.
 */
class ProjectorPresentation(
  context: Context,
  display: Display,
  /** Called when the system closes the window (the display went away). */
  private val onGone: () -> Unit,
) : Presentation(context, display) {

  private lateinit var follower: ProjectorFollowerView
  private var followed: InkCanvasView? = null

  override fun onCreate(savedInstanceState: android.os.Bundle?) {
    super.onCreate(savedInstanceState)
    follower = ProjectorFollowerView(context)
    setContentView(follower)
    window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
    setOnDismissListener { onGone() }
  }

  /** Dismissed, by us or by the system: let go of the canvas so it stops asking this window to redraw. */
  override fun onStop() {
    follow(null)
    super.onStop()
  }

  /** Shows [canvas]'s page (null: just the empty background). Main thread. */
  fun follow(canvas: InkCanvasView?) {
    if (canvas !== followed) {
      followed?.projectorView = null
      followed = canvas
    }
    if (!::follower.isInitialized) return
    follower.source = canvas
    canvas?.projectorView = follower
  }

  /** Draws the page again at the next frame. */
  fun redraw() {
    if (::follower.isInitialized) follower.postInvalidateOnAnimation()
  }
}
