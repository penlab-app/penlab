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

  /**
   * The accent line round each page, from the theme's [primary] colour, drawn at
   * [EDGE_ACCENT_ALPHA]. On the light page it is the primary as it is. On the dark page a dark
   * primary (the light themes' copper or blue) keeps its hue but is lightened until the line, over
   * the page and over the desk, is at least [EDGE_MIN_CONTRAST] against both. (It was close to
   * invisible: dark copper at 55% on near-black.)
   */
  fun pageEdgeAccent(primary: Int, darkPage: Boolean): Int {
    if (!darkPage) return (EDGE_ACCENT_ALPHA * 255f).roundToInt() shl 24 or (primary and 0x00FFFFFF)
    val page = pageOnScreen(darkPage)
    val desk = deskOnScreen(darkPage)
    var colour = primary or 0xFF000000.toInt()
    val toward = if (darkPage) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    for (step in 0..20) {
      val shown = (EDGE_ACCENT_ALPHA * 255f).roundToInt() shl 24 or (colour and 0x00FFFFFF)
      if (contrast(over(shown, page), page) >= EDGE_MIN_CONTRAST && contrast(over(shown, desk), desk) >= EDGE_MIN_CONTRAST) break
      colour = mix(colour, toward, 0.05f)
    }
    return (EDGE_ACCENT_ALPHA * 255f).roundToInt() shl 24 or (colour and 0x00FFFFFF)
  }

  const val EDGE_ACCENT_ALPHA = 0.55f
  const val EDGE_MIN_CONTRAST = 3f

  private fun over(top: Int, bottom: Int): Int {
    val a = (top ushr 24) / 255f
    fun ch(shift: Int) = (((top shr shift and 0xFF) * a + (bottom shr shift and 0xFF) * (1f - a))).roundToInt()
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
  }

  private fun mix(from: Int, to: Int, t: Float): Int {
    fun ch(shift: Int) = (((from shr shift and 0xFF) * (1f - t) + (to shr shift and 0xFF) * t)).roundToInt()
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
  }

  /** WCAG contrast ratio between two opaque colours. */
  fun contrast(a: Int, b: Int): Float {
    val la = luminance(a)
    val lb = luminance(b)
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
  }

  private fun luminance(argb: Int): Float {
    fun lin(shift: Int): Float {
      val c = (argb shr shift and 0xFF) / 255f
      return if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }
    return 0.2126f * lin(16) + 0.7152f * lin(8) + 0.0722f * lin(0)
  }

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
