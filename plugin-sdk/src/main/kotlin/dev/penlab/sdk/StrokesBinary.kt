package dev.penlab.sdk

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Which pen made a stroke, by the names PenLab saves in its page files. [id] is that name. A decoder
 * that meets a [code] it does not know uses [PRESSURE] (as PenLab does for an unknown saved name).
 */
enum class StrokeFamily(val code: Int, val id: String) {
  /** Round tip whose width follows pressure: PenLab's default pen. */
  PRESSURE(0, "pressure"),

  /** Round tip of one width however hard the pen presses. */
  BALLPOINT(1, "ballpoint"),

  /** A flat nib held at an angle: thin and thick strokes by direction, wider with pressure. */
  FOUNTAIN(2, "fountain"),

  /** Wide and see-through, drawn under the ink. Give [SdkStroke.argb] an alpha (PenLab's own are 0x66); an opaque colour is drawn at that 40 %. */
  HIGHLIGHTER(3, "highlighter");

  companion object {
    fun fromCode(code: Int): StrokeFamily = entries.firstOrNull { it.code == code } ?: PRESSURE

    /** By [id] (`pressure`, `ballpoint`, `fountain`, `highlighter`); `pen` and anything unknown read as [PRESSURE]. */
    fun fromId(id: String): StrokeFamily = entries.firstOrNull { it.id == id } ?: PRESSURE
  }
}

/**
 * The strokes file: real ink that PenLab turns into strokes on the page (they erase, select, move and
 * are searched like handwriting). JSON is too big for thousands of strokes, so it is a compact binary file
 * (format version [VERSION]), sent as a result file next to the JSON (`strokesFile`, see docs/plugin-bridge.md).
 *
 * Layout, big-endian:
 * ```
 * 4 bytes   magic "PLNK"
 * u16       format version (1)
 * u32       section count    (a section is a page of an imported notebook, or the one batch of a page action)
 * per section:
 *   u32     stroke count
 *   per stroke:
 *     u8    family code      (StrokeFamily: 0 pressure, 1 ballpoint, 2 fountain, 3 highlighter)
 *     u8    flags            bit 0: pressures follow, bit 1: times follow
 *     i32   argb             colour as PenLab stores it (paper colours: dark ink for a white page)
 *     f32   width            pen width in millimetres of paper
 *     i32   layer            0-based layer, or -1 for "the notebook's current layer"
 *     u32   point count n
 *     n x (f32 x, f32 y)     millimetres from the top-left of the page (see the bridge doc)
 *     n x f32 pressure       only with flag 0; 0..1, or -1 for "no pressure"
 *     n x u32 time           only with flag 1; milliseconds since the stroke began, never decreasing
 * ```
 * A reader rejects a newer version outright; unknown family codes read as pressure.
 */
object StrokesBinary {
  const val VERSION = 1

  /** Hard limits a decoder enforces (a malformed file must not exhaust memory). */
  const val MAX_STROKES = 2_000_000
  const val MAX_POINTS_PER_STROKE = 2_000_000
  private const val MAX_SECTIONS = 100_000

  private const val MAGIC = 0x504C4E4B // "PLNK"
  private const val FLAG_PRESSURE = 1
  private const val FLAG_TIMES = 2

  /** One section holding [strokes]. */
  fun encode(strokes: List<SdkStroke>): ByteArray = encodeSections(listOf(strokes))

  /**
   * Encodes [sections] (one list of strokes per page for an importer; section i is page i). Strokes with
   * no usable point are left out, and so is any point whose x or y is not a finite number.
   */
  fun encodeSections(sections: List<List<SdkStroke>>): ByteArray {
    val prepared = sections.map { list -> list.mapNotNull(::prepare) }
    var size = 4L + 2 + 4
    for (section in prepared) {
      size += 4
      for (s in section) size += 1 + 1 + 4 + 4 + 4 + 4 + s.xs.size * 8L + (if (s.pressures != null) s.xs.size * 4L else 0) + (if (s.times != null) s.xs.size * 4L else 0)
    }
    require(size <= Int.MAX_VALUE) { "strokes file larger than 2 GB" }
    val out = ByteBuffer.allocate(size.toInt()).order(ByteOrder.BIG_ENDIAN)
    out.putInt(MAGIC).putShort(VERSION.toShort()).putInt(prepared.size)
    for (section in prepared) {
      out.putInt(section.size)
      for (s in section) {
        out.put(s.family.code.toByte())
        out.put(((if (s.pressures != null) FLAG_PRESSURE else 0) or (if (s.times != null) FLAG_TIMES else 0)).toByte())
        out.putInt(s.argb).putFloat(s.widthMm).putInt(s.layer).putInt(s.xs.size)
        for (i in s.xs.indices) out.putFloat(s.xs[i]).putFloat(s.ys[i])
        s.pressures?.forEach { out.putFloat(it) }
        s.times?.forEach { out.putInt(it) }
      }
    }
    return out.array()
  }

