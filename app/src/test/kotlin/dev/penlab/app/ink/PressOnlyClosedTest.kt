package dev.penlab.app.ink

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PressOnlyClosedTest {

  @Test
  fun theSamePressCounts() {
    assertTrue(pressOnlyClosed(10_000L, 10_000L))
    assertTrue(pressOnlyClosed(10_002L, 10_000L))
  }

  @Test
  fun aLaterPressDoesNot() = assertFalse(pressOnlyClosed(10_500L, 10_000L))

  @Test
  fun nothingClosedYetDoesNot() = assertFalse(pressOnlyClosed(10_000L, Long.MIN_VALUE / 2))
}
