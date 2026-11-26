package dev.penlab.app.ink

/** Room left above a page's top edge for the buttons along the top of the screen (dp). */
const val TOP_CLEARANCE_DP = 64f

/**
 * The room above the first page, in pixels. Present mode shows nothing but the page, so there
 * are no buttons to leave room for and the page may start at the very top.
 */
fun topClearancePx(presenting: Boolean, density: Float): Float = if (presenting) 0f else TOP_CLEARANCE_DP * density

/**
 * Remembers where the page was panned and zoomed to (the nine values of the view matrix) while
 * something temporary, like Present mode, moves it, so leaving can put it back exactly.
 */
class ViewMemory {
  private var values: FloatArray? = null

  /** Keeps a copy of [view] (the caller's array may be reused). */
  fun keep(view: FloatArray) {
    values = view.copyOf()
  }

  /** The kept view, once: a second call (nothing kept since) returns null. */
  fun take(): FloatArray? = values.also { values = null }
}
