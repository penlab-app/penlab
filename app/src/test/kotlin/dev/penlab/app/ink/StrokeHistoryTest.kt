package dev.penlab.app.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StrokeHistoryTest {

  private val history = StrokeHistory<String>()

  private fun add(vararg strokes: String) = strokes.forEach(history::add)

  @Test
  fun undoAndRedoAnAddedStroke() {
    add("a", "b")

    assertTrue(history.undo())
    assertEquals(listOf("a"), history.strokes)

    assertTrue(history.redo())
    assertEquals(listOf("a", "b"), history.strokes)
  }

  @Test
  fun nothingToUndoOrRedoReturnsFalse() {
    assertFalse(history.undo())
    assertFalse(history.redo())
  }

  @Test
  fun undoingAnEraseRestoresTheOriginalOrder() {
    add("a", "b", "c", "d")

    assertTrue(history.removeAll { it == "b" || it == "d" })
    assertEquals(listOf("a", "c"), history.strokes)

    history.undo()
    assertEquals(listOf("a", "b", "c", "d"), history.strokes)

    history.redo()
    assertEquals(listOf("a", "c"), history.strokes)
  }

  @Test
  fun anEraseInsideAGroupCanBeUndoneOnlyOnceTheGroupEnds() {
    // The first erase of a drag, as the canvas does it: the group is open until the pen lifts.
    val fresh = StrokeHistory<String>()
    fresh.load(listOf("a", "b"))
    fresh.beginGroup()
    fresh.removeAll { it == "a" }
    assertFalse(fresh.canUndo) // why the canvas must report again when the group ends
    assertTrue(fresh.endGroup())
    assertTrue(fresh.canUndo)
  }

  @Test
  fun endingAnEmptyOrMissingGroupReportsNothing() {
    assertFalse(history.endGroup()) // no group open
    history.beginGroup()
    assertFalse(history.endGroup()) // nothing recorded
  }

  @Test
  fun oneEraserDragIsOneUndoStep() {
    add("a", "b", "c", "d")

    history.removeAll { it == "c" }
    history.removeAll(mergeIntoPrevious = true) { it == "a" }
    history.removeAll(mergeIntoPrevious = true) { it == "d" }
    assertEquals(listOf("b"), history.strokes)

    history.undo()
    assertEquals(listOf("a", "b", "c", "d"), history.strokes)

    history.redo()
    assertEquals(listOf("b"), history.strokes)
  }

  @Test
  fun mergeDoesNotSwallowAStrokeDrawnInBetween() {
    add("a", "b")
    history.removeAll { it == "a" }
    add("c")

    // The previous step is now an add, so this erase must be its own step.
    history.removeAll(mergeIntoPrevious = true) { it == "b" }
    history.undo()

    assertEquals(listOf("b", "c"), history.strokes)
  }

  @Test
  fun eraseThatHitsNothingIsNotAnUndoStep() {
    add("a")

    assertFalse(history.removeAll { it == "zzz" })
    history.undo()

    assertEquals(emptyList<String>(), history.strokes)
  }

  @Test
  fun newStrokeAfterUndoDropsTheRedo() {
    add("a", "b")
    history.undo()
    add("c")

    assertFalse(history.redo())
    assertEquals(listOf("a", "c"), history.strokes)
  }

  @Test
  fun listenerCanRebuildThePageFromEventsAlone() {
    // A copy of the page kept up to date only through the listener, like the drawing cache.
    val mirror = mutableListOf<String>()
    history.listener =
      object : StrokeHistory.Listener<String> {
        override fun onInserted(index: Int, stroke: String) = mirror.add(index, stroke)

        override fun onRemoved(index: Int, stroke: String) {
          assertEquals(stroke, mirror.removeAt(index))
        }
      }

    val random = Random(7)
    repeat(2000) { step ->
      when (random.nextInt(6)) {
        0 -> history.add("s$step")
        1 -> history.add("s$step", index = random.nextInt(history.strokes.size + 1))
        2 -> history.removeAll(mergeIntoPrevious = random.nextBoolean()) { random.nextInt(4) == 0 }
        3 -> history.undo()
        4 -> history.redo()
        5 -> history.strokes.randomOrNull(random)?.let { history.replace(mapOf(it to "$it'")) }
      }
      assertEquals(history.strokes, mirror)
    }
  }

  @Test
  fun strokeAddedUnderneathUndoesAndRedoesInPlace() {
    add("ink1", "ink2")
    history.add("highlight", index = 0)
    assertEquals(listOf("highlight", "ink1", "ink2"), history.strokes)

    add("ink3")
    history.undo()
    history.undo()
    assertEquals(listOf("ink1", "ink2"), history.strokes)

    history.redo()
    assertEquals(listOf("highlight", "ink1", "ink2"), history.strokes)
  }

  @Test
  fun canUndoAndCanRedoFollowTheStacks() {
    assertFalse(history.canUndo)
    add("a")
    assertTrue(history.canUndo)
    assertFalse(history.canRedo)

    history.undo()
    assertFalse(history.canUndo)
    assertTrue(history.canRedo)
  }

  @Test
  fun replaceSwapsInPlaceAndIsOneUndoStep() {
    add("a", "b", "c")
    assertTrue(history.replace(mapOf("a" to "a2", "c" to "c2")))
    assertEquals(listOf("a2", "b", "c2"), history.strokes)

    history.undo()
    assertEquals(listOf("a", "b", "c"), history.strokes)
    history.redo()
    assertEquals(listOf("a2", "b", "c2"), history.strokes)
  }

  @Test
  fun addAllIsOneUndoStep() {
    add("ink1", "ink2")
    history.addAll(listOf(0 to "hl", 3 to "ink3"))
    assertEquals(listOf("hl", "ink1", "ink2", "ink3"), history.strokes)

    history.undo()
    assertEquals(listOf("ink1", "ink2"), history.strokes)
    history.redo()
    assertEquals(listOf("hl", "ink1", "ink2", "ink3"), history.strokes)
  }

  @Test
  fun loadedStrokesAreNotUndoable() {
    history.load(listOf("a", "b"))
    assertEquals(listOf("a", "b"), history.strokes)
    assertFalse(history.canUndo)
    assertFalse(history.undo())
  }

  @Test
  fun groupedChangesAreOneUndoStep() {
    add("a", "b", "c")
    var pages = 1
    history.grouped {
      history.removeAll { it == "b" }
      history.replace(mapOf("c" to "c2"))
      pages = 2
      history.recordAction(undo = { pages = 1 }, redo = { pages = 2 })
    }
    assertEquals(listOf("a", "c2"), history.strokes)

    assertTrue(history.undo())
    assertEquals(listOf("a", "b", "c"), history.strokes)
    assertEquals(1, pages)
    assertFalse("the whole group was one step", history.undo().also { history.redo() } && history.strokes == listOf("a", "b"))

    history.redo()
    assertEquals(listOf("a", "c2"), history.strokes)
    assertEquals(2, pages)
  }

  @Test
  fun clearIsUndoable() {
    add("a", "b")

    assertTrue(history.clear())
    assertEquals(emptyList<String>(), history.strokes)

    history.undo()
    assertEquals(listOf("a", "b"), history.strokes)
  }
}
