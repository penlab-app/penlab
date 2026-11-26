package dev.penlab.app.plugins.host

import dev.penlab.app.ink.MediaMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The host's session state: what a plugin's calls do, and what the pen-up stamp then reads. */
class MediaSessionsTest {
  private var now = 100_000L
  private var changes = 0
  private val sessions = MediaSessions(clock = { now }, onChange = { changes++ })
  private val persisted = ArrayList<List<String>>()

  private fun open(id: String = "nb", recordings: List<String> = emptyList()) = sessions.bindNotebook(MediaSessions.NotebookBinding(id, recordings) { persisted += it })

  @Test
  fun startingReturnsTheIndexAndStoresTheRefOnceInOrder() {
    open(recordings = listOf("old1", "old2"))
    assertEquals(2, sessions.started("p", "content://rec/1", now))
    assertEquals(listOf(listOf("old1", "old2", "content://rec/1")), persisted)
    sessions.stopped("p")
    assertEquals(3, sessions.started("p", "content://rec/2", now))
    assertEquals(listOf("old1", "old2", "content://rec/1", "content://rec/2"), persisted.last())
  }

  @Test
  fun startingTheSameRefAgainReusesTheIndexAndWritesNothingNew() {
    open(recordings = listOf("a"))
    assertEquals(0, sessions.started("p", "a", now))
    assertTrue(persisted.isEmpty()) // nothing changed in the list: nothing to persist
  }

  @Test
  fun noNotebookOpenMeansMinusOneAndNoSession() {
    assertEquals(-1, sessions.started("p", "ref", now))
    assertNull(sessions.session)
  }

  @Test
  fun anUnstorableRefIsRefusedWithoutTouchingTheList() {
    open(recordings = listOf("a"))
    assertEquals(-1, sessions.started("p", "bad\u001Fref", now))
    assertEquals(-1, sessions.started("p", "", now))
    assertNull(sessions.session)
    assertTrue(persisted.isEmpty())
  }

  @Test
  fun onlyOnePluginRecordsAtATime() {
    open()
    assertEquals(0, sessions.started("p1", "r1", now))
    assertEquals(-1, sessions.started("p2", "r2", now))
    assertEquals("p1", sessions.session!!.pluginId)
    assertEquals(listOf("r1"), persisted.single()) // the refused one was not stored
    assertEquals(1, sessions.started("p1", "r1b", now)) // the same plugin may start over
  }

  @Test
  fun aStartInTheFutureOrLongAgoIsClampedToNow() {
    open()
    sessions.started("p", "a", now + 60_000)
    assertEquals(now, sessions.session!!.timeline.startMs)
    sessions.stopped("p")
    sessions.started("p", "b", now - 10 * 60_000)
    assertEquals(now, sessions.session!!.timeline.startMs)
    sessions.stopped("p")
    sessions.started("p", "c", now - 5_000)
    assertEquals(now - 5_000, sessions.session!!.timeline.startMs)
  }

  @Test
  fun pauseResumeStopOnlyAffectTheOwnersSession() {
    open()
    sessions.started("p", "r", now)
    sessions.paused("someone-else", now)
    assertFalse(sessions.session!!.paused)
    now += 3_000
    sessions.paused("p", now)
    assertTrue(sessions.session!!.paused)
    now += 7_000
    assertEquals(3_000L, sessions.elapsedMs()) // frozen while paused
    sessions.resumed("p", now)
    now += 1_000
    assertEquals(4_000L, sessions.elapsedMs())
    sessions.stopped("someone-else")
    assertNotNull(sessions.session)
    sessions.stopped("p")
    assertNull(sessions.session)
    assertEquals(0L, sessions.elapsedMs())
  }

  @Test
  fun aPluginsClockCannotRunAheadOfTheDevice() {
    open()
    sessions.started("p", "r", now)
    sessions.paused("p", now + 1_000_000) // clamped to now
    now += 2_000
    sessions.resumed("p", now)
    assertEquals(0L, sessions.elapsedMs()) // paused at start, resumed 2 s later
  }

  @Test
  fun strokesAreStampedFromTheLiveSessionOfTheirNotebook() {
    open("nb", listOf("older"))
    sessions.started("p", "r", now) // index 1
    now += 20_000
    var asked = 0
    val mark = sessions.markFor("nb") { asked++; 2_000L }
    assertEquals(1, asked)
    assertEquals(1, MediaMark.recording(mark))
    assertEquals(18_000L, MediaMark.positionMs(mark))
    assertEquals(MediaMark.NONE, sessions.markFor("other-notebook") { 0L })
  }

  @Test
  fun theStrokeDurationIsNotAskedWhenNothingRecords() {
    open()
    var asked = 0
    assertEquals(MediaMark.NONE, sessions.markFor("nb") { asked++; 1L })
    assertEquals(0, asked) // nothing to stamp: not even the stroke's duration is computed
    sessions.started("p", "r", now)
    assertEquals(MediaMark.NONE, sessions.markFor("other") { asked++; 1L })
    assertEquals(0, asked)
  }

