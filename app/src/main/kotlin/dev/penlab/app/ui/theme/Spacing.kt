package dev.penlab.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * PenLab's 8dp-aligned spacing tokens and ergonomic touch/margin constants.
 */
object PenLabSpacing {
  val xs = 4.dp
  val sm = 8.dp
  val md = 12.dp
  val lg = 16.dp
  val xl = 24.dp
  val xxl = 32.dp

  /** Minimum interactive touch target size across all controls. */
  val touchTarget = 48.dp

  /** Floating canvas chrome safe margin from screen edges (in addition to system bar insets). */
  val edgeMargin = 12.dp
}
