package dev.penlab.app.plugins.host

import java.io.StringReader
import java.io.StringWriter
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The notebook's recordings list: indices must never move, whatever a plugin sends. */
class RecordingRefsTest {
  /** Through the same java.util.Properties text round trip notebook.properties uses. */
  private fun throughTheFile(list: List<String>): List<String> {
    val out = StringWriter()
    Properties().apply { setProperty("recordings", RecordingRefs.encode(list)) }.store(out, null)
    val back = Properties().apply { load(StringReader(out.toString())) }
    return RecordingRefs.decode(back.getProperty("recordings"))
  }

  @Test
  fun refsRoundTripThroughTheFileInOrder() {
    val list = listOf(
      "content://media/external/audio/media/42",
      "Recordings/Graphite/Unfiled/Lecture 1.m4a", // a migrated Graphite notebook
      "file:///sdcard/Recordings/PenLab/ünïcode ≠ weird=chars:with#specials!.m4a",
      "back\\slash and \"quotes\"",
    )
    assertEquals(list, throughTheFile(list))
    assertEquals(emptyList<String>(), throughTheFile(emptyList()))
  }

  @Test
  fun addingAppendsAndReturnsTheIndex() {
    val (one, i0) = RecordingRefs.add(emptyList(), "a")!!
    val (two, i1) = RecordingRefs.add(one, "b")!!
    assertEquals(listOf("a", "b"), two)
    assertEquals(0, i0)
    assertEquals(1, i1)
  }

  @Test
  fun theSameRefReusesItsIndexAndNeverMovesOthers() {
    val list = listOf("a", "b", "c")
    val (same, index) = RecordingRefs.add(list, "b")!!
    assertEquals(1, index)
    assertTrue(same === list)
  }

  @Test
  fun refsThatWouldShiftIndicesAreRefused() {
    for (bad in listOf("", "   ", "a\u001Fb", "a\nb", "a\u0000b", "tab\there", "x".repeat(1025))) {
      assertFalse("'$bad'", RecordingRefs.isStorable(bad))
      assertNull(RecordingRefs.add(listOf("keep"), bad))
    }
    assertTrue(RecordingRefs.isStorable("x".repeat(1024)))
    assertNotNull(RecordingRefs.add(emptyList(), " spaces inside are fine "))
  }

  @Test
  fun theUnitSeparatorCannotSplitAnEntryOnReload() {
    // If it were stored, "a\u001Fb" would read back as TWO entries and every later index would be off by one.
    val stored = RecordingRefs.add(listOf("first"), "a\u001Fb")
    assertNull(stored)
    assertEquals(listOf("first"), throughTheFile(listOf("first")))
  }
}
