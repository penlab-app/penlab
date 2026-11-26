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

  /** Where a picture goes on the page, in page units: centre, size, turn in degrees. */
  data class Frame(val cx: Float, val cy: Float, val width: Float, val height: Float, val rotation: Float)

  /**
   * The frame of a picture whose result box is ([left], [top], [right], [bottom]) millimetres (the unrotated box, turned by
   * [rotationDeg]), moved by ([dx], [dy]) page units. A box with no area gets [DEFAULT_WIDTH_MM] and the picture's [aspect]
   * (width / height), unless it [replaced] a page item: then it keeps that item's centre and turn and fits inside its box.
   */
  fun pictureFrame(left: Float, top: Float, right: Float, bottom: Float, rotationDeg: Float, unitsPerMm: Float, dx: Float, dy: Float, aspect: Float, replaced: PageItem?): Frame {
    val w = (right - left) * unitsPerMm
    val h = (bottom - top) * unitsPerMm
    if (w > 0f && h > 0f) return Frame((left + right) / 2f * unitsPerMm + dx, (top + bottom) / 2f * unitsPerMm + dy, w, h, rotationDeg)
    if (replaced != null && replaced.width > 0f && replaced.height > 0f) {
      val fitsWide = replaced.width / replaced.height > aspect
      val fh = if (fitsWide) replaced.height else replaced.width / aspect
      return Frame(replaced.cx, replaced.cy, fh * aspect, fh, replaced.rotation)
    }
    val width = DEFAULT_WIDTH_MM * unitsPerMm
    return Frame((left + right) / 2f * unitsPerMm + dx, (top + bottom) / 2f * unitsPerMm + dy, width, width / aspect, rotationDeg)
  }

  /**
   * The app's item for [staged], moved by ([dx], [dy]) page units; [unitsPerMm] converts the plugin's millimetres. [replaced]
   * is the page item the result item replaces (see [dev.penlab.plugin.PluginItem.replaces]); it only matters when the result
   * gives no box of its own (see [pictureFrame]).
   */
  fun toPageItem(staged: StagedItem, unitsPerMm: Float, dx: Float = 0f, dy: Float = 0f, replaced: PageItem? = null): PageItem {
    val item = staged.item
    val b = item.boundsMm
    val boundsWidth = (b.right - b.left) * unitsPerMm
    val boundsHeight = (b.bottom - b.top) * unitsPerMm
    val cx = (b.left + b.right) / 2f * unitsPerMm + dx
    return when {
      staged.imageFile != null -> {
        val f = pictureFrame(b.left, b.top, b.right, b.bottom, item.rotationDeg, unitsPerMm, dx, dy, staged.aspect, replaced)
        ImageItem(PageItem.newId(), staged.imageFile, f.cx, f.cy, f.width, f.height, f.rotation, isInk = item.typeId == TYPE_INK_IMAGE)
      }
      item.typeId == PluginConvert.TYPE_TEXT -> {
        val keepBox = boundsWidth <= 0f && replaced != null
        val width = if (keepBox) replaced!!.width else if (boundsWidth > 0f) boundsWidth else DEFAULT_WIDTH_MM * unitsPerMm
        val rotation = if (keepBox) replaced!!.rotation else item.rotationDeg
        val draft = TextItem(PageItem.newId(), item.payload.toString(Charsets.UTF_8), BLACK_PAPER, TEXT_SIZE_MM * unitsPerMm, if (keepBox) replaced!!.cx else cx, 0f, width, TEXT_SIZE_MM * unitsPerMm, rotation)
        val height = TextItems.measureHeight(draft)
        draft.copy(cy = if (keepBox) replaced!!.cy else b.top * unitsPerMm + dy + height / 2f, height = height)
      }
      else -> PluginConvert.toPageItem(item, unitsPerMm, dx, dy).let { if (item.rotationDeg != 0f) it.copy(rotation = item.rotationDeg) else it }
    }
  }

  /** The page's items after [place], and which selected item each replacement took over ([swapped]: old id to new id). */
  class Placement(val items: List<PageItem>, val swapped: Map<String, String>)

  /**
   * Puts [made] on the page whose items are [current]. Each entry is a new item and the id it replaces: if that item is
   * still in the list, the new one takes its exact place in the stacking order; otherwise (no id, an unknown id, or an item
   * already replaced by an earlier entry) the new item is added on top. Pure, so the single-step guarantee is testable.
   */
  fun place(current: List<PageItem>, made: List<Pair<PageItem, String?>>): Placement {
    val list = current.toMutableList()
    val swapped = LinkedHashMap<String, String>()
    for ((item, replaces) in made) {
      val at = if (replaces == null) -1 else list.indexOfFirst { it.id == replaces }
      if (at >= 0) {
        list[at] = item
        swapped[replaces!!] = item.id
      } else {
        list += item
      }
    }
    return Placement(list, swapped)
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
