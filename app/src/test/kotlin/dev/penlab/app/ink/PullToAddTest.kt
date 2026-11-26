package dev.penlab.app.ink

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
  fun readyNeedsALivePullWithAFingerDownPastTheThreshold() {
    assertFalse(PullToAdd.isReady(live = false, touching = true, distance = 900f, threshold = 500f))
    assertFalse(PullToAdd.isReady(live = true, touching = false, distance = 900f, threshold = 500f))
    assertFalse(PullToAdd.isReady(live = true, touching = true, distance = 400f, threshold = 500f))
    assertTrue(PullToAdd.isReady(live = true, touching = true, distance = 600f, threshold = 500f))
  }
}
