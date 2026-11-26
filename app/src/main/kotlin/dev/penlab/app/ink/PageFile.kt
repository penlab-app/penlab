package dev.penlab.app.ink

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream

/**
 * One page as it is kept on disk: plain numbers, no Android or Ink objects, so the file format
 * can be tested on the laptop. Converting to and from real strokes happens in [PageStore].
 */
data class SavedPage(
  val pageStyle: String,
  /** Where the page sat on screen: the 9 values of the page-to-screen matrix. */
  val view: FloatArray,
  val strokes: List<SavedStroke>,
  /** Photos and text boxes (version 2 and later; empty for older files). */
  val items: List<SavedItem> = emptyList(),
)

/**
 * A photo ([kind] 0, [content] = file name in `images/`) or a text box ([kind] 1, [content] =
 * the text, [color] its paper colour, [textSize] in page units), with its centre, size and turn.
 */
data class SavedItem(
  val kind: Int,
  val id: String,
  val cx: Float,
  val cy: Float,
  val width: Float,
  val height: Float,
  val rotation: Float,
  val content: String,
  val color: Int,
  val textSize: Float,
)

/**
 * One stroke: which brush drew it, and every point the pen reported. Points are packed as
 * [FLOATS_PER_POINT] floats each (x, y, pressure, tilt, orientation), times separately.
 */
class SavedStroke(
  val brushFamily: String,
  val colorArgb: Int,
  val size: Float,
  val epsilon: Float,
  val isHighlighter: Boolean,
  val toolType: Int,
  val strokeUnitLengthCm: Float,
  val points: FloatArray,
  val times: LongArray,
  /** Layer number (version 3 and later; 0 in older files). */
  val layer: Int = 0,
  /** Place in a recording (version 4 and later; see MediaMark), -1 for none. */
  val mediaMark: Long = -1L,
  /** Patterned-line group id (version 5 and later; 0 = on its own, also for older files). */
  val group: Long = 0L,
  /** Which [LineType] the group was cut with, as its position in [LineType.entries] (version 5 and later). */
  val lineType: Int = 0,
) {
  val pointCount: Int
    get() = times.size

  companion object {
    const val FLOATS_PER_POINT = 5
  }
}

/**
 * The page file format, version 5 (version 1 = the same without the items at the end; 3 adds each
 * stroke's layer, 4 its audio mark, 5 its patterned-line group id and line type, all after the
 * stroke's points). Every older version still reads.
 * Plain bytes since 20 Nov (gzip-compressed before; both read), laid out as:
 *
 * ```
 * int    magic "GRPH"
 * int    version (1)
 * utf    page style name
 * 9 x float  view matrix
 * int    stroke count, then for each stroke:
 *   utf    brush family id     int   colour (ARGB)
 *   float  size                float epsilon
 *   bool   highlighter         int   tool type
 *   float  stroke unit length (cm)
 *   int    point count, then for each point:
 *     float x, y, pressure, tilt, orientation    long time (ms)
 * int    item count (version 2), then for each item:
 *   int kind   utf id   float cx, cy, width, height, rotation   utf content   int colour   float text size
 * ```
 *
 * A future version 2 must still read version 1 files (they are users' notes).
 */
object PageFile {
  internal const val MAGIC = 0x47525048 // "GRPH"
  private const val VERSION = 5 // 4: strokes carry their audio mark; 5: and their line group + pattern

  /** One point on disk: x, y, pressure, tilt, orientation (4 bytes each) and its time (8). */
  private const val POINT_BYTES = SavedStroke.FLOATS_PER_POINT * 4 + 8

  fun write(page: SavedPage, output: OutputStream) {
    // Buffer in front of the compressor: DataOutputStream writes 4 bytes at a time, and feeding
    // the compressor that way made a 100-page notebook take 15 s to save (12 Nov).
    // Not compressed since 20 Nov: on the tablet, compressing cost more time than writing twice the
    // bytes (the major task: ~0.9 s per save, 0.26 s to open). Older gzip files still read.
    DataOutputStream(BufferedOutputStream(output, 1 shl 16)).use { out ->
      out.writeInt(MAGIC)
      out.writeInt(VERSION)
      out.writeUTF(page.pageStyle)
      require(page.view.size == 9) { "view must have 9 values" }
      page.view.forEach(out::writeFloat)
      out.writeInt(page.strokes.size)
      for (stroke in page.strokes) writeStroke(out, stroke)
      out.writeInt(page.items.size)
      for (item in page.items) writeItem(out, item)
    }
  }

