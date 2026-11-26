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
}
