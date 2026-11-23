package dev.penlab.app.ink

import dev.penlab.app.ink.RecognizedShape.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ShapeRecognizerTest {

  private val random = Random(42)

  /** Hand-drawn-ish: points every few units along [corners], with a little wobble. */
  private fun wobbly(vararg corners: Point, wobble: Float = 3f): List<Point> {
    val points = mutableListOf<Point>()
    for ((a, b) in corners.toList().zipWithNext()) {
      val steps = 25
      for (i in 0 until steps) {
        val t = i / steps.toFloat()
        points += Point(
          a.x + (b.x - a.x) * t + (random.nextFloat() - 0.5f) * wobble,
          a.y + (b.y - a.y) * t + (random.nextFloat() - 0.5f) * wobble,
        )
      }
    }
    points += corners.last()
    return points
  }

  private fun wobblyEllipse(cx: Float, cy: Float, rx: Float, ry: Float, wobble: Float = 4f, gapDegrees: Int = 10) =
    (0..(360 - gapDegrees) step 4).map {
      val a = it * PI.toFloat() / 180f
      Point(
        cx + rx * cos(a) + (random.nextFloat() - 0.5f) * wobble,
        cy + ry * sin(a) + (random.nextFloat() - 0.5f) * wobble,
      )
    }

  private fun kindOf(points: List<Point>) = ShapeRecognizer.recognize(points)?.kind

  @Test
  fun wobblyLineBecomesALine() {
    val shape = ShapeRecognizer.recognize(wobbly(Point(0f, 0f), Point(400f, 130f)))
    assertEquals(Kind.LINE, shape?.kind)
    assertEquals(2, shape?.points?.size)
  }

  @Test
  fun nearlyLevelLineBecomesExactlyLevel() {
    val shape = ShapeRecognizer.recognize(wobbly(Point(0f, 0f), Point(400f, 20f)))!!
    assertEquals(shape.points[0].y, shape.points[1].y, 0.01f)
  }

  @Test
  fun nearlyDiagonalLineBecomesExactly45Degrees() {
    val shape = ShapeRecognizer.recognize(wobbly(Point(0f, 0f), Point(300f, 320f)))!!
    val (a, b) = shape.points
    assertEquals(abs(b.x - a.x), abs(b.y - a.y), 0.5f)
  }

  @Test
  fun roughRectangleBecomesALevelRectangle() {
    val stroke = wobbly(Point(0f, 0f), Point(400f, 10f), Point(405f, 210f), Point(-5f, 200f), Point(2f, 4f))
    val shape = ShapeRecognizer.recognize(stroke)!!
    assertEquals(Kind.RECTANGLE, shape.kind)
    assertEquals(5, shape.points.size) // four corners + back to the start
    val (topLeft, topRight) = shape.points
    assertEquals("level top edge", topLeft.y, topRight.y, 0.5f)
  }

  @Test
  fun rectangleStartedMidEdgeStillHasFourCorners() {
    val stroke = wobbly(Point(200f, 0f), Point(400f, 0f), Point(400f, 200f), Point(0f, 200f), Point(0f, 0f), Point(195f, 2f))
    assertEquals(Kind.RECTANGLE, kindOf(stroke))
  }

  @Test
  fun nearlySquareRectangleBecomesASquare() {
    val stroke = wobbly(Point(0f, 0f), Point(300f, 5f), Point(305f, 285f), Point(5f, 280f), Point(3f, 3f))
    val shape = ShapeRecognizer.recognize(stroke)!!
    assertEquals(Kind.SQUARE, shape.kind)
    val (a, b, c) = shape.points
    assertEquals(Math.hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()),
      Math.hypot((c.x - b.x).toDouble(), (c.y - b.y).toDouble()), 0.5)
  }

  @Test
  fun roughTriangleBecomesATriangle() {
    val stroke = wobbly(Point(0f, 300f), Point(170f, 0f), Point(340f, 300f), Point(5f, 296f))
    assertEquals(Kind.TRIANGLE, kindOf(stroke))
  }

  @Test
  fun roughCircleBecomesACircle() {
    assertEquals(Kind.CIRCLE, kindOf(wobblyEllipse(200f, 200f, 150f, 145f)))
  }

  @Test
  fun wideOvalBecomesAnEllipseNotARectangle() {
    assertEquals(Kind.ELLIPSE, kindOf(wobblyEllipse(300f, 200f, 250f, 120f)))
  }

  @Test
  fun handwritingIsLeftAlone() {
    // A loopy scribble, like a cursive word: neither straight, closed nor round.
    val scribble = (0..200).map {
      val t = it / 20f
      Point(t * 30f + 20f * sin(t * 3f), 40f * cos(t * 3f))
    }
    assertNull(ShapeRecognizer.recognize(scribble))
  }

  @Test
  fun openRoundCurveBecomesAnArc() {
    // A "C": round, but the ends are far apart.
    val shape = ShapeRecognizer.recognize(wobblyEllipse(200f, 200f, 150f, 150f, gapDegrees = 120))
    assertEquals(Kind.ARC, shape?.kind)
  }

  @Test
  fun shallowCurveBecomesAnArcThroughItsEnds() {
    // A 90-degree bow, drawn left to right.
    val stroke = (0..45).map {
      val a = (225 + it * 2) * PI.toFloat() / 180f
      Point(300 + 200 * cos(a) + (random.nextFloat() - 0.5f) * 3f, 300 + 200 * sin(a) + (random.nextFloat() - 0.5f) * 3f)
    }
    val shape = ShapeRecognizer.recognize(stroke)!!
    assertEquals(Kind.ARC, shape.kind)
    assertEquals(stroke.first().x, shape.points.first().x, 0.01f)
    assertEquals(stroke.last().y, shape.points.last().y, 2f)
    // The arc bulges the same way as the stroke: its middle is above both ends.
    val middle = shape.points[shape.points.size / 2]
    assertTrue(middle.y < shape.points.first().y && middle.y < shape.points.last().y)
  }

  @Test
  fun sCurveIsLeftAlone() {
    val stroke = (0..60).map { Point(it * 8f, 80f * sin(it / 60f * 2 * PI.toFloat())) }
    assertNull(ShapeRecognizer.recognize(stroke))
  }

  @Test
  fun triangleWithOvershootAtTheEnd() {
    // Closing quickly, the pen runs past the start along the first edge.
    val stroke = wobbly(Point(0f, 300f), Point(170f, 0f), Point(340f, 300f), Point(0f, 300f), Point(40f, 230f), wobble = 5f)
    assertEquals(Kind.TRIANGLE, kindOf(stroke))
  }

  @Test
  fun triangleWithRoundedCorners() {
    val stroke = wobbly(
      Point(20f, 300f), Point(150f, 30f), Point(175f, 5f), Point(200f, 30f),
      Point(330f, 290f), Point(310f, 310f), Point(30f, 310f), Point(15f, 295f), wobble = 5f,
    )
    assertEquals(Kind.TRIANGLE, kindOf(stroke))
  }

  @Test
  fun triangleStartedMidEdge() {
    val stroke = wobbly(Point(85f, 150f), Point(170f, 0f), Point(340f, 300f), Point(0f, 300f), Point(80f, 158f), wobble = 5f)
    assertEquals(Kind.TRIANGLE, kindOf(stroke))
  }

  @Test
  fun closedShapesEndWhereTheyStart() {
    for (shape in listOfNotNull(
      ShapeRecognizer.recognize(wobblyEllipse(200f, 200f, 150f, 150f)),
      ShapeRecognizer.recognize(wobbly(Point(0f, 300f), Point(170f, 0f), Point(340f, 300f), Point(5f, 296f))),
    )) {
      assertTrue(shape.points.first() == shape.points.last())
    }
  }
}
