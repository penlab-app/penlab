package dev.penlab.app.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class StraightLineTest {
  // 10 page units per mm, like a notebook page.
  private val mm = 10f

  /** n+1 points along (x0,y0)-(x1,y1), each pushed sideways by wobble(i). */
  private fun line(x0: Float, y0: Float, x1: Float, y1: Float, n: Int = 40, wobble: (Int) -> Float = { 0f }): FloatArray {
    val length = hypot(x1 - x0, y1 - y0)
    val nx = -(y1 - y0) / length
    val ny = (x1 - x0) / length
    val out = FloatArray((n + 1) * 2)
    for (i in 0..n) {
      val t = i / n.toFloat()
      out[i * 2] = x0 + (x1 - x0) * t + nx * wobble(i)
      out[i * 2 + 1] = y0 + (y1 - y0) * t + ny * wobble(i)
    }
    return out
  }

  // ---- Detection -------------------------------------------------------------------------

  @Test
  fun aPerfectLineIsFoundWithItsEnds() {
    val ends = StraightLine.detect(line(100f, 200f, 700f, 500f), mm)!!
    assertEquals(100f, ends.ax, 0.01f)
    assertEquals(200f, ends.ay, 0.01f)
    assertEquals(700f, ends.bx, 0.01f)
    assertEquals(500f, ends.by, 0.01f)
  }

  @Test
  fun theEndNearerTheFirstPointIsA() {
    // Drawn right to left: A is the right-hand end.
    val ends = StraightLine.detect(line(700f, 500f, 100f, 200f), mm)!!
    assertEquals(700f, ends.ax, 0.01f)
    assertEquals(100f, ends.bx, 0.01f)
  }

  @Test
  fun aSlightWobbleWithinTwoPercentStillCounts() {
    // 600 units (60 mm) long: 2% is 12 units, 1.5 mm is 15, so 12 is the limit. 8 units of bow passes.
    val bow = line(0f, 0f, 600f, 0f) { i -> 8f * kotlin.math.sin(Math.PI * i / 40).toFloat() }
    assertNotNull(StraightLine.detect(bow, mm))
  }

  @Test
  fun moreThanTwoPercentIsNotALine() {
    val bow = line(0f, 0f, 600f, 0f) { i -> 20f * kotlin.math.sin(Math.PI * i / 40).toFloat() }
    assertNull(StraightLine.detect(bow, mm))
  }

  @Test
  fun aLongLineIsHeldToOnePointFiveMillimetres() {
    // 1000 mm long: 2% would be 200 mm, but the limit is 1.5 mm = 15 units.
    val ok = line(0f, 0f, 10_000f, 0f) { i -> if (i == 20) 14f else 0f }
    val bad = line(0f, 0f, 10_000f, 0f) { i -> if (i == 20) 16f else 0f }
    assertNotNull(StraightLine.detect(ok, mm))
    assertNull(StraightLine.detect(bad, mm))
  }

  @Test
  fun anArcOrAnLIsNotALine() {
    val arc = FloatArray(82)
    for (i in 0..40) {
      val a = Math.PI * i / 40
      arc[i * 2] = (300 * kotlin.math.cos(a)).toFloat()
      arc[i * 2 + 1] = (300 * kotlin.math.sin(a)).toFloat()
    }
    assertNull(StraightLine.detect(arc, mm))
    val corner = line(0f, 0f, 300f, 0f) + line(300f, 0f, 300f, 300f)
    assertNull(StraightLine.detect(corner, mm))
  }

  @Test
  fun aTickOrDotIsTooShort() {
    assertNull(StraightLine.detect(line(0f, 0f, 70f, 0f), mm)) // 7 mm < 8 mm
    assertNotNull(StraightLine.detect(line(0f, 0f, 90f, 0f), mm))
    assertNull(StraightLine.detect(floatArrayOf(5f, 5f, 5.01f, 5f), mm)) // a dotted line's dot
  }

  @Test
  fun aSinglePointOrNothingIsNotALine() {
    assertNull(StraightLine.detect(floatArrayOf(), mm))
    assertNull(StraightLine.detect(floatArrayOf(1f, 2f), mm))
  }

  @Test
  fun dashesOnOneLineTogetherAreALineButScatteredOnesAreNot() {
    // Three dashes along y = x * 0.5 (what a dashed line's pieces look like).
    val dashes = line(0f, 0f, 120f, 60f, 4) + line(160f, 80f, 280f, 140f, 4) + line(320f, 160f, 400f, 200f, 4)
    val ends = StraightLine.detect(dashes, mm)!!
    assertEquals(0f, ends.ax, 0.01f)
    assertEquals(400f, ends.bx, 0.01f)
    // The same dashes with one knocked sideways: no longer one line.
    val broken = line(0f, 0f, 120f, 60f, 4) + line(160f, 180f, 280f, 240f, 4) + line(320f, 160f, 400f, 200f, 4)
    assertNull(StraightLine.detect(broken, mm))
  }

  // ---- Endpoint drag ---------------------------------------------------------------------

  @Test
  fun draggingAnEndKeepsTheOtherEndAndReAngles() {
    val start = StraightLine.Ends(0f, 0f, 400f, 0f)
    val longer = StraightLine.moveEnd(start, 1, 800f, 0f, 80f)!!
    assertEquals(StraightLine.Ends(0f, 0f, 800f, 0f), longer)
    val shorter = StraightLine.moveEnd(start, 1, 200f, 0f, 80f)!!
    assertEquals(200f, shorter.length, 0.001f)
    val turned = StraightLine.moveEnd(start, 0, 0f, -300f, 80f)!! // A swung up; B stays
    assertEquals(400f, turned.bx, 0f)
    assertEquals(0f, turned.by, 0f)
    assertEquals(500f, turned.length, 0.001f)
  }

  @Test
  fun anEndCannotBeDraggedOntoTheOther() {
    val start = StraightLine.Ends(0f, 0f, 400f, 0f)
    assertNull(StraightLine.moveEnd(start, 1, 30f, 10f, 80f))
    assertNull(StraightLine.moveEnd(start, 0, 400f, 0f, 80f))
  }

  @Test
  fun aRebuiltLineIsStraightAndReachesBothEnds() {
    val ends = StraightLine.Ends(10f, 20f, 310f, 420f) // 500 long
    val xy = StraightLine.pointsAlong(ends, 3f)
    assertTrue(xy.size / 2 >= 500 / 3)
    assertEquals(10f, xy[0], 0f)
    assertEquals(20f, xy[1], 0f)
    assertEquals(310f, xy[xy.size - 2], 0f)
    assertEquals(420f, xy[xy.size - 1], 0f)
    // No gap bigger than the spacing, and nothing off the line.
    for (i in 1 until xy.size / 2) {
      assertTrue(hypot(xy[i * 2] - xy[i * 2 - 2], xy[i * 2 + 1] - xy[i * 2 - 1]) <= 3.01f)
      assertEquals(0f, StraightLine.distanceToSegment(xy[i * 2], xy[i * 2 + 1], ends), 0.01f)
    }
    // And what comes out is itself detected as that line.
    val back = StraightLine.detect(xy, mm)!!
    assertEquals(ends.ax, back.ax, 0.01f)
    assertEquals(ends.by, back.by, 0.01f)
  }

  @Test
  fun aVeryLongLineKeepsWithinThePointCap() {
    val xy = StraightLine.pointsAlong(StraightLine.Ends(0f, 0f, 1_000_000f, 0f), 1f)
    assertTrue(xy.size / 2 <= StraightLine.MAX_POINTS)
    assertEquals(1_000_000f, xy[xy.size - 2], 0f)
  }

  // ---- Pattern layout and grouping -------------------------------------------------------

  @Test
  fun dashedLineIsCutIntoTwelveAndThreeUnits() {
    val ranges = dashRanges(100f, LineType.DASHED.pattern!!, unit = 1f)
    assertEquals(listOf(0f to 12f, 15f to 27f, 30f to 42f, 45f to 57f, 60f to 72f, 75f to 87f, 90f to 100f), ranges)
  }

  @Test
  fun dottedAndCentreLinesMakeDotsAsZeroLengthRanges() {
    val dots = dashRanges(10f, LineType.DOTTED.pattern!!, unit = 1f)
    assertEquals(listOf(0f to 0f, 3f to 3f, 6f to 6f, 9f to 9f), dots)
    val centre = dashRanges(60f, LineType.CENTRE.pattern!!, unit = 1f)
    assertEquals(0f to 24f, centre[0])
    assertEquals(27f to 27f, centre[1]) // the dot after the long dash and a gap
    assertEquals(30f to 54f, centre[2]) // next long dash, after a gap
  }

  @Test
  fun solidAndEmptyGiveNoRanges() {
    assertTrue(dashRanges(0f, LineType.DASHED.pattern!!, 1f).isEmpty())
    assertTrue(dashRanges(50f, LineType.DASHED.pattern!!, 0f).isEmpty())
  }

  private class Piece(val group: Long, val name: String)

  @Test
  fun pickingOneDashPicksTheWholeLineAndNothingElse() {
    val a1 = Piece(7, "a1")
    val a2 = Piece(7, "a2")
    val a3 = Piece(7, "a3")
    val b1 = Piece(9, "b1")
    val solo = Piece(0, "solo")
    val solo2 = Piece(0, "solo2")
    val page = listOf(solo, a1, a2, b1, a3, solo2)
    val mates = withGroupMates(listOf(a2), page) { it.group }
    assertEquals(listOf("a1", "a3"), mates.map { it.name })
    // An ungrouped stroke brings nobody; a group already whole brings nobody.
    assertTrue(withGroupMates(listOf(solo), page) { it.group }.isEmpty())
    assertTrue(withGroupMates(listOf(a1, a2, a3), page) { it.group }.isEmpty())
    // Two lines at once.
    assertEquals(setOf("a1", "a2"), withGroupMates(listOf(a3, b1), page) { it.group }.map { it.name }.toSet())
  }
}
