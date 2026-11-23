package dev.penlab.app.search

import dev.penlab.app.search.LineFinder.Box
import org.junit.Assert.assertEquals
import org.junit.Test

/** Units are page units: 10 per millimetre. Letters here are 5 mm tall on lines 9 mm apart. */
class LineFinderTest {
  private fun letter(x: Float, lineTop: Float, h: Float = 50f) = Box(x, lineTop, x + 35f, lineTop + h)

  private fun row(lineTop: Float, count: Int, startX: Float = 0f) = (0 until count).map { letter(startX + it * 45f, lineTop) }

  @Test
  fun twoLinesOfWritingStayApart() {
    val boxes = row(0f, 6) + row(90f, 5)
    val lines = LineFinder.group(boxes).map { it.sorted() }.sortedBy { it.first() }
    assertEquals(listOf((0..5).toList(), (6..10).toList()), lines)
  }

  @Test
  fun dotOverAnIJoinsItsLine() {
    val boxes = row(0f, 4) + Box(50f, -45f, 56f, -39f) // a 0.6 mm dot sitting 4 mm above the letters
    val lines = LineFinder.group(boxes)
    assertEquals(1, lines.size)
    assertEquals(5, lines[0].size)
  }

  @Test
  fun tallLettersAndDescendersStayOnTheirLine() {
    val boxes = row(0f, 3) + Box(140f, -20f, 170f, 50f) /* an l */ + Box(185f, 10f, 215f, 80f) /* a g */ + row(90f, 3)
    val lines = LineFinder.group(boxes).map { it.toSet() }
    assertEquals(2, lines.size)
    assertEquals(true, lines.any { it.containsAll(listOf(0, 1, 2, 3, 4)) })
  }

  @Test
  fun wideGapMakesTwoParts() {
    // Same height, but a 10 cm gap between them (two columns).
    val boxes = row(0f, 3) + row(0f, 3, startX = 1200f)
    assertEquals(2, LineFinder.group(boxes).size)
  }

  @Test
  fun drawingsAndUnderlinesAreLeftOut() {
    val boxes = row(0f, 3) + Box(0f, 60f, 1000f, 70f) /* underline 10 cm */ + Box(0f, 200f, 400f, 700f) /* 5 cm drawing */
    val lines = LineFinder.group(boxes)
    assertEquals(listOf(listOf(0, 1, 2)), lines.map { it.sorted() })
  }
}
