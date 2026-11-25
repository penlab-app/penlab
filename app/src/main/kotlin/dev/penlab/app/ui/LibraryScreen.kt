package dev.penlab.app.ui

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.layout.imePadding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.penlab.app.ink.InkColors
import dev.penlab.app.ink.PageBackground
import dev.penlab.app.ink.PageStyle
import dev.penlab.app.notes.NotebookInfo
import dev.penlab.app.notes.NotebookKind
import dev.penlab.app.ui.components.PenLabIconButton
import dev.penlab.app.ui.theme.PenLabColors
import dev.penlab.app.ui.theme.PenLabComponentTokens
import java.text.DateFormat
import java.util.Date

/**
 * The library, one folder at a time: the folders inside the current one (one per subject, say),
 * then its notebooks, newest first. The path along the top goes back up; so does Back.
 * Long-press a folder or notebook for its options.
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
  /** Opens the system file picker for PDFs; they are imported into [current]. */
  onImportPdf: () -> Unit,
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
  onReport: () -> Unit,
  /** Where a notebook's cover picture is (it may not exist yet). */
  coverFile: (NotebookInfo) -> java.io.File,
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

  Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
      // Portrait: the search box gets its own line, or the buttons get squashed.
      androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 1000.dp
        val buttons: @Composable () -> Unit = {
          TextButton(onClick = onReport) {
            Text("Bug / idea", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.labelLarge, maxLines = 1)
          }
          TextButton(onClick = onImportPdf) {
            Text("Import files", color = PenLabColors.icon, style = MaterialTheme.typography.labelLarge, maxLines = 1)
          }
          TextButton(onClick = onImportFolder) {
            Text("Import folder", color = PenLabColors.icon, style = MaterialTheme.typography.labelLarge, maxLines = 1)
          }
          TextButton(onClick = { creatingFolder = true }) {
            Text("New folder", color = PenLabColors.icon, style = MaterialTheme.typography.labelLarge, maxLines = 1)
          }
          PenLabIconButton(
            icon = PenLabIcons.Settings,
            contentDescription = "Settings",
            onClick = onOpenSettings,
          )
          Spacer(Modifier.width(8.dp))
          Button(
            onClick = { creating = true },
            colors = ButtonDefaults.buttonColors(containerColor = PenLabColors.accent, contentColor = PenLabColors.onAccent),
          ) {
            Text("New notebook", style = MaterialTheme.typography.labelLarge, maxLines = 1)
          }
        }
        // The path has its own line below, so deep folders ("Fall 2026 › Mechanics of Materials")
        // are never cut off by the buttons.
        if (narrow) {
          Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)) {
            LibrarySearchField(searchQuery, onSearchQuery)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
              Spacer(Modifier.weight(1f))
              buttons()
            }
          }
        } else {
          Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            LibrarySearchField(searchQuery, onSearchQuery)
            Spacer(Modifier.weight(1f))
            buttons()
          }
        }
      }
      // Scrolled to the end, so the folder you are in is always the part you see (portrait).
      val pathScroll = androidx.compose.foundation.rememberScrollState()
      androidx.compose.runtime.LaunchedEffect(current, pathScroll.maxValue) { pathScroll.scrollTo(pathScroll.maxValue) }
      Breadcrumbs(
        current,
        onGo = onFolderChange,
        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = 8.dp)
          .horizontalScroll(pathScroll),
      )

      if (storageBanner) StorageBanner(onAllowStorage, onDismissStorageBanner)

      if (searchQuery.isNotBlank()) {
        LibrarySearchResults(searchQuery, searchResults, searchReading, onOpenResult)
      } else if (subfolders.isEmpty() && here.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          val empty = if (current.isEmpty()) "No notebooks yet. Make a folder for each subject, or start a notebook." else "This folder is empty."
          Text(empty, color = PenLabColors.iconDisabled, style = MaterialTheme.typography.bodyLarge)
        }
      } else {
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 150.dp),
          contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 48.dp),
          horizontalArrangement = Arrangement.spacedBy(24.dp),
          verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
          // Continue: the notebooks touched last, wherever they are, at the top level.
          val recent = if (current.isEmpty()) notebooks.sortedByDescending { it.modified }.take(4) else emptyList()
          if (recent.size >= 2) {
            item(key = "continue", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }, contentType = "continue") {
              Column {
                Text("Continue", color = PenLabColors.icon, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                  for (notebook in recent) {
                    Column(Modifier.width(190.dp)) {
                      NotebookCard(
                        notebook,
                        darkPage,
                        coverFile = coverFile(notebook),
                        onOpen = { onOpen(notebook) },
                        onRename = { renaming = notebook },
                        onMove = { moving = notebook },
                        onDelete = { onDelete(notebook) },
                        onExport = { onExport(notebook) },
                        onShare = { onShare(notebook) },
                        onTurn = { onTurn(notebook) },
                        place = notebook.folder.substringAfterLast("/").ifEmpty { null },
                        accent = folderColor(notebook.folder, folderColors),
                      )
                    }
                  }
                }
                Spacer(Modifier.height(12.dp))
                Text(if (subfolders.isNotEmpty()) "Courses" else "Notebooks", color = PenLabColors.icon, style = MaterialTheme.typography.headlineSmall)
              }
            }
          }
          // Course shelves: at the top level each folder is a row of its
          // notebooks, newest first; the folder name opens the folder.
          if (current.isEmpty()) {
            // A term folder (only folders inside, and those have folders of their own, e.g.
            // Fall 2026 › Mechanics of Materials › Lectures) opens up into its courses.
            fun shelvesOf(folder: String): List<String> {
              val kids = folders.filter { it.substringBeforeLast("/", "") == folder }
              val direct = notebooks.any { it.folder == folder }
              val isTerm = !direct && kids.isNotEmpty() && kids.all { k -> folders.any { it.substringBeforeLast("/", "") == k } }
              return if (isTerm) kids.flatMap(::shelvesOf) else listOf(folder)
            }
            val shelves = subfolders.flatMap(::shelvesOf)
            items(shelves, key = { "shelf:$it" }, span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }, contentType = { "shelf" }) { folder ->
              val inside = notebooks.filter { it.folder == folder || it.folder.startsWith("$folder/") }.sortedByDescending { it.modified }
              CourseShelf(
                name = folder.substringAfterLast("/"),
                parent = folder.substringBeforeLast("/", "").replace("/", " › "),
                count = inside.size,
                color = folderColor(folder, folderColors),
                onOpenFolder = { onFolderChange(folder) },
                onRename = { renamingFolder = folder },
                onMove = { movingFolder = folder },
                onColor = { colouringFolder = folder },
                onDelete = { onDeleteFolder(folder) },
              ) {
                for (notebook in inside.take(12)) {
                  Column(Modifier.width(150.dp)) {
                    NotebookCard(
                      notebook, darkPage, coverFile = coverFile(notebook),
                      onOpen = { onOpen(notebook) }, onRename = { renaming = notebook }, onMove = { moving = notebook },
                      onDelete = { onDelete(notebook) }, onExport = { onExport(notebook) }, onShare = { onShare(notebook) }, onTurn = { onTurn(notebook) },
                      place = notebook.folder.removePrefix(folder).removePrefix("/").substringAfterLast("/").ifEmpty { null },
                    )
                  }
                }
              }
            }
          } else items(subfolders, key = { "folder:$it" }, contentType = { "folder" }) { folder ->
            val inside = notebooks.count { it.folder == folder || it.folder.startsWith("$folder/") }
            FolderTile(
              name = folder.substringAfterLast("/"),
              detail = if (inside == 1) "1 notebook" else "$inside notebooks",
              color = folderColor(folder, folderColors),
              onOpen = { onFolderChange(folder) },
              onRename = { renamingFolder = folder },
              onMove = { movingFolder = folder },
              onColor = { colouringFolder = folder },
              onDelete = { onDeleteFolder(folder) },
            )
          }
          // Loose notebooks after the folders get their own heading, so "Folders" isn't over them.
          if (recent.size >= 2 && subfolders.isNotEmpty() && here.isNotEmpty()) {
            item(key = "notebooks-heading", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }, contentType = "heading") {
              Text("Notebooks", color = PenLabColors.icon, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
            }
          }
          items(here, key = { it.id }, contentType = { "notebook" }) { notebook ->
            NotebookCard(
              notebook,
              darkPage,
              coverFile = coverFile(notebook),
              onOpen = { onOpen(notebook) },
              onRename = { renaming = notebook },
              onMove = { moving = notebook },
              onDelete = { onDelete(notebook) },
              onExport = { onExport(notebook) },
              onShare = { onShare(notebook) },
              onTurn = { onTurn(notebook) },
            )
          }
        }
      }
    }

    SnackbarHost(messages, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 16.dp)) { data ->
      ToolbarMessage(data.visuals.message, data.visuals.actionLabel, onAction = data::performAction)
    }
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
    TextPromptDialog("New folder$where", "Name (e.g. Mechanics)", "", emptyList(), "Create", onDismiss = { creatingFolder = false }) { name ->
      creatingFolder = false
      if (name.isNotBlank()) onCreateFolder(if (current.isEmpty()) name else "$current/$name")
    }
  }
  renamingFolder?.let { folder ->
    TextPromptDialog("Rename folder", "Name", folder.substringAfterLast("/"), emptyList(), "Rename", onDismiss = { renamingFolder = null }) { name ->
      renamingFolder = null
      val parent = folder.substringBeforeLast("/", "")
      if (name.isNotBlank()) onRenameFolder(folder, if (parent.isEmpty()) name else "$parent/$name")
    }
  }
  renaming?.let { notebook ->
    TextPromptDialog("Rename notebook", "Title", notebook.title, emptyList(), "Rename", onDismiss = { renaming = null }) {
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

/** "Notebooks › Mechanics › Lectures", each part tappable. */
@Composable
private fun Breadcrumbs(current: String, onGo: (String) -> Unit, modifier: Modifier = Modifier) {
  val parts = if (current.isEmpty()) emptyList() else current.split("/")
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Crumb("Notebooks", last = parts.isEmpty()) { onGo("") }
    parts.forEachIndexed { i, part ->
      Text("  ›  ", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.headlineSmall)
      Crumb(part, last = i == parts.lastIndex) { onGo(parts.subList(0, i + 1).joinToString("/")) }
    }
  }
}

@Composable
private fun Crumb(text: String, last: Boolean, onClick: () -> Unit) {
  Text(
    text,
    color = if (last) PenLabColors.icon else PenLabColors.iconDisabled,
    style = MaterialTheme.typography.headlineSmall,
    maxLines = 1,
    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !last, onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
  )
}

/** A folder: a tab-topped shape, its name and how many notebooks are inside. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTile(
  name: String,
  detail: String,
  color: Int?,
  onOpen: () -> Unit,
  onRename: () -> Unit,
  onMove: () -> Unit,
  onColor: () -> Unit,
  onDelete: () -> Unit,
) {
  // A coloured folder: the tab in its colour, the body tinted with it, the edge in it.
  val accent = color?.let { Color(it) }
  val body = accent?.let { androidx.compose.ui.graphics.lerp(PenLabColors.toolbar, it, 0.22f) } ?: PenLabColors.toolbar
  val edge = accent?.copy(alpha = 0.55f) ?: PenLabColors.hairline
  var menu by remember { mutableStateOf(false) }
  Column(
    Modifier.clip(RoundedCornerShape(12.dp))
      .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
      .semantics { contentDescription = "Folder $name, $detail. Long press for options." }
      .padding(4.dp)
  ) {
    Box {
      Canvas(Modifier.fillMaxWidth().aspectRatio(210f / 297f)) {
        val tabHeight = size.height * 0.08f
        val bodySize = androidx.compose.ui.geometry.Size(size.width, size.height - tabHeight)
        drawRoundRect(
          accent ?: PenLabColors.toolbar,
          topLeft = Offset(0f, 0f),
          size = androidx.compose.ui.geometry.Size(size.width * 0.45f, tabHeight * 2f),
          cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
        )
        drawRoundRect(
          body,
          topLeft = Offset(0f, tabHeight),
          size = bodySize,
          cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
        )
        drawRoundRect(
          edge,
          topLeft = Offset(0f, tabHeight),
          size = bodySize,
          cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
          style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
        )
      }
      DropdownMenu(
        expanded = menu,
        onDismissRequest = { menu = false },
        containerColor = PenLabComponentTokens.panelBg,
      ) {
        DropdownMenuItem(text = { Text("Rename", color = MaterialTheme.colorScheme.onSurface) }, onClick = { menu = false; onRename() })
        DropdownMenuItem(text = { Text("Colour", color = MaterialTheme.colorScheme.onSurface) }, onClick = { menu = false; onColor() })
        DropdownMenuItem(text = { Text("Move to folder", color = MaterialTheme.colorScheme.onSurface) }, onClick = { menu = false; onMove() })
        DropdownMenuItem(text = { Text("Delete (if empty)", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
      }
    }
    Spacer(Modifier.height(10.dp))
    Text(name, color = PenLabColors.icon, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(2.dp))
    Text(detail, color = PenLabColors.iconDisabled, style = MaterialTheme.typography.labelMedium, maxLines = 1)
  }
}

/** Pick the folder a notebook moves to. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveDialog(notebook: NotebookInfo, folders: List<String>, onDismiss: () -> Unit, onMove: (String) -> Unit) {
  var target by remember { mutableStateOf(notebook.folder) }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = PenLabColors.toolbar,
    title = { Text("Move “${notebook.title}”", color = PenLabColors.icon) },
    text = {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChoiceChip("No folder", target.isEmpty()) { target = "" }
        for (f in folders) ChoiceChip(f.replace("/", " › "), target == f) { target = f }
      }
    },
    confirmButton = { TextButton(onClick = { onMove(target) }) { Text("Move", color = PenLabColors.icon) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = PenLabColors.iconDisabled) } },
  )
}

/** Where to move a folder: the top level or any other folder (not inside itself). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderMoveDialog(name: String, parent: String, places: List<String>, onDismiss: () -> Unit, onMove: (String) -> Unit) {
  var target by remember { mutableStateOf(parent) }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = PenLabColors.toolbar,
    title = { Text("Move folder “$name”", color = PenLabColors.icon) },
    text = {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChoiceChip("Top level", target.isEmpty()) { target = "" }
        for (f in places) ChoiceChip(f.replace("/", " › "), target == f) { target = f }
      }
    },
    confirmButton = { TextButton(onClick = { onMove(target) }, enabled = target != parent) { Text("Move", color = PenLabColors.icon) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = PenLabColors.iconDisabled) } },
  )
}

/** Folder colours: muted enough for the charcoal library, distinct enough to tell courses apart. */
val FolderPalette = listOf(
  0xFF5B8DEF.toInt(), // blue
  0xFF3FB6A8.toInt(), // teal
  0xFF6CC070.toInt(), // green
  0xFFE8C547.toInt(), // yellow
  0xFFF09A4A.toInt(), // orange
  0xFFE5646E.toInt(), // red
  0xFFE07BB5.toInt(), // pink
  0xFF9B7BEA.toInt(), // purple
  0xFF9AA0A6.toInt(), // grey
)

/** A folder's colour: its own, or the nearest parent's, or none. */
fun folderColor(path: String, colors: Map<String, Int>): Int? {
  var p = path
  while (true) {
    colors[p]?.let { return it }
    if (!p.contains('/')) return null
    p = p.substringBeforeLast('/')
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderColorDialog(name: String, current: Int?, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = PenLabColors.toolbar,
    title = { Text("Colour for “$name”", color = PenLabColors.icon) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          for (c in FolderPalette) {
            val selected = c == current
            Box(
              Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(c))
                .border(if (selected) 3.dp else 0.dp, PenLabColors.icon, androidx.compose.foundation.shape.CircleShape)
                .clickable { onPick(c) }
                .semantics { contentDescription = "Colour ${FolderPalette.indexOf(c) + 1}" + if (selected) ", chosen" else "" }
            )
          }
        }
        Text("Folders inside take this colour unless they have their own.", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.bodySmall)
      }
    },
    confirmButton = { TextButton(onClick = { onPick(null) }) { Text("No colour", color = PenLabColors.icon) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = PenLabColors.iconDisabled) } },
  )
}

