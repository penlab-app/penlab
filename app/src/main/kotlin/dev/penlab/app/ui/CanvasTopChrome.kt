package dev.penlab.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.penlab.app.ink.PageStyle
import dev.penlab.app.notes.NotebookInfo
import dev.penlab.app.notes.NotebookKind
import dev.penlab.app.ui.components.PenLabChromePill
import dev.penlab.app.ui.components.PenLabDialog
import dev.penlab.app.ui.components.PenLabMenuItem
import dev.penlab.app.ui.components.PenLabPopoverSurface
import dev.penlab.app.ui.components.insetPillClickable
import dev.penlab.app.ui.components.PenLabChoiceChip
import dev.penlab.app.ui.components.PenLabSegmentedControl
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.PenLabLayout
import dev.penlab.app.ui.theme.PenLabShapes
import dev.penlab.app.ui.theme.PenLabSpacing
import dev.penlab.app.ui.theme.PenLabTextStyles

/** Tabs inside [PageConfigPopover]. Endless canvases omit [PAGES]. */
enum class PageConfigTab(val label: String) {
  PAGES("Pages"),
  PAPER("Paper"),
  LAYERS("Layers"),
}

/**
 * Left floating island of the 3-island top chrome:
 * `[ < | file name (+ compact PDF badge / folder subtitle) | ⋮ ]`.
 */
@Composable
fun FileChromePill(
  title: String,
  folder: String,
  quickMenuOpen: Boolean,
  optionsOpen: Boolean,
  onBack: () -> Unit,
  onToggleQuickMenu: () -> Unit,
  onToggleOptions: () -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
  isPdfNotebook: Boolean = false,
) {
  val scheme = MaterialTheme.colorScheme
  val titleBg by animateColorAsState(
    targetValue = if (quickMenuOpen) PenLabComponentTokens.toolCellSelectedBg else Color.Transparent,
    animationSpec = Motion.tween(120),
    label = "fileTitleBg",
  )

  val titleInteraction = remember { MutableInteractionSource() }

  PenLabChromePill(
    modifier = modifier.height(BarHeight),
    isPalm = isPalm,
    contentPadding = PaddingValues(horizontal = PenLabSpacing.xs),
    horizontalArrangement = Arrangement.spacedBy(0.dp),
  ) {
    ChromePillIconButton(
      icon = PenLabIcons.Back,
      contentDescription = "Back to library",
      onClick = onBack,
    )
    Box(
      modifier =
        Modifier
          .height(BarHeight)
          .widthIn(min = 64.dp, max = 160.dp)
          .insetPillClickable(titleInteraction, onToggleQuickMenu, titleBg)
          .padding(horizontal = PenLabSpacing.sm)
          .semantics {
            val pdfPrefix = if (isPdfNotebook) "PDF notebook, " else ""
            contentDescription =
              if (folder.isNotEmpty()) "$pdfPrefix$title in ${folder.replace("/", ", ")}. Double tap for recent files and file actions."
              else "$pdfPrefix$title. Double tap for recent files and file actions."
          },
      contentAlignment = Alignment.CenterStart,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.xs),
      ) {
        Column(
          modifier = Modifier.weight(1f, fill = false),
          verticalArrangement = Arrangement.Center,
        ) {
          Text(
            text = title,
            color = if (quickMenuOpen) scheme.onPrimaryContainer else scheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (isPdfNotebook || folder.isNotEmpty()) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              if (isPdfNotebook) {
                PdfNotebookBadge(inverted = quickMenuOpen)
              }
              if (folder.isNotEmpty()) {
                Text(
                  text = folder.replace("/", " › "),
                  color = if (quickMenuOpen) scheme.onPrimaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                  style = MaterialTheme.typography.labelSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f, fill = false),
                )
              }
            }
          }
        }
        Icon(
          imageVector = PenLabIcons.ChevronDown,
          contentDescription = null,
          tint = if (quickMenuOpen) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
          modifier = Modifier.size(13.dp),
        )
      }
    }
    ChromePillIconButton(
      icon = PenLabIcons.MoreVert,
      contentDescription = "File options",
      selected = optionsOpen,
      onClick = onToggleOptions,
    )
  }
}

