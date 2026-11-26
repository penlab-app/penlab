package dev.penlab.app.ui

import dev.penlab.app.ink.InkTool
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

  @Test
  fun dropInInteriorLandsFloating() {
    val target = DockDrop.resolveTarget(
      x = 640f,
      y = 400f,
      width = 1280f,
      height = 800f,
      cornerReachPx = 110f,
      edgeSnapPx = 76f,
    )
    assertEquals(DockSide.FLOATING, target)
  }

  @Test
  fun dropNearEdgesSnapsToEdgeAndCornersSnapToWheel() {
    val w = 1280f
    val h = 800f
    val corner = 110f
    val edge = 76f

    assertEquals(DockSide.LEFT, DockDrop.resolveTarget(40f, 400f, w, h, corner, edge))
    assertEquals(DockSide.RIGHT, DockDrop.resolveTarget(w - 40f, 400f, w, h, corner, edge))
    assertEquals(DockSide.TOP, DockDrop.resolveTarget(640f, 40f, w, h, corner, edge))
    assertEquals(DockSide.BOTTOM, DockDrop.resolveTarget(640f, h - 40f, w, h, corner, edge))

    assertEquals(DockSide.TOP_LEFT, DockDrop.resolveTarget(80f, 80f, w, h, corner, edge))
    assertEquals(DockSide.TOP_RIGHT, DockDrop.resolveTarget(w - 80f, 80f, w, h, corner, edge))
    assertEquals(DockSide.BOTTOM_LEFT, DockDrop.resolveTarget(80f, h - 80f, w, h, corner, edge))
    assertEquals(DockSide.BOTTOM_RIGHT, DockDrop.resolveTarget(w - 80f, h - 80f, w, h, corner, edge))
  }

  @Test
  fun collapsedToolbarKeepsOnlyPensAndEraser() {
    val tools =
      listOf(
        InkTool(InkTool.Kind.PEN, 0xFF111111.toInt(), 2.2f),
        InkTool(InkTool.Kind.PEN, 0xFF1565C0.toInt(), 2.2f),
        InkTool(InkTool.Kind.HIGHLIGHTER, 0xFFFDD835.toInt(), 14f),
        InkTool(InkTool.Kind.ERASER, 0, 14f),
        InkTool(InkTool.Kind.LASSO, 0, 0f),
        InkTool(InkTool.Kind.TEXT, 0xFF111111.toInt(), 4f),
      )
    val items =
      listOf(
        DockItem.Tool(0),
        DockItem.Tool(1),
        DockItem.Tool(2),
        DockItem.Tool(3),
        DockItem.Tool(4),
        DockItem.Tool(5),
        DockItem.Instruments,
        DockItem.Paper,
        DockItem.Layers,
        DockItem.Undo,
        DockItem.Redo,
        DockItem.Clear,
        DockItem.More,
      )

    assertEquals(
      listOf(DockItem.Tool(0), DockItem.Tool(1), DockItem.Tool(3)),
      collapsedToolbarItems(items, tools),
    )
  }
}
