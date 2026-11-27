package dev.penlab.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenChromeTest {
  @Test
  fun normalScreenShowsEverything() {
    val c = ScreenChrome()
    assertTrue(c.topBar && c.toolbar && c.messages && c.selectionUi)
    assertFalse(c.presentButtons || c.leaveFullScreenButton || c.systemBarsHidden || c.keepScreenOn)
  }

  @Test
  fun fullScreenHidesOnlyTheTopBar() {
    val c = ScreenChrome(fullScreen = true)
    assertFalse(c.topBar)
    assertTrue(c.toolbar && c.messages && c.selectionUi)
    assertTrue(c.leaveFullScreenButton && c.systemBarsHidden)
    assertFalse(c.keepScreenOn)
  }

  @Test
  fun presentShowsOnlyPageAndInk() {
    val c = ScreenChrome(present = true)
    assertFalse(c.topBar || c.toolbar || c.messages || c.selectionUi)
    assertTrue(c.presentButtons && c.keepScreenOn && c.systemBarsHidden && c.backLeavesPresent)
    assertFalse(c.leaveFullScreenButton) // present has its own corner buttons
  }

  @Test
  fun presentWithFullScreenStillHidesTopBar() {
    val c = ScreenChrome(fullScreen = true, present = true)
    assertFalse(c.topBar)
    assertFalse(c.leaveFullScreenButton)
  }

  @Test
  fun peekBringsBackOnlyTheToolbar() {
    val c = ScreenChrome(present = true, toolsPeeking = true)
    assertTrue(c.toolbar)
    assertFalse(c.topBar || c.messages || c.selectionUi)
  }

  @Test
  fun peekFlagMeansNothingOutsidePresent() {
    assertTrue(ScreenChrome(toolsPeeking = true).toolbar)
    assertFalse(ScreenChrome(toolsPeeking = true).presentButtons)
  }

  @Test
  fun dockedWheelHidesOnlyTheSystemBars() {
    val c = ScreenChrome(wheelDocked = true)
    assertTrue(c.systemBarsHidden)
    assertTrue(c.topBar && c.toolbar && c.messages && c.selectionUi)
    assertFalse(c.leaveFullScreenButton || c.keepScreenOn)
  }

  @Test
  fun leavingTheWheelBringsTheBarsBackUnlessFullScreenHoldsThem() {
    assertFalse(ScreenChrome(wheelDocked = false).systemBarsHidden)
    assertTrue(ScreenChrome(wheelDocked = false, fullScreen = true).systemBarsHidden)
    assertTrue(ScreenChrome(wheelDocked = true, fullScreen = true).systemBarsHidden)
  }

  @Test
  fun wheelWithTheToolbarHiddenInPresentDoesNotChangeTheBars() {
    // Present hides the bars itself; the wheel flag adds nothing and removes nothing.
    assertTrue(ScreenChrome(present = true, wheelDocked = true).systemBarsHidden)
  }

  @Test
  fun topRowChromePillsStayUnshiftedWhenSidePillDocked() {
    val base = 12f
    for (side in DockSide.entries) {
      assertEquals(base, topChromeHorizontalPaddingDp(base, side), 0f)
    }
  }

  @Test
  fun presentCornerPillAddsExtraClearanceOnlyWhenPeekingToolbar() {
    assertEquals(16f to 16f, presentCornerPillMarginsDp(16f, toolsPeeking = false, side = DockSide.RIGHT, 70f, 60f))
    assertEquals(86f to 16f, presentCornerPillMarginsDp(16f, toolsPeeking = true, side = DockSide.RIGHT, 70f, 60f))
    assertEquals(16f to 76f, presentCornerPillMarginsDp(16f, toolsPeeking = true, side = DockSide.BOTTOM, 70f, 60f))
    assertEquals(16f to 76f, presentCornerPillMarginsDp(16f, toolsPeeking = true, side = DockSide.BOTTOM_RIGHT, 70f, 60f))
  }

  @Test
  fun topRowLeftOccupiedIncludesMediaPillWhenVisibleOnTopBar() {
    assertEquals(200f, topRowLeftOccupiedDp(200f, mediaPillVisibleOnTopBar = false, mediaPillWidthDp = 120f, gapDp = 8f), 0f)
    assertEquals(328f, topRowLeftOccupiedDp(200f, mediaPillVisibleOnTopBar = true, mediaPillWidthDp = 120f, gapDp = 8f), 0f)
  }

  @Test
  fun fileOptionsPopoverAnchorsUnderOverflowButton() {
    assertEquals(260, dev.penlab.app.fileOptionsPopoverOffsetPx(pillWidthPx = 356, popoverWidthPx = 568, buttonWidthPx = 96))
    assertEquals(100, dev.penlab.app.fileOptionsPopoverOffsetPx(pillWidthPx = 668, popoverWidthPx = 568, buttonWidthPx = 96))
  }
}
