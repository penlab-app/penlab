package dev.penlab.app.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
}
