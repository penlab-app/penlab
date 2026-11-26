package dev.penlab.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPillTest {
  @Test
  fun theClockShowsHoursOnlyWhenThereAreSome() {
    assertEquals("0:00", formatRecordingTime(0))
    assertEquals("0:59", formatRecordingTime(59_999))
    assertEquals("12:34", formatRecordingTime(754_000))
    assertEquals("1:02:05", formatRecordingTime(3_725_000))
    assertEquals("0:00", formatRecordingTime(-5))
  }

  @Test
  fun theMeterLightsBarsWithTheLevel() {
    assertEquals(0, litBars(0f))
    assertEquals(1, litBars(0.2f)) // quiet speech still shows a bar
    assertEquals(0, litBars(0.1f))
    assertEquals(2, litBars(0.5f))
    assertEquals(3, litBars(0.6f))
    assertEquals(5, litBars(1f))
    assertEquals(5, litBars(9f))
    assertEquals(0, litBars(-1f))
  }

  @Test
  fun thePillShowsEverywhereButPresent() {
    assertTrue(ScreenChrome().mediaPill)
    assertTrue(ScreenChrome(fullScreen = true).mediaPill) // the only way to see and stop a recording in full screen
    assertFalse(ScreenChrome(present = true).mediaPill) // recording goes on; only page and ink show
    assertFalse(ScreenChrome(present = true, toolsPeeking = true).mediaPill)
  }
}
