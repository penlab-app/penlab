package dev.penlab.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.penlab.app.ink.InkColors
import dev.penlab.app.ink.InkTool
import dev.penlab.app.ink.InkTools
import dev.penlab.app.ink.LineType
import dev.penlab.app.ink.NamedColor
import dev.penlab.app.ink.PageBackground
import dev.penlab.app.ink.PageStyle
import dev.penlab.app.ink.PenStyle
import dev.penlab.app.ui.components.PenLabChromePill
import dev.penlab.app.ui.components.PenLabIconButton
import dev.penlab.app.ui.components.PenLabMenuItem
import dev.penlab.app.ui.components.PenLabPopoverSurface
import dev.penlab.app.ui.components.PenLabSegmentedControl
import dev.penlab.app.ui.components.PenLabSliderRow
import dev.penlab.app.ui.components.PenLabSnackbar
import dev.penlab.app.ui.components.PenLabSwitchRow
import dev.penlab.app.ui.components.insetPillClickable
import dev.penlab.app.ui.theme.Accent
import dev.penlab.app.ui.theme.LocalPenLabColors
import dev.penlab.app.ui.theme.PageThemeMode
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.PenLabLayout
import dev.penlab.app.ui.theme.PenLabShapes
import dev.penlab.app.ui.theme.PenLabSpacing
import dev.penlab.app.ui.theme.PenLabTextStyles
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sin

// ---- Pure formatting & contrast helpers (unit-tested) -----------------------------------------

/** Formats a tool's active size readout in millimetres with stable decimal precision. */
internal fun formatToolSizeMm(kind: InkTool.Kind, sizeDp: Float, mmPerDp: Float): String {
  val mm =
    when (kind) {
      InkTool.Kind.ERASER -> sizeDp * mmPerDp * 2f // sizeDp is eraser radius; display diameter
      InkTool.Kind.TEXT -> sizeDp // text sizeDp is already stored in millimetres
      else -> sizeDp * mmPerDp
    }
  return when (kind) {
    InkTool.Kind.PEN -> String.format(Locale.US, "%.2f mm", mm)
    else -> String.format(Locale.US, "%.1f mm", mm)
  }
}

/** Formats a favourite size chip concisely in millimetres. */
internal fun formatFavouriteChipMm(kind: InkTool.Kind, sizeDp: Float, mmPerDp: Float): String {
  val mm =
    when (kind) {
      InkTool.Kind.ERASER -> sizeDp * mmPerDp * 2f
      InkTool.Kind.TEXT -> sizeDp
      else -> sizeDp * mmPerDp
    }
  return if (mm < 1f) String.format(Locale.US, "%.2f mm", mm)
  else String.format(Locale.US, "%.1f mm", mm)
}

/** Picks whichever of [lightTint] or [darkTint] achieves higher contrast on [swatchColor]. */
internal fun swatchCheckTint(swatchColor: Color, lightTint: Color, darkTint: Color): Color {
  val bg = swatchColor.luminance()
  val lightL = lightTint.luminance()
  val darkL = darkTint.luminance()
  val lightRatio = (maxOf(bg, lightL) + 0.05f) / (minOf(bg, lightL) + 0.05f)
  val darkRatio = (maxOf(bg, darkL) + 0.05f) / (minOf(bg, darkL) + 0.05f)
  return if (lightRatio >= darkRatio) lightTint else darkTint
}

/** Steps paper ruling spacing by [deltaMm] in 0.5 mm increments within 2.0..20.0 mm. */
internal fun stepPaperSpacingMm(currentMm: Float, deltaMm: Float): Float {
  val stepped = ((currentMm + deltaMm) * 2f).roundToInt() / 2f
  return stepped.coerceIn(MIN_SPACING_MM, MAX_SPACING_MM)
}

/** Resolves whether paper style previews render on dark paper (PDF pages always stay white). */
internal fun effectivePaperPreviewDark(darkPage: Boolean, isPdfNotebook: Boolean): Boolean =
  darkPage && !isPdfNotebook

private const val MIN_SPACING_MM = 2f
private const val MAX_SPACING_MM = 20f

// ---- Tool options popover (Pen, Eraser, Highlighter, Fill, Text) ------------------------------

/** Swatches per row in the colour grid of [ToolOptionsPanel]. */
internal const val SWATCH_COLUMNS = 4

/**
 * The swatches to show for [current]. A colour picked from the wheel or eyedropper (not in the
 * [palette]) takes the place of the last swatch instead of adding one: when the palette fills
 * its rows, an extra swatch would start a new row and push More colours / Pick down by one row.
 * Picking any palette swatch brings the replaced one back. A partly filled last row just gains it.
 */
internal fun swatchesWithCustom(palette: List<NamedColor>, current: Int): List<NamedColor> {
  if (palette.isEmpty() || palette.any { it.paperColor == current }) return palette
  val custom = NamedColor("Custom", current)
  return if (palette.size % SWATCH_COLUMNS == 0) palette.dropLast(1) + custom else palette + custom
}

/**
 * Anchored stylus popover opened by a second tap on an already-selected tool slot:
 *  - **Pen**: 3 visual nib cards (Ballpoint / Pressure / Fountain), 5 line-pattern buttons,
 *    stroke width preview + slider + editable favourite mm chips, curated ink swatches +
 *    More colours + 1-tap Pick from page eyedropper.
 *  - **Eraser**: Complete stroke vs Partial stroke segmented switch with helper text, live
 *    circular radius preview + diameter readout + slider + favourite chips.
 *  - **Highlighter**: Translucent stroke band preview + width slider + favourite chips +
 *    curated highlighter swatches + More colours + Eyedropper.
 *  - **Fill**: Curated swatches + More colours + Eyedropper + concise tip.
 *  - **Text**: Text size slider + favourite chips + colour swatches + More colours + Eyedropper.
 */
