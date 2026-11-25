package dev.penlab.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.penlab.app.ui.PenLabIcons
import dev.penlab.app.ui.theme.LocalPenLabColors
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.PenLabShapes
import dev.penlab.app.ui.theme.PenLabSpacing
import dev.penlab.app.ui.theme.PenLabTextStyles
import dev.penlab.app.ui.theme.floatingChromeSurface
import dev.penlab.app.ui.theme.softShadow

/**
 * Consistent menu row: optional Phosphor Duotone leading icon, clear sans label, optional
 * subtitle or trailing badge/chevron, optional [destructive] error tint, and >=48dp touch height.
 */
@Composable
fun PenLabMenuItem(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  subtitle: String? = null,
  trailingText: String? = null,
  showChevron: Boolean = false,
  destructive: Boolean = false,
  enabled: Boolean = true,
  trailingContent: (@Composable () -> Unit)? = null,
) {
  val scheme = MaterialTheme.colorScheme
  val primaryTint =
    when {
      !enabled -> PenLabComponentTokens.toolIconDisabled
      destructive -> scheme.error
      else -> scheme.onSurface
    }
  val secondaryTint =
    when {
      !enabled -> PenLabComponentTokens.toolIconDisabled
      destructive -> scheme.error.copy(alpha = 0.8f)
      else -> scheme.onSurfaceVariant
    }

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = PenLabSpacing.touchTarget)
        .clip(PenLabShapes.small)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = PenLabSpacing.md, vertical = PenLabSpacing.sm),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (destructive || !enabled) primaryTint else scheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
      Spacer(Modifier.width(PenLabSpacing.md))
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = label,
        color = primaryTint,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          color = secondaryTint,
          style = MaterialTheme.typography.bodySmall,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (trailingText != null) {
      Spacer(Modifier.width(PenLabSpacing.sm))
      Text(
        text = trailingText,
        color = secondaryTint,
        style = PenLabTextStyles.tabularMedium,
        maxLines = 1,
      )
    }
    if (trailingContent != null) {
      Spacer(Modifier.width(PenLabSpacing.sm))
      trailingContent()
    }
    if (showChevron) {
      Spacer(Modifier.width(PenLabSpacing.xs))
      Icon(
        imageVector = PenLabIcons.ChevronRight,
        contentDescription = null,
        tint = secondaryTint,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

/**
 * Clean section heading with more space above than below. No kickers, eyebrows, or section numbers.
 */
@Composable
fun PenLabSectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  topPadding: Dp = PenLabSpacing.lg,
  bottomPadding: Dp = PenLabSpacing.sm,
) {
  Text(
    text = title,
    color = MaterialTheme.colorScheme.onSurface,
    style = MaterialTheme.typography.titleMedium,
    modifier = modifier.padding(top = topPadding, bottom = bottomPadding),
  )
}

/**
 * Full-width switch row with >=48dp touch height, optional Phosphor Duotone leading icon and
 * supporting subtitle, and a Material 3 [Switch] tinted with [MaterialTheme.colorScheme.primary] /
 * [MaterialTheme.colorScheme.onPrimary] when checked.
 */
@Composable
fun PenLabSwitchRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  subtitle: String? = null,
  enabled: Boolean = true,
) {
  val scheme = MaterialTheme.colorScheme
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = PenLabSpacing.touchTarget)
        .clip(PenLabShapes.small)
        .toggleable(
          value = checked,
          enabled = enabled,
          role = Role.Switch,
          onValueChange = onCheckedChange,
        )
        .padding(horizontal = PenLabSpacing.xs, vertical = PenLabSpacing.xs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint =
          when {
            !enabled -> PenLabComponentTokens.toolIconDisabled
            checked -> scheme.primary
            else -> scheme.onSurfaceVariant
          },
        modifier = Modifier.size(22.dp),
      )
      Spacer(Modifier.width(PenLabSpacing.md))
    }
    Column(
      modifier = Modifier.weight(1f).padding(end = PenLabSpacing.md),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        color = if (enabled) scheme.onSurface else PenLabComponentTokens.toolIconDisabled,
        style = MaterialTheme.typography.bodyLarge,
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          color = if (enabled) scheme.onSurfaceVariant else PenLabComponentTokens.toolIconDisabled,
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
    Switch(
      checked = checked,
      onCheckedChange = null,
      enabled = enabled,
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = scheme.onPrimary,
          checkedTrackColor = scheme.primary,
          checkedBorderColor = scheme.primary,
          uncheckedThumbColor = scheme.outline,
          uncheckedTrackColor = scheme.surfaceContainerHighest,
          uncheckedBorderColor = scheme.outline,
          disabledCheckedThumbColor = scheme.surface,
          disabledCheckedTrackColor = scheme.onSurface.copy(alpha = 0.24f),
          disabledUncheckedThumbColor = scheme.onSurface.copy(alpha = 0.38f),
          disabledUncheckedTrackColor = scheme.surfaceContainerHighest.copy(alpha = 0.5f),
          disabledUncheckedBorderColor = scheme.outlineVariant,
        ),
    )
  }
}

