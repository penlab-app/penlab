package dev.penlab.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.penlab.app.ui.components.PenLabMenuItem
import dev.penlab.app.ui.components.PenLabPopoverSurface
import dev.penlab.app.ui.components.PenLabSwitchRow
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.PenLabLayout
import dev.penlab.app.ui.theme.PenLabShapes
import dev.penlab.app.ui.theme.PenLabSpacing
import dev.penlab.app.ui.theme.PenLabTextStyles

/**
 * Opens under the readout: a row each for zoom and rotation, with a lock, the presets (tap to
 * jump, long-press to remove) and a + that saves the current value as a new preset.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ViewPanel(
  settings: ViewSettings,
  zoomPercent: Int,
  angleDegrees: Int,
  onZoomPreset: (Int) -> Unit,
  onAnglePreset: (Int) -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
  isPaged: Boolean = true,
  onFitOrHome: (() -> Unit)? = null,
  onOpenSettings: (() -> Unit)? = null,
) {
  val scheme = MaterialTheme.colorScheme
  PenLabPopoverSurface(
    isPalm = isPalm,
    modifier = modifier.width(284.dp).heightIn(max = 560.dp),
    verticalArrangement = Arrangement.Top,
  ) {
    Column(
      modifier = Modifier.verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
    PresetRow(
      title = "Zoom",
      locked = settings.zoomLocked,
      onLock = { settings.setZoomLock(!settings.zoomLocked) },
      presets = settings.zoomPresets,
      label = { "$it%" },
      onPick = onZoomPreset,
      onRemove = settings::removeZoomPreset,
      onAdd = { settings.addZoomPreset(zoomPercent) },
      addLabel = "Save $zoomPercent% as a preset",
    )
    PresetRow(
      title = "Rotation",
      locked = settings.rotationLocked,
      onLock = { settings.setRotationLock(!settings.rotationLocked) },
      presets = settings.anglePresets,
      label = { "$it°" },
      onPick = onAnglePreset,
      onRemove = settings::removeAnglePreset,
      onAdd = { settings.addAnglePreset(angleDegrees) },
      addLabel = "Save $angleDegrees° as a preset",
    )
    Text(
      "Long-press a preset to remove it.",
      color = scheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodySmall,
    )
    if (onFitOrHome != null) {
      HorizontalDivider(color = scheme.outlineVariant, thickness = 1.dp)
      PenLabMenuItem(
        label = if (isPaged) "Fit page to width" else "Go home",
        icon = if (isPaged) PenLabIcons.FitWidth else PenLabIcons.Home,
        onClick = onFitOrHome,
      )
    }
    HorizontalDivider(color = scheme.outlineVariant, thickness = 1.dp)
    PenLabSwitchRow(
      label = "Draw with finger",
      icon = PenLabIcons.Hand,
      // The menu rows pad by md, the switch row by xs: add the difference so icons and labels line up.
      modifier = Modifier.padding(horizontal = PenLabSpacing.sm),
      menuStyle = true, // same label style and icon size as "Fit page to width" and "Settings"
      checked = settings.drawWithFinger,
      onCheckedChange = settings::setDrawWithFinger,
    )
    if (onOpenSettings != null) {
      HorizontalDivider(color = scheme.outlineVariant, thickness = 1.dp)
      PenLabMenuItem(
        label = "Settings",
        icon = PenLabIcons.Settings,
        showChevron = true,
        onClick = onOpenSettings,
      )
    }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PresetRow(
  title: String,
  locked: Boolean,
  onLock: () -> Unit,
  presets: List<Int>,
  label: (Int) -> String,
  onPick: (Int) -> Unit,
  onRemove: (Int) -> Unit,
  onAdd: () -> Unit,
  addLabel: String,
) {
  val scheme = MaterialTheme.colorScheme
  Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(title, color = scheme.onSurface, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
      // Icon plus a word, so the padlock says what it does: "Lock" / "Locked".
      Row(
        modifier =
          Modifier.heightIn(min = PenLabSpacing.touchTarget)
            .clip(PenLabShapes.small)
            .toggleable(value = locked, role = Role.Switch, onValueChange = { onLock() })
            .semantics { contentDescription = "Lock $title" }
            .padding(horizontal = PenLabSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          if (locked) LockIcon else LockOpenIcon,
          contentDescription = null,
          tint = if (locked) scheme.primary else scheme.onSurfaceVariant,
          modifier = Modifier.size(PenLabLayout.iconCompact),
        )
        Spacer(Modifier.width(PenLabSpacing.xs))
        Text(
          if (locked) "Locked" else "Lock",
          color = if (locked) scheme.primary else scheme.onSurfaceVariant,
          style = MaterialTheme.typography.labelMedium,
        )
      }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      for (preset in presets) {
        Chip(label(preset), Modifier.combinedClickable(role = Role.Button, onClick = { onPick(preset) }, onLongClick = { onRemove(preset) }))
      }
      Chip("+", Modifier.clickable(role = Role.Button, onClick = onAdd).semantics { contentDescription = addLabel })
    }
  }
}

@Composable
private fun Chip(text: String, modifier: Modifier) {
  val shape = PenLabShapes.small
  Text(
    text,
    color = MaterialTheme.colorScheme.onSurface,
    style = PenLabTextStyles.tabularLabel,
    modifier =
      Modifier.clip(shape)
        .background(PenLabComponentTokens.chipIdleBg, shape)
        .then(modifier)
        .heightIn(min = PenLabSpacing.touchTarget)
        .widthIn(min = PenLabSpacing.touchTarget)
        .padding(horizontal = 14.dp, vertical = 10.dp),
  )
}