@Composable
fun ToolOptionsPanel(
  tool: InkTool,
  darkPage: Boolean,
  favourites: List<Float>,
  mmPerDp: Float,
  onStylePicked: (PenStyle) -> Unit,
  onLinePicked: (LineType) -> Unit,
  onEraseModePicked: (partial: Boolean) -> Unit,
  onSizeChanging: (Float) -> Unit,
  onSizePicked: (Float) -> Unit,
  onAddFavourite: () -> Unit,
  onRemoveFavourite: (Float) -> Unit,
  onColorPicked: (Int) -> Unit,
  onMoreColors: () -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
  onPickEyedropper: (() -> Unit)? = null,
) {
  val scheme = MaterialTheme.colorScheme
  val palette = InkTools.colorsFor(tool.kind)
  // A colour picked from the wheel or eyedropper appears as an extra swatch so it can be re-picked.
  val colors = swatchesWithCustom(palette, tool.paperColor)
  val range = InkTools.sizeRange(tool.kind)
  val hasSizeControl = range.endInclusive > range.start

  PenLabPopoverSurface(
    isPalm = isPalm,
    modifier = modifier.width(320.dp).heightIn(max = 600.dp),
    contentPadding = PaddingValues(PenLabSpacing.md),
    verticalArrangement = Arrangement.Top,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(PenLabSpacing.md),
    ) {
      // ---- Pen: nib style cards + 5 line patterns ----
      if (tool.kind == InkTool.Kind.PEN) {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .selectableGroup()
              .semantics { contentDescription = "Pen style" },
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          for (style in PenStyle.entries) {
            val isSelected = style == tool.style
            val bg = if (isSelected) PenLabComponentTokens.chipSelectedBg else PenLabComponentTokens.chipIdleBg
            val fg = if (isSelected) PenLabComponentTokens.chipSelectedContent else scheme.onSurface
            Column(
              modifier =
                Modifier
                  .weight(1f)
                  .heightIn(min = 68.dp)
                  .clip(PenLabShapes.small)
                  .background(bg)
                  .then(
                    if (isSelected) Modifier.border(1.5.dp, scheme.primary, PenLabShapes.small)
                    else Modifier
                  )
                  .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onStylePicked(style) })
                  .padding(horizontal = 6.dp, vertical = PenLabSpacing.sm)
                  .semantics {
                    contentDescription = style.label
                    if (isSelected) selected = true
                  },
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
              PenStylePreview(style, tool.paperColor, darkPage)
              Text(
                text = style.label,
                color = fg,
                style =
                  MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                  ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        // Five technical line patterns: Solid, Dotted, Dashed, Centre, Phantom.
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .selectableGroup()
              .semantics { contentDescription = "Line pattern" },
          horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.xs),
        ) {
          for (line in LineType.entries) {
            val isSelected = line == tool.line
            val bg = if (isSelected) PenLabComponentTokens.chipSelectedBg else PenLabComponentTokens.chipIdleBg
            val fg = if (isSelected) PenLabComponentTokens.chipSelectedContent else scheme.onSurface
            Column(
              modifier =
                Modifier
                  .weight(1f)
                  .heightIn(min = 54.dp)
                  .clip(PenLabShapes.small)
                  .background(bg)
                  .then(
                    if (isSelected) Modifier.border(1.5.dp, scheme.primary, PenLabShapes.small)
                    else Modifier
                  )
                  .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onLinePicked(line) })
                  .padding(horizontal = 2.dp, vertical = 6.dp)
                  .semantics {
                    contentDescription = "${line.label} line"
                    if (isSelected) selected = true
                  },
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(PenLabSpacing.xs, Alignment.CenterVertically),
            ) {
              LinePreview(line, tool.paperColor, darkPage, modifier = Modifier.size(width = 38.dp, height = 16.dp))
              Text(
                text = line.label,
                color = fg,
                style =
                  PenLabTextStyles.lineTypeLabel.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                  ),
                maxLines = 1,
                softWrap = false,
              )
            }
          }
        }

        PanelDivider()
      }

      // ---- Eraser: Complete vs Partial mode switcher + helper text ----
      if (tool.isEraser) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          PenLabSegmentedControl(
            options = listOf(false, true),
            selected = tool.partialErase,
            onSelect = onEraseModePicked,
            label = { partial -> if (partial) "Partial stroke" else "Complete stroke" },
          )
          Text(
            text =
              if (tool.partialErase) "Erases only the portion of a stroke under the tip."
              else "Removes an entire stroke wherever the eraser touches.",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
          )
        }
        PanelDivider()
      }

      // ---- Size control: live preview + tabular mm readout + slider + favourite mm chips ----
      if (hasSizeControl) {
        val sizeTitle =
          when (tool.kind) {
            InkTool.Kind.ERASER -> "Eraser diameter"
            InkTool.Kind.HIGHLIGHTER -> "Highlighter width"
            InkTool.Kind.TEXT -> "Text size"
            else -> "Stroke width"
          }
        PenLabSliderRow(
          title = sizeTitle,
          valueText = formatToolSizeMm(tool.kind, tool.sizeDp, mmPerDp),
          value = tool.sizeDp,
          onValueChange = onSizeChanging,
          valueRange = range,
          leadingPreview = { SizePreview(tool, tool.sizeDp, darkPage) },
          favourites = favourites,
          favouriteLabel = { formatFavouriteChipMm(tool.kind, it, mmPerDp) },
          isFavouriteSelected = { abs(it - tool.sizeDp) < 0.05f },
          onSelectFavourite = onSizePicked,
          onRemoveFavourite = onRemoveFavourite,
          onAddFavourite = onAddFavourite,
          addFavouriteDescription = "Save current size as favourite",
        )
      }

      // ---- Colour swatches + More colours + 1-tap Eyedropper ----
      if (colors.isNotEmpty()) {
        if (hasSizeControl) PanelDivider()
        Column(
          modifier = Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = "Colour" },
          verticalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
        ) {
          Text(
            text = "Colour",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
          )
          val swatchShape = if (tool.isHighlighter) PenLabShapes.small else CircleShape
          val ringShape = if (tool.isHighlighter) RoundedCornerShape(13.dp) else CircleShape
          val swatchEdge = LocalPenLabColors.current.swatchEdge
          val lightCheckTint = if (LocalPenLabColors.current.isDark) scheme.onSurface else scheme.surface
          val darkCheckTint = if (LocalPenLabColors.current.isDark) scheme.surface else scheme.onSurface

          Column(verticalArrangement = Arrangement.spacedBy(PenLabSpacing.xs)) {
            for (row in colors.chunked(SWATCH_COLUMNS)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
              ) {
                for (color in row) {
                  val isSelected = color.paperColor == tool.paperColor
                  val displayColor = Color(InkColors.onScreen(color.paperColor, darkPage) or 0xFF000000.toInt())
                  Box(
                    modifier =
                      Modifier
                        .weight(1f)
                        .height(PenLabSpacing.touchTarget)
                        .clip(ringShape)
                        .selectable(
                          selected = isSelected,
                          role = Role.RadioButton,
                          onClick = { onColorPicked(color.paperColor) },
                        )
                        .semantics {
                          contentDescription = color.name
                          if (isSelected) selected = true
                        },
                    contentAlignment = Alignment.Center,
                  ) {
                    Box(
                      modifier =
                        Modifier
                          .size(42.dp)
                          .then(
                            if (isSelected) Modifier.border(2.dp, scheme.primary, ringShape)
                            else Modifier
                          ),
                      contentAlignment = Alignment.Center,
                    ) {
                      Box(
                        modifier =
                          Modifier
                            .size(PenLabLayout.swatchSizeCompact)
                            .clip(swatchShape)
                            .background(displayColor)
                            .border(1.dp, swatchEdge, swatchShape),
                        contentAlignment = Alignment.Center,
                      ) {
                        if (isSelected) {
                          Icon(
                            imageVector = PenLabIcons.CheckBare,
                            contentDescription = null,
                            tint = swatchCheckTint(displayColor, lightCheckTint, darkCheckTint),
                            modifier = Modifier.size(PenLabLayout.iconCompact),
                          )
                        }
                      }
                    }
                  }
                }
                repeat(SWATCH_COLUMNS - row.size) {
                  Spacer(Modifier.weight(1f))
                }
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              modifier =
                Modifier
                  .weight(1f)
                  .heightIn(min = PenLabSpacing.touchTarget)
                  .clip(PenLabShapes.small)
                  .background(PenLabComponentTokens.chipIdleBg)
                  .clickable(role = Role.Button, onClick = onMoreColors)
                  .padding(horizontal = PenLabSpacing.md)
                  .semantics { contentDescription = "More colours" },
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = PenLabIcons.Palette,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(PenLabLayout.iconMedium),
              )
              Spacer(Modifier.width(PenLabSpacing.sm))
              Text(
                text = "More colours",
                color = scheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
              )
            }
            if (onPickEyedropper != null) {
              Row(
                modifier =
                  Modifier
                    .heightIn(min = PenLabSpacing.touchTarget)
                    .clip(PenLabShapes.small)
                    .background(PenLabComponentTokens.chipIdleBg)
                    .clickable(role = Role.Button, onClick = onPickEyedropper)
                    .padding(horizontal = PenLabSpacing.md)
                    .semantics { contentDescription = "Pick from page" },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = PenLabIcons.Eyedropper,
                  contentDescription = null,
                  tint = scheme.onSurface,
                  modifier = Modifier.size(PenLabLayout.iconMedium),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                  text = "Pick",
                  color = scheme.onSurface,
                  style = MaterialTheme.typography.labelLarge,
                  maxLines = 1,
                  softWrap = false,
                )
              }
            }
          }
        }
      }

      if (tool.isFill) {
        Text(
          text = "Tap inside a closed shape to fill.",
          color = scheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

// ---- Tools (Instruments) & More popovers ------------------------------------------------------

/**
 * The panel behind the Tools button: switch the ruler, protractor, and hold-to-snap shapes on or
 * off. When on, instruments lie on the page: drag with one finger, turn with two, draw along an
 * edge with the pen.
 */
@Composable
fun InstrumentsPanel(
  rulerOn: Boolean,
  protractorOn: Boolean,
  onRuler: (Boolean) -> Unit,
  onProtractor: (Boolean) -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
  shapeSnappingOn: Boolean = true,
  onShapeSnapping: ((Boolean) -> Unit)? = null,
) {
  PenLabPopoverSurface(
    isPalm = isPalm,
    modifier = modifier.width(308.dp),
    contentPadding = PaddingValues(PenLabSpacing.md),
    verticalArrangement = Arrangement.spacedBy(PenLabSpacing.xs),
  ) {
    PenLabSwitchRow(
      label = "Ruler",
      subtitle = "Draw crisp straight edges & measure",
      icon = PenLabIcons.Ruler,
      checked = rulerOn,
      onCheckedChange = onRuler,
    )
    PenLabSwitchRow(
      label = "Protractor",
      subtitle = "Measure angles & trace arcs",
      icon = PenLabIcons.Protractor,
      checked = protractorOn,
      onCheckedChange = onProtractor,
    )
    if (onShapeSnapping != null) {
      PanelDivider()
      PenLabSwitchRow(
        label = "Hold to snap shapes",
        subtitle = "Pause at stroke end to perfect lines & curves",
        icon = PenLabIcons.Shapes,
        checked = shapeSnappingOn,
        onCheckedChange = onShapeSnapping,
      )
    }
    Text(
      text = "Drag instruments with one finger, turn with two.",
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(top = PenLabSpacing.xs, start = PenLabSpacing.xs),
    )
  }
}

/**
 * The panel behind the More button on the center toolbar, grouped cleanly into:
 *  - **Insert**: Add from PDF, Add image, Text box
 *  - **Pens**: Highlighter (a drawing tool outside the pen slots, so it is not "Canvas")
 *  - **Canvas**: Layers
 *  - **Destructive**: Clear page
 */
@Composable
fun MoreToolsPanel(
  canClear: Boolean,
  onAddFromPdf: () -> Unit,
  onAddImage: () -> Unit,
  onSelectHighlighter: () -> Unit,
  onSelectTextBox: () -> Unit,
  onOpenLayers: () -> Unit,
  onClearPage: () -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  PenLabPopoverSurface(
    isPalm = isPalm,
    modifier = modifier.width(256.dp),
    contentPadding = PaddingValues(vertical = PenLabSpacing.sm, horizontal = 6.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text = "Insert",
      color = scheme.onSurfaceVariant,
      style = MaterialTheme.typography.labelMedium,
      modifier = Modifier.padding(horizontal = PenLabSpacing.md, vertical = PenLabSpacing.xs),
    )
    PenLabMenuItem(label = "Add from PDF", icon = PenLabIcons.Pdf, onClick = onAddFromPdf)
    PenLabMenuItem(label = "Add image", icon = PenLabIcons.Image, onClick = onAddImage)
    PenLabMenuItem(label = "Text box", icon = PenLabIcons.Text, onClick = onSelectTextBox)

    HorizontalDivider(
      color = scheme.outlineVariant,
      thickness = 1.dp,
      modifier = Modifier.padding(vertical = PenLabSpacing.xs, horizontal = PenLabSpacing.sm),
    )

    Text(
      text = "Pens",
      color = scheme.onSurfaceVariant,
      style = MaterialTheme.typography.labelMedium,
      modifier = Modifier.padding(horizontal = PenLabSpacing.md, vertical = PenLabSpacing.xs),
    )
    PenLabMenuItem(label = "Highlighter", icon = PenLabIcons.Highlighter, onClick = onSelectHighlighter)

    HorizontalDivider(
      color = scheme.outlineVariant,
      thickness = 1.dp,
      modifier = Modifier.padding(vertical = PenLabSpacing.xs, horizontal = PenLabSpacing.sm),
    )

    Text(
      text = "Canvas",
      color = scheme.onSurfaceVariant,
      style = MaterialTheme.typography.labelMedium,
      modifier = Modifier.padding(horizontal = PenLabSpacing.md, vertical = PenLabSpacing.xs),
    )
    PenLabMenuItem(label = "Layers", icon = PenLabIcons.Layers, onClick = onOpenLayers)

    HorizontalDivider(
      color = scheme.outlineVariant,
      thickness = 1.dp,
      modifier = Modifier.padding(vertical = PenLabSpacing.xs, horizontal = PenLabSpacing.sm),
    )

    PenLabMenuItem(
      label = "Clear page",
      icon = PenLabIcons.Clear,
      destructive = true,
      enabled = canClear,
      onClick = onClearPage,
    )
  }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  PenLabSwitchRow(label = label, checked = checked, onCheckedChange = onChange)
}

// ---- Paper style & spacing popover ------------------------------------------------------------

/** The panel behind the paper button: Light/Dark paper, 3×3 visual paper styles, and spacing. */
@Composable
fun PaperPanel(
  style: PageStyle,
  darkPage: Boolean,
  onStylePicked: (PageStyle) -> Unit,
  spacingMm: Float,
  onSpacing: (Float) -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
  isPdfNotebook: Boolean = false,
  showPaperModeToggle: Boolean = false,
) {
  PenLabPopoverSurface(
    isPalm = isPalm,
    modifier = modifier.width(332.dp).heightIn(max = 580.dp),
    contentPadding = PaddingValues(PenLabSpacing.md),
    verticalArrangement = Arrangement.Top,
  ) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
      PaperPanelContent(
        style = style,
        darkPage = darkPage,
        onStylePicked = onStylePicked,
        spacingMm = spacingMm,
        onSpacing = onSpacing,
        isPdfNotebook = isPdfNotebook,
        showPaperModeToggle = showPaperModeToggle,
      )
    }
  }
}

