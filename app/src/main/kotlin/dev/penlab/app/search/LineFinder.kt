package dev.penlab.app.search

import android.graphics.RectF
import dev.penlab.app.ink.InkStroke
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Groups strokes into lines of writing, so each line can be read on its own.
 *
 * The rules (sizes in millimetres; notebooks have 10 units per mm, endless canvases one per
 * screen pixel at 100%):
 *  - Strokes taller than 3 cm (drawings, brackets round a whole block) and long flat strokes
 *    (underlines, rules) are left out: they are not words and confuse the reader.
 *  - A stroke joins a line when its middle is within 3/4 of a letter height of the line's middle
 *    and it is not far to the side. Letter heights count as 3–15 mm, so dots and dashes still
 *    join and tall letters don't swallow the next line.
 *  - Lone dots and accents (an "i" dot sitting a bit high) are merged into the line below them.
 *  - A line is cut where there is a gap wider than 3 letter heights (two columns, or a label
 *    beside a diagram), so each part is found where it is.
 *
 * The maths works on plain boxes ([group]) so it can be unit-tested without Android.
 */
object LineFinder {

  /** A line found on a page: its strokes in writing order, and a signature that changes when they do. */
  class FoundLine(val strokes: List<InkStroke>, val page: Int, val rect: RectF, val signature: Long)

  /** A stroke's bounding box. */
  data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerY get() = (top + bottom) / 2f
  }

  // Sizes in millimetres (multiplied by the canvas's units per mm).
  private const val MIN_LETTER_MM = 3f
  private const val MAX_LETTER_MM = 15f
  private const val TOO_TALL_MM = 30f
  private const val LONG_FLAT_MM = 80f
  private const val SAME_LINE = 0.75f
  private const val SIDE_GAP = 6f // letter heights
  private const val CUT_GAP = 3f // letter heights

  fun find(strokes: List<InkStroke>, pageOf: (Float) -> Int, unitsPerMm: Float = 10f): List<FoundLine> {
    val byPage = HashMap<Int, ArrayList<Int>>()
    strokes.forEachIndexed { i, s -> byPage.getOrPut(pageOf(s.bounds.centerY())) { ArrayList() } += i }
    val found = ArrayList<FoundLine>()
    for ((page, indices) in byPage) {
      val boxes = indices.map { strokes[it].bounds.let { b -> Box(b.left, b.top, b.right, b.bottom) } }
      for (group in group(boxes, unitsPerMm)) {
        // Writing order = the order in the notebook (oldest first), which is how ML Kit expects it.
        val members = group.map { indices[it] }.sorted().map { strokes[it] }
        val rect = RectF(members[0].bounds)
        var signature = 0L
        for (m in members) {
          rect.union(m.bounds)
          signature += strokeHash(m)
        }
        found += FoundLine(members, page, rect, mix(signature + members.size))
      }
    }
    return found
  }

  /** Groups boxes into lines; returns each line as indices into [boxes]. */
  fun group(boxes: List<Box>, unitsPerMm: Float = 10f): List<List<Int>> {
    val minLetter = MIN_LETTER_MM * unitsPerMm
    val maxLetter = MAX_LETTER_MM * unitsPerMm
    val tooTall = TOO_TALL_MM * unitsPerMm
    val longFlat = LONG_FLAT_MM * unitsPerMm
    fun letter(box: Box) = box.height.coerceIn(minLetter, maxLetter)
    fun isWriting(box: Box) = box.height <= tooTall && !(box.width > longFlat && box.height < 0.15f * box.width)

    class Line(first: Int, box: Box) {
      val members = arrayListOf(first)
      var sumY = box.centerY
      var sumH = letter(box)
      var left = box.left
      var right = box.right
      val meanY get() = sumY / members.size
      val meanH get() = sumH / members.size

      fun add(i: Int, box: Box) {
        members += i
        sumY += box.centerY
        sumH += letter(box)
        left = min(left, box.left)
        right = max(right, box.right)
      }
    }

    val order = boxes.indices.filter { isWriting(boxes[it]) }.sortedBy { boxes[it].centerY }
    val active = ArrayList<Line>()
    val done = ArrayList<Line>()
    for (i in order) {
      val box = boxes[i]
      val h = letter(box)
      // Lines far above can't take this stroke any more (strokes come top to bottom).
      active.removeAll { line -> (box.centerY - line.meanY > 2 * maxLetter).also { if (it) done += line } }
      var best: Line? = null
      var bestDy = Float.MAX_VALUE
      for (line in active) {
        val size = max(line.meanH, h)
        val dy = abs(box.centerY - line.meanY)
        val side = max(0f, max(box.left - line.right, line.left - box.right))
        if (dy < SAME_LINE * size && side < SIDE_GAP * size && dy < bestDy) {
          best = line
          bestDy = dy
        }
      }
      if (best != null) best.add(i, box) else active += Line(i, box)
    }
    done += active

    // Lone dots and accents: merge into the nearest line just below that they sit over.
    val lines = done.toMutableList()
    val tiny = lines.filter { line -> line.members.size <= 2 && line.members.all { boxes[it].height < minLetter && boxes[it].width < minLetter } }
    for (dot in tiny) {
      val box = boxes[dot.members[0]]
      val target = lines.filter { it !== dot && it !in tiny }
        .filter { it.meanY > dot.meanY && it.meanY - dot.meanY < 1.5f * max(it.meanH, minLetter) }
        .filter { box.right >= it.left && box.left <= it.right }
        .minByOrNull { it.meanY - dot.meanY }
      if (target != null) {
        for (m in dot.members) target.add(m, boxes[m])
        lines.remove(dot)
      }
    }

    // Cut lines at wide gaps.
    val result = ArrayList<List<Int>>()
    for (line in lines) {
      val sorted = line.members.sortedBy { boxes[it].left }
      val gap = CUT_GAP * line.meanH
      var part = arrayListOf(sorted[0])
      var reach = boxes[sorted[0]].right
      for (k in 1 until sorted.size) {
        val b = boxes[sorted[k]]
        if (b.left - reach > gap) {
          result += part
          part = arrayListOf()
        }
        part += sorted[k]
        reach = max(reach, b.right)
      }
      result += part
    }
    return result
  }

  /** Stable across sessions (made from the points, not from object identity). */
  private fun strokeHash(s: InkStroke): Long {
    val last = s.pointCount - 1
    var h = s.pointCount.toLong()
    h = mix(h + s.x(0).toRawBits())
    h = mix(h + s.y(0).toRawBits())
    h = mix(h + s.x(last).toRawBits())
    h = mix(h + s.y(last).toRawBits())
    return h
  }

  private fun mix(value: Long): Long {
    var z = value + -0x61c8864680b583ebL
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    return z xor (z ushr 31)
  }
}
