package dev.penlab.app.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Fill (user request: "add fill", both kinds): colouring the inside of an outline.
 *
 * The result is always a picture (colour on transparent) that the canvas keeps as an ink
 * [ImageItem] under the strokes. That way a fill moves with the lasso, undoes in one step, shows
 * lightness-inverted on the dark page like ink, and exports to PDF, with no change to the page
 * file format.
 */
object RegionFill {
  /** A filled picture and where it goes on the page (page units). */
  class Result(val bitmap: Bitmap, val bounds: RectF)

  /** Pixels per page unit are chosen so the work area is at most this many pixels on its long side. */
  private const val MAX_SIDE = 1800f

  /**
   * Tap-to-fill: the region around ([x], [y]) enclosed by [strokes], searched within [area]
   * (the visible part of the page). Null when the tap is on ink or the region leaks out of
   * [area], which means the outline isn't closed (or is bigger than the screen).
   */
  fun fillAt(strokes: List<InkStroke>, area: RectF, x: Float, y: Float, color: Int, wallExtra: Float = 0f): Result? {
    val scale = MAX_SIDE / maxOf(area.width(), area.height())
    val w = ceil(area.width() * scale).toInt().coerceAtLeast(2)
    val h = ceil(area.height() * scale).toInt().coerceAtLeast(2)
    // 1. The strokes as walls.
    val walls = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
    val canvas = Canvas(walls)
    canvas.scale(scale, scale)
    canvas.translate(-area.left, -area.top)
    val paint = Paint().apply {
      style = Paint.Style.STROKE
      strokeCap = Paint.Cap.ROUND
      strokeJoin = Paint.Join.ROUND
      isAntiAlias = false
    }
    val path = Path()
    for (stroke in strokes) {
      if (!RectF.intersects(stroke.bounds, area)) continue
      // At least ~1.5 px thick, so a thin pen line has no diagonal gaps to leak through.
      // [wallExtra] thickens every wall, sealing small gaps such as the breaks in a dashed line.
      paint.strokeWidth = maxOf(stroke.brush.size * 0.8f, 1.5f / scale) + wallExtra
      path.rewind()
      path.moveTo(stroke.x(0), stroke.y(0))
      if (stroke.pointCount == 1) path.lineTo(stroke.x(0) + 0.01f, stroke.y(0))
      for (i in 1 until stroke.pointCount) path.lineTo(stroke.x(i), stroke.y(i))
      canvas.drawPath(path, paint)
    }
    // ALPHA_8 rows may be padded: copy row by row into a tight w x h array.
    val stride = walls.rowBytes
    val wallBytes = java.nio.ByteBuffer.allocate(stride * h)
    walls.copyPixelsToBuffer(wallBytes)
    walls.recycle()
    val raw = wallBytes.array()
    val wall = if (stride == w) raw else ByteArray(w * h).also { for (row in 0 until h) System.arraycopy(raw, row * stride, it, row * w, w) }
    val sx = ((x - area.left) * scale).toInt()
    val sy = ((y - area.top) * scale).toInt()
    if (sx !in 0 until w || sy !in 0 until h || wall[sy * w + sx].toInt() != 0) return null

    // 2. Flood fill (scanline, 4-connected). Touching the edge of the area means it leaked.
    val filled = BooleanArray(w * h)
    val stack = IntArray(w * h / 4 + 16).let { ArrayDequeInt(it) }
    stack.push(sy * w + sx)
    var minX = sx; var maxX = sx; var minY = sy; var maxY = sy
    while (stack.isNotEmpty()) {
      val p = stack.pop()
      val py = p / w
      var lx = p % w
      if (filled[p]) continue
      while (lx > 0 && open(wall, filled, py * w + lx - 1)) lx--
      var rx = p % w
      while (rx < w - 1 && open(wall, filled, py * w + rx + 1)) rx++
      if (lx == 0 || rx == w - 1 || py == 0 || py == h - 1) return null
      for (px in lx..rx) {
        filled[py * w + px] = true
        if (open(wall, filled, (py - 1) * w + px) && (px == lx || !open(wall, filled, (py - 1) * w + px - 1))) stack.push((py - 1) * w + px)
        if (open(wall, filled, (py + 1) * w + px) && (px == lx || !open(wall, filled, (py + 1) * w + px - 1))) stack.push((py + 1) * w + px)
      }
      if (lx < minX) minX = lx
      if (rx > maxX) maxX = rx
      if (py < minY) minY = py
      if (py > maxY) maxY = py
    }

    // 3. Grow into the walls a little, so the colour runs under the ink with no pale seam.
    val grow = 3 + kotlin.math.ceil(wallExtra * scale / 2).toInt()
    repeat(grow) {
      val add = ArrayList<Int>()
      for (py in (minY - 1).coerceAtLeast(1)..(maxY + 1).coerceAtMost(h - 2)) for (px in (minX - 1).coerceAtLeast(1)..(maxX + 1).coerceAtMost(w - 2)) {
        val i = py * w + px
        if (filled[i] || wall[i].toInt() == 0) continue
        if (filled[i - 1] || filled[i + 1] || filled[i - w] || filled[i + w]) add += i
      }
      for (i in add) filled[i] = true
      minX = (minX - 1).coerceAtLeast(0); maxX = (maxX + 1).coerceAtMost(w - 1)
      minY = (minY - 1).coerceAtLeast(0); maxY = (maxY + 1).coerceAtMost(h - 1)
    }

    // 4. The picture, cropped to the region.
    val bw = maxX - minX + 1
    val bh = maxY - minY + 1
    val pixels = IntArray(bw * bh)
    val argb = color or 0xFF000000.toInt()
    for (py in 0 until bh) for (px in 0 until bw) if (filled[(py + minY) * w + px + minX]) pixels[py * bw + px] = argb
    val bitmap = Bitmap.createBitmap(pixels, bw, bh, Bitmap.Config.ARGB_8888)
    val bounds = RectF(area.left + minX / scale, area.top + minY / scale, area.left + (maxX + 1) / scale, area.top + (maxY + 1) / scale)
    return Result(bitmap, bounds)
  }

