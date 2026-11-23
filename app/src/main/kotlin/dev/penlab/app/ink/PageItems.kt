package dev.penlab.app.ink

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import java.io.File
import java.util.Base64
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Something on a page that is not ink: a photo or a typed text box. Items never change; a move
 * or edit makes a new item with the same [id] (like strokes), which keeps undo simple.
 *
 * Position is the centre ([cx], [cy]) in page units, [width] x [height] in page units, turned
 * by [rotation] degrees about the centre.
 */
sealed class PageItem {
  abstract val id: String
  abstract val cx: Float
  abstract val cy: Float
  abstract val width: Float
  abstract val height: Float
  abstract val rotation: Float

  /** The same item moved, scaled and turned by [matrix] (page units). */
  abstract fun mapped(matrix: Matrix): PageItem

  /** The area it covers on the page (its turned rectangle's bounding box). */
  fun bounds(out: RectF = RectF()): RectF {
    val r = Math.toRadians(rotation.toDouble())
    val c = abs(cos(r)).toFloat()
    val s = abs(sin(r)).toFloat()
    val halfW = (width * c + height * s) / 2
    val halfH = (width * s + height * c) / 2
    return out.apply { set(cx - halfW, cy - halfH, cx + halfW, cy + halfH) }
  }

  protected fun mapGeometry(matrix: Matrix): FloatArray {
    // Centre through the matrix; size by how much the matrix stretches each of the item's own
    // axes; angle by where the item's x-axis ends up.
    val r = Math.toRadians(rotation.toDouble())
    val pts = floatArrayOf(cx, cy, cx + cos(r).toFloat(), cy + sin(r).toFloat(), cx - sin(r).toFloat(), cy + cos(r).toFloat())
    matrix.mapPoints(pts)
    val ax = pts[2] - pts[0]
    val ay = pts[3] - pts[1]
    val bx = pts[4] - pts[0]
    val by = pts[5] - pts[1]
    val scaleX = sqrt(ax * ax + ay * ay)
    val scaleY = sqrt(bx * bx + by * by)
    val angle = Math.toDegrees(atan2(ay.toDouble(), ax.toDouble())).toFloat()
    return floatArrayOf(pts[0], pts[1], width * scaleX, height * scaleY, angle)
  }

  companion object {
    fun newId(): String = UUID.randomUUID().toString()
  }
}

/**
 * A photo; the picture is a file in the notebook's `images/` folder. With [isInk] it is ink, not
 * a photo (a typeset equation from handwriting → LaTeX): dark on transparent, shown light on the
 * dark page like handwriting.
 */
data class ImageItem(
  override val id: String,
  val fileName: String,
  override val cx: Float,
  override val cy: Float,
  override val width: Float,
  override val height: Float,
  override val rotation: Float = 0f,
  val isInk: Boolean = false,
) : PageItem() {
  override fun mapped(matrix: Matrix): ImageItem {
    val g = mapGeometry(matrix)
    return copy(cx = g[0], cy = g[1], width = g[2], height = g[3], rotation = g[4])
  }
}

/**
 * A typed text box: [text] in [paperColor] at [textSize] (page units), wrapped to [width].
 * [height] follows the text.
 */
data class TextItem(
  override val id: String,
  val text: String,
  val paperColor: Int,
  val textSize: Float,
  override val cx: Float,
  override val cy: Float,
  override val width: Float,
  override val height: Float,
  override val rotation: Float = 0f,
) : PageItem() {
  override fun mapped(matrix: Matrix): TextItem {
    val g = mapGeometry(matrix)
    // Text grows with the box's width change, so resizing makes the writing bigger or smaller.
    val grow = if (width > 0f) g[2] / width else 1f
    val resized = copy(cx = g[0], cy = g[1], width = g[2], textSize = textSize * grow, rotation = g[4])
    return resized.copy(height = TextItems.measureHeight(resized))
  }
}

/**
 * A page object PenLab itself does not understand: made by a plugin (stored kind [KIND_PLUGIN],
 * [content] = type id, a newline, the payload as Base64), or any kind a newer version wrote. It is
 * kept exactly as read ([kind], [content], [color], [textSize] are the saved fields untouched), so
 * a notebook opens in any build and saving it loses nothing. It is drawn as a grey box with the
 * plugin's id.
 */