/**
 * Embeddable paper style + spacing + optional Light/Dark paper toggle content shared by
 * [PaperPanel] and [PageConfigPopover].
 */
@Composable
fun PaperPanelContent(
  style: PageStyle,
  darkPage: Boolean,
  onStylePicked: (PageStyle) -> Unit,
  spacingMm: Float,
  onSpacing: (Float) -> Unit,
  isPdfNotebook: Boolean = false,
  showPaperModeToggle: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val presets = PageBackground.spacingPresetsMm(style)
  val previewDark = effectivePaperPreviewDark(darkPage, isPdfNotebook)

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(PenLabSpacing.md),
  ) {
    if (showPaperModeToggle) {
      if (isPdfNotebook) {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(PenLabShapes.small)
              .background(PenLabComponentTokens.chipIdleBg)
              .padding(horizontal = PenLabSpacing.md, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
        ) {
          Icon(
            imageVector = PenLabIcons.Pdf,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(PenLabLayout.iconCompact),
          )
          Text(
            text = "PDF pages stay white",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
          )
        }
      } else {
        PenLabSegmentedControl(
          options = PageThemeMode.entries,
          selected = Accent.pageThemeMode,
          onSelect = Accent::setPageThemeMode,
          label = { mode ->
            when (mode) {
              PageThemeMode.LIGHT -> "Light paper"
              PageThemeMode.DARK -> "Dark paper"
            }
          },
          icon = { mode ->
            when (mode) {
              PageThemeMode.LIGHT -> PenLabIcons.Sun
              PageThemeMode.DARK -> PenLabIcons.Moon
            }
          },
        )
      }
    }

    // 3×3 visual grid of all 9 paper styles rendered with live Light/Dark paper tint & ruling.
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .selectableGroup()
          .semantics { contentDescription = "Paper style" },
      verticalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
    ) {
      for (row in PageStyle.entries.chunked(3)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
        ) {
          for (option in row) {
            val isSelected = option == style
            val bg = if (isSelected) PenLabComponentTokens.chipSelectedBg else PenLabComponentTokens.chipIdleBg
            val fg = if (isSelected) PenLabComponentTokens.chipSelectedContent else scheme.onSurface
            Column(
              modifier =
                Modifier
                  .weight(1f)
                  .heightIn(min = 78.dp)
                  .clip(PenLabShapes.small)
                  .background(bg)
                  .then(
                    if (isSelected) Modifier.border(1.5.dp, scheme.primary, PenLabShapes.small)
                    else Modifier
                  )
                  .selectable(
                    selected = isSelected,
                    role = Role.RadioButton,
                    onClick = { onStylePicked(option) },
                  )
                  .padding(horizontal = PenLabSpacing.xs, vertical = PenLabSpacing.sm)
                  .semantics {
                    contentDescription = option.label
                    if (isSelected) selected = true
                  },
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
              PaperPreview(option, previewDark)
              Text(
                text = option.label,
                color = fg,
                style =
                  MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                  ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          repeat(3 - row.size) {
            Spacer(Modifier.weight(1f))
          }
        }
      }
    }

    if (presets.isNotEmpty()) {
      PanelDivider()
      val current = if (spacingMm > 0f) spacingMm else PageBackground.defaultSpacingMm(style)
      Column(verticalArrangement = Arrangement.spacedBy(PenLabSpacing.sm)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Ruling spacing",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
          )
          Row(
            modifier =
              Modifier
                .clip(PenLabShapes.small)
                .background(PenLabComponentTokens.chipIdleBg),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            PenLabIconButton(
              icon = PenLabIcons.Minus,
              contentDescription = "Decrease ruling spacing",
              enabled = current > MIN_SPACING_MM + 0.05f,
              iconSize = PenLabLayout.iconCompact,
              onClick = { onSpacing(stepPaperSpacingMm(current, -0.5f)) },
            )
            Text(
              text = String.format(Locale.US, "%.1f mm", current),
              color = scheme.onSurface,
              style = PenLabTextStyles.tabularLabel,
              textAlign = TextAlign.Center,
              modifier = Modifier.widthIn(min = 60.dp),
            )
            PenLabIconButton(
              icon = PenLabIcons.Plus,
              contentDescription = "Increase ruling spacing",
              enabled = current < MAX_SPACING_MM - 0.05f,
              iconSize = PenLabLayout.iconCompact,
              onClick = { onSpacing(stepPaperSpacingMm(current, 0.5f)) },
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          for (mm in presets) {
            val isSelected = abs(current - mm) < 0.05f
            val bg = if (isSelected) PenLabComponentTokens.chipSelectedBg else PenLabComponentTokens.chipIdleBg
            val fg = if (isSelected) PenLabComponentTokens.chipSelectedContent else scheme.onSurface
            Box(
              modifier =
                Modifier
                  .weight(1f)
                  .heightIn(min = PenLabSpacing.touchTarget)
                  .clip(PenLabShapes.small)
                  .background(bg)
                  .then(
                    if (isSelected) Modifier.border(1.5.dp, scheme.primary, PenLabShapes.small)
                    else Modifier
                  )
                  .clickable(role = Role.Button) { onSpacing(mm) }
                  .semantics {
                    contentDescription = "Spacing ${mm.toInt()} millimetres"
                    if (isSelected) selected = true
                  },
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "${mm.toInt()} mm",
                color = fg,
                style =
                  PenLabTextStyles.tabularMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                  ),
                maxLines = 1,
                softWrap = false,
              )
            }
          }
        }
      }
    }
  }
}

