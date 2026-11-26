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

  /** A live pull with a finger down has gone past [threshold] (the progress bar is full). */
  fun isPastThreshold(live: Boolean, touching: Boolean, distance: Float, threshold: Float): Boolean =
    live && touching && distance > threshold

  /** How long (ms) the finger must stay past the threshold before letting go adds a page. */
  const val HOLD_MS = 350L

  /** "Not past the threshold" marker for the timestamp kept by [heldSince]. */
  const val NOT_PAST = -1L

  /**
   * When the pull first went past the threshold (and stayed there): [now] on the frame it crosses,
   * the earlier time while it stays past, [NOT_PAST] as soon as it drops back or the touch ends.
   */
  fun heldSince(previous: Long, past: Boolean, now: Long): Long =
    if (!past) NOT_PAST else if (previous == NOT_PAST) now else previous

  /**
   * Letting go adds a page only when the pull has been past the threshold for [HOLD_MS]. A quick
   * swipe or fling crosses the threshold and lets go within a few frames, so it never adds one.
   */
  fun isReady(since: Long, now: Long, holdMs: Long = HOLD_MS): Boolean =
    since != NOT_PAST && now - since >= holdMs

  /** Milliseconds still to wait before [isReady] turns true (0 when not past the threshold or already ready). */
  fun remainingMs(since: Long, now: Long, holdMs: Long = HOLD_MS): Long =
    if (since == NOT_PAST) 0L else (holdMs - (now - since)).coerceAtLeast(0L)
}

/**
 * Where the page should rest after the view's size changed (a rotation, a split-screen resize).
 * Pure so it is unit-tested; [InkCanvasView] applies it.
 */
internal object ViewSettle {
  /**
   * How far (px, positive = down) to move the content so the end of the notebook is not left above
   * the bottom of the screen with an empty area under it. [pastEnd] is how far the screen's bottom
   * is beyond the end of the last page; [pageTop] and [topLimit] are the first page's top and the
   * lowest it may sit. The page's top never moves below [topLimit]: a short notebook stays at the top.
   */
  fun shiftDown(pastEnd: Float, pageTop: Float, topLimit: Float): Float =
    if (pastEnd <= 0f) 0f else minOf(pastEnd, (topLimit - pageTop).coerceAtLeast(0f))
}
