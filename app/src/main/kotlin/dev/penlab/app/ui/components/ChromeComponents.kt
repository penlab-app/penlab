package dev.penlab.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.penlab.app.ui.Motion
import dev.penlab.app.ui.PenLabIcons
import dev.penlab.app.ui.ignorePalm
import dev.penlab.app.ui.theme.LocalPenLabColors
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.PenLabLayout
import dev.penlab.app.ui.theme.PenLabShapes
import dev.penlab.app.ui.theme.PenLabSpacing
import dev.penlab.app.ui.theme.floatingChromeSurface

/**
 * Floating island container for top-bar pills and floating canvas chrome.
 *
 * Enforces the single-depth-cue rule via [floatingChromeSurface] (soft drop shadow plus a
 * 0.5dp boundary on light theme where white chrome may sit over white paper) and applies
 * [ignorePalm] so the writing hand never triggers accidental taps.
 */
@Composable
fun PenLabChromePill(
  modifier: Modifier = Modifier,
  isPalm: () -> Boolean = { false },
  shape: Shape = PenLabShapes.pill,
  elevation: Dp = 8.dp,
  contentPadding: PaddingValues = PaddingValues(horizontal = PenLabSpacing.xs),
  horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(PenLabSpacing.xs),
  content: @Composable RowScope.() -> Unit,
) {
  val isDark = LocalPenLabColors.current.isDark
  Row(
    modifier =
      modifier
        .floatingChromeSurface(
          shape = shape,
          background = PenLabComponentTokens.chromePillBg,
          elevation = elevation,
          subtleLightHairline = true,
          borderColor = PenLabComponentTokens.chromePillBorder,
          isDark = isDark,
        )
        .ignorePalm(isPalm)
        .heightIn(min = PenLabSpacing.touchTarget)
        .padding(contentPadding),
    horizontalArrangement = horizontalArrangement,
    verticalAlignment = Alignment.CenterVertically,
    content = content,
  )
}

/**
 * A tappable pill inside a [BarHeight]-tall row whose **hit area is the full bar height** while the
 * visible shape (background and pressed ripple) stays inset by [PenLabSpacing.xs] top and bottom.
 *
 * Chain order is the point: the (invisible) clickable comes first so it spans the whole height; then
 * the inset, clip, background and a ripple driven by the same [interactionSource]. Callers add their
 * own horizontal padding, semantics and press scale after this modifier, and a `widthIn(min = 48.dp)`
 * before it when the content could be narrower than a touch target.
 */
@Composable
fun Modifier.insetPillClickable(
  interactionSource: MutableInteractionSource,
  onClick: () -> Unit,
  background: Color = Color.Transparent,
  enabled: Boolean = true,
): Modifier =
  this
    .clickable(
      enabled = enabled,
      interactionSource = interactionSource,
      indication = null,
      role = Role.Button,
      onClick = onClick,
    )
    .padding(vertical = PenLabSpacing.xs)
    .clip(PenLabShapes.pill)
    .background(background)
    .indication(interactionSource, ripple(bounded = true))

/**
 * Ergonomic 48dp Phosphor Duotone icon button with crisp press scale feedback (read in
 * [graphicsLayer] so animation frames never recompose), tonal [MaterialTheme.colorScheme.primaryContainer]
 * highlight when [selected], and semantic accessibility metadata.
 */
@Composable
fun PenLabIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  enabled: Boolean = true,
  iconSize: Dp = PenLabLayout.iconDefault,
  tint: Color? = null,
  isPalm: (() -> Boolean)? = null,
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val pressScale by animateFloatAsState(
    targetValue = if (pressed && enabled) 0.94f else 1f,
    animationSpec = Motion.spring(dampingRatio = 0.85f, stiffness = 700f),
    label = "iconButtonPress",
  )
  val containerBg by animateColorAsState(
    targetValue = if (selected) PenLabComponentTokens.toolCellSelectedBg else Color.Transparent,
    animationSpec = Motion.tween(120),
    label = "iconButtonBg",
  )
  val resolvedTint =
    when {
      !enabled -> PenLabComponentTokens.toolIconDisabled
      tint != null -> tint
      selected -> PenLabComponentTokens.toolIconActive
      else -> PenLabComponentTokens.toolIconDefault
    }

  val baseModifier =
    if (isPalm != null) modifier.ignorePalm(isPalm) else modifier

  Box(
    modifier =
      baseModifier
        .size(PenLabSpacing.touchTarget)
        .clip(PenLabShapes.pill)
        .clickable(
          enabled = enabled,
          interactionSource = interaction,
          indication = ripple(bounded = true, radius = 24.dp),
          role = Role.Button,
          onClick = onClick,
        )
        .semantics {
          this.contentDescription = contentDescription
          if (selected) this.selected = true
        }
        .graphicsLayer {
          scaleX = pressScale
          scaleY = pressScale
        },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier
          .size(40.dp)
          .clip(PenLabShapes.pill)
          .background(containerBg),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = resolvedTint,
        modifier = Modifier.size(iconSize),
      )
    }
  }
}

