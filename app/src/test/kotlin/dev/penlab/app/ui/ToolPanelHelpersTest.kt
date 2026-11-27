package dev.penlab.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.penlab.app.ink.InkTool
import dev.penlab.app.ink.InkTools
import dev.penlab.app.ink.NamedColor
import dev.penlab.app.ink.PenStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPanelHelpersTest {
  @Test
  fun penStyleLabelsMatchBallpointPressureFountain() {
    assertEquals(listOf("Ballpoint", "Pressure", "Fountain"), PenStyle.entries.map { it.label })
  }

  @Test
  fun formatToolSizeMmHandlesPenEraserHighlighterAndText() {
    val mmPerDp = 0.16f
    // Pen: 2 decimal places
    assertEquals("0.35 mm", formatToolSizeMm(InkTool.Kind.PEN, 2.2f, mmPerDp))
    // Eraser: displays diameter (radius * 2)
    assertEquals("4.5 mm", formatToolSizeMm(InkTool.Kind.ERASER, 14f, mmPerDp))
    // Highlighter: 1 decimal place
    assertEquals("2.2 mm", formatToolSizeMm(InkTool.Kind.HIGHLIGHTER, 14f, mmPerDp))
    // Text: sizeDp is already stored in millimetres
    assertEquals("5.0 mm", formatToolSizeMm(InkTool.Kind.TEXT, 5f, mmPerDp))
  }

  @Test
  fun formatFavouriteChipMmUsesCompactPrecision() {
    val mmPerDp = 0.16f
    assertEquals("0.22 mm", formatFavouriteChipMm(InkTool.Kind.PEN, 1.4f, mmPerDp))
    assertEquals("1.1 mm", formatFavouriteChipMm(InkTool.Kind.PEN, 7.0f, mmPerDp))
    assertEquals("2.6 mm", formatFavouriteChipMm(InkTool.Kind.ERASER, 8f, mmPerDp))
    assertEquals("3.5 mm", formatFavouriteChipMm(InkTool.Kind.TEXT, 3.5f, mmPerDp))
  }

  @Test
  fun swatchCheckTintAlwaysPicksHigherContrastSide() {
    val light = Color(0xFFFFFFFF)
    val dark = Color(0xFF14171C)
    // Dark ink swatches get light check
    assertEquals(light, swatchCheckTint(Color(0xFF111111), light, dark))
    assertEquals(light, swatchCheckTint(Color(0xFF1565C0), light, dark))
    // Bright / pastel swatches get dark check
    assertEquals(dark, swatchCheckTint(Color(0xFFFFD600), light, dark))
    assertEquals(dark, swatchCheckTint(Color(0xFFCFE3FF), light, dark))
    assertEquals(dark, swatchCheckTint(Color(0xFFFFFFFF), light, dark))
  }

  @Test
  fun stepPaperSpacingMmClampsAndSnapsToHalfMm() {
    assertEquals(8.5f, stepPaperSpacingMm(8.0f, 0.5f), 0.001f)
    assertEquals(7.5f, stepPaperSpacingMm(8.0f, -0.5f), 0.001f)
    assertEquals(2.0f, stepPaperSpacingMm(2.0f, -0.5f), 0.001f)
    assertEquals(20.0f, stepPaperSpacingMm(20.0f, 0.5f), 0.001f)
    // Non-half-mm default (e.g. 6.35 mm) snaps cleanly to 0.5 mm increments
    assertEquals(7.0f, stepPaperSpacingMm(6.35f, 0.5f), 0.001f)
  }

  @Test
  fun effectivePaperPreviewDarkKeepsPdfPagesWhite() {
    assertFalse(effectivePaperPreviewDark(darkPage = false, isPdfNotebook = false))
    assertTrue(effectivePaperPreviewDark(darkPage = true, isPdfNotebook = false))
    assertFalse(effectivePaperPreviewDark(darkPage = true, isPdfNotebook = true))
    assertFalse(effectivePaperPreviewDark(darkPage = false, isPdfNotebook = true))
  }

  @Test
  fun formatHexRgbProducesUppercaseSixDigitHex() {
    assertEquals("#1565C0", formatHexRgb(0xFF1565C0.toInt()))
    assertEquals("#000000", formatHexRgb(0x66000000))
    assertEquals("#FFFFFF", formatHexRgb(0xFFFFFFFF.toInt()))
  }

  @Test
  fun computeColorWheelDiameterCapsAtMaxAndFitsTightBounds() {
    // Large tablet viewport caps at 440.dp so HSL/RGB stay cleanly clear of chrome
    assertEquals(440.dp, computeColorWheelDiameter(availableWidth = 1157.dp, availableHeight = 529.dp))
    // Tighter height shrinks diameter proportionally
    assertEquals(360.dp, computeColorWheelDiameter(availableWidth = 900.dp, availableHeight = 360.dp))
    // Tighter width shrinks diameter proportionally
    assertEquals(310.dp, computeColorWheelDiameter(availableWidth = 310.dp, availableHeight = 720.dp))
    // Degenerate negative bounds never produce a negative diameter
    assertEquals(0.dp, computeColorWheelDiameter(availableWidth = (-10).dp, availableHeight = 400.dp))
  }

  @Test
  fun aCustomColourNeverAddsARowToAFullPalette() {
    val pens = InkTools.penColors
    assertEquals(0, pens.size % SWATCH_COLUMNS)
    val shown = swatchesWithCustom(pens, 0xFF123456.toInt())
    assertEquals(pens.size, shown.size) // same number of rows: More colours / Pick stay put
    assertEquals(0xFF123456.toInt(), shown.last().paperColor)
    assertEquals(pens.dropLast(1), shown.dropLast(1))
  }

  @Test
  fun aPaletteColourOrAPartRowIsLeftAlone() {
    val pens = InkTools.penColors
    assertEquals(pens, swatchesWithCustom(pens, pens[2].paperColor))
    val part = listOf(NamedColor("A", 1), NamedColor("B", 2))
    assertEquals(3, swatchesWithCustom(part, 3).size)
    assertTrue(swatchesWithCustom(emptyList(), 5).isEmpty())
  }

  @Test
  fun markerWheelCenteredAndInsideViewportMargins() {
    val margin = 48f
    val top = 64f
    val bottom = 192f
    val layout = computeMarkerWheelLayout(
      widthPx = 2560f,
      heightPx = 1600f,
      topReservePx = top,
      bottomReservePx = bottom,
      marginPx = margin,
      baseGreyStartPx = 140f,
      baseSpokeStartPx = 336f,
    )
    assertEquals(1280f, layout.centerX, 0.5f)
    assertTrue(layout.centerX - layout.radius >= margin - 0.5f)
    assertTrue(layout.centerX + layout.radius <= 2560f - margin + 0.5f)
    assertTrue(layout.centerY - layout.radius >= top + margin - 0.5f)
    assertTrue(layout.centerY + layout.radius <= 1600f - bottom - margin + 0.5f)
    assertTrue(layout.greyStartPx >= 1f && layout.greyStartPx < layout.spokeStartPx)
    assertTrue(layout.spokeStartPx < layout.radius)
  }

  @Test
  fun visibleRecentColorsShrinksWhenEmptyAndCapsAtLimit() {
    assertTrue(visibleRecentColors(emptyList()).isEmpty())
    assertEquals(listOf(1, 2, 3), visibleRecentColors(listOf(1, 2, 3)))
    assertEquals(8, visibleRecentColors((1..12).toList()).size)
  }

  @Test
  fun hslSquareFractionGuardsZeroHalfSpan() {
    assertEquals(0f, hslSquareFraction(100f, 100f, 0f), 0f)
    assertEquals(0f, hslSquareFraction(100f, 100f, -5f), 0f)
    assertFalse(hslSquareFraction(100f, 100f, 0f).isNaN())
    assertEquals(0.5f, hslSquareFraction(100f, 100f, 40f), 0.001f)
  }

  @Test
  fun canSubmitTextBoxRejectsBlankAndAcceptsContent() {
    assertFalse(canSubmitTextBox(""))
    assertFalse(canSubmitTextBox("   \n\t  "))
    assertTrue(canSubmitTextBox("Note"))
    assertTrue(canSubmitTextBox("  x  "))
  }
}
