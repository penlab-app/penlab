package dev.penlab.app.ink

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RotateKnobTest {
  // Knob radius 15 px, margin 13 px, top buttons cover the first 100 px.
  private fun hidden(centreY: Float) = rotateKnobHidden(centreY, radiusPx = 15f, marginPx = 13f, obstructionPx = 100f)

  @Test
  fun flipsWhenTheCircleWouldTouchTheButtonsEvenIfTheCentreIsClear() {
    assertTrue(hidden(117f)) // centre 17 px below the strip, but its edge plus margin reaches it
    assertTrue(hidden(80f))
  }

  @Test
  fun staysAboveWhenTheWholeCircleAndMarginAreClear() {
    assertFalse(hidden(129f)) // 129 - 28 = 101 > 100
    assertFalse(hidden(300f))
  }
}
