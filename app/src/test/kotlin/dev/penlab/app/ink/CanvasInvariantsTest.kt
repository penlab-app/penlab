package dev.penlab.app.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.roundToInt

class CanvasInvariantsTest {

  private data class StubStroke(val layer: Int, val centerY: Float)

  private fun checkClear(
    isPaged: Boolean = false,
    pageTop: Float = 0f,
    pageBottom: Float = 3090f,
    strokes: List<StubStroke> = emptyList(),
    items: List<PageItem> = emptyList(),
    hiddenLayers: Set<Int> = emptySet(),
  ): Boolean =
    canClearPage(
      isPaged = isPaged,
      pageTop = pageTop,
      pageBottom = pageBottom,
      strokes = strokes,
      items = items,
      hiddenLayers = hiddenLayers,
      strokeLayer = { it.layer },
      strokeCenterY = { it.centerY },
      itemCenterY = { it.cy },
    )

  // ---- canClearCurrentPage ------------------------------------------------------------------

  @Test
  fun canClearIsFalseWhenBlankAndTrueWithVisibleStrokes() {
    assertFalse(checkClear())
    assertTrue(checkClear(strokes = listOf(StubStroke(layer = 0, centerY = 100f))))
  }

  @Test
  fun canClearIsTrueForItemsOnlyPagesOfEveryItemKind() {
    val image = ImageItem(id = "img", fileName = "a.png", cx = 200f, cy = 400f, width = 100f, height = 100f)
    val text = TextItem(id = "txt", text = "hi", paperColor = 0xFF000000.toInt(), textSize = 40f, cx = 200f, cy = 400f, width = 200f, height = 80f)
    val plugin = PluginPageItem(id = "plg", kind = 99, content = "x", color = 0, textSize = 10f, cx = 200f, cy = 400f, width = 100f, height = 100f)

    assertTrue(checkClear(items = listOf(image)))
    assertTrue(checkClear(items = listOf(text)))
    assertTrue(checkClear(items = listOf(plugin)))
  }

  @Test
  fun canClearIgnoresHiddenLayerStrokesAndBlankSubpages() {
    // Only hidden-layer strokes -> nothing clearable.
    assertFalse(checkClear(strokes = listOf(StubStroke(layer = 1, centerY = 100f)), hiddenLayers = setOf(1)))
    // Page 0 has strokes and items, but current subpage is Page 1 (y in 3090..6180) which is blank -> false.
    val imgOnPage0 = ImageItem(id = "img0", fileName = "a.png", cx = 200f, cy = 500f, width = 100f, height = 100f)
    assertFalse(
      checkClear(
        isPaged = true,
        pageTop = 3090f,
        pageBottom = 6180f,
        strokes = listOf(StubStroke(layer = 0, centerY = 500f)),
        items = listOf(imgOnPage0),
      ),
    )
    // Item on Page 1 -> true even with zero strokes on Page 1.
    val textOnPage1 = TextItem(id = "txt1", text = "p1", paperColor = 0, textSize = 40f, cx = 200f, cy = 3500f, width = 100f, height = 50f)
    assertTrue(
      checkClear(
        isPaged = true,
        pageTop = 3090f,
        pageBottom = 6180f,
        strokes = listOf(StubStroke(layer = 0, centerY = 500f)),
        items = listOf(textOnPage1),
      ),
    )
  }

  // ---- Layers undo/redo hidden preservation -------------------------------------------------

  @Test
  fun deleteLayerUndoAndRedoPreserveHiddenLayersAndIndices() {
    val history = StrokeHistory<String>()
    var layers = SavedLayers(names = listOf("Base", "Notes", "Grid"), hidden = listOf(1, 2), current = 2)
    history.stateHandler = { kind, state ->
      if (kind == HistoryStates.LAYERS) layers = applySavedLayers(layers, state as SavedLayers)
    }

    // Delete middle hidden layer ("Notes", index 1): "Grid" shifts from index 2 to 1 and stays hidden.
    val beforeDelete = layers
    val afterDelete = deleteLayerSaved(beforeDelete, index = 1)
    assertNotNull(afterDelete)
    history.recordState(HistoryStates.LAYERS, beforeDelete, afterDelete!!)
    layers = afterDelete

    assertEquals(listOf("Base", "Grid"), layers.names)
    assertEquals(listOf(1), layers.hidden)
    assertEquals(1, layers.current)

    // Undo restores all three layers with both index 1 ("Notes") and index 2 ("Grid") still hidden.
    assertTrue(history.undo())
    assertEquals(listOf("Base", "Notes", "Grid"), layers.names)
    assertEquals(listOf(1, 2), layers.hidden)
    assertEquals(2, layers.current)

    // Redo re-applies deletion and keeps shifted hidden layer index 1 ("Grid").
    assertTrue(history.redo())
    assertEquals(listOf("Base", "Grid"), layers.names)
    assertEquals(listOf(1), layers.hidden)
    assertEquals(1, layers.current)
  }