  internal fun writeStroke(out: DataOutputStream, stroke: SavedStroke) {
    out.writeUTF(stroke.brushFamily)
    out.writeInt(stroke.colorArgb)
    out.writeFloat(stroke.size)
    out.writeFloat(stroke.epsilon)
    out.writeBoolean(stroke.isHighlighter)
    out.writeInt(stroke.toolType)
    out.writeFloat(stroke.strokeUnitLengthCm)
    out.writeInt(stroke.pointCount)
    // All points in one block: the same big-endian bytes DataOutputStream would write, without
    // millions of 4-byte calls (20 Nov: the major task took ~0.9 s to save).
    val block = java.nio.ByteBuffer.allocate(stroke.pointCount * POINT_BYTES)
    for (i in 0 until stroke.pointCount) {
      val base = i * SavedStroke.FLOATS_PER_POINT
      for (k in 0 until SavedStroke.FLOATS_PER_POINT) block.putFloat(stroke.points[base + k])
      block.putLong(stroke.times[i])
    }
    out.write(block.array())
    out.writeInt(stroke.layer)
    out.writeLong(stroke.mediaMark)
    out.writeLong(stroke.group)
    out.writeInt(stroke.lineType)
  }

  internal fun readStroke(data: DataInputStream, hasLayer: Boolean = true, hasAudio: Boolean = true, hasGroup: Boolean = true): SavedStroke {
    val family = data.readUTF()
    val color = data.readInt()
    val size = data.readFloat()
    val epsilon = data.readFloat()
    val highlighter = data.readBoolean()
    val toolType = data.readInt()
    val unitLength = data.readFloat()
    val count = data.readInt()
    if (count < 0 || count > 10_000_000) throw IOException("bad point count")
    val points = FloatArray(count * SavedStroke.FLOATS_PER_POINT)
    val times = LongArray(count)
    // One read for all points, then decoded from memory (same bytes as readFloat/readLong).
    val bytes = ByteArray(count * POINT_BYTES)
    data.readFully(bytes)
    val block = java.nio.ByteBuffer.wrap(bytes)
    for (i in 0 until count) {
      for (k in 0 until SavedStroke.FLOATS_PER_POINT) points[i * SavedStroke.FLOATS_PER_POINT + k] = block.getFloat()
      times[i] = block.getLong()
    }
    val layer = if (hasLayer) data.readInt() else 0
    val mediaMark = if (hasAudio) data.readLong() else -1L
    val group = if (hasGroup) data.readLong() else 0L
    val lineType = if (hasGroup) data.readInt() else 0
    return SavedStroke(family, color, size, epsilon, highlighter, toolType, unitLength, points, times, layer, mediaMark, group, lineType)
  }

  internal fun writeItem(out: DataOutputStream, item: SavedItem) {
    out.writeInt(item.kind)
    out.writeUTF(item.id)
    out.writeFloat(item.cx)
    out.writeFloat(item.cy)
    out.writeFloat(item.width)
    out.writeFloat(item.height)
    out.writeFloat(item.rotation)
    writeLongUtf(out, item.content)
    out.writeInt(item.color)
    out.writeFloat(item.textSize)
  }

  internal fun readItem(data: DataInputStream) =
    SavedItem(
      kind = data.readInt(),
      id = data.readUTF(),
      cx = data.readFloat(),
      cy = data.readFloat(),
      width = data.readFloat(),
      height = data.readFloat(),
      rotation = data.readFloat(),
      content = readLongUtf(data),
      color = data.readInt(),
      textSize = data.readFloat(),
    )

  /** Text can be longer than writeUTF allows (64 KB), so store its length and bytes. */
  internal fun writeLongUtf(out: DataOutputStream, text: String) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    out.writeInt(bytes.size)
    out.write(bytes)
  }

  internal fun readLongUtf(data: DataInputStream): String {
    val size = data.readInt()
    if (size < 0 || size > 10_000_000) throw IOException("bad text length")
    val bytes = ByteArray(size)
    data.readFully(bytes)
    return String(bytes, Charsets.UTF_8)
  }

  /** Reads a page, or throws [IOException] if the file is not a page file this version can read. */
  fun read(input: InputStream): SavedPage =
    DataInputStream(BufferedInputStream(unpacked(input), 1 shl 16)).use { data ->
      if (data.readInt() != MAGIC) throw IOException("not a PenLab page")
      val version = data.readInt()
      if (version !in 1..VERSION) throw IOException("page file version $version is newer than this app")
      val style = data.readUTF()
      val view = FloatArray(9) { data.readFloat() }
      val strokeCount = data.readInt()
      if (strokeCount < 0) throw IOException("bad stroke count")
      val strokes = List(strokeCount) { readStroke(data, hasLayer = version >= 3, hasAudio = version >= 4, hasGroup = version >= 5) }
      val items =
        if (version < 2) emptyList()
        else List(data.readInt().also { if (it < 0) throw IOException("bad item count") }) { readItem(data) }
      SavedPage(style, view, strokes, items)
    }
}

/** The file's bytes: gzip files (saved before 20 Nov) are unpacked, plain ones read as they are. */
internal fun unpacked(input: InputStream): InputStream {
  val buffered = BufferedInputStream(input, 1 shl 16)
  buffered.mark(2)
  val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
  buffered.reset()
  return if (gzip) GZIPInputStream(buffered, 1 shl 16) else buffered
}
