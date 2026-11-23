package dev.penlab.app.plugins

import android.graphics.RectF
import dev.penlab.app.ink.ImageItem
import dev.penlab.app.ink.InkStroke
import dev.penlab.app.ink.PageItem
import dev.penlab.app.ink.PluginPageItem
import dev.penlab.app.ink.TextItem
import dev.penlab.plugin.PluginItem
import dev.penlab.plugin.PluginStroke
import dev.penlab.plugin.Tool

/**
 * Converts between the app's page objects and the plugin API's plain data. The app works in page
 * units, the API in millimetres: [unitsPerMm] says how many page units make a millimetre (10 on
 * A4 notebooks, the screen's dots per millimetre on an endless canvas).
 */
object PluginConvert {
  const val TYPE_TEXT = "penlab.text"
  const val TYPE_IMAGE = "penlab.image"

  fun stroke(s: InkStroke, unitsPerMm: Float): PluginStroke {
    val n = s.pointCount
    val xs = FloatArray(n) { s.x(it) / unitsPerMm }
    val ys = FloatArray(n) { s.y(it) / unitsPerMm }
    val pressures = FloatArray(n) { s.points[it * InkStroke.FLOATS_PER_POINT + 2].let { p -> if (p < 0f) -1f else p } }
    return PluginStroke(xs, ys, pressures, s.times.copyOf(), if (s.isHighlighter) Tool.HIGHLIGHTER else Tool.PEN, s.brush.colorIntArgb, s.brush.size / unitsPerMm)
  }

  /**
   * The plugin's view of one of the app's items: plugin items as they are, text as its UTF-8
   * text, a photo or ink picture with no payload (a plugin sees it in the selection's bitmap).
   */
  fun item(item: PageItem, unitsPerMm: Float): PluginItem {
    val b = item.bounds()
    val bounds = RectF(b.left / unitsPerMm, b.top / unitsPerMm, b.right / unitsPerMm, b.bottom / unitsPerMm)
    return when (item) {
      is PluginPageItem -> PluginItem(item.typeId, bounds, item.payload)
      is TextItem -> PluginItem(TYPE_TEXT, bounds, item.text.toByteArray(Charsets.UTF_8))
      is ImageItem -> PluginItem(TYPE_IMAGE, bounds, ByteArray(0))
    }
  }

  /** A plugin's item as a page item, moved by ([dx], [dy]) page units. */
  fun toPageItem(item: PluginItem, unitsPerMm: Float, dx: Float = 0f, dy: Float = 0f): PluginPageItem {
    val b = item.boundsMm
    val width = (b.right - b.left) * unitsPerMm
    val height = (b.bottom - b.top) * unitsPerMm
    return PluginPageItem.of(item.typeId, item.payload, (b.left + b.right) / 2f * unitsPerMm + dx, (b.top + b.bottom) / 2f * unitsPerMm + dy, width, height)
  }
}
