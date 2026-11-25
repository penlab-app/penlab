package dev.penlab.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect

/**
 * Wraps the app's Compose UI in PenLab's 3-layer Material 3 design system:
 * - Light & Dark [MaterialTheme.colorScheme] for both Blueprint and Copper looks
 * - [LocalPenLabColors] extended semantic tokens
 * - [PenLabTypography] (Bricolage Grotesque display/title + system sans body/label)
 * - [PenLabShapes.material] corner radii
 */
@Composable
fun PenLabTheme(content: @Composable () -> Unit) {
  val look = Accent.theme
  val mode = Accent.themeMode
  val systemDark = isSystemInDarkTheme()
  val isDark = mode.resolveDark(systemDark)
  // Keep Accent.isDark synchronized for non-composable/DrawScope bridges immediately and across system theme changes.
  Accent.syncResolvedDark(isDark)
  SideEffect { Accent.syncResolvedDark(isDark) }

  val scheme = penLabColorScheme(look, isDark)
  val extended = penLabExtendedColors(look, isDark)

  CompositionLocalProvider(LocalPenLabColors provides extended) {
    MaterialTheme(
      colorScheme = scheme,
      typography = PenLabTypography,
      shapes = PenLabShapes.material,
      content = content,
    )
  }
}
