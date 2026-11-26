package dev.penlab.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import dev.penlab.app.notes.NotebookInfo
import dev.penlab.app.notes.NotebookKind
import dev.penlab.app.ink.PageStyle
import dev.penlab.app.ui.components.PenLabChoiceChip
import dev.penlab.app.ui.components.PenLabDialog
import dev.penlab.app.ui.components.PenLabEmptyState
import dev.penlab.app.ui.components.PenLabIconButton
import dev.penlab.app.ui.components.PenLabMenuItem
import dev.penlab.app.ui.components.PenLabSectionHeader
import dev.penlab.app.ui.theme.FolderPalette
import dev.penlab.app.ui.theme.LocalPenLabColors
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.PenLabLayout
import dev.penlab.app.ui.theme.PenLabShapes
import dev.penlab.app.ui.theme.PenLabSpacing

/**
 * The library, one folder at a time: a calm header (search, Import menu, New folder, Settings,
 * New notebook), the folder trail, then at the top level a Continue strip and one shelf per
 * course, or inside a folder its folders and notebooks. The trail goes back up; so does Back.
 * Each card has a visible three-dot menu, and long-press opens the same one.
 */
@Composable
fun LibraryScreen(
  /** Notes are in the app's private storage: offer to keep them in Documents/PenLab (see [StorageBanner]). */
  storageBanner: Boolean,
  onAllowStorage: () -> Unit,
  onDismissStorageBanner: () -> Unit,
  /** Opens the system folder picker; every PDF in it (and its subfolders) is imported. */
  onImportFolder: () -> Unit,
  /** Handwriting search across every notebook (see [LibrarySearchResults]). */
  searchQuery: String,
  onSearchQuery: (String) -> Unit,
  searchResults: List<dev.penlab.app.search.LibrarySearch.Result>,
  /** (notebooks read, notebooks to read) while catching up, else null. */
  searchReading: Pair<Int, Int>?,
  onOpenResult: (dev.penlab.app.search.LibrarySearch.Result) -> Unit,
  notebooks: List<NotebookInfo>,
  folders: List<String>,
  /** The folder being shown ("" = top level). Kept by the caller so it survives opening a notebook. */
  current: String,
  onFolderChange: (String) -> Unit,
  darkPage: Boolean,
  messages: SnackbarHostState,
  onOpen: (NotebookInfo) -> Unit,
  onCreate: (title: String, folder: String, kind: NotebookKind, style: PageStyle, landscape: Boolean) -> Unit,
  onCreateFolder: (String) -> Unit,
  /** Opens the system file picker for importable files; they are imported into [current]. */
  onImportPdf: () -> Unit,
  /** Labels of the importers that exist (the registry's): the Import menu names these formats. */
  importFormats: List<String> = emptyList(),
  onRenameFolder: (from: String, to: String) -> Unit,
  /** Folder colours by path (see [folderColor] for inheritance). */
  folderColors: Map<String, Int>,
  onFolderColor: (path: String, color: Int?) -> Unit,
  onDeleteFolder: (String) -> Unit,
  onRename: (NotebookInfo, String) -> Unit,
  onMove: (NotebookInfo, String) -> Unit,
  onDelete: (NotebookInfo) -> Unit,
  onExport: (NotebookInfo) -> Unit,
  onShare: (NotebookInfo) -> Unit,
  onTurn: (NotebookInfo) -> Unit,
  onOpenSettings: () -> Unit,
  /** The Welcome sheet is wanted (first run, or opened from Settings); [onWelcomeClosed] ends it for good. */
  showWelcome: Boolean,
  onWelcomeClosed: () -> Unit,
  /** Where a notebook's cover picture is (it may not exist yet). */
  coverFile: (NotebookInfo, Boolean) -> java.io.File,
) {
  var creating by remember { mutableStateOf(false) }
  var creatingFolder by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf<NotebookInfo?>(null) }
  var moving by remember { mutableStateOf<NotebookInfo?>(null) }
  var renamingFolder by remember { mutableStateOf<String?>(null) }
  var movingFolder by remember { mutableStateOf<String?>(null) }
  var colouringFolder by remember { mutableStateOf<String?>(null) }

  BackHandler(enabled = current.isNotEmpty()) { onFolderChange(current.substringBeforeLast("/", "")) }

  val subfolders = remember(folders, current) {
    folders.filter { it.substringBeforeLast("/", "") == current && it != current }
  }
  val here = remember(notebooks, current) { notebooks.filter { it.folder == current } }
  // Notebooks whose title matches come first, above the handwriting matches.
  val titleHits = remember(searchQuery, notebooks) { titleMatches(searchQuery, notebooks) }

  Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
      LibraryHeader(
        current = current,
        searchQuery = searchQuery,
        onSearchQuery = onSearchQuery,
        searchReading = searchReading,
        onImportPdf = onImportPdf,
        importFormats = importFormats,
        onImportFolder = onImportFolder,
        onNewFolder = { creatingFolder = true },
        onOpenSettings = onOpenSettings,
        onNewNotebook = { creating = true },
        // An empty library or folder has its own "New notebook" button in the middle; that one is
        // the screen's primary, so this one steps back to the tonal style of its neighbours.
        newNotebookIsPrimary = !(searchQuery.isBlank() && subfolders.isEmpty() && here.isEmpty()),
      )
      FolderTrail(current, folderColors, onGo = onFolderChange)

      if (storageBanner) StorageBanner(onAllowStorage, onDismissStorageBanner)

      if (searchQuery.isNotBlank()) {
        LibrarySearchResults(searchQuery, titleHits, searchResults, searchReading, onOpenResult, onOpenNotebook = onOpen)
      } else if (subfolders.isEmpty() && here.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          if (current.isEmpty()) EmptyLibraryState(onNewNotebook = { creating = true }, onImportPdf = onImportPdf)
          else EmptyFolderState(current.substringAfterLast("/"), onNewNotebook = { creating = true }, onImportPdf = onImportPdf)
        }
      } else {
        LibraryGrid(
          notebooks = notebooks,
          folders = folders,
          current = current,
          subfolders = subfolders,
          here = here,
          darkPage = darkPage,
          folderColors = folderColors,
          coverFile = coverFile,
          onOpen = onOpen,
          onFolderChange = onFolderChange,
          onRename = { renaming = it },
          onMove = { moving = it },
          onDelete = onDelete,
          onExport = onExport,
          onShare = onShare,
          onTurn = onTurn,
          onRenameFolder = { renamingFolder = it },
          onMoveFolder = { movingFolder = it },
          onColorFolder = { colouringFolder = it },
          onDeleteFolder = onDeleteFolder,
        )
      }
    }

    SnackbarHost(messages, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = PenLabSpacing.lg)) { data ->
      ToolbarMessage(data.visuals.message, data.visuals.actionLabel, onAction = data::performAction)
    }
  }

  if (showWelcome) {
    val first = startWritingCreatesNotebook(notebooks.size)
    FirstLaunchSheet(
      startLabel = if (first) "Start writing" else "Done",
      onSkip = onWelcomeClosed,
      onStart = {
        onWelcomeClosed()
        if (first) onCreate("", current, FallbackPaperDefaults.kind, FallbackPaperDefaults.style, FallbackPaperDefaults.landscape)
      },
      onImportPdf = {
        onWelcomeClosed()
        onImportPdf()
      },
    )
  }
  if (creating) {
    NewNotebookDialog(
      folder = current,
      onDismiss = { creating = false },
      onCreate = { title, kind, style, landscape ->
        creating = false
        onCreate(title, current, kind, style, landscape)
      },
    )
  }
  if (creatingFolder) {
    val where = if (current.isEmpty()) "" else " in ${current.substringAfterLast("/")}"
    TextPromptDialog("New folder$where", "Name (e.g. Mechanics)", "", "Create", onDismiss = { creatingFolder = false }) { name ->
      creatingFolder = false
      if (name.isNotBlank()) onCreateFolder(if (current.isEmpty()) name else "$current/$name")
    }
  }
  renamingFolder?.let { folder ->
    TextPromptDialog("Rename folder", "Name", folder.substringAfterLast("/"), "Rename", onDismiss = { renamingFolder = null }) { name ->
      renamingFolder = null
      val parent = folder.substringBeforeLast("/", "")
      if (name.isNotBlank()) onRenameFolder(folder, if (parent.isEmpty()) name else "$parent/$name")
    }
  }
  renaming?.let { notebook ->
    TextPromptDialog("Rename notebook", "Title", notebook.title, "Rename", onDismiss = { renaming = null }) {
      renaming = null
      onRename(notebook, it)
    }
  }
  movingFolder?.let { folder ->
    val name = folder.substringAfterLast("/")
    // Anywhere except inside itself.
    val places = folders.filter { it != folder && !it.startsWith("$folder/") }
    FolderMoveDialog(name, folder.substringBeforeLast("/", ""), places, onDismiss = { movingFolder = null }) { parent ->
      movingFolder = null
      onRenameFolder(folder, if (parent.isEmpty()) name else "$parent/$name")
    }
  }
  colouringFolder?.let { folder ->
    FolderColorDialog(folder.substringAfterLast("/"), folderColors[folder], onDismiss = { colouringFolder = null }) { color ->
      colouringFolder = null
      onFolderColor(folder, color)
    }
  }
  moving?.let { notebook ->
    MoveDialog(notebook, folders, onDismiss = { moving = null }) {
      moving = null
      onMove(notebook, it)
    }
  }
}

