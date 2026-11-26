package dev.penlab.app.ui

import dev.penlab.app.ink.InkColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoversTest {
  @Test
  fun cacheKeySeparatesLightAndDarkEntries() {
    val light = Covers.cacheKey("nb-1", dark = false)
    val dark = Covers.cacheKey("nb-1", dark = true)
    assertEquals("nb-1", light)
    assertEquals("nb-1#dark", dark)
    assertNotEquals(light, dark)
  }

  @Test
  fun darkPlainNotebookPicksDarkCoverWithoutInversionWhenPresent() {
    val calls = mutableListOf<Boolean>()
    val resolved =
      resolveCoverVariant(darkPage = true, isPdf = false) { dark ->
        calls += dark
        if (dark) "dark-bitmap" else "light-bitmap"
      }
    assertEquals(CoverVariant("dark-bitmap", invertFallback = false), resolved)
    assertEquals(listOf(true), calls)
  }

  @Test
  fun darkPlainNotebookFallsBackToLightCoverWithInversionWhenDarkMissing() {
    val calls = mutableListOf<Boolean>()
    val resolved =
      resolveCoverVariant(darkPage = true, isPdf = false) { dark ->
        calls += dark
        if (dark) null else "light-bitmap"
      }
    assertEquals(CoverVariant("light-bitmap", invertFallback = true), resolved)
    assertEquals(listOf(true, false), calls)
  }

  @Test
  fun darkPlainNotebookReturnsNullWhenBothVariantsMissing() {
    val resolved = resolveCoverVariant<String>(darkPage = true, isPdf = false) { null }
    assertNull(resolved)
  }

  @Test
  fun pdfAndLightModeNeverPickDarkOrInvert() {
    val pdfCalls = mutableListOf<Boolean>()
    val pdfResolved =
      resolveCoverVariant(darkPage = true, isPdf = true) { dark ->
        pdfCalls += dark
        if (dark) "dark-bitmap" else "light-bitmap"
      }
    assertEquals(CoverVariant("light-bitmap", invertFallback = false), pdfResolved)
    assertEquals(listOf(false), pdfCalls)

    val lightCalls = mutableListOf<Boolean>()
    val lightResolved =
      resolveCoverVariant(darkPage = false, isPdf = false) { dark ->
        lightCalls += dark
        if (dark) "dark-bitmap" else "light-bitmap"
      }
    assertEquals(CoverVariant("light-bitmap", invertFallback = false), lightResolved)
    assertEquals(listOf(false), lightCalls)
  }

  @Test
  fun inkColorsOnScreenPreservesHuesAndSoftensHighlightsInDarkMode() {
    // Deep blue ink stays blue-dominant and gets brighter on dark paper.
    val darkBlue = 0xFF1565C0.toInt()
    val shownBlue = InkColors.onScreen(darkBlue, darkPage = true)
    assertEquals(0xFF, shownBlue ushr 24)
    val r = shownBlue shr 16 and 0xFF
    val g = shownBlue shr 8 and 0xFF
    val b = shownBlue and 0xFF
    assertTrue("blue stays dominant: %08X".format(shownBlue), b > g && g > r)
    assertTrue("blue brightens on dark paper", r + g + b > 0x15 + 0x65 + 0xC0)

    // Translucent yellow highlighter keeps yellow hue (R, G > B) and dims alpha.
    val yellowHighlight = 0x66FFD600
    val shownHighlight = InkColors.onScreen(yellowHighlight, darkPage = true)
    val hr = shownHighlight shr 16 and 0xFF
    val hg = shownHighlight shr 8 and 0xFF
    val hb = shownHighlight and 0xFF
    assertTrue("yellow stays warm: %08X".format(shownHighlight), hr > hb && hg > hb)
    val alphaBefore = yellowHighlight ushr 24
    val alphaAfter = shownHighlight ushr 24
    assertTrue("highlighter alpha softened ($alphaAfter < $alphaBefore)", alphaAfter < alphaBefore)
    assertFalse(alphaAfter == 0)
  }
}
