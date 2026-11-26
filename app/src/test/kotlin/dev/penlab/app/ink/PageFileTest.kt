package dev.penlab.app.ink

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class PageFileTest {

  private fun stroke(family: String, highlighter: Boolean, n: Int) =
    SavedStroke(
      brushFamily = family,
      colorArgb = 0x66FFD600,
      size = 4.5f,
      epsilon = 0.1f,
      isHighlighter = highlighter,
      toolType = 3,
      strokeUnitLengthCm = -1f,
      points = FloatArray(n * SavedStroke.FLOATS_PER_POINT) { it * 0.5f },
      times = LongArray(n) { it * 4L },
    )

  private fun roundTrip(page: SavedPage): SavedPage {
    val bytes = ByteArrayOutputStream().also { PageFile.write(page, it) }.toByteArray()
    return PageFile.read(ByteArrayInputStream(bytes))
  }

  @Test
  fun everyValueSurvivesARoundTrip() {
    val page = SavedPage("DOTTED", FloatArray(9) { it + 0.25f }, listOf(stroke("fountain", false, 40), stroke("highlighter", true, 3)))
    val back = roundTrip(page)

    assertEquals("DOTTED", back.pageStyle)
    assertArrayEquals(page.view, back.view, 0f)
    assertEquals(2, back.strokes.size)
    for ((a, b) in page.strokes.zip(back.strokes)) {
      assertEquals(a.brushFamily, b.brushFamily)
      assertEquals(a.colorArgb, b.colorArgb)
      assertEquals(a.size, b.size, 0f)
      assertEquals(a.epsilon, b.epsilon, 0f)
      assertEquals(a.isHighlighter, b.isHighlighter)
      assertEquals(a.toolType, b.toolType)
      assertEquals(a.strokeUnitLengthCm, b.strokeUnitLengthCm, 0f)
      assertArrayEquals(a.points, b.points, 0f)
      assertArrayEquals(a.times, b.times)
    }
  }

  @Test
  fun photosAndTextBoxesRoundTrip() {
    val items = listOf(
      SavedItem(0, "a", 10f, 20f, 300f, 200f, 15f, "photo.jpg", 0, 0f),
      SavedItem(1, "b", 50f, 60f, 400f, 80f, 0f, "Shear force V = dM/dx\nsecond line", 0xFF1565C0.toInt(), 45f),
    )
    val back = roundTrip(SavedPage("LINED", FloatArray(9), emptyList(), items))
    assertEquals(items, back.items)
  }

  @Test
  fun versionOneFilesStillOpen() {
    // A version-1 file: same layout but version 1 and no item section.
    val out = ByteArrayOutputStream()
    java.io.DataOutputStream(java.util.zip.GZIPOutputStream(out)).use { d ->
      d.writeInt(0x47525048)
      d.writeInt(1)
      d.writeUTF("DOTTED")
      repeat(9) { d.writeFloat(0f) }
      d.writeInt(0)
    }
    val back = PageFile.read(ByteArrayInputStream(out.toByteArray()))
    assertEquals("DOTTED", back.pageStyle)
    assertTrue(back.items.isEmpty())
  }

  @Test
  fun layersRoundTripAndVersionTwoFilesAreLayerZero() {
    val onTwo = SavedStroke("pressure", 0, 1f, 0.1f, false, 2, -1f, FloatArray(10), LongArray(2), layer = 2)
    val back = roundTrip(SavedPage("BLANK", FloatArray(9), listOf(onTwo)))
    assertEquals(2, back.strokes[0].layer)

    // A version-2 file (no layer numbers) still opens, everything on layer 0.
    val out = ByteArrayOutputStream()
    java.io.DataOutputStream(java.util.zip.GZIPOutputStream(out)).use { d ->
      d.writeInt(0x47525048)
      d.writeInt(2)
      d.writeUTF("BLANK")
      repeat(9) { d.writeFloat(0f) }
      d.writeInt(1)
      d.writeUTF("pressure"); d.writeInt(0); d.writeFloat(1f); d.writeFloat(0.1f); d.writeBoolean(false); d.writeInt(2); d.writeFloat(-1f)
      d.writeInt(1); repeat(5) { d.writeFloat(1f) }; d.writeLong(0L)
      d.writeInt(0) // no items
    }
    val old = PageFile.read(ByteArrayInputStream(out.toByteArray()))
    assertEquals(0, old.strokes[0].layer)
  }

  /**
   * The app no longer records audio, but files written by versions that did must come back out
   * byte for byte: strokes keep their MediaMark, and ink pictures (kind 2: equations, diagrams)
   * keep their place and file name.
   */
  @Test
  fun mediaMarksAndInkPicturesSurviveByteForByte() {
    val mark = (3L shl 32) or 5_400_000L // recording 3, 90 minutes in
    val marked = SavedStroke("pen", 1, 2f, 0.1f, false, 2, -1f, FloatArray(10) { it * 0.25f }, LongArray(2) { it * 8L }, layer = 1, mediaMark = mark)
    val items = listOf(
      SavedItem(2, "eq", 40f, 50f, 600f, 90f, 0f, "equation.png", 0, 0f),
      SavedItem(2, "fig", 300f, 400f, 800f, 500f, 12f, "diagram.png", 0, 0f),
    )
    val page = SavedPage("LINED", FloatArray(9) { it + 0.5f }, listOf(marked, stroke("pen", false, 2)), items)

    val first = ByteArrayOutputStream().also { PageFile.write(page, it) }.toByteArray()
    val back = PageFile.read(ByteArrayInputStream(first))
    assertEquals(mark, back.strokes[0].mediaMark)
    assertEquals(-1L, back.strokes[1].mediaMark)
    assertEquals(items, back.items)

    val second = ByteArrayOutputStream().also { PageFile.write(back, it) }.toByteArray()
    assertArrayEquals(first, second)
  }

  @Test
  fun patternedLineGroupsRoundTripAndOlderFilesHaveNone() {
    val dashes = (0 until 3).map {
      SavedStroke("pen", 1, 2f, 0.1f, false, 2, -1f, FloatArray(10), LongArray(2), group = 0x1234_5678_9ABC_DEF0L, lineType = LineType.DASHED.ordinal)
    }
    val back = roundTrip(SavedPage("BLANK", FloatArray(9), dashes + stroke("pen", false, 2))).strokes
    for (i in 0 until 3) {
      assertEquals(0x1234_5678_9ABC_DEF0L, back[i].group)
      assertEquals(LineType.DASHED.ordinal, back[i].lineType)
    }
    assertEquals(0L, back[3].group)
    assertEquals(LineType.SOLID.ordinal, back[3].lineType)

    // A version-4 file (audio mark, but no group or line type) opens with every stroke on its own.
    val out = ByteArrayOutputStream()
    java.io.DataOutputStream(out).use { d ->
      d.writeInt(0x47525048)
      d.writeInt(4)
      d.writeUTF("BLANK")
      repeat(9) { d.writeFloat(0f) }
      d.writeInt(2)
      repeat(2) {
        d.writeUTF("pen"); d.writeInt(0); d.writeFloat(1f); d.writeFloat(0.1f); d.writeBoolean(false); d.writeInt(2); d.writeFloat(-1f)
        d.writeInt(1); repeat(5) { d.writeFloat(1f) }; d.writeLong(0L)
        d.writeInt(1) // layer
        d.writeLong(42L) // audio mark
      }
      d.writeInt(0) // no items
    }
    val old = PageFile.read(ByteArrayInputStream(out.toByteArray())).strokes
    assertEquals(2, old.size)
    assertEquals(42L, old[1].mediaMark)
    assertEquals(1, old[1].layer)
    assertEquals(0L, old[1].group)
    assertEquals(0, old[1].lineType)
  }

  @Test
  fun emptyPageRoundTrips() {
    val back = roundTrip(SavedPage("BLANK", FloatArray(9), emptyList()))
    assertTrue(back.strokes.isEmpty())
  }

  @Test(expected = IOException::class)
  fun somethingElseIsRejectedNotMisread() {
    PageFile.read(ByteArrayInputStream("hello, not a page".toByteArray()))
  }

  @Test(expected = IOException::class)
  fun aTruncatedFileIsRejected() {
    val page = SavedPage("BLANK", FloatArray(9), listOf(stroke("pressure", false, 200)))
    val bytes = ByteArrayOutputStream().also { PageFile.write(page, it) }.toByteArray()
    PageFile.read(ByteArrayInputStream(bytes.copyOf(bytes.size / 2)))
  }
}
