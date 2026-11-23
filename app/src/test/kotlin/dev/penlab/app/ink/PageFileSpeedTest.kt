package dev.penlab.app.ink

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

/** A notebook the size of the Diff Eq major task (10k strokes, ~700k points): write + read time. */
class PageFileSpeedTest {
  @Test fun majorTaskSizedPage() {
    val rnd = java.util.Random(1)
    val strokes = List(10_000) {
      val n = 70
      SavedStroke("pen", -16776961, 0.5f, 0.1f, false, 2, 1f,
        FloatArray(n * SavedStroke.FLOATS_PER_POINT) { rnd.nextFloat() * 2000f }, LongArray(n) { it * 8L }, 0, -1L)
    }
    val page = SavedPage("BLANK", FloatArray(9) { 1f }, strokes)
    var bytes = ByteArray(0)
    repeat(3) { // warm up the JIT, then the last round counts
      val t0 = System.nanoTime()
      val out = ByteArrayOutputStream()
      PageFile.write(page, out)
      bytes = out.toByteArray()
      val t1 = System.nanoTime()
      val back = PageFile.read(ByteArrayInputStream(bytes))
      val t2 = System.nanoTime()
      assertEquals(strokes.size, back.strokes.size)
      assertEquals(strokes[42].points.toList(), back.strokes[42].points.toList())
      println("PageFile ${bytes.size / 1024} KB: write ${(t1 - t0) / 1_000_000} ms, read ${(t2 - t1) / 1_000_000} ms")
    }
  }
}
