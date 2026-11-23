package dev.penlab.app.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Turns a recognised shape's outline into an ordinary stroke, as if it had been drawn with
 * [brush] at a steady [pressure]. Points are laid every [spacing] page units so the brush
 * renders it like real ink; time advances as if the pen moved at a calm, even speed.
 */
internal fun strokeAlong(outline: List<Point>, brush: Brush, pressure: Float, spacing: Float): Stroke {
  val inputs = MutableStrokeInputBatch()
  var timeMillis = 0L
  fun add(x: Float, y: Float) {
    inputs.add(InputToolType.STYLUS, x, y, timeMillis, pressure = pressure.coerceIn(0.05f, 1f))
    timeMillis += 4
  }
  add(outline.first().x, outline.first().y)
  for ((a, b) in outline.zipWithNext()) {
    val steps = ceil(hypot(b.x - a.x, b.y - a.y) / spacing).toInt().coerceAtLeast(1)
    for (i in 1..steps) {
      val t = i / steps.toFloat()
      add(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }
  }
  return Stroke(brush, inputs)
}
