package dev.penlab.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.penlab.app.R

/**
 * Titles (notebook names, headings, dialog titles) use Bricolage Grotesque, a variable font with
 * architectural character; body text, labels and controls use the system sans-serif face so dense
 * UI controls stay calm and legible. Licence: licenses/BricolageGrotesque-OFL.txt.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun bricolage(weight: Int, opticalSize: Float) =
  Font(
    R.font.bricolage,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opticalSize)),
  )

val TitleFont =
  FontFamily(
    bricolage(500, 14f),
    bricolage(600, 24f),
    bricolage(700, 36f),
  )

private val base = Typography()

val PenLabTypography =
  Typography(
    displaySmall =
      base.displaySmall.copy(
        fontFamily = TitleFont,
        fontWeight = FontWeight(700),
        letterSpacing = (-0.03).em,
      ),
    headlineLarge =
      base.headlineLarge.copy(
        fontFamily = TitleFont,
        fontWeight = FontWeight(700),
        letterSpacing = (-0.025).em,
      ),
    headlineMedium =
      base.headlineMedium.copy(
        fontFamily = TitleFont,
        fontWeight = FontWeight(700),
        letterSpacing = (-0.025).em,
      ),
    headlineSmall =
      base.headlineSmall.copy(
        fontFamily = TitleFont,
        fontWeight = FontWeight(600),
        letterSpacing = (-0.02).em,
      ),
    titleLarge =
      base.titleLarge.copy(
        fontFamily = TitleFont,
        fontWeight = FontWeight(600),
        letterSpacing = (-0.02).em,
      ),
    titleMedium =
      base.titleMedium.copy(
        fontFamily = TitleFont,
        fontWeight = FontWeight(600),
        letterSpacing = (-0.02).em,
      ),
    titleSmall =
      base.titleSmall.copy(
        fontFamily = TitleFont,
        fontWeight = FontWeight(500),
        letterSpacing = (-0.02).em,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
      ),
    bodySmall =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
      ),
  )

/** Alias preserved for existing references. */
val Typography: Typography = PenLabTypography

/**
 * Tabular-numeral styles for zoom %, angle °, page counters, stroke mm, and search counts
 * so digits never jitter horizontally as values change.
 */
object PenLabTextStyles {
  val tabularLabel: TextStyle = PenLabTypography.labelLarge.copy(fontFeatureSettings = "tnum")
  val tabularMedium: TextStyle = PenLabTypography.labelMedium.copy(fontFeatureSettings = "tnum")
  val tabularSmall: TextStyle = PenLabTypography.labelSmall.copy(fontFeatureSettings = "tnum")
  val tabularBody: TextStyle = PenLabTypography.bodyMedium.copy(fontFeatureSettings = "tnum")

  /**
   * Tiny uppercase-style badge text ("PDF" on the file pill). 11 sp is the floor for any text in
   * PenLab, so a compact badge gets weight and tracking instead of a smaller size.
   */
  val badge: TextStyle =
    PenLabTypography.labelSmall.copy(
      fontSize = 11.sp,
      lineHeight = 12.sp,
      fontWeight = FontWeight.SemiBold,
      letterSpacing = 0.4.sp,
    )

  /** Caption under a line-type preview in the pen panel (Solid, Dashed, ...). 11 sp, medium. */
  val lineTypeLabel: TextStyle = PenLabTypography.labelSmall.copy(letterSpacing = 0.sp)

  /**
   * Live "Aa" preview in the pen-size panel: the font grows with the pen size, 11 sp up to 19 sp.
   * Lives here so no composable has to write a literal `.sp`.
   */
  fun sizePreview(penSizeDp: Float): TextStyle =
    PenLabTypography.labelLarge.copy(fontSize = (11f + (penSizeDp - 2f) * 0.45f).coerceIn(11f, 19f).sp)
}
