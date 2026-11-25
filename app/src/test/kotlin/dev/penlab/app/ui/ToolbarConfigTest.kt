package dev.penlab.app.ui

import dev.penlab.app.ink.InkTool
import dev.penlab.app.ink.InkTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarConfigTest {
  @Test
  fun defaultConfigMatchesSketchOneExactly() {
    val cfg = ToolbarConfigState.default()
    assertEquals(4, cfg.penCount)
    assertEquals(
      listOf(
        ToolbarItem.PENS,
        ToolbarItem.ERASER,
        ToolbarItem.SELECT,
        ToolbarItem.FILL,
        ToolbarItem.TOOLS,
        ToolbarItem.MORE,
        ToolbarItem.UNDO_REDO,
      ),
      cfg.visibleOrderedItems,
    )
    assertFalse(ToolbarItem.HIGHLIGHTER in cfg.visible)
    assertFalse(ToolbarItem.TEXT in cfg.visible)
    assertFalse(ToolbarItem.LAYERS in cfg.visible)
    assertFalse(ToolbarItem.CLEAR in cfg.visible)
    assertEquals(ToolbarItem.entries.size, cfg.order.size)
  }

  @Test
  fun defaultToolsIncludeFourCustomizablePensWithForestGreen() {
    val pens = InkTools.defaults.filter { it.kind == InkTool.Kind.PEN }
    assertEquals(4, pens.size)
    assertEquals(0xFF111111.toInt(), pens[0].paperColor) // Black
    assertEquals(0xFF1565C0.toInt(), pens[1].paperColor) // Blue
    assertEquals(0xFFD32F2F.toInt(), pens[2].paperColor) // Red
    assertEquals(0xFF1E7E3E.toInt(), pens[3].paperColor) // Forest Green

    // Up to 6 distinct pen slot defaults exist cleanly.
    val sixColors = (0 until MAX_PEN_COUNT).map { InkTools.defaultPenForSlot(it).paperColor }.toSet()
    assertEquals(MAX_PEN_COUNT, sixColors.size)
  }

  @Test
  fun penCountClampsBetweenOneAndSix() {
    val base = ToolbarConfigState.default()
    assertEquals(1, base.withPenCount(-5).penCount)
    assertEquals(1, base.withPenCount(0).penCount)
    assertEquals(1, base.withPenCount(1).penCount)
    assertEquals(5, base.withPenCount(5).penCount)
    assertEquals(6, base.withPenCount(6).penCount)
    assertEquals(6, base.withPenCount(12).penCount)
  }

  @Test
  fun toggleItemPinsOptionalAndPreventsEmptyToolbar() {
    var cfg = ToolbarConfigState.default()
    cfg = cfg.toggleItem(ToolbarItem.HIGHLIGHTER)
    assertTrue(ToolbarItem.HIGHLIGHTER in cfg.visible)

    cfg = cfg.toggleItem(ToolbarItem.FILL)
    assertFalse(ToolbarItem.FILL in cfg.visible)

    // Hide everything except one item; trying to hide the last visible item is a no-op.
    for (item in ToolbarItem.entries) {
      if (item != ToolbarItem.PENS && item in cfg.visible) {
        cfg = cfg.toggleItem(item)
      }
    }
    assertEquals(setOf(ToolbarItem.PENS), cfg.visible)
    val attemptedEmpty = cfg.toggleItem(ToolbarItem.PENS)
    assertEquals(setOf(ToolbarItem.PENS), attemptedEmpty.visible)
  }

  @Test
  fun moveItemReordersAndRespectsBounds() {
    val base = ToolbarConfigState.default()
    // Moving first item up is a no-op.
    assertEquals(base, base.moveItem(base.order.first(), -1))
    // Moving last item down is a no-op.
    assertEquals(base, base.moveItem(base.order.last(), +1))

    // Move SELECT up above ERASER.
    val moved = base.moveItem(ToolbarItem.SELECT, -1)
    assertEquals(ToolbarItem.PENS, moved.order[0])
    assertEquals(ToolbarItem.SELECT, moved.order[1])
    assertEquals(ToolbarItem.ERASER, moved.order[2])
    assertEquals(
      listOf(
        ToolbarItem.PENS,
        ToolbarItem.SELECT,
        ToolbarItem.ERASER,
        ToolbarItem.FILL,
        ToolbarItem.TOOLS,
        ToolbarItem.MORE,
        ToolbarItem.UNDO_REDO,
      ),
      moved.visibleOrderedItems,
    )
  }

  @Test
  fun serializationRoundTripPreservesCustomState() {
    val customized =
      ToolbarConfigState.default()
        .withPenCount(6)
        .toggleItem(ToolbarItem.HIGHLIGHTER)
        .toggleItem(ToolbarItem.LAYERS)
        .toggleItem(ToolbarItem.FILL)
        .moveItem(ToolbarItem.UNDO_REDO, -2)
        .moveItem(ToolbarItem.HIGHLIGHTER, -4)

    val raw = customized.serialize()
    val restored = ToolbarConfigState.deserialize(raw)
    assertEquals(customized, restored)
  }

  @Test
  fun deserializeHandlesCorruptAndLegacyStringsSafely() {
    assertEquals(ToolbarConfigState.default(), ToolbarConfigState.deserialize(null))
    assertEquals(ToolbarConfigState.default(), ToolbarConfigState.deserialize(""))
    assertEquals(ToolbarConfigState.default(), ToolbarConfigState.deserialize("garbage"))
    assertEquals(ToolbarConfigState.default(), ToolbarConfigState.deserialize("v99|penCount=3"))

    // Legacy string missing newer enum items appends them to order and clamps penCount.
    val partial = ToolbarConfigState.deserialize("v1|penCount=99|order=ERASER,PENS,UNKNOWN|visible=ERASER,UNKNOWN")
    assertEquals(MAX_PEN_COUNT, partial.penCount)
    assertEquals(ToolbarItem.ERASER, partial.order[0])
    assertEquals(ToolbarItem.PENS, partial.order[1])
    assertEquals(ToolbarItem.entries.size, partial.order.size)
    assertEquals(setOf(ToolbarItem.ERASER), partial.visible)

    // Empty visible set falls back to defaults.
    val emptyVisible = ToolbarConfigState.deserialize("v1|penCount=2|order=PENS|visible=")
    assertEquals(2, emptyVisible.penCount)
    assertEquals(DEFAULT_VISIBLE_TOOLBAR_ITEMS.toSet(), emptyVisible.visible)
  }

  @Test
  fun resetRestoresDefaultState() {
    val changed =
      ToolbarConfigState.default()
        .withPenCount(2)
        .toggleItem(ToolbarItem.CLEAR)
        .moveItem(ToolbarItem.CLEAR, -5)
    assertEquals(ToolbarConfigState.default(), changed.reset())
  }

  @Test
  fun zoomStepperMovesCleanlyAcrossLadderAndClamps() {
    assertEquals(115, stepZoomIn(100))
    assertEquals(130, stepZoomIn(115))
    assertEquals(85, stepZoomOut(100))
    assertEquals(75, stepZoomOut(85))

    // Intermediate zoom snaps smoothly to the next ladder rung.
    assertEquals(100, stepZoomIn(92))
    assertEquals(85, stepZoomOut(92))

    // Clamps cleanly at ends.
    assertEquals(400, stepZoomIn(400))
    assertEquals(400, stepZoomIn(500))
    assertEquals(25, stepZoomOut(25))
    assertEquals(25, stepZoomOut(10))
  }
}