  private fun open(wall: ByteArray, filled: BooleanArray, i: Int) = wall[i].toInt() == 0 && !filled[i]

  /** True when [stroke] ends near where it started, i.e. it outlines something that can be filled. */
  fun isClosed(stroke: InkStroke): Boolean {
    val n = stroke.pointCount
    if (n < 3) return false
    val b = stroke.bounds
    val gap = hypot(stroke.x(n - 1) - stroke.x(0), stroke.y(n - 1) - stroke.y(0))
    return gap <= maxOf(hypot(b.width(), b.height()) * 0.15f, stroke.brush.size * 2f)
  }

  /** Fills the inside of each closed stroke in [strokes] (snapped shapes, drawn loops); null if none is closed. */
  fun fillShapes(strokes: List<InkStroke>, color: Int): Result? {
    val closed = strokes.filter(::isClosed)
    if (closed.isEmpty()) return null
    val area = RectF(closed[0].bounds)
    for (s in closed) area.union(s.bounds)
    val pad = closed.maxOf { it.brush.size }
    area.inset(-pad, -pad)
    val scale = MAX_SIDE / maxOf(area.width(), area.height())
    val bitmap = Bitmap.createBitmap(ceil(area.width() * scale).toInt().coerceAtLeast(1), ceil(area.height() * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(scale, scale)
    canvas.translate(-area.left, -area.top)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color or 0xFF000000.toInt(); style = Paint.Style.FILL }
    val path = Path()
    for (s in closed) {
      path.rewind()
      path.moveTo(s.x(0), s.y(0))
      for (i in 1 until s.pointCount) path.lineTo(s.x(i), s.y(i))
      path.close()
      canvas.drawPath(path, paint)
    }
    return Result(bitmap, area)
  }

  /** A growable int stack without boxing. */
  private class ArrayDequeInt(private var data: IntArray) {
    private var size = 0
    fun push(v: Int) {
      if (size == data.size) data = data.copyOf(size * 2)
      data[size++] = v
    }
    fun pop(): Int = data[--size]
    fun isNotEmpty() = size > 0
  }
}
