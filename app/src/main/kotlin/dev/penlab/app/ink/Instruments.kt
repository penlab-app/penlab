package dev.penlab.app.ink

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A drawing instrument lying on the page: the ruler or the protractor. It lives in page units,
 * so its scale is the paper's (a millimetre on the ruler is a millimetre on the page) and it
 * moves and zooms with the page. Fingers move and turn it; the pen can draw along its straight
 * edges (see [snapEdge]).
 *
 * [unitsPerMm] converts millimetres to page units (10 in a notebook; screen pixels per mm at
 * 100% on an endless canvas).
 */
sealed class Instrument(val unitsPerMm: Float) {
  /** Centre on the page. */
  var cx = 0f
  var cy = 0f

  /** Turn, in degrees (0 = level). */
  var angle = 0f

  /** Converts a page point into the instrument's own frame: u along it, v across it. */
  protected fun toLocal(x: Float, y: Float, out: FloatArray) {
    val r = Math.toRadians(-angle.toDouble())
    val dx = x - cx
    val dy = y - cy
    out[0] = (dx * cos(r) - dy * sin(r)).toFloat()
    out[1] = (dx * sin(r) + dy * cos(r)).toFloat()
  }

  protected fun toPage(u: Float, v: Float, out: FloatArray) {
    val r = Math.toRadians(angle.toDouble())
    out[0] = (cx + u * cos(r) - v * sin(r)).toFloat()
    out[1] = (cy + u * sin(r) + v * cos(r)).toFloat()
  }

  /** True if the page point is on the instrument (for grabbing it with a finger). */
  abstract fun contains(x: Float, y: Float): Boolean

  /**
   * If the page point is within [reach] page units of one of the straight edges (and alongside
   * it), returns which edge (an id to pass to [project]); otherwise null.
   */
  abstract fun snapEdge(x: Float, y: Float, reach: Float): Int?

  /** Moves the point onto edge [edge], keeping how far along the edge it is. */
  abstract fun project(edge: Int, x: Float, y: Float, out: FloatArray)

  /**
   * Draws it; [canvas] is in page units, [zoom] is page-to-screen scale (for hairline widths),
   * [visible] is the part of the page on screen (an endless ruler draws only that much).
   */
  abstract fun draw(canvas: Canvas, zoom: Float, visible: RectF, style: InstrumentStyle)

  /** Makes it bigger or smaller (two fingers pinching it). */
  abstract fun resize(factor: Float)

  protected val local = FloatArray(2)
}

/** Colours and paints shared by the instruments, set from the page theme. */
class InstrumentStyle(darkPage: Boolean) {
  val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (darkPage) 0x30E4E5E7 else 0x26000000 }
  val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    color = if (darkPage) 0xCCE4E5E7.toInt() else 0xB3000000.toInt()
  }
  val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (darkPage) 0xE6E4E5E7.toInt() else 0xD9000000.toInt() }
  val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = if (darkPage) 0xFFE4E5E7.toInt() else 0xFF111111.toInt()
    textAlign = Paint.Align.CENTER
  }
}

/**
 * A clear straight-edge as long as you need: it runs right across the screen at any angle.
 * Millimetre ticks along both edges, centimetre numbers counting out from a zero mark in the
 * middle, and the angle. Pinch it to make it wider or narrower (the scale stays millimetres).
 */
class Ruler(unitsPerMm: Float) : Instrument(unitsPerMm) {
  var widthMm = 22f
    private set

  private val halfWidth get() = widthMm * unitsPerMm / 2

  override fun resize(factor: Float) {
    widthMm = (widthMm * factor).coerceIn(MIN_WIDTH_MM, MAX_WIDTH_MM)
  }

  override fun contains(x: Float, y: Float): Boolean {
    toLocal(x, y, local)
    return abs(local[1]) <= halfWidth
  }

  /** Edge 0 = top (v = -w/2), edge 1 = bottom (v = +w/2). Endless, so any point along it counts. */
  override fun snapEdge(x: Float, y: Float, reach: Float): Int? {
    toLocal(x, y, local)
    val toTop = abs(local[1] + halfWidth)
    val toBottom = abs(local[1] - halfWidth)
    return when {
      toTop <= reach && toTop <= toBottom -> 0
      toBottom <= reach -> 1
      else -> null
    }
  }

  override fun project(edge: Int, x: Float, y: Float, out: FloatArray) {
    toLocal(x, y, local)
    toPage(local[0], if (edge == 0) -halfWidth else halfWidth, out)
  }

  private val corner = FloatArray(2)