/**
 * Slider control with title, tabular value readout ([PenLabTextStyles.tabularLabel]), primary-tinted
 * Material 3 [Slider], and optional editable favourite chips (tap to apply, long-press to remove,
 * plus button to save current value).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PenLabSliderRow(
  title: String,
  valueText: String,
  value: Float,
  onValueChange: (Float) -> Unit,
  valueRange: ClosedFloatingPointRange<Float>,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  leadingPreview: (@Composable () -> Unit)? = null,
  favourites: List<Float> = emptyList(),
  favouriteLabel: ((Float) -> String)? = null,
  isFavouriteSelected: ((Float) -> Boolean)? = null,
  onSelectFavourite: ((Float) -> Unit)? = null,
  onRemoveFavourite: ((Float) -> Unit)? = null,
  onAddFavourite: (() -> Unit)? = null,
  addFavouriteDescription: String = "Save favourite",
) {
  val scheme = MaterialTheme.colorScheme
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PenLabSpacing.xs)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (leadingPreview != null) {
        leadingPreview()
        Spacer(Modifier.width(PenLabSpacing.sm))
      }
      Text(
        text = title,
        color = scheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = valueText,
        color = scheme.onSurface,
        style = PenLabTextStyles.tabularLabel,
      )
    }
    Slider(
      value = value.coerceIn(valueRange),
      onValueChange = onValueChange,
      valueRange = valueRange,
      enabled = enabled,
      colors =
        SliderDefaults.colors(
          thumbColor = scheme.primary,
          activeTrackColor = scheme.primary,
          inactiveTrackColor = scheme.outlineVariant,
        ),
      modifier = Modifier.fillMaxWidth().semantics { contentDescription = title },
    )
    if (favourites.isNotEmpty() || onAddFavourite != null) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        for (fav in favourites) {
          val selected = isFavouriteSelected?.invoke(fav) == true
          val chipText = favouriteLabel?.invoke(fav) ?: "%.1f".format(fav)
          Box(
            modifier =
              Modifier
                .heightIn(min = PenLabSpacing.touchTarget)
                .clip(PenLabShapes.small)
                .background(if (selected) PenLabComponentTokens.chipSelectedBg else PenLabComponentTokens.chipIdleBg)
                .then(
                  if (selected) Modifier.border(1.5.dp, scheme.primary, PenLabShapes.small)
                  else Modifier
                )
                .combinedClickable(
                  enabled = enabled,
                  onClick = { onSelectFavourite?.invoke(fav) },
                  onLongClick = { onRemoveFavourite?.invoke(fav) },
                )
                .padding(horizontal = PenLabSpacing.md, vertical = PenLabSpacing.sm)
                .semantics { contentDescription = "$chipText. Long press to remove." },
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = chipText,
              color = if (selected) PenLabComponentTokens.chipSelectedContent else scheme.onSurface,
              style = PenLabTextStyles.tabularMedium,
            )
          }
        }
        if (onAddFavourite != null) {
          Box(
            modifier =
              Modifier
                .size(PenLabSpacing.touchTarget)
                .clip(PenLabShapes.small)
                .background(PenLabComponentTokens.chipIdleBg)
                .clickable(enabled = enabled, onClick = onAddFavourite)
                .semantics { contentDescription = addFavouriteDescription },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = PenLabIcons.Plus,
              contentDescription = null,
              tint = scheme.onSurface,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}

/**
 * Polished Material 3 dialog container using [PenLabShapes.extraLarge] (24dp), Bricolage Grotesque
 * title, scrollable body slot, and consistent Secondary + Primary/Destructive action row.
 */
