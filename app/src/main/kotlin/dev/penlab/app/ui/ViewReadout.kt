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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.penlab.app.ui.components.PenLabMenuItem
import dev.penlab.app.ui.components.PenLabPopoverSurface
import dev.penlab.app.ui.components.PenLabSwitchRow
import dev.penlab.app.ui.theme.LocalPenLabColors
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.PenLabLayout
import dev.penlab.app.ui.theme.PenLabShapes
import dev.penlab.app.ui.theme.PenLabSpacing
import dev.penlab.app.ui.theme.PenLabTextStyles
import dev.penlab.app.ui.theme.floatingChromeSurface

/**
 * The small readout in the top-right corner: zoom and angle of the page.
 * [zoomPercent] and [angleDegrees] are read inside, so only this pill redraws while zooming.
 * Tap it for presets and locks. A lock icon shows when something is locked.
 */
@Composable
fun ViewReadout(
  zoomPercent: () -> Int,
  angleDegrees: () -> Int,
  settings: ViewSettings,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(12.dp)
  val zoom = zoomPercent()
  val angle = angleDegrees()
  Row(
    modifier
      .floatingChromeSurface(
        shape = shape,
        background = PenLabComponentTokens.chromePillBg,
        elevation = 8.dp,
        subtleLightHairline = true,
        borderColor = PenLabComponentTokens.chromePillBorder,
        isDark = LocalPenLabColors.current.isDark,
      )
      .clickable(onClick = onClick)
      .heightIn(min = PenLabSpacing.touchTarget)
      .padding(horizontal = PenLabSpacing.md)
      .semantics { contentDescription = "Zoom $zoom percent, rotation $angle degrees. Double tap for presets and locks." },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ReadoutValue("$zoom%", settings.zoomLocked)
    Spacer(Modifier.width(10.dp))
    ReadoutValue("$angle°", settings.rotationLocked)
  }
}

@Composable
private fun ReadoutValue(text: String, locked: Boolean) {
  val scheme = MaterialTheme.colorScheme
  if (locked) {
    Icon(LockIcon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
    Spacer(Modifier.width(3.dp))
  }
  Text(text, color = scheme.onSurface, style = PenLabTextStyles.tabularLabel)
}

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
      checked = settings.drawWithFinger,
      onCheckedChange = settings::setDrawWithFinger,
    )
    if (onOpenSettings != null) {
      HorizontalDivider(color = scheme.outlineVariant, thickness = 1.dp)
      PenLabMenuItem(
        label = "Settings…",
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
      IconButton(
        onClick = onLock,
        modifier = Modifier.size(PenLabSpacing.touchTarget).semantics { stateDescription = if (locked) "Locked" else "Unlocked" },
      ) {
        Icon(
          if (locked) LockIcon else LockOpenIcon,
          contentDescription = "Lock $title",
          tint = if (locked) scheme.primary else scheme.onSurfaceVariant,
          modifier = Modifier.size(PenLabLayout.iconMedium),
        )
      }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      for (preset in presets) {
        Chip(label(preset), Modifier.combinedClickable(onClick = { onPick(preset) }, onLongClick = { onRemove(preset) }))
      }
      Chip("+", Modifier.clickable(onClick = onAdd).semantics { contentDescription = addLabel })
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
