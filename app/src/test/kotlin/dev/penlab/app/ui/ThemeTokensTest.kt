package dev.penlab.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import dev.penlab.app.ui.theme.AccentTheme
import dev.penlab.app.ui.theme.PageThemeMode
import dev.penlab.app.ui.theme.PenLabShapes
import dev.penlab.app.ui.theme.PenLabSpacing
import dev.penlab.app.ui.theme.ThemeMode
import dev.penlab.app.ui.theme.penLabColorScheme
import dev.penlab.app.ui.theme.penLabExtendedColors
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTokensTest {
  private val allCombos =
    listOf(
      AccentTheme.BLUEPRINT to false,
      AccentTheme.BLUEPRINT to true,
      AccentTheme.COPPER to false,
      AccentTheme.COPPER to true,
    )

  @Test
  fun allFourThemeCombinationsProduceDistinctColorSchemes() {
    val schemes = allCombos.map { (accent, isDark) -> penLabColorScheme(accent, isDark) }
    assertEquals("Expected 4 distinct ColorSchemes", 4, schemes.toSet().size)

    // Blueprint and Copper must have distinct primary & background tones in both Light and Dark.
    assertNotEquals(
      penLabColorScheme(AccentTheme.BLUEPRINT, isDark = false).primary,
      penLabColorScheme(AccentTheme.COPPER, isDark = false).primary,
    )
    assertNotEquals(
      penLabColorScheme(AccentTheme.BLUEPRINT, isDark = true).primary,
      penLabColorScheme(AccentTheme.COPPER, isDark = true).primary,
    )
    assertNotEquals(
      penLabColorScheme(AccentTheme.BLUEPRINT, isDark = false).background,
      penLabColorScheme(AccentTheme.COPPER, isDark = false).background,
    )
    assertNotEquals(
      penLabColorScheme(AccentTheme.BLUEPRINT, isDark = true).background,
      penLabColorScheme(AccentTheme.COPPER, isDark = true).background,
    )
  }

  @Test
  fun allFourThemesMeetWcagTextAndAccentContrast() {
    for ((accent, isDark) in allCombos) {
      val label = "${accent.name}/${if (isDark) "DARK" else "LIGHT"}"
      val s = penLabColorScheme(accent, isDark)

      assertContrastAtLeast("$label onSurface on surface", s.onSurface, s.surface, 4.5)
      assertContrastAtLeast("$label onSurface on background", s.onSurface, s.background, 4.5)
      assertContrastAtLeast("$label onSurface on surfaceContainer", s.onSurface, s.surfaceContainer, 4.5)
      assertContrastAtLeast("$label onSurface on surfaceContainerHigh", s.onSurface, s.surfaceContainerHigh, 4.5)
      assertContrastAtLeast("$label onSurface on surfaceContainerHighest", s.onSurface, s.surfaceContainerHighest, 4.5)

      assertContrastAtLeast("$label onSurfaceVariant on surface", s.onSurfaceVariant, s.surface, 4.5)
      assertContrastAtLeast("$label onSurfaceVariant on background", s.onSurfaceVariant, s.background, 4.5)
      assertContrastAtLeast("$label onSurfaceVariant on surfaceContainer", s.onSurfaceVariant, s.surfaceContainer, 4.5)
      assertContrastAtLeast("$label onSurfaceVariant on surfaceContainerHigh", s.onSurfaceVariant, s.surfaceContainerHigh, 4.5)

      assertContrastAtLeast("$label onPrimary on primary", s.onPrimary, s.primary, 5.0)
      assertContrastAtLeast("$label primary on surface", s.primary, s.surface, 5.0)
      assertContrastAtLeast("$label primary on surfaceContainer", s.primary, s.surfaceContainer, 5.0)

      assertContrastAtLeast("$label onPrimaryContainer on primaryContainer", s.onPrimaryContainer, s.primaryContainer, 4.5)
      assertContrastAtLeast("$label onError on error", s.onError, s.error, 4.5)
      assertContrastAtLeast("$label onErrorContainer on errorContainer", s.onErrorContainer, s.errorContainer, 4.5)
      assertContrastAtLeast("$label inverseOnSurface on inverseSurface", s.inverseOnSurface, s.inverseSurface, 4.5)
    }
  }

  @Test
  fun chosenSegmentStandsOutFromTheTrackInAllFourThemes() {
    for ((accent, isDark) in allCombos) {
      val s = penLabColorScheme(accent, isDark)
      val e = penLabExtendedColors(accent, isDark)
      // The track is the translucent chip colour over the surface the control sits on (dialog / panel).
      val track = e.chipIdle.compositeOver(s.surfaceContainerHigh)
      val label = "${accent.name}/${if (isDark) "DARK" else "LIGHT"}"
      // Segment tokens: selected = primary fill with onPrimary text (PenLabComponentTokens.segmentSelected*).
      assertContrastAtLeast("$label chosen segment on track", s.primary, track, 3.0)
      assertContrastAtLeast("$label chosen segment text", s.onPrimary, s.primary, 4.5)
      assertContrastAtLeast("$label idle segment text on track", s.onSurface, track, 4.5)
    }
  }

  @Test
  fun extendedColorsMatchLightAndDarkModes() {
    val palettes = allCombos.map { (accent, isDark) -> penLabExtendedColors(accent, isDark) }
    assertEquals(4, palettes.toSet().size)

    for ((accent, isDark) in allCombos) {
      val ext = penLabExtendedColors(accent, isDark)
      assertEquals(isDark, ext.isDark)
      assertTrue(ext.floatingChrome.alpha == 1f)
      assertTrue(ext.canvasBackdrop.alpha == 1f)
      assertTrue(ext.toolSelectedRing.alpha == 1f)
      assertTrue(ext.swatchEdge.alpha > 0f)
    }
  }

  @Test
  fun themeModeResolvesLightDarkAndSystem() {
    assertFalse(ThemeMode.LIGHT.resolveDark(systemIsDark = false))
    assertFalse(ThemeMode.LIGHT.resolveDark(systemIsDark = true))
    assertTrue(ThemeMode.DARK.resolveDark(systemIsDark = false))
    assertTrue(ThemeMode.DARK.resolveDark(systemIsDark = true))
    assertFalse(ThemeMode.SYSTEM.resolveDark(systemIsDark = false))
    assertTrue(ThemeMode.SYSTEM.resolveDark(systemIsDark = true))
  }

  @Test
  fun pageThemeModeDistinguishesLightAndDarkPaper() {
    assertFalse(PageThemeMode.LIGHT.isDark)
    assertTrue(PageThemeMode.DARK.isDark)
  }

  @Test
  fun spacingAndShapesHonorErgonomicFloor() {
    assertTrue("Minimum touch target must be >= 48dp", PenLabSpacing.touchTarget >= 48.dp)
    assertEquals(4.dp, PenLabSpacing.xs)
    assertEquals(8.dp, PenLabSpacing.sm)
    assertEquals(12.dp, PenLabSpacing.md)
    assertEquals(16.dp, PenLabSpacing.lg)
    assertEquals(24.dp, PenLabSpacing.xl)
    assertEquals(32.dp, PenLabSpacing.xxl)
    assertEquals(PenLabShapes.medium, PenLabShapes.material.medium)
    assertEquals(PenLabShapes.large, PenLabShapes.material.large)
  }

  private fun assertContrastAtLeast(name: String, foreground: Color, background: Color, minimum: Double) {
    val ratio = contrastRatio(foreground, background)
    assertTrue("$name contrast was %.2f:1 (expected >= %.2f:1)".format(ratio, minimum), ratio >= minimum)
  }

  private fun contrastRatio(a: Color, b: Color): Double {
    val l1 = relativeLuminance(a)
    val l2 = relativeLuminance(b)
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
  }

  private fun relativeLuminance(c: Color): Double {
    fun linearize(channel: Float): Double {
      val v = channel.toDouble()
      return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linearize(c.red) + 0.7152 * linearize(c.green) + 0.0722 * linearize(c.blue)
  }
}
