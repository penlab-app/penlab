package dev.penlab.plugin

import android.graphics.Bitmap
import android.graphics.RectF

/*
 * Plain data: what crosses the plugin boundary. No PenLab or Ink types, so the same shapes can later
 * be sent to a plugin in another app (v2). Every position is in MILLIMETRES of paper, in the
 * notebook's own space: x from the left edge, y from the top of the first page (pages are stacked
 * top to bottom with a gap between them); on an endless canvas, from the canvas origin.
 */

/** Which pen made a stroke. */
enum class Tool { PEN, HIGHLIGHTER }

/**
 * One finished stroke. [xs], [ys] and [pressures] have one entry per point (pressure 0..1, or -1
 * when the pen gave none); [times] is milliseconds since the stroke began. [argb] is the colour as
 * stored (paper colours, i.e. dark ink for a white page) and [widthMm] the pen width.
 */
class PluginStroke(
  val xs: FloatArray,
  val ys: FloatArray,
  val pressures: FloatArray,
  val times: LongArray,
  val tool: Tool,
  val argb: Int,
  val widthMm: Float,
) {
  val pointCount: Int get() = xs.size
}

/** Where a call happens: the notebook, the page the user is on, and that page's size. */
class PageRef(val notebookId: String, val pageIndex: Int, val widthMm: Float, val heightMm: Float)

/**
 * A page object that is not ink. [typeId] says what it is (a plugin's own id, or `penlab.text` /
 * `penlab.image` for the base's); [payload] is whatever bytes the plugin needs. PenLab stores
 * [typeId] and [payload] untouched and gives them back after any number of saves, even in a build
 * where the plugin is absent (it then shows as a grey box). [boundsMm] is the area it covers.
 */
class PluginItem(val typeId: String, val boundsMm: RectF, val payload: ByteArray) {
  init {
    require(typeId.isNotEmpty() && '\n' !in typeId) { "typeId must be a non-empty single line" }
  }
}

/**
 * What the user has selected with the lasso. [strokes] and [items] are the selected ones, [boundsMm]
 * their common area, and [bitmap] a picture of exactly them (dark ink on a transparent
 * background, at most 1200 px on the long side; null when the selection is empty). The host owns the bitmap:
 * copy it if it is needed after the call returns.
 */
class Selection(
  val strokes: List<PluginStroke>,
  val items: List<PluginItem>,
  val boundsMm: RectF,
  val bitmap: Bitmap?,
) {
  val isEmpty: Boolean get() = strokes.isEmpty() && items.isEmpty()

  companion object {
    /** Nothing selected. */
    fun empty() = Selection(emptyList(), emptyList(), RectF(), null)
  }
}

/** What a [PageAction] hands back. */
sealed interface Result {
  /** Items for PenLab to put on the page (one undo step). With an empty selection they are centred on the visible page; with a selection, [PluginItem.boundsMm] is used as given. */
  class Items(val items: List<PluginItem>) : Result

  /** Text to show the user (a result, an explanation). */
  class Text(val text: String) : Result

  /** A short message, shown briefly. */
  class Message(val message: String) : Result

  /** Nothing to show. */
  data object None : Result
}
