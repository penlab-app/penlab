package dev.penlab.app.ui

import androidx.compose.ui.geometry.Offset
import dev.penlab.app.ui.WheelGeometry.PxRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sin

class WheelGeometryTest {
  // A 1600 x 2560 px screen at 2x density (800 x 1280 dp), like the Tab S9 in portrait.
  private val w = 1600f
  private val h = 2560f
  private fun corner(onLeft: Boolean, onTop: Boolean) =
    WheelGeometry.corner(onLeft, onTop, w, h, insetLeft = 48f, insetRight = 64f, insetBottom = 72f, topReserve = 200f, gap = 24f, hubHalf = 72f)

  // ---- The gesture: the item list must compare equal across recompositions ----

  @Test
  fun dockItemsCompareByContentSoTheWheelGestureIsNotRestarted() {
    val a = listOf(DockItem.Tool(0), DockItem.Tool(2), DockItem.Undo)
    val b = listOf(DockItem.Tool(0), DockItem.Tool(2), DockItem.Undo)
    assertEquals(a, b)
    assertNotEquals(DockItem.Tool(1), DockItem.Tool(2))
  }

  // ---- Edge inset clamping ----

  @Test
  fun insetIsTheLargerOfGestureAndCoveredAndNeverNegative() {
    assertEquals(60f, WheelGeometry.edgeInset(60f, 20f, 1600f), 0f)
    assertEquals(40f, WheelGeometry.edgeInset(10f, 40f, 1600f), 0f)
    assertEquals(0f, WheelGeometry.edgeInset(-5f, -9f, 1600f), 0f)
  }

  @Test
  fun absurdInsetsAreClampedToAFractionOfTheScreen() {
    val max = 1600f * WheelGeometry.MAX_INSET_FRACTION
    assertEquals(max, WheelGeometry.edgeInset(5000f, 0f, 1600f), 0f)
    assertEquals(0f, WheelGeometry.edgeInset(100f, 100f, 0f), 0f) // a zero-size screen: nothing to inset
  }

  @Test
  fun everyCornerSitsInsideTheGestureSafeArea() {
    for (onLeft in listOf(true, false)) for (onTop in listOf(true, false)) {
      val c = corner(onLeft, onTop)
      // The button's whole disc (centre +/- half) stays inside the safe rectangle.
      val safeLeft = 48f
      val safeRight = w - 64f
      val safeBottom = h - 72f
      val safeTop = 200f
      assertTrue(c.hubX - 72f >= safeLeft && c.hubX + 72f <= safeRight)
      assertTrue(c.hubY - 72f >= safeTop && c.hubY + 72f <= safeBottom)
      // And it is the same distance from both axes through the wheel's centre.
      assertEquals(96f, hypot(c.hubX - c.originX, 0f), 0f)
      assertEquals(96f, hypot(0f, c.hubY - c.originY), 0f)
    }
  }

  @Test
  fun bottomCornersAreLiftedOutOfTheHomeGestureZone() {
    val c = corner(onLeft = true, onTop = false)
    assertEquals(h - 72f, c.originY, 0f)
    assertEquals(48f, c.originX, 0f)
    assertTrue(c.hubY + 72f <= h - 72f)
  }

  @Test
  fun quarterContainsOnlyTheScreenSideOfTheDisc() {
    val c = corner(onLeft = true, onTop = false)
    val r = 380f
    fun inside(x: Float, y: Float) = WheelGeometry.inQuarter(Offset(x, y), c.originX, c.originY, true, false, r)
    assertTrue(inside(c.originX + 100f, c.originY - 100f))
    assertFalse(inside(c.originX - 10f, c.originY - 100f)) // back towards the Back-gesture strip
    assertFalse(inside(c.originX + 100f, c.originY + 10f)) // down into the Home-gesture strip
    assertFalse(inside(c.originX + 300f, c.originY - 300f)) // past the rim
  }

  // ---- Exclusion rects ----

  @Test
  fun leftWheelExcludesFromTheLeftEdgeAcrossTheWheel() {
    val c = corner(onLeft = true, onTop = false)
    val rects = WheelGeometry.exclusionRects(true, false, w, h, c, radius = 380f, maxHeight = 400f)
    assertEquals(1, rects.size)
    val r = rects.single()
    assertEquals(0, r.left) // includes the gesture strip itself
    assertEquals((48f + 380f).toInt(), r.right)
    assertEquals(h.toInt(), r.bottom) // runs on to the screen's bottom edge
    assertTrue(r.height <= 400)
  }

  @Test
  fun rightWheelExcludesToTheRightEdge() {
    val c = corner(onLeft = false, onTop = false)
    val r = WheelGeometry.exclusionRects(false, false, w, h, c, radius = 380f, maxHeight = 400f).single()
    assertEquals(w.toInt(), r.right)
    assertEquals((w - 64f - 380f).toInt(), r.left)
  }

  @Test
  fun topWheelExcludesFromTheTopEdgeDownwards() {
    val c = corner(onLeft = true, onTop = true)
    val whole = WheelGeometry.exclusionRects(true, true, w, h, c, radius = 380f, maxHeight = 5000f).single()
    assertEquals(0, whole.top) // uncut, it starts at the top edge and runs to the disc's far side
    assertEquals((c.originY + 380f).toInt(), whole.bottom)
    val cut = WheelGeometry.exclusionRects(true, true, w, h, c, radius = 380f, maxHeight = 400f).single()
    assertEquals(400, cut.height)
    assertTrue(c.hubY >= cut.top && c.hubY <= cut.bottom)
  }

