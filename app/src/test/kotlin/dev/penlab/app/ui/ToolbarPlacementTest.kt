package dev.penlab.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolbarPlacementTest {
  // Screen 1280 px wide, free gap between the side pills 200..1000 (clearance already taken off), bar 400 wide.
  @Test
  fun centredOnTheScreenWhenItFitsWithClearance() {
    assertEquals(440, inlineToolbarX(640f, 400, 200, 1000, centreInGap = true)) // 640 - 200
  }

  @Test
  fun centredInTheGapWhenScreenCentreWouldOverlapAPill() {
    // Right pill is wide: free gap 200..800 (centre 500). Screen centre 640 would reach 840 > 800.
    assertEquals(300, inlineToolbarX(640f, 400, 200, 800, centreInGap = true)) // (200 + 800 - 400) / 2
  }

  @Test
  fun aDraggedBarIsOnlyPushedFarEnoughToFit() {
    assertEquals(400, inlineToolbarX(640f, 400, 200, 800, centreInGap = false)) // right edge at 800
    assertEquals(200, inlineToolbarX(100f, 400, 200, 800, centreInGap = false))
  }

  @Test
  fun exactFitStaysPut() {
    assertEquals(200, inlineToolbarX(500f, 400, 200, 600, centreInGap = true))
  }
}
