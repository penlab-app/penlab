package dev.penlab.app.plugins.external

import android.graphics.RectF
import dev.penlab.plugin.ImportedNotebook
import dev.penlab.plugin.ImportedPage
import dev.penlab.plugin.PluginItem
import dev.penlab.sdk.PenLabBridge
import dev.penlab.sdk.PluginResult as WireResult
import dev.penlab.sdk.ResultItem
import dev.penlab.sdk.WireFormatException
import java.io.File
import java.io.IOException
import java.util.UUID
import dev.penlab.plugin.Result as PluginResult

/**
 * Turns what a plugin app answered (the SDK's wire result) into PenLab's v1 types. Only the wire
 * result and a [ResultFiles] go in, so it is tested without a device.
 *
 * Pictures and text become [PluginItem]s with the base's own type ids (`penlab.image`,
 * `penlab.ink-image`, `penlab.text`) and the bytes as payload: the host turns those into real
 * ImageItems (saved in the notebook's images/) and TextItems, never grey plugin boxes, so they draw and
 * export after the plugin is uninstalled (see PluginItemMaterialiser). A plugin's own type ids pass through untouched.
 *
 * A malformed answer (missing file, a notebook where an action was asked) throws
 * [WireFormatException]: the plugin's fault, which the registry's crash isolation handles.
 */
object ResultMapper {
  /** Largest picture accepted from a plugin. */
  const val MAX_PICTURE_BYTES = 8L * 1024 * 1024

  /** A page action's or screen's answer. [pluginName] is for the message when the plugin reports an error. */
  fun toResult(wire: WireResult, files: ResultFiles, pluginName: String): PluginResult =
    when (wire) {
      is WireResult.Items -> PluginResult.Items(wire.items.map { item(it, files) })
      is WireResult.Text -> PluginResult.Text(wire.text)
      is WireResult.Message -> PluginResult.Message(wire.message)
      WireResult.None -> PluginResult.None
      is WireResult.Error -> PluginResult.Message("“$pluginName”: ${wire.message}")
      is WireResult.Notebook -> throw WireFormatException("a page action answered with a notebook")
    }

  /**
   * An importer's answer. Null means "cannot read this file" (an error answer, or nothing): a failed
   * import, not a broken plugin. The background PDF is copied into [workDir].
   */
  fun toNotebook(wire: WireResult, files: ResultFiles, workDir: File): ImportedNotebook? =
    when (wire) {
      is WireResult.Error, WireResult.None -> null
      is WireResult.Notebook -> {
        val n = wire.notebook
        val pdf =
          n.backgroundPdfName?.let { name ->
            val target = File(workDir, "${UUID.randomUUID()}.pdf")
            if (!files.copyTo(name, target)) throw WireFormatException("the notebook's PDF \"$name\" is missing")
            target
          }
        ImportedNotebook(n.title, n.pageWidthMm, n.pageHeightMm, n.pages.map { page -> ImportedPage(if (pdf != null) page.backgroundPdfPage else -1, page.items.map { item(it, files) }) }, pdf)
      }
      else -> throw WireFormatException("an importer answered with something other than a notebook")
    }

  private fun item(item: ResultItem, files: ResultFiles): PluginItem {
    val payload =
      when {
        item.file != null -> {
          val limit = if (item.typeId == PenLabBridge.TYPE_IMAGE || item.typeId == PenLabBridge.TYPE_INK_IMAGE) MAX_PICTURE_BYTES else MAX_PAYLOAD_BYTES
          try {
            files.readBytes(item.file!!, limit)
          } catch (e: IOException) {
            throw WireFormatException("item file \"${item.file}\" is too large")
          } ?: throw WireFormatException("item file \"${item.file}\" is missing")
        }
        item.text != null -> item.text!!.toByteArray(Charsets.UTF_8)
        else -> ByteArray(0)
      }
    if ((item.typeId == PenLabBridge.TYPE_IMAGE || item.typeId == PenLabBridge.TYPE_INK_IMAGE) && payload.isEmpty()) throw WireFormatException("a picture item has no picture")
    val b = item.boundsMm
    return try {
      PluginItem(item.typeId, RectF(b[0], b[1], b[2], b[3]), payload)
    } catch (e: IllegalArgumentException) {
      throw WireFormatException(e.message ?: "bad item")
    }
  }

  /** Largest payload of a plugin's own item type. */
  private const val MAX_PAYLOAD_BYTES = 1L * 1024 * 1024
}