  @Test
  fun aPausedRecordingStampsNothingForNewStrokes() {
    open()
    sessions.started("p", "r", now)
    now += 5_000
    sessions.paused("p", now)
    now += 1_000
    assertEquals(MediaMark.NONE, sessions.markFor("nb") { 100L })
    now += 4_000
    sessions.resumed("p", now)
    assertEquals(MediaMark.NONE, sessions.markFor("nb") { 100L }) // 100 ms of drawing reaches back into the pause
    now += 200
    assertTrue(sessions.markFor("nb") { 100L } != MediaMark.NONE)
  }

  @Test
  fun closingTheNotebookStopsStampingButTheRecordingGoesOn() {
    open("nb")
    sessions.started("p", "r", now)
    sessions.unbindNotebook("nb")
    assertNotNull(sessions.session)
    sessions.started("p", "r2", now) // no notebook is open any more: refused
    assertEquals("r", sessions.session!!.ref)
    open("other")
    assertEquals(MediaMark.NONE, sessions.markFor("other") { 0L }) // the live session belongs to "nb"
  }

  @Test
  fun levelIsClampedAndThrottled() {
    open()
    sessions.started("p", "r", now)
    now += 100
    sessions.level("p", 0.4f)
    assertEquals(0.4f, sessions.level, 0f)
    now += 10
    sessions.level("p", 0.9f) // too soon: dropped
    assertEquals(0.4f, sessions.level, 0f)
    now += 100
    sessions.level("p", 7f)
    assertEquals(1f, sessions.level, 0f)
    now += 100
    sessions.level("p", Float.NaN)
    assertEquals(0f, sessions.level, 0f)
    now += 100
    sessions.level("not-the-owner", 0.5f)
    assertEquals(0f, sessions.level, 0f)
    sessions.stopped("p")
    assertEquals(0f, sessions.level, 0f)
  }

  @Test
  fun playbackIsKeptAndGarbageIsDropped() {
    open(recordings = listOf("a", "b", "c"))
    sessions.playbackPosition("p", 2, 12_000, true)
    val p = sessions.playback!!
    assertEquals(2, p.recordingIndex)
    assertEquals(12_000L, p.positionMs)
    assertTrue(p.playing)
    sessions.playbackPosition("p", -1, 5, true)
    sessions.playbackPosition("p", 1, -5, true)
    assertEquals(2, sessions.playback!!.recordingIndex)
    sessions.clearPlayback()
    assertNull(sessions.playback)
  }

  @Test
  fun listenersHearAboutEveryVisibleChange() {
    open()
    val before = changes
    sessions.started("p", "r", now)
    sessions.paused("p", now)
    sessions.paused("p", now) // already paused: nothing changed
    sessions.resumed("p", now)
    sessions.stopped("p")
    assertEquals(before + 4, changes)
  }

  @Test
  fun endLocallyClearsAHungSession() {
    open()
    sessions.started("p", "r", now)
    sessions.endLocally()
    assertNull(sessions.session)
    sessions.endLocally() // idempotent
  }

  @Test
  fun theSeekStartsALittleBeforeTheInk() {
    assertEquals(8_000L, MediaSessions.seekPosition(10_000))
    assertEquals(0L, MediaSessions.seekPosition(500))
  }

  @Test
  fun playbackForAnIndexTheNotebookDoesNotListIsDroppedUntilASeekAsksForIt() {
    // A notebook migrated from Graphite: the plugin cannot match the ref, so it reports the index the seek carried.
    open(recordings = listOf("Recordings/Graphite/a.m4a", "Recordings/Graphite/b.m4a"))
    sessions.playbackPosition("p", 5, 1_000, true)
    assertNull(sessions.playback) // 5 is not in a 2-entry list
    sessions.noteSeek(5)
    sessions.playbackPosition("p", 5, 1_000, true)
    assertEquals(5, sessions.playback!!.recordingIndex)
    sessions.playbackPosition("p", 1, 2_000, true) // listed indices always count
    assertEquals(1, sessions.playback!!.recordingIndex)
    sessions.playbackPosition("p", -1, 2_000, true) // the old "unknown" report is no use for the glow
    assertEquals(1, sessions.playback!!.recordingIndex)
  }

  @Test
  fun aSeekWithoutAnIndexChangesNothing() {
    sessions.noteSeek(-1)
    sessions.playbackPosition("p", 0, 1, true)
    assertNull(sessions.playback)
  }

  @Test
  fun theLiveRecordingsIndexAlwaysCounts() {
    open(recordings = listOf("x"))
    sessions.started("p", "y", now) // index 1
    sessions.playbackPosition("p", 1, 500, false)
    assertEquals(1, sessions.playback!!.recordingIndex)
  }
}
