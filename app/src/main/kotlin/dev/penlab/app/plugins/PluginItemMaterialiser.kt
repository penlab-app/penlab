package dev.penlab.app.plugins

import android.graphics.BitmapFactory
import android.util.Log
import dev.penlab.app.ink.ImageItem
import dev.penlab.app.ink.PageItem
import dev.penlab.app.ink.TextItem
import dev.penlab.app.ink.TextItems
import dev.penlab.plugin.PluginItem
import java.io.File
import java.util.UUID

/** A [PluginItem] after [PluginItemMaterialiser.stage]: for a picture, the file now in the notebook's `images/` and its width / height. */
class StagedItem(val item: PluginItem, val imageFile: String? = null, val aspect: Float = 0f)

/**
 * Turns the items plugins return (built-in or plugin apps) into the app's own page items, in one place
 * for both paths that need it (placing a result on the page, and importing a notebook).
 *
 * - `penlab.image` / `penlab.ink-image`: the bytes are written to the notebook's `images/` folder and
 *   the item becomes an [ImageItem] ([ImageItem.isInk] for the ink kind), so it draws and exports after
 *   the plugin is gone. An unreadable picture is left out.
 * - `penlab.text`: a [TextItem] with the item's UTF-8 payload as text.
 * - any other type id: a [dev.penlab.app.ink.PluginPageItem] (a grey box until a drawing hook exists).
 *
 * [stage] does the file work and may run on any thread; [toPageItem] only builds objects.
 */
object PluginItemMaterialiser {
  const val TYPE_INK_IMAGE = "penlab.ink-image"

  /** Width given to an item whose bounds have none (millimetres). */
  private const val DEFAULT_WIDTH_MM = 60f

  /** Text size of a text item (millimetres of font size). */
  private const val TEXT_SIZE_MM = 5f

  fun stage(items: List<PluginItem>, imagesDir: File?): List<StagedItem> =
    items.mapNotNull { item ->
      if (item.typeId != PluginConvert.TYPE_IMAGE && item.typeId != TYPE_INK_IMAGE) return@mapNotNull StagedItem(item)
      val aspect = pictureAspect(item.payload)
      if (imagesDir == null || aspect == null) {
        Log.w(TAG, "Dropped an unusable picture from a plugin")
        return@mapNotNull null
      }
      imagesDir.mkdirs()
      val name = "plugin-${UUID.randomUUID()}.png"
      File(imagesDir, name).writeBytes(item.payload)
      StagedItem(item, name, aspect)
    }

  /** The app's item for [staged], moved by ([dx], [dy]) page units; [unitsPerMm] converts the plugin's millimetres. */
  fun toPageItem(staged: StagedItem, unitsPerMm: Float, dx: Float = 0f, dy: Float = 0f): PageItem {
    val item = staged.item
    val b = item.boundsMm
    val boundsWidth = (b.right - b.left) * unitsPerMm
    val boundsHeight = (b.bottom - b.top) * unitsPerMm
    val cx = (b.left + b.right) / 2f * unitsPerMm + dx
    return when {
      staged.imageFile != null -> {
        var w = boundsWidth
        var h = boundsHeight
        if (w <= 0f || h <= 0f) {
          w = DEFAULT_WIDTH_MM * unitsPerMm
          h = w / staged.aspect
        }
        ImageItem(PageItem.newId(), staged.imageFile, cx, (b.top + b.bottom) / 2f * unitsPerMm + dy, w, h, isInk = item.typeId == TYPE_INK_IMAGE)
      }
      item.typeId == PluginConvert.TYPE_TEXT -> {
        val width = if (boundsWidth > 0f) boundsWidth else DEFAULT_WIDTH_MM * unitsPerMm
        val draft = TextItem(PageItem.newId(), item.payload.toString(Charsets.UTF_8), BLACK_PAPER, TEXT_SIZE_MM * unitsPerMm, cx, 0f, width, TEXT_SIZE_MM * unitsPerMm)
        val height = TextItems.measureHeight(draft)
        draft.copy(cy = b.top * unitsPerMm + dy + height / 2f, height = height)
      }
      else -> PluginConvert.toPageItem(item, unitsPerMm, dx, dy)
    }
  }

  /** width / height of the picture in [bytes], or null if they are not a decodable image. */
  private fun pictureAspect(bytes: ByteArray): Float? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth.toFloat() / o.outHeight else null
  }

  /** Paper colour of black ink (shown light on the dark page, like a text box the user types). */
  private const val BLACK_PAPER = 0xFF000000.toInt()
  private const val TAG = "PluginItems"
}
