package dev.penlab.app.ink

import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentsTest {
  @Test
  fun staggeredInstrumentsNeverOverlapEachOtherOrTopChrome() {
    val (rulerY, protractorY) = computeStaggeredInstrumentPairY(
      viewHeightPx = 1600f,
      topClearancePx = 180f,
      bottomClearancePx = 48f,
    )
    assertTrue("Protractor ($protractorY) must sit above ruler ($rulerY)", protractorY < rulerY - 140f)
    assertTrue("Protractor ($protractorY) must clear top chrome", protractorY >= 400f)
  }

  @Test
  fun staggeredInstrumentsNudgeAwayFromFloatingToolbarBand() {
    val toolbarTop = 580f
    val toolbarBottom = 680f
    val (rulerY, protractorY) = computeStaggeredInstrumentPairY(
      viewHeightPx = 1600f,
      topClearancePx = 140f,
      bottomClearancePx = 48f,
      toolbarTopPx = toolbarTop,
      toolbarBottomPx = toolbarBottom,
    )
    assertTrue("Protractor ($protractorY) overlapped toolbar band", protractorY !in toolbarTop..toolbarBottom)
    assertTrue("Ruler ($rulerY) overlapped toolbar band", rulerY !in toolbarTop..toolbarBottom)
    assertTrue("Protractor ($protractorY) must remain distinct from ruler ($rulerY)", rulerY - protractorY >= 140f)
  }
}
