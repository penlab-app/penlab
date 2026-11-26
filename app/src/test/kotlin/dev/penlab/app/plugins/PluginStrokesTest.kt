package dev.penlab.app.plugins

import dev.penlab.plugin.PluginStroke
import dev.penlab.plugin.Tool
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginStrokesTest {
  private fun stroke(xs: FloatArray, ys: FloatArray, pressures: FloatArray, times: LongArray) = PluginStroke(xs, ys, pressures, times, Tool.PEN, 0xFF000000.toInt(), 0.4f)

  @Test
  fun packScalesMillimetresToPageUnitsAndMovesByTheOffset() {
    val p = PluginStrokes.pack(stroke(floatArrayOf(1f, 2f), floatArrayOf(3f, 4f), floatArrayOf(0.5f, 2f), longArrayOf(0, 16)), 10f, 5f, 100f)!!
    // x, y, pressure, tilt, orientation per point
    assertArrayEquals(floatArrayOf(15f, 130f, 0.5f, -1f, -1f, 25f, 140f, 1f, -1f, -1f), p.points, 0f)
    assertArrayEquals(longArrayOf(0, 16), p.times)
  }

  @Test
  fun negativeOrBrokenPressureMeansNoPressure() {
    val p = PluginStrokes.pack(stroke(floatArrayOf(0f, 1f, 2f), floatArrayOf(0f, 0f, 0f), floatArrayOf(-1f, Float.NaN, 0f), longArrayOf(0, 1, 2)), 1f)!!
    assertEquals(listOf(-1f, -1f, 0f), listOf(p.points[2], p.points[7], p.points[12]))
  }

  @Test
  fun timesNeverGoBackwardsAndAreInventedWhenMissing() {
    val back = PluginStrokes.pack(stroke(floatArrayOf(0f, 1f, 2f), floatArrayOf(0f, 0f, 0f), floatArrayOf(0.5f, 0.5f, 0.5f), longArrayOf(10, 4, 20)), 1f)!!
    assertArrayEquals(longArrayOf(10, 10, 20), back.times)
    val none = PluginStrokes.pack(stroke(floatArrayOf(0f, 1f, 2f), floatArrayOf(0f, 0f, 0f), floatArrayOf(), LongArray(0)), 1f)!!
    assertTrue(none.times.toList().zipWithNext().all { (a, b) -> b > a })
    assertEquals(-1f, none.points[2], 0f) // no pressures either
  }

  @Test
  fun pointsThatAreNotNumbersAreDroppedAndAnEmptyStrokeIsNull() {
    val p = PluginStrokes.pack(stroke(floatArrayOf(0f, Float.NaN, 2f), floatArrayOf(0f, 1f, 2f), floatArrayOf(0.1f, 0.2f, 0.3f), longArrayOf(0, 1, 2)), 1f)!!
    assertEquals(2, p.times.size)
    assertEquals(2f, p.points[5], 0f)
    assertNull(PluginStrokes.pack(stroke(floatArrayOf(Float.NaN), floatArrayOf(0f), floatArrayOf(0f), longArrayOf(0)), 1f))
    assertNull(PluginStrokes.pack(stroke(floatArrayOf(0f, 1f), floatArrayOf(0f), floatArrayOf(), longArrayOf()), 1f)) // arrays of different length
  }

  @Test
  fun anOpaqueHighlighterIsDrawnAtPenLabsOwnOpacity() {
    assertEquals(0x66FFD600, PluginStrokes.colorFor("highlighter", 0xFFFFD600.toInt()))
    assertEquals(0x80FFD600.toInt(), PluginStrokes.colorFor("highlighter", 0x80FFD600.toInt())) // already see-through: kept
    assertEquals(0xFF123456.toInt(), PluginStrokes.colorFor("fountain", 0xFF123456.toInt()))
  }

  @Test
  fun widthIsClampedAndConvertedToPageUnits() {
    assertEquals(4f, PluginStrokes.widthUnits(0.4f, 10f), 1e-6f)
    assertEquals(PluginStrokes.MIN_WIDTH_MM * 10f, PluginStrokes.widthUnits(0f, 10f), 1e-6f)
    assertEquals(PluginStrokes.MIN_WIDTH_MM * 10f, PluginStrokes.widthUnits(Float.NaN, 10f), 1e-6f)
    assertEquals(PluginStrokes.MAX_WIDTH_MM * 10f, PluginStrokes.widthUnits(1e9f, 10f), 1e-3f)
  }

  @Test
  fun layerIsTheStrokesOwnClampedToTheNotebookOrTheDefault() {
    assertEquals(1, PluginStrokes.layerFor(-1, 1, 3))
    assertEquals(2, PluginStrokes.layerFor(2, 0, 3))
    assertEquals(2, PluginStrokes.layerFor(99, 0, 3))
    assertEquals(0, PluginStrokes.layerFor(5, 0, 1))
  }

  @Test
  fun boundsCoverEveryFinitePoint() {
    val a = stroke(floatArrayOf(1f, 5f), floatArrayOf(2f, 3f), floatArrayOf(), LongArray(0))
    val b = stroke(floatArrayOf(-4f, Float.NaN), floatArrayOf(9f, 100f), floatArrayOf(), LongArray(0))
    assertArrayEquals(floatArrayOf(-4f, 2f, 5f, 9f), PluginStrokes.boundsMm(listOf(a, b))!!, 0f)
    assertNull(PluginStrokes.boundsMm(emptyList()))
  }
}