/**
 * A short message with one action ("Page cleared · Undo"), in the same shape as the toolbar so
 * it reads as the toolbar speaking, not as a system alert.
 */
@Composable
fun ToolbarMessage(message: String, actionLabel: String?, onAction: () -> Unit, modifier: Modifier = Modifier) {
  PenLabSnackbar(
    message = message,
    actionLabel = actionLabel,
    onAction = if (actionLabel != null) onAction else null,
    modifier = modifier,
  )
}

@Composable
private fun PanelDivider() {
  HorizontalDivider(
    color = MaterialTheme.colorScheme.outlineVariant,
    thickness = 1.dp,
  )
}

// ---- Crisp stroke & paper previews ------------------------------------------------------------

/** A short line drawn in [line]'s pattern over a subtle paper-matched pill so ink always reads. */
@Composable
internal fun LinePreview(
  line: LineType,
  paperColor: Int,
  darkPage: Boolean,
  modifier: Modifier = Modifier.size(width = 40.dp, height = 18.dp),
) {
  val paper = Color(InkColors.pageOnScreen(darkPage))
  val color = Color(InkColors.onScreen(paperColor, darkPage) or 0xFF000000.toInt())
  val edge = LocalPenLabColors.current.swatchEdge
  val shape = RoundedCornerShape(6.dp)
  Canvas(
    modifier
      .clip(shape)
      .background(paper)
      .border(0.5.dp, edge, shape)
  ) {
    val d = 2.dp.toPx()
    val effect =
      line.pattern?.let { pattern ->
        // Zero-length "on" parts are dots: a round cap makes them visible.
        PathEffect.dashPathEffect(FloatArray(pattern.size) { i -> maxOf(pattern[i] * d * 0.55f, if (i % 2 == 0) 0.01f else d) })
      }
    val inset = 4.dp.toPx()
    drawLine(
      color = color,
      start = Offset(inset, size.height / 2f),
      end = Offset(size.width - inset, size.height / 2f),
      strokeWidth = d,
      cap = StrokeCap.Round,
      pathEffect = effect,
    )
  }
}

/**
 * A smooth wavy stroke drawn the way each pen type behaves on a crisp paper-matched well:
 * even for Ballpoint, swelling in the middle for Pressure, calligraphic thick-and-thin for Fountain.
 */
@Composable
private fun PenStylePreview(style: PenStyle, paperColor: Int, darkPage: Boolean) {
  val paper = Color(InkColors.pageOnScreen(darkPage))
  val color = Color(InkColors.onScreen(paperColor, darkPage) or 0xFF000000.toInt())
  val edge = LocalPenLabColors.current.swatchEdge
  val shape = RoundedCornerShape(8.dp)
  Canvas(
    Modifier
      .size(width = 62.dp, height = 28.dp)
      .clip(shape)
      .background(paper)
      .border(0.5.dp, edge, shape)
  ) {
    val steps = 32
    val base = 2.2.dp.toPx()
    val padX = 7.dp.toPx()
    var previous = Offset(padX, size.height / 2f)
    for (i in 1..steps) {
      val t = i / steps.toFloat()
      val point =
        Offset(
          padX + t * (size.width - padX * 2f),
          size.height / 2f - sin(t * 2f * PI.toFloat()) * size.height * 0.28f,
        )
      val width =
        when (style) {
          PenStyle.BALLPOINT -> base
          PenStyle.PRESSURE -> base * (0.42f + 1.25f * sin(t * PI.toFloat()))
          PenStyle.FOUNTAIN -> {
            val direction = atan2(point.y - previous.y, point.x - previous.x)
            base * (0.35f + 1.55f * abs(sin(direction + PI.toFloat() / 4f)))
          }
        }
      drawLine(color, previous, point, strokeWidth = width, cap = StrokeCap.Round)
      previous = point
    }
  }
}