/**
 * Search pill on the left; Import menu, New folder, Settings and the primary New notebook pill on
 * the right. In portrait the search gets its own line so the buttons never get squashed.
 */
@Composable
private fun LibraryHeader(
  current: String,
  searchQuery: String,
  onSearchQuery: (String) -> Unit,
  searchReading: Pair<Int, Int>?,
  onImportPdf: () -> Unit,
  importFormats: List<String>,
  onImportFolder: () -> Unit,
  onNewFolder: () -> Unit,
  onOpenSettings: () -> Unit,
  onNewNotebook: () -> Unit,
  newNotebookIsPrimary: Boolean,
) {
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val narrow = maxWidth < PenLabLayout.headerNarrowBreakpoint
    val actions: @Composable () -> Unit = {
      ImportMenuButton(current, importFormats, onImportPdf, onImportFolder)
      PenLabIconButton(icon = PenLabIcons.FolderPlus, contentDescription = "New folder", onClick = onNewFolder)
      PenLabIconButton(icon = PenLabIcons.Settings, contentDescription = "Settings", onClick = onOpenSettings)
      Spacer(Modifier.width(PenLabSpacing.sm))
      val scheme = MaterialTheme.colorScheme
      val label: @Composable RowScope.() -> Unit = {
        Icon(PenLabIcons.FilePlus, contentDescription = null, modifier = Modifier.size(PenLabLayout.iconMedium))
        Spacer(Modifier.width(PenLabSpacing.sm))
        Text("New notebook", style = MaterialTheme.typography.labelLarge, maxLines = 1)
      }
      val buttonPadding = PaddingValues(horizontal = PenLabSpacing.lg, vertical = PenLabSpacing.sm)
      if (newNotebookIsPrimary) {
        Button(
          onClick = onNewNotebook,
          shape = PenLabShapes.pill,
          colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
          modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
          contentPadding = buttonPadding,
          content = label,
        )
      } else {
        FilledTonalButton(
          onClick = onNewNotebook,
          shape = PenLabShapes.pill,
          colors = ButtonDefaults.filledTonalButtonColors(containerColor = PenLabComponentTokens.chipIdleBg, contentColor = scheme.onSurface),
          border = BorderStroke(PenLabLayout.hairline, scheme.outline),
          modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
          contentPadding = buttonPadding,
          content = label,
        )
      }
    }
    val padding = Modifier.fillMaxWidth().padding(start = PenLabLayout.libraryGutter, end = PenLabLayout.libraryGutter, top = PenLabLayout.libraryGutter, bottom = PenLabSpacing.sm)
    if (narrow) {
      Column(padding) {
        PenLabSearchField(searchQuery, onSearchQuery, Modifier.fillMaxWidth(), searchReading)
        Row(Modifier.fillMaxWidth().padding(top = PenLabSpacing.md), verticalAlignment = Alignment.CenterVertically) {
          Spacer(Modifier.weight(1f))
          actions()
        }
      }
    } else {
      Row(padding, verticalAlignment = Alignment.CenterVertically) {
        PenLabSearchField(searchQuery, onSearchQuery, Modifier.width(PenLabLayout.searchWidth), searchReading)
        Spacer(Modifier.weight(1f))
        actions()
      }
    }
  }
}

