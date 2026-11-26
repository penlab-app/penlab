package dev.penlab.app.ink

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class SelectionHitTest {
  private val grab = 54f // 24 dp at 2.25

  @Test
  fun theCentreOfASmallBoxMovesInsteadOfResizing() {
    // The QA case: 36 px tall plus 2 x 13 px margin = 62 px, a wide box. The nearest handle to the centre
    // is a top or bottom edge midpoint, half the short side away.
    val short = 62f
    assertFalse(handleClaimsTouch(distancePx = short / 2, insideBox = true, boxShortSidePx = short, grabPx = grab))
    // A square one: the corners are further still.
    assertFalse(handleClaimsTouch(hypot(31f, 31f), insideBox = true, boxShortSidePx = short, grabPx = grab))
  }

  @Test
  fun aCornerOfASmallBoxIsStillGrabbedClose() {
    assertTrue(handleClaimsTouch(distancePx = 10f, insideBox = true, boxShortSidePx = 62f, grabPx = grab))
  }

  @Test
  fun outsideTheBoxTheHandleKeepsItsFullReach() {
    assertTrue(handleClaimsTouch(distancePx = 40f, insideBox = false, boxShortSidePx = 62f, grabPx = grab))
    assertFalse(handleClaimsTouch(distancePx = 60f, insideBox = false, boxShortSidePx = 62f, grabPx = grab))
  }

  @Test
  fun aBigBoxBehavesAsBefore() {
    // 300 px short side: 30% is 90 px, so the full 54 px reach applies inside too.
    assertTrue(handleClaimsTouch(distancePx = 50f, insideBox = true, boxShortSidePx = 300f, grabPx = grab))
    assertFalse(handleClaimsTouch(distancePx = 60f, insideBox = true, boxShortSidePx = 300f, grabPx = grab))
  }

  @Test
  fun everySizeKeepsAMoveZoneInTheMiddle() {
    // For any box the point nearest every handle is at least half the short side away from an edge handle,
    // which the interior reach (30%) never reaches.
    for (short in listOf(20f, 40f, 62f, 100f, 180f, 400f)) {
      assertFalse(handleClaimsTouch(short / 2, insideBox = true, boxShortSidePx = short, grabPx = grab))
    }
  }
}
