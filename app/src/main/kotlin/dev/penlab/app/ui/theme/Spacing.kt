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

/**
 * Named sizes for the Library and onboarding screens, so no literal dp values sit in those
 * composables. Spacing between things still comes from [PenLabSpacing].
 */
object PenLabLayout {
  /** Dialog / sheet maximum widths. */
  val dialogMaxWidth = 520.dp
  val wideDialogMaxWidth = 560.dp

  /** Shadow depth of dialogs and sheets. */
  val dialogElevation = 16.dp

  /** Library page gutter (header, trail, grid, shelves). */
  val libraryGutter = PenLabSpacing.xl

  /** Width at or below which the library header wraps onto two lines (portrait). */
  val headerNarrowBreakpoint = 1000.dp

  /** Library search pill width. */
  val searchWidth = 320.dp

  /** Notebook / folder card widths: grid minimum, Continue strip, course shelf. */
  val cardMinWidth = 160.dp
  val continueCardWidth = 190.dp
  val shelfCardWidth = 150.dp

  /** Minimum width of overflow / import menus. */
  val menuMinWidth = 220.dp

  /** Small glyph sizes (status dots, badges, in-field icons). */
  val dot = 8.dp
  val dotLarge = 12.dp
  val iconSmall = 16.dp
  val iconMedium = 20.dp
  val iconLarge = 24.dp
  val iconFolder = 40.dp

  /** Icon glyph sizes inside 48dp controls: compact (18) and the default control icon (22). */
  val iconCompact = 18.dp
  val iconDefault = 22.dp

  /**
   * The tinted round "visual" drawn inside a 48dp hit box (chrome icon buttons, dock cells, the
   * bookmark toggle): smaller than the touch target on purpose, so neighbours do not look crowded.
   */
  val controlVisual = 36.dp
  val controlVisualSmall = 28.dp

  /** Round colour swatch used in the pen panel's 4-column palette (inside its 48dp cell). */
  val swatchSizeCompact = 36.dp

  /** Paper mini-preview tile (New notebook dialog): width-to-height of the paper swatch. */
  const val paperPreviewAspect = 1.9f

  /** Cover-slot aspect for plain folders (A4 portrait, so folder and notebook rows line up). */
  const val a4PortraitAspect = 210f / 297f

  /** Cover aspect of an endless canvas. */
  const val canvasAspect = 4f / 3f

  /** Round colour swatch inside its 48dp touch target (room left for the selection ring). */
  val swatchSize = 40.dp

  /** Selected-tile ring and hairline thickness. */
  val ringSelected = 2.dp
  val hairline = 1.dp
}