/** One "Import" pill that opens a menu: PDF files or a whole folder (see [importMenuEntries]). */
@Composable
private fun ImportMenuButton(current: String, formats: List<String>, onImportPdf: () -> Unit, onImportFolder: () -> Unit) {
  var open by remember { mutableStateOf(false) }
  val scheme = MaterialTheme.colorScheme
  Box {
    FilledTonalButton(
      onClick = { open = true },
      shape = PenLabShapes.pill,
      colors = ButtonDefaults.filledTonalButtonColors(containerColor = PenLabComponentTokens.chipIdleBg, contentColor = scheme.onSurface),
      border = BorderStroke(PenLabLayout.hairline, scheme.outline), // the pale fill alone is ~1.1:1 on the page
      modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget).semantics { contentDescription = "Import" },
      contentPadding = PaddingValues(start = PenLabSpacing.lg, end = PenLabSpacing.md, top = PenLabSpacing.sm, bottom = PenLabSpacing.sm),
    ) {
      Icon(PenLabIcons.Save, contentDescription = null, modifier = Modifier.size(PenLabLayout.iconMedium))
      Spacer(Modifier.width(PenLabSpacing.sm))
      Text("Import", style = MaterialTheme.typography.labelLarge, maxLines = 1)
      Icon(PenLabIcons.ChevronDown, contentDescription = null, modifier = Modifier.padding(start = PenLabSpacing.xs).size(PenLabLayout.iconSmall))
    }
    LibraryMenu(open, onDismiss = { open = false }) {
      for (entry in importMenuEntries(current, formats)) {
        PenLabMenuItem(
          label = entry.label,
          subtitle = entry.subtitle,
          icon = if (entry.option == ImportOption.PDF_FILES) PenLabIcons.Pdf else PenLabIcons.Folder,
          onClick = {
            open = false
            if (entry.option == ImportOption.PDF_FILES) onImportPdf() else onImportFolder()
          },
        )
      }
    }
  }
}

