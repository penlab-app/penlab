package dev.penlab.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionAndPresentTest {
  @Test
  fun pasteEnabledMatchesClipboardAvailability() {
    val empty = resolveSelectionBarState(hasClipboard = false, canFillSelection = false, registeredPlugins = emptyList())
    val filled = resolveSelectionBarState(hasClipboard = true, canFillSelection = true, registeredPlugins = emptyList())

    assertFalse(empty.pasteEnabled)
    assertFalse(empty.showFill)
    assertTrue(filled.pasteEnabled)
    assertTrue(filled.showFill)
  }

  @Test
  fun noPluginEntriesWhenNoPluginIsRegistered() {
    val state = resolveSelectionBarState(hasClipboard = false, canFillSelection = true, registeredPlugins = emptyList())

    assertTrue(state.pluginEntries.isEmpty())
  }

  @Test
  fun matchingRegisteredPluginGetsItsSlotAndIsTheOnlyEntry() {
    var invoked = false
    val latexPlugin =
      SelectionPluginAction(
        id = "dev.penlab.latex",
        label = "Convert to LaTeX",
        onClick = { invoked = true },
      )
    val state =
      resolveSelectionBarState(
        hasClipboard = true,
        canFillSelection = true,
        registeredPlugins = listOf(latexPlugin),
      )

    assertEquals(1, state.pluginEntries.size)
    val first = state.pluginEntries[0] as SelectionPluginMenuEntry.Registered
    assertEquals(BuiltInSelectionPluginSlot.LATEX, first.matchedSlot)
    first.action.onClick()
    assertTrue(invoked)
  }

  @Test
  fun customRegisteredPluginIsListedWithoutASlot() {
    val custom = SelectionPluginAction(id = "dev.example.ocr", label = "Extract summary", onClick = {})
    val state =
      resolveSelectionBarState(
        hasClipboard = false,
        canFillSelection = false,
        registeredPlugins = listOf(custom),
      )

    assertEquals(1, state.pluginEntries.size)
    val first = state.pluginEntries[0] as SelectionPluginMenuEntry.Registered
    assertNull(first.matchedSlot)
    assertEquals("Extract summary", first.action.label)
  }

  @Test
  fun selectionBarCentersAboveSelectionWhenRoomExists() {
    val placed =
      placeSelectionBar(
        selectionLeft = 500f,
        selectionTop = 320f,
        selectionRight = 700f,
        selectionBottom = 440f,
        barWidth = 480,
        barHeight = 48,
        containerWidth = 1205,
        containerHeight = 753,
        gapPx = 12,
        topClearancePx = 80,
        bottomClearancePx = 12,
      )

    assertTrue(placed.placedAbove)
    assertEquals(360, placed.x)
    assertEquals(260, placed.y)
  }

  @Test
  fun selectionBarFlipsBelowTopChromeInsteadOfOverlappingIslands() {
    val placed =
      placeSelectionBar(
        selectionLeft = 400f,
        selectionTop = 95f,
        selectionRight = 600f,
        selectionBottom = 180f,
        barWidth = 480,
        barHeight = 48,
        containerWidth = 1205,
        containerHeight = 753,
        gapPx = 12,
        topClearancePx = 88,
        bottomClearancePx = 12,
      )

    assertFalse(placed.placedAbove)
    assertEquals(192, placed.y)
  }

  @Test
  fun selectionBarClampsInsideLeftAndRightEdgesIncludingDockedRail() {
    val leftClamped =
      placeSelectionBar(
        selectionLeft = 20f,
        selectionTop = 300f,
        selectionRight = 90f,
        selectionBottom = 380f,
        barWidth = 500,
        barHeight = 48,
        containerWidth = 1205,
        containerHeight = 753,
        gapPx = 12,
        leftClearancePx = 72,
        rightClearancePx = 12,
      )
    assertEquals(72, leftClamped.x)

    val rightClamped =
      placeSelectionBar(
        selectionLeft = 1100f,
        selectionTop = 300f,
        selectionRight = 1190f,
        selectionBottom = 380f,
        barWidth = 500,
        barHeight = 48,
        containerWidth = 1205,
        containerHeight = 753,
        gapPx = 12,
        leftClearancePx = 12,
        rightClearancePx = 72,
      )
    assertEquals(1205 - 500 - 72, rightClamped.x)
  }

  @Test
  fun floatingPastePillOnlyShowsForLassoWithClipboardAndNoActiveOverlay() {
    assertTrue(
      shouldShowFloatingPastePill(
        selectionUiVisible = true,
        hasClipboard = true,
        hasSelection = false,
        pasteMenuOpen = false,
        isLassoSelected = true,
      ),
    )
    assertFalse(shouldShowFloatingPastePill(false, true, false, false, true))
    assertFalse(shouldShowFloatingPastePill(true, false, false, false, true))
    assertFalse(shouldShowFloatingPastePill(true, true, true, false, true))
    assertFalse(shouldShowFloatingPastePill(true, true, false, true, true))
    assertFalse(shouldShowFloatingPastePill(true, true, false, false, false))
  }

  @Test
  fun presentModeOpacityAndChromeTransitionsFollowContract() {
    assertEquals(0.60f, presentPillAlpha(toolsPeeking = false), 0.001f)
    assertEquals(1.0f, presentPillAlpha(toolsPeeking = true), 0.001f)
    assertEquals(0.72f, FULLSCREEN_EXIT_ALPHA, 0.001f)
    // The pen-tools button is a toggle (Akram, 24 Nov): no auto-hide timer to assert.
    assertTrue(PRESENT_HINT_MESSAGE.contains("Presenting"))
    assertTrue(PRESENT_HINT_MS in 1500L..4000L)

    // Normal -> Fullscreen -> Present -> Peek -> Exit Present
    val normal = ScreenChrome()
    assertTrue(normal.topBar && normal.toolbar && normal.selectionUi)

    val full = normal.copy(fullScreen = true)
    assertFalse(full.topBar)
    assertTrue(full.leaveFullScreenButton && full.systemBarsHidden && full.toolbar)
    assertFalse(full.keepScreenOn)

    val presenting = full.copy(present = true, toolsPeeking = false)
    assertTrue(presenting.presentButtons && presenting.keepScreenOn && presenting.systemBarsHidden && presenting.backLeavesPresent)
    assertFalse(presenting.topBar || presenting.toolbar || presenting.selectionUi || presenting.messages || presenting.leaveFullScreenButton)

    val peeking = presenting.copy(toolsPeeking = true)
    assertTrue(peeking.toolbar && peeking.presentButtons && peeking.keepScreenOn)
    assertFalse(peeking.topBar || peeking.selectionUi || peeking.messages)

    val exited = peeking.copy(present = false, toolsPeeking = false)
    assertTrue(exited.leaveFullScreenButton && exited.toolbar && exited.selectionUi && exited.messages)
    assertFalse(exited.presentButtons || exited.keepScreenOn)
  }

  @Test
  fun backPeelsOverlayThenPresentThenFullScreenThenNotebook() {
    assertEquals(BackAction.CLOSE_OVERLAYS, backAction(overlayOpen = true, present = true, fullScreen = true))
    assertEquals(BackAction.CLOSE_OVERLAYS, backAction(overlayOpen = true, present = false, fullScreen = false))
    assertEquals(BackAction.LEAVE_PRESENT, backAction(overlayOpen = false, present = true, fullScreen = true))
    assertEquals(BackAction.LEAVE_FULL_SCREEN, backAction(overlayOpen = false, present = false, fullScreen = true))
    assertEquals(BackAction.LEAVE_NOTEBOOK, backAction(overlayOpen = false, present = false, fullScreen = false))
  }

  @Test
  fun pageThumbnailUsesThePagesOwnShape() {
    assertEquals(297f / 210f, thumbnailAspect(297f, 210f), 1e-6f) // A4 landscape is wider than tall
    assertEquals(210f / 297f, thumbnailAspect(210f, 297f), 1e-6f)
    assertEquals(A4_PORTRAIT_ASPECT, thumbnailAspect(0f, 0f), 1e-6f) // unknown size falls back to A4 portrait
  }
}
