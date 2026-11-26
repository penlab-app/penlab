package dev.penlab.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTest {
  @Test
  fun removeAnimationsMeansOff() {
    assertEquals(MotionSpeed.OFF, speedForSystemScale(0f))
  }

  @Test
  fun androidScaleMapsToTheNearestSpeed() {
    assertEquals(MotionSpeed.FAST, speedForSystemScale(0.5f))
    assertEquals(MotionSpeed.NORMAL, speedForSystemScale(1f))
    assertEquals(MotionSpeed.SLOW, speedForSystemScale(2f))
  }

  @Test
  fun offHasNoTimeAndTheOthersStretchIt() {
    assertEquals(0f, MotionSpeed.OFF.factor, 0f)
    assertEquals(1f, MotionSpeed.NORMAL.factor, 0f)
  }

  @Test
  fun aPickedSpeedBeatsAndroidsScaleButOtherwiseTheScaleDecides() {
    assertEquals(MotionSpeed.FAST, resolveSpeed("FAST", 0f))
    assertEquals(MotionSpeed.OFF, resolveSpeed(null, 0f)) // re-read on resume: scale turned to 0
    assertEquals(MotionSpeed.NORMAL, resolveSpeed(null, 1f)) // and back again
    assertEquals(MotionSpeed.NORMAL, resolveSpeed("garbage", 1f))
  }
}