/**
 * "Notebooks › Mechanics › Lectures": ancestors are tappable, the folder you are in is the big
 * Bricolage title with its colour dot. Scrolled to the end so the active folder is always visible.
 */
@Composable
private fun FolderTrail(current: String, folderColors: Map<String, Int>, onGo: (String) -> Unit) {
  val crumbs = remember(current) { breadcrumbs(current) }
  val scheme = MaterialTheme.colorScheme
  val scroll = rememberScrollState()
  LaunchedEffect(current, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
  Row(
    Modifier.fillMaxWidth().padding(horizontal = PenLabLayout.libraryGutter).horizontalScroll(scroll),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    crumbs.forEachIndexed { i, crumb ->
      val last = i == crumbs.lastIndex
      if (last) {
        folderColor(crumb.path, folderColors)?.takeIf { crumb.path.isNotEmpty() }?.let {
          Box(Modifier.size(PenLabLayout.dotLarge).background(Color(it), CircleShape))
          Spacer(Modifier.width(PenLabSpacing.sm))
        }
        Text(
          crumb.label,
          color = scheme.onSurface,
          style = MaterialTheme.typography.headlineSmall,
          maxLines = 1,
          modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget).padding(vertical = PenLabSpacing.sm).semantics { heading() },
        )
      } else {
        Box(
          Modifier.clip(PenLabShapes.small).clickable(role = Role.Button) { onGo(crumb.path) }
            .heightIn(min = PenLabSpacing.touchTarget).padding(horizontal = PenLabSpacing.sm),
          contentAlignment = Alignment.Center,
        ) {
          Text(crumb.label, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
        Icon(PenLabIcons.ChevronRight, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = PenLabSpacing.xs).size(PenLabLayout.iconSmall))
      }
    }
  }
}

@Composable
private fun LibraryGrid(
  notebooks: List<NotebookInfo>,
  folders: List<String>,
  current: String,
  subfolders: List<String>,
  here: List<NotebookInfo>,
  darkPage: Boolean,
  folderColors: Map<String, Int>,
  coverFile: (NotebookInfo, Boolean) -> java.io.File,
  onOpen: (NotebookInfo) -> Unit,
  onFolderChange: (String) -> Unit,
  onRename: (NotebookInfo) -> Unit,
  onMove: (NotebookInfo) -> Unit,
  onDelete: (NotebookInfo) -> Unit,
  onExport: (NotebookInfo) -> Unit,
  onShare: (NotebookInfo) -> Unit,
  onTurn: (NotebookInfo) -> Unit,
  onRenameFolder: (String) -> Unit,
  onMoveFolder: (String) -> Unit,
  onColorFolder: (String) -> Unit,
  onDeleteFolder: (String) -> Unit,
) {
  val recent = remember(notebooks, current) { continueNotebooks(notebooks, current) }
  // A term folder (only folders inside, and those have folders of their own, e.g.
  // Fall 2026 › Mechanics of Materials › Lectures) opens up into its courses.
  fun shelvesOf(folder: String): List<String> {
    val kids = folders.filter { it.substringBeforeLast("/", "") == folder }
    val direct = notebooks.any { it.folder == folder }
    val isTerm = !direct && kids.isNotEmpty() && kids.all { k -> folders.any { it.substringBeforeLast("/", "") == k } }
    return if (isTerm) kids.flatMap(::shelvesOf) else listOf(folder)
  }
  val shelves = remember(subfolders, folders, notebooks, current) { if (current.isEmpty()) subfolders.flatMap(::shelvesOf) else emptyList() }
  // Section headings only when there is more than one section to tell apart, and never one that
  // repeats the page title (see sectionHeadings).
  val headings = sectionHeadings(recent.isNotEmpty(), subfolders.isNotEmpty(), here.isNotEmpty(), current)
  val full: GridSpan = { GridItemSpan(maxLineSpan) }

  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = PenLabLayout.cardMinWidth),
    contentPadding = PaddingValues(start = PenLabLayout.libraryGutter, end = PenLabLayout.libraryGutter, top = PenLabSpacing.sm, bottom = PenLabSpacing.xxl),
    horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.xl),
    verticalArrangement = Arrangement.spacedBy(PenLabSpacing.lg),
  ) {
    // Continue: the notebooks touched last, wherever they are, at the top level.
    if (recent.isNotEmpty()) {
      item(key = "continue", span = full, contentType = "continue") {
        Column {
          if (headings.continueStrip) PenLabSectionHeader(CONTINUE_HEADING, topPadding = PenLabSpacing.xs)
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.xl)) {
            for (notebook in recent) {
              NotebookCard(
                notebook, darkPage, coverFile,
                onOpen = { onOpen(notebook) }, onRename = { onRename(notebook) }, onMove = { onMove(notebook) },
                onDelete = { onDelete(notebook) }, onExport = { onExport(notebook) }, onShare = { onShare(notebook) }, onTurn = { onTurn(notebook) },
                modifier = Modifier.width(PenLabLayout.continueCardWidth),
                place = notebook.folder.substringAfterLast("/").ifEmpty { null },
                accent = folderColor(notebook.folder, folderColors),
              )
            }
          }
        }
      }
    }
    if (headings.folders) {
      item(key = "folders-heading", span = full, contentType = "heading") {
        PenLabSectionHeader(if (current.isEmpty()) COURSES_HEADING else FOLDERS_HEADING, topPadding = PenLabSpacing.xs)
      }
    }
    // Course shelves: at the top level each folder is a row of its notebooks, newest first;
    // the folder name (or Open) opens the folder. Inside a folder, its folders are cards.
    if (current.isEmpty()) {
      items(shelves, key = { "shelf:$it" }, span = { GridItemSpan(maxLineSpan) }, contentType = { "shelf" }) { folder ->
        val inside = notebooks.filter { it.folder == folder || it.folder.startsWith("$folder/") }.sortedByDescending { it.modified }
        CourseShelf(
          name = folder.substringAfterLast("/"),
          parent = folder.substringBeforeLast("/", "").replace("/", " › "),
          count = inside.size,
          color = folderColor(folder, folderColors),
          onOpenFolder = { onFolderChange(folder) },
          onRename = { onRenameFolder(folder) },
          onMove = { onMoveFolder(folder) },
          onColor = { onColorFolder(folder) },
          onDelete = { onDeleteFolder(folder) },
        ) {
          for (notebook in inside.take(SHELF_LIMIT)) {
            NotebookCard(
              notebook, darkPage, coverFile,
              onOpen = { onOpen(notebook) }, onRename = { onRename(notebook) }, onMove = { onMove(notebook) },
              onDelete = { onDelete(notebook) }, onExport = { onExport(notebook) }, onShare = { onShare(notebook) }, onTurn = { onTurn(notebook) },
              modifier = Modifier.width(PenLabLayout.shelfCardWidth),
              place = notebook.folder.removePrefix(folder).removePrefix("/").substringAfterLast("/").ifEmpty { null },
            )
          }
        }
      }
    } else {
      items(subfolders, key = { "folder:$it" }, contentType = { "folder" }) { folder ->
        FolderCard(
          name = folder.substringAfterLast("/"),
          count = notebooks.count { it.folder == folder || it.folder.startsWith("$folder/") },
          color = folderColor(folder, folderColors),
          onOpen = { onFolderChange(folder) },
          onRename = { onRenameFolder(folder) },
          onMove = { onMoveFolder(folder) },
          onColor = { onColorFolder(folder) },
          onDelete = { onDeleteFolder(folder) },
        )
      }
    }
    // Loose notebooks after the folders get their own heading.
    if (headings.notebooks) {
      item(key = "notebooks-heading", span = full, contentType = "heading") {
        PenLabSectionHeader(NOTEBOOKS_HEADING, topPadding = PenLabSpacing.xs)
      }
    }
    items(here, key = { it.id }, contentType = { "notebook" }) { notebook ->
      NotebookCard(
        notebook, darkPage, coverFile,
        onOpen = { onOpen(notebook) }, onRename = { onRename(notebook) }, onMove = { onMove(notebook) },
        onDelete = { onDelete(notebook) }, onExport = { onExport(notebook) }, onShare = { onShare(notebook) }, onTurn = { onTurn(notebook) },
      )
    }
  }
}

