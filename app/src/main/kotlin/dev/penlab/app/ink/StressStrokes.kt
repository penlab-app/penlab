package dev.penlab.app.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Makes [count] small squiggles, roughly the size and point count of handwritten letters, laid
 * out in lines across a [width] × [height] area. For performance testing only: it lets a build
 * be checked against a "full page of notes" without writing one by hand.
 *
 * The same [count] always gives the same strokes, so runs can be compared.
 */
fun handwritingLikeStrokes(count: Int, brush: Brush, width: Float, height: Float): List<InkStroke> {
  val random = Random(count)
  val letter = 28f // rough letter size in pixels
  val columns = (width / (letter * 1.3f)).toInt().coerceAtLeast(1)
  val lineHeight = letter * 2.2f
  val rows = (height / lineHeight).toInt().coerceAtLeast(1)

  return List(count) { i ->
    // Wrap back to the top when the area is full, so a large count gives a denser page.
    val left = (i % columns) * letter * 1.3f + 40f
    val top = (i / columns % rows) * lineHeight + 60f + random.nextFloat() * 8f

    val inputs = MutableStrokeInputBatch()
    val phase = random.nextFloat() * 6.28f
    val loops = 1f + random.nextFloat() * 1.5f
    for (step in 0 until POINTS_PER_STROKE) {
      val t = step / (POINTS_PER_STROKE - 1f)
      val angle = phase + t * loops * 6.28f
      inputs.add(
        InputToolType.STYLUS,
        left + letter * (0.5f + 0.45f * cos(angle) * (1f - 0.3f * t)),
        top + letter * (0.5f + 0.45f * sin(angle)) + letter * 0.4f * t,
        (step * 4).toLong(), // one sample every 4 ms, like the S Pen at 240 Hz
        pressure = 0.35f + 0.5f * sin(t * 3.14f), // rises and falls along the stroke
      )
    }
    InkStroke.from(Stroke(brush, inputs))
  }
}

private const val POINTS_PER_STROKE = 40

/**
 * A full notebook of fake handwriting for stress tests: [perPage] letter-sized squiggles on each
 * of [pages] A4 pages of [layout], in lines like real notes (4 mm letters, 9 mm line spacing).
 */
fun notebookStrokes(pages: Int, perPage: Int, brush: Brush, layout: PageLayout): List<InkStroke> {
  val random = Random(pages * 7919 + perPage)
  val mm = PageLayout.UNITS_PER_MM
  val letter = 4f * mm
  val margin = 15f * mm
  val columns = ((layout.pageWidth - 2 * margin) / (letter * 1.3f)).toInt()
  val lineHeight = 9f * mm
  val rows = ((layout.pageHeight - 2 * margin) / lineHeight).toInt()
  val out = ArrayList<InkStroke>(pages * perPage)
  for (page in 0 until pages) {
    val pageTop = layout.top(page)
    for (i in 0 until perPage) {
      val left = margin + (i % columns) * letter * 1.3f
      val top = pageTop + margin + (i / columns % rows) * lineHeight + random.nextFloat() * 0.8f * mm
      // Plain numbers, no mesh: like a notebook just read from disk.
      val f = InkStroke.FLOATS_PER_POINT
      val pts = FloatArray(POINTS_PER_STROKE * f)
      val times = LongArray(POINTS_PER_STROKE) { it * 4L }
      val phase = random.nextFloat() * 6.28f
      val loops = 1f + random.nextFloat() * 1.5f
      for (step in 0 until POINTS_PER_STROKE) {
        val t = step / (POINTS_PER_STROKE - 1f)
        val angle = phase + t * loops * 6.28f
        pts[step * f] = left + letter * (0.5f + 0.45f * cos(angle) * (1f - 0.3f * t))
        pts[step * f + 1] = top + letter * (0.5f + 0.45f * sin(angle)) + letter * 0.4f * t
        pts[step * f + 2] = 0.35f + 0.5f * sin(t * 3.14f)
        pts[step * f + 3] = androidx.ink.strokes.StrokeInput.NO_TILT
        pts[step * f + 4] = androidx.ink.strokes.StrokeInput.NO_ORIENTATION
      }
      out += InkStroke(brush, pts, times)
    }
  }
  return out
}
