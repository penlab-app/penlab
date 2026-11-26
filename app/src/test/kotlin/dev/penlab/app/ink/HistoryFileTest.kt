package dev.penlab.app.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class HistoryFileTest {
  private fun stroke(x: Float) =
    SavedStroke("pressure", 0xFF111111.toInt(), 2f, 0.1f, false, 2, -1f, FloatArray(10) { x + it }, LongArray(2) { it * 4L })

  @Test
  fun everyKindOfStepRoundTrips() {
    val item = SavedItem(1, "t", 1f, 2f, 3f, 4f, 0f, "hello", 0xFF000000.toInt(), 12f)
    val history =
      SavedHistory(
        strokeCount = 3,
        undo =
          listOf(
            SavedEdit.Add(0, stroke(1f)),
            SavedEdit.Remove(listOf(listOf(2 to stroke(2f)), listOf(0 to stroke(3f)))),
            SavedEdit.Replace(listOf(Triple(1, stroke(4f), stroke(5f)))),
            SavedEdit.Group(listOf(SavedEdit.Insert(listOf(0 to stroke(6f))), SavedEdit.Pages(SavedPages(1, emptyList(), emptyList()), SavedPages(2, listOf(1), listOf(-1, 0))))),
          ),
        redo = listOf(SavedEdit.Items(emptyList(), listOf(item))),
      )
    val bytes = ByteArrayOutputStream().also { HistoryFile.write(history, it) }.toByteArray()
    val back = HistoryFile.read(ByteArrayInputStream(bytes))

    assertEquals(3, back.strokeCount)
    assertEquals(4, back.undo.size)
    val add = back.undo[0] as SavedEdit.Add
    assertEquals(1f, add.stroke.points[0], 0f)
    val remove = back.undo[1] as SavedEdit.Remove
    assertEquals(2, remove.steps.size)
    assertEquals(2, remove.steps[0][0].first)
    val group = back.undo[3] as SavedEdit.Group
    val pages = group.edits[1] as SavedEdit.Pages
    assertEquals(SavedPages(2, listOf(1), listOf(-1, 0)), pages.after)
    assertEquals(listOf(item), (back.redo[0] as SavedEdit.Items).after)
  }

  @Test
  fun lineGroupsSurviveInUndoSteps() {
    fun dash(x: Float) = SavedStroke("pen", 1, 2f, 0.1f, false, 2, -1f, FloatArray(10) { x + it }, LongArray(2), group = 99L, lineType = LineType.CENTRE.ordinal)
    val history = SavedHistory(2, listOf(SavedEdit.Remove(listOf(listOf(0 to dash(1f), 1 to dash(2f)))), SavedEdit.Replace(listOf(Triple(0, dash(3f), dash(4f))))), emptyList())
    val bytes = ByteArrayOutputStream().also { HistoryFile.write(history, it) }.toByteArray()
    val back = HistoryFile.read(ByteArrayInputStream(bytes))
    val removed = (back.undo[0] as SavedEdit.Remove).steps[0]
    assertEquals(99L, removed[1].second.group)
    assertEquals(LineType.CENTRE.ordinal, removed[0].second.lineType)
    assertEquals(99L, (back.undo[1] as SavedEdit.Replace).swaps[0].third.group)
  }

  @Test(expected = IOException::class)
  fun aPageFileIsNotTakenForHistory() {
    val bytes = ByteArrayOutputStream().also { PageFile.write(SavedPage("BLANK", FloatArray(9), emptyList()), it) }.toByteArray()
    HistoryFile.read(ByteArrayInputStream(bytes))
  }

  @Test
  fun restoredStepsUndoAndRedo() {
    val first = StrokeHistory<String>()
    first.add("a")
    first.add("b")
    first.grouped { first.recordState(7, 1, 2) }
    val (undo, redo) = first.saveableSteps(100)

    // A fresh history, as after reopening: same strokes, saved steps put back.
    val second = StrokeHistory<String>()
    var state = 2
    second.stateHandler = { _, s -> state = s as Int }
    second.load(listOf("a", "b"))
    second.restoreSteps(undo, redo)
    assertTrue(second.undo())
    assertEquals(1, state)
    assertTrue(second.undo())
    assertEquals(listOf("a"), second.strokes)
    assertTrue(second.redo())
    assertEquals(listOf("a", "b"), second.strokes)
  }

  @Test
  fun closuresAreNotSaved() {
    val history = StrokeHistory<String>()
    history.add("a")
    history.recordAction(undo = {}, redo = {})
    history.add("b")
    val (undo, _) = history.saveableSteps(100)
    assertEquals(1, undo.size) // only the step after the closure
  }
}