private const val SHELF_LIMIT = 12
private typealias GridSpan = androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan

/** Nothing in the library at all: invite a first notebook or a PDF. */
@Composable
internal fun EmptyLibraryState(onNewNotebook: () -> Unit, onImportPdf: () -> Unit) {
  PenLabEmptyState(
    icon = PenLabIcons.FilePlus,
    title = "Your library is empty",
    description = "Start your first notebook or import a PDF exam sheet. Make a folder for each subject to keep them tidy.",
    primaryActionLabel = "New notebook",
    primaryActionIcon = PenLabIcons.FilePlus,
    onPrimaryAction = onNewNotebook,
    secondaryActionLabel = "Import PDF",
    onSecondaryAction = onImportPdf,
  )
}

/** A folder with nothing in it yet. */
@Composable
internal fun EmptyFolderState(folderName: String, onNewNotebook: () -> Unit, onImportPdf: () -> Unit) {
  PenLabEmptyState(
    icon = PenLabIcons.Folder,
    title = "This folder is empty",
    description = "Add a notebook to “$folderName”, or import PDFs straight into it.",
    primaryActionLabel = "New notebook",
    primaryActionIcon = PenLabIcons.FilePlus,
    onPrimaryAction = onNewNotebook,
    secondaryActionLabel = "Import PDF",
    onSecondaryAction = onImportPdf,
  )
}