  /** The strokes of every section, in order. Throws [WireFormatException] for anything outside the format. */
  fun decodeSections(bytes: ByteArray): List<List<SdkStroke>> =
    try {
      read(ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN))
    } catch (e: BufferUnderflowException) {
      throw WireFormatException("the strokes file ends early", e)
    }

  /** All strokes of all sections as one list. */
  fun decode(bytes: ByteArray): List<SdkStroke> = decodeSections(bytes).flatten()

  /** A stroke with its arrays checked and sized: what [encodeSections] writes. */
  private class Prepared(val family: StrokeFamily, val argb: Int, val widthMm: Float, val layer: Int, val xs: FloatArray, val ys: FloatArray, val pressures: FloatArray?, val times: IntArray?)

  private fun prepare(s: SdkStroke): Prepared? {
    val n = s.xs.size
    require(s.ys.size == n) { "xs and ys differ in length" }
    require(s.pressures.isEmpty() || s.pressures.size == n) { "pressures must be empty or one per point" }
    require(s.times.isEmpty() || s.times.size == n) { "times must be empty or one per point" }
    val keep = (0 until n).filter { s.xs[it].isFinite() && s.ys[it].isFinite() }
    if (keep.isEmpty()) return null
    require(keep.size <= MAX_POINTS_PER_STROKE) { "a stroke has more than $MAX_POINTS_PER_STROKE points" }
    val hasPressure = s.pressures.isNotEmpty() && keep.any { s.pressures[it] >= 0f }
    var last = 0L
    val times = if (s.times.isEmpty()) null else IntArray(keep.size) { j -> last = maxOf(last, s.times[keep[j]]); last.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt() }
    return Prepared(
      s.family, s.argb, if (s.widthMm.isFinite()) s.widthMm else 0f, s.layer.coerceAtLeast(-1),
      FloatArray(keep.size) { s.xs[keep[it]] }, FloatArray(keep.size) { s.ys[keep[it]] },
      if (hasPressure) FloatArray(keep.size) { j -> s.pressures[keep[j]].let { p -> if (p.isFinite()) p else -1f } } else null,
      times,
    )
  }

  private fun read(b: ByteBuffer): List<List<SdkStroke>> {
    if (b.remaining() < 10 || b.getInt() != MAGIC) throw WireFormatException("not a PenLab strokes file")
    val version = b.getShort().toInt() and 0xFFFF
    if (version != VERSION) throw WireFormatException("strokes file version $version, this reader knows $VERSION")
    val sectionCount = b.getInt()
    if (sectionCount < 0 || sectionCount > MAX_SECTIONS || sectionCount.toLong() * 4 > b.remaining()) throw WireFormatException("bad section count $sectionCount")
    var total = 0L
    return List(sectionCount) {
      val count = b.getInt()
      total += count
      if (count < 0 || total > MAX_STROKES || count.toLong() * MIN_STROKE_BYTES > b.remaining()) throw WireFormatException("bad stroke count $count")
      List(count) { readStroke(b) }
    }
  }

  private const val MIN_STROKE_BYTES = 22

  private fun readStroke(b: ByteBuffer): SdkStroke {
    val family = StrokeFamily.fromCode(b.get().toInt() and 0xFF)
    val flags = b.get().toInt() and 0xFF
    val argb = b.getInt()
    val width = b.getFloat()
    val layer = b.getInt().coerceAtLeast(-1)
    val n = b.getInt()
    val perPoint = 8L + (if (flags and FLAG_PRESSURE != 0) 4 else 0) + (if (flags and FLAG_TIMES != 0) 4 else 0)
    if (n < 0 || n > MAX_POINTS_PER_STROKE || n * perPoint > b.remaining()) throw WireFormatException("bad point count $n")
    val xs = FloatArray(n)
    val ys = FloatArray(n)
    for (i in 0 until n) {
      xs[i] = b.getFloat()
      ys[i] = b.getFloat()
    }
    val pressures = if (flags and FLAG_PRESSURE != 0) FloatArray(n) { b.getFloat() } else FloatArray(n) { -1f }
    val times = if (flags and FLAG_TIMES != 0) LongArray(n) { b.getInt().toLong() and 0xFFFFFFFFL } else LongArray(0)
    return SdkStroke(xs, ys, pressures, times, if (family == StrokeFamily.HIGHLIGHTER) "HIGHLIGHTER" else "PEN", argb, width, family, layer)
  }
}
