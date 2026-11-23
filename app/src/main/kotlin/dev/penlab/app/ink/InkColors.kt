package dev.penlab.app.ink

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Paper colours and screen colours.
 *
 * Every stroke is stored in its paper colour: what it looks like on a white page, and what an
 * exported PDF will show. With the dark page on, the screen shows the page inverted (dark
 * paper, light ink) the way other note apps do, but nothing stored changes.
 *
 * Colours are ARGB ints (0xAARRGGBB), the format Android and Jetpack Ink use.
 */
object InkColors {
  private const val PAPER_WHITE = 0xFFFFFFFF.toInt()

  /** The dark page is graphite, not pure black: softer on the eyes and on an OLED panel. */
  private const val DARK_PAGE = 0xFF18191B.toInt()

  /** The page background as shown on screen. */
  fun pageOnScreen(darkPage: Boolean): Int = if (darkPage) DARK_PAGE else PAPER_WHITE

  /** Around the pages of an A4 notebook: a desk a step darker (or greyer) than the paper. */
  fun deskOnScreen(darkPage: Boolean): Int = if (darkPage) 0xFF0C0D0E.toInt() else 0xFFD9DADC.toInt()

  /** The thin edge drawn round each page. */
  fun pageEdgeOnScreen(darkPage: Boolean): Int = if (darkPage) 0xFF2A2C2F.toInt() else 0xFFBFC2C6.toInt()

  /**
   * The colour to show on screen for a stroke or page stored as [paperColor].
   *
   * On the dark page, see-through colours (the highlighter) are also made fainter: a band that
   * glows on white paper would otherwise drown coloured ink on dark paper.
   */
  fun onScreen(paperColor: Int, darkPage: Boolean): Int {
    if (!darkPage) return paperColor
    val inverted = invertLightness(paperColor)
    val alpha = inverted ushr 24
    if (alpha == 0xFF) return inverted
    val fainter = (alpha * DARK_PAGE_TRANSLUCENT_FACTOR).toInt()
    return (fainter shl 24) or (inverted and 0x00FFFFFF)
  }

  private const val DARK_PAGE_TRANSLUCENT_FACTOR = 0.7f

  /**
   * Flips how light a colour is and keeps its hue, saturation and transparency: black becomes
   * white, dark blue becomes light blue, mid red stays about the same. Doing it twice gives
   * the original colour back.
   */
  fun invertLightness(argb: Int): Int {
    val r = (argb shr 16 and 0xFF) / 255f
    val g = (argb shr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f

    // RGB → hue, saturation, lightness.
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val lightness = (max + min) / 2f
    val spread = max - min
    val saturation = if (spread == 0f) 0f else spread / (1f - abs(2f * lightness - 1f))
    val hue =
      when {
        spread == 0f -> 0f
        max == r -> ((g - b) / spread).mod(6f)
        max == g -> (b - r) / spread + 2f
        else -> (r - g) / spread + 4f
      } // in sixths of a turn, 0..6

    // Same hue and saturation, opposite lightness → back to RGB.
    val newLightness = 1f - lightness
    val chroma = (1f - abs(2f * newLightness - 1f)) * saturation
    val second = chroma * (1f - abs(hue.mod(2f) - 1f))
    val base = newLightness - chroma / 2f
    val (r2, g2, b2) =
      when (hue.toInt()) {
        0 -> Triple(chroma, second, 0f)
        1 -> Triple(second, chroma, 0f)
        2 -> Triple(0f, chroma, second)
        3 -> Triple(0f, second, chroma)
        4 -> Triple(second, 0f, chroma)
        else -> Triple(chroma, 0f, second)
      }

    fun channel(value: Float) = ((value + base) * 255f).roundToInt().coerceIn(0, 255)
    return (argb and 0xFF000000.toInt()) or (channel(r2) shl 16) or (channel(g2) shl 8) or channel(b2)
  }
}
