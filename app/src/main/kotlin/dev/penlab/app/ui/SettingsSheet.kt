package dev.penlab.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.penlab.app.ui.components.PenLabIconButton
import dev.penlab.app.ui.components.PenLabSectionHeader
import dev.penlab.app.ui.components.PenLabSegmentedControl
import dev.penlab.app.ui.components.PenLabSwitchRow
import dev.penlab.app.ui.theme.Accent
import dev.penlab.app.ui.theme.AccentTheme
import dev.penlab.app.ui.theme.LocalPenLabColors
import dev.penlab.app.ui.theme.PageThemeMode
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.PenLabShapes
import dev.penlab.app.ui.theme.PenLabSpacing
import dev.penlab.app.ui.theme.ThemeMode
import dev.penlab.app.ui.theme.penLabColorScheme
import dev.penlab.app.ui.theme.softShadow

/**
 * Unified Settings modal sheet reachable from both the Library top bar and the Canvas menus.
 *
 * Uses a single elevated surface with subtle [MaterialTheme.colorScheme.outlineVariant] hairlines
 * between sections (no nested cards). All appearance, input, and storage changes apply live.
 */
@Composable
fun SettingsSheet(
  viewSettings: ViewSettings,
  storageShared: Boolean,
  onAllowStorage: () -> Unit,
  onDismiss: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val extended = LocalPenLabColors.current
  val systemDark = isSystemInDarkTheme()

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier
          .padding(horizontal = PenLabSpacing.xl, vertical = 20.dp)
          .widthIn(max = 620.dp)
          .heightIn(max = 660.dp)
          .fillMaxWidth()
          .softShadow(PenLabShapes.extraLarge, elevation = 16.dp),
      shape = PenLabShapes.extraLarge,
      color = PenLabComponentTokens.dialogBg,
      contentColor = scheme.onSurface,
      tonalElevation = 0.dp,
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(start = PenLabSpacing.xl, end = PenLabSpacing.md, top = PenLabSpacing.lg, bottom = PenLabSpacing.md),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Settings",
            color = scheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
          )
          PenLabIconButton(
            icon = PenLabIcons.Close,
            contentDescription = "Close settings",
            onClick = onDismiss,
          )
        }
        HorizontalDivider(color = scheme.outlineVariant, thickness = 1.dp)

        // Scrollable sections
        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .weight(1f, fill = false)
              .verticalScroll(rememberScrollState())
              .padding(horizontal = PenLabSpacing.xl, vertical = PenLabSpacing.lg),
        ) {
          // ---- Appearance ----
          PenLabSectionHeader(title = "Appearance", topPadding = 0.dp, bottomPadding = PenLabSpacing.md)

          SettingFieldLabel("App theme")
          Spacer(Modifier.height(PenLabSpacing.xs))
          PenLabSegmentedControl(
            options = ThemeMode.entries,
            selected = Accent.themeMode,
            onSelect = { Accent.setThemeMode(it, systemIsDark = systemDark) },
            label = { it.label },
            icon = { mode ->
              when (mode) {
                ThemeMode.LIGHT -> PenLabIcons.Sun
                ThemeMode.DARK -> PenLabIcons.Moon
                ThemeMode.SYSTEM -> PenLabIcons.DeviceTablet
              }
            },
          )

          Spacer(Modifier.height(PenLabSpacing.lg))

          SettingFieldLabel("Accent look")
          Spacer(Modifier.height(PenLabSpacing.xs))
          Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.md),
          ) {
            for (look in AccentTheme.entries) {
              AccentLookTile(
                look = look,
                isDark = extended.isDark,
                selected = Accent.theme == look,
                onClick = { Accent.set(look) },
                modifier = Modifier.weight(1f),
              )
            }
          }

          Spacer(Modifier.height(PenLabSpacing.lg))

          SettingFieldLabel("Paper background")
          Spacer(Modifier.height(PenLabSpacing.xs))
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
          Spacer(Modifier.height(PenLabSpacing.xs))
          Text(
            text = "PDF pages always stay white.",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
          )

          Spacer(Modifier.height(PenLabSpacing.xl))
          HorizontalDivider(color = scheme.outlineVariant, thickness = 1.dp)

          // ---- Writing & Input ----
          PenLabSectionHeader(title = "Writing & Input", topPadding = PenLabSpacing.lg, bottomPadding = PenLabSpacing.sm)

          PenLabSwitchRow(
            label = "Draw with finger",
            subtitle = "Use a single finger to write and draw instead of panning.",
            checked = viewSettings.drawWithFinger,
            onCheckedChange = viewSettings::setDrawWithFinger,
          )
          Spacer(Modifier.height(PenLabSpacing.xs))
          PenLabSwitchRow(
            label = "Hold to snap shapes",
            subtitle = "Hold the stylus still at the end of a stroke to tidy lines and shapes.",
            checked = viewSettings.shapeSnapping,
            onCheckedChange = viewSettings::setShapeSnapping,
          )

          Spacer(Modifier.height(PenLabSpacing.md))
          SettingFieldLabel("Animations")
          Spacer(Modifier.height(PenLabSpacing.xs))
          PenLabSegmentedControl(
            options = MotionSpeed.entries,
            selected = Motion.speed,
            onSelect = Motion::set,
            label = { it.label },
          )

          Spacer(Modifier.height(PenLabSpacing.xl))
          HorizontalDivider(color = scheme.outlineVariant, thickness = 1.dp)

          // ---- Storage ----
          PenLabSectionHeader(title = "Storage", topPadding = PenLabSpacing.lg, bottomPadding = PenLabSpacing.sm)

          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = PenLabSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier
                  .size(44.dp)
                  .clip(PenLabShapes.small)
                  .background(scheme.surfaceContainerHighest),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = PenLabIcons.Folder,
                contentDescription = null,
                tint = if (storageShared) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
              )
            }
            Spacer(Modifier.width(PenLabSpacing.md))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = if (storageShared) "Documents/PenLab" else "App storage",
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
              )
              Text(
                text =
                  if (storageShared) "Shared storage is active. Notes survive reinstalling PenLab."
                  else "Notes are kept in private app storage. Allow access to store them in Documents/PenLab.",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
              )
            }
            Spacer(Modifier.width(PenLabSpacing.md))
            if (storageShared) {
              Text(
                text = "Active",
                color = extended.success,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
              )
            } else {
              Button(
                onClick = onAllowStorage,
                shape = PenLabShapes.pill,
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary,
                  ),
                modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
                contentPadding = PaddingValues(horizontal = PenLabSpacing.lg, vertical = PenLabSpacing.sm),
              ) {
                Text("Allow access", style = MaterialTheme.typography.labelLarge, maxLines = 1)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SettingFieldLabel(text: String) {
  Text(
    text = text,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.labelLarge,
  )
}

/**
 * Selectable swatch tile previewing an [AccentTheme]'s actual surface, background, and primary
 * tokens in the current Light/Dark mode.
 */
@Composable
private fun AccentLookTile(
  look: AccentTheme,
  isDark: Boolean,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val preview = penLabColorScheme(look, isDark)
  val borderColor by animateColorAsState(
    targetValue = if (selected) scheme.primary else scheme.outlineVariant,
    animationSpec = Motion.tween(140),
    label = "accentTileBorder",
  )
  val bgColor by animateColorAsState(
    targetValue = if (selected) scheme.primaryContainer else scheme.surface,
    animationSpec = Motion.tween(140),
    label = "accentTileBg",
  )
  val titleColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface
  val subtitleColor = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant
  val description =
    when (look) {
      AccentTheme.BLUEPRINT -> "Cool slate & cobalt"
      AccentTheme.COPPER -> "Warm stone & clay"
    }

  Row(
    modifier =
      modifier
        .heightIn(min = 68.dp)
        .clip(PenLabShapes.medium)
        .background(bgColor)
        .border(if (selected) 2.dp else 1.dp, borderColor, PenLabShapes.medium)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = PenLabSpacing.md, vertical = PenLabSpacing.md)
        .semantics { contentDescription = "${look.label} accent look, $description" },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Mini dual swatch: surface backdrop + primary accent dot
    Box(
      modifier =
        Modifier
          .size(36.dp)
          .clip(PenLabShapes.small)
          .background(preview.background)
          .border(1.dp, preview.outlineVariant, PenLabShapes.small),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier =
          Modifier
            .size(18.dp)
            .clip(PenLabShapes.pill)
            .background(preview.primary),
      )
    }
    Spacer(Modifier.width(PenLabSpacing.md))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = look.label,
        color = titleColor,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        maxLines = 1,
      )
      Text(
        text = description,
        color = subtitleColor,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
      )
    }
    if (selected) {
      Spacer(Modifier.width(PenLabSpacing.sm))
      Icon(
        imageVector = PenLabIcons.Check,
        contentDescription = null,
        tint = scheme.primary,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}