/** Pick the folder a notebook moves to. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveDialog(notebook: NotebookInfo, folders: List<String>, onDismiss: () -> Unit, onMove: (String) -> Unit) {
  var target by remember { mutableStateOf(notebook.folder) }
  PenLabDialog(title = "Move “${notebook.title}”", onDismiss = onDismiss, primaryLabel = "Move", onPrimaryClick = { onMove(target) }) {
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.sm), verticalArrangement = Arrangement.spacedBy(PenLabSpacing.sm)) {
      PenLabChoiceChip("No folder", target.isEmpty(), onClick = { target = "" })
      for (f in folders) PenLabChoiceChip(f.replace("/", " › "), target == f, onClick = { target = f })
    }
  }
}

/** Where to move a folder: the top level or any other folder (not inside itself). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderMoveDialog(name: String, parent: String, places: List<String>, onDismiss: () -> Unit, onMove: (String) -> Unit) {
  var target by remember { mutableStateOf(parent) }
  PenLabDialog(
    title = "Move folder “$name”",
    onDismiss = onDismiss,
    primaryLabel = "Move",
    onPrimaryClick = { onMove(target) },
    primaryEnabled = target != parent,
  ) {
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.sm), verticalArrangement = Arrangement.spacedBy(PenLabSpacing.sm)) {
      PenLabChoiceChip("Top level", target.isEmpty(), onClick = { target = "" })
      for (f in places) PenLabChoiceChip(f.replace("/", " › "), target == f, onClick = { target = f })
    }
  }
}

/** A folder's colour: its own, or the nearest parent's, or none. */
fun folderColor(path: String, colors: Map<String, Int>): Int? {
  var p = path
  while (true) {
    colors[p]?.let { return it }
    if (!p.contains('/')) return null
    p = p.substringBeforeLast('/')
  }
}

