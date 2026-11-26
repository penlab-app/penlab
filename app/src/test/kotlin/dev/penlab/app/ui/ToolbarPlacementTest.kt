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

  // Device case, in device px (2560 wide): two pen slots make a 1056 px bar, gap 480..2000.
  @Test
  fun twoSlotsSitOnTheScreenCentreEvenFromAnOldSavedPosition() {
    val along = snappedAlong(0.383f) // an old drop 117 px (1280-wide) off centre snaps to the middle
    assertEquals(0.5f, along, 0f)
    assertEquals(752, inlineToolbarX(along * 2560f, 1056, 480, 2000, centreInGap = along == 0.5f)) // 1280 - 528
  }

  @Test
  fun aBarDroppedFarFromTheMiddleKeepsItsPlace() {
    assertEquals(0.2f, snappedAlong(0.2f), 0f)
    assertEquals(0.5f, snappedAlong(0.62f), 0f)
    assertEquals(0.5f, snappedAlong(0.5f), 0f)
  }
}