@Composable
fun PenLabDialog(
  title: String,
  onDismiss: () -> Unit,
  primaryLabel: String,
  onPrimaryClick: () -> Unit,
  modifier: Modifier = Modifier,
  primaryEnabled: Boolean = true,
  destructive: Boolean = false,
  secondaryLabel: String? = "Cancel",
  onSecondaryClick: (() -> Unit)? = onDismiss,
  subtitle: String? = null,
  body: @Composable ColumnScope.() -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    Surface(
      modifier =
        modifier
          .imePadding()
          .padding(horizontal = PenLabSpacing.xl, vertical = 20.dp)
          .widthIn(min = 320.dp, max = 520.dp)
          .heightIn(max = 660.dp)
          .fillMaxWidth()
          .softShadow(PenLabShapes.extraLarge, elevation = 16.dp),
      shape = PenLabShapes.extraLarge,
      color = PenLabComponentTokens.dialogBg,
      contentColor = scheme.onSurface,
      tonalElevation = 0.dp,
    ) {
      Column(modifier = Modifier.padding(PenLabSpacing.xl)) {
        Text(
          text = title,
          color = scheme.onSurface,
          style = MaterialTheme.typography.headlineSmall,
        )
        if (subtitle != null) {
          Spacer(Modifier.height(PenLabSpacing.xs))
          Text(
            text = subtitle,
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        Spacer(Modifier.height(PenLabSpacing.lg))
        Column(
          modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(PenLabSpacing.md),
          content = body,
        )
        Spacer(Modifier.height(PenLabSpacing.xl))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (secondaryLabel != null && onSecondaryClick != null) {
            TextButton(
              onClick = onSecondaryClick,
              modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
            ) {
              Text(
                text = secondaryLabel,
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
              )
            }
            Spacer(Modifier.width(PenLabSpacing.sm))
          }
          Button(
            onClick = onPrimaryClick,
            enabled = primaryEnabled,
            shape = PenLabShapes.pill,
            colors =
              if (destructive) {
                ButtonDefaults.buttonColors(
                  containerColor = scheme.error,
                  contentColor = scheme.onError,
                )
              } else {
                ButtonDefaults.buttonColors(
                  containerColor = scheme.primary,
                  contentColor = scheme.onPrimary,
                )
              },
            modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
            contentPadding = PaddingValues(horizontal = PenLabSpacing.lg, vertical = PenLabSpacing.sm),
          ) {
            Text(text = primaryLabel, style = MaterialTheme.typography.labelLarge)
          }
        }
      }
    }
  }
}

/**
 * Floating inverse-surface toast with crisp contrast in both Light and Dark themes and an
 * optional accent action button (e.g. "Undo").
 */
@Composable
fun PenLabSnackbar(
  message: String,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  val isDark = LocalPenLabColors.current.isDark
  Row(
    modifier =
      modifier
        .floatingChromeSurface(
          shape = PenLabShapes.medium,
          background = PenLabComponentTokens.snackbarBg,
          elevation = 10.dp,
          subtleLightHairline = false,
          isDark = isDark,
        )
        .heightIn(min = PenLabSpacing.touchTarget)
        .padding(start = PenLabSpacing.lg, end = if (actionLabel != null) PenLabSpacing.xs else PenLabSpacing.lg),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = message,
      color = PenLabComponentTokens.snackbarContent,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(vertical = PenLabSpacing.md),
    )
    if (actionLabel != null && onAction != null) {
      Spacer(Modifier.width(PenLabSpacing.sm))
      TextButton(
        onClick = onAction,
        modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
      ) {
        Text(
          text = actionLabel,
          color = PenLabComponentTokens.snackbarAction,
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}

/**
 * Calm, centered empty state with a soft tonal Phosphor Duotone icon container, Bricolage
 * Grotesque heading, concise description, primary pill CTA, and optional secondary action.
 */
@Composable
fun PenLabEmptyState(
  icon: ImageVector,
  title: String,
  description: String,
  modifier: Modifier = Modifier,
  primaryActionLabel: String? = null,
  primaryActionIcon: ImageVector? = null,
  onPrimaryAction: (() -> Unit)? = null,
  secondaryActionLabel: String? = null,
  onSecondaryAction: (() -> Unit)? = null,
) {
  val scheme = MaterialTheme.colorScheme
  Column(
    modifier = modifier.padding(PenLabSpacing.xl).widthIn(max = 400.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(
      modifier =
        Modifier
          .size(64.dp)
          .clip(PenLabShapes.large)
          .background(scheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = scheme.onPrimaryContainer,
        modifier = Modifier.size(32.dp),
      )
    }
    Spacer(Modifier.height(PenLabSpacing.lg))
    Text(
      text = title,
      color = scheme.onSurface,
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(PenLabSpacing.sm))
    Text(
      text = description,
      color = scheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
    )
    if (primaryActionLabel != null && onPrimaryAction != null) {
      Spacer(Modifier.height(PenLabSpacing.xl))
      Row(
        horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Button(
          onClick = onPrimaryAction,
          shape = PenLabShapes.pill,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = scheme.primary,
              contentColor = scheme.onPrimary,
            ),
          modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
          contentPadding = PaddingValues(horizontal = PenLabSpacing.xl, vertical = PenLabSpacing.sm),
        ) {
          if (primaryActionIcon != null) {
            Icon(
              imageVector = primaryActionIcon,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(PenLabSpacing.sm))
          }
          Text(text = primaryActionLabel, style = MaterialTheme.typography.labelLarge)
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
          OutlinedButton(
            onClick = onSecondaryAction,
            shape = PenLabShapes.pill,
            modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
            contentPadding = PaddingValues(horizontal = PenLabSpacing.lg, vertical = PenLabSpacing.sm),
          ) {
            Text(
              text = secondaryActionLabel,
              color = scheme.onSurface,
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }
      }
    }
  }
}