/**
 * Shows a tool's live size:
 * - Pen: stroke line at true visual weight over a paper-matched well.
 * - Highlighter: translucent band over a sample baseline ink stroke so opacity reads clearly.
 * - Eraser: crisp circular radius preview tinted in theme `onSurface` / `primary`.
 * - Text: clean Aa glyph preview.
 */
@Composable
private fun SizePreview(tool: InkTool, sizeDp: Float, darkPage: Boolean) {
  val scheme = MaterialTheme.colorScheme
  val edge = LocalPenLabColors.current.swatchEdge
  val shape = RoundedCornerShape(8.dp)

  if (tool.isText) {
    val color = Color(InkColors.onScreen(tool.paperColor, darkPage) or 0xFF000000.toInt())
    val paper = Color(InkColors.pageOnScreen(darkPage))
    Box(
      modifier =
        Modifier
          .size(width = 44.dp, height = 34.dp)
          .clip(shape)
          .background(paper)
          .border(0.5.dp, edge, shape),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = "Aa",
        color = color,
        style = PenLabTextStyles.sizePreview(sizeDp),
      )
    }
    return
  }

  if (tool.isEraser) {
    val strokeColor = scheme.onSurface
    val fillColor = scheme.primary.copy(alpha = 0.14f)
    Canvas(
      Modifier
        .size(width = 44.dp, height = 34.dp)
        .clip(shape)
        .background(PenLabComponentTokens.chipIdleBg)
    ) {
      // Scale eraser radii proportionally so the full 4..48 dp range stays clearly distinguishable.
      val maxR = size.minDimension / 2f - 3.dp.toPx()
      val normalized = ((sizeDp - 4f) / 44f).coerceIn(0f, 1f)
      val r = 3.dp.toPx() + normalized * (maxR - 3.dp.toPx())
      drawCircle(fillColor, radius = r)
      drawCircle(strokeColor, radius = r, style = Stroke(1.5.dp.toPx()))
    }
    return
  }

  val paper = Color(InkColors.pageOnScreen(darkPage))
  val sampleInk = Color(InkColors.onScreen(InkTools.penColors[0].paperColor, darkPage) or 0xFF000000.toInt())
  val toolColor =
    if (tool.isHighlighter) Color(InkColors.onScreen(tool.paperColor, darkPage))
    else Color(InkColors.onScreen(tool.paperColor, darkPage) or 0xFF000000.toInt())

  Canvas(
    Modifier
      .size(width = 48.dp, height = 34.dp)
      .clip(shape)
      .background(paper)
      .border(0.5.dp, edge, shape)
  ) {
    val y = size.height / 2f
    val padX = 7.dp.toPx()
    if (tool.isHighlighter) {
      // Draw a faint underlying baseline line so the translucent highlighter band is unmistakable.
      drawLine(
        color = sampleInk.copy(alpha = 0.55f),
        start = Offset(padX, y),
        end = Offset(size.width - padX, y),
        strokeWidth = 1.4.dp.toPx(),
        cap = StrokeCap.Round,
      )
      val bandH = (sizeDp * 0.6f).dp.toPx().coerceIn(4.dp.toPx(), size.height - 6.dp.toPx())
      drawLine(
        color = toolColor,
        start = Offset(padX, y),
        end = Offset(size.width - padX, y),
        strokeWidth = bandH,
        cap = StrokeCap.Butt,
      )
    } else {
      val widthPx = sizeDp.dp.toPx().coerceIn(1.dp.toPx(), size.height - 8.dp.toPx())
      drawLine(
        color = toolColor,
        start = Offset(padX, y),
        end = Offset(size.width - padX, y),
        strokeWidth = widthPx,
        cap = StrokeCap.Round,
      )
    }
  }
}