/** Compact tonal badge indicating the open notebook is an annotated PDF. */
@Composable
private fun PdfNotebookBadge(inverted: Boolean) {
  val scheme = MaterialTheme.colorScheme
  val badgeBg =
    if (inverted) scheme.onPrimaryContainer.copy(alpha = 0.14f)
    else scheme.primaryContainer
  val badgeFg = scheme.onPrimaryContainer
  Row(
    modifier =
      Modifier
        .clip(PenLabShapes.extraSmall)
        .background(badgeBg)
        .padding(horizontal = PenLabSpacing.xs, vertical = 1.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Icon(
      imageVector = PenLabIcons.Pdf,
      contentDescription = null,
      tint = badgeFg,
      modifier = Modifier.size(10.dp),
    )
    Text(
      text = "PDF",
      color = badgeFg,
      style = PenLabTextStyles.badge,
      maxLines = 1,
      softWrap = false,
    )
  }
}

/**
 * Anchored popover under [FileChromePill] filename: recent files switcher + current file controls
 * (Rename, Move to folder, and Turn portrait/landscape for plain A4 notebooks).
 */
@Composable
fun FileQuickMenuPopover(
  current: NotebookInfo,
  recentNotebooks: List<NotebookInfo>,
  folderColorFor: (String) -> Int?,
  onSwitchNotebook: (NotebookInfo) -> Unit,
  onRename: () -> Unit,
  onMoveToFolder: () -> Unit,
  onTurnOrientation: (() -> Unit)?,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  PenLabPopoverSurface(
    isPalm = isPalm,
    modifier = modifier.width(316.dp).heightIn(max = 560.dp),
    contentPadding = PaddingValues(vertical = PenLabSpacing.sm, horizontal = PenLabSpacing.sm),
    verticalArrangement = Arrangement.Top,
  ) {
    Column(
      modifier = Modifier.verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = "Recent files",
        color = scheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = PenLabSpacing.md, vertical = PenLabSpacing.xs),
      )
      for (info in recentNotebooks) {
        val isCurrent = info.id == current.id
        val accentInt = if (info.folder.isNotEmpty()) folderColorFor(info.folder) else null
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .heightIn(min = PenLabSpacing.touchTarget)
              .clip(PenLabShapes.small)
              .clickable(role = Role.Button) { if (!isCurrent) onSwitchNotebook(info) }
              .padding(horizontal = PenLabSpacing.md, vertical = PenLabSpacing.sm)
              .semantics {
                contentDescription =
                  if (isCurrent) "${info.title}, currently open"
                  else "Switch to ${info.title}"
              },
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Same 20 dp slot as the icons of PenLabMenuItem below, so the titles start at the same x.
          Box(Modifier.size(PenLabLayout.iconMedium), contentAlignment = Alignment.Center) {
            if (accentInt != null) {
              Box(
                Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(Color(accentInt)),
              )
            } else {
              Icon(
                imageVector = if (info.pdfPages.isNotEmpty()) PenLabIcons.Pdf else PenLabIcons.Paper,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(PenLabLayout.iconMedium),
              )
            }
          }
          Spacer(Modifier.width(PenLabSpacing.md))
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
              text = info.title,
              color = scheme.onSurface,
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (info.folder.isNotEmpty()) {
              Text(
                text = info.folder.replace("/", " › "),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          if (isCurrent) {
            Spacer(Modifier.width(PenLabSpacing.sm))
            // Selection is the bare check alone (as in the swatches), not check plus a fill.
            Icon(
              imageVector = PenLabIcons.CheckBare,
              contentDescription = null,
              tint = scheme.primary,
              modifier = Modifier.size(PenLabLayout.iconCompact),
            )
          }
        }
      }

      HorizontalDivider(
        color = scheme.outlineVariant,
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = PenLabSpacing.xs),
      )

      Text(
        text = "Current file",
        color = scheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = PenLabSpacing.md, vertical = PenLabSpacing.xs),
      )
      PenLabMenuItem(
        label = "Rename",
        icon = PenLabIcons.Rename,
        onClick = onRename,
      )
      PenLabMenuItem(
        label = "Move to folder",
        subtitle = current.folder.ifEmpty { "Top level" }.replace("/", " › "),
        icon = PenLabIcons.Move,
        onClick = onMoveToFolder,
      )
      if (onTurnOrientation != null) {
        val toLandscape = current.pageWidthMm < current.pageHeightMm
        PenLabMenuItem(
          label = if (toLandscape) "Turn to landscape" else "Turn to portrait",
          icon = PenLabIcons.Rotate,
          onClick = onTurnOrientation,
        )
      }
    }
  }
}

