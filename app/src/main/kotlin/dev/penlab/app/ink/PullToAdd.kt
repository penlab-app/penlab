package dev.penlab.app.ink

/** The rule for when the "pull to add a page" hint exists (kept apart from the view so it is unit-tested). */
internal object PullToAdd {
  /**
   * The hint belongs on screen only while a touch is pulling the page past the end by more than it
   * already was when the touch began ([slop] more), or while the page springs back. Being past the
   * end by geometry alone (short notebook, turned tablet, fitted page) is not a pull.
   */
  fun isLive(distance: Float, atDown: Float, touching: Boolean, springingBack: Boolean, slop: Float): Boolean =
    distance > 0f && (springingBack || (touching && distance > atDown + slop))

  /** Letting go adds a page only when the live pull has passed [threshold] with a finger down. */
  fun isReady(live: Boolean, touching: Boolean, distance: Float, threshold: Float): Boolean =
    live && touching && distance > threshold
}