/**
 * Pick a folder's colour. Tapping a swatch applies it at once; "No colour" is one more choice in
 * the row, not a button, so it only looks chosen when it is the current state. The current choice
 * has a ring and a check, never fill alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderColorDialog(name: String, current: Int?, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val edge = LocalPenLabColors.current.swatchEdge
  val lightCheck = if (LocalPenLabColors.current.isDark) scheme.onSurface else scheme.surface
  val darkCheck = if (LocalPenLabColors.current.isDark) scheme.surface else scheme.onSurface
  PenLabDialog(
    title = "Colour for “$name”",
    onDismiss = onDismiss,
    primaryLabel = "Done",
    onPrimaryClick = onDismiss,
    secondaryLabel = null,
    onSecondaryClick = null,
    subtitle = "Folders inside take this colour unless they have their own.",
  ) {
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.sm), verticalArrangement = Arrangement.spacedBy(PenLabSpacing.sm)) {
      FolderColorSwatch(null, selected = current == null, description = "No colour", edge = edge, checkTint = scheme.onSurface) { onPick(null) }
      for (c in FolderPalette) {
        val swatch = Color(c)
        FolderColorSwatch(
          swatch,
          selected = c == current,
          description = "Colour ${FolderPalette.indexOf(c) + 1}",
          edge = edge,
          checkTint = swatchCheckTint(swatch, lightCheck, darkCheck),
        ) { onPick(c) }
      }
    }
  }
}

/** One round colour choice (48dp target): a ring and a check mark when chosen; [color] null is "No colour" (an empty circle with a cross). */
@Composable
private fun FolderColorSwatch(color: Color?, selected: Boolean, description: String, edge: Color, checkTint: Color, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  Box(
    Modifier.size(PenLabSpacing.touchTarget).clip(CircleShape)
      .then(if (selected) Modifier.border(PenLabLayout.ringSelected, scheme.primary, CircleShape) else Modifier)
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
      .semantics { contentDescription = description + if (selected) ", chosen" else "" },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier.size(PenLabLayout.swatchSize).clip(CircleShape).background(color ?: Color.Transparent).border(PenLabLayout.hairline, edge, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      when {
        selected -> Icon(PenLabIcons.CheckBare, contentDescription = null, tint = checkTint, modifier = Modifier.size(PenLabLayout.iconMedium))
        color == null -> Icon(PenLabIcons.Close, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(PenLabLayout.iconMedium))
      }
    }
  }
}