/**
 * Anchored popover under [FileChromePill]'s `⋮` button: Export PDF, Share, Save to Google Drive,
 * Present mode, Settings, Bug / idea, plus any registered `SELECTION_EMPTY` plugin actions.
 */
@Composable
fun FileOptionsPopover(
  onExportPdf: () -> Unit,
  onShare: () -> Unit,
  onSyncGoogleDrive: () -> Unit,
  onPresentMode: () -> Unit,
  onOpenSettings: () -> Unit,
  /** Null when no external display is connected: then the entry is hidden. */
  projectorActive: Boolean? = null,
  onProjectorView: () -> Unit = {},
  onReportBugs: () -> Unit,
  pluginActions: List<MenuAction>,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  PenLabPopoverSurface(
    isPalm = isPalm,
    modifier = modifier.width(284.dp).heightIn(max = 560.dp),
    contentPadding = PaddingValues(vertical = PenLabSpacing.sm, horizontal = PenLabSpacing.sm),
    verticalArrangement = Arrangement.Top,
  ) {
    Column(
      modifier = Modifier.verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      PenLabMenuItem(
        label = "Export PDF",
        icon = PenLabIcons.Save,
        onClick = onExportPdf,
      )
      PenLabMenuItem(
        label = "Share",
        icon = PenLabIcons.Export,
        onClick = onShare,
      )
      PenLabMenuItem(
        label = "Save to Google Drive",
        subtitle = "Save PDF via Drive picker",
        icon = PenLabIcons.CloudSync,
        onClick = onSyncGoogleDrive,
      )
      PenLabMenuItem(
        label = "Present mode",
        icon = PenLabIcons.Present,
        onClick = onPresentMode,
      )
      if (projectorActive != null) {
        PenLabMenuItem(
          label = if (projectorActive) "Projector view · on" else "Projector view",
          subtitle = "Only the page on the projector; tools stay here",
          icon = PenLabIcons.Present,
          onClick = onProjectorView,
        )
      }
      HorizontalDivider(
        color = scheme.outlineVariant,
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = PenLabSpacing.xs),
      )
      PenLabMenuItem(
        label = "Settings",
        icon = PenLabIcons.Settings,
        onClick = onOpenSettings,
      )
      PenLabMenuItem(
        label = "Bug / idea",
        icon = PenLabIcons.Idea,
        onClick = onReportBugs,
      )
      if (pluginActions.isNotEmpty()) {
        HorizontalDivider(
          color = scheme.outlineVariant,
          thickness = 1.dp,
          modifier = Modifier.padding(vertical = PenLabSpacing.xs),
        )
        for (action in pluginActions) {
          if (action.iconRes != 0) {
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .heightIn(min = PenLabSpacing.touchTarget)
                  .clip(PenLabShapes.small)
                  .clickable(role = Role.Button, onClick = action.onClick)
                  .padding(horizontal = PenLabSpacing.md, vertical = PenLabSpacing.sm),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                painter = painterResource(action.iconRes),
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(PenLabLayout.iconMedium),
              )
              Spacer(Modifier.width(PenLabSpacing.md))
              Text(
                text = action.label,
                color = scheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          } else {
            PenLabMenuItem(
              label = action.label,
              icon = action.icon,
              onClick = action.onClick,
            )
          }
        }
      }
    }
  }
}