/**
 * Crisp segmented control with optional leading Phosphor Duotone icon per segment, tonal active
 * highlight (solid primary, so the chosen option reads at a glance), >=48dp touch height per option, and smooth transitions respecting [Motion.speed].
 */
@Composable
fun <T> PenLabSegmentedControl(
  options: List<T>,
  selected: T,
  onSelect: (T) -> Unit,
  label: (T) -> String,
  modifier: Modifier = Modifier,
  icon: ((T) -> ImageVector?)? = null,
  enabled: Boolean = true,
) {
  val scheme = MaterialTheme.colorScheme
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(PenLabShapes.small)
        .background(PenLabComponentTokens.chipIdleBg)
        .padding(3.dp)
        .selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(3.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    for (option in options) {
      val isSelected = option == selected
      val optionLabel = label(option)
      val optionIcon = icon?.invoke(option)
      val interaction = remember(option) { MutableInteractionSource() }
      val pressed by interaction.collectIsPressedAsState()
      val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = Motion.spring(dampingRatio = 0.85f, stiffness = 700f),
        label = "segmentPress",
      )
      val bgColor by animateColorAsState(
        targetValue =
          when {
            !isSelected -> Color.Transparent
            // A disabled control keeps its choice readable: a quiet neutral pill, not primary-on-primary.
            !enabled -> scheme.outlineVariant
            else -> PenLabComponentTokens.segmentSelectedBg
          },
        animationSpec = Motion.tween(140),
        label = "segmentBg",
      )
      val fgColor by animateColorAsState(
        targetValue =
          when {
            !enabled && isSelected -> scheme.onSurface
            !enabled -> PenLabComponentTokens.toolIconDisabled
            isSelected -> PenLabComponentTokens.segmentSelectedContent
            else -> scheme.onSurface
          },
        animationSpec = Motion.tween(140),
        label = "segmentFg",
      )

      Box(
        modifier =
          Modifier
            .weight(1f)
            .heightIn(min = PenLabSpacing.touchTarget)
            .clip(PenLabShapes.small)
            .selectable(
              selected = isSelected,
              enabled = enabled,
              interactionSource = interaction,
              indication = ripple(bounded = true),
              role = Role.RadioButton,
              onClick = { onSelect(option) },
            )
            .padding(vertical = 2.dp)
            .graphicsLayer {
              scaleX = pressScale
              scaleY = pressScale
            }
            .clip(PenLabShapes.small)
            .background(bgColor),
        contentAlignment = Alignment.Center,
      ) {
        Row(
          modifier = Modifier.padding(horizontal = PenLabSpacing.sm, vertical = PenLabSpacing.xs),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          if (optionIcon != null) {
            Icon(
              imageVector = optionIcon,
              contentDescription = null,
              tint = fgColor,
              modifier = Modifier.size(PenLabLayout.iconCompact),
            )
            Spacer(Modifier.width(6.dp))
          }
          Text(
            text = optionLabel,
            color = fgColor,
            style =
              MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
              ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/**
 * Anchored Level 3 popover container for tool panels, paper config, search, and view readouts.
 * Uses [PenLabShapes.large] (18dp), single depth cue via [floatingChromeSurface], and [ignorePalm].
 */
@Composable
fun PenLabPopoverSurface(
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(PenLabSpacing.md),
  verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(PenLabSpacing.sm),
  content: @Composable ColumnScope.() -> Unit,
) {
  val isDark = LocalPenLabColors.current.isDark
  Column(
    modifier =
      modifier
        .floatingChromeSurface(
          shape = PenLabShapes.large,
          background = PenLabComponentTokens.panelBg,
          elevation = 14.dp,
          subtleLightHairline = true,
          borderColor = PenLabComponentTokens.panelBorder,
          isDark = isDark,
        )
        .ignorePalm(isPalm)
        .padding(contentPadding),
    verticalArrangement = verticalArrangement,
    content = content,
  )
}

/**
 * A choice chip for "pick one of these places" lists (Move to folder). The chosen one says so
 * three ways, so it never rests on fill colour alone: tinted fill, a primary outline ring and a
 * leading check. Meant to sit in a `selectableGroup`.
 */
@Composable
fun PenLabChoiceChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  Row(
    modifier =
      modifier
        .heightIn(min = PenLabSpacing.touchTarget)
        .clip(PenLabShapes.small)
        .background(if (selected) PenLabComponentTokens.chipSelectedBg else PenLabComponentTokens.chipIdleBg)
        .then(if (selected) Modifier.border(PenLabLayout.ringSelected, scheme.primary, PenLabShapes.small) else Modifier)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = PenLabSpacing.lg, vertical = PenLabSpacing.sm),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (selected) {
      Icon(
        imageVector = PenLabIcons.CheckBare,
        contentDescription = null,
        tint = PenLabComponentTokens.chipSelectedContent,
        modifier = Modifier.size(PenLabLayout.iconSmall),
      )
      Spacer(Modifier.width(PenLabSpacing.sm))
    }
    Text(
      text = label,
      color = if (selected) PenLabComponentTokens.chipSelectedContent else scheme.onSurface,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
