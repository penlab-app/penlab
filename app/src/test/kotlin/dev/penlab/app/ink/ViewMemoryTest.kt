package dev.penlab.app.ink

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Present mode: the view from before it comes back on leaving, and the page may start at the very top while presenting. */
class ViewMemoryTest {
  @Test
  fun leavingGetsBackTheViewFromBeforeEntering() {
    val memory = ViewMemory()
    val live = floatArrayOf(2.5f, 0f, -120f, 0f, 2.5f, -3400f, 0f, 0f, 1f) // zoomed in, scrolled down
    memory.keep(live)
    live[0] = 1f // the canvas keeps moving (fit to width) and may reuse the array
    live[2] = 0f
    assertArrayEquals(floatArrayOf(2.5f, 0f, -120f, 0f, 2.5f, -3400f, 0f, 0f, 1f), memory.take(), 0f)
  }

  @Test
  fun aViewIsRestoredOnlyOnce() {
    val memory = ViewMemory()
    assertNull("nothing kept yet (first composition is not 'leaving')", memory.take())
    memory.keep(FloatArray(9))
    memory.take()
    assertNull(memory.take())
  }

  @Test
  fun noTopClearanceWhilePresenting() {
    assertEquals(0f, topClearancePx(presenting = true, density = 2.125f), 0f)
    assertEquals(TOP_CLEARANCE_DP * 2.125f, topClearancePx(presenting = false, density = 2.125f), 0f)
  }
}
