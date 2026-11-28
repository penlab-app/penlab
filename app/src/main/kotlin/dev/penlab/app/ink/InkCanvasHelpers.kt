package dev.penlab.app.ink

/**
 * Pure-Kotlin helpers for [InkCanvasView] so clear-page rules, zoom limits, and layer undo/redo
 * state transitions can be unit-tested on the JVM without an Android View.
 */
internal inline fun <S, I> canClearPage(
  isPaged: Boolean,
  pageTop: Float,
  pageBottom: Float,
  strokes: List<S>,
  items: List<I>,
  hiddenLayers: Set<Int>,
  strokeLayer: (S) -> Int,
  strokeCenterY: (S) -> Float,
  itemCenterY: (I) -> Float,
): Boolean {
  for (i in strokes.indices) {
    val s = strokes[i]
    if (strokeLayer(s) in hiddenLayers) continue
    if (!isPaged) return true
    val y = strokeCenterY(s)
    if (y >= pageTop && y < pageBottom) return true
  }
  for (i in items.indices) {
    if (!isPaged) return true
    val y = itemCenterY(items[i])
    if (y >= pageTop && y < pageBottom) return true
  }
  return false
}

internal const val BASE_MIN_ZOOM = 0.25f
internal const val BASE_MAX_ZOOM = 8f

/**
 * Minimum raw `pageToScreen` scale for a canvas whose 100% physical zoom is [trueSizeZoom].
 * Scales down below [baseMinZoom] on low-DPI screens so 25% true-size remains reachable.
 */
internal fun minZoomFor(trueSizeZoom: Float, baseMinZoom: Float = BASE_MIN_ZOOM): Float =
  baseMinZoom * minOf(1f, trueSizeZoom.coerceAtLeast(1e-3f))

/**
 * Maximum raw `pageToScreen` scale for a canvas whose 100% physical zoom is [trueSizeZoom].
 * Scales linearly with [trueSizeZoom] on high-DPI tablets (e.g. 340 dpi Tab S9) so deep zoom
 * headroom stays >= 800% of true paper size rather than capping early.
 */
internal fun maxZoomFor(trueSizeZoom: Float, baseMaxZoom: Float = BASE_MAX_ZOOM): Float =
  baseMaxZoom * maxOf(1f, trueSizeZoom)

internal fun addLayerSaved(current: SavedLayers): SavedLayers {
  val names = current.names + "Layer ${current.names.size + 1}"
  return SavedLayers(names = names, hidden = current.hidden, current = names.lastIndex)
}

internal fun renameLayerSaved(current: SavedLayers, index: Int, name: String): SavedLayers? {
  if (index !in current.names.indices || name.isBlank()) return null
  val names = current.names.toMutableList().also { it[index] = name.trim() }
  return current.copy(names = names)
}

internal fun deleteLayerSaved(current: SavedLayers, index: Int): SavedLayers? {
  if (current.names.size <= 1 || index !in current.names.indices) return null
  val names = current.names.toMutableList().also { it.removeAt(index) }
  val hidden = current.hidden.filter { it != index }.map { if (it > index) it - 1 else it }.distinct().sorted()
  val active =
    when {
      current.current > index -> current.current - 1
      current.current == index -> (index - 1).coerceAtLeast(0)
      else -> current.current
    }.coerceIn(0, names.lastIndex)
  return SavedLayers(names = names, hidden = hidden, current = active)
}

internal fun applySavedLayers(current: SavedLayers, state: SavedLayers): SavedLayers {
  val names = state.names.ifEmpty { listOf("Layer 1") }
  val legacy = state.current == HistoryFile.LEGACY_LAYER_CURRENT
  val hidden = (if (legacy) current.hidden else state.hidden).filter { it in names.indices }.distinct().sorted()
  val active = (if (legacy) current.current else state.current).coerceIn(0, names.lastIndex)
  return SavedLayers(names = names, hidden = hidden, current = active)
}
