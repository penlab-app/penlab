package dev.penlab.sdk

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StrokesBinaryTest {
  private fun stroke(family: StrokeFamily = StrokeFamily.FOUNTAIN, layer: Int = 2, n: Int = 3, argb: Int = 0xFF112233.toInt()) =
    SdkStroke(
      FloatArray(n) { 1.5f + it }, FloatArray(n) { 10f - it * 0.25f }, FloatArray(n) { 0.25f * (it + 1) }, LongArray(n) { it * 7L },
      if (family == StrokeFamily.HIGHLIGHTER) "HIGHLIGHTER" else "PEN", argb, 0.42f, family, layer,
    )

  @Test
  fun roundTripKeepsEveryField() {
    val back = StrokesBinary.decode(StrokesBinary.encode(listOf(stroke(), stroke(StrokeFamily.HIGHLIGHTER, layer = -1, argb = 0x66FFD600))))
    assertEquals(2, back.size)
    val a = back[0]
    assertEquals(StrokeFamily.FOUNTAIN, a.family)
    assertEquals(2, a.layer)
    assertEquals(0xFF112233.toInt(), a.argb)
    assertEquals(0.42f, a.widthMm, 0f)
    assertArrayEquals(floatArrayOf(1.5f, 2.5f, 3.5f), a.xs, 0f)
    assertArrayEquals(floatArrayOf(10f, 9.75f, 9.5f), a.ys, 0f)
    assertArrayEquals(floatArrayOf(0.25f, 0.5f, 0.75f), a.pressures, 0f)
    assertArrayEquals(longArrayOf(0, 7, 14), a.times)
    assertEquals("PEN", a.tool)
    assertEquals(StrokeFamily.HIGHLIGHTER, back[1].family)
    assertEquals("HIGHLIGHTER", back[1].tool)
    assertEquals(-1, back[1].layer)
    assertEquals(0x66FFD600, back[1].argb)
  }

  @Test
  fun everyFamilyRoundTrips() {
    for (family in StrokeFamily.entries) assertEquals(family, StrokesBinary.decode(StrokesBinary.encode(listOf(stroke(family))))[0].family)
  }

  @Test
  fun sectionsKeepTheirOrderAndEmptySectionsStay() {
    val back = StrokesBinary.decodeSections(StrokesBinary.encodeSections(listOf(listOf(stroke(), stroke()), emptyList(), listOf(stroke(n = 5)))))
    assertEquals(listOf(2, 0, 1), back.map { it.size })
    assertEquals(5, back[2][0].pointCount)
    assertEquals(0, StrokesBinary.decodeSections(StrokesBinary.encodeSections(emptyList())).size)
  }

  @Test
  fun absentPressureAndTimesStayAbsent() {
    val s = SdkStroke(floatArrayOf(0f, 1f), floatArrayOf(0f, 1f), floatArrayOf(-1f, -1f), LongArray(0), "PEN", 0xFF000000.toInt(), 0.3f)
    val back = StrokesBinary.decode(StrokesBinary.encode(listOf(s)))[0]
    assertArrayEquals(floatArrayOf(-1f, -1f), back.pressures, 0f)
    assertEquals(0, back.times.size)
    // and a file without them is smaller than one with them
    assertTrue(StrokesBinary.encode(listOf(s)).size < StrokesBinary.encode(listOf(stroke(n = 2))).size)
  }

  @Test
  fun badPointsAreDroppedAndEmptyStrokesLeftOut() {
    val s = SdkStroke(floatArrayOf(0f, Float.NaN, 2f), floatArrayOf(0f, 1f, Float.POSITIVE_INFINITY), floatArrayOf(0.1f, 0.2f, 0.3f), longArrayOf(0, 5, 9), "PEN", 0, 1f)
    val nothing = SdkStroke(floatArrayOf(Float.NaN), floatArrayOf(0f), floatArrayOf(0f), longArrayOf(0), "PEN", 0, 1f)
    val back = StrokesBinary.decode(StrokesBinary.encode(listOf(s, nothing)))
    assertEquals(1, back.size)
    assertEquals(1, back[0].pointCount)
    assertArrayEquals(floatArrayOf(0.1f), back[0].pressures, 0f)
  }

  @Test
  fun timesNeverGoBackwards() {
    val s = SdkStroke(floatArrayOf(0f, 1f, 2f), floatArrayOf(0f, 1f, 2f), floatArrayOf(0.5f, 0.5f, 0.5f), longArrayOf(10, 4, 20), "PEN", 0, 1f)
    assertArrayEquals(longArrayOf(10, 10, 20), StrokesBinary.decode(StrokesBinary.encode(listOf(s)))[0].times)
  }

  @Test
  fun mismatchedArraysAreRefusedWhenEncoding() {
    val s = SdkStroke(floatArrayOf(0f, 1f), floatArrayOf(0f), floatArrayOf(), LongArray(0), "PEN", 0, 1f)
    try {
      StrokesBinary.encode(listOf(s))
      fail()
    } catch (e: IllegalArgumentException) {
      // expected
    }
  }

  @Test
  fun anUnknownFamilyCodeReadsAsPressure() {
    val bytes = StrokesBinary.encode(listOf(stroke(StrokeFamily.BALLPOINT)))
    bytes[4 + 2 + 4 + 4] = 99 // the family byte of the first stroke
    assertEquals(StrokeFamily.PRESSURE, StrokesBinary.decode(bytes)[0].family)
  }

  @Test
  fun malformedFilesAreWireFormatExceptions() {
    val good = StrokesBinary.encode(listOf(stroke(), stroke()))
    val badMagic = good.copyOf().also { it[0] = 0 }
    val newer = good.copyOf().also { it[5] = 2 } // version 2
    val badCount = ByteBuffer.allocate(10 + 4).putInt(0x504C4E4B).putShort(1).putInt(1).putInt(Int.MAX_VALUE).array()
    val hugePoints = good.copyOf().also { ByteBuffer.wrap(it).putInt(4 + 2 + 4 + 4 + 1 + 1 + 4 + 4 + 4, Int.MAX_VALUE) }
    for ((name, bytes) in listOf("empty" to ByteArray(0), "magic" to badMagic, "version" to newer, "count" to badCount, "points" to hugePoints, "truncated" to good.copyOf(good.size - 3), "cut in header" to good.copyOf(7))) {
      try {
        StrokesBinary.decode(bytes)
        fail(name)
      } catch (e: WireFormatException) {
        // expected
      }
    }
  }

  @Test
  fun thousandsOfStrokesStayCompact() {
    val strokes = List(5000) { stroke(n = 100) }
    val bytes = StrokesBinary.encode(strokes)
    assertEquals(5000, StrokesBinary.decode(bytes).size)
    // 16 bytes a point (x, y, pressure, time) plus a 22-byte header: under 8.5 MB for half a million points, where the JSON would be several times that
    assertTrue(bytes.size < 5000 * (100 * 16 + 22) + 100)
  }

  @Test
  fun familyIdsMatchPenLabsSavedNames() {
    assertEquals(listOf("pressure", "ballpoint", "fountain", "highlighter"), StrokeFamily.entries.map { it.id })
    assertEquals(StrokeFamily.PRESSURE, StrokeFamily.fromId("pen"))
    assertEquals(StrokeFamily.HIGHLIGHTER, StrokeFamily.fromId("highlighter"))
  }
}