/**
 * Right floating island of the 3-island top chrome:
 * `[ Search | − 100% + | Fullscreen | Page Config ]`.
 */
@Composable
fun PageConfigChromePill(
  searchAvailable: Boolean,
  searchOpen: Boolean,
  zoomPercent: () -> Int,
  viewLocked: Boolean,
  viewPanelOpen: Boolean,
  isPaged: Boolean,
  currentPage: () -> Int,
  pageCount: Int,
  pageConfigOpen: Boolean,
  onToggleSearch: () -> Unit,
  onZoomOut: () -> Unit,
  onToggleViewPanel: () -> Unit,
  onZoomIn: () -> Unit,
  onEnterFullScreen: () -> Unit,
  onTogglePageConfig: () -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val zoom = zoomPercent()
  val pageNum = currentPage() + 1
  val zoomInteraction = remember { MutableInteractionSource() }
  val pageConfigInteraction = remember { MutableInteractionSource() }

  PenLabChromePill(
    modifier = modifier.height(BarHeight),
    isPalm = isPalm,
    contentPadding = PaddingValues(horizontal = PenLabSpacing.xs),
    horizontalArrangement = Arrangement.spacedBy(0.dp),
  ) {
    if (searchAvailable) {
      ChromePillIconButton(
        icon = PenLabIcons.Search,
        contentDescription = "Search handwriting",
        selected = searchOpen,
        onClick = onToggleSearch,
      )
      ChromePillDivider()
    }

    // Inline Zoom Control: −  100%  +
    ChromePillIconButton(
      icon = PenLabIcons.Minus,
      contentDescription = "Zoom out",
      onClick = onZoomOut,
      iconSize = PenLabLayout.iconCompact,
    )
    Box(
      modifier =
        Modifier
          .height(BarHeight)
          .widthIn(min = PenLabSpacing.touchTarget)
          .insetPillClickable(
            zoomInteraction,
            onToggleViewPanel,
            if (viewPanelOpen) PenLabComponentTokens.toolCellSelectedBg else Color.Transparent,
          )
          .padding(horizontal = 6.dp)
          .semantics {
            contentDescription = "Zoom $zoom percent. Double tap for zoom presets and view locks."
            if (viewPanelOpen) selected = true
          },
      contentAlignment = Alignment.Center,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        if (viewLocked) {
          Icon(
            imageVector = PenLabIcons.Lock,
            contentDescription = null,
            tint = if (viewPanelOpen) scheme.onPrimaryContainer else scheme.primary,
            modifier = Modifier.size(11.dp),
          )
        }
        Text(
          text = "$zoom%",
          color = if (viewPanelOpen) scheme.onPrimaryContainer else scheme.onSurface,
          style = PenLabTextStyles.tabularLabel,
          maxLines = 1,
          softWrap = false,
        )
      }
    }
    ChromePillIconButton(
      icon = PenLabIcons.Plus,
      contentDescription = "Zoom in",
      onClick = onZoomIn,
      iconSize = PenLabLayout.iconCompact,
    )

    ChromePillDivider()

    ChromePillIconButton(
      icon = PenLabIcons.FullScreen,
      contentDescription = "Full screen",
      onClick = onEnterFullScreen,
    )

    ChromePillDivider()

    // Page Config button (Pages / Paper / Layers) + compact tabular page counter for A4
    Box(
      modifier =
        Modifier
          .height(BarHeight)
          .widthIn(min = PenLabSpacing.touchTarget)
          .insetPillClickable(
            pageConfigInteraction,
            onTogglePageConfig,
            if (pageConfigOpen) PenLabComponentTokens.toolCellSelectedBg else Color.Transparent,
          )
          .padding(horizontal = if (isPaged) 10.dp else 11.dp)
          .semantics {
            contentDescription =
              if (isPaged) "Page config, page $pageNum of $pageCount. Double tap for pages, paper and layers."
              else "Page config. Double tap for paper and layers."
            if (pageConfigOpen) selected = true
          },
      contentAlignment = Alignment.Center,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Icon(
          imageVector = PenLabIcons.Pages,
          contentDescription = null,
          tint = if (pageConfigOpen) scheme.onPrimaryContainer else scheme.onSurface,
          modifier = Modifier.size(19.dp),
        )
        if (isPaged) {
          Text(
            text = "$pageNum/$pageCount",
            color = if (pageConfigOpen) scheme.onPrimaryContainer else scheme.onSurface,
            style = PenLabTextStyles.tabularMedium,
            maxLines = 1,
            softWrap = false,
          )
        }
      }
    }
  }
}

