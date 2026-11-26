package dev.penlab.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * PenLab corner radii tokens, wired into MaterialTheme.shapes plus the pill capsule shape.
 */
object PenLabShapes {
  val extraSmall = RoundedCornerShape(6.dp)
  val small = RoundedCornerShape(10.dp)
  val medium = RoundedCornerShape(14.dp)
  /**
   * [medium] on the top corners only. A library card clips its ripple to this: the text under the
   * cover sits at the card's bottom-left, and a rounded bottom corner cut off its first glyph.
   */
  val mediumTop = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
  val large = RoundedCornerShape(18.dp)
  val extraLarge = RoundedCornerShape(24.dp)
  val pill = CircleShape

  val material: Shapes =
    Shapes(
      extraSmall = extraSmall,
      small = small,
      medium = medium,
      large = large,
      extraLarge = extraLarge,
    )
}
