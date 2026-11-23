package dev.penlab.app.ink

import androidx.ink.brush.BrushBehavior
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.BrushTip
import androidx.ink.brush.EasingFunction
import androidx.ink.brush.ExperimentalInkCustomBrushApi
import androidx.ink.brush.StockBrushes

/**
 * The shapes of line each tool makes ("brush families" in Jetpack Ink). A family decides how
 * the line reacts to the pen; colour and size are added per stroke.
 */
internal object PenBrushes {
  /** Round tip whose width follows how hard you press. The pen PenLab started with. */
  private val pressure = StockBrushes.pressurePen()

  /** Round tip, same width however you press: a ballpoint or fineliner. */
  private val ballpoint = StockBrushes.marker()

  val highlighter = StockBrushes.highlighter()

  /**
   * A flat nib held at an angle, like a fountain or calligraphy pen: strokes along the nib's
   * edge come out thin, strokes across it thick, and pressing harder widens both.
   *
   * Built with Ink's custom-brush API, which is still marked experimental (it may change in a
   * later Ink version; the stock brushes above will not). Lint flags the constructors as
   * library-group-only, but they are public in Ink 1.0.
   */
  @OptIn(ExperimentalInkCustomBrushApi::class)
  @android.annotation.SuppressLint("RestrictedApi")
  private val fountain: BrushFamily =
    BrushFamily(
      tip =
        BrushTip(
          scaleX = 1f,
          scaleY = NIB_THICKNESS,
          cornerRounding = 0.5f,
          rotationDegrees = NIB_ANGLE_DEGREES,
          behaviors =
            listOf(
              BrushBehavior(
                source = BrushBehavior.Source.NORMALIZED_PRESSURE,
                target = BrushBehavior.Target.SIZE_MULTIPLIER,
                sourceValueRangeStart = 0f,
                sourceValueRangeEnd = 1f,
                targetModifierRangeStart = 0.6f,
                targetModifierRangeEnd = 1.3f,
                sourceOutOfRangeBehavior = BrushBehavior.OutOfRange.CLAMP,
                responseCurve = EasingFunction.Predefined.LINEAR,
                responseTimeMillis = 0L,
                enabledToolTypes = BrushBehavior.ALL_TOOL_TYPES,
              )
            ),
        )
    )

  /**
   * Names for the families, used in saved pages. Never rename one: files on the tablet use
   * these names. Strokes always hold one of the exact objects above, so comparing by identity
   * is enough.
   */
  fun idOf(family: BrushFamily): String =
    when {
      family === ballpoint -> "ballpoint"
      family === fountain -> "fountain"
      family === highlighter -> "highlighter"
      else -> "pressure"
    }

  fun familyById(id: String): BrushFamily =
    when (id) {
      "ballpoint" -> ballpoint
      "fountain" -> fountain
      "highlighter" -> highlighter
      else -> pressure
    }

  fun familyFor(tool: InkTool): BrushFamily =
    when {
      tool.isHighlighter -> highlighter
      tool.style == PenStyle.BALLPOINT -> ballpoint
      tool.style == PenStyle.FOUNTAIN -> fountain
      else -> pressure
    }

  /** Nib thickness as a share of its width: smaller = more contrast between thick and thin. */
  private const val NIB_THICKNESS = 0.3f

  /**
   * How the nib is turned: the classic italic-hand angle, so strokes rising to the right are
   * hairlines and strokes falling to the right are broad. (Negative because y grows downwards
   * on a screen.)
   */
  private const val NIB_ANGLE_DEGREES = -45f
}
