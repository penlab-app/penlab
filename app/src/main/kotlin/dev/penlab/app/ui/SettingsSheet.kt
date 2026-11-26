package dev.penlab.app.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.penlab.app.plugins.external.PluginRow
import dev.penlab.app.ui.components.PenLabDialog
import dev.penlab.app.ui.components.PenLabIconButton
import dev.penlab.app.ui.components.PenLabMenuItem
import dev.penlab.app.ui.components.PenLabSectionHeader
import dev.penlab.app.ui.components.PenLabSegmentedControl
import dev.penlab.app.ui.components.PenLabSwitchRow
import dev.penlab.app.ui.theme.Accent
import dev.penlab.app.ui.theme.AccentTheme
import dev.penlab.app.ui.theme.LocalPenLabColors
import dev.penlab.app.ui.theme.PageThemeMode
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.PenLabLayout
import dev.penlab.app.ui.theme.PenLabShapes
import dev.penlab.app.ui.theme.PenLabSpacing
import dev.penlab.app.ui.theme.PenLabTextStyles
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
  toolbarConfig: ToolbarConfig,
  toolDock: ToolDock,
  storageShared: Boolean,
  onAllowStorage: () -> Unit,
  onDismiss: () -> Unit,
  /** Replays the Welcome sheet; only offered on the library (null hides the row). */
  onWelcomeGuide: (() -> Unit)? = null,
  /** Opens the Bug / idea form (null hides the row). */
  onReport: (() -> Unit)? = null,
  /** The plugin apps found on the device (Plugins section). */
  pluginRows: List<PluginRow> = emptyList(),
  /** Called when the section appears: rescans for plugin apps. */
  onPluginsShown: () -> Unit = {},
  /** The user flipped a plugin's switch; false means "ask for consent first" (then [onPluginConsent] runs on accept). */
  onPluginEnabled: (packageName: String, on: Boolean) -> Boolean = { _, _ -> true },
  onPluginConsent: (packageName: String) -> Unit = {},
  /** "Try again" on a plugin that crashed this session. */
  onPluginRetry: (packageName: String) -> Unit = {},
) {
  val scheme = MaterialTheme.colorScheme
  val extended = LocalPenLabColors.current
  val systemDark = isSystemInDarkTheme()
  var customizeToolbarExpanded by remember { mutableStateOf(false) }
  var consentFor by remember { mutableStateOf<PluginRow?>(null) }
  LaunchedEffect(Unit) { onPluginsShown() }

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

          // ---- Writing & input ----
          PenLabSectionHeader(title = "Writing & input", topPadding = PenLabSpacing.lg, bottomPadding = PenLabSpacing.sm)

          PenLabSwitchRow(
            label = "Draw with finger",
            subtitle = "Use a single finger to write and draw instead of panning.",
            checked = viewSettings.drawWithFinger,
            onCheckedChange = viewSettings::setDrawWithFinger,
            horizontalPadding = 0.dp,
          )
          Spacer(Modifier.height(PenLabSpacing.xs))
          PenLabSwitchRow(
            label = "Hold to snap shapes",
            subtitle = "Hold the stylus still at the end of a stroke to tidy lines and shapes.",
            checked = viewSettings.shapeSnapping,
            onCheckedChange = viewSettings::setShapeSnapping,
            horizontalPadding = 0.dp,
          )

          Spacer(Modifier.height(PenLabSpacing.md))
          SettingFieldLabel("Animations")
          Spacer(Modifier.height(PenLabSpacing.xs))
          PenLabSegmentedControl(
            options = MotionSpeed.entries,
            selected = Motion.speed,
            onSelect = Motion::set,
            label = { it.label },
            enabled = !Motion.systemOff,
          )
          if (Motion.systemOff) {
            Text(
              text = "Off because Android's Remove animations is on.",
              style = MaterialTheme.typography.bodySmall,
              color = scheme.onSurfaceVariant,
              modifier = Modifier.padding(top = PenLabSpacing.xs),
            )
          }

          Spacer(Modifier.height(PenLabSpacing.xl))
          HorizontalDivider(color = scheme.outlineVariant, thickness = 1.dp)

          // ---- Toolbar ----
          PenLabSectionHeader(title = "Toolbar", topPadding = PenLabSpacing.lg, bottomPadding = PenLabSpacing.sm)

          // Pen slots stepper (− 4 +, range 1..6)
          Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = PenLabSpacing.touchTarget),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = "Pen slots",
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
              )
              Text(
                text = "Quick-switch pens on the toolbar (1 to 6).",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
              )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              PenLabIconButton(
                icon = PenLabIcons.Minus,
                contentDescription = "Fewer pen slots",
                enabled = toolbarConfig.penCount > MIN_PEN_COUNT,
                onClick = { toolbarConfig.setPenCount(toolbarConfig.penCount - 1) },
                iconSize = PenLabLayout.iconCompact,
              )
              Text(
                text = "${toolbarConfig.penCount}",
                color = scheme.onSurface,
                style = PenLabTextStyles.tabularLabel,
                modifier = Modifier.width(28.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
              )
              PenLabIconButton(
                icon = PenLabIcons.Plus,
                contentDescription = "More pen slots",
                enabled = toolbarConfig.penCount < MAX_PEN_COUNT,
                onClick = { toolbarConfig.setPenCount(toolbarConfig.penCount + 1) },
                iconSize = PenLabLayout.iconCompact,
              )
            }
          }

          Spacer(Modifier.height(PenLabSpacing.md))
          SettingFieldLabel("Dock position")
          Spacer(Modifier.height(PenLabSpacing.xs))
          val selectedDockOption = DockPositionOption.fromDockSide(toolDock.side)
          PenLabSegmentedControl(
            options = DockPositionOption.entries,
            selected = selectedDockOption,
            onSelect = { option ->
              val target = option.toDockSide(currentSide = toolDock.side)
              toolDock.dockTo(target)
            },
            label = { it.label },
          )

          Spacer(Modifier.height(PenLabSpacing.md))
          PenLabMenuItem(
            label = "Customise toolbar items",
            subtitle = "${toolbarConfig.visibleOrderedItems.size} items visible · tap to toggle or reorder",
            icon = PenLabIcons.PenTools,
            horizontalPadding = 0.dp, // the labels around it start at the section edge
            onClick = { customizeToolbarExpanded = !customizeToolbarExpanded },
            trailingContent = {
              Icon(
                imageVector = if (customizeToolbarExpanded) PenLabIcons.ChevronUp else PenLabIcons.ChevronDown,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(PenLabLayout.iconCompact),
              )
            },
          )

          AnimatedVisibility(visible = customizeToolbarExpanded) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(top = PenLabSpacing.xs),
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              val order = toolbarConfig.order
              val visible = toolbarConfig.visible
              order.forEachIndexed { index, item ->
                val isVisible = item in visible
                val canDisable = !isVisible || visible.size > 1
                ToolbarItemCustomizeRow(
                  item = item,
                  penCount = toolbarConfig.penCount,
                  visible = isVisible,
                  canToggle = canDisable,
                  canMoveUp = index > 0,
                  canMoveDown = index < order.lastIndex,
                  onToggle = { toolbarConfig.toggleItem(item) },
                  onMoveUp = { toolbarConfig.moveItem(item, -1) },
                  onMoveDown = { toolbarConfig.moveItem(item, +1) },
                )
              }
            }
          }

          Spacer(Modifier.height(PenLabSpacing.sm))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(
              onClick = {
                toolbarConfig.reset()
                toolDock.dockTo(DockSide.TOP)
              },
              shape = PenLabShapes.pill,
              modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
              contentPadding = PaddingValues(horizontal = PenLabSpacing.lg, vertical = PenLabSpacing.sm),
            ) {
              Text("Reset toolbar", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge)
            }
          }

          Spacer(Modifier.height(PenLabSpacing.xl))
          HorizontalDivider(color = scheme.outlineVariant, thickness = 1.dp)

          // ---- Storage ----
          PenLabSectionHeader(title = "Storage", topPadding = PenLabSpacing.lg, bottomPadding = PenLabSpacing.sm)

          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = PenLabSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            // Same icon size and gap as the menu rows above, so the text lines up with theirs.
            Icon(
              imageVector = PenLabIcons.Folder,
              contentDescription = null,
              tint = if (storageShared) scheme.primary else scheme.onSurfaceVariant,
              modifier = Modifier.size(PenLabLayout.iconMedium),
            )
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

          // ---- Plugins ----
          Spacer(Modifier.height(PenLabSpacing.xl))
          HorizontalDivider(color = scheme.outlineVariant, thickness = 1.dp)
          PenLabSectionHeader(title = "Plugins", topPadding = PenLabSpacing.lg, bottomPadding = PenLabSpacing.sm)
          if (pluginRows.isEmpty()) {
            Text(
              text = "No plugins installed. A plugin is a separate app; PenLab finds it by itself and lists it here.",
              color = scheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodySmall,
            )
          } else {
            for (row in pluginRows) {
              PenLabSwitchRow(
                label = if (row.trusted) "${row.name} · Trusted" else row.name,
                subtitle = pluginSubtitle(row),
                checked = row.switchOn,
                enabled = row.switchEnabled,
                onCheckedChange = { on -> if (!onPluginEnabled(row.packageName, on)) consentFor = row },
                horizontalPadding = 0.dp,
              )
              if (row.canRetry) {
                TextButton(onClick = { onPluginRetry(row.packageName) }) { Text("Try again") }
              }
            }
          }

          // ---- Help & feedback ----
          if (onWelcomeGuide != null || onReport != null) {
            Spacer(Modifier.height(PenLabSpacing.xl))
            HorizontalDivider(color = scheme.outlineVariant, thickness = 1.dp)
            PenLabSectionHeader(title = "Help & feedback", topPadding = PenLabSpacing.lg, bottomPadding = PenLabSpacing.sm)
            if (onWelcomeGuide != null) {
              PenLabMenuItem(
                label = "Welcome guide",
                subtitle = "Replay the quick look-and-paper setup.",
                icon = PenLabIcons.Sparkles,
                horizontalPadding = 0.dp,
                showChevron = true,
                onClick = onWelcomeGuide,
              )
            }
            if (onReport != null) {
              PenLabMenuItem(
                label = "Bug / idea",
                subtitle = "Tell us what broke or what you would like.",
                icon = PenLabIcons.Idea,
                horizontalPadding = 0.dp,
                showChevron = true,
                onClick = onReport,
              )
            }
          }
        }
      }
    }
  }

  consentFor?.let { row ->
    PenLabDialog(
      title = "Switch on “${row.name}”?",
      onDismiss = { consentFor = null },
      primaryLabel = "Switch on",
      onPrimaryClick = {
        consentFor = null
        onPluginConsent(row.packageName)
      },
    ) {
      Text(
        text = pluginConsentText(row),
        color = scheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

/** The lines under a plugin's switch in Settings. */
internal fun pluginSubtitle(row: PluginRow): String =
  buildList {
    add(row.problem?.let { "Cannot be used: $it" } ?: if (row.trusted) "Signed with PenLab's own certificate" else "Not verified: from another developer")
    add(row.packageName)
    if (row.capabilities.isNotEmpty()) add(row.capabilities.joinToString(", "))
    add(if (row.permissions.isEmpty()) "Declares no permissions" else "Declares: " + row.permissions.joinToString(", "))
    if (row.switchedOff) add("Stopped working: off until PenLab restarts")
  }.joinToString("\n")

/** What the one-time consent dialog says a plugin can reach. */
internal fun pluginConsentText(row: PluginRow): String =
  "This plugin comes from another developer (${row.packageName}); PenLab cannot vouch for it.\n\n" +
    "When you use it, it receives what you give it: the ink and items you select, or the file you import with it. " +
    "It can add things to your pages. " +
    (if (row.permissions.isEmpty()) "It declares no extra permissions." else "It declares these permissions: ${row.permissions.joinToString(", ")}.") +
    "\n\nYou can switch it off here at any time."

@Composable
private fun SettingFieldLabel(text: String) {
  Text(
    text = text,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.labelLarge,
  )
}

private enum class DockPositionOption(val label: String) {
  TOP("Top"),
  BOTTOM("Bottom"),
  LEFT("Left"),
  RIGHT("Right"),
  CORNER("Corner");

  fun toDockSide(currentSide: DockSide): DockSide =
    when (this) {
      TOP -> DockSide.TOP
      BOTTOM -> DockSide.BOTTOM
      LEFT -> DockSide.LEFT
      RIGHT -> DockSide.RIGHT
      CORNER -> if (currentSide.isCorner) currentSide else DockSide.BOTTOM_RIGHT
    }

  companion object {
    fun fromDockSide(side: DockSide): DockPositionOption =
      when {
        side == DockSide.TOP -> TOP
        side == DockSide.BOTTOM -> BOTTOM
        side == DockSide.LEFT -> LEFT
        side == DockSide.RIGHT -> RIGHT
        else -> CORNER
      }
  }
}

private fun toolbarItemIcon(item: ToolbarItem): ImageVector =
  when (item) {
    ToolbarItem.PENS -> PenLabIcons.Pen
    ToolbarItem.ERASER -> PenLabIcons.Eraser
    ToolbarItem.SELECT -> PenLabIcons.Lasso
    ToolbarItem.FILL -> PenLabIcons.Fill
    ToolbarItem.TOOLS -> PenLabIcons.Ruler
    ToolbarItem.MORE -> PenLabIcons.ChevronDown
    ToolbarItem.UNDO_REDO -> PenLabIcons.Undo
    ToolbarItem.HIGHLIGHTER -> PenLabIcons.Highlighter
    ToolbarItem.TEXT -> PenLabIcons.Text
    ToolbarItem.LAYERS -> PenLabIcons.Layers
    ToolbarItem.CLEAR -> PenLabIcons.Clear
  }

@Composable
private fun ToolbarItemCustomizeRow(
  item: ToolbarItem,
  penCount: Int,
  visible: Boolean,
  canToggle: Boolean,
  canMoveUp: Boolean,
  canMoveDown: Boolean,
  onToggle: () -> Unit,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val displayLabel = if (item == ToolbarItem.PENS) "Pens ($penCount)" else item.label
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = PenLabSpacing.touchTarget)
        .clip(PenLabShapes.small)
        .padding(start = PenLabSpacing.sm, end = PenLabSpacing.xs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = toolbarItemIcon(item),
      contentDescription = null,
      tint = if (visible) scheme.onSurface else PenLabComponentTokens.toolIconDisabled,
      modifier = Modifier.size(19.dp),
    )
    Spacer(Modifier.width(PenLabSpacing.md))
    Text(
      text = displayLabel,
      color = if (visible) scheme.onSurface else scheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodyMedium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    PenLabIconButton(
      icon = PenLabIcons.ChevronUp,
      contentDescription = "Move ${item.label} up",
      enabled = canMoveUp,
      onClick = onMoveUp,
      iconSize = PenLabLayout.iconSmall,
    )
    PenLabIconButton(
      icon = PenLabIcons.ChevronDown,
      contentDescription = "Move ${item.label} down",
      enabled = canMoveDown,
      onClick = onMoveDown,
      iconSize = PenLabLayout.iconSmall,
    )
    Spacer(Modifier.width(PenLabSpacing.xs))
    Switch(
      checked = visible,
      onCheckedChange = { onToggle() },
      enabled = canToggle,
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = scheme.onPrimary,
          checkedTrackColor = scheme.primary,
          checkedBorderColor = scheme.primary,
          uncheckedThumbColor = scheme.outline,
          uncheckedTrackColor = scheme.surfaceContainerHighest,
          uncheckedBorderColor = scheme.outline,
        ),
    )
  }
}

/**
 * Selectable swatch tile previewing an [AccentTheme]'s actual surface, background, and primary
 * tokens in the current Light/Dark mode.
 */
@Composable
internal fun AccentLookTile(
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
        imageVector = PenLabIcons.CheckBare,
        contentDescription = null,
        tint = scheme.primary,
        modifier = Modifier.size(PenLabLayout.iconMedium),
      )
    }
  }
}
