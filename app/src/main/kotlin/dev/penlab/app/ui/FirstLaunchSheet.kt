package dev.penlab.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.penlab.app.ui.components.PenLabSegmentedControl
import dev.penlab.app.ui.theme.Accent
import dev.penlab.app.ui.theme.AccentTheme
import dev.penlab.app.ui.theme.LocalPenLabColors
import dev.penlab.app.ui.theme.PageThemeMode
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.PenLabLayout
import dev.penlab.app.ui.theme.PenLabShapes
import dev.penlab.app.ui.theme.PenLabSpacing
import dev.penlab.app.ui.theme.ThemeMode
import dev.penlab.app.ui.theme.softShadow

/**
 * The welcome sheet: shown on a genuine first run (see [firstLaunchAction]) and from
 * Settings > "Welcome guide". Three live selectors (app theme, accent look, paper) recolour the
 * app behind the sheet as they are tapped, using the same [Accent] switches as Settings, so
 * nothing here needs saving. [startLabel] is "Start writing" for a first run and "Done" when
 * reopened by someone who already has notebooks.
 */
@Composable
fun FirstLaunchSheet(
  onSkip: () -> Unit,
  onStart: () -> Unit,
  onImportPdf: () -> Unit,
  startLabel: String = "Start writing",
) {
  val scheme = MaterialTheme.colorScheme
  val systemDark = isSystemInDarkTheme()
  Dialog(onDismissRequest = onSkip, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(
      modifier =
        Modifier
          .padding(horizontal = PenLabSpacing.xl, vertical = PenLabSpacing.xl)
          .widthIn(max = PenLabLayout.wideDialogMaxWidth)
          .fillMaxWidth()
          .softShadow(PenLabShapes.extraLarge, elevation = PenLabLayout.dialogElevation),
      shape = PenLabShapes.extraLarge,
      color = PenLabComponentTokens.dialogBg,
      contentColor = scheme.onSurface,
    ) {
      Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = PenLabSpacing.xl).padding(bottom = PenLabSpacing.xl)) {
        // Skip sits in the top corner, out of the way of the choices.
        Row(Modifier.fillMaxWidth().padding(top = PenLabSpacing.sm), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget)) {
            Text("Skip", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
          }
        }
        Text("Welcome to PenLab", color = scheme.onSurface, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(PenLabSpacing.xs))
        Text(
          "Pick your look and paper — you can change this anytime in Settings.",
          color = scheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(PenLabSpacing.xl))

        WelcomeLabel("App theme")
        PenLabSegmentedControl(
          options = listOf(ThemeMode.LIGHT, ThemeMode.DARK),
          // "System" resolves to what is on screen, so one of the two always shows as chosen.
          selected = if (Accent.isDark) ThemeMode.DARK else ThemeMode.LIGHT,
          onSelect = { Accent.setThemeMode(it, systemIsDark = systemDark) },
          label = { it.label },
          icon = { if (it == ThemeMode.LIGHT) PenLabIcons.Sun else PenLabIcons.Moon },
        )
        Spacer(Modifier.height(PenLabSpacing.lg))

        WelcomeLabel("Accent look")
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.md)) {
          for (look in AccentTheme.entries) {
            AccentLookTile(
              look = look,
              isDark = LocalPenLabColors.current.isDark,
              selected = Accent.theme == look,
              onClick = { Accent.set(look) },
              modifier = Modifier.weight(1f),
            )
          }
        }
        Spacer(Modifier.height(PenLabSpacing.lg))

        WelcomeLabel("Paper")
        PenLabSegmentedControl(
          options = PageThemeMode.entries,
          selected = Accent.pageThemeMode,
          onSelect = Accent::setPageThemeMode,
          label = { if (it == PageThemeMode.LIGHT) "Light paper" else "Dark paper" },
          icon = { if (it == PageThemeMode.LIGHT) PenLabIcons.Sun else PenLabIcons.Moon },
        )
        Spacer(Modifier.height(PenLabSpacing.xs))
        Text("PDF pages always stay white.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(PenLabSpacing.xl))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.md), verticalAlignment = Alignment.CenterVertically) {
          Button(
            onClick = onStart,
            shape = PenLabShapes.pill,
            colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
            modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
            contentPadding = PaddingValues(horizontal = PenLabSpacing.xl, vertical = PenLabSpacing.sm),
          ) {
            Icon(PenLabIcons.Pen, contentDescription = null, modifier = Modifier.size(PenLabLayout.iconMedium))
            Spacer(Modifier.width(PenLabSpacing.sm))
            Text(startLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
          }
          OutlinedButton(
            onClick = onImportPdf,
            shape = PenLabShapes.pill,
            modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
            contentPadding = PaddingValues(horizontal = PenLabSpacing.lg, vertical = PenLabSpacing.sm),
          ) {
            Icon(PenLabIcons.Pdf, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(PenLabLayout.iconMedium))
            Spacer(Modifier.width(PenLabSpacing.sm))
            Text("Import a PDF", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge, maxLines = 1)
          }
        }
      }
    }
  }
}

@Composable
private fun WelcomeLabel(text: String) {
  Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = PenLabSpacing.xs))
}