  @Test
  fun exclusionNeverExceedsTheEdgeLimitAndStaysOnTheButton() {
    val c = corner(onLeft = true, onTop = false)
    val r = WheelGeometry.exclusionRects(true, false, w, h, c, radius = 380f, maxHeight = 400f).single()
    assertEquals(400, r.height)
    assertTrue(c.hubY >= r.top && c.hubY <= r.bottom) // the window follows the button, where the pen is
    assertTrue(r.top >= 0 && r.bottom <= h.toInt())
  }

  @Test
  fun shortWheelIsExcludedWholeAndRectsStayOnScreen() {
    val c = corner(onLeft = true, onTop = false)
    val r = WheelGeometry.exclusionRects(true, false, w, h, c, radius = 380f, maxHeight = 5000f).single()
    assertEquals((h - (h - 72f - 380f)).toInt(), r.height) // from the disc's top to the screen's bottom
    assertTrue(r.left >= 0 && r.right <= w.toInt() && r.top >= 0 && r.bottom <= h.toInt())
  }

  @Test
  fun aDegenerateScreenGivesNoRects() {
    val c = WheelGeometry.corner(true, false, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    assertTrue(WheelGeometry.exclusionRects(true, false, 0f, 0f, c, 380f, 400f).isEmpty())
    assertEquals(PxRect(0, 0, 10, 20).height, 20)
  }

  // ---- Ring sizes and hit-testing at the new size ----

  private val density = 2f // px per dp, for converting the dp constants

  @Test
  fun ringCellsMeetTheTouchTargetMinimumAndAreAboutAQuarterBigger() {
    assertTrue(WheelGeometry.CELL_DP >= 48f)
    assertEquals(1.25f, WheelGeometry.CELL_DP / 40f, 0.01f)
    assertEquals(1.25f, WheelGeometry.RING_RADIUS_DP / 124f, 0.01f)
    assertEquals(1.25f, WheelGeometry.DISC_RADIUS_DP / 152f, 0.01f)
  }

  @Test
  fun neighbouringRingCellsDoNotOverlapAndFitInsideTheDisc() {
    val ring = WheelGeometry.RING_RADIUS_DP
    val step = (78f - 12f) / 3f // RingStepDeg
    val chord = 2f * ring * sin(Math.toRadians(step / 2.0)).toFloat()
    assertTrue("cells overlap: chord $chord", chord >= WheelGeometry.CELL_DP)
    assertTrue(ring + WheelGeometry.CELL_DP / 2f <= WheelGeometry.DISC_RADIUS_DP)
    // The round button stays clear of the ring's inner edge.
    val hubReach = (WheelGeometry.EDGE_GAP_DP + WheelGeometry.HUB_DP / 2f) * 1.4143f + WheelGeometry.HUB_DP / 2f
    assertTrue(hubReach < ring - WheelGeometry.CELL_DP / 2f)
  }

  private fun targetsFor(c: WheelGeometry.Corner, onLeft: Boolean, onTop: Boolean): Map<Int, Offset> {
    val ring = WheelGeometry.RING_RADIUS_DP * density
    return listOf(12f, 34f, 56f, 78f).mapIndexed { i, deg ->
      val (u, v) = WheelGeometry.ringSlot(ring, deg)
      i to Offset(c.originX + if (onLeft) u else -u, c.originY + if (onTop) v else -v)
    }.toMap()
  }

  @Test
  fun pullingOntoACellPicksItAndTheHubsOwnZonePicksNothing() {
    val c = corner(onLeft = true, onTop = false)
    val targets = targetsFor(c, true, false)
    val hub = Offset(c.hubX, c.hubY)
    val dead = 55f * density
    val reach = 104f * density
    for ((key, centre) in targets) {
      assertEquals(key, WheelGeometry.pickTarget(targets, centre, hub, dead, reach))
      // A finger a bit off the cell's centre (within its 50 dp) still picks it.
      assertEquals(key, WheelGeometry.pickTarget(targets, centre + Offset(15f * density, -10f * density), hub, dead, reach))
    }
    assertNull(WheelGeometry.pickTarget(targets, hub + Offset(10f, -10f), hub, dead, reach))
    assertNull(WheelGeometry.pickTarget(emptyMap(), Offset(500f, 500f), hub, dead, reach))
  }

  @Test
  fun pullingFarFromEveryCellPicksNothing() {
    val c = corner(onLeft = true, onTop = false)
    val targets = targetsFor(c, true, false)
    val hub = Offset(c.hubX, c.hubY)
    assertNull(WheelGeometry.pickTarget(targets, Offset(c.originX + 900f, c.originY - 900f), hub, 110f, 208f))
  }

  @Test
  fun slotsBeyondTheEndsAreNotTargets() {
    assertTrue(WheelGeometry.slotOnScreen(12f) && WheelGeometry.slotOnScreen(78f))
    assertFalse(WheelGeometry.slotOnScreen(-20f) || WheelGeometry.slotOnScreen(100f))
    val s = WheelGeometry.ringSlot(100f, 90f)
    assertEquals(0f, s.x, 0.001f)
    assertEquals(100f, s.y, 0.001f)
  }
}
