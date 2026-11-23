package dev.penlab.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Wraps the app's Compose UI so Material components pick up PenLab's colours. Material's
 * primary is the accent, so text buttons, switches, sliders and progress all carry it.
 */
@Composable
fun PenLabTheme(content: @Composable () -> Unit) {
  val look = Accent.theme
  val scheme = remember(look) {
    darkColorScheme(
      primary = look.accent,
      onPrimary = look.onAccent,
      primaryContainer = look.accent.copy(alpha = 0.22f),
      onPrimaryContainer = look.icon,
      secondary = look.accent,
      surface = look.toolbar,
      surfaceContainer = look.toolbar,
      surfaceContainerHigh = look.toolbar,
      surfaceContainerHighest = look.toolbar,
      background = look.backdrop,
      onBackground = look.icon,
      onSurface = look.icon,
      onSurfaceVariant = look.icon,
      outline = look.hairline,
      outlineVariant = look.hairline,
      // The "Page cleared" message.
      inverseSurface = look.toolbar,
      inverseOnSurface = look.icon,
      inversePrimary = look.accent,
    )
  }
  MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
}