/** One text field. Used for rename and new folder. */
@Composable
private fun TextPromptDialog(
  title: String,
  label: String,
  initial: String,
  confirm: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var value by remember { mutableStateOf(initial) }
  PenLabDialog(title = title, onDismiss = onDismiss, primaryLabel = confirm, onPrimaryClick = { onConfirm(value) }) {
    PenLabTextField(value, { value = it }, label)
  }
}

@Composable
internal fun PenLabTextField(value: String, onChange: (String) -> Unit, label: String, minLines: Int = 1) {
  val focus = LocalFocusManager.current
  OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label) },
    singleLine = minLines == 1,
    minLines = minLines,
    // Done puts the keyboard away instead of leaving it over the rest of the dialog (a
    // several-line field keeps Enter for new lines).
    keyboardOptions = if (minLines == 1) KeyboardOptions(imeAction = ImeAction.Done) else KeyboardOptions.Default,
    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
    shape = PenLabShapes.small,
    colors =
      OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = MaterialTheme.colorScheme.primary,
      ),
    modifier = Modifier.fillMaxWidth(),
  )
}

/**
 * A calm, dismissible note above the notebooks: without "All files access" the notes sit in the
 * app's private storage and vanish if the app is uninstalled. Shown until allowed or dismissed
 * (dismissal lasts for the session; there is no dialog).
 */
@Composable
private fun StorageBanner(onAllow: () -> Unit, onDismiss: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  Row(
    Modifier.fillMaxWidth().padding(horizontal = PenLabLayout.libraryGutter, vertical = PenLabSpacing.xs)
      .clip(PenLabShapes.medium).background(PenLabComponentTokens.chipIdleBg).padding(start = PenLabSpacing.lg, end = PenLabSpacing.xs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      "Keep your notes safe: store them in Documents/PenLab so they survive reinstalling the app.",
      Modifier.weight(1f).padding(vertical = PenLabSpacing.md),
      color = scheme.onSurface,
      style = MaterialTheme.typography.bodyMedium,
    )
    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget)) {
      Text("Not now", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
    TextButton(onClick = onAllow, modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget)) {
      Text("Allow", color = scheme.primary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
  }
}
