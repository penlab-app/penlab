package dev.penlab.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import dev.penlab.app.ui.ignorePalm
import dev.penlab.app.ui.theme.LocalPenLabColors
import dev.penlab.app.ui.theme.PenLabComponentTokens
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
  iconSize: Dp = 22.dp,
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
 * highlight, >=48dp touch height per option, and smooth transitions respecting [Motion.speed].
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
        targetValue = if (isSelected) PenLabComponentTokens.chipSelectedBg else Color.Transparent,
        animationSpec = Motion.tween(140),
        label = "segmentBg",
      )
      val fgColor by animateColorAsState(
        targetValue =
          when {
            !enabled -> PenLabComponentTokens.toolIconDisabled
            isSelected -> PenLabComponentTokens.chipSelectedContent
            else -> scheme.onSurfaceVariant
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
              modifier = Modifier.size(18.dp),
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
