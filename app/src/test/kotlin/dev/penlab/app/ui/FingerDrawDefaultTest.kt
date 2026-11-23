package dev.penlab.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerDrawDefaultTest {

  @Test
  fun noStylusSeenMeansOn() = assertTrue(fingerDrawDefault(stylusSeen = false, userChoice = null))

  @Test
  fun stylusSeenWithoutChoiceMeansOff() = assertFalse(fingerDrawDefault(stylusSeen = true, userChoice = null))

  @Test
  fun userChoiceWinsEvenAfterAStylusAppears() {
    assertTrue(fingerDrawDefault(stylusSeen = true, userChoice = true))
    assertFalse(fingerDrawDefault(stylusSeen = false, userChoice = false))
  }
}