  override fun draw(canvas: Canvas, zoom: Float, visible: RectF, style: InstrumentStyle) {
    // How far along the ruler the screen reaches: the screen corners in the ruler's own frame.
    var from = Float.MAX_VALUE
    var to = -Float.MAX_VALUE
    for ((x, y) in listOf(visible.left to visible.top, visible.right to visible.top, visible.left to visible.bottom, visible.right to visible.bottom)) {
      toLocal(x, y, corner)
      from = minOf(from, corner[0])
      to = maxOf(to, corner[0])
    }
    val mm = unitsPerMm
    from -= 10 * mm
    to += 10 * mm

    canvas.save()
    canvas.translate(cx, cy)
    canvas.rotate(angle)
    val hw = halfWidth
    canvas.drawRect(from, -hw, to, hw, style.body)
    style.edge.strokeWidth = 1.2f / zoom
    canvas.drawLine(from, -hw, to, -hw, style.edge)
    canvas.drawLine(from, hw, to, hw, style.edge)

    // Ticks: 1 mm short, 5 mm medium, 10 mm long with the distance from zero in cm.
    style.tick.strokeWidth = 1f / zoom
    style.label.textSize = (3.2f * mm).coerceAtMost(widthMm * mm * 0.3f)
    val tickScale = (widthMm / 22f).coerceIn(0.6f, 1.6f)
    val firstMm = Math.floor((from / mm).toDouble()).toInt()
    val lastMm = Math.ceil((to / mm).toDouble()).toInt()
    val showMillimetres = mm * zoom >= 3f
    if (lastMm - firstMm < 20000) {
      for (i in firstMm..lastMm) {
        if (i % 5 != 0 && !showMillimetres) continue
        val u = i * mm
        val length = tickScale * mm * when {
          i % 10 == 0 -> 5f
          i % 5 == 0 -> 3.5f
          else -> 2f
        }
        canvas.drawLine(u, -hw, u, -hw + length, style.tick)
        canvas.drawLine(u, hw, u, hw - length, style.tick)
        if (i % 10 == 0) canvas.drawText("${abs(i) / 10}", u, -hw + length + style.label.textSize * 1.1f, style.label)
      }
    }
    // Zero mark: a small triangle on each edge where the counting starts.
    val t = 1.6f * mm
    canvas.drawPath(Path().apply { moveTo(-t, hw); lineTo(t, hw); lineTo(0f, hw - 1.6f * t); close() }, style.tick)

    // The angle, upright, just below the numbers so it is readable at any turn.
    canvas.rotate(-angle)
    style.label.textSize = (3.6f * mm).coerceAtMost(widthMm * mm * 0.32f)
    val shown = ((Math.round(angle) % 360) + 360) % 360
    canvas.drawText("$shown°", 0f, style.label.textSize / 3f, style.label)
    canvas.restore()
  }

  private companion object {
    const val MIN_WIDTH_MM = 10f
    const val MAX_WIDTH_MM = 80f
  }
}

/**
 * A half-circle protractor: degree marks round the arc (numbers every 10°, both directions),
 * a centre mark, and a straight base the pen can draw along.
 */
class Protractor(unitsPerMm: Float) : Instrument(unitsPerMm) {
  var radiusMm = 60f
    private set

  override fun resize(factor: Float) {
    radiusMm = (radiusMm * factor).coerceIn(25f, 160f)
  }
  private val radius get() = radiusMm * unitsPerMm
  private val path = Path()
  private val oval = RectF()

  /** The base is the diameter (v = 0); the arc bulges to negative v (upwards when level). */
  override fun contains(x: Float, y: Float): Boolean {
    toLocal(x, y, local)
    return local[1] <= 2f * unitsPerMm && local[0] * local[0] + local[1] * local[1] <= radius * radius
  }

  override fun snapEdge(x: Float, y: Float, reach: Float): Int? {
    toLocal(x, y, local)
    return if (abs(local[0]) <= radius + reach && abs(local[1]) <= reach) 0 else null
  }

  override fun project(edge: Int, x: Float, y: Float, out: FloatArray) {
    toLocal(x, y, local)
    toPage(local[0], 0f, out)
  }

  override fun draw(canvas: Canvas, zoom: Float, visible: RectF, style: InstrumentStyle) {
    canvas.save()
    canvas.translate(cx, cy)
    canvas.rotate(angle)
    val r = radius
    oval.set(-r, -r, r, r)
    path.rewind()
    path.arcTo(oval, 180f, 180f)
    path.close()
    canvas.drawPath(path, style.body)
    style.edge.strokeWidth = 1.2f / zoom
    canvas.drawPath(path, style.edge)

    val mm = unitsPerMm
    style.tick.strokeWidth = 1f / zoom
    style.label.textSize = 3.2f * mm
    for (deg in 0..180) {
      val tickLength = when {
        deg % 10 == 0 -> 7f * mm
        deg % 5 == 0 -> 4.5f * mm
        else -> 2.5f * mm
      }
      if (deg % 5 != 0 && r * zoom * Math.toRadians(1.0) < 3f) continue
      val a = Math.toRadians(180.0 + deg)
      val ca = cos(a).toFloat()
      val sa = sin(a).toFloat()
      canvas.drawLine(ca * r, sa * r, ca * (r - tickLength), sa * (r - tickLength), style.tick)
      if (deg % 10 == 0) {
        val labelRadius = r - 11f * mm
        canvas.drawText("$deg", ca * labelRadius, sa * labelRadius + style.label.textSize / 3f, style.label)
      }
    }
    // Centre mark: where angles are measured from.
    val c = 3f * mm
    canvas.drawLine(-c, 0f, c, 0f, style.edge)
    canvas.drawLine(0f, -c, 0f, c, style.edge)
    canvas.restore()
  }
}
