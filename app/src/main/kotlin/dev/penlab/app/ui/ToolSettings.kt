package dev.penlab.app.ui

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.penlab.app.ink.InkTool
import dev.penlab.app.ink.InkTools
import dev.penlab.app.ink.PenStyle
import dev.penlab.app.ink.LineType

/**
 * The toolbar's tools and the favourite sizes, kept between launches (SharedPreferences).
 *
 * Tools are stored as text, one per slot, `KIND:colour:size:style` (colour as hex ARGB), e.g.
 * `PEN:ff111111:2.2:PRESSURE`. Anything unreadable falls back to the starting tools.
 */
@Stable
class ToolSettings(context: Context) {
  private val prefs = context.getSharedPreferences("tools", Context.MODE_PRIVATE)

  /** The saved toolbar, or the starting one. Always the same kinds in the same order. */
  fun loadTools(): List<InkTool> {
    val saved = prefs.getString(KEY_TOOLS, null)?.split(";")?.mapNotNull(::decode).orEmpty()
    // Keep each saved tool in the slot of its kind; new kinds (added in an update) get defaults.
    val byKind = saved.groupBy { it.kind }.mapValues { it.value.toMutableList() }
    val tools = InkTools.defaults.map { default -> byKind[default.kind]?.removeFirstOrNull() ?: default }
    // Once (13 Nov): the pens go back to white, blue and red, keeping their sizes and types;
    // the tool order changed too, so start on the first pen.
    if (!prefs.getBoolean(KEY_PENS_RESET, false)) {
      prefs.edit().putBoolean(KEY_PENS_RESET, true).putInt(KEY_SELECTED, 0).apply()
      var pen = 0
      return tools.map { tool ->
        if (tool.kind == InkTool.Kind.PEN && pen < 3) tool.copy(paperColor = InkTools.defaults[pen++].paperColor) else tool
      }.also(::saveTools)
    }
    return tools
  }

  fun saveTools(tools: List<InkTool>) {
    prefs.edit().putString(KEY_TOOLS, tools.joinToString(";", transform = ::encode)).apply()
  }

  var selectedIndex: Int
    get() = prefs.getInt(KEY_SELECTED, 0)
    set(value) = prefs.edit().putInt(KEY_SELECTED, value).apply()

  /** Favourite sizes per kind of tool, in dp, smallest first. */
  var favourites by mutableStateOf(InkTool.Kind.entries.associateWith(::readFavourites))
    private set

  fun addFavourite(kind: InkTool.Kind, sizeDp: Float) = writeFavourites(kind, (favourites.getValue(kind) + round1(sizeDp)).distinct().sorted())

  fun removeFavourite(kind: InkTool.Kind, sizeDp: Float) = writeFavourites(kind, favourites.getValue(kind) - sizeDp)

  /** Colours picked from the wheel lately, newest first (shown in the wheel for quick reuse). */
  var recentColors by mutableStateOf(
    prefs.getString(KEY_RECENT, null)?.split(",")?.mapNotNull { it.toLongOrNull(16)?.toInt() }.orEmpty()
  )
    private set

  fun addRecentColor(color: Int) {
    val opaque = color or 0xFF000000.toInt()
    if (InkTools.penColors.any { it.paperColor == opaque }) return // already on the panel
    recentColors = (listOf(opaque) + recentColors.filter { it != opaque }).take(8)
    prefs.edit().putString(KEY_RECENT, recentColors.joinToString(",") { Integer.toHexString(it) }).apply()
  }

  private fun readFavourites(kind: InkTool.Kind): List<Float> =
    prefs.getString(KEY_FAVOURITES + kind.name, null)?.split(",")?.mapNotNull { it.toFloatOrNull() }
      ?: InkTools.sizesFor(kind)

  private fun writeFavourites(kind: InkTool.Kind, list: List<Float>) {
    favourites = favourites + (kind to list)
    prefs.edit().putString(KEY_FAVOURITES + kind.name, list.joinToString(",")).apply()
  }

  private fun encode(tool: InkTool) =
    "${tool.kind.name}:${Integer.toHexString(tool.paperColor)}:${tool.sizeDp}:${tool.style.name}:${if (tool.partialErase) "part" else "whole"}:${tool.line.name}"

  private fun decode(text: String): InkTool? {
    val parts = text.split(":")
    if (parts.size !in 4..6) return null // fewer parts: saved before the partial eraser / line types existed
    val kind = InkTool.Kind.entries.firstOrNull { it.name == parts[0] } ?: return null
    val color = parts[1].toLongOrNull(16)?.toInt() ?: return null
    val size = parts[2].toFloatOrNull() ?: return null
    val style = PenStyle.entries.firstOrNull { it.name == parts[3] } ?: return null
    val line = LineType.entries.firstOrNull { it.name == parts.getOrNull(5) } ?: LineType.SOLID
    return InkTool(kind, color, size, style, partialErase = parts.getOrNull(4) == "part", line = line)
  }

  private fun round1(value: Float) = Math.round(value * 10f) / 10f

  private companion object {
    const val KEY_TOOLS = "tools"
    const val KEY_SELECTED = "selected"
    const val KEY_FAVOURITES = "favourites_"
    const val KEY_PENS_RESET = "pens_reset_2026_11_13"
    const val KEY_RECENT = "recent_colors"
  }
}
