package dev.penlab.app.plugins.host

import dev.penlab.app.ink.MediaMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The packing of a stroke's mark and the recording's clock with pauses left out (the pen-up stamp's arithmetic). */
class MediaTimelineTest {
  // ---- mark packing (Graphite's AudioMark, unchanged on disk) ----

  @Test
  fun markPacksRecordingHighAndPositionLow() {
    val mark = MediaMark.of(3, 83_250L)
    assertEquals((3L shl 32) or 83_250L, mark)
    assertEquals(3, MediaMark.recording(mark))
    assertEquals(83_250L, MediaMark.positionMs(mark))
  }

  @Test
  fun markRoundTripsAtTheEdges() {
    for ((recording, position) in listOf(0 to 0L, 0 to 1L, 1 to 0L, 41 to 3_600_000L, Int.MAX_VALUE to 0xFFFFFFFEL)) {
      val mark = MediaMark.of(recording, position)
      assertEquals(recording, MediaMark.recording(mark))
      assertEquals(position, MediaMark.positionMs(mark))
    }
  }

  @Test
  fun aRealMarkIsNeverNone() {
    assertTrue(MediaMark.of(0, 0L) != MediaMark.NONE)
    assertEquals(-1L, MediaMark.NONE)
  }

  // ---- position with pauses ----

  @Test
  fun positionCountsFromTheStart() {
    val t = MediaTimeline(1_000)
    assertEquals(0L, t.positionAt(1_000))
    assertEquals(0L, t.positionAt(500)) // before the start
    assertEquals(4_000L, t.positionAt(5_000))
  }

  @Test
  fun aPauseIsLeftOut() {
    val t = MediaTimeline(0).paused(10_000).resumed(25_000)
    assertEquals(9_000L, t.positionAt(9_000))
    assertEquals(10_000L, t.positionAt(10_000))
    assertEquals(10_000L, t.positionAt(17_000)) // frozen inside the pause
    assertEquals(10_000L, t.positionAt(25_000))
    assertEquals(15_000L, t.positionAt(30_000)) // 30 s on the wall clock, 15 s of audio
  }

  @Test
  fun severalPausesAddUp() {
    val t = MediaTimeline(100).paused(1_100).resumed(2_100).paused(3_100).resumed(5_100)
    assertEquals(1_000L, t.positionAt(1_100))
    assertEquals(2_000L, t.positionAt(3_100)) // 1000 + 1000 of audio, one pause behind
    assertEquals(2_000L, t.positionAt(4_000))
    assertEquals(3_000L, t.positionAt(6_100))
  }

  @Test
  fun anOpenPauseFreezesThePositionForever() {
    val t = MediaTimeline(0).paused(4_000)
    assertTrue(t.isPaused)
    assertEquals(4_000L, t.positionAt(4_000))
    assertEquals(4_000L, t.positionAt(999_999))
    assertTrue(t.isPausedAt(5_000))
    assertFalse(t.isPausedAt(3_999))
  }

  @Test
  fun doublePauseAndStrayResumeAreIgnored() {
    val once = MediaTimeline(0).paused(1_000)
    assertTrue(once.paused(2_000) === once)
    val running = MediaTimeline(0)
    assertTrue(running.resumed(1_000) === running)
    assertFalse(running.isPaused)
  }

  @Test
  fun aWrongClockCannotMakeANegativePause() {
    val early = MediaTimeline(5_000).paused(1_000) // before the start: clamped to it
    assertEquals(0L, early.positionAt(9_000))
    val backwards = MediaTimeline(0).paused(5_000).resumed(2_000) // resume before the pause: zero-length pause
    assertEquals(8_000L, backwards.positionAt(8_000))
  }

  // ---- the mark of a committed stroke ----

  private fun session(timeline: MediaTimeline, notebook: String = "nb", index: Int = 2) = MediaSession("p", notebook, index, "ref", timeline)

  @Test
  fun aStrokeIsMarkedWithWhereItBegan() {
    val s = session(MediaTimeline(1_000))
    // The pen lifted at 11 s on the device clock after drawing for 1.5 s: it began 8.5 s into the recording... (11000-1500-1000)
    val mark = MediaSession.markFor(s, "nb", nowMs = 11_000, strokeDurationMs = 1_500)
    assertEquals(2, MediaMark.recording(mark))
    assertEquals(8_500L, MediaMark.positionMs(mark))
  }

  @Test
  fun pausesAreExcludedFromTheStamp() {
    val s = session(MediaTimeline(0).paused(10_000).resumed(40_000))
    val mark = MediaSession.markFor(s, "nb", nowMs = 45_000, strokeDurationMs = 1_000) // began at 44 s: 14 s of audio
    assertEquals(14_000L, MediaMark.positionMs(mark))
  }

  @Test
  fun aStrokeThatBeganInsideAPauseGetsNoMark() {
    val s = session(MediaTimeline(0).paused(10_000).resumed(40_000))
    assertEquals(MediaMark.NONE, MediaSession.markFor(s, "nb", nowMs = 41_000, strokeDurationMs = 5_000)) // began at 36 s: paused
    assertEquals(MediaMark.NONE, MediaSession.markFor(session(MediaTimeline(0).paused(10_000)), "nb", nowMs = 20_000, strokeDurationMs = 10)) // still paused
  }

  @Test
  fun aStrokeThatBeganBeforeAPauseKeepsItsMarkEvenIfThePenLiftedInsideIt() {
    val s = session(MediaTimeline(0).paused(10_000))
    val mark = MediaSession.markFor(s, "nb", nowMs = 10_400, strokeDurationMs = 1_000) // began at 9.4 s, before the pause
    assertEquals(9_400L, MediaMark.positionMs(mark))
  }

  @Test
  fun aStrokeThatBeganBeforeTheRecordingIsMarkedZero() {
    val mark = MediaSession.markFor(session(MediaTimeline(5_000)), "nb", nowMs = 5_300, strokeDurationMs = 2_000)
    assertEquals(0L, MediaMark.positionMs(mark))
  }

  @Test
  fun anotherNotebookOrNoSessionIsNotStamped() {
    assertEquals(MediaMark.NONE, MediaSession.markFor(session(MediaTimeline(0)), "other", 5_000, 100))
    assertEquals(MediaMark.NONE, MediaSession.markFor(null, "nb", 5_000, 100))
  }
}
