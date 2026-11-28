package dev.penlab.app.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
