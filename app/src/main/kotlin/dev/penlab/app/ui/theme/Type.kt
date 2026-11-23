package dev.penlab.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.penlab.app.R

/**
 * Titles (notebook names, headings, dialog titles) use Bricolage Grotesque, a variable font with
 * some character (user request: the UI felt "too generic"); body text, labels and controls keep
 * the system face so they stay plain and legible. Licence: licenses/BricolageGrotesque-OFL.txt.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun bricolage(weight: Int, opticalSize: Float) =
  Font(
    R.font.bricolage,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opticalSize)),
  )

val TitleFont =
  FontFamily(bricolage(600, 24f), bricolage(700, 36f), bricolage(500, 14f))

private val base = Typography()

val Typography =
  Typography(
    displaySmall = base.displaySmall.copy(fontFamily = TitleFont, fontWeight = FontWeight(700), letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontFamily = TitleFont, fontWeight = FontWeight(700), letterSpacing = (-0.4).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = TitleFont, fontWeight = FontWeight(700), letterSpacing = (-0.3).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = TitleFont, fontWeight = FontWeight(600), letterSpacing = (-0.2).sp),
    titleLarge = base.titleLarge.copy(fontFamily = TitleFont, fontWeight = FontWeight(600)),
    titleMedium = base.titleMedium.copy(fontFamily = TitleFont, fontWeight = FontWeight(600), letterSpacing = 0.sp),
    titleSmall = base.titleSmall.copy(fontFamily = TitleFont, fontWeight = FontWeight(500), letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
  )
