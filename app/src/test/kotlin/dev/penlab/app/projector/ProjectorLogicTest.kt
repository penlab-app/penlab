package dev.penlab.app.projector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectorLogicTest {
  // ---- fitToAspect ------------------------------------------------------------------------

  private val tablet = Box(0f, 0f, 1600f, 1000f) // 1.6

  @Test
  fun widerProjectorExtendsSidewaysAndKeepsTheHeight() {
    val fitted = fitToAspect(tablet, 16f / 9f)
    assertEquals(1000f, fitted.height, 0.01f)
    assertEquals(1000f * 16f / 9f, fitted.width, 0.01f)
    assertEquals(tablet.centerX, fitted.centerX, 0.01f)
  }

  @Test
  fun narrowerProjectorExtendsUpAndDown() {
    val fitted = fitToAspect(tablet, 1f)
    assertEquals(1600f, fitted.width, 0.01f)
    assertEquals(1600f, fitted.height, 0.01f)
    assertEquals(tablet.centerY, fitted.centerY, 0.01f)
  }

  @Test
  fun neverCropsTheTabletsView() {
    for (aspect in listOf(0.5f, 1f, 1.6f, 16f / 9f, 21f / 9f, 4f)) {
      val fitted = fitToAspect(tablet, aspect)
      assertTrue(fitted.left <= tablet.left && fitted.right >= tablet.right)
      assertTrue(fitted.top <= tablet.top && fitted.bottom >= tablet.bottom)
      assertEquals(aspect, fitted.width / fitted.height, 0.001f)
    }
  }

  @Test
  fun sameShapeChangesNothing() {
    val fitted = fitToAspect(tablet, 1.6f)
    assertEquals(tablet.width, fitted.width, 0.01f)
    assertEquals(tablet.height, fitted.height, 0.01f)
  }

  @Test
  fun offsetViewKeepsItsCentre() {
    val view = Box(200f, 0f, 1500f, 1000f) // with a toolbar on the left
    val fitted = fitToAspect(view, 2f)
    assertEquals(view.centerX, fitted.centerX, 0.01f)
    assertEquals(2000f, fitted.width, 0.01f)
  }

  @Test
  fun degenerateInputsAreReturnedAsTheyAre() {
    assertEquals(Box(0f, 0f, 0f, 10f), fitToAspect(Box(0f, 0f, 0f, 10f), 1.6f))
    assertEquals(tablet, fitToAspect(tablet, 0f))
    assertEquals(tablet, fitToAspect(tablet, Float.NaN))
  }

  // ---- the offer --------------------------------------------------------------------------

  @Test
  fun offerNeedsDisplayAndNotebook() {
    assertTrue(shouldOfferProjector(hasDisplay = true, notebookOpen = true, active = false, alreadyOffered = false))
    assertFalse(shouldOfferProjector(false, true, false, false))
    assertFalse(shouldOfferProjector(true, false, false, false))
  }

  @Test
  fun offerIsMadeOnlyOncePerConnectionAndNotWhileOn() {
    assertFalse(shouldOfferProjector(true, true, false, alreadyOffered = true))
    assertFalse(shouldOfferProjector(true, true, active = true, alreadyOffered = false))
  }
}
