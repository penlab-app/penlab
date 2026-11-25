package dev.penlab.app.ui

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.penlab.app.ink.InkTool
import dev.penlab.app.ink.InkTools
import dev.penlab.app.ink.LineType
import dev.penlab.app.ink.PenStyle

/**
 * The toolbar's tools and the favourite sizes, kept between launches (SharedPreferences).
 *
 * Tools are stored as text, one per slot, `KIND:colour:size:style:eraseMode:lineType` (colour as
 * hex ARGB), e.g. `PEN:ff111111:2.2:PRESSURE:whole:SOLID`. Anything unreadable falls back to the
 * starting tools.
 *
 * All customized pen slots (up to [MAX_PEN_COUNT]) are cached in a separate pool so lowering
 * [penCount] and later increasing it restores every pen's colour, width, style, and line type.
 */
@Stable
class ToolSettings(context: Context) {
  private val prefs = context.getSharedPreferences("tools", Context.MODE_PRIVATE)

  /**
   * Loads [penCount] customizable pens followed by the canonical non-pen utility tools.
   * Existing 3-pen saves are preserved cleanly in slots 0..2, with slot 3 gaining the new default
   * forest green pen (`#1E7E3E`).
   */
  fun loadTools(penCount: Int = DEFAULT_PEN_COUNT): List<InkTool> {
    val safePenCount = penCount.coerceIn(MIN_PEN_COUNT, MAX_PEN_COUNT)
    val saved = prefs.getString(KEY_TOOLS, null)?.split(";")?.mapNotNull(::decode).orEmpty()
    val savedPool = prefs.getString(KEY_PEN_POOL, null)?.split(";")?.mapNotNull(::decode)
      ?.filter { it.kind == InkTool.Kind.PEN }.orEmpty()

    val savedPens = saved.filter { it.kind == InkTool.Kind.PEN }
    val penPool = buildPenPool(savedPens, savedPool)
    val utilitiesByKind = saved.filter { it.kind != InkTool.Kind.PEN }.groupBy { it.kind }
    val utilities = InkTools.defaultUtilities.map { default ->
      utilitiesByKind[default.kind]?.firstOrNull() ?: default
    }

    var activePens = penPool.take(safePenCount)
    // Legacy one-time reset (13 Nov): preserve for very old installs without touching slot 3+.
    if (!prefs.getBoolean(KEY_PENS_RESET, false)) {
      prefs.edit().putBoolean(KEY_PENS_RESET, true).putInt(KEY_SELECTED, 0).apply()
      activePens = activePens.mapIndexed { idx, pen ->
        if (idx < 3) pen.copy(paperColor = InkTools.defaultPenForSlot(idx).paperColor) else pen
      }
      val combined = activePens + utilities
      saveTools(combined)
      return combined
    }
    return activePens + utilities
  }

  /**
   * Dynamically adjusts the active [current] tool list to hold [penCount] pens while preserving
   * any previously saved pens from the pen pool and keeping all utility tools intact.
   */
  fun resizeTools(current: List<InkTool>, penCount: Int): List<InkTool> {
    val safePenCount = penCount.coerceIn(MIN_PEN_COUNT, MAX_PEN_COUNT)
    val currentPens = current.filter { it.kind == InkTool.Kind.PEN }
    val savedPool = prefs.getString(KEY_PEN_POOL, null)?.split(";")?.mapNotNull(::decode)
      ?.filter { it.kind == InkTool.Kind.PEN }.orEmpty()
    val mergedPool = buildPenPool(currentPens, savedPool)
    savePenPool(mergedPool)
    val utilities = current.filter { it.kind != InkTool.Kind.PEN }.ifEmpty { InkTools.defaultUtilities }
    val resized = mergedPool.take(safePenCount) + utilities
    saveTools(resized)
    return resized
  }

  fun saveTools(tools: List<InkTool>) {
    val activePens = tools.filter { it.kind == InkTool.Kind.PEN }
    val existingPool = prefs.getString(KEY_PEN_POOL, null)?.split(";")?.mapNotNull(::decode)
      ?.filter { it.kind == InkTool.Kind.PEN }.orEmpty()
    val mergedPool = buildPenPool(activePens, existingPool)
    prefs.edit()
      .putString(KEY_TOOLS, tools.joinToString(";", transform = ::encode))
      .putString(KEY_PEN_POOL, mergedPool.joinToString(";", transform = ::encode))
      .apply()
  }

  private fun savePenPool(pool: List<InkTool>) {
    prefs.edit().putString(KEY_PEN_POOL, pool.joinToString(";", transform = ::encode)).apply()
  }

  private fun buildPenPool(primary: List<InkTool>, fallback: List<InkTool>): List<InkTool> =
    List(MAX_PEN_COUNT) { slot ->
      primary.getOrNull(slot)
        ?: fallback.getOrNull(slot)
        ?: InkTools.defaultPenForSlot(slot)
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
    const val KEY_PEN_POOL = "pen_pool"
    const val KEY_SELECTED = "selected"
    const val KEY_FAVOURITES = "favourites_"
    const val KEY_PENS_RESET = "pens_reset_2026_11_13"
    const val KEY_RECENT = "recent_colors"
  }
}
