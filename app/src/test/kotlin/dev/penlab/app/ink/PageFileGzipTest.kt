package dev.penlab.app.ink

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pages saved before 20 Nov are gzip files; they must still open. */
class PageFileGzipTest {
  @Test fun oldGzipFileStillReads() {
    val stroke = SavedStroke("pen", 1, 2f, 0.1f, false, 2, 1f, FloatArray(10) { it.toFloat() }, LongArray(2) { it * 5L }, 1, 7L)
    val page = SavedPage("LINED", FloatArray(9) { it.toFloat() }, listOf(stroke))
    val plain = ByteArrayOutputStream().also { PageFile.write(page, it) }.toByteArray()
    val zipped = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(plain) } }.toByteArray()
    for (bytes in listOf(plain, zipped)) {
      val back = PageFile.read(ByteArrayInputStream(bytes))
      assertEquals("LINED", back.pageStyle)
      assertEquals(stroke.points.toList(), back.strokes[0].points.toList())
      assertEquals(7L, back.strokes[0].mediaMark)
    }
  }
}