  @Test
  fun addAndRenameLayerPreserveExistingHiddenLayersAcrossUndoRedo() {
    val history = StrokeHistory<String>()
    var layers = SavedLayers(names = listOf("Base", "Hidden"), hidden = listOf(1), current = 0)
    history.stateHandler = { kind, state ->
      if (kind == HistoryStates.LAYERS) layers = applySavedLayers(layers, state as SavedLayers)
    }

    val beforeAdd = layers
    val afterAdd = addLayerSaved(beforeAdd)
    history.recordState(HistoryStates.LAYERS, beforeAdd, afterAdd)
    layers = afterAdd
    assertEquals(listOf(1), layers.hidden)
    assertEquals(2, layers.current)

    val beforeRename = layers
    val afterRename = renameLayerSaved(beforeRename, 1, "Sketch")!!
    history.recordState(HistoryStates.LAYERS, beforeRename, afterRename)
    layers = afterRename

    assertTrue(history.undo()) // undo rename
    assertEquals(listOf("Base", "Hidden", "Layer 3"), layers.names)
    assertEquals(listOf(1), layers.hidden)

    assertTrue(history.undo()) // undo add
    assertEquals(listOf("Base", "Hidden"), layers.names)
    assertEquals(listOf(1), layers.hidden)
    assertEquals(0, layers.current)

    assertTrue(history.redo()) // redo add
    assertEquals(listOf("Base", "Hidden", "Layer 3"), layers.names)
    assertEquals(listOf(1), layers.hidden)
    assertEquals(2, layers.current)
  }

  @Test
  fun legacyV4SavedLayersPreservesCurrentHiddenState() {
    val current = SavedLayers(names = listOf("Base", "Notes", "Top"), hidden = listOf(0, 2), current = 1)
    val legacy = SavedLayers(names = listOf("Base", "Notes"), hidden = emptyList(), current = HistoryFile.LEGACY_LAYER_CURRENT)
    val applied = applySavedLayers(current, legacy)
    assertEquals(listOf("Base", "Notes"), applied.names)
    assertEquals(listOf(0), applied.hidden) // index 2 pruned, index 0 preserved
    assertEquals(1, applied.current)
  }

  // ---- MAX_ZOOM / MIN_ZOOM scaling with trueSizeZoom ----------------------------------------

  @Test
  fun zoomBoundsScaleWithTrueSizeZoomForHighDpiTablets() {
    // Endless canvas: trueSizeZoom = 1f -> 25%..800%.
    assertEquals(0.25f, minZoomFor(1f), 1e-6f)
    assertEquals(8f, maxZoomFor(1f), 1e-6f)

    // Galaxy Tab S9 (340 dpi): trueSizeZoom = 340 / 25.4 / 10 ≈ 1.33858f -> maxZoom reaches 800% true size (~10.71).
    val tabS9TrueSize = 340f / 25.4f / PageLayout.UNITS_PER_MM
    val tabS9Max = maxZoomFor(tabS9TrueSize)
    assertTrue(tabS9Max > BASE_MAX_ZOOM)
    assertEquals(800, (tabS9Max / tabS9TrueSize * 100f).roundToInt())

    // Very high-DPI display (480 dpi): still reaches 800% true size, and trueSizeZoom stays inside [min, max].
    val hiDpiTrueSize = 480f / 25.4f / PageLayout.UNITS_PER_MM
    assertTrue(hiDpiTrueSize in minZoomFor(hiDpiTrueSize)..maxZoomFor(hiDpiTrueSize))
    assertEquals(800, (maxZoomFor(hiDpiTrueSize) / hiDpiTrueSize * 100f).roundToInt())

    // Low-DPI display (160 dpi): minZoom scales down so 25% true size remains reachable.
    val mdpiTrueSize = 160f / 25.4f / PageLayout.UNITS_PER_MM
    assertEquals(25, (minZoomFor(mdpiTrueSize) / mdpiTrueSize * 100f).roundToInt())
    assertTrue(maxZoomFor(mdpiTrueSize) >= BASE_MAX_ZOOM)
  }