/** A mini sheet preview with accurate Light or Dark paper tint and ruling lines. */
@Composable
private fun PaperPreview(style: PageStyle, darkPage: Boolean) {
  val paper = Color(InkColors.pageOnScreen(darkPage))
  val ruling = Color(InkColors.onScreen(PageBackground.RULING_PAPER_COLOR, darkPage))
  val minor = Color(InkColors.onScreen(PageBackground.MINOR_PAPER_COLOR, darkPage))
  val edge = LocalPenLabColors.current.swatchEdge
  val shape = RoundedCornerShape(6.dp)
  Canvas(
    Modifier
      .size(width = 52.dp, height = 38.dp)
      .clip(shape)
      .background(paper)
      .border(1.dp, edge, shape)
  ) {
    val step = size.height / 4f
    when (style) {
      PageStyle.BLANK -> Unit
      PageStyle.LINED ->
        for (i in 1..3) {
          drawLine(ruling, Offset(0f, step * i), Offset(size.width, step * i), strokeWidth = 1.dp.toPx())
        }
      PageStyle.DOTTED ->
        for (row in 1..3) {
          for (column in 1..4) {
            drawCircle(ruling, radius = 1.15.dp.toPx(), center = Offset(size.width / 5f * column, step * row))
          }
        }
      PageStyle.CORNELL -> {
        for (i in 1..3) {
          drawLine(minor, Offset(0f, step * i), Offset(size.width, step * i), strokeWidth = 0.8.dp.toPx())
        }
        val w = 1.2.dp.toPx()
        drawLine(ruling, Offset(0f, step * 0.85f), Offset(size.width, step * 0.85f), strokeWidth = w)
        drawLine(ruling, Offset(size.width * 0.3f, step * 0.85f), Offset(size.width * 0.3f, step * 3.2f), strokeWidth = w)
        drawLine(ruling, Offset(0f, step * 3.2f), Offset(size.width, step * 3.2f), strokeWidth = w)
      }
      PageStyle.CP -> {
        val cell = 6.dp.toPx()
        var x = cell
        while (x < size.width) {
          drawLine(minor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.7.dp.toPx())
          x += cell
        }
        var y = cell
        while (y < size.height) {
          drawLine(minor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.7.dp.toPx())
          y += cell
        }
        val w = 1.2.dp.toPx()
        drawLine(ruling, Offset(0f, step * 0.75f), Offset(size.width, step * 0.75f), strokeWidth = w)
        drawLine(ruling, Offset(size.width * 0.2f, step * 0.75f), Offset(size.width * 0.2f, size.height), strokeWidth = w)
      }
      PageStyle.ISOMETRIC -> {
        val gap = 7.5.dp.toPx()
        var x = 0f
        while (x <= size.width) {
          drawLine(ruling.copy(alpha = 0.7f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.7.dp.toPx())
          x += gap
        }
        var c = -size.width
        while (c <= size.height + size.width) {
          drawLine(ruling.copy(alpha = 0.7f), Offset(0f, c), Offset(size.width, c + size.width * 0.577f), strokeWidth = 0.7.dp.toPx())
          drawLine(ruling.copy(alpha = 0.7f), Offset(0f, c), Offset(size.width, c - size.width * 0.577f), strokeWidth = 0.7.dp.toPx())
          c += gap * 1.155f
        }
      }
      PageStyle.GRID, PageStyle.ENGINEERING, PageStyle.GRAPH -> {
        val cell = if (style == PageStyle.GRAPH) 3.4.dp.toPx() else 6.dp.toPx()
        val heavyEvery =
          when (style) {
            PageStyle.ENGINEERING -> 4
            PageStyle.GRAPH -> 5
            else -> 1
          }
        var i = 0
        var x = 0f
        while (x <= size.width) {
          val heavy = i % heavyEvery == 0
          drawLine(
            color = if (heavy) ruling else minor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = if (heavy) 1.dp.toPx() else 0.6.dp.toPx(),
          )
          x += cell
          i++
        }
        i = 0
        var y = 0f
        while (y <= size.height) {
          val heavy = i % heavyEvery == 0
          drawLine(
            color = if (heavy) ruling else minor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = if (heavy) 1.dp.toPx() else 0.6.dp.toPx(),
          )
          y += cell
          i++
        }
      }
    }
  }
}

// ---- Lasso selection floating action bar (Sketch 2) & Paste menu ------------------------------

/** A registered `SELECTION_NOT_EMPTY` plugin action exposed in the selection `More ▾` menu. */
data class SelectionPluginAction(
  val id: String,
  val label: String,
  val onClick: () -> Unit,
  val iconRes: Int = 0,
)

/**
 * Plugin kinds the Lasso `More ▾` popover knows how to give a fitting icon (LaTeX, diagrams).
 * Nothing is shown for a slot unless a registered plugin provides it.
 */
enum class BuiltInSelectionPluginSlot(
  val id: String,
  val label: String,
  val matchKeywords: List<String>,
) {
  LATEX(
    id = "latex",
    label = "Convert to LaTeX",
    matchKeywords = listOf("latex", "tex", "math", "equation"),
  ),
  DIAGRAMS(
    id = "diagrams",
    label = "Clean up diagram",
    matchKeywords = listOf("diagram", "figure", "geometry", "clean"),
  );

  internal fun matches(action: SelectionPluginAction): Boolean {
    val haystack = "${action.id} ${action.label}".lowercase(Locale.US)
    return matchKeywords.any { it in haystack }
  }
}

/** One entry rendered under the Plugins section of the Lasso `More ▾` dropdown. */
sealed interface SelectionPluginMenuEntry {
  data class Registered(
    val action: SelectionPluginAction,
    val matchedSlot: BuiltInSelectionPluginSlot? = null,
  ) : SelectionPluginMenuEntry
}

/** Pure resolved state for [SelectionActions] (unit-tested without Android runtime). */
data class SelectionBarState(
  val pasteEnabled: Boolean,
  val showFill: Boolean,
  val pluginEntries: List<SelectionPluginMenuEntry>,
)

/**
 * Resolves the enabled state and `More ▾` plugin entries for the Lasso selection bar. Only
 * registered plugins appear (a plugin matching a built-in slot gets that slot's icon); with none
 * installed the list is empty and the Plugins section is not drawn.
 */
internal fun resolveSelectionBarState(
  hasClipboard: Boolean,
  canFillSelection: Boolean,
  registeredPlugins: List<SelectionPluginAction>,
): SelectionBarState {
  val claimedSlots = mutableSetOf<BuiltInSelectionPluginSlot>()
  val entries = buildList {
    for (plugin in registeredPlugins) {
      val slot = BuiltInSelectionPluginSlot.entries.firstOrNull { it !in claimedSlots && it.matches(plugin) }
      if (slot != null) claimedSlots += slot
      add(SelectionPluginMenuEntry.Registered(plugin, matchedSlot = slot))
    }
  }
  return SelectionBarState(
    pasteEnabled = hasClipboard,
    showFill = canFillSelection,
    pluginEntries = entries,
  )
}

/** Pure computed coordinates for placing a floating bar beside a selection rectangle. */
internal data class SelectionBarPlacement(
  val x: Int,
  val y: Int,
  val placedAbove: Boolean,
)

/**
 * Places a floating selection or paste pill centred above the selection box, or flips it below
 * when there is not enough room under the top chrome, always clamped cleanly inside safe bounds.
 */
internal fun placeSelectionBar(
  selectionLeft: Float,
  selectionTop: Float,
  selectionRight: Float,
  selectionBottom: Float,
  barWidth: Int,
  barHeight: Int,
  containerWidth: Int,
  containerHeight: Int,
  gapPx: Int,
  topClearancePx: Int = 0,
  bottomClearancePx: Int = 0,
  leftClearancePx: Int = 0,
  rightClearancePx: Int = 0,
): SelectionBarPlacement {
  val minLeft = maxOf(gapPx, leftClearancePx)
  val maxLeft = maxOf(minLeft, containerWidth - barWidth - maxOf(gapPx, rightClearancePx))
  val centerX = (selectionLeft + selectionRight) / 2f
  val x = (centerX - barWidth / 2f).roundToInt().coerceIn(minLeft, maxLeft)

  val minTop = maxOf(gapPx, topClearancePx)
  val maxTop = maxOf(minTop, containerHeight - barHeight - maxOf(gapPx, bottomClearancePx))
  val aboveY = (selectionTop - gapPx - barHeight).roundToInt()
  val belowY = (selectionBottom + gapPx).roundToInt()

  return if (aboveY >= minTop) {
    SelectionBarPlacement(x = x, y = aboveY.coerceIn(minTop, maxTop), placedAbove = true)
  } else {
    SelectionBarPlacement(x = x, y = belowY.coerceIn(minTop, maxTop), placedAbove = false)
  }
}

/** Whether the floating lone Paste pill should appear while Lasso is selected with clipboard content. */
internal fun shouldShowFloatingPastePill(
  selectionUiVisible: Boolean,
  hasClipboard: Boolean,
  hasSelection: Boolean,
  pasteMenuOpen: Boolean,
  isLassoSelected: Boolean,
): Boolean = selectionUiVisible && hasClipboard && !hasSelection && !pasteMenuOpen && isLassoSelected

/**
 * Floating Lasso selection bar faithful to Sketch 2:
 * `[ Copy | Cut | Paste | Duplicate | Delete | Flip H | Flip V | More ▾ ]`.
 *
 * Uses [PenLabChromePill] with Phosphor Duotone icons, >=48dp touch targets, muted disabled Paste
 * when clipboard is empty (so layout never jumps), destructive error tint on Delete, and an
 * edge-clamped `More ▾` [PenLabPopoverSurface] for Fill, Deselect, and Plugin actions.
 */
@Composable
fun SelectionActions(
  hasClipboard: Boolean,
  canFillSelection: Boolean,
  moreExpanded: Boolean,
  onMoreExpandedChange: (Boolean) -> Unit,
  onCopy: () -> Unit,
  onCut: () -> Unit,
  onPaste: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit,
  onMirror: (sideways: Boolean) -> Unit,
  onFill: () -> Unit,
  onDeselect: () -> Unit,
  pluginActions: List<SelectionPluginAction>,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
) {
  val state =
    remember(hasClipboard, canFillSelection, pluginActions) {
      resolveSelectionBarState(
        hasClipboard = hasClipboard,
        canFillSelection = canFillSelection,
        registeredPlugins = pluginActions,
      )
    }

  PenLabChromePill(
    modifier = modifier.heightIn(min = BarHeight),
    isPalm = isPalm,
    contentPadding = PaddingValues(horizontal = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(0.dp),
  ) {
    SelectionPillAction(
      label = "Copy",
      icon = PenLabIcons.Copy,
      onClick = {
        onMoreExpandedChange(false)
        onCopy()
      },
    )
    SelectionPillAction(
      label = "Cut",
      icon = PenLabIcons.Cut,
      onClick = {
        onMoreExpandedChange(false)
        onCut()
      },
    )
    SelectionPillAction(
      label = "Paste",
      icon = PenLabIcons.Paste,
      enabled = state.pasteEnabled,
      onClick = {
        onMoreExpandedChange(false)
        onPaste()
      },
    )
    SelectionPillAction(
      label = "Duplicate",
      icon = PenLabIcons.Duplicate,
      contentDescription = "Duplicate selection",
      onClick = {
        onMoreExpandedChange(false)
        onDuplicate()
      },
    )
    SelectionPillAction(
      label = "Delete",
      icon = PenLabIcons.Delete,
      destructive = true,
      onClick = {
        onMoreExpandedChange(false)
        onDelete()
      },
    )

    SelectionPillDivider()

    SelectionPillAction(
      label = "Flip H",
      icon = PenLabIcons.FlipHorizontal,
      contentDescription = "Mirror horizontally",
      onClick = {
        onMoreExpandedChange(false)
        onMirror(true)
      },
    )
    SelectionPillAction(
      label = "Flip V",
      icon = PenLabIcons.FlipVertical,
      contentDescription = "Mirror vertically",
      onClick = {
        onMoreExpandedChange(false)
        onMirror(false)
      },
    )

    SelectionPillDivider()

    Box(contentAlignment = Alignment.Center) {
      SelectionMoreTrigger(
        expanded = moreExpanded,
        onClick = { onMoreExpandedChange(!moreExpanded) },
      )
      if (moreExpanded) {
        val density = LocalDensity.current
        val positionProvider =
          remember(density) {
            SelectionMorePopupPositionProvider(
              gapPx = with(density) { 8.dp.roundToPx() },
              marginPx = with(density) { PenLabSpacing.edgeMargin.roundToPx() },
            )
          }
        Popup(
          popupPositionProvider = positionProvider,
          onDismissRequest = { onMoreExpandedChange(false) },
          properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
        ) {
          SelectionMorePopover(
            showFill = state.showFill,
            pluginEntries = state.pluginEntries,
            onFill = {
              onMoreExpandedChange(false)
              onFill()
            },
            onDeselect = {
              onMoreExpandedChange(false)
              onDeselect()
            },
            onRunRegisteredPlugin = { action ->
              onMoreExpandedChange(false)
              action.onClick()
            },
            isPalm = isPalm,
          )
        }
      }
    }
  }
}

@Composable
private fun SelectionMorePopover(
  showFill: Boolean,
  pluginEntries: List<SelectionPluginMenuEntry>,
  onFill: () -> Unit,
  onDeselect: () -> Unit,
  onRunRegisteredPlugin: (SelectionPluginAction) -> Unit,
  isPalm: () -> Boolean,
) {
  val scheme = MaterialTheme.colorScheme
  PenLabPopoverSurface(
    isPalm = isPalm,
    modifier = Modifier.width(284.dp).heightIn(max = 460.dp),
    contentPadding = PaddingValues(vertical = PenLabSpacing.sm, horizontal = PenLabSpacing.sm),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Column(
      modifier = Modifier.verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      if (showFill) {
        PenLabMenuItem(
          label = "Fill selection",
          icon = PenLabIcons.Fill,
          onClick = onFill,
        )
      }
      PenLabMenuItem(
        label = "Deselect",
        icon = PenLabIcons.Close,
        onClick = onDeselect,
      )

      if (pluginEntries.isNotEmpty()) {
        HorizontalDivider(
          color = scheme.outlineVariant,
          thickness = 1.dp,
          modifier = Modifier.padding(vertical = PenLabSpacing.xs, horizontal = PenLabSpacing.xs),
        )

        Text(
          text = "Plugins",
          color = scheme.onSurfaceVariant,
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.padding(horizontal = PenLabSpacing.md, vertical = PenLabSpacing.xs),
        )

        for (entry in pluginEntries) {
          when (entry) {
            is SelectionPluginMenuEntry.Registered -> {
              val action = entry.action
              if (action.iconRes != 0) {
                Row(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .heightIn(min = PenLabSpacing.touchTarget)
                      .clip(PenLabShapes.small)
                      .clickable(role = Role.Button) { onRunRegisteredPlugin(action) }
                      .padding(horizontal = PenLabSpacing.md, vertical = PenLabSpacing.sm),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    painter = painterResource(action.iconRes),
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(PenLabLayout.iconMedium),
                  )
                  Spacer(Modifier.width(PenLabSpacing.md))
                  Text(
                    text = action.label,
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              } else {
                val icon =
                  when (entry.matchedSlot) {
                    BuiltInSelectionPluginSlot.LATEX -> PenLabIcons.Function
                    BuiltInSelectionPluginSlot.DIAGRAMS -> PenLabIcons.Shapes
                    null -> PenLabIcons.Sparkles
                  }
                PenLabMenuItem(
                  label = action.label,
                  icon = icon,
                  onClick = { onRunRegisteredPlugin(action) },
                )
              }
            }
          }
        }
      }
    }
  }
}

private class SelectionMorePopupPositionProvider(
  private val gapPx: Int,
  private val marginPx: Int,
) : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
  ): IntOffset {
    val maxX = maxOf(marginPx, windowSize.width - popupContentSize.width - marginPx)
    val preferredX = anchorBounds.right - popupContentSize.width
    val x = preferredX.coerceIn(marginPx, maxX)

    val belowY = anchorBounds.bottom + gapPx
    val aboveY = anchorBounds.top - gapPx - popupContentSize.height
    val maxY = maxOf(marginPx, windowSize.height - popupContentSize.height - marginPx)
    val y =
      when {
        belowY + popupContentSize.height <= windowSize.height - marginPx -> belowY
        aboveY >= marginPx -> aboveY
        else -> belowY.coerceIn(marginPx, maxY)
      }
    return IntOffset(x, y)
  }
}

@Composable
private fun SelectionPillAction(
  label: String,
  icon: ImageVector,
  onClick: () -> Unit,
  enabled: Boolean = true,
  destructive: Boolean = false,
  contentDescription: String = label,
) {
  val scheme = MaterialTheme.colorScheme
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val pressScale by animateFloatAsState(
    targetValue = if (pressed && enabled) 0.94f else 1f,
    animationSpec = Motion.spring(dampingRatio = 0.85f, stiffness = 700f),
    label = "selectionActionPress",
  )
  val tint =
    when {
      !enabled -> PenLabComponentTokens.toolIconDisabled
      destructive -> PenLabComponentTokens.selectionBarDestructive
      else -> scheme.onSurface
    }
  val iconTint =
    when {
      !enabled -> PenLabComponentTokens.toolIconDisabled
      destructive -> PenLabComponentTokens.selectionBarDestructive
      else -> scheme.onSurfaceVariant
    }

  Row(
    modifier =
      Modifier
        .heightIn(min = BarHeight)
        .widthIn(min = PenLabSpacing.touchTarget)
        .insetPillClickable(interaction, onClick, enabled = enabled)
        .padding(horizontal = 9.dp)
        .semantics { this.contentDescription = contentDescription }
        .graphicsLayer {
          scaleX = pressScale
          scaleY = pressScale
        },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = iconTint,
      modifier = Modifier.size(17.dp),
    )
    Text(
      text = label,
      color = tint,
      style =
        MaterialTheme.typography.labelLarge.copy(
          fontWeight = if (destructive) FontWeight.SemiBold else FontWeight.Medium,
        ),
      maxLines = 1,
      softWrap = false,
    )
  }
}