/**
 * Anchored popover under [PageConfigChromePill] with a segmented switcher between:
 * - **Pages** (A4 notebooks only), **Paper** (9 styles + spacing + Light/Dark paper toggle), and **Layers**.
 */
@Composable
fun PageConfigPopover(
  isPaged: Boolean,
  isPdfNotebook: Boolean,
  selectedTab: PageConfigTab,
  onSelectTab: (PageConfigTab) -> Unit,
  // Pages tab state
  pageCount: Int,
  currentPage: Int,
  bookmarks: Set<Int>,
  contentVersion: Int,
  thumbnail: (page: Int, widthPx: Int) -> Bitmap?,
  onJumpPage: (Int) -> Unit,
  onToggleBookmark: (Int) -> Unit,
  onInsertPage: (at: Int) -> Unit,
  onDeletePage: (Int) -> Unit,
  // Paper tab state
  pageStyle: PageStyle,
  darkPage: Boolean,
  rulingMm: Float,
  onStylePicked: (PageStyle) -> Unit,
  onSpacing: (Float) -> Unit,
  // Layers tab state
  layerNames: List<String>,
  hiddenLayers: Set<Int>,
  currentLayer: Int,
  onSelectLayer: (Int) -> Unit,
  onLayerVisible: (Int, Boolean) -> Unit,
  onRenameLayer: (Int) -> Unit,
  onDeleteLayer: (Int) -> Unit,
  onAddLayer: () -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
) {
  val tabs =
    remember(isPaged) {
      if (isPaged) listOf(PageConfigTab.PAGES, PageConfigTab.PAPER, PageConfigTab.LAYERS)
      else listOf(PageConfigTab.PAPER, PageConfigTab.LAYERS)
    }
  val activeTab = if (selectedTab in tabs) selectedTab else tabs.first()
  val width = if (activeTab == PageConfigTab.PAGES) 424.dp else 332.dp

  PenLabPopoverSurface(
    isPalm = isPalm,
    modifier = modifier.width(width).heightIn(max = 616.dp),
    contentPadding = PaddingValues(PenLabSpacing.md),
    verticalArrangement = Arrangement.spacedBy(PenLabSpacing.md),
  ) {
    PenLabSegmentedControl(
      options = tabs,
      selected = activeTab,
      onSelect = onSelectTab,
      label = { it.label },
      icon = { tab ->
        when (tab) {
          PageConfigTab.PAGES -> PenLabIcons.Pages
          PageConfigTab.PAPER -> PenLabIcons.Paper
          PageConfigTab.LAYERS -> PenLabIcons.Layers
        }
      },
    )

    when (activeTab) {
      PageConfigTab.PAGES -> {
        PagesPanelContent(
          count = pageCount,
          currentPage = currentPage,
          bookmarks = bookmarks,
          version = contentVersion,
          thumbnail = thumbnail,
          onJump = onJumpPage,
          onToggleBookmark = onToggleBookmark,
          onInsert = onInsertPage,
          onDelete = onDeletePage,
          showHeading = false,
        )
      }
      PageConfigTab.PAPER -> {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          PaperPanelContent(
            style = pageStyle,
            darkPage = darkPage,
            onStylePicked = onStylePicked,
            spacingMm = rulingMm,
            onSpacing = onSpacing,
            isPdfNotebook = isPdfNotebook,
            showPaperModeToggle = true,
          )
        }
      }
      PageConfigTab.LAYERS -> {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          LayersPanelContent(
            names = layerNames,
            hidden = hiddenLayers,
            current = currentLayer,
            onSelect = onSelectLayer,
            onVisible = onLayerVisible,
            onRename = onRenameLayer,
            onDelete = onDeleteLayer,
            onAdd = onAddLayer,
            showHeading = false,
          )
        }
      }
    }
  }
}