  // ---- snapPointToLineRay --------------------------------------------------------------------

  @Test
  fun snapPointToLineRaySnapsWithinAngleOrPerpSlopAndReleasesOutside() {
    // Horizontal ray from (0, 0) through (100, 0): extending to ~500 units at 3° (tan(3°) ≈ 0.0524 -> y ≈ 26.2)
    // exceeds perp slop (14) yet stays within ±6° -> snaps onto axis (y = 0).
    val (sx1, sy1) = snapPointToLineRay(
      anchorX = 0f,
      anchorY = 0f,
      origEndX = 100f,
      origEndY = 0f,
      rawX = 500f,
      rawY = 26f,
      cosThreshold = LINE_SNAP_COS_THRESHOLD,
      screenPerpSlopPage = 14f,
    )
    assertEquals(500f, sx1, 1e-3f)
    assertEquals(0f, sy1, 1e-4f)

    // Short drag at ~11° (x = 50, y = 10 -> perp = 10 <= slop 14) -> snaps via perpendicular slop.
    val (sx2, sy2) = snapPointToLineRay(
      anchorX = 0f,
      anchorY = 0f,
      origEndX = 100f,
      origEndY = 0f,
      rawX = 50f,
      rawY = 10f,
      cosThreshold = LINE_SNAP_COS_THRESHOLD,
      screenPerpSlopPage = 14f,
    )
    assertEquals(50f, sx2, 1e-3f)
    assertEquals(0f, sy2, 1e-4f)

    // Deliberate 20° rotation far from anchor (x = 200, y = 73 -> cos ≈ 0.939 < 0.9945 and perp = 73 > 14) -> breaks free.
    val (sx3, sy3) = snapPointToLineRay(
      anchorX = 0f,
      anchorY = 0f,
      origEndX = 100f,
      origEndY = 0f,
      rawX = 200f,
      rawY = 73f,
      cosThreshold = LINE_SNAP_COS_THRESHOLD,
      screenPerpSlopPage = 14f,
    )
    assertEquals(200f, sx3, 1e-4f)
    assertEquals(73f, sy3, 1e-4f)
  }

  @Test
  fun snapPointToLineRayNeverSnapsBackwardBehindAnchor() {
    val (sx, sy) = snapPointToLineRay(
      anchorX = 100f,
      anchorY = 50f,
      origEndX = 200f,
      origEndY = 50f,
      rawX = 80f,
      rawY = 52f,
      cosThreshold = LINE_SNAP_COS_THRESHOLD,
      screenPerpSlopPage = 20f,
    )
    assertEquals(80f, sx, 1e-4f)
    assertEquals(52f, sy, 1e-4f)
  }

  // ---- computeVelocityScaledFactor -----------------------------------------------------------