@Composable
private fun SelectionMoreTrigger(
  expanded: Boolean,
  onClick: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val pressScale by animateFloatAsState(
    targetValue = if (pressed) 0.94f else 1f,
    animationSpec = Motion.spring(dampingRatio = 0.85f, stiffness = 700f),
    label = "selectionMorePress",
  )
  val chevronRotation by animateFloatAsState(
    targetValue = if (expanded) 180f else 0f,
    animationSpec = Motion.tween(140),
    label = "selectionMoreChevron",
  )
  val bg by animateColorAsState(
    targetValue = if (expanded) PenLabComponentTokens.toolCellSelectedBg else Color.Transparent,
    animationSpec = Motion.tween(120),
    label = "selectionMoreBg",
  )
  val fg = if (expanded) scheme.onPrimaryContainer else scheme.onSurface

  Row(
    modifier =
      Modifier
        .heightIn(min = BarHeight)
        .widthIn(min = PenLabSpacing.touchTarget)
        .insetPillClickable(interaction, onClick, background = bg)
        .padding(horizontal = 10.dp)
        .semantics {
          contentDescription = "More selection actions"
          if (expanded) selected = true
        }
        .graphicsLayer {
          scaleX = pressScale
          scaleY = pressScale
        },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.xs, Alignment.CenterHorizontally),
  ) {
    Text(
      text = "More",
      color = fg,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
      softWrap = false,
    )
    Icon(
      imageVector = PenLabIcons.ChevronDown,
      contentDescription = null,
      tint = if (expanded) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
      modifier =
        Modifier
          .size(14.dp)
          .graphicsLayer { rotationZ = chevronRotation },
    )
  }
}