data class PluginPageItem(
  override val id: String,
  val kind: Int,
  val content: String,
  val color: Int,
  val textSize: Float,
  override val cx: Float,
  override val cy: Float,
  override val width: Float,
  override val height: Float,
  override val rotation: Float = 0f,
) : PageItem() {
  /** The plugin's id for the item, or "kind N" for a kind from a newer version. */
  val typeId: String
    get() = if (kind == KIND_PLUGIN) content.substringBefore('\n') else "kind $kind"

  /** The plugin's bytes (empty for a kind from a newer version, which has none of its own). */
  val payload: ByteArray
    get() = if (kind == KIND_PLUGIN && '\n' in content) Base64.getDecoder().decode(content.substringAfter('\n')) else ByteArray(0)

  override fun mapped(matrix: Matrix): PluginPageItem {
    val g = mapGeometry(matrix)
    return copy(cx = g[0], cy = g[1], width = g[2], height = g[3], rotation = g[4])
  }

  companion object {
    /** The stored kind for plugin items (0, 1, 2 are photo, text, ink picture). */
    const val KIND_PLUGIN = 3

    /** A new plugin item; [typeId] must be a single non-empty line. */
    fun of(typeId: String, payload: ByteArray, cx: Float, cy: Float, width: Float, height: Float): PluginPageItem {
      require(typeId.isNotEmpty() && '\n' !in typeId) { "typeId must be a non-empty single line" }
      val content = typeId + "\n" + Base64.getEncoder().encodeToString(payload)
      return PluginPageItem(newId(), KIND_PLUGIN, content, 0, 0f, cx, cy, width, height)
    }
  }
}

/** Laying out and drawing text boxes. */
object TextItems {
  private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)

  fun layout(item: TextItem, color: Int): StaticLayout {
    paint.textSize = item.textSize
    paint.color = color
    return StaticLayout.Builder.obtain(item.text, 0, item.text.length, paint, item.width.toInt().coerceAtLeast(1))
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setIncludePad(false)
      .build()
  }

  fun measureHeight(item: TextItem): Float = layout(item, 0).height.toFloat().coerceAtLeast(item.textSize)
}

/**
 * Draws page items and keeps decoded photos in a memory-limited cache. [imagesDir] is the
 * notebook's `images/` folder.
 */
class ItemPainter(private val imagesDir: File?) {
  private val bitmaps =
    object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
      override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
  private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
  private val placeholder = Paint().apply { color = 0x33808080 }
  // Lightness inverted with the hue kept (RGB inverted, then hue turned 180°), like ink on the dark
  // page: black → white, red stays red, a pale blue fill turns deep blue (a plain RGB inversion
  // turned blue fills orange and red ink cyan).
  private val inkInvertPaint =
    Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
      colorFilter = android.graphics.ColorMatrixColorFilter(
        floatArrayOf(0.574f, -1.430f, -0.144f, 0f, 255f, -0.426f, -0.430f, -0.144f, 0f, 255f, -0.426f, -1.430f, 0.856f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)
      )
    }
  private val rect = RectF()
  private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A8A8A.toInt() }
  private val boxText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER }

  /** Draws [item] on [canvas] (page units). [textColor] turns a text box's paper colour into its screen colour. */
  fun draw(canvas: Canvas, item: PageItem, textColor: (Int) -> Int) {
    canvas.save()
    canvas.translate(item.cx, item.cy)
    canvas.rotate(item.rotation)
    rect.set(-item.width / 2, -item.height / 2, item.width / 2, item.height / 2)
    when (item) {
      is ImageItem -> {
        val bitmap = bitmap(item.fileName)
        // Ink pictures follow the ink: inverted when the page shows dark (black ink shows light).
        val showsDark = textColor(0xFF000000.toInt()) != 0xFF000000.toInt()
        val paint = if (item.isInk && showsDark) inkInvertPaint else bitmapPaint
        if (bitmap != null) canvas.drawBitmap(bitmap, null, rect, paint) else canvas.drawRect(rect, placeholder)
      }
      is TextItem -> {
        canvas.translate(rect.left, rect.top)
        TextItems.layout(item, textColor(item.paperColor)).draw(canvas)
      }
      is PluginPageItem -> {
        // Plugin drawing hooks are not called in this version: a grey box with the plugin's id.
        val corner = minOf(item.width, item.height) * 0.08f
        canvas.drawRoundRect(rect, corner, corner, boxPaint)
        canvas.clipRect(rect)
        boxText.textSize = (minOf(item.height * 0.2f, item.width / (item.typeId.length.coerceAtLeast(4) * 0.6f))).coerceAtLeast(1f)
        canvas.drawText(item.typeId, 0f, boxText.textSize * 0.35f, boxText)
      }
    }
    canvas.restore()
  }

  private fun bitmap(name: String): Bitmap? {
    bitmaps.get(name)?.let { return it }
    val file = imagesDir?.let { File(it, name) } ?: return null
    if (!file.exists()) return null
    val decoded = BitmapFactory.decodeFile(file.path) ?: return null
    bitmaps.put(name, decoded)
    return decoded
  }

  /** The picture's width / height, for placing a new photo, or null if it cannot be read. */
  fun aspect(name: String): Float? = bitmap(name)?.let { it.width.toFloat() / it.height }
}