  @Test
  fun velocityScaledEraserHandlesDisabledSlowFastClampAndEmaSmoothing() {
    // Disabled always returns 1.0 immediately.
    assertEquals(1.0f, computeVelocityScaledFactor(speedDpPerSec = 2000f, prevFactor = 1.9f, dtMs = 16f, enabled = false), 1e-6f)

    // Slow speed <= 250 dp/s stays 1.0 when starting at 1.0.
    assertEquals(1.0f, computeVelocityScaledFactor(speedDpPerSec = 200f, prevFactor = 1.0f, dtMs = 16f, enabled = true), 1e-6f)
    assertEquals(1.0f, computeVelocityScaledFactor(speedDpPerSec = 250f, prevFactor = 1.0f, dtMs = 16f, enabled = true), 1e-6f)

    // EMA smoothing at 16 ms for fast sweep (>= 1400 dp/s): moves partway from 1.0 toward 2.2.
    val step1 = computeVelocityScaledFactor(speedDpPerSec = 1800f, prevFactor = 1.0f, dtMs = 16f, enabled = true)
    assertTrue(step1 > 1.0f && step1 < ERASER_VEL_MAX_FACTOR)
    val step2 = computeVelocityScaledFactor(speedDpPerSec = 1800f, prevFactor = step1, dtMs = 16f, enabled = true)
    assertTrue(step2 > step1 && step2 < ERASER_VEL_MAX_FACTOR)

    // Very fast sweep over sufficient time clamps cleanly at 2.2f.
    val clamped = computeVelocityScaledFactor(speedDpPerSec = 5000f, prevFactor = 1.0f, dtMs = 120f, enabled = true)
    assertEquals(ERASER_VEL_MAX_FACTOR, clamped, 1e-6f)
    assertEquals(ERASER_VEL_MAX_FACTOR, computeVelocityScaledFactor(5000f, ERASER_VEL_MAX_FACTOR, 16f, true), 1e-6f)

    // Slowing back down decays smoothly toward 1.0f.
    val decayed = computeVelocityScaledFactor(speedDpPerSec = 100f, prevFactor = 2.0f, dtMs = 16f, enabled = true)
    assertTrue(decayed < 2.0f && decayed > 1.0f)
  }

  // ---- computePluginPlacementTransform -------------------------------------------------------

  @Test
  fun pluginPlacementScalesOversizedSelectionDiagramsDownWithReadableFloor() {
    // Selection is 600x400; diagram is 1800x1200 (> 1.35x * 600 = 810) -> scales toward 1.15x * 600 = 690.
    val oversized = computePluginPlacementTransform(
      incomingLeft = 100f,
      incomingTop = 100f,
      incomingRight = 1900f,
      incomingBottom = 1300f,
      wasEmpty = false,
      selectionLeft = 200f,
      selectionTop = 200f,
      selectionRight = 800f,
      selectionBottom = 600f,
      visibleLeft = 0f,
      visibleTop = 0f,
      visibleRight = 2100f,
      visibleBottom = 2970f,
      minFloorPage = 400f,
    )
    assertEquals(690f / 1800f, oversized.scale, 1e-4f)
    assertEquals(1000f, oversized.anchorX, 1e-4f)
    assertEquals(700f, oversized.anchorY, 1e-4f)
    assertEquals(0f, oversized.dx, 1e-4f)
    assertEquals(0f, oversized.dy, 1e-4f)

    // Reasonable diagram (750 <= 1.35 * 600 = 810) stays 1:1.
    val normal = computePluginPlacementTransform(
      incomingLeft = 200f,
      incomingTop = 200f,
      incomingRight = 950f,
      incomingBottom = 600f,
      wasEmpty = false,
      selectionLeft = 200f,
      selectionTop = 200f,
      selectionRight = 800f,
      selectionBottom = 600f,
      visibleLeft = 0f,
      visibleTop = 0f,
      visibleRight = 2100f,
      visibleBottom = 2970f,
      minFloorPage = 400f,
    )
    assertEquals(1f, normal.scale, 1e-6f)

    // Tiny text snippet (80x40) with 400-unit floor and 1200-unit diagram scales to floor (400 / 1200), not 92 / 1200.
    val tiny = computePluginPlacementTransform(
      incomingLeft = 0f,
      incomingTop = 0f,
      incomingRight = 1200f,
      incomingBottom = 800f,
      wasEmpty = false,
      selectionLeft = 100f,
      selectionTop = 100f,
      selectionRight = 180f,
      selectionBottom = 140f,
      visibleLeft = 0f,
      visibleTop = 0f,
      visibleRight = 2100f,
      visibleBottom = 2970f,
      minFloorPage = 400f,
    )
    assertEquals(400f / 1200f, tiny.scale, 1e-4f)
  }

