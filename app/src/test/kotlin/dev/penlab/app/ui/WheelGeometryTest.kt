package dev.penlab.app.ui

import androidx.compose.ui.geometry.Offset
import dev.penlab.app.ui.WheelGeometry.PxRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
      // The disc itself is centred on the real screen corner (the edges cut it), not pulled in.
      assertEquals(if (onLeft) 0f else w, c.originX, 0f)
      assertEquals(if (onTop) 0f else h, c.originY, 0f)
    }
  }

  @Test
  fun bottomCornersAreLiftedOutOfTheHomeGestureZone() {
    val c = corner(onLeft = true, onTop = false)
    assertEquals(h, c.originY, 0f) // the disc is anchored at the real corner...
    assertEquals(0f, c.originX, 0f)
    assertTrue(c.hubY + 72f <= h - 72f) // ...but the button is lifted out of the Home-gesture zone
    assertTrue(c.hubX - 72f >= 48f)
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

  // ---- Immersive (system bars hidden): the wheel sits in the real corner ----

  @Test
  fun immersiveIgnoresGestureInsetButKeepsACoveringCutout() {
    assertEquals(0f, WheelGeometry.edgeInset(60f, 0f, 1600f, immersive = true), 0f)
    assertEquals(30f, WheelGeometry.edgeInset(60f, 30f, 1600f, immersive = true), 0f)
    assertEquals(60f, WheelGeometry.edgeInset(60f, 0f, 1600f, immersive = false), 0f)
  }

  @Test
  fun immersiveHubAndCellsSitAtTheNaturalCornerAndStayOnScreen() {
    val half = WheelGeometry.CELL_DP * density / 2f
    val margin = WheelGeometry.CORNER_MARGIN_DP * density
    val hubHalf = WheelGeometry.HUB_DP * density / 2f
    val gap = WheelGeometry.EDGE_GAP_DP * density
    for (onLeft in listOf(true, false)) for (onTop in listOf(true, false)) {
      // No gesture pull-in: the hub is just EDGE_GAP from the side and from the bottom (or top buttons).
      val topReserve = 200f
      val c = WheelGeometry.corner(onLeft, onTop, w, h, 0f, 0f, 0f, topReserve, gap, hubHalf)
      assertEquals(gap + hubHalf, c.hubU, 0.01f)
      assertEquals(if (onTop) topReserve + gap + hubHalf else gap + hubHalf, c.hubV, 0.01f)
      val ring = WheelGeometry.ring(
        minRadius = WheelGeometry.RING_RADIUS_DP * density, cell = WheelGeometry.CELL_DP * density,
        discMargin = WheelGeometry.DISC_MARGIN_DP * density, insetU = margin,
        insetV = if (onTop) maxOf(topReserve, margin) else margin,
        hubU = c.hubU, hubV = c.hubV, hubHalf = hubHalf,
        cellGap = WheelGeometry.CELL_GAP_DP * density, hubClearance = WheelGeometry.HUB_CLEARANCE_DP * density,
      )
      for (p in cells(c, ring, onLeft, onTop)) {
        assertTrue("cell $p fully on screen", p.x - half >= margin - 0.01f && p.x + half <= w - margin + 0.01f)
        assertTrue("cell $p fully on screen", p.y - half >= margin - 0.01f && p.y + half <= h - margin + 0.01f)
      }
      // The cells sit nearer the real corner than they would behind a 30 dp gesture strip.
      val strip = ringFor(onLeft, onTop, insetSide = 60f, insetBottom = 60f, topReserve = topReserve).second
      assertTrue(ring.startDeg < strip.startDeg || onTop)
    }
  }

  @Test
  fun foldedHubExclusionRunsFromTheSideEdgeOverTheButton() {
    val hubHalf = 72f
    val c = WheelGeometry.corner(true, false, w, h, 0f, 0f, 0f, 200f, 24f, hubHalf)
    val r = WheelGeometry.hubExclusionRects(true, w, h, c, hubHalf, 24f).single()
    assertEquals(0, r.left)
    assertTrue(r.right >= (c.hubX + hubHalf).toInt())
    assertTrue(c.hubY >= r.top && c.hubY <= r.bottom && r.bottom <= h.toInt())
    val rr = WheelGeometry.hubExclusionRects(false, w, h, WheelGeometry.corner(false, false, w, h, 0f, 0f, 0f, 200f, 24f, hubHalf), hubHalf, 24f).single()
    assertEquals(w.toInt(), rr.right)
    assertTrue(WheelGeometry.hubExclusionRects(true, 0f, 0f, c, hubHalf, 24f).isEmpty())
  }

  // ---- Exclusion rects ----

  @Test
  fun leftWheelExcludesFromTheLeftEdgeAcrossTheWheel() {
    val c = corner(onLeft = true, onTop = false)
    val rects = WheelGeometry.exclusionRects(true, false, w, h, c, radius = 380f, maxHeight = 400f)
    assertEquals(1, rects.size)
    val r = rects.single()
    assertEquals(0, r.left) // includes the gesture strip itself
    assertEquals(380, r.right) // the disc is centred on the real corner
    assertEquals(h.toInt(), r.bottom) // runs on to the screen's bottom edge
    assertTrue(r.height <= 400)
  }

  @Test
  fun rightWheelExcludesToTheRightEdge() {
    val c = corner(onLeft = false, onTop = false)
    val r = WheelGeometry.exclusionRects(false, false, w, h, c, radius = 380f, maxHeight = 400f).single()
    assertEquals(w.toInt(), r.right)
    assertEquals((w - 380f).toInt(), r.left)
  }

  @Test
  fun topWheelExcludesFromTheTopEdgeDownwards() {
    val c = corner(onLeft = true, onTop = true)
    val whole = WheelGeometry.exclusionRects(true, true, w, h, c, radius = 500f, maxHeight = 5000f).single()
    assertEquals(0, whole.top) // uncut, it starts at the top edge and runs to the disc's far side
    assertEquals(500, whole.bottom)
    val cut = WheelGeometry.exclusionRects(true, true, w, h, c, radius = 500f, maxHeight = 400f).single()
    assertEquals(400, cut.height)
    assertTrue(c.hubY >= cut.top && c.hubY <= cut.bottom)
  }

  @Test
  fun exclusionNeverExceedsTheEdgeLimitAndStaysOnTheButton() {
    val c = corner(onLeft = true, onTop = false)
    val r = WheelGeometry.exclusionRects(true, false, w, h, c, radius = 500f, maxHeight = 400f).single()
    assertEquals(400, r.height)
    assertTrue(c.hubY >= r.top && c.hubY <= r.bottom) // the window follows the button, where the pen is
    assertTrue(r.top >= 0 && r.bottom <= h.toInt())
  }

  @Test
  fun shortWheelIsExcludedWholeAndRectsStayOnScreen() {
    val c = corner(onLeft = true, onTop = false)
    val r = WheelGeometry.exclusionRects(true, false, w, h, c, radius = 380f, maxHeight = 5000f).single()
    assertEquals(380, r.height) // from the disc's top to the screen's bottom
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
  }

  /** The ring for a corner of the test screen, at 2 px per dp, exactly as ToolDock asks for it. */
  private fun ringFor(onLeft: Boolean, onTop: Boolean, insetSide: Float = 60f, insetBottom: Float = 60f, topReserve: Float = 200f): Pair<WheelGeometry.Corner, WheelGeometry.Ring> {
    val c = WheelGeometry.corner(
      onLeft, onTop, w, h, insetLeft = insetSide, insetRight = insetSide, insetBottom = insetBottom, topReserve = topReserve,
      gap = WheelGeometry.EDGE_GAP_DP * density, hubHalf = WheelGeometry.HUB_DP * density / 2f,
    )
    val ring = WheelGeometry.ring(
      minRadius = WheelGeometry.RING_RADIUS_DP * density,
      cell = WheelGeometry.CELL_DP * density,
      discMargin = WheelGeometry.DISC_MARGIN_DP * density,
      insetU = insetSide,
      insetV = if (onTop) topReserve else insetBottom,
      hubU = c.hubU,
      hubV = c.hubV,
      hubHalf = WheelGeometry.HUB_DP * density / 2f,
      cellGap = WheelGeometry.CELL_GAP_DP * density,
      hubClearance = WheelGeometry.HUB_CLEARANCE_DP * density,
    )
    return c to ring
  }

  /** The visible cells' centres in root pixels. */
  private fun cells(c: WheelGeometry.Corner, ring: WheelGeometry.Ring, onLeft: Boolean, onTop: Boolean): List<Offset> =
    (0 until WheelGeometry.VISIBLE_CELLS).map { i ->
      val s = WheelGeometry.ringSlot(ring.radius, ring.startDeg + i * ring.stepDeg)
      Offset(c.originX + if (onLeft) s.x else -s.x, c.originY + if (onTop) s.y else -s.y)
    }

  private val insetCases = listOf(
    Triple(60f, 60f, 200f), // about 30 dp strips: the screenshot's case
    Triple(48f, 72f, 160f),
    Triple(0f, 0f, 120f), // no gesture strips at all
    Triple(120f, 100f, 260f), // unusually fat strips
  )

  @Test
  fun everyVisibleCellIsFullyOnScreenAndClearOfTheGestureStrips() {
    val half = WheelGeometry.CELL_DP * density / 2f
    for ((side, bottom, top) in insetCases) for (onLeft in listOf(true, false)) for (onTop in listOf(true, false)) {
      val (c, ring) = ringFor(onLeft, onTop, side, bottom, top)
      for (p in cells(c, ring, onLeft, onTop)) {
        val msg = "corner left=$onLeft top=$onTop insets=$side/$bottom/$top cell=$p"
        // Whole cell (its hit circle) on screen...
        assertTrue(msg, p.x - half >= -0.01f && p.x + half <= w + 0.01f && p.y - half >= -0.01f && p.y + half <= h + 0.01f)
        // ...and clear of the side strip and the bottom (or top) strip.
        val fromSide = if (onLeft) p.x - half else w - p.x - half
        val fromEdge = if (onTop) p.y - half else h - p.y - half
        assertTrue("$msg side gap $fromSide", fromSide >= side - 0.01f)
        assertTrue("$msg edge gap $fromEdge", fromEdge >= (if (onTop) top else bottom) - 0.01f)
      }
    }
  }

  @Test
  fun visibleCellsAreEvenlySpacedAndDoNotOverlapEachOtherOrTheButton() {
    val cell = WheelGeometry.CELL_DP * density
    for ((side, bottom, top) in insetCases) for (onLeft in listOf(true, false)) for (onTop in listOf(true, false)) {
      val (c, ring) = ringFor(onLeft, onTop, side, bottom, top)
      val pts = cells(c, ring, onLeft, onTop)
      val gaps = pts.zipWithNext { a, b -> (b - a).getDistance() }
      for (g in gaps) {
        assertEquals("uneven spacing $gaps", gaps[0], g, 0.5f)
        assertTrue("cells overlap: $g", g >= cell)
      }
      val hub = Offset(c.hubX, c.hubY)
      for (p in pts) assertTrue("cell on the button", (p - hub).getDistance() >= cell / 2f + WheelGeometry.HUB_DP * density / 2f)
    }
  }

  @Test
  fun theDiscIsAnchoredAtTheScreenCornerAndHoldsTheWholeRing() {
    for (onLeft in listOf(true, false)) for (onTop in listOf(true, false)) {
      val (c, ring) = ringFor(onLeft, onTop)
      assertEquals(if (onLeft) 0f else w, c.originX, 0f)
      assertEquals(if (onTop) 0f else h, c.originY, 0f)
      assertEquals(ring.radius + WheelGeometry.CELL_DP * density / 2f + WheelGeometry.DISC_MARGIN_DP * density, ring.disc, 0.001f)
      // Every visible cell and the button are inside the disc's quarter.
      for (p in cells(c, ring, onLeft, onTop) + Offset(c.hubX, c.hubY))
        assertTrue(WheelGeometry.inQuarter(p, c.originX, c.originY, onLeft, onTop, ring.disc))
      assertTrue(ring.radius >= WheelGeometry.RING_RADIUS_DP * density) // never smaller than the 25%-larger ring
    }
  }

  private fun targetsFor(c: WheelGeometry.Corner, ring: WheelGeometry.Ring, onLeft: Boolean, onTop: Boolean): Map<Int, Offset> =
    cells(c, ring, onLeft, onTop).mapIndexed { i, p -> i to p }.toMap()

  @Test
  fun pullingOntoACellPicksItAndTheHubsOwnZonePicksNothing() {
    val (c, ring) = ringFor(true, false)
    val targets = targetsFor(c, ring, true, false)
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
    val (c, ring) = ringFor(true, false)
    val targets = targetsFor(c, ring, true, false)
    val hub = Offset(c.hubX, c.hubY)
    assertNull(WheelGeometry.pickTarget(targets, Offset(c.originX + 900f, c.originY - 900f), hub, 110f, 208f))
  }

  @Test
  fun slotsBeyondTheEndsAreNotTargetsAndFadeOut() {
    val (_, ring) = ringFor(true, false)
    val first = ring.startDeg
    val last = ring.endDeg
    assertTrue(WheelGeometry.slotOnScreen(first, ring) && WheelGeometry.slotOnScreen(last, ring))
    assertFalse(WheelGeometry.slotOnScreen(first - ring.stepDeg, ring) || WheelGeometry.slotOnScreen(last + ring.stepDeg, ring))
    assertEquals(1f, WheelGeometry.ringAlpha(first, ring), 0f)
    assertEquals(1f, WheelGeometry.ringAlpha((first + last) / 2f, ring), 0f)
    assertEquals(0f, WheelGeometry.ringAlpha(first - ring.stepDeg, ring), 0f) // a tool parked past the end is gone
    assertEquals(0f, WheelGeometry.ringAlpha(last + ring.stepDeg, ring), 0f)
    val half = WheelGeometry.ringAlpha(first - 0.3f * ring.stepDeg, ring)
    assertTrue(half > 0f && half < 1f)
    val s = WheelGeometry.ringSlot(100f, 90f)
    assertEquals(0f, s.x, 0.001f)
    assertEquals(100f, s.y, 0.001f)
  }

  // ---- Rail exclusion ----

  @Test
  fun railExclusionCoversBothGripsOnTheEdgeWithinTheLimit() {
    val rects = WheelGeometry.railExclusionRects(true, w, h, 160f, listOf(100f, h - 100f), maxHeight = 400f)
    assertEquals(2, rects.size)
    assertTrue(rects.all { it.left == 0 && it.right == 160 })
    assertTrue(rects.sumOf { it.height } <= 400)
    assertTrue(rects[0].top <= 100 && rects[0].bottom >= 100)
    assertTrue(rects[1].top <= (h - 100f).toInt() && rects[1].bottom >= (h - 100f).toInt())
  }

  @Test
  fun railExclusionOnTheRightSitsAgainstTheRightEdge() {
    val r = WheelGeometry.railExclusionRects(false, w, h, 160f, listOf(h / 2), maxHeight = 400f).single()
    assertEquals(w.toInt(), r.right)
    assertEquals((w - 160f).toInt(), r.left)
    assertEquals(400, r.height)
  }

  @Test
  fun railExclusionMergesTouchingGripsAndSkipsOffScreenOnes() {
    val merged = WheelGeometry.railExclusionRects(true, w, h, 160f, listOf(300f, 350f), maxHeight = 400f)
    assertEquals(1, merged.size)
    assertTrue(merged.single().height <= 400)
    val onlyVisible = WheelGeometry.railExclusionRects(true, w, h, 160f, listOf(-50f, 300f), maxHeight = 400f)
    assertEquals(1, onlyVisible.size)
    assertTrue(WheelGeometry.railExclusionRects(true, w, h, 160f, listOf(-50f, h + 10f), maxHeight = 400f).isEmpty())
  }

  @Test
  fun railExclusionStaysOnScreenNearTheEnds() {
    val r = WheelGeometry.railExclusionRects(true, w, h, 160f, listOf(5f), maxHeight = 400f).single()
    assertEquals(0, r.top)
    val e = WheelGeometry.railExclusionRects(true, w, h, 160f, listOf(h - 5f), maxHeight = 400f).single()
    assertEquals(h.toInt(), e.bottom)
  }

  // ---- Dropping the toolbar, and the bar's "picked tool" test ----

  @Test
  fun aSlowDropIsJudgedWhereTheBallIs() {
    val ball = Offset(40f, 60f)
    val p = DockDrop.landingPoint(ball, Offset(120f, -80f), w, h, flickMinSpeedPx = 1000f)
    assertEquals(ball, p)
  }

  @Test
  fun aFlickGlidesOnAndStaysOnTheScreen() {
    val p = DockDrop.landingPoint(Offset(800f, 1200f), Offset(0f, 9000f), w, h, flickMinSpeedPx = 1000f)
    assertEquals(h, p.y, 0.001f) // thrown down: clamped to the bottom edge
    assertEquals(800f, p.x, 0.001f)
    val q = DockDrop.landingPoint(Offset(10f, 10f), Offset(-4000f, -4000f), w, h, flickMinSpeedPx = 1000f)
    assertEquals(Offset(0f, 0f), q)
  }

  @Test
  fun aToolWithoutACellOnTheBarIsHiddenFromIt() {
    val items = listOf(DockItem.Tool(0), DockItem.Tool(1), DockItem.More)
    assertFalse(toolHiddenFromBar(items, 1))
    assertTrue(toolHiddenFromBar(items, 5)) // e.g. the highlighter, picked from More
    assertFalse(toolHiddenFromBar(items, -1))
  }
}
