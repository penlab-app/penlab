package dev.penlab.app.ui

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Modular items that can appear on the customizable canvas toolbar.
 *
 * Default visible set matches Sketch 1: `[PENS, ERASER, SELECT, FILL, TOOLS, MORE, UNDO_REDO]`,
 * while optional slots (`HIGHLIGHTER`, `TEXT`, `LAYERS`, `CLEAR`) can be pinned directly onto
 * the bar from Settings.
 */
enum class ToolbarItem(val label: String) {
  PENS("Pens"),
  ERASER("Eraser"),
  SELECT("Select"),
  FILL("Fill"),
  TOOLS("Tools"),
  MORE("More"),
  UNDO_REDO("Undo & Redo"),
  HIGHLIGHTER("Highlighter"),
  TEXT("Text box"),
  LAYERS("Layers"),
  CLEAR("Clear page"),
}

const val MIN_PEN_COUNT = 1
const val MAX_PEN_COUNT = 6
const val DEFAULT_PEN_COUNT = 4

val DEFAULT_VISIBLE_TOOLBAR_ITEMS: List<ToolbarItem> =
  listOf(
    ToolbarItem.PENS,
    ToolbarItem.ERASER,
    ToolbarItem.SELECT,
    ToolbarItem.FILL,
    ToolbarItem.TOOLS,
    ToolbarItem.MORE,
    ToolbarItem.UNDO_REDO,
  )

val DEFAULT_TOOLBAR_ORDER: List<ToolbarItem> = ToolbarItem.entries.toList()

/**
 * Pure, immutable state for toolbar customization: number of customizable pen slots (`1..6`),
 * canonical ordering of all [ToolbarItem]s, and which items are currently visible on the bar.
 */
data class ToolbarConfigState(
  val penCount: Int = DEFAULT_PEN_COUNT,
  val order: List<ToolbarItem> = DEFAULT_TOOLBAR_ORDER,
  val visible: Set<ToolbarItem> = DEFAULT_VISIBLE_TOOLBAR_ITEMS.toSet(),
) {
  /** Visible items in the user's chosen order. */
  val visibleOrderedItems: List<ToolbarItem>
    get() = order.filter { it in visible }

  fun withPenCount(count: Int): ToolbarConfigState =
    copy(penCount = count.coerceIn(MIN_PEN_COUNT, MAX_PEN_COUNT))

  /**
   * Toggles [item]'s visibility. Prevents hiding the last remaining visible item so the toolbar
   * can never become empty.
   */
  fun toggleItem(item: ToolbarItem): ToolbarConfigState {
    val next =
      if (item in visible) {
        if (visible.size <= 1) return this
        visible - item
      } else {
        visible + item
      }
    return copy(visible = next)
  }

  /**
   * Moves [item] by [delta] positions in [order] (`-1` earlier/up, `+1` later/down).
   * Safely no-ops at list boundaries.
   */
  fun moveItem(item: ToolbarItem, delta: Int): ToolbarConfigState {
    val from = order.indexOf(item)
    if (from < 0 || delta == 0) return this
    val to = (from + delta).coerceIn(0, order.lastIndex)
    if (to == from) return this
    val updated = order.toMutableList()
    updated.removeAt(from)
    updated.add(to, item)
    return copy(order = updated)
  }

  fun reset(): ToolbarConfigState = default()

  fun serialize(): String =
    buildString {
      append(VERSION_PREFIX)
      append("|penCount=").append(penCount)
      append("|order=").append(order.joinToString(",") { it.name })
      append("|visible=").append(order.filter { it in visible }.joinToString(",") { it.name })
    }

  companion object {
    private const val VERSION_PREFIX = "v1"

    fun default(): ToolbarConfigState =
      ToolbarConfigState(
        penCount = DEFAULT_PEN_COUNT,
        order = DEFAULT_TOOLBAR_ORDER,
        visible = DEFAULT_VISIBLE_TOOLBAR_ITEMS.toSet(),
      )

    fun deserialize(raw: String?): ToolbarConfigState {
      if (raw.isNullOrBlank()) return default()
      val parts = raw.split("|")
      if (parts.firstOrNull() != VERSION_PREFIX) return default()

      val fields =
        parts.drop(1).mapNotNull { segment ->
          val eq = segment.indexOf('=')
          if (eq <= 0) null else segment.substring(0, eq) to segment.substring(eq + 1)
        }.toMap()

      val penCount =
        fields["penCount"]?.toIntOrNull()?.coerceIn(MIN_PEN_COUNT, MAX_PEN_COUNT)
          ?: DEFAULT_PEN_COUNT

      val parsedOrder =
        fields["order"]
          ?.split(",")
          ?.mapNotNull { name -> ToolbarItem.entries.firstOrNull { it.name == name } }
          ?.distinct()
          .orEmpty()

      // Append any newly introduced items that weren't in an older saved preference.
      val completeOrder = parsedOrder + DEFAULT_TOOLBAR_ORDER.filter { it !in parsedOrder }

      val parsedVisible =
        fields["visible"]
          ?.split(",")
          ?.mapNotNull { name -> ToolbarItem.entries.firstOrNull { it.name == name } }
          ?.toSet()
          .orEmpty()

      val safeVisible = parsedVisible.ifEmpty { DEFAULT_VISIBLE_TOOLBAR_ITEMS.toSet() }

      return ToolbarConfigState(
        penCount = penCount,
        order = completeOrder,
        visible = safeVisible,
      )
    }
  }
}

/**
 * SharedPreferences-backed reactive holder for [ToolbarConfigState].
 * Persisted in `"toolbar"` prefs alongside dock placement without key overlap.
 */
@Stable
class ToolbarConfig(context: Context) {
  private val prefs = context.getSharedPreferences("toolbar", Context.MODE_PRIVATE)

  var state by mutableStateOf(ToolbarConfigState.deserialize(prefs.getString(KEY_CONFIG, null)))
    private set

  val penCount: Int get() = state.penCount
  val order: List<ToolbarItem> get() = state.order
  val visible: Set<ToolbarItem> get() = state.visible
  val visibleOrderedItems: List<ToolbarItem> get() = state.visibleOrderedItems

  fun setPenCount(count: Int) = update(state.withPenCount(count))

  fun toggleItem(item: ToolbarItem) = update(state.toggleItem(item))

  fun moveItem(item: ToolbarItem, delta: Int) = update(state.moveItem(item, delta))

  fun reset() = update(state.reset())

  private fun update(next: ToolbarConfigState) {
    if (next == state) return
    state = next
    prefs.edit().putString(KEY_CONFIG, next.serialize()).apply()
  }

  private companion object {
    const val KEY_CONFIG = "config_v1"
  }
}

/** Clean zoom-step ladder for the inline `− 100% +` stepper in the right chrome island. */
val ZOOM_STEP_LADDER: List<Int> = listOf(25, 35, 50, 65, 75, 85, 100, 115, 130, 150, 175, 200, 250, 300, 400)

/** Next higher zoom percentage on the clean zoom ladder, clamped at `400%`. */
fun stepZoomIn(currentPercent: Int): Int =
  ZOOM_STEP_LADDER.firstOrNull { it > currentPercent } ?: ZOOM_STEP_LADDER.last()

/** Next lower zoom percentage on the clean zoom ladder, clamped at `25%`. */
fun stepZoomOut(currentPercent: Int): Int =
  ZOOM_STEP_LADDER.lastOrNull { it < currentPercent } ?: ZOOM_STEP_LADDER.first()
