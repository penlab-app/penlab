package dev.penlab.app.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class InkColorsTest {

  private fun hex(color: Int) = "%08X".format(color)

  @Test
  fun blackAndWhiteSwap() {
    assertEquals("FFFFFFFF", hex(InkColors.invertLightness(0xFF000000.toInt())))
    assertEquals("FF000000", hex(InkColors.invertLightness(0xFFFFFFFF.toInt())))
  }

  @Test
  fun transparencyIsKept() {
    assertEquals("66", hex(InkColors.invertLightness(0x66FFD600)).take(2))
  }

  @Test
  fun darkBlueBecomesLightBlueNotAnotherHue() {
    val light = InkColors.invertLightness(0xFF1565C0.toInt())
    val r = light shr 16 and 0xFF
    val g = light shr 8 and 0xFF
    val b = light and 0xFF
    assertTrue("still blue: ${hex(light)}", b > g && g > r)
    assertTrue("lighter than before: ${hex(light)}", r + g + b > 0x15 + 0x65 + 0xC0)
  }

  @Test
  fun invertingTwiceGivesTheColourBack() {
    val colors = listOf(0xFF111111, 0xFF1565C0, 0xFFD32F2F, 0x66FFD600, 0xFF2E7D32, 0xFF808080)
    for (color in colors.map(Long::toInt)) {
      val back = InkColors.invertLightness(InkColors.invertLightness(color))
      for (shift in listOf(24, 16, 8, 0)) {
        val before = color shr shift and 0xFF
        val after = back shr shift and 0xFF
        assertTrue("${hex(color)} came back as ${hex(back)}", abs(before - after) <= 2)
      }
    }
  }

  @Test
  fun lightPageShowsPaperColoursUnchanged() {
    assertEquals(0xFF1565C0.toInt(), InkColors.onScreen(0xFF1565C0.toInt(), darkPage = false))
  }

  // ---- Page edge accent (QA 26 Nov: dark copper on the dark page was invisible) ----

  private fun shownOver(accent: Int, under: Int): Int {
    val a = (accent ushr 24) / 255f
    fun ch(shift: Int) = ((accent shr shift and 0xFF) * a + (under shr shift and 0xFF) * (1f - a)).roundToInt()
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
  }

  @Test
  fun edgeAccentIsClearlyVisibleOnTheDarkPageForEveryThemePrimary() {
    val primaries = listOf(0xFFAD4D1B.toInt(), 0xFFE59158.toInt(), 0xFF1D4ED8.toInt()) // copper light/dark, a dark blue
    for (primary in primaries) {
      val accent = InkColors.pageEdgeAccent(primary, darkPage = true)
      for (under in listOf(InkColors.pageOnScreen(true), InkColors.deskOnScreen(true))) {
        val ratio = InkColors.contrast(shownOver(accent, under), under)
        assertTrue("primary ${hex(primary)} vs ${hex(under)}: %.2f".format(ratio), ratio >= InkColors.EDGE_MIN_CONTRAST)
      }
    }
  }

  @Test
  fun lightPageKeepsThePrimaryAtTheFixedAlpha() {
    val accent = InkColors.pageEdgeAccent(0xFFAD4D1B.toInt(), darkPage = false)
    assertEquals(0xAD4D1B, accent and 0xFFFFFF)
    assertEquals((InkColors.EDGE_ACCENT_ALPHA * 255f).roundToInt(), accent ushr 24)
  }

  @Test
  fun darkPageLightensADarkPrimaryButStaysWarm() {
    val accent = InkColors.pageEdgeAccent(0xFFAD4D1B.toInt(), darkPage = true)
    val red = accent shr 16 and 0xFF
    val blue = accent and 0xFF
    assertTrue("lightened", red > 0xAD)
    assertTrue("still warm (red above blue)", red > blue)
  }
}