/** Clean dialog for moving the open notebook to another folder (or creating a new folder path). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoveNotebookDialog(
  notebookTitle: String,
  currentFolder: String,
  folders: List<String>,
  onDismiss: () -> Unit,
  onMove: (String) -> Unit,
) {
  var folderInput by remember(currentFolder) { mutableStateOf(currentFolder) }
  val scheme = MaterialTheme.colorScheme

  PenLabDialog(
    title = "Move notebook",
    subtitle = "Choose a folder for “$notebookTitle” or type a new folder path.",
    onDismiss = onDismiss,
    primaryLabel = "Move",
    onPrimaryClick = { onMove(folderInput) },
  ) {
    OutlinedTextField(
      value = folderInput,
      onValueChange = { folderInput = it },
      label = { Text("Folder (blank for top level)") },
      singleLine = true,
      colors =
        OutlinedTextFieldDefaults.colors(
          focusedTextColor = scheme.onSurface,
          unfocusedTextColor = scheme.onSurface,
          focusedBorderColor = scheme.primary,
          unfocusedBorderColor = scheme.outlineVariant,
          focusedLabelColor = scheme.primary,
          unfocusedLabelColor = scheme.onSurfaceVariant,
          cursorColor = scheme.primary,
        ),
      modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
      verticalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
    ) {
      PenLabChoiceChip(
        label = "Top level",
        selected = folderInput.trim().isEmpty(),
        onClick = { folderInput = "" },
      )
      for (folder in folders) {
        PenLabChoiceChip(
          label = folder.replace("/", " › "),
          selected = folderInput.trim() == folder,
          onClick = { folderInput = folder },
        )
      }
    }
  }
}

@Composable
private fun ChromePillDivider() {
  Box(
    modifier = Modifier.width(5.dp).height(BarHeight),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier
        .width(1.dp)
        .height(20.dp)
        .background(MaterialTheme.colorScheme.outlineVariant),
    )
  }
}

@Composable
private fun ChromePillIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  iconSize: Dp = PenLabLayout.iconMedium,
  selected: Boolean = false,
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val pressScale by animateFloatAsState(
    targetValue = if (pressed) 0.94f else 1f,
    animationSpec = Motion.spring(dampingRatio = 0.85f, stiffness = 700f),
    label = "chromeBtnPress",
  )
  val bg by animateColorAsState(
    targetValue = if (selected) PenLabComponentTokens.toolCellSelectedBg else Color.Transparent,
    animationSpec = Motion.tween(120),
    label = "chromeBtnBg",
  )
  val tint = if (selected) PenLabComponentTokens.toolIconActive else PenLabComponentTokens.toolIconDefault

  Box(
    modifier =
      Modifier
        .size(width = PenLabSpacing.touchTarget, height = BarHeight)
        .clip(CircleShape)
        .clickable(
          interactionSource = interaction,
          indication = ripple(bounded = true, radius = 22.dp),
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
          .size(PenLabLayout.controlVisual)
          .clip(CircleShape)
          .background(bg),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(iconSize),
      )
    }
  }
}