  @Test
  fun pluginPlacementClampsOversizedEmptySelectionDiagramsToViewportAndCenters() {
    // Visible viewport: 1000x800 centered at (600, 500). Max allowed: 650 x 520.
    // Incoming diagram: 1300x1040 -> scale = min(650/1300, 520/1040) = 0.5f.
    val t = computePluginPlacementTransform(
      incomingLeft = 0f,
      incomingTop = 0f,
      incomingRight = 1300f,
      incomingBottom = 1040f,
      wasEmpty = true,
      selectionLeft = 0f,
      selectionTop = 0f,
      selectionRight = 0f,
      selectionBottom = 0f,
      visibleLeft = 100f,
      visibleTop = 100f,
      visibleRight = 1100f,
      visibleBottom = 900f,
      minFloorPage = 400f,
    )
    assertEquals(0.5f, t.scale, 1e-5f)
    assertEquals(650f, t.anchorX, 1e-4f)
    assertEquals(520f, t.anchorY, 1e-4f)
    // Centered in visible viewport: anchor + dx == visCx (600), anchor + dy == visCy (500)
    assertEquals(600f, t.anchorX + t.dx, 1e-4f)
    assertEquals(500f, t.anchorY + t.dy, 1e-4f)
  }

  // ---- hitTestTopItem ------------------------------------------------------------------------

  @Test
  fun hitTestTopItemPicksTopmostVisibleObjectAndSkipsHiddenAndMisses() {
    val bottomImg = ImageItem(id = "img1", fileName = "a.png", cx = 200f, cy = 200f, width = 120f, height = 80f)
    val midText = TextItem(id = "txt1", text = "hi", paperColor = 0, textSize = 30f, cx = 210f, cy = 210f, width = 100f, height = 60f)
    val topPlugin = PluginPageItem(id = "plg1", kind = 3, content = "x\ny", color = 0, textSize = 0f, cx = 220f, cy = 220f, width = 100f, height = 60f)
    val items = listOf(bottomImg, midText, topPlugin)

    // Reverse z-order picks topPlugin when all overlap.
    assertSame(topPlugin, hitTestTopItem(items, pageX = 215f, pageY = 215f))

    // Hidden topPlugin is skipped -> midText is returned.
    assertSame(midText, hitTestTopItem(items, pageX = 215f, pageY = 215f, hiddenItemIds = setOf("plg1")))

    // Tolerance expands bounds cleanly (bottomImg covers x in 140..260; with tol 15 -> 125..275).
    assertSame(bottomImg, hitTestTopItem(items, pageX = 130f, pageY = 170f, tolerancePage = 15f))
    assertNull(hitTestTopItem(items, pageX = 130f, pageY = 170f, tolerancePage = 0f))

    // Miss outside all items returns null.
    assertNull(hitTestTopItem(items, pageX = 900f, pageY = 900f, tolerancePage = 10f))
  }

  // ---- RubberBandLineState (6.6 live hold-to-snap straight line) -----------------------------

  @Test
  fun rubberBandLineKeepsAnchorFixedWhileUpdatingEndFreelyAndFinalizesCleanly() {
    val initial = RubberBandLineState(anchorX = 120f, anchorY = 240f, endX = 320f, endY = 240f)

    // Move freely to an arbitrary 360-degree angle (e.g. up-left, ~135°): anchor must stay fixed, no angle snap.
    val aimed1 = updateRubberBandLineEnd(initial, newEndX = 40f, newEndY = 160f)
    assertEquals(120f, aimed1.anchorX, 0f)
    assertEquals(240f, aimed1.anchorY, 0f)
    assertEquals(40f, aimed1.endX, 0f)
    assertEquals(160f, aimed1.endY, 0f)

    // Move freely again to stretch downward-right (~37°, arbitrary non-axis angle).
    val aimed2 = updateRubberBandLineEnd(aimed1, newEndX = 520f, newEndY = 540f)
    assertEquals(120f, aimed2.anchorX, 0f)
    assertEquals(240f, aimed2.anchorY, 0f)
    assertEquals(520f, aimed2.endX, 0f)
    assertEquals(540f, aimed2.endY, 0f)

    val committed = finalizeRubberBandLinePoints(aimed2)
    assertEquals(listOf(Point(120f, 240f), Point(520f, 540f)), committed)

    // Degenerate zero-length release back onto anchor nudges slightly so Ink stroke remains valid.
    val collapsed = updateRubberBandLineEnd(initial, newEndX = 120f, newEndY = 240f)
    val collapsedPts = finalizeRubberBandLinePoints(collapsed)
    assertEquals(2, collapsedPts.size)
    assertEquals(Point(120f, 240f), collapsedPts[0])
    assertTrue(hypot(collapsedPts[1].x - collapsedPts[0].x, collapsedPts[1].y - collapsedPts[0].y) > 0f)
  }
}
