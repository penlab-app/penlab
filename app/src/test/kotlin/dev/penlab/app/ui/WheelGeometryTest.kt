package dev.penlab.app.ui

import androidx.compose.ui.geometry.Offset
import dev.penlab.app.ink.InkTool
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
  private val density = 2f // px per dp, for converting the dp constants
  private val hubHalfPx = WheelGeometry.HUB_DP * density / 2f

  /** The ring for a corner of the test screen, at 2 px per dp, exactly as ToolDock asks for it. */
  private fun ringWith(insetU: Float, insetV: Float): WheelGeometry.Ring = WheelGeometry.ring(
    minRadius = WheelGeometry.RING_RADIUS_DP * density,
    cell = WheelGeometry.CELL_DP * density,
    discMargin = WheelGeometry.DISC_MARGIN_DP * density,
    insetU = insetU,
    insetV = insetV,
    hubHalf = hubHalfPx,
    cellGap = WheelGeometry.CELL_GAP_DP * density,
    hubClearance = WheelGeometry.HUB_CLEARANCE_DP * density,
  )

  private fun cornerOf(onLeft: Boolean, onTop: Boolean, ring: WheelGeometry.Ring) =
    WheelGeometry.corner(onLeft, onTop, w, h, ring.hubU, ring.hubV)

  private fun corner(onLeft: Boolean, onTop: Boolean) = cornerOf(onLeft, onTop, ringWith(48f, 72f))

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
  fun theHubIsTheDiscCentreAndSitsWellInFromBothEdges() {
    for (onLeft in listOf(true, false)) for (onTop in listOf(true, false)) {
      val ring = ringWith(48f, 72f)
      val c = cornerOf(onLeft, onTop, ring)
      // The hub is the disc's centre (ToolDock places the disc on c.hub); it is clear of the strips...
      assertTrue(c.hubU - hubHalfPx >= 48f)
      assertTrue(c.hubV - hubHalfPx >= 72f)
      // ...and so far in that more than a quarter of the disc shows: the centre is inside the screen
      // by well over the quarter's zero, and the disc reaches past it on both sides.
      assertEquals(if (onLeft) c.hubU else w - c.hubU, c.hubX, 0f)
      assertEquals(if (onTop) c.hubV else h - c.hubV, c.hubY, 0f)
      assertTrue(c.hubU > 0f && c.hubV > 0f)
    }
  }

  @Test
  fun discContainmentIsACircleAroundTheHub() {
    val hub = Offset(500f, 500f)
    assertTrue(WheelGeometry.inDisc(Offset(500f, 500f), hub, 300f))
    assertTrue(WheelGeometry.inDisc(Offset(200f, 500f), hub, 300f)) // on the rim
    assertTrue(WheelGeometry.inDisc(Offset(300f, 300f), hub, 300f)) // behind the hub, towards the corner
    assertFalse(WheelGeometry.inDisc(Offset(800f, 800f), hub, 300f))
  }

  // ---- Immersive (system bars hidden): the wheel sits in the real corner ----

  @Test
  fun immersiveIgnoresGestureInsetButKeepsACoveringCutout() {
    assertEquals(0f, WheelGeometry.edgeInset(60f, 0f, 1600f, immersive = true), 0f)
    assertEquals(30f, WheelGeometry.edgeInset(60f, 30f, 1600f, immersive = true), 0f)
    assertEquals(60f, WheelGeometry.edgeInset(60f, 0f, 1600f, immersive = false), 0f)
  }

  @Test
  fun immersiveCellsKeepOnlyTheCornerMarginAndStayOnScreen() {
    val half = WheelGeometry.CELL_DP * density / 2f
    val margin = WheelGeometry.CORNER_MARGIN_DP * density
    for (onLeft in listOf(true, false)) for (onTop in listOf(true, false)) {
      val ring = ringWith(margin, margin)
      val c = cornerOf(onLeft, onTop, ring)
      for (p in cells(c, ring, onLeft, onTop)) {
        assertTrue("cell $p fully on screen", p.x - half >= margin - 0.01f && p.x + half <= w - margin + 0.01f)
        assertTrue("cell $p fully on screen", p.y - half >= margin - 0.01f && p.y + half <= h - margin + 0.01f)
      }
      // With no gesture strips the cells reach nearer the real corner than behind 30 dp strips.
      val strip = ringWith(60f, 60f)
      assertTrue(ring.hubU < strip.hubU && ring.hubV < strip.hubV)
    }
  }

  @Test
  fun foldedHubExclusionRunsFromTheSideEdgeOverTheButton() {
    val hubHalf = 72f
    val ring = ringWith(0f, 0f)
    val c = cornerOf(true, false, ring)
    val r = WheelGeometry.hubExclusionRects(true, w, h, c, hubHalf, 24f).single()
    assertEquals(0, r.left)
    assertTrue(r.right >= (c.hubX + hubHalf).toInt())
    assertTrue(c.hubY >= r.top && c.hubY <= r.bottom && r.bottom <= h.toInt())
    val rr = WheelGeometry.hubExclusionRects(false, w, h, cornerOf(false, false, ring), hubHalf, 24f).single()
    assertEquals(w.toInt(), rr.right)
    assertTrue(WheelGeometry.hubExclusionRects(true, 0f, 0f, c, hubHalf, 24f).isEmpty())
  }

  // ---- Exclusion rects ----

  @Test
  fun leftWheelExcludesFromTheLeftEdgeAcrossTheDisc() {
    val c = corner(onLeft = true, onTop = false)
    val radius = 380f
    val r = WheelGeometry.exclusionRects(true, false, w, h, c, radius = radius, maxHeight = 5000f).single()
    assertEquals(0, r.left) // includes the gesture strip itself
    assertEquals((c.hubX + radius).toInt(), r.right) // the disc's far side, centred on the hub
    assertEquals((c.hubY - radius).toInt(), r.top)
    assertEquals(minOf(c.hubY + radius, h).toInt(), r.bottom)
    assertTrue(r.left >= 0 && r.right <= w.toInt() && r.top >= 0 && r.bottom <= h.toInt())
  }

  @Test
  fun rightWheelExcludesToTheRightEdge() {
    val c = corner(onLeft = false, onTop = false)
    val r = WheelGeometry.exclusionRects(false, false, w, h, c, radius = 380f, maxHeight = 5000f).single()
    assertEquals(w.toInt(), r.right)
    assertEquals((c.hubX - 380f).toInt(), r.left)
  }

  @Test
  fun topWheelIsExcludedFromTheTopEdgeWhenTheDiscReachesIt() {
    val c = corner(onLeft = true, onTop = true)
    val whole = WheelGeometry.exclusionRects(true, true, w, h, c, radius = c.hubY + 50f, maxHeight = 5000f).single()
    assertEquals(0, whole.top) // the disc runs past the top edge, so the rect is clipped to it
    assertEquals((2 * c.hubY + 50f).toInt(), whole.bottom)
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
  fun aDegenerateScreenGivesNoRects() {
    val c = WheelGeometry.corner(true, false, 0f, 0f, 0f, 0f)
    assertTrue(WheelGeometry.exclusionRects(true, false, 0f, 0f, c, 380f, 400f).isEmpty())
    assertEquals(PxRect(0, 0, 10, 20).height, 20)
  }

  // ---- Ring sizes and hit-testing at the new size ----

  @Test
  fun ringCellsMeetTheTouchTargetMinimumAndAreAboutAQuarterBigger() {
    assertTrue(WheelGeometry.CELL_DP >= 48f)
    assertEquals(1.25f, WheelGeometry.CELL_DP / 40f, 0.01f)
  }

  private fun ringFor(onLeft: Boolean, onTop: Boolean, insetSide: Float = 60f, insetBottom: Float = 60f, topReserve: Float = 200f): Pair<WheelGeometry.Corner, WheelGeometry.Ring> {
    val ring = ringWith(insetSide, if (onTop) topReserve else insetBottom)
    return cornerOf(onLeft, onTop, ring) to ring
  }

  /** The visible cells' centres in root pixels, round the hub. */
  private fun cells(c: WheelGeometry.Corner, ring: WheelGeometry.Ring, onLeft: Boolean, onTop: Boolean): List<Offset> =
    (0 until ring.count).map { i ->
      val s = WheelGeometry.ringSlot(ring.radius, ring.startDeg + i * ring.stepDeg)
      Offset(c.hubX + if (onLeft) s.x else -s.x, c.hubY + if (onTop) s.y else -s.y)
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
      for (p in pts) assertTrue("cell on the button", (p - hub).getDistance() >= cell / 2f + hubHalfPx)
    }
  }

  @Test
  fun theVisibleArcIsAtLeastAHalfCircleAndHoldsMoreThanFourTools() {
    for ((side, bottom, top) in insetCases) for (onLeft in listOf(true, false)) for (onTop in listOf(true, false)) {
      val (_, ring) = ringFor(onLeft, onTop, side, bottom, top)
      assertTrue("arc ${ring.spanDeg}", ring.spanDeg >= 120f)
      assertTrue("count ${ring.count}", ring.count >= 4)
      assertEquals(ring.spanDeg, (ring.count - 1) * ring.stepDeg, 0.01f)
    }
  }

  @Test
  fun theDiscIsCentredOnTheHubAndHoldsTheWholeRingAndShowsMoreThanAQuarter() {
    for (onLeft in listOf(true, false)) for (onTop in listOf(true, false)) {
      val (c, ring) = ringFor(onLeft, onTop)
      val hub = Offset(c.hubX, c.hubY)
      assertEquals(ring.radius + WheelGeometry.CELL_DP * density / 2f + WheelGeometry.DISC_MARGIN_DP * density, ring.disc, 0.001f)
      // Every visible cell is inside the disc, whose centre is the hub itself.
      for (p in cells(c, ring, onLeft, onTop)) assertTrue(WheelGeometry.inDisc(p, hub, ring.disc))
      assertTrue(ring.radius >= WheelGeometry.RING_RADIUS_DP * density)
      // More than a quarter shows: the screen edges are closer to the centre than the rim is on
      // both axes, so the visible part is the disc minus two thin caps, not a 90 degree wedge.
      // (measured from the gesture strips: 60 px at the sides and bottom, 200 px under the top buttons)
      assertTrue(c.hubU - 60f < ring.disc && c.hubV - (if (onTop) 200f else 60f) < ring.disc)
      assertTrue(c.hubU > 0f && c.hubV > 0f)
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
    assertNull(WheelGeometry.pickTarget(targets, hub + Offset(900f, -900f), hub, 110f, 208f))
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

  // ---- Two rows inside one disc: colours near the button, tools and actions on the ring ----

  private fun colorsOf(tool: WheelGeometry.Ring) = WheelGeometry.colorRing(
    tool = tool,
    toolCell = WheelGeometry.CELL_DP * density,
    colorCell = WheelGeometry.COLOR_CELL_DP * density,
    hubHalf = hubHalfPx,
    hubClearance = WheelGeometry.HUB_CLEARANCE_DP * density,
    cellGap = WheelGeometry.CELL_GAP_DP * density,
  )

  @Test
  fun everyColourCellIsFullyOnScreenAndClearOfTheGestureStrips() {
    val half = WheelGeometry.COLOR_CELL_DP * density / 2f
    for ((side, bottom, top) in insetCases) for (onLeft in listOf(true, false)) for (onTop in listOf(true, false)) {
      val (c, tool) = ringFor(onLeft, onTop, side, bottom, top)
      val colors = colorsOf(tool)
      for (p in cells(c, colors, onLeft, onTop)) {
        val msg = "corner left=$onLeft top=$onTop insets=$side/$bottom/$top cell=$p"
        assertTrue(msg, p.x - half >= -0.01f && p.x + half <= w + 0.01f && p.y - half >= -0.01f && p.y + half <= h + 0.01f)
        val fromSide = if (onLeft) p.x - half else w - p.x - half
        val fromEdge = if (onTop) p.y - half else h - p.y - half
        assertTrue("$msg side gap $fromSide", fromSide >= side - 0.5f)
        assertTrue("$msg edge gap $fromEdge", fromEdge >= (if (onTop) top else bottom) - 0.5f)
      }
    }
  }

  @Test
  fun theDiscKeepsItsOriginalSizeAndColourDotsFitBetweenTheButtonAndTheToolRing() {
    val cell = WheelGeometry.CELL_DP * density
    val dot = WheelGeometry.COLOR_CELL_DP * density
    for ((side, bottom, top) in insetCases) for (onLeft in listOf(true, false)) for (onTop in listOf(true, false)) {
      val (c, tool) = ringFor(onLeft, onTop, side, bottom, top)
      val colors = colorsOf(tool)
      assertEquals(tool.disc, colors.disc, 0f) // no growth: the colours live inside the tool ring's disc
      assertTrue(tool.radius >= WheelGeometry.RING_RADIUS_DP * density)
      assertEquals(tool.hubU, colors.hubU, 0f)
      assertTrue("colour ring is inside the tool ring", colors.radius + dot / 2f < tool.radius - cell / 2f)
      assertTrue("colour ring clears the button", colors.radius - dot / 2f >= hubHalfPx + WheelGeometry.HUB_CLEARANCE_DP * density - 0.01f)
      assertTrue("colours hold ${colors.count}", colors.count >= 4)
      assertEquals(colors.spanDeg, (colors.count - 1) * colors.stepDeg, 0.01f)
      val pts = cells(c, colors, onLeft, onTop)
      for (g in pts.zipWithNext { a, b -> (b - a).getDistance() }) assertTrue("dots overlap: $g", g >= dot)
      val hub = Offset(c.hubX, c.hubY)
      for (p in pts) assertTrue("dot reaches the tool ring", (p - hub).getDistance() + dot / 2f < tool.radius - cell / 2f)
    }
  }

  @Test
  fun aPressIsOnTheColourArcNearTheHubAndOnTheToolRingBeyondTheGap() {
    val (_, tool) = ringFor(true, false)
    val colors = colorsOf(tool)
    val split = WheelGeometry.ringSplitRadius(colors, tool)
    assertTrue(colors.radius < split && split < tool.radius)
  }

  @Test
  fun scrollSpanIsZeroWhenEverythingFitsAndGrowsWithMoreCells() {
    val (_, inner) = ringFor(true, false)
    assertEquals(0f, WheelGeometry.scrollSpan(inner.count, inner), 0.01f)
    assertEquals(0f, WheelGeometry.scrollSpan(1, inner), 0f)
    assertEquals(2 * inner.stepDeg, WheelGeometry.scrollSpan(inner.count + 2, inner), 0.01f)
  }

  private val tools = listOf(
    InkTool(InkTool.Kind.PEN, 0xFF000000.toInt(), 2f), // 0
    InkTool(InkTool.Kind.PEN, 0xFF1565C0.toInt(), 2f), // 1
    InkTool(InkTool.Kind.ERASER, 0, 10f), // 2
    InkTool(InkTool.Kind.LASSO, 0, 0f), // 3
    InkTool(InkTool.Kind.HIGHLIGHTER, 0xFFFFEB3B.toInt(), 12f), // 4
    InkTool(InkTool.Kind.FILL, 0xFF2E7D32.toInt(), 0f), // 5
    InkTool(InkTool.Kind.TEXT, 0xFF000000.toInt(), 4f), // 6
  )

  @Test
  fun penToolsGoOnTheColourArcAndEveryOtherToolAndActionOnTheToolRing() {
    val items = listOf(
      DockItem.Tool(0), DockItem.Tool(1), DockItem.Tool(2), DockItem.Tool(3), DockItem.Tool(4), DockItem.Tool(5), DockItem.Tool(6),
      DockItem.Instruments, DockItem.Paper, DockItem.Layers, DockItem.Clear, DockItem.More, DockItem.Undo, DockItem.Redo,
    )
    val (inner, outer) = wheelRingItems(items, tools)
    assertEquals(listOf(DockItem.Tool(0), DockItem.Tool(1)), inner)
    assertEquals(
      listOf(
        DockItem.Tool(2), DockItem.Tool(3), DockItem.Tool(4), DockItem.Tool(5), DockItem.Tool(6),
        DockItem.Instruments, DockItem.Paper, DockItem.Clear, DockItem.More, DockItem.Undo, DockItem.Redo,
      ),
      outer,
    ) // Layers is on neither ring
  }

  @Test
  fun aToolWithNoEntryIsDroppedAndEachRingKeepsTheToolbarOrder() {
    val items = listOf(DockItem.Redo, DockItem.Tool(1), DockItem.Tool(99), DockItem.Tool(3), DockItem.Tool(0), DockItem.Tool(2))
    val (inner, outer) = wheelRingItems(items, tools)
    assertEquals(listOf(DockItem.Tool(1), DockItem.Tool(0)), inner)
    assertEquals(listOf(DockItem.Redo, DockItem.Tool(3), DockItem.Tool(2)), outer)
    assertTrue(inner.none { it in outer }) // every cell lives on exactly one ring
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