/** A notebook: a small sheet of its paper (A4 shaped, or wider for an endless canvas) and its title. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotebookCard(
  notebook: NotebookInfo,
  darkPage: Boolean,
  coverFile: java.io.File,
  onOpen: () -> Unit,
  onRename: () -> Unit,
  onMove: () -> Unit,
  onDelete: () -> Unit,
  onExport: () -> Unit,
  onShare: () -> Unit,
  onTurn: () -> Unit,
  /** Shown instead of the date's lead, e.g. the folder on a Continue card. */
  place: String? = null,
  /** The folder's colour, as a small mark beside the place. */
  accent: Int? = null,
) {
  var menu by remember { mutableStateOf(false) }
  val date = remember(notebook.modified) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(notebook.modified)) }
  val pages = "${notebook.pageCount} ${if (notebook.pageCount == 1) "page" else "pages"}"
  val detail =
    when {
      notebook.kind == NotebookKind.INFINITE -> "Canvas · $date"
      notebook.pdfPages.isNotEmpty() -> "PDF · $pages · $date"
      else -> "$pages · $date"
    }
  Column(
    Modifier.clip(RoundedCornerShape(12.dp))
      .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
      .semantics { contentDescription = "${notebook.title}, $detail. Long press for options." }
      .padding(4.dp)
  ) {
    Box {
      val aspect = if (notebook.kind == NotebookKind.A4) notebook.pageWidthMm / notebook.pageHeightMm else 4f / 3f
      // The real first page once there is a cover; the paper style until then.
      val cover by androidx.compose.runtime.produceState(dev.penlab.app.ui.Covers.cached(notebook.id), notebook.id, notebook.modified) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dev.penlab.app.ui.Covers.load(notebook.id, coverFile) }
      }
      val image = cover
      if (image != null) {
        val shape = RoundedCornerShape(8.dp)
        androidx.compose.foundation.Image(
          image,
          contentDescription = null,
          contentScale = androidx.compose.ui.layout.ContentScale.Crop,
          alignment = Alignment.TopCenter,
          // On the dark page, plain notebooks look as they do inside (ink inverted); PDFs stay white.
          colorFilter = if (darkPage && notebook.pdfPages.isEmpty()) InvertCover else null,
          modifier = Modifier.fillMaxWidth().aspectRatio(aspect).clip(shape).border(1.dp, PenLabColors.hairline, shape),
        )
      } else {
        PaperThumbnail(notebook.pageStyle, darkPage, aspect = aspect, pdf = notebook.pdfPages.isNotEmpty())
      }
      DropdownMenu(
        expanded = menu,
        onDismissRequest = { menu = false },
        containerColor = PenLabComponentTokens.panelBg,
      ) {
        DropdownMenuItem(text = { Text("Rename", color = MaterialTheme.colorScheme.onSurface) }, onClick = { menu = false; onRename() })
        DropdownMenuItem(text = { Text("Move to folder", color = MaterialTheme.colorScheme.onSurface) }, onClick = { menu = false; onMove() })
        DropdownMenuItem(text = { Text("Save as PDF", color = MaterialTheme.colorScheme.onSurface) }, onClick = { menu = false; onExport() })
        DropdownMenuItem(text = { Text("Share PDF", color = MaterialTheme.colorScheme.onSurface) }, onClick = { menu = false; onShare() })
        // Plain A4 notebooks can be turned; a PDF's pages keep their own shape.
        if (notebook.kind == NotebookKind.A4 && notebook.pdfPages.isEmpty()) {
          val toLandscape = notebook.pageWidthMm < notebook.pageHeightMm
          DropdownMenuItem(text = { Text(if (toLandscape) "Turn to landscape" else "Turn to portrait", color = MaterialTheme.colorScheme.onSurface) }, onClick = { menu = false; onTurn() })
        }
        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
      }
    }
    Spacer(Modifier.height(10.dp))
    Text(notebook.title, color = PenLabColors.icon, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(2.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (place != null) {
        Box(Modifier.size(8.dp).background(accent?.let { Color(it) } ?: PenLabColors.iconDisabled, androidx.compose.foundation.shape.CircleShape))
        Spacer(Modifier.width(6.dp))
      }
      Text(if (place != null) "$place · $detail" else detail, color = PenLabColors.iconDisabled, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

/** Covers on the dark page: lightness inverted, hue kept (the same matrix the page uses for ink pictures). */
private val InvertCover =
  androidx.compose.ui.graphics.ColorFilter.colorMatrix(
    androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(0.574f, -1.430f, -0.144f, 0f, 255f, -0.426f, -0.430f, -0.144f, 0f, 255f, -0.426f, -1.430f, 0.856f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))
  )

/** A blank sheet of the notebook's paper with its ruling: the cover until page thumbnails exist. */
@Composable
private fun PaperThumbnail(style: PageStyle, darkPage: Boolean, aspect: Float, pdf: Boolean, modifier: Modifier = Modifier) {
  val paper = if (pdf) Color.White else Color(InkColors.pageOnScreen(darkPage))
  val ruling = Color(InkColors.onScreen(PageBackground.RULING_PAPER_COLOR, darkPage))
  val shape = RoundedCornerShape(6.dp)
  Canvas(
    modifier
      .fillMaxWidth()
      .aspectRatio(aspect)
      .clip(shape)
      .background(paper)
      .border(1.dp, Color(InkColors.pageEdgeOnScreen(darkPage)), shape)
  ) {
    val step = 10.dp.toPx()
    if (pdf) {
      // A stand-in for a slide until real covers exist: a title bar and a few text lines.
      val ink = Color(0xFFB4B8BF)
      drawRect(ink, topLeft = Offset(size.width * 0.12f, size.height * 0.16f), size = androidx.compose.ui.geometry.Size(size.width * 0.5f, size.height * 0.09f))
      for (i in 0 until 4) {
        val y = size.height * (0.4f + i * 0.12f)
        drawLine(ink, Offset(size.width * 0.12f, y), Offset(size.width * (0.8f - i * 0.1f), y), strokeWidth = 2f)
      }
      return@Canvas
    }
    when (style) {
      PageStyle.BLANK -> Unit
      PageStyle.LINED -> {
        var y = step * 1.5f
        while (y < size.height) {
          drawLine(ruling, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
          y += step
        }
      }
      PageStyle.DOTTED -> {
        var y = step
        while (y < size.height) {
          var x = step
          while (x < size.width) {
            drawCircle(ruling, radius = 1.2f, center = Offset(x, y))
            x += step
          }
          y += step
        }
      }
      PageStyle.CORNELL -> {
        var y = step * 1.5f
        while (y < size.height) {
          drawLine(ruling, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
          y += step
        }
        drawLine(ruling, Offset(size.width * 0.3f, step * 1.5f), Offset(size.width * 0.3f, size.height * 0.8f), strokeWidth = 2f)
        drawLine(ruling, Offset(0f, size.height * 0.8f), Offset(size.width, size.height * 0.8f), strokeWidth = 2f)
      }
      PageStyle.CP -> {
        var x = 0f
        while (x < size.width) { drawLine(ruling.copy(alpha = 0.45f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.6f); x += step * 0.6f }
        var y = 0f
        while (y < size.height) { drawLine(ruling.copy(alpha = 0.45f), Offset(0f, y), Offset(size.width, y), strokeWidth = 0.6f); y += step * 0.6f }
        drawLine(ruling, Offset(0f, size.height * 0.1f), Offset(size.width, size.height * 0.1f), strokeWidth = 2f)
        drawLine(ruling, Offset(size.width * 0.12f, size.height * 0.1f), Offset(size.width * 0.12f, size.height), strokeWidth = 2f)
      }
      PageStyle.ISOMETRIC -> {
        val gap = step * 0.8f
        var c = -size.width
        while (c < size.height + size.width) {
          drawLine(ruling, Offset(0f, c), Offset(size.width, c + size.width * 0.577f), strokeWidth = 1f)
          drawLine(ruling, Offset(0f, c), Offset(size.width, c - size.width * 0.577f), strokeWidth = 1f)
          c += gap
        }
      }
      PageStyle.GRID, PageStyle.ENGINEERING, PageStyle.GRAPH -> {
        val cell = if (style == PageStyle.GRAPH) step / 2 else step * 0.6f
        var x = 0f
        while (x < size.width) { drawLine(ruling, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f); x += cell }
        var y = 0f
        while (y < size.height) { drawLine(ruling, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f); y += cell }
      }
    }
  }
}

/** Title, A4 or endless canvas, and paper. It goes into the folder that is open. */
@Composable
private fun NewNotebookDialog(
  folder: String,
  onDismiss: () -> Unit,
  onCreate: (String, NotebookKind, PageStyle, Boolean) -> Unit,
) {
  // Each folder remembers the pages and paper last chosen in it, or inherits its parent's
  // (e.g. Circuits always on grid paper). Dotted A4 portrait otherwise.
  val prefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("folder_paper", android.content.Context.MODE_PRIVATE)
  val remembered = remember(folder) {
    generateSequence(folder) { f -> if (f.isEmpty()) null else f.substringBeforeLast("/", "") }
      .firstNotNullOfOrNull { prefs.getString(it.ifEmpty { "/" }, null) }
      ?.split(":")
  }
  var title by remember { mutableStateOf("") }
  var kind by remember { mutableStateOf(NotebookKind.entries.firstOrNull { it.name == remembered?.getOrNull(1) } ?: NotebookKind.A4) }
  var style by remember { mutableStateOf(PageStyle.entries.firstOrNull { it.name == remembered?.getOrNull(0) } ?: PageStyle.DOTTED) }
  var landscape by remember { mutableStateOf(remembered?.getOrNull(2) == "landscape") }
  // The keyboard must not cover Create (QC 21 Nov): the dialog sits above the keyboard and its
  // middle scrolls when space is short.
  AlertDialog(
    properties = androidx.compose.ui.window.DialogProperties(decorFitsSystemWindows = false),
    modifier = Modifier.imePadding(),
    onDismissRequest = onDismiss,
    containerColor = PenLabColors.toolbar,
    // The heading is the first row of the scrolling content, not the dialog's title slot: with
    // the keyboard open the slot kept its full height and left the Paper chips a sliver of view.
    title = null,
    text = {
      Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (folder.isEmpty()) "New notebook" else "New notebook in ${folder.substringAfterLast("/")}", color = PenLabColors.icon, style = MaterialTheme.typography.headlineSmall)
        PenLabTextField(title, { title = it }, "Title")
        Text("Pages", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          ChoiceChip("A4 portrait", kind == NotebookKind.A4 && !landscape) { kind = NotebookKind.A4; landscape = false }
          ChoiceChip("A4 landscape", kind == NotebookKind.A4 && landscape) { kind = NotebookKind.A4; landscape = true }
          ChoiceChip("Endless canvas", kind == NotebookKind.INFINITE) { kind = NotebookKind.INFINITE }
        }
        Text(if (folder.isEmpty()) "Paper" else "Paper (remembered for ${folder.substringAfterLast("/")})", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.labelMedium)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          for (option in PageStyle.entries) ChoiceChip(option.label, style == option) { style = option }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = {
        prefs.edit().putString(folder.ifEmpty { "/" }, "${style.name}:${kind.name}:${if (landscape) "landscape" else "portrait"}").apply()
        onCreate(title, kind, style, landscape)
      }) { Text("Create") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = PenLabColors.iconDisabled) } },
  )
}

/** One text field with optional suggestions underneath. Used for rename and move. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextPromptDialog(
  title: String,
  label: String,
  initial: String,
  suggestions: List<String>,
  confirm: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var value by remember { mutableStateOf(initial) }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = PenLabColors.toolbar,
    title = { Text(title, color = PenLabColors.icon) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PenLabTextField(value, { value = it }, label)
        if (suggestions.isNotEmpty()) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in suggestions) ChoiceChip(s, selected = s == value) { value = s }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(confirm, color = PenLabColors.icon) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = PenLabColors.iconDisabled) } },
  )
}

@Composable
private fun PenLabTextField(value: String, onChange: (String) -> Unit, label: String) {
  OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label) },
    singleLine = true,
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

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
  val shape = RoundedCornerShape(10.dp)
  Text(
    label,
    color = if (selected) PenLabColors.onAccent else PenLabColors.icon,
    style = MaterialTheme.typography.labelLarge,
    modifier =
      Modifier.clip(shape)
        .background(if (selected) PenLabColors.accent else PenLabColors.chip, shape)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .heightIn(min = 40.dp)
        .padding(horizontal = 14.dp, vertical = 10.dp),
  )
}


/** One course folder as a shelf: its name (opens the folder), a menu, and its newest notebooks in a row. */
@Composable
private fun CourseShelf(
  name: String,
  parent: String,
  count: Int,
  color: Int?,
  onOpenFolder: () -> Unit,
  onRename: () -> Unit,
  onMove: () -> Unit,
  onColor: () -> Unit,
  onDelete: () -> Unit,
  books: @Composable () -> Unit,
) {
  var menu by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Row(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpenFolder).padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(12.dp).background(color?.let { Color(it) } ?: PenLabColors.iconDisabled, androidx.compose.foundation.shape.CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(name, color = PenLabColors.icon, style = MaterialTheme.typography.titleMedium)
        if (parent.isNotEmpty()) {
          Spacer(Modifier.width(8.dp))
          Text(parent, color = PenLabColors.iconDisabled, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(10.dp))
        Text(if (count == 1) "1 notebook" else "$count notebooks", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.labelMedium)
      }
      Spacer(Modifier.weight(1f))
      Box {
        androidx.compose.material3.IconButton(onClick = { menu = true }) {
          androidx.compose.material3.Icon(MoreIcon, contentDescription = "$name options", tint = PenLabColors.iconDisabled)
        }
        DropdownMenu(
          expanded = menu,
          onDismissRequest = { menu = false },
          containerColor = PenLabComponentTokens.panelBg,
        ) {
          DropdownMenuItem(text = { Text("Open folder", color = MaterialTheme.colorScheme.onSurface) }, onClick = { menu = false; onOpenFolder() })
          DropdownMenuItem(text = { Text("Rename", color = MaterialTheme.colorScheme.onSurface) }, onClick = { menu = false; onRename() })
          DropdownMenuItem(text = { Text("Colour", color = MaterialTheme.colorScheme.onSurface) }, onClick = { menu = false; onColor() })
          DropdownMenuItem(text = { Text("Move to folder", color = MaterialTheme.colorScheme.onSurface) }, onClick = { menu = false; onMove() })
          DropdownMenuItem(text = { Text("Delete (if empty)", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
        }
      }
    }
    if (count == 0) Text("Empty", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.bodySmall)
    else Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(18.dp)) { books() }
  }
}

/**
 * A calm, dismissible note above the notebooks: without "All files access" the notes sit in the
 * app's private storage and vanish if the app is uninstalled. Shown until allowed or dismissed
 * (dismissal lasts for the session; there is no dialog).
 */
@Composable
private fun StorageBanner(onAllow: () -> Unit, onDismiss: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp)
      .clip(RoundedCornerShape(12.dp)).background(PenLabColors.chip).padding(start = 16.dp, end = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      "Keep your notes safe: store them in Documents/PenLab so they survive reinstalling the app.",
      Modifier.weight(1f).padding(vertical = 10.dp),
      color = PenLabColors.icon,
      style = MaterialTheme.typography.bodyMedium,
    )
    TextButton(onClick = onDismiss) {
      Text("Not now", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
    TextButton(onClick = onAllow) {
      Text("Allow", color = PenLabColors.accent, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
  }
}
