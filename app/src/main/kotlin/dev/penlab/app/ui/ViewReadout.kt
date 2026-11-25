package dev.penlab.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.penlab.app.ui.components.PenLabMenuItem
import dev.penlab.app.ui.components.PenLabPopoverSurface
import dev.penlab.app.ui.components.PenLabSwitchRow
import dev.penlab.app.ui.theme.Accent
import dev.penlab.app.ui.theme.AccentTheme
import dev.penlab.app.ui.theme.LocalPenLabColors
import dev.penlab.app.ui.theme.PenLabComponentTokens
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
      .heightIn(min = 36.dp)
      .padding(horizontal = 12.dp)
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
  onOpenSettings: (() -> Unit)? = null,
) {
  val scheme = MaterialTheme.colorScheme
  PenLabPopoverSurface(
    isPalm = isPalm,
    modifier = modifier.width(276.dp),
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
    HorizontalDivider(color = scheme.outlineVariant, thickness = 1.dp)
    // Interface animations: off, slow, normal or fast.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("Animations", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge)
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (speed in MotionSpeed.entries) {
          val selected = Motion.speed == speed
          Box(
            Modifier.clip(PenLabShapes.small)
              .background(if (selected) scheme.primary else PenLabComponentTokens.chipIdleBg)
              .clickable { Motion.set(speed) }
              .padding(horizontal = 10.dp, vertical = 7.dp),
          ) {
            Text(
              speed.label,
              color = if (selected) scheme.onPrimary else scheme.onSurface,
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }
      }
    }
    // The look: blueprint blue or copper.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("Look", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (look in AccentTheme.entries) {
          val selected = Accent.theme == look
          Row(
            Modifier.clip(PenLabShapes.small)
              .background(if (selected) scheme.primaryContainer else Color.Transparent)
              .border(1.dp, if (selected) look.accent else scheme.outlineVariant, PenLabShapes.small)
              .clickable { Accent.set(look) }
              .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(Modifier.size(10.dp).background(look.accent, CircleShape))
            Text(
              look.label,
              color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }
      }
    }
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
          modifier = Modifier.size(20.dp),
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
        .heightIn(min = 40.dp)
        .padding(horizontal = 14.dp, vertical = 10.dp),
  )
}
