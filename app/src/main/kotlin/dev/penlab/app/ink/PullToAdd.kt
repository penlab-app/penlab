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
 * The notebook view's resting rules (pure, so they are unit-tested; [InkCanvasView] applies them):
 * the normal pan clamp, and where the view lands after the screen's size changed (a rotation, a
 * split-screen resize). The resize reuses the pan clamp, so there is exactly one rule for the sides.
 */
internal object ViewSettle {
  /** A view turned by no more than this is a slip of the fingers, not a choice: treated as level. */
  const val LEVEL_TOLERANCE_DEG = 1f

  /** Whether a view turned by [angleDegrees] counts as level (the notebook rules still apply). */
  fun isNearLevel(angleDegrees: Float): Boolean = kotlin.math.abs(angleDegrees) <= LEVEL_TOLERANCE_DEG

  /**
   * The pan clamp for the page's left edge [tx] (px) when the page is [pageWidthPx] wide: narrower
   * than the free space between the toolbars, it stays centred; wider, its edges never come inside
   * the screen edges.
   */
  fun clampX(tx: Float, pageWidthPx: Float, width: Float, leftInset: Float, rightInset: Float): Float {
    val free = width - leftInset - rightInset
    return if (pageWidthPx <= free) leftInset + (free - pageWidthPx) / 2f
    else tx.coerceIn(width - rightInset - pageWidthPx, leftInset)
  }

  /** The first page's top cannot be pulled down past [topLimit]. */
  fun clampTop(ty: Float, topLimit: Float): Float = ty.coerceAtMost(topLimit)

  /**
   * How far (px, positive = down) to move the content so the end of the notebook is not left above
   * the bottom of the screen with an empty area under it. [pastEnd] is how far the screen's bottom
   * is beyond the end of the last page; [pageTop] and [topLimit] are the first page's top and the
   * lowest it may sit. The page's top never moves below [topLimit]: a short notebook stays at the top.
   */
  fun shiftDown(pastEnd: Float, pageTop: Float, topLimit: Float): Float =
    if (pastEnd <= 0f) 0f else minOf(pastEnd, (topLimit - pageTop).coerceAtLeast(0f))

  /** A level view: page units to pixels [scale], the page's left edge [tx] and top [ty] on screen. */
  data class Level(val scale: Float, val tx: Float, val ty: Float)

  /**
   * Where a level view lands after the size went from [oldW]x[oldH] to [newW]x[newH].
   *  - A view that was at the fit zoom for the OLD size (within [FITTED_SHARE]) is fitted to the new width.
   *  - Any other zoom is the person's own choice and is kept; the page point that was at the top-centre
   *    of the view stays at the top-centre where the clamp allows.
   *  - The end of the notebook ([contentEnd], page units) is slid back down to the bottom of the
   *    screen above [bottomInset] if the taller screen left it high.
   *  - Then the ordinary pan clamp ([clampX], [clampTop]) has the last word, on both axes.
   */
  fun afterResize(
    view: Level, pageWidth: Float, contentEnd: Float,
    oldW: Float, oldH: Float, newW: Float, newH: Float,
    leftInset: Float, rightInset: Float, bottomInset: Float, topLimit: Float,
    minZoom: Float, maxZoom: Float,
  ): Level {
    var scale = view.scale
    var tx = view.tx
    var ty = view.ty
    val oldFree = oldW - leftInset - rightInset
    val newFree = newW - leftInset - rightInset
    val fitScale = (oldFree / pageWidth).coerceIn(minZoom, maxZoom)
    val wasFitted = kotlin.math.abs(scale - fitScale) / fitScale < FITTED_SHARE
    if (wasFitted) {
      val middleY = (oldH / 2f - ty) / scale
      scale = (newFree / pageWidth).coerceIn(minZoom, maxZoom)
      tx = leftInset
      ty = newH / 2f - middleY * scale
    } else {
      val centreX = (oldW / 2f - tx) / scale // the page point at the top-centre; the top edge keeps its ty
      tx = newW / 2f - centreX * scale
    }
    val pastEnd = ((newH - bottomInset - ty) / scale - contentEnd) * scale
    ty += shiftDown(pastEnd, ty, topLimit)
    return Level(scale, clampX(tx, pageWidth * scale, newW, leftInset, rightInset), clampTop(ty, topLimit))
  }

  /** Delta (px) to shift `MTRANS_X` and `MTRANS_Y` so transformed pages stay in view. */
  data class ClampDelta(val dx: Float, val dy: Float)

  /**
   * Computes the screen-space translation delta needed to keep a notebook's pages in view for
   * any orientation (level, 90°/-90°, 180°, or arbitrary rotation).
   *
   * - Vertical clamping keeps the notebook's topmost screen corner at or below [topLimit].
   * - Horizontal clamping uses the active page when near-vertical (`|cos| >= |sin|`) so tilted
   *   multi-page notebooks don't drift sideways due to off-screen pages, and uses the full
   *   notebook strip when turned sideways (`|sin| > |cos|`) so users can pan across all pages.
   */
  fun clampPagesInView(
    matrixValues: FloatArray,
    pageWidth: Float,
    pageHeight: Float,
    activePageTop: Float,
    totalHeight: Float,
    screenWidth: Float,
    leftInset: Float,
    rightInset: Float,
    topLimit: Float,
  ): ClampDelta {
    val sx = matrixValues[0]
    val kx = matrixValues[1]
    val tx = matrixValues[2]
    val ky = matrixValues[3]
    val sy = matrixValues[4]
    val ty = matrixValues[5]

    var minY = Float.POSITIVE_INFINITY
    fun checkY(px: Float, py: Float) {
      val y = ky * px + sy * py + ty
      if (y < minY) minY = y
    }
    checkY(0f, 0f)
    checkY(pageWidth, 0f)
    checkY(0f, totalHeight)
    checkY(pageWidth, totalHeight)

    val sideways = kotlin.math.abs(ky) > kotlin.math.abs(sx)
    val y0 = if (sideways) 0f else activePageTop
    val y1 = if (sideways) totalHeight else (activePageTop + pageHeight)
    var minX = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    fun checkX(px: Float, py: Float) {
      val x = sx * px + kx * py + tx
      if (x < minX) minX = x
      if (x > maxX) maxX = x
    }
    checkX(0f, y0)
    checkX(pageWidth, y0)
    checkX(0f, y1)
    checkX(pageWidth, y1)

    val clampedLeft = clampX(minX, maxX - minX, screenWidth, leftInset, rightInset)
    val clampedTop = clampTop(minY, topLimit)
    return ClampDelta(dx = clampedLeft - minX, dy = clampedTop - minY)
  }

  /** "Fitted" means within this share of the free width. */
  private const val FITTED_SHARE = 0.02f
}
