package dev.penlab.app.ink

import android.graphics.RectF
import kotlin.math.floor

/**
 * Pages of paper stacked top to bottom, in page units. Page 0 starts at y = 0. Used by A4
 * notebooks; an infinite canvas has no layout.
 *
 * Units: [UNITS_PER_MM] page units per millimetre of paper, so an A4 page is 2100 x 2970 and
 * measurements on the page match the printed sheet.
 */
class PageLayout(val pageWidth: Float, val pageHeight: Float, val gap: Float, var count: Int) {

  /** Bookmarked pages (page numbers from 0). */
  var bookmarks: Set<Int> = emptySet()

  /**
   * For each page, the imported PDF page drawn under it, or -1 for plain paper. Empty when the
   * notebook has no PDF.
   */
  var backgrounds: List<Int> = emptyList()

  /** The PDF page under [page], or -1. */
  fun backgroundOf(page: Int): Int = backgrounds.getOrElse(page) { -1 }

  val pitch: Float
    get() = pageHeight + gap

  val totalHeight: Float
    get() = count * pageHeight + (count - 1).coerceAtLeast(0) * gap

  fun top(page: Int): Float = page * pitch

  fun rect(page: Int, out: RectF): RectF = out.apply { set(0f, top(page), pageWidth, top(page) + pageHeight) }

  /** The page under height [y] (clamped to the existing pages). */
  fun pageAt(y: Float): Int = floor(y / pitch).toInt().coerceIn(0, count - 1)

  companion object {
    const val UNITS_PER_MM = 10f

    fun a4(count: Int, bookmarks: Collection<Int> = emptyList()) = paged(210f, 297f, count, bookmarks, emptyList())

    /** Pages of [widthMm] x [heightMm] (A4, or the size of an imported PDF's pages). */
    fun paged(widthMm: Float, heightMm: Float, count: Int, bookmarks: Collection<Int>, backgrounds: List<Int>) =
      PageLayout(widthMm * UNITS_PER_MM, heightMm * UNITS_PER_MM, 12 * UNITS_PER_MM, count.coerceAtLeast(1)).also { layout ->
        layout.bookmarks = bookmarks.filter { it in 0 until layout.count }.toSet()
        layout.backgrounds = if (backgrounds.isEmpty()) emptyList() else List(layout.count) { backgrounds.getOrElse(it) { -1 } }
      }
  }
}
