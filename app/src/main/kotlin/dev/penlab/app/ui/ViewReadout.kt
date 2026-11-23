package dev.penlab.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import dev.penlab.app.ui.theme.PenLabColors
import dev.penlab.app.ui.theme.softShadow

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
      .softShadow(shape).clip(shape)
      .background(PenLabColors.toolbar)
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
  if (locked) {
    Icon(LockIcon, contentDescription = null, tint = PenLabColors.iconDisabled, modifier = Modifier.size(12.dp))
    Spacer(Modifier.width(3.dp))
  }
  Text(text, color = PenLabColors.icon, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
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
) {
  val shape = RoundedCornerShape(16.dp)
  Column(
    modifier
      .softShadow(shape).clip(shape)
      .background(PenLabColors.toolbar)
      .ignorePalm(isPalm)
      .padding(12.dp)
      .width(260.dp),
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
    // Interface animations: off, slow, normal or fast.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("Animations", color = PenLabColors.icon, style = MaterialTheme.typography.labelLarge)
      androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (speed in MotionSpeed.entries) {
          val selected = Motion.speed == speed
          Box(
            Modifier.clip(RoundedCornerShape(10.dp))
              .background(if (selected) PenLabColors.accent else PenLabColors.chip)
              .clickable { Motion.set(speed) }
              .padding(horizontal = 10.dp, vertical = 7.dp)
          ) {
            Text(speed.label, color = if (selected) PenLabColors.onAccent else PenLabColors.icon, style = MaterialTheme.typography.labelMedium)
          }
        }
      }
    }
    // The look: blueprint blue or copper.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("Look", color = PenLabColors.icon, style = MaterialTheme.typography.labelLarge)
      androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (look in dev.penlab.app.ui.theme.AccentTheme.entries) {
          val selected = dev.penlab.app.ui.theme.Accent.theme == look
          androidx.compose.foundation.layout.Row(
            Modifier.clip(RoundedCornerShape(10.dp))
              .background(if (selected) PenLabColors.chip else androidx.compose.ui.graphics.Color.Transparent)
              .border(1.dp, if (selected) look.accent else PenLabColors.hairline, RoundedCornerShape(10.dp))
              .clickable { dev.penlab.app.ui.theme.Accent.set(look) }
              .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(Modifier.size(10.dp).background(look.accent, androidx.compose.foundation.shape.CircleShape))
            Text(look.label, color = PenLabColors.icon, style = MaterialTheme.typography.labelMedium)
          }
        }
      }
    }
    Text(
      "Long-press a preset to remove it.",
      color = PenLabColors.iconDisabled,
      style = MaterialTheme.typography.bodySmall,
    )
    SwitchRow("Draw with finger", settings.drawWithFinger, settings::setDrawWithFinger)
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
  Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(title, color = PenLabColors.icon, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
      IconButton(
        onClick = onLock,
        modifier = Modifier.size(40.dp).semantics { stateDescription = if (locked) "Locked" else "Unlocked" },
      ) {
        Icon(
          if (locked) LockIcon else LockOpenIcon,
          contentDescription = "Lock $title",
          tint = if (locked) PenLabColors.icon else PenLabColors.iconDisabled,
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
  val shape = RoundedCornerShape(10.dp)
  Text(
    text,
    color = PenLabColors.icon,
    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
    modifier =
      Modifier.clip(shape)
        .background(PenLabColors.chip, shape)
        .then(modifier)
        .heightIn(min = 40.dp)
        .padding(horizontal = 14.dp, vertical = 10.dp),
  )
}