@Composable
private fun SelectionPillDivider() {
  Box(
    modifier = Modifier.width(6.dp).height(BarHeight),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier
        .size(width = 1.dp, height = 20.dp)
        .background(MaterialTheme.colorScheme.outlineVariant),
    )
  }
}

/**
 * Floating Paste pill shown both at a 500ms hold-to-paste point on the page and as the lone
 * clipboard Paste pill when Lasso is picked with items on the clipboard.
 */
@Composable
fun PasteMenu(
  onPaste: () -> Unit,
  onClear: () -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
) {
  PenLabChromePill(
    modifier = modifier.heightIn(min = BarHeight),
    isPalm = isPalm,
    contentPadding = PaddingValues(horizontal = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(0.dp),
  ) {
    SelectionPillAction(
      label = "Paste",
      icon = PenLabIcons.Paste,
      onClick = onPaste,
    )
    SelectionPillDivider()
    PenLabIconButton(
      icon = PenLabIcons.Close,
      contentDescription = "Clear clipboard",
      iconSize = PenLabLayout.iconCompact,
      onClick = onClear,
    )
  }
}

/** One entry of the top bar's "more" menu. */
class MenuAction(
  val icon: androidx.compose.ui.graphics.vector.ImageVector,
  val label: String,
  val onClick: () -> Unit,
  /** A drawable resource shown instead of [icon] (plugins bring their own); 0 = use [icon]. */
  val iconRes: Int = 0,
)

// ---- Layers popover ---------------------------------------------------------------------------

/**
 * The layers, top layer first (as they stack). Tap a layer to draw on it; eye toggles visibility;
 * pencil renames it; trash deletes it with its ink (undo brings it back).
 */
@Composable
fun LayersPanel(
  names: List<String>,
  hidden: Set<Int>,
  current: Int,
  onSelect: (Int) -> Unit,
  onVisible: (Int, Boolean) -> Unit,
  onRename: (Int) -> Unit,
  onDelete: (Int) -> Unit,
  onAdd: () -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
) {
  PenLabPopoverSurface(
    isPalm = isPalm,
    modifier = modifier.width(320.dp).heightIn(max = 520.dp),
    contentPadding = PaddingValues(PenLabSpacing.md),
    verticalArrangement = Arrangement.Top,
  ) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
      LayersPanelContent(
        names = names,
        hidden = hidden,
        current = current,
        onSelect = onSelect,
        onVisible = onVisible,
        onRename = onRename,
        onDelete = onDelete,
        onAdd = onAdd,
      )
    }
  }
}

/**
 * Embeddable layers list shared by [LayersPanel] and [PageConfigPopover].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LayersPanelContent(
  names: List<String>,
  hidden: Set<Int>,
  current: Int,
  onSelect: (Int) -> Unit,
  onVisible: (Int, Boolean) -> Unit,
  onRename: (Int) -> Unit,
  onDelete: (Int) -> Unit,
  onAdd: () -> Unit,
  showHeading: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (showHeading) {
      Text(
        text = "Layers",
        color = scheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(start = PenLabSpacing.xs, bottom = 2.dp),
      )
    }
    for (index in names.indices.reversed()) {
      val isCurrent = index == current
      val shown = index !in hidden
      val bg = if (isCurrent) PenLabComponentTokens.chipSelectedBg else PenLabComponentTokens.chipIdleBg
      val nameColor =
        when {
          !shown -> PenLabComponentTokens.toolIconDisabled
          isCurrent -> PenLabComponentTokens.chipSelectedContent
          else -> scheme.onSurface
        }
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(PenLabShapes.small)
            .background(bg)
            .then(
              if (isCurrent) Modifier.border(1.5.dp, scheme.primary, PenLabShapes.small)
              else Modifier
            )
            .combinedClickable(
              onClick = { onSelect(index) },
              onLongClick = { onRename(index) },
            )
            .padding(end = PenLabSpacing.xs)
            .semantics {
              contentDescription = "${names[index]}${if (isCurrent) ", active layer" else ""}"
              if (isCurrent) selected = true
            },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        PenLabIconButton(
          icon = if (shown) PenLabIcons.Eye else PenLabIcons.EyeOff,
          contentDescription = if (shown) "Hide ${names[index]}" else "Show ${names[index]}",
          iconSize = PenLabLayout.iconMedium,
          tint =
            when {
              !shown -> PenLabComponentTokens.toolIconDisabled
              isCurrent -> PenLabComponentTokens.chipSelectedContent
              else -> scheme.onSurface
            },
          onClick = { onVisible(index, !shown) },
        )
        if (isCurrent) {
          Box(
            Modifier
              .size(7.dp)
              .clip(CircleShape)
              .background(scheme.primary),
          )
          Spacer(Modifier.width(PenLabSpacing.sm))
        }
        Text(
          text = names[index],
          color = nameColor,
          style =
            MaterialTheme.typography.labelLarge.copy(
              fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
            ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        PenLabIconButton(
          icon = PenLabIcons.Rename,
          contentDescription = "Rename ${names[index]}",
          iconSize = PenLabLayout.iconCompact,
          tint = if (isCurrent) PenLabComponentTokens.chipSelectedContent else scheme.onSurfaceVariant,
          onClick = { onRename(index) },
        )
        if (names.size > 1) {
          PenLabIconButton(
            icon = PenLabIcons.Delete,
            contentDescription = "Delete ${names[index]} and its ink",
            iconSize = PenLabLayout.iconCompact,
            tint = scheme.error,
            onClick = { onDelete(index) },
          )
        }
      }
    }

    Spacer(Modifier.height(2.dp))
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .heightIn(min = PenLabSpacing.touchTarget)
          .clip(PenLabShapes.small)
          // Outlined, not tinted: the tinted fill is what marks the selected layer row above.
          .border(PenLabLayout.hairline, scheme.outline, PenLabShapes.small)
          .clickable(role = Role.Button, onClick = onAdd)
          .padding(horizontal = PenLabSpacing.md)
          .semantics { contentDescription = "Add new layer" },
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = PenLabIcons.Plus,
        contentDescription = null,
        tint = scheme.onSurfaceVariant,
        modifier = Modifier.size(PenLabLayout.iconCompact),
      )
      Spacer(Modifier.width(PenLabSpacing.sm))
      Text(
        text = "New layer",
        color = scheme.onSurface,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
      )
    }
  }
}
