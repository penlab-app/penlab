package dev.penlab.app.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullToAddTest {
  @Test
  fun anIdleViewPastTheEndShowsNothing() {
    // After a rotation or a fit: far past the end, no touch, no spring-back.
    assertFalse(PullToAdd.isLive(distance = 900f, atDown = 0f, touching = false, springingBack = false, slop = 8f))
  }

  @Test
  fun aTouchOnlyCountsAPullItMadeItself() {
    // Already 300 past the end when the finger came down: resting there is not a pull.
    assertFalse(PullToAdd.isLive(300f, 300f, touching = true, springingBack = false, slop = 8f))
    assertTrue(PullToAdd.isLive(320f, 300f, touching = true, springingBack = false, slop = 8f))
  }

  @Test
  fun springBackShowsTheHintUntilItEnds() {
    assertTrue(PullToAdd.isLive(120f, 0f, touching = false, springingBack = true, slop = 8f))
    assertFalse(PullToAdd.isLive(0f, 0f, touching = false, springingBack = true, slop = 8f))
  }

  @Test
  fun pastThresholdNeedsALivePullWithAFingerDownPastTheThreshold() {
    assertFalse(PullToAdd.isPastThreshold(live = false, touching = true, distance = 900f, threshold = 500f))
    assertFalse(PullToAdd.isPastThreshold(live = true, touching = false, distance = 900f, threshold = 500f))
    assertFalse(PullToAdd.isPastThreshold(live = true, touching = true, distance = 400f, threshold = 500f))
    assertTrue(PullToAdd.isPastThreshold(live = true, touching = true, distance = 600f, threshold = 500f))
  }

  @Test
  fun heldSinceStartsOnCrossingKeepsWhilePastAndResetsOnDropping() {
    val none = PullToAdd.NOT_PAST
    assertEquals(none, PullToAdd.heldSince(none, past = false, now = 1000L))
    assertEquals(1000L, PullToAdd.heldSince(none, past = true, now = 1000L))
    assertEquals(1000L, PullToAdd.heldSince(1000L, past = true, now = 1200L))
    assertEquals(none, PullToAdd.heldSince(1000L, past = false, now = 1200L))
  }

  @Test
  fun aQuickSwipeNeverGetsReadyButADeliberateHoldDoes() {
    // Crossed the threshold at t=1000 and let go 80 ms later (a swipe): not ready.
    assertFalse(PullToAdd.isReady(since = 1000L, now = 1080L))
    // Held until just before the hold ends: not ready; at the hold time: ready.
    assertFalse(PullToAdd.isReady(1000L, 1000L + PullToAdd.HOLD_MS - 1))
    assertTrue(PullToAdd.isReady(1000L, 1000L + PullToAdd.HOLD_MS))
    // Never past the threshold: never ready, however late.
    assertFalse(PullToAdd.isReady(PullToAdd.NOT_PAST, now = 1_000_000L))
  }

  @Test
  fun remainingTimeCountsDownToZero() {
    assertEquals(0L, PullToAdd.remainingMs(PullToAdd.NOT_PAST, 5000L))
    assertEquals(PullToAdd.HOLD_MS, PullToAdd.remainingMs(1000L, 1000L))
    assertEquals(PullToAdd.HOLD_MS - 100, PullToAdd.remainingMs(1000L, 1100L))
    assertEquals(0L, PullToAdd.remainingMs(1000L, 9000L))
  }

  @Test
  fun resizeMovesTheEndBackUpToTheBottomButNeverPastTheTopLimit() {
    // 600 px of empty space under the end, the content is scrolled 2000 px above its resting top: move down 600.
    assertEquals(600f, ViewSettle.shiftDown(pastEnd = 600f, pageTop = -2000f, topLimit = 80f), 0f)
    // A short notebook: its top is already at the limit, so nothing moves.
    assertEquals(0f, ViewSettle.shiftDown(pastEnd = 600f, pageTop = 80f, topLimit = 80f), 0f)
    // Only 100 px of room before the top reaches its limit: move just that far.
    assertEquals(100f, ViewSettle.shiftDown(pastEnd = 600f, pageTop = -20f, topLimit = 80f), 0f)
    // The end is still below the bottom of the screen: nothing to do.
    assertEquals(0f, ViewSettle.shiftDown(pastEnd = -300f, pageTop = -2000f, topLimit = 80f), 0f)
  }

  // ---- Resize settle: 4 A4 portrait pages (2100 x 2970 units) at 75%, Tab S9 ----------------------

  private val pageW = 2100f
  private val end = 4 * 2970f + 3 * 120f // the content's end in page units (pages + gaps)
  private val scale75 = 0.809f // 75% of true size at about 274 dpi: the page is 1700 px wide

  private fun resize(view: ViewSettle.Level, oldW: Float, oldH: Float, newW: Float, newH: Float, left: Float = 0f, right: Float = 0f) =
    ViewSettle.afterResize(view, pageW, end, oldW, oldH, newW, newH, left, right, 0f, 160f, 0.1f, 8f)

  @Test
  fun landscapeToPortraitOnATallWidePageFitsEdgeToEdgeAndRestsAtTheEnd() {
    // Landscape 2560x1600, page centred (narrower than the view), scrolled so the end is at the bottom.
    val tx = (2560f - pageW * scale75) / 2f
    val view = ViewSettle.Level(scale75, tx, 1600f - end * scale75)
    val out = resize(view, 2560f, 1600f, 1600f, 2560f)
    // Wider than the new view: fitted, edges on the screen edges (the reported bug left it 410 px right).
    assertEquals(1600f / pageW, out.scale, 1e-6f)
    assertEquals(0f, out.tx, 0f)
    // The end of the notebook rests at the bottom of the screen, not at 59% of it.
    assertEquals(2560f, out.ty + end * out.scale, 0.01f)
  }

  @Test
  fun aPageStillWiderThanTheViewKeepsItsEdgesBeyondTheScreenEdges() {
    // Zoomed in (page 3400 px) and scrolled sideways; a height-only change must not strand it right of the edge.
    val view = ViewSettle.Level(1.619f, 300f, -9000f)
    val out = resize(view, 1600f, 2560f, 1600f, 2000f)
    assertEquals(0f, out.tx, 0f) // clamped: left edge at the screen edge, never inside it
    assertTrue(out.tx + pageW * out.scale >= 1600f)
  }

  @Test
  fun aNarrowerPageIsCentredAfterTheResize() {
    val view = ViewSettle.Level(0.3f, 0f, 160f) // 630 px page
    val out = resize(view, 1600f, 2560f, 2560f, 1600f)
    assertEquals((2560f - pageW * out.scale) / 2f, out.tx, 0.01f)
  }

  @Test
  fun resizeUsesThePanClamp() {
    assertEquals(100f, ViewSettle.clampX(40f, 800f, 1000f, 100f, 100f), 0f) // narrower: centred in the free space
    assertEquals(0f, ViewSettle.clampX(410f, 1700f, 1600f, 0f, 0f), 0f) // wider: left edge at the screen edge at most
    assertEquals(-100f, ViewSettle.clampX(-500f, 1700f, 1600f, 0f, 0f), 0f) // right edge at the screen edge at least
    assertEquals(160f, ViewSettle.clampTop(300f, 160f), 0f)
  }

  @Test
  fun aSlipOfADegreeIsLevelButADeliberateTurnIsNot() {
    assertTrue(ViewSettle.isNearLevel(0.5f))
    assertTrue(ViewSettle.isNearLevel(-1f))
    assertFalse(ViewSettle.isNearLevel(5f))
  }
}
