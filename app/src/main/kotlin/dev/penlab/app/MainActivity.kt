package dev.penlab.app

import android.content.ActivityNotFoundException
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.toArgb
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.penlab.app.ink.InkCanvasView
import dev.penlab.app.ink.ViewMemory
import dev.penlab.app.ink.InkStroke
import dev.penlab.app.ink.InkTool
import dev.penlab.app.ink.PageLayout
import dev.penlab.app.ink.notebookStrokes
import dev.penlab.app.ink.PageStore
import dev.penlab.app.ink.PageSnapshot
import dev.penlab.app.ink.PageStyle
import dev.penlab.app.ink.PdfBackground
import dev.penlab.app.notes.LibraryLocation
import dev.penlab.app.notes.LibraryMigration
import dev.penlab.app.notes.NotebookInfo
import dev.penlab.app.notes.NotebookKind
import dev.penlab.app.notes.NotebookStore
import dev.penlab.app.notes.cleanFolder
import dev.penlab.app.notes.IncomingPdf
import dev.penlab.app.ui.BarHeight
import dev.penlab.app.ui.FileChromePill
import dev.penlab.app.ui.FileOptionsPopover
import dev.penlab.app.ui.FileQuickMenuPopover
import dev.penlab.app.ui.MORE_CELL
import dev.penlab.app.ui.MenuAction
import dev.penlab.app.ui.MoreToolsPanel
import dev.penlab.app.ui.MoveNotebookDialog
import dev.penlab.app.ui.PageConfigChromePill
import dev.penlab.app.ui.PageConfigPopover
import dev.penlab.app.ui.PageConfigTab
import dev.penlab.app.ui.ToolbarConfig
import dev.penlab.app.ui.ToolbarItem
import dev.penlab.app.ui.stepZoomIn
import dev.penlab.app.ui.stepZoomOut
import dev.penlab.app.ui.TextBoxDialog
import dev.penlab.app.ink.PageItem
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.result.PickVisualMediaRequest
import dev.penlab.app.ui.IdeaIcon
import dev.penlab.app.ui.RequestDialog
import dev.penlab.app.ui.SearchPanel
import dev.penlab.app.ui.SearchStepper
import dev.penlab.app.ui.FIRST_LAUNCH_DONE_KEY
import dev.penlab.app.ui.FirstLaunchAction
import dev.penlab.app.ui.LOOK_PREFS
import dev.penlab.app.ui.SettingsSheet
import dev.penlab.app.ui.firstLaunchAction
import dev.penlab.app.ui.resolveCanvasDarkPage
import dev.penlab.app.ui.systemBarsOverDarkSurface
import dev.penlab.app.search.HandwritingIndex
import dev.penlab.app.ui.FULLSCREEN_EXIT_ALPHA
import dev.penlab.app.ui.PRESENT_HINT_MESSAGE
import dev.penlab.app.ui.PRESENT_HINT_MS
import dev.penlab.app.ui.PenLabIcons
import dev.penlab.app.ui.ScreenChrome
import dev.penlab.app.ui.SelectionPluginAction
import dev.penlab.app.ui.components.PenLabChromePill
import dev.penlab.app.ui.components.PenLabDialog
import dev.penlab.app.ui.components.PenLabIconButton
import dev.penlab.app.ui.components.PenLabSnackbar
import dev.penlab.app.ui.ignorePalm
import dev.penlab.app.ui.placeSelectionBar
import dev.penlab.app.ui.presentPillAlpha
import dev.penlab.app.ui.shouldShowFloatingPastePill
import kotlinx.coroutines.delay
import dev.penlab.app.ui.FullScreenIcon
import androidx.compose.ui.draw.alpha
import dev.penlab.app.ui.FullScreenExitIcon
import dev.penlab.app.ui.CORNER_BUTTON_ALPHA
import dev.penlab.app.ui.CloseIcon
import dev.penlab.app.ui.PenToolsIcon
import dev.penlab.app.ui.PresentIcon
import dev.penlab.app.search.LibrarySearch
import androidx.core.view.doOnLayout
import android.graphics.RectF
import dev.penlab.app.notes.RequestList
import java.io.File
import dev.penlab.app.plugins.AppExportSource
import dev.penlab.app.plugins.BuiltInPlugins
import dev.penlab.app.plugins.PluginRegistry
import androidx.lifecycle.lifecycleScope
import dev.penlab.app.ui.theme.PenLabSpacing
import dev.penlab.plugin.ImportSource
import dev.penlab.plugin.PageAction
import dev.penlab.plugin.PageRef
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import dev.penlab.app.ui.LibraryScreen
import dev.penlab.app.ink.handwritingLikeStrokes
import dev.penlab.app.ui.PAPER_CELL
import dev.penlab.app.ui.INSTRUMENTS_CELL
import dev.penlab.app.ui.InstrumentsPanel
import dev.penlab.app.ui.PaperPanel
import dev.penlab.app.ui.PasteMenu
import dev.penlab.app.ui.SelectionActions
import dev.penlab.app.ui.ToolOptionsPanel
import dev.penlab.app.ui.ToolSettings
import dev.penlab.app.ui.ToolDock
import dev.penlab.app.ui.Motion
import dev.penlab.app.ui.ColorWheel
import dev.penlab.app.ui.DockedToolbar
import dev.penlab.app.ui.DockItem
import dev.penlab.app.ui.LAYERS_CELL
import dev.penlab.app.ui.LayersPanel
import dev.penlab.app.ui.RenameDialog
import dev.penlab.app.ui.DockSide
import dev.penlab.app.ui.DockInsets
import dev.penlab.app.ui.RailWidth
import dev.penlab.app.ui.ToolbarMessage
import dev.penlab.app.ui.ViewPanel
import dev.penlab.app.ui.ViewSettings
import dev.penlab.app.ui.theme.Accent
import dev.penlab.app.ui.theme.LocalPenLabColors
import dev.penlab.app.ui.theme.PenLabTheme
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  /** Where the library lives (Documents/PenLab with "All files access", else private); the one source of truth. */
  private val location by lazy { LibraryLocation.forApp(this) }
  private val notebooks by lazy { NotebookStore(location) }

  /** The library is still in private storage (no "All files access"): the library shows a banner, unless dismissed. */
  private var needsStorageAccess by mutableStateOf(false)
  private var storageBannerDismissed by mutableStateOf(false)

  /** A move into Documents/PenLab is running, or was tried (and failed) this session: not repeated until the next launch. */
  private var migrating = false

  /**
   * The built-in plugins (PDF import and export, ...). Every call into a plugin goes through this
   * registry, which switches a crashing plugin off and tells the user in the snackbar.
   */
  private val plugins by lazy { PluginRegistry(BuiltInPlugins.all()) { message -> main.post { exportMessage = message } } }

  /** A plugin action is running (one at a time), and the text a finished one wants shown. */
  private var pluginBusy = false
  private var pluginText by mutableStateOf<String?>(null)
  private val io = Executors.newSingleThreadExecutor()
  private val main = Handler(Looper.getMainLooper())
  private val saveNow = Runnable { save() }

  /** The library's contents, newest first. Loaded off the main thread. */
  private var library by mutableStateOf<List<NotebookInfo>>(emptyList())

  /** The first read of the library has finished: before it, an empty [library] only means "not loaded yet". */
  private var libraryLoaded by mutableStateOf(false)

  /** The Welcome sheet was seen (or skipped, or not needed); persisted as `first_launch_done` in the `look` prefs. */
  private var firstLaunchDone by mutableStateOf(false)

  /** Settings > "Welcome guide": show the sheet whatever the first-run rules say. */
  private var welcomeRequested by mutableStateOf(false)

  private fun markFirstLaunchDone() {
    firstLaunchDone = true
    getSharedPreferences(LOOK_PREFS, MODE_PRIVATE).edit().putBoolean(FIRST_LAUNCH_DONE_KEY, true).apply()
  }

  /**
   * Notebook tabs: the last three notebooks opened, newest first, kept across
   * restarts. The others show as tabs on the top bar; a tap saves this one and opens that one.
   */
  private val tabPrefs by lazy { getSharedPreferences("tabs", MODE_PRIVATE) }
  private var tabs by mutableStateOf<List<String>>(emptyList())
  private var folders by mutableStateOf<List<String>>(emptyList())
  private var folderColors by mutableStateOf<Map<String, Int>>(emptyMap())

  /** The folder the library shows; kept here so closing a notebook returns to its folder. */
  private var libraryFolder by mutableStateOf("")

  /** The notebook on screen, or null for the library. */
  private var open by mutableStateOf<OpenNotebook?>(null)

  /** Projector view: shows only the page on a cabled projector or monitor (see projector/). */
  private val projector by lazy { dev.penlab.app.projector.ProjectorController(this) }

  private lateinit var viewSettings: ViewSettings
  private lateinit var toolbarConfig: ToolbarConfig
  private lateinit var toolDock: ToolDock

  // Library-wide handwriting search: the words, the matches, and how far catching up has got.
  private val librarySearch by lazy { LibrarySearch(notebooks, resources.displayMetrics.xdpi / 25.4f) }
  private var libraryQuery by mutableStateOf("")
  private var libraryResults by mutableStateOf<List<LibrarySearch.Result>>(emptyList())
  private var libraryReading by mutableStateOf<Pair<Int, Int>?>(null)

  private fun searchLibrary() {
    librarySearch.search(libraryQuery, library) { libraryResults = it }
  }

  /** The notebook whose finished PDF is waiting for the user to pick where it goes. */
  private var exporting: NotebookInfo? = null

  /**
   * The finished PDF (in the cache) waiting for a destination. The PDF is built first, while
   * PenLab is on screen, and only then is the folder picker opened: a picker in front used to
   * let Android freeze the app mid-export, which left a 0-byte file behind.
   */
  private var exportFile: File? = null

  /** Set to a file name to open the "save as" picker; the composition launches it and clears this. */
  private var saveRequest by mutableStateOf<String?>(null)

  /** The export being written keeps the dark page (light ink on charcoal) instead of white paper. */
  private var exportDark = false

  /** The running list of bugs and ideas (requests.md, readable over USB). */
  private val requests by lazy { RequestList(File(getExternalFilesDir(null), "requests.md")) }

  /** Where the bug/idea form was opened from, or null when it is closed. */
  private var reportingFrom by mutableStateOf<String?>(null)

  /** Shown when an export finishes or fails. */
  private var exportMessage by mutableStateOf<String?>(null)

  /**
   * An open notebook: its own canvas (a fresh one per notebook, so nothing carries over) and
   * where it saves. [pages] is null for the stress test, which must never touch real notes.
   */
  private class OpenNotebook(
    initialInfo: NotebookInfo,
    val canvas: InkCanvasView,
    val pages: PageStore?,
    val pdf: PdfBackground? = null,
    val search: HandwritingIndex? = null,
  ) {
    /** Observable metadata so live rename/move/page updates reflect immediately in top chrome. */
    var info by mutableStateOf(initialInfo)

    /** False until the saved strokes are on the canvas; saving before that would overwrite them. */
    var loaded = false

    /** Something was written, erased or changed since the last save. */
    var dirty = false

    /** Where the page was panned and zoomed to at the last save (or at opening). */
    var savedView: FloatArray? = null

    /** Edited since it was opened: its library cover is redrawn on closing. */
    var editedThisVisit = false
  }

  private val canvas: InkCanvasView?
    get() = open?.canvas

  // Remembers the first time a pen is used (it turns "Draw with finger" off by default).
  override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
    if (::viewSettings.isInitialized) viewSettings.noteMotionEvent(ev)
    return super.dispatchTouchEvent(ev)
  }

  override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
    if (::viewSettings.isInitialized) viewSettings.noteMotionEvent(ev) // pen hover arrives here
    return super.dispatchGenericMotionEvent(ev)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Accent.load(this)
    // Draw behind the status and navigation bars with icon contrast matching the active UI theme.
    val initialBars =
      if (Accent.isDark) SystemBarStyle.dark(Color.TRANSPARENT)
      else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    enableEdgeToEdge(statusBarStyle = initialBars, navigationBarStyle = initialBars)
    requestHighestRefreshRate()
    viewSettings = ViewSettings(this)
    toolbarConfig = ToolbarConfig(this)
    toolDock = ToolDock(this)
    Motion.load(this)
    firstLaunchDone = getSharedPreferences(LOOK_PREFS, MODE_PRIVATE).getBoolean(FIRST_LAUNCH_DONE_KEY, false)
    tabs = tabPrefs.getString("ids", null)?.split(",")?.filter { it.isNotEmpty() }.orEmpty()
    val toolSettings = ToolSettings(this)
    setContent {
      PenLabTheme {
        val isDarkTheme = LocalPenLabColors.current.isDark
        // The icon colour follows what is behind the bars: the app theme in the library, the paper
        // (white for a PDF) while a notebook is open.
        val notebookPaperDark = open?.let { resolveCanvasDarkPage(Accent.pageThemeMode, isPdfNotebook = it.pdf != null) }
        val barsOverDark = systemBarsOverDarkSurface(isDarkTheme, notebookOpen = notebookPaperDark != null, paperIsDark = notebookPaperDark == true)
        DisposableEffect(barsOverDark) {
          val bars =
            if (barsOverDark) SystemBarStyle.dark(Color.TRANSPARENT)
            else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
          enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
          onDispose {}
        }
        val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
          Log.i("Export", "picker returned $uri")
          val info = exporting
          val file = exportFile
          exporting = null
          exportFile = null
          if (uri != null && info != null && file != null) saveExport(info, file, uri) else file?.delete()
        }
        LaunchedEffect(saveRequest) {
          saveRequest?.let {
            Log.i("Export", "opening save picker for $it")
            saveRequest = null
            savePdf.launch(it)
          }
        }
        var askExportLook by remember { mutableStateOf<Pair<NotebookInfo, Boolean>?>(null) }
        val startExport: (NotebookInfo, Boolean) -> Unit = { info, share ->
          save()
          val target = open?.info?.takeIf { it.id == info.id } ?: info
          // PDF notebooks are light on screen and export light; a dark note asks which look to keep.
          if (target.pdfPages.isNotEmpty() || !Accent.isDarkPage) buildExport(target, false, share)
          else askExportLook = target to share
        }
        askExportLook?.let { (info, share) ->
          dev.penlab.app.ui.ExportLookDialog(
            onPick = { dark ->
              askExportLook = null
              buildExport(info, dark, share)
            },
            onDismiss = { askExportLook = null },
          )
        }
        pluginText?.let { text ->
          PenLabDialog(
            title = "Plugin result",
            onDismiss = { pluginText = null },
            primaryLabel = "Done",
            onPrimaryClick = { pluginText = null },
            secondaryLabel = null,
            onSecondaryClick = null,
          ) {
            androidx.compose.material3.Text(
              text = text,
              color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
              style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
          }
        }
        reportingFrom?.let { where ->
          val existing = remember(where) { requests.read() }
          RequestDialog(
            where = where,
            existing = existing,
            onDismiss = { reportingFrom = null },
            onSave = { kind, text ->
              io.execute { requests.add(kind, where, text) }
              reportingFrom = null
            },
          )
        }
        var settingsOpen by remember { mutableStateOf(false) }
        if (settingsOpen) {
          SettingsSheet(
            viewSettings = viewSettings,
            toolbarConfig = toolbarConfig,
            toolDock = toolDock,
            storageShared = !needsStorageAccess,
            onAllowStorage = ::requestStorageAccess,
            onDismiss = { settingsOpen = false },
            // The guide lives on the library screen, so it is only offered there.
            onWelcomeGuide = if (open == null) ({ settingsOpen = false; welcomeRequested = true }) else null,
            onReport = {
              settingsOpen = false
              reportingFrom =
                open?.info?.let { "settings › ${it.title}" }
                  ?: if (libraryFolder.isEmpty()) "library" else "library › $libraryFolder"
            },
          )
        }
        val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
          val notebook = open
          if (uri != null && notebook != null) importImage(uri, notebook)
        }
        val pickPdfFromCanvas = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
          if (uri != null) importPdfAndOpen(uri)
        }
        val current = open
        if (current == null) {
          val messages = remember { SnackbarHostState() }
          val scope = rememberCoroutineScope()
          // Exports started from the library menu report here (the notebook screen has its own).
          LaunchedEffect(exportMessage) { showExportMessage(exportMessage, messages) { exportMessage = null } }
          val pickPdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            importPdfs(uris, libraryFolder) { failed ->
              if (failed > 0) scope.launch { messages.showSnackbar(if (failed == 1) "One file could not be read as a PDF." else "$failed files could not be read as PDFs.") }
            }
          }
          // While the library is on screen, read any notebook whose handwriting isn't read yet.
          LaunchedEffect(library) {
            librarySearch.catchUp(library) { done, total ->
              libraryReading = if (total == 0) null else done to total
              if (libraryQuery.isNotBlank()) searchLibrary() // newly read notebooks may match
            }
          }
          val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
            if (tree != null) {
              scope.launch { messages.showSnackbar("Importing the folder… notebooks appear as they are read.") }
              importFolder(tree, libraryFolder) { imported, skipped, failed ->
                val parts = listOfNotNull(
                  "$imported imported",
                  if (skipped > 0) "$skipped already here" else null,
                  if (failed > 0) "$failed couldn't be read" else null,
                )
                scope.launch { messages.showSnackbar("Folder import: " + parts.joinToString(", ")) }
              }
            }
          }
          // Welcome sheet: a genuine first run (library read, no notebooks) or asked for in Settings.
          val welcomeAction = firstLaunchAction(
            done = firstLaunchDone,
            libraryLoaded = libraryLoaded,
            notebookCount = library.size,
            notebookOpen = open != null,
            requested = welcomeRequested,
          )
          // A returning user (notebooks, flag never set) is recorded once so deleting everything later shows no welcome.
          LaunchedEffect(welcomeAction) { if (welcomeAction == FirstLaunchAction.MARK_DONE) markFirstLaunchDone() }
          LibraryScreen(
            storageBanner = needsStorageAccess && !storageBannerDismissed,
            onAllowStorage = ::requestStorageAccess,
            onDismissStorageBanner = { storageBannerDismissed = true },
            onImportFolder = { pickFolder.launch(null) },
            searchQuery = libraryQuery,
            onSearchQuery = {
              libraryQuery = it
              searchLibrary()
            },
            searchResults = libraryResults,
            searchReading = libraryReading,
            onOpenResult = { result -> openNotebook(result.notebook, jumpTo = result.hit.line.rect) },
            notebooks = library,
            folders = folders,
            current = libraryFolder,
            onFolderChange = { libraryFolder = it },
            darkPage = Accent.isDarkPage,
            messages = messages,
            onOpen = ::openNotebook,
            onCreate = { title, folder, kind, style, landscape -> createNotebook(title, folder, kind, style, landscape) },
            onCreateFolder = { path -> inBackground { notebooks.createFolder(path) } },
            onImportPdf = { pickPdfs.launch(arrayOf(*plugins.importMimeTypes().toTypedArray(), "*/*")) },
            onRenameFolder = { from, to -> inBackground { notebooks.renameFolder(from, to) } },
            folderColors = folderColors,
            onFolderColor = { path, color -> inBackground { notebooks.setFolderColor(path, color) } },
            onDeleteFolder = { path ->
              // The folder's own colour (not an inherited one), read now: it is dropped with the folder.
              val ownColor = folderColors[path]
              val name = path.substringAfterLast("/")
              inBackground {
                if (notebooks.deleteFolder(path)) {
                  main.post {
                    scope.launch {
                      messages.currentSnackbarData?.dismiss()
                      val result = messages.showSnackbar("Deleted folder “$name”", actionLabel = "Undo", duration = SnackbarDuration.Long)
                      if (result == SnackbarResult.ActionPerformed) inBackground { notebooks.restoreFolder(path, ownColor) }
                    }
                  }
                } else {
                  main.post {
                    scope.launch { messages.showSnackbar("“$name” isn't empty. Move or delete what's inside first.") }
                  }
                }
              }
            },
            onRename = { info, title -> updateInfo(info.copy(title = title.trim().ifEmpty { info.title })) },
            onMove = { info, folder -> updateInfo(info.copy(folder = cleanFolder(folder))) },
            onExport = { startExport(it, false) },
            onShare = { startExport(it, true) },
            onTurn = ::turnNotebook,
            coverFile = { info -> File(notebooks.imagesDir(info).parentFile, COVER_FILE) },
            onOpenSettings = { settingsOpen = true },
            showWelcome = welcomeAction == FirstLaunchAction.SHOW,
            onWelcomeClosed = {
              welcomeRequested = false
              markFirstLaunchDone()
            },
            onDelete = { info ->
              deleteNotebook(info)
              scope.launch {
                messages.currentSnackbarData?.dismiss()
                val result = messages.showSnackbar("Deleted “${info.title}”", actionLabel = "Undo", duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) restoreNotebook(info)
              }
            },
          )
        } else {
          BackHandler { closeNotebook() }
          val effectiveDarkPage = resolveCanvasDarkPage(Accent.pageThemeMode, isPdfNotebook = current.pdf != null)
          LaunchedEffect(current.canvas, effectiveDarkPage) {
            current.canvas.setDarkPage(effectiveDarkPage)
          }
          key(current.info.id) {
            val recentNotebooks =
              remember(current.info, tabs, library) {
                buildList {
                  add(current.info)
                  val seen = hashSetOf(current.info.id)
                  for (id in tabs) {
                    if (seen.add(id)) library.firstOrNull { it.id == id }?.let(::add)
                  }
                  for (info in library) {
                    if (size >= MAX_RECENT_FILES) break
                    if (seen.add(info.id)) add(info)
                  }
                }
              }
            NoteScreen(
              canvas = current.canvas,
              darkPage = effectiveDarkPage,
              isPdfNotebook = current.pdf != null,
              notebookInfo = current.info,
              viewSettings = viewSettings,
              toolSettings = toolSettings,
              toolbarConfig = toolbarConfig,
              dock = toolDock,
              recentNotebooks = recentNotebooks,
              folders = folders,
              folderColorFor = { folder -> dev.penlab.app.ui.folderColor(folder, folderColors) },
              onBack = ::closeNotebook,
              onSwitchNotebook = ::switchTo,
              onRenameNotebook = ::renameOpenNotebook,
              onMoveNotebook = ::moveOpenNotebook,
              onTurnNotebook = if (current.info.kind == NotebookKind.A4 && current.info.pdfPages.isEmpty()) ::turnOpenNotebook else null,
              onExport = { startExport(current.info, false) },
              onShare = { startExport(current.info, true) },
              onOpenSettings = { settingsOpen = true },
              onReport = { where -> reportingFrom = where },
              onInsertImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
              onImportPdf = { pickPdfFromCanvas.launch(arrayOf(*plugins.importMimeTypes().toTypedArray(), "application/pdf")) },
              pluginActions = plugins::pageActions,
              onPluginAction = ::runPluginAction,
              exportMessage = exportMessage,
              onExportMessageShown = { exportMessage = null },
              onPageEdited = ::scheduleSave,
              search = current.search,
              projector = projector,
            )
          }
        }
      }
    }
    if (!openStressTestIfRequested()) {
      io.execute {
        notebooks.migrateSinglePage()
        refreshLibrary()
      }
      handleIncomingPdf(intent, restored = savedInstanceState != null) // queued after the migration (one io thread)
    }
  }

  override fun onStart() {
    super.onStart()
    projector.start()
  }

  override fun onResume() {
    super.onResume()
    checkStorage()
  }

  /**
   * Re-reads "All files access" (it is granted on a Settings page, so this runs on every resume),
   * and when notes can live in Documents/PenLab, moves any private ones there. Not while a
   * notebook is open: its files are already in use at the old place, so this waits for closing.
   */
  private fun checkStorage() {
    if (open != null) return
    if (location.refresh()) io.execute { refreshLibrary() }
    needsStorageAccess = !location.isShared
    if (!location.isShared || migrating) return
    migrating = true
    io.execute {
      if (!LibraryMigration.pending(location.privateLibrary)) {
        main.post { migrating = false }
        return@execute
      }
      val result = LibraryMigration.run(location.privateLibrary, location.root)
      refreshLibrary()
      main.post {
        // After a failure `migrating` stays true: no nagging, the next launch tries again.
        migrating = result.error != null
        val notes = if (result.moved == 1) "1 notebook" else "${result.moved} notebooks"
        exportMessage = when {
          result.error == null && result.renamed > 0 ->
            "Moved $notes to Documents/PenLab. ${result.renamed} kept as a separate copy because a different notebook with the same id was already there."
          result.error == null -> "Moved $notes to Documents/PenLab."
          result.moved > 0 -> "Moved $notes to Documents/PenLab, then stopped (${result.error}). The rest are untouched in the app's private storage; PenLab will try again next time."
          else -> "Couldn't move your notebooks to Documents/PenLab (${result.error}). They are untouched in the app's private storage; PenLab will try again next time."
        }
      }
    }
  }

  /** Opens the system page where "All files access" is switched on for PenLab. */
  private fun requestStorageAccess() {
    try {
      startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
    } catch (e: ActivityNotFoundException) {
      try {
        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
      } catch (e2: ActivityNotFoundException) {
        exportMessage = "Couldn't open the settings page. Turn on \"All files access\" for PenLab in the system settings."
      }
    }
  }

  private fun refreshLibrary() {
    val list = notebooks.list()
    val folderList = notebooks.folders()
    val colors = notebooks.folderColors()
    main.post {
      library = list
      libraryLoaded = true
      folders = folderList
      folderColors = colors
      backfillCovers()
    }
  }

  /** Runs [work] on the file thread, then refreshes the library. */
  private fun inBackground(work: () -> Unit) {
    io.execute {
      work()
      refreshLibrary()
    }
  }

  private fun createNotebook(title: String, folder: String, kind: NotebookKind, style: PageStyle, landscape: Boolean = false) {
    io.execute {
      val made = notebooks.create(title, folder, kind, style)
      // Landscape: the same A4 sheet turned sideways.
      val info = if (landscape) made.copy(pageWidthMm = 297f, pageHeightMm = 210f) else made
      if (info != made) notebooks.write(info)
      refreshLibrary()
      main.post { openNotebook(info) }
    }
  }

  /**
   * Makes a notebook from each PDF, named after the file, in [folder]. [onDone] gets how many
   * could not be read.
   */
  private fun importPdfs(uris: List<Uri>, folder: String, onDone: (failed: Int) -> Unit) {
    if (uris.isEmpty()) return
    io.execute {
      var failed = 0
      for (uri in uris) {
        if (importFile(uri, displayName(uri) ?: "Imported", folder) == null) failed++
      }
      refreshLibrary()
      main.post { onDone(failed) }
    }
  }

  /** Marks an intent whose PDF has been taken, so the same intent is never imported twice. */
  private val handledExtra = "dev.penlab.app.PDF_HANDLED"

  /**
   * "Open with PenLab" / "Share to PenLab" for a PDF (Files, WhatsApp, Drive): imports it into a
   * new notebook, through the same importer plugins as the library's Import, and opens it.
   * The intent is consumed first (marked, and stripped of its file), so a second delivery of the
   * same intent (the tablet turning, the app restored) does nothing. [restored] is true when the
   * activity is being recreated: its intent was already dealt with.
   */
  private fun handleIncomingPdf(incoming: Intent?, restored: Boolean = false) {
    incoming ?: return
    val stream = if (incoming.action == Intent.ACTION_SEND) androidx.core.content.IntentCompat.getParcelableExtra(incoming, Intent.EXTRA_STREAM, Uri::class.java) else null
    val uri = IncomingPdf.pick(
      incoming.action,
      incoming.type,
      incoming.data,
      stream,
      handled = restored || incoming.getBooleanExtra(handledExtra, false),
      fromHistory = incoming.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0,
    ) ?: return
    if (uri.scheme != "content" && uri.scheme != "file") return
    // Consume it before doing anything slow.
    incoming.putExtra(handledExtra, true)
    incoming.data = null
    incoming.removeExtra(Intent.EXTRA_STREAM)
    // Keep read access only when the sender offered it as persistable (some file pickers do);
    // otherwise the import below reads the file right now, while the temporary grant is valid.
    if (incoming.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
      try {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
      } catch (e: SecurityException) {
        Log.i("Import", "No persistable access to $uri; reading it now instead")
      } catch (e: IllegalArgumentException) {
        Log.i("Import", "Not a persistable uri: $uri")
      }
    }
    val folder = libraryFolder
    io.execute {
      val info = importFile(uri, IncomingPdf.fileName(displayName(uri)), folder)
      if (info == null) {
        main.post { exportMessage = "That file could not be read as a PDF." }
      } else {
        refreshLibrary()
        main.post { openNotebook(info) }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingPdf(intent)
  }

  /**
   * Imports one file into [folder] (on the io thread) with the importer for its type (a PDF today).
   * If no importer claims the file's type, every importer gets a try. Null if it couldn't be read.
   */
  private fun importFile(uri: Uri, fileName: String, folder: String): NotebookInfo? {
    val name = notebookTitle(fileName)
    return try {
      val mime = mimeOf(fileName, contentResolver.getType(uri))
      val candidates = plugins.importers(mime).ifEmpty { plugins.importers() }
      for (entry in candidates) {
        val work = File(cacheDir, "imports/${java.util.UUID.randomUUID()}").apply { mkdirs() }
        try {
          val source = ImportSource(fileName, mime, { contentResolver.openInputStream(uri) ?: throw java.io.FileNotFoundException(fileName) }, work, name)
          val imported = (plugins.guardedBlocking(entry.plugin) { entry.capability.import(source) } as? PluginRegistry.Call.Ok)?.value ?: continue
          return notebooks.createFromImport(imported, folder)
        } finally {
          work.deleteRecursively()
        }
      }
      null
    } catch (e: Exception) {
      Log.e("Import", "Could not import $fileName", e)
      null
    }
  }

  /** A file's MIME type: from its name's extension (as before, a ".pdf" is a PDF), else what the provider reported. */
  private fun mimeOf(fileName: String, reported: String?): String =
    android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase())
      ?: reported
      ?: "application/octet-stream"

  /**
   * Imports every PDF in a folder the user picked, and in its subfolders, recreating the folders
   * inside [into].
   * A file whose notebook (same title, same folder) is already in the library is skipped, so
   * importing the same folder again only adds what is new. [onDone] gets (imported, skipped, failed).
   */
  private fun importFolder(tree: Uri, into: String, onDone: (Int, Int, Int) -> Unit) {
    io.execute {
      var imported = 0
      var skipped = 0
      var failed = 0
      val existing = notebooks.list().map { it.folder to it.title }.toHashSet()
      fun walk(documentId: String, folder: String) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        val entries = ArrayList<Triple<String, String, String>>()
        contentResolver.query(children, columns, null, null, null)?.use { c ->
          while (c.moveToNext()) entries += Triple(c.getString(0), c.getString(1), c.getString(2))
        }
        for ((id, name, mime) in entries.sortedBy { it.second.lowercase() }) {
          when {
            mime == DocumentsContract.Document.MIME_TYPE_DIR -> {
              if (name.startsWith("_") || name.startsWith(".")) continue // staging folders, hidden folders
              val sub = if (folder.isEmpty()) name else "$folder/$name"
              notebooks.createFolder(sub)
              walk(id, sub)
            }
            plugins.importers(mimeOf(name, mime)).isNotEmpty() -> {
              if ((folder to notebookTitle(name)) in existing) {
                skipped++
                continue
              }
              val made = importFile(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, folder)
              if (made == null) failed++ else imported++
              if (imported % 5 == 0) refreshLibrary() // show progress as notebooks appear
            }
          }
        }
      }
      val rootId = DocumentsContract.getTreeDocumentId(tree)
      val rootName = contentResolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, rootId), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
      // Picking a folder imports what is inside it (with its subfolders) into [into].
      walk(rootId, into)
      Log.i("Import", "Folder ${rootName ?: tree}: $imported imported, $skipped skipped, $failed failed")
      refreshLibrary()
      main.post { onDone(imported, skipped, failed) }
    }
  }

  /** "Lecture 1.pdf" -> "Lecture 1". */
  private fun notebookTitle(fileName: String): String =
    fileName.substringBeforeLast('.').trim().ifBlank { "Imported" }

  private fun displayName(uri: Uri): String? =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) cursor.getString(0) else null
    }

  private fun updateInfo(info: NotebookInfo) {
    io.execute {
      notebooks.write(info)
      refreshLibrary()
    }
  }

  private fun deleteNotebook(info: NotebookInfo) {
    io.execute {
      notebooks.delete(info)
      refreshLibrary()
    }
  }

  private fun restoreNotebook(info: NotebookInfo) {
    io.execute {
      notebooks.restore(info)
      refreshLibrary()
    }
  }

  /**
   * Writes [info] as a PDF to [uri] (a file the user chose, e.g. in Google Drive). Uses the open
   * canvas's strokes if it is this notebook, otherwise loads them from disk first.
   */
  /**
   * Step 1 of an export or share: builds the PDF into a cache file while PenLab is on screen.
   * Step 2 (afterwards) is the "save as" picker, or the share sheet when [share] is true.
   */
  private fun buildExport(info: NotebookInfo, dark: Boolean, share: Boolean) {
    Log.i("Export", "build: ${info.title} share=$share dark=$dark")
    exportMessage = "Preparing “${info.title}” as PDF… stay in PenLab until it's ready"
    val dir = File(cacheDir, "exports").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, info.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "Notebook" } + ".pdf")
    renderPdf(info, file, dark) { ok ->
      Log.i("Export", "built ok=$ok size=${file.length()} share=$share")
      if (!ok) {
        file.delete()
        exportMessage = "Export failed"
      } else if (share) {
        exportMessage = null // takes "Preparing…" down
        sharePdf(info, file)
      } else {
        exportMessage = null // takes "Preparing…" down
        exporting = info
        exportFile = file
        saveRequest = "${info.title}.pdf"
      }
    }
  }

  /** Draws the notebook into [file] on the worker thread; [done] runs on the main thread. */
  private fun renderPdf(info: NotebookInfo, file: File, dark: Boolean, done: (Boolean) -> Unit) {
    val openNow = open?.takeIf { it.info.id == info.id && it.loaded }
    val write: (List<InkStroke>, PageStyle, List<PageItem>) -> Unit = { strokes, style, items ->
      io.execute {
        val ok =
          try {
            val exporter = plugins.exporter("application/pdf")
            val unitsPerMm = if (info.kind == NotebookKind.A4) PageLayout.UNITS_PER_MM else resources.displayMetrics.xdpi / 25.4f
            val source = AppExportSource(info, strokes, items, style, notebooks.pdfFile(info), notebooks.imagesDir(info), dark, unitsPerMm)
            val written =
              exporter != null &&
                file.outputStream().use { out -> plugins.guardedBlocking(exporter.plugin) { exporter.capability.export(source, out) } } is PluginRegistry.Call.Ok
            written && file.length() > 0
          } catch (e: Throwable) {
            Log.e("Export", "PDF export failed", e)
            false
          }
        main.post { done(ok) }
      }
    }
    if (openNow != null) {
      val snapshot = openNow.canvas.snapshot()
      // Hidden layers stay out of the PDF, like out of sight.
      val hidden = openNow.canvas.layers.hidden
      write(snapshot.strokes.filter { it.layer !in hidden }, snapshot.pageStyle, snapshot.items)
    } else {
      notebooks.pageStore(info).load { page -> write(page?.strokes.orEmpty().filter { it.layer !in info.hiddenLayers }, page?.pageStyle ?: info.pageStyle, page?.items.orEmpty()) }
    }
  }

  /**
   * Runs a plugin's page action on the open notebook: gives it the selection (read here, on the
   * main thread), shows "Running..." while it works off the main thread, then places its items or
   * shows its text or message. One action at a time. A crash is reported by the registry.
   */
  private fun runPluginAction(entry: PluginRegistry.Entry<PageAction>) {
    val notebook = open ?: return
    if (pluginBusy) return
    pluginBusy = true
    val canvas = notebook.canvas
    val selection = canvas.pluginSelection()
    val info = notebook.info
    val mmWide = if (info.kind == NotebookKind.A4) info.pageWidthMm else canvas.width / (resources.displayMetrics.xdpi / 25.4f)
    val mmHigh = if (info.kind == NotebookKind.A4) info.pageHeightMm else canvas.height / (resources.displayMetrics.xdpi / 25.4f)
    val page = PageRef(info.id, canvas.currentPage, mmWide, mmHigh)
    val label = entry.capability.label
    exportMessage = "Running “$label”…"
    lifecycleScope.launch {
      val outcome = plugins.runAction(entry, selection, page)
      pluginBusy = false
      when (outcome) {
        is PluginRegistry.ActionOutcome.Place -> {
          exportMessage = null // takes "Running…" down
          if (open === notebook) canvas.placePluginItems(outcome.items, selection.isEmpty)
        }
        is PluginRegistry.ActionOutcome.ShowText -> { exportMessage = null; pluginText = outcome.text }
        is PluginRegistry.ActionOutcome.ShowMessage -> exportMessage = outcome.message
        PluginRegistry.ActionOutcome.Nothing -> exportMessage = null
        // A crash already told the user (the plugin is switched off); a device failure did not.
        is PluginRegistry.ActionOutcome.Failed -> if (!outcome.pluginDisabled) exportMessage = "“$label” could not finish."
      }
    }
  }

  /** Copies the finished PDF to the place the user picked. A failed copy removes the empty file. */
  private fun saveExport(info: NotebookInfo, file: File, uri: Uri) {
    io.execute {
      val ok =
        try {
          contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
        } catch (e: Throwable) {
          Log.e("Export", "PDF copy failed", e)
          false
        }
      if (!ok) runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
      file.delete()
      main.post { exportMessage = if (ok) "Exported “${info.title}” as PDF" else "Export failed" }
    }
  }

  /** Opens Android's share sheet for the finished PDF (WhatsApp, Drive, mail, ...). */
  private fun sharePdf(info: NotebookInfo, file: File) {
    val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", file)
    val send =
      Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, info.title)
        clipData = android.content.ClipData.newRawUri(info.title, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    startActivity(Intent.createChooser(send, "Share “${info.title}” as PDF"))
  }

  /**
   * Copies a picked photo into the notebook's images folder (shrunk so its long side is at most
   * 2400 px, saved as JPEG), then puts it on the page.
   */
  private fun importImage(uri: Uri, notebook: OpenNotebook) {
    io.execute {
      val name =
        try {
          val dir = notebooks.imagesDir(notebook.info).apply { mkdirs() }
          val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
          var sample = 1
          while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
          val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
          } ?: return@execute
          val file = File(dir, "${java.util.UUID.randomUUID()}.jpg")
          file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
          file.name
        } catch (e: Exception) {
          Log.e("Photo", "Could not add the photo", e)
          null
        }
      if (name != null) main.post { if (open === notebook) notebook.canvas.addImage(name) }
    }
  }

  /**
   * Turns a plain A4 notebook between portrait and landscape: the pages swap width and height,
   * and each page's ink keeps its place from the page's top-left corner (ink past the new edge
   * stays, just off the paper). Undo history is cleared: its positions no longer fit.
   */
  private fun turnNotebook(info: NotebookInfo) {
    if (info.kind != NotebookKind.A4 || info.pdfPages.isNotEmpty()) return
    val store = notebooks.pageStore(info)
    store.load { page ->
      io.execute {
        val old = PageLayout.paged(info.pageWidthMm, info.pageHeightMm, info.pageCount, info.bookmarks, emptyList())
        val turned = info.copy(pageWidthMm = info.pageHeightMm, pageHeightMm = info.pageWidthMm, modified = System.currentTimeMillis())
        val new = PageLayout.paged(turned.pageWidthMm, turned.pageHeightMm, info.pageCount, info.bookmarks, emptyList())
        if (page != null) {
          fun shift(y: Float): Float {
            val p = old.pageAt(y)
            return y - old.top(p) + new.top(p)
          }
          val strokes = page.strokes.map { s ->
            val dy = shift(s.bounds.centerY()) - s.bounds.centerY()
            if (dy == 0f) s else s.translated(0f, dy)
          }
          val items = page.items.map { item ->
            val dy = shift(item.cy) - item.cy
            item.mapped(android.graphics.Matrix().apply { setTranslate(0f, dy) })
          }
          store.clearHistoryNow()
          store.saveNow(PageSnapshot(strokes, page.pageStyle, FloatArray(9).also { android.graphics.Matrix().getValues(it) }, items))
        }
        notebooks.write(turned)
        refreshLibrary()
      }
    }
  }

  /** Opens [info] in a new canvas and loads its strokes in the background. */
  private fun openNotebook(info: NotebookInfo, jumpTo: RectF? = null) {
    val openedAt = android.os.SystemClock.uptimeMillis()
    tabs = (listOf(info.id) + tabs.filter { it != info.id }).take(MAX_TABS)
    tabPrefs.edit().putString("ids", tabs.joinToString(",")).apply()
    librarySearch.stopCatchingUp() // the open notebook reads its own handwriting
    val layout =
      if (info.kind == NotebookKind.A4) PageLayout.paged(info.pageWidthMm, info.pageHeightMm, info.pageCount, info.bookmarks, info.pdfPages)
      else null
    val pdfFile = notebooks.pdfFile(info)
    var canvasRef: InkCanvasView? = null
    val pdf = if (layout != null && info.pdfPages.isNotEmpty() && pdfFile.exists()) PdfBackground(pdfFile) { canvasRef?.pdfPageReady() } else null
    // A notebook made from a PDF is shown light (white pages, ink in its true colours, added
    // blank pages white too), like other note apps; plain notebooks follow the chosen page mode.
    val canvas = InkCanvasView(this, darkPage = resolveCanvasDarkPage(Accent.pageThemeMode, isPdfNotebook = pdf != null), pageLayout = layout, pdf = pdf, imagesDir = notebooks.imagesDir(info))
    canvasRef = canvas
    canvas.zoomLocked = viewSettings.zoomLocked
    canvas.rotationLocked = viewSettings.rotationLocked
    canvas.shapeSnappingEnabled = viewSettings.shapeSnapping
    canvas.fingerDrawEnabled = { viewSettings.drawWithFinger }
    canvas.pageStyle = info.pageStyle
    canvas.rulingMm = info.rulingMm
    canvas.loadLayers(InkCanvasView.Layers(info.layerNames, info.hiddenLayers.toSet(), info.currentLayer))
    val notebook = OpenNotebook(info, canvas, notebooks.pageStore(info), pdf, HandwritingIndex(notebooks.searchFile(info)))
    canvas.onPagesChanged = {
      layout?.let {
        notebook.info = notebook.info.copy(pageCount = it.count, bookmarks = it.bookmarks.sorted(), pdfPages = it.backgrounds)
      }
    }
    open = notebook
    notebook.pages?.onSaveFailed = {
      // Never fail silently: the notes on screen are not on disk.
      exportMessage = "Couldn't save “${notebook.info.title}”. Is the tablet's storage full? Your last save is safe."
    }
    notebook.pages?.load { page ->
      if (open !== notebook) return@load // closed again before it finished loading
      val t0 = android.os.SystemClock.uptimeMillis()
      if (page != null) canvas.load(page) else canvas.fitPageWidth()
      Log.i("PenLabPerf", "open: canvas.load ${android.os.SystemClock.uptimeMillis() - t0} ms (main thread), ${android.os.SystemClock.uptimeMillis() - openedAt} ms since tap")
      canvas.post { Log.i("PenLabPerf", "open: first frame after load ${android.os.SystemClock.uptimeMillis() - openedAt} ms since tap") }
      notebook.loaded = true
      // The view once it has settled (a fit happens on the next frame), so reading isn't "a change".
      canvas.post { canvas.post { if (open === notebook && !notebook.dirty) notebook.savedView = canvas.snapshot().view } }
      // From a library search result: go to the line once the page has its size.
      if (jumpTo != null) canvas.doOnLayout { canvas.post { canvas.showSearchHit(jumpTo) } }
      reindex(notebook) // reads anything written since the last index (or everything, the first time)
    }
  }

  /** From one notebook tab to another: saves this one (and its cover), opens [info]. */
  /** Renames the open notebook immediately in chrome and persists to [NotebookStore]. */
  private fun renameOpenNotebook(newTitle: String) {
    val current = open ?: return
    val clean = newTitle.trim().ifEmpty { current.info.title }
    if (clean == current.info.title) return
    val updated = current.info.copy(title = clean, modified = System.currentTimeMillis())
    current.info = updated
    updateInfo(updated)
  }

  /** Moves the open notebook to [targetFolder] immediately in chrome and persists to [NotebookStore]. */
  private fun moveOpenNotebook(targetFolder: String) {
    val current = open ?: return
    val clean = cleanFolder(targetFolder)
    if (clean == current.info.folder) return
    val updated = current.info.copy(folder = clean, modified = System.currentTimeMillis())
    current.info = updated
    libraryFolder = clean
    io.execute {
      if (clean.isNotEmpty()) notebooks.createFolder(clean)
      notebooks.write(updated)
      refreshLibrary()
    }
  }

  /**
   * Turns the currently open plain A4 notebook between portrait and landscape without racing with
   * async saves: captures its live snapshot, shifts ink/items on [io], saves, and reopens cleanly.
   */
  private fun turnOpenNotebook() {
    val current = open ?: return
    val info = current.info
    if (info.kind != NotebookKind.A4 || info.pdfPages.isNotEmpty()) return
    main.removeCallbacks(saveNow)
    val snapshot = current.canvas.snapshot()
    val store = current.pages ?: notebooks.pageStore(info)
    current.pdf?.close()
    current.search?.close()
    open = null
    io.execute {
      val old = PageLayout.paged(info.pageWidthMm, info.pageHeightMm, info.pageCount, info.bookmarks, emptyList())
      val turned = info.copy(pageWidthMm = info.pageHeightMm, pageHeightMm = info.pageWidthMm, modified = System.currentTimeMillis())
      val new = PageLayout.paged(turned.pageWidthMm, turned.pageHeightMm, info.pageCount, info.bookmarks, emptyList())
      fun shift(y: Float): Float {
        val p = old.pageAt(y)
        return y - old.top(p) + new.top(p)
      }
      val strokes = snapshot.strokes.map { s ->
        val dy = shift(s.bounds.centerY()) - s.bounds.centerY()
        if (dy == 0f) s else s.translated(0f, dy)
      }
      val items = snapshot.items.map { item ->
        val dy = shift(item.cy) - item.cy
        item.mapped(android.graphics.Matrix().apply { setTranslate(0f, dy) })
      }
      store.clearHistoryNow()
      store.saveNow(PageSnapshot(strokes, snapshot.pageStyle, FloatArray(9).also { android.graphics.Matrix().getValues(it) }, items))
      notebooks.write(turned)
      refreshLibrary()
      main.post { openNotebook(turned) }
    }
  }

  /**
   * Imports a PDF chosen from inside the canvas's More menu into the current notebook's folder
   * (via the PDF importer plugin) and opens it cleanly.
   */
  private fun importPdfAndOpen(uri: Uri) {
    val folder = open?.info?.folder ?: libraryFolder
    save()
    open?.let(::saveCover)
    exportMessage = "Importing PDF…"
    io.execute {
      val info = importFile(uri, IncomingPdf.fileName(displayName(uri)), folder)
      if (info == null) {
        main.post { exportMessage = "That file could not be read as a PDF." }
      } else {
        refreshLibrary()
        main.post {
          exportMessage = null
          switchTo(info)
        }
      }
    }
  }

  private fun switchTo(info: NotebookInfo) {
    val current = open ?: return openNotebook(info)
    if (current.info.id == info.id) return
    save()
    saveCover(current)
    current.pdf?.close()
    current.search?.close()
    open = null
    openNotebook(info)
  }

  /** Saves and goes back to the library. */
  private fun closeNotebook() {
    save()
    open?.let(::saveCover)
    open?.pdf?.close()
    open?.search?.close()
    open = null
    io.execute { refreshLibrary() }
    checkStorage() // the permission may have been granted while the notebook was open
  }

  /**
   * The library card's cover: the first page as a small picture (`cover.jpg`), redrawn when the
   * notebook was edited or has none yet. Drawn here (a few ms at this size), written on [io].
   */
  private fun saveCover(notebook: OpenNotebook) {
    val file = File(notebooks.imagesDir(notebook.info).parentFile, COVER_FILE)
    if (!notebook.editedThisVisit && file.exists()) return
    val picture = try { notebook.canvas.pagePicture(page = 0, widthPx = 360) } catch (e: Exception) { return }
    io.execute {
      try {
        val tmp = File(file.parentFile, "$COVER_FILE.tmp")
        tmp.outputStream().use { picture.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        tmp.renameTo(file)
        dev.penlab.app.ui.Covers.forget(notebook.info.id)
        main.post { refreshLibraryQuietly() }
      } catch (e: Exception) {
        Log.w("Cover", "Could not save the cover", e)
      }
    }
  }

  private fun refreshLibraryQuietly() = io.execute { refreshLibrary() }

  /** Covers for notebooks that have none (drawn once, at low priority, newest notebooks first). */
  private var coversRunning = false
  private val coversTried = HashSet<String>()
  private fun backfillCovers() {
    if (coversRunning) return
    val todo = library.filter { "${it.id}@${it.modified}" !in coversTried && !File(notebooks.imagesDir(it).parentFile, COVER_FILE).exists() }
      .sortedByDescending { it.modified }
    if (todo.isEmpty()) return
    coversRunning = true
    todo.forEach { coversTried += "${it.id}@${it.modified}" }
    Thread({
      var made = 0
      for (info in todo) {
        if (open?.info?.id == info.id) continue
        try {
          val bitmap = dev.penlab.app.notes.CoverMaker.make(info, notebooks.pageStore(info), notebooks.pdfFile(info), notebooks.imagesDir(info)) ?: continue
          val file = File(notebooks.imagesDir(info).parentFile, COVER_FILE)
          val tmp = File(file.parentFile, "$COVER_FILE.tmp")
          tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
          tmp.renameTo(file)
          dev.penlab.app.ui.Covers.forget(info.id)
          made++
          if (made % 6 == 0) main.post { refreshLibraryQuietly() }
        } catch (e: Throwable) {
          Log.w("Cover", "No cover for ${info.id}", e)
        }
      }
      main.post {
        coversRunning = false
        if (made > 0) refreshLibraryQuietly()
      }
    }, "penlab-covers").apply { priority = Thread.MIN_PRIORITY }.start()
  }

  /** Autosave: one second after the last change, so a burst of writing is one save. */
  private fun scheduleSave() {
    // Opening a notebook fires the same "changed" signal while the strokes go in; that is not an
    // edit, and saving it rewrote the whole file (10 MB, ~0.9 s for the major task) on every open.
    val notebook = open ?: return
    if (!notebook.loaded) return
    notebook.dirty = true
    main.removeCallbacks(saveNow)
    main.postDelayed(saveNow, AUTOSAVE_DELAY_MS)
  }

  private fun save() {
    main.removeCallbacks(saveNow)
    val notebook = open ?: return
    val pages = notebook.pages ?: return
    if (!notebook.loaded) return
    val snapshot = notebook.canvas.snapshot()
    // Nothing changed and the view is where it was: nothing to write (leaving the app or closing a
    // notebook you only read used to rewrite the whole file).
    val viewMoved = notebook.savedView?.contentEquals(snapshot.view) != true
    if (!notebook.dirty && !viewMoved) return
    val edited = notebook.dirty
    if (edited) notebook.editedThisVisit = true
    notebook.dirty = false
    notebook.savedView = snapshot.view
    pages.save(snapshot)
    if (!edited) return // only the view moved: the page file keeps it, nothing else changed
    reindex(notebook)
    val layers = notebook.canvas.layers
    notebook.info = notebook.info.copy(
      modified = System.currentTimeMillis(),
      pageStyle = notebook.canvas.pageStyle,
      rulingMm = notebook.canvas.rulingMm,
      layerNames = layers.names,
      hiddenLayers = layers.hidden.sorted(),
      currentLayer = layers.current,
    )
    val info = notebook.info
    io.execute { notebooks.write(info) }
  }

  /** Handwriting search reads new or changed lines in the background (after every save). */
  private fun reindex(notebook: OpenNotebook) {
    val canvas = notebook.canvas
    notebook.search?.update(canvas.searchableStrokes(), canvas.pageLayout, canvas.unitsPerMm)
  }

  /** Leaving the app (home, another app, screen off) saves straight away. */
  override fun onStop() {
    save()
    projector.stop()
    super.onStop()
  }

  /**
   * Keyboard: zoom keys or Ctrl with + / - to zoom, Ctrl+Z to undo, Ctrl+Shift+Z or Ctrl+Y to
   * redo, Ctrl+C / X / V to copy, cut and paste, Delete to delete the selection.
   */
  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    val canvas = canvas ?: return super.onKeyDown(keyCode, event)
    val zoomIn = keyCode == KeyEvent.KEYCODE_ZOOM_IN ||
      (event.isCtrlPressed && (keyCode == KeyEvent.KEYCODE_EQUALS || keyCode == KeyEvent.KEYCODE_PLUS))
    val zoomOut = keyCode == KeyEvent.KEYCODE_ZOOM_OUT ||
      (event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_MINUS)
    if (event.isCtrlPressed) {
      when (keyCode) {
        KeyEvent.KEYCODE_C -> return true.also { canvas.copySelection() }
        KeyEvent.KEYCODE_X -> return true.also { canvas.cutSelection() }
        KeyEvent.KEYCODE_V -> return true.also { canvas.paste() }
        KeyEvent.KEYCODE_D -> return true.also { canvas.duplicateSelection() }
      }
    }
    if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
      if (canvas.hasSelection) return true.also { canvas.deleteSelection() }
    }
    val undo = event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_Z && !event.isShiftPressed
    val redo = event.isCtrlPressed &&
      (keyCode == KeyEvent.KEYCODE_Y || (keyCode == KeyEvent.KEYCODE_Z && event.isShiftPressed))
    when {
      undo -> canvas.undo()
      redo -> canvas.redo()
      zoomIn -> canvas.zoomBy(KEY_ZOOM_STEP)
      zoomOut -> canvas.zoomBy(1f / KEY_ZOOM_STEP)
      else -> return super.onKeyDown(keyCode, event)
    }
    return true
  }

  /**
   * Performance testing, debug builds only. Opens a throwaway canvas (never saved) filled with
   * fake handwriting: `adb shell am start -n dev.penlab.app/.MainActivity --ei stress_strokes 2000`
   */
  private fun openStressTestIfRequested(): Boolean {
    val stressPages = intent.getIntExtra("stress_pages", 0)
    if (stressPages > 0) {
      // A real, saved notebook full of fake handwriting (in the "Stress tests" folder), to test
      // opening, saving, scrolling and editing a huge file. Works in release builds too.
      val perPage = intent.getIntExtra("stress_per_page", 400)
      io.execute {
        // Only one stress notebook at a time: earlier ones go to the trash.
        notebooks.list().filter { it.folder == "Stress tests" }.forEach { notebooks.delete(it) }
        val made = notebooks.create("Stress $stressPages x $perPage", "Stress tests", NotebookKind.A4, PageStyle.LINED)
        val info = made.copy(pageCount = stressPages)
        notebooks.write(info)
        refreshLibrary()
        main.post {
          openNotebook(info)
          val notebook = open ?: return@post
          notebook.canvas.post {
            val layout = notebook.canvas.pageLayout ?: return@post
            val started = SystemClock.uptimeMillis()
            val strokes = notebookStrokes(stressPages, perPage, notebook.canvas.brush, layout)
            Log.i("PenLabPerf", "made ${strokes.size} strokes in ${SystemClock.uptimeMillis() - started} ms")
            notebook.canvas.addStrokes(strokes)
            notebook.loaded = true
            scheduleSave()
          }
        }
      }
      return true
    }
    val count = intent.getIntExtra("stress_strokes", 0)
    if (count <= 0) return false // (a throwaway canvas: never saved, so it is safe in release too)
    val info = NotebookInfo("stress", "Stress test", "", NotebookKind.INFINITE, PageStyle.BLANK, 1, emptyList(), 0, 0)
    val canvas = InkCanvasView(this, darkPage = Accent.isDarkPage, imagesDir = notebooks.imagesDir(info))
    // Its search index lives in the cache (thrown away), to time reading many strokes.
    val notebook = OpenNotebook(info, canvas, pages = null, search = HandwritingIndex(File(cacheDir, "stress-search.txt").apply { delete() }))
    open = notebook
    canvas.post {
      canvas.addStrokes(handwritingLikeStrokes(count, canvas.brush, canvas.width.toFloat(), canvas.height.toFloat()))
      reindex(notebook)
    }
    return true
  }

  /**
   * Asks for the panel's fastest mode (120 Hz on supporting tablets) at the current resolution, so the
   * system does not drop to 60 Hz to save power while writing.
   */
  private fun requestHighestRefreshRate() {
    val display = display ?: return
    val current = display.mode
    val fastest =
      display.supportedModes
        .filter {
          it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
        }
        .maxByOrNull { it.refreshRate } ?: return
    window.attributes = window.attributes.apply { preferredDisplayModeId = fastest.modeId }
  }
}

private const val AUTOSAVE_DELAY_MS = 400L
private const val COVER_FILE = "cover.jpg"
private const val MAX_TABS = 3
private const val MAX_RECENT_FILES = 6

/** How much one press of a zoom key zooms. */
private const val KEY_ZOOM_STEP = 1.25f

/** Height of the top floating island row plus its margins when the center bar drops to row 2. */
private val TOP_BUTTONS_HEIGHT = BarHeight + 20.dp

/** The canvas screen: edge-to-edge paper, 3-island floating top chrome, and customizable toolbar. */
@Composable
private fun NoteScreen(
  canvas: InkCanvasView,
  darkPage: Boolean,
  isPdfNotebook: Boolean,
  notebookInfo: NotebookInfo,
  viewSettings: ViewSettings,
  toolSettings: ToolSettings,
  toolbarConfig: ToolbarConfig,
  dock: ToolDock,
  recentNotebooks: List<NotebookInfo>,
  folders: List<String>,
  folderColorFor: (String) -> Int?,
  onBack: () -> Unit,
  onSwitchNotebook: (NotebookInfo) -> Unit,
  onRenameNotebook: (String) -> Unit,
  onMoveNotebook: (String) -> Unit,
  onTurnNotebook: (() -> Unit)?,
  onExport: () -> Unit,
  onShare: () -> Unit,
  onOpenSettings: () -> Unit,
  onReport: (where: String) -> Unit,
  onInsertImage: () -> Unit,
  onImportPdf: () -> Unit,
  /** Plugin page actions for the "more" menu (true) or the lasso menu (false), and running one. */
  pluginActions: (selectionEmpty: Boolean) -> List<PluginRegistry.Entry<PageAction>>,
  onPluginAction: (PluginRegistry.Entry<PageAction>) -> Unit,
  exportMessage: String?,
  onExportMessageShown: () -> Unit,
  onPageEdited: () -> Unit,
  search: HandwritingIndex?,
  projector: dev.penlab.app.projector.ProjectorController,
) {
  // The toolbar's slots, dynamically sized to [toolbarConfig.penCount] pens + utility tools.
  val tools = remember { mutableStateListOf<InkTool>().apply { addAll(toolSettings.loadTools(toolbarConfig.penCount)) } }
  var selected by remember { mutableIntStateOf(toolSettings.selectedIndex.coerceIn(0, tools.lastIndex)) }
  val context = LocalContext.current
  val mmPerDp = remember {
    val metrics = context.resources.displayMetrics
    metrics.density * 25.4f / metrics.xdpi // dp → pixels → millimetres on this screen
  }
  var pageStyle by remember { mutableStateOf(canvas.pageStyle) }
  var rulingMm by remember { mutableStateOf(canvas.rulingMm) }
  var layerState by remember { mutableStateOf(canvas.layers) }
  // Which toolbar panel is open: a tool's index, PAPER_CELL, LAYERS_CELL, INSTRUMENTS_CELL, MORE_CELL, or null.
  var openPanel by remember { mutableStateOf<Int?>(null) }
  var shownPanel by remember { mutableIntStateOf(0) }

  var canUndo by remember { mutableStateOf(canvas.canUndo) }
  var canRedo by remember { mutableStateOf(canvas.canRedo) }
  var canClear by remember { mutableStateOf(!canvas.isEmpty) }
  var selectionRect by remember { mutableStateOf<Rect?>(null) }
  var selectionMoreOpen by remember { mutableStateOf(false) }
  var hasClipboard by remember { mutableStateOf(canvas.hasClipboard) }
  var pasteMenuAt by remember { mutableStateOf<Offset?>(null) }
  // Page zoom and angle for the right island readout, updated only when whole-number values change.
  var viewZoom by remember { mutableIntStateOf(canvas.zoomPercent) }
  var viewAngle by remember { mutableIntStateOf(Math.round(canvas.angleDegrees)) }

  // Top-chrome popovers (mutually exclusive with each other and with toolbar panels).
  var fileQuickMenuOpen by remember { mutableStateOf(false) }
  var fileOptionsOpen by remember { mutableStateOf(false) }
  var viewPanelOpen by remember { mutableStateOf(false) }
  var pageConfigOpen by remember { mutableStateOf(false) }
  var pageConfigTab by remember { mutableStateOf(if (canvas.pageLayout != null) PageConfigTab.PAGES else PageConfigTab.PAPER) }
  var searchOpen by remember { mutableStateOf(false) }

  // Current-file dialogs triggered from FileQuickMenuPopover.
  var renamingNotebook by remember { mutableStateOf(false) }
  var movingNotebook by remember { mutableStateOf(false) }

  // Full screen: the 3-island top chrome and Android's status bar hide; the tools stay.
  var fullScreen by remember { mutableStateOf(false) }
  // Present mode (projecting in class): only the page and the ink show. Never remembered across
  // launches. The pencil corner button shows or hides the toolbar (a toggle, no timer).
  var present by remember { mutableStateOf(false) }
  var toolsPeeking by remember { mutableStateOf(false) }
  var presentHintVisible by remember { mutableStateOf(false) }
  val chrome = ScreenChrome(fullScreen = fullScreen, present = present, toolsPeeking = toolsPeeking)
  val view = androidx.compose.ui.platform.LocalView.current
  // Present mode and Projector view both keep the screen on: the class is looking at it.
  DisposableEffect(chrome.keepScreenOn || projector.active) {
    val window = (view.context as? android.app.Activity)?.window
    if (chrome.keepScreenOn || projector.active) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
  }
  DisposableEffect(chrome.systemBarsHidden) {
    val window = (view.context as? android.app.Activity)?.window
    val bars = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
    if (chrome.systemBarsHidden) {
      bars?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      bars?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    } else {
      bars?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
    onDispose { bars?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
  }

  // Handwriting search state.
  var searchQuery by remember { mutableStateOf("") }
  var searchHits by remember { mutableStateOf<List<HandwritingIndex.Hit>>(emptyList()) }
  var searchShown by remember { mutableStateOf<Int?>(null) }
  var searchStatus by remember { mutableStateOf(search?.status ?: HandwritingIndex.Status.Ready) }
  fun runSearch() {
    searchHits = search?.search(searchQuery).orEmpty()
  }
  var jumpBack by remember { mutableStateOf<Pair<FloatArray, Int>?>(null) }
  fun showHit(index: Int) {
    val hit = searchHits.getOrNull(index) ?: return
    if (jumpBack == null) jumpBack = canvas.currentView() to canvas.currentPage
    searchShown = index
    searchOpen = false
    canvas.showSearchHit(hit.line.rect)
  }
  DisposableEffect(search) {
    search?.onStatus = { status ->
      searchStatus = status
      runSearch()
    }
    onDispose { search?.onStatus = {} }
  }

  var currentPage by remember { mutableIntStateOf(canvas.currentPage) }
  var contentVersion by remember { mutableIntStateOf(0) }
  var rulerOn by remember { mutableStateOf(canvas.rulerVisible) }
  var protractorOn by remember { mutableStateOf(canvas.protractorVisible) }
  var editingText by remember { mutableStateOf<Pair<String?, String>?>(null) }

  // Exact hit regions for the floating top islands and their open popovers:
  // [0] Left pill, [1] Right pill, [2] Left popover, [3] Right popover.
  val topBarBounds = remember { arrayOfNulls<Rect>(4) }
  val pasteMenuBounds = remember { arrayOfNulls<Rect>(1) }
  fun topBarOverlayOpen() =
    fileQuickMenuOpen || fileOptionsOpen || viewPanelOpen || pageConfigOpen || searchOpen
  fun closeTopBarOverlays() {
    fileQuickMenuOpen = false
    fileOptionsOpen = false
    viewPanelOpen = false
    pageConfigOpen = false
    searchOpen = false
    topBarBounds[2] = null
    topBarBounds[3] = null
  }
  fun closeOverlays(topBarToo: Boolean) {
    openPanel = null
    dock.wheelOpen = false
    pasteMenuAt = null
    pasteMenuBounds[0] = null
    selectionMoreOpen = false
    if (topBarToo) closeTopBarOverlays()
  }
  fun openSettings() {
    closeOverlays(topBarToo = true)
    onOpenSettings()
  }

  var colorWheelFor by remember { mutableStateOf<Int?>(null) }
  var eyedropperOn by remember { mutableStateOf(false) }
  var renamingLayer by remember { mutableStateOf<Int?>(null) }
  fun hidePeekingTools() {
    toolsPeeking = false
    closeOverlays(topBarToo = true)
    colorWheelFor = null
  }
  fun enterPresent() {
    closeOverlays(topBarToo = true)
    colorWheelFor = null
    renamingLayer = null
    eyedropperOn = false
    canvas.onColorSampled = null
    toolsPeeking = false
    present = true
    presentHintVisible = true
  }
  fun leavePresent() {
    presentHintVisible = false
    hidePeekingTools()
    present = false
  }
  LaunchedEffect(presentHintVisible) {
    if (presentHintVisible) {
      delay(PRESENT_HINT_MS)
      presentHintVisible = false
    }
  }
  // The space the toolbar covers beside the page, as the dock last reported it. Present mode
  // hides the toolbar, so the page may use the whole screen; leaving restores these.
  val dockSides = remember { floatArrayOf(0f, 0f) }
  // Where the page was panned and zoomed to just before Present: leaving puts it back.
  val viewBeforePresent = remember { ViewMemory() }
  DisposableEffect(present) {
    // Order matters: every call below re-clamps the view with the rules in force at that moment.
    canvas.presenting = present // hides the selection box and handles, stops new selections, lets the page start at the very top
    if (present) {
      viewBeforePresent.keep(canvas.currentView())
      canvas.setSideInsets(0f, 0f)
      canvas.fitToWidth() // fills the screen
    } else {
      canvas.setSideInsets(dockSides[0], dockSides[1])
      viewBeforePresent.take()?.let(canvas::restoreView)
    }
    onDispose { canvas.presenting = false }
  }
  BackHandler(enabled = chrome.backLeavesPresent) { leavePresent() }
  val messages = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  LaunchedEffect(exportMessage) { showExportMessage(exportMessage, messages, onExportMessageShown) }

  fun sendToolsToCanvas() {
    val safeIndex = selected.coerceIn(0, tools.lastIndex)
    if (safeIndex != selected) selected = safeIndex
    canvas.tool = tools[safeIndex]
    val eraser = tools.firstOrNull { it.isEraser }
    if (eraser != null) {
      canvas.eraserRadiusDp = eraser.sizeDp
      canvas.partialErase = eraser.partialErase
    }
    toolSettings.saveTools(tools)
    toolSettings.selectedIndex = safeIndex
  }

  // Live-resize pen slots whenever the user adjusts penCount in Settings.
  LaunchedEffect(toolbarConfig.penCount) {
    val currentPenCount = tools.count { it.kind == InkTool.Kind.PEN }
    if (currentPenCount != toolbarConfig.penCount) {
      val prevTool = tools.getOrNull(selected)
      val resized = toolSettings.resizeTools(tools.toList(), toolbarConfig.penCount)
      tools.clear()
      tools.addAll(resized)
      selected =
        when {
          prevTool == null -> 0
          prevTool.kind == InkTool.Kind.PEN -> selected.coerceIn(0, toolbarConfig.penCount - 1)
          else -> tools.indexOfFirst { it.kind == prevTool.kind }.takeIf { it >= 0 } ?: 0
        }
      if (openPanel != null && openPanel!! >= 0) openPanel = null
      sendToolsToCanvas()
    }
  }

  // This notebook is what a cabled projector shows; the one-time offer comes through the snackbar.
  DisposableEffect(canvas) {
    projector.attach(canvas)
    onDispose { projector.detach(canvas) }
  }
  LaunchedEffect(projector.offerPending) {
    if (projector.offerPending && !present) {
      projector.offerShown()
      val result = messages.showSnackbar("Projector connected", actionLabel = "Show only the page", duration = SnackbarDuration.Long)
      if (result == SnackbarResult.ActionPerformed) projector.begin()
    }
  }
  DisposableEffect(canvas) {
    sendToolsToCanvas()
    canvas.onHistoryChanged = {
      canUndo = canvas.canUndo
      canRedo = canvas.canRedo
      canClear = !canvas.isEmpty
      contentVersion++
      onPageEdited()
      projector.markDirty() // stroke committed, undo/redo, items, pages, layers, paper: all end here
    }
    canvas.onPageTouched = {
      jumpBack = null
      presentHintVisible = false
      closeOverlays(topBarToo = true)
    }
    canvas.onClipboardChanged = { hasClipboard = it }
    canvas.onPasteMenu = { x, y ->
      if (!present) {
        closeOverlays(topBarToo = true)
        pasteMenuAt = Offset(x, y)
      }
    }
    canvas.onCurrentPageChanged = { currentPage = it }
    canvas.onTextRequest = { id, existing -> editingText = id to existing }
    canvas.onNotice = { text -> if (!present) scope.launch { messages.currentSnackbarData?.dismiss(); messages.showSnackbar(text, duration = SnackbarDuration.Short) } }
    canvas.onLayersChanged = {
      layerState = canvas.layers
      onPageEdited() // worth saving
      projector.markDirty()
    }
    canvas.onPicturesUpdated = {
      contentVersion++
      projector.markDirty() // a PDF page finished drawing
    }
    canvas.onViewChanged = { zoom, angle ->
      viewZoom = zoom
      viewAngle = angle
    }
    canvas.onSelectionChanged = { r ->
      selectionRect = r?.let { Rect(it.left, it.top, it.right, it.bottom) }
      if (r == null) selectionMoreOpen = false
    }

    onDispose {
      canvas.onHistoryChanged = {}
      canvas.onNotice = {}
      canvas.onPageTouched = {}
      canvas.onSelectionChanged = {}
      canvas.onClipboardChanged = {}
      canvas.onPasteMenu = { _, _ -> }
      canvas.onViewChanged = { _, _ -> }
      canvas.onCurrentPageChanged = {}
      canvas.onTextRequest = { _, _ -> }
      canvas.onLayersChanged = {}
      canvas.onPicturesUpdated = {}
    }
  }

  fun togglePanel(cell: Int) {
    if (openPanel == cell) {
      openPanel = null
    } else {
      closeTopBarOverlays()
      pasteMenuAt = null
      shownPanel = cell
      openPanel = cell
    }
  }

  fun clearPageWithUndo() {
    openPanel = null
    canvas.clear()
    scope.launch {
      messages.currentSnackbarData?.dismiss()
      val result = messages.showSnackbar("Page cleared", actionLabel = "Undo", duration = SnackbarDuration.Short)
      if (result == SnackbarResult.ActionPerformed) canvas.undo()
    }
  }

  fun startEyedropper(slot: Int) {
    openPanel = null
    dock.wheelOpen = false
    colorWheelFor = null
    eyedropperOn = true
    canvas.onColorSampled = { sampled ->
      eyedropperOn = false
      if (sampled != null && slot in tools.indices) {
        val color = (sampled and 0x00FFFFFF) or (tools[slot].paperColor and 0xFF000000.toInt())
        tools[slot] = tools[slot].copy(paperColor = color)
        toolSettings.addRecentColor(color)
        sendToolsToCanvas()
      }
    }
  }

  val isPalm = { canvas.isPenWriting }
  val edgeAccent = androidx.compose.material3.MaterialTheme.colorScheme.primary
  LaunchedEffect(edgeAccent) { canvas.pageEdgeAccent = edgeAccent.copy(alpha = 0.55f).toArgb() }

  // Measure left & right floating island widths so DockedToolbar knows the exact free middle span.
  var leftIslandWidthPx by remember { mutableIntStateOf(0) }
  var rightIslandWidthPx by remember { mutableIntStateOf(0) }

  Box(
    Modifier.fillMaxSize().pointerInput(dock) {
      awaitPointerEventScope {
        while (true) {
          val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
          if (event.type != androidx.compose.ui.input.pointer.PointerEventType.Press) continue
          val at = event.changes.firstOrNull()?.position ?: continue
          if (dock.isOnDock(at)) {
            if (topBarOverlayOpen()) closeTopBarOverlays()
            continue
          }
          if (pasteMenuAt != null && pasteMenuBounds[0]?.contains(at) == true) {
            continue
          }
          val onTopBar = topBarBounds.any { it?.contains(at) == true }
          val somethingOpen = openPanel != null || dock.wheelOpen || pasteMenuAt != null || (!onTopBar && topBarOverlayOpen())
          closeOverlays(topBarToo = !onTopBar)
          if (somethingOpen) canvas.closedSomethingAt = event.changes.first().uptimeMillis
        }
      }
    }
  ) {
    AndroidView(factory = { canvas }, modifier = Modifier.fillMaxSize())

    val density = LocalDensity.current
    val safe = WindowInsets.safeDrawing
    val statusTopPx = WindowInsets.statusBars.getTop(density)
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    // Back (sides) and Home (bottom) swipe zones: the corner wheel keeps clear of them.
    val gestureZones = WindowInsets.systemGestures.union(WindowInsets.mandatorySystemGestures)
    val insets =
      with(density) {
        DockInsets(
          top = statusTopPx.toDp(),
          bottom = safe.getBottom(density).toDp(),
          left = safe.getLeft(density, layoutDirection).toDp(),
          right = safe.getRight(density, layoutDirection).toDp(),
          topButtons = TOP_BUTTONS_HEIGHT,
          topRowLeftClearance = leftIslandWidthPx.toDp(),
          topRowRightClearance = rightIslandWidthPx.toDp(),
          topChromeVisible = chrome.topBar,
          gestureLeft = gestureZones.getLeft(density, layoutDirection).toDp(),
          gestureRight = gestureZones.getRight(density, layoutDirection).toDp(),
          gestureBottom = gestureZones.getBottom(density).toDp(),
        )
      }

    // Build ordered DockItems directly from ToolbarConfig.
    val penIndices = tools.indices.filter { tools[it].kind == InkTool.Kind.PEN }
    val eraserIndex = tools.indexOfFirst { it.isEraser }
    val lassoIndex = tools.indexOfFirst { it.isLasso }
    val fillIndex = tools.indexOfFirst { it.isFill }
    val highlighterIndex = tools.indexOfFirst { it.isHighlighter }
    val textIndex = tools.indexOfFirst { it.isText }

    val dockItems = buildList {
      for (item in toolbarConfig.visibleOrderedItems) {
        when (item) {
          ToolbarItem.PENS -> penIndices.forEach { add(DockItem.Tool(it)) }
          ToolbarItem.ERASER -> if (eraserIndex >= 0) add(DockItem.Tool(eraserIndex))
          ToolbarItem.SELECT -> if (lassoIndex >= 0) add(DockItem.Tool(lassoIndex))
          ToolbarItem.FILL -> if (fillIndex >= 0) add(DockItem.Tool(fillIndex))
          ToolbarItem.TOOLS -> add(DockItem.Instruments)
          ToolbarItem.MORE -> add(DockItem.More)
          ToolbarItem.UNDO_REDO -> {
            add(DockItem.Undo)
            add(DockItem.Redo)
          }
          ToolbarItem.HIGHLIGHTER -> if (highlighterIndex >= 0) add(DockItem.Tool(highlighterIndex))
          ToolbarItem.TEXT -> if (textIndex >= 0) add(DockItem.Tool(textIndex))
          ToolbarItem.LAYERS -> add(DockItem.Layers)
          ToolbarItem.CLEAR -> add(DockItem.Clear)
        }
      }
    }

    val onDockItem: (DockItem) -> Unit = { item ->
      canvas.onColorSampled = null
      eyedropperOn = false
      closeTopBarOverlays()
      pasteMenuAt = null
      when (item) {
        is DockItem.Tool -> {
          val index = item.index
          if (index == selected) {
            if (!tools[index].isLasso) togglePanel(index)
          } else {
            selected = index
            openPanel = null
            dock.wheelOpen = false
            sendToolsToCanvas()
          }
        }
        DockItem.Undo -> canvas.undo()
        DockItem.Redo -> canvas.redo()
        DockItem.Paper -> {
          dock.wheelOpen = false
          togglePanel(PAPER_CELL)
        }
        DockItem.Layers -> {
          dock.wheelOpen = false
          togglePanel(LAYERS_CELL)
        }
        DockItem.Instruments -> {
          dock.wheelOpen = false
          togglePanel(INSTRUMENTS_CELL)
        }
        DockItem.More -> {
          dock.wheelOpen = false
          togglePanel(MORE_CELL)
        }
        DockItem.Clear -> clearPageWithUndo()
      }
    }

    if (chrome.toolbar) DockedToolbar(
      dock = dock,
      items = dockItems,
      tools = tools,
      selectedIndex = selected,
      darkPage = darkPage,
      mmPerDp = mmPerDp,
      canUndo = canUndo,
      canRedo = canRedo,
      canClear = canClear,
      onItem = onDockItem,
      isPalm = isPalm,
      scope = scope,
      onDragStart = { closeOverlays(topBarToo = true) },
      insets = insets,
      panelKey = openPanel,
      onSidesCovered = { left, right ->
        dockSides[0] = left
        dockSides[1] = right
        if (!present) canvas.setSideInsets(left, right) // the peeking toolbar overlays the page
      },
      instrumentsActive = rulerOn || protractorOn,
      modifier = Modifier.fillMaxSize(), // raises itself above other chrome while open (inside DockedToolbar)
    ) {
      when (shownPanel) {
        MORE_CELL -> {
          MoreToolsPanel(
            canClear = canClear,
            onAddFromPdf = {
              openPanel = null
              onImportPdf()
            },
            onAddImage = {
              openPanel = null
              onInsertImage()
            },
            onSelectHighlighter = {
              if (highlighterIndex >= 0) {
                selected = highlighterIndex
                sendToolsToCanvas()
                shownPanel = highlighterIndex
                openPanel = highlighterIndex
              } else {
                openPanel = null
              }
            },
            onSelectTextBox = {
              if (textIndex >= 0) {
                selected = textIndex
                sendToolsToCanvas()
              }
              openPanel = null
            },
            onOpenLayers = {
              shownPanel = LAYERS_CELL
              openPanel = LAYERS_CELL
            },
            onClearPage = { clearPageWithUndo() },
            isPalm = isPalm,
          )
        }
        LAYERS_CELL -> {
          LayersPanel(
            names = layerState.names,
            hidden = layerState.hidden,
            current = layerState.current,
            onSelect = canvas::selectLayer,
            onVisible = canvas::setLayerVisible,
            onRename = { renamingLayer = it },
            onDelete = { index ->
              canvas.deleteLayer(index)
              scope.launch {
                messages.currentSnackbarData?.dismiss()
                val result = messages.showSnackbar("Layer deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) canvas.undo()
              }
            },
            onAdd = canvas::addLayer,
            isPalm = isPalm,
          )
        }
        INSTRUMENTS_CELL -> {
          InstrumentsPanel(
            rulerOn = rulerOn,
            protractorOn = protractorOn,
            onRuler = {
              rulerOn = it
              canvas.rulerVisible = it
            },
            onProtractor = {
              protractorOn = it
              canvas.protractorVisible = it
            },
            shapeSnappingOn = viewSettings.shapeSnapping,
            onShapeSnapping = viewSettings::setShapeSnapping,
            isPalm = isPalm,
          )
        }
        PAPER_CELL -> {
          PaperPanel(
            style = pageStyle,
            darkPage = darkPage,
            onStylePicked = {
              pageStyle = it
              canvas.pageStyle = it
              rulingMm = 0f
              canvas.rulingMm = 0f
            },
            spacingMm = rulingMm,
            onSpacing = {
              rulingMm = it
              canvas.rulingMm = it
            },
            isPdfNotebook = isPdfNotebook,
            showPaperModeToggle = true,
            isPalm = isPalm,
          )
        }
        else -> {
          val panelTool = tools.getOrNull(shownPanel)
          if (panelTool != null) {
            ToolOptionsPanel(
              tool = panelTool,
              darkPage = darkPage,
              onStylePicked = {
                tools[shownPanel] = tools[shownPanel].copy(style = it)
                sendToolsToCanvas()
              },
              onLinePicked = {
                tools[shownPanel] = tools[shownPanel].copy(line = it)
                sendToolsToCanvas()
              },
              onEraseModePicked = {
                tools[shownPanel] = tools[shownPanel].copy(partialErase = it)
                sendToolsToCanvas()
              },
              favourites = toolSettings.favourites.getValue(panelTool.kind),
              mmPerDp = mmPerDp,
              onSizeChanging = {
                tools[shownPanel] = tools[shownPanel].copy(sizeDp = Math.round(it * 10f) / 10f)
                sendToolsToCanvas()
              },
              onSizePicked = {
                tools[shownPanel] = tools[shownPanel].copy(sizeDp = it)
                sendToolsToCanvas()
              },
              onAddFavourite = { toolSettings.addFavourite(panelTool.kind, tools[shownPanel].sizeDp) },
              onRemoveFavourite = { toolSettings.removeFavourite(panelTool.kind, it) },
              onColorPicked = {
                tools[shownPanel] = tools[shownPanel].copy(paperColor = it)
                sendToolsToCanvas()
              },
              onMoreColors = {
                colorWheelFor = shownPanel
                openPanel = null
                dock.wheelOpen = false
              },
              onPickEyedropper = { startEyedropper(shownPanel) },
              isPalm = isPalm,
            )
          }
        }
      }
    }

    val railLeft = if (dock.side == DockSide.LEFT) RailWidth else 0.dp
    val railRight = if (dock.side == DockSide.RIGHT) RailWidth else 0.dp
    val selectionTopClearancePx =
      with(density) {
        if (chrome.topBar) statusTopPx + (TOP_BUTTONS_HEIGHT + 4.dp).roundToPx()
        else statusTopPx + 12.dp.roundToPx()
      }
    val selectionBottomClearancePx =
      with(density) {
        safe.getBottom(density) + (if (dock.side == DockSide.BOTTOM) BarHeight + 24.dp else 12.dp).roundToPx()
      }
    val selectionLeftClearancePx = with(density) { safe.getLeft(density, layoutDirection) + (railLeft + 12.dp).roundToPx() }
    val selectionRightClearancePx = with(density) { safe.getRight(density, layoutDirection) + (railRight + 12.dp).roundToPx() }

    if (selectionRect != null && chrome.selectionUi) {
      SelectionActions(
        hasClipboard = hasClipboard,
        canFillSelection = selectionRect.let { canvas.selectionHasStrokes },
        moreExpanded = selectionMoreOpen,
        onMoreExpandedChange = { selectionMoreOpen = it },
        onCopy = canvas::copySelection,
        onCut = canvas::cutSelection,
        onPaste = canvas::paste,
        onDuplicate = canvas::duplicateSelection,
        onDelete = canvas::deleteSelection,
        onMirror = canvas::mirrorSelection,
        onFill = {
          canvas.fillSelection(
            tools.firstOrNull { it.isFill }?.paperColor ?: dev.penlab.app.ink.InkTools.fillColors[0].paperColor
          )
        },
        onDeselect = canvas::clearSelection,
        pluginActions =
          pluginActions(false).map { entry ->
            SelectionPluginAction(
              id = entry.plugin.id,
              label = entry.capability.label,
              onClick = { onPluginAction(entry) },
              iconRes = entry.capability.iconRes,
            )
          },
        onUnavailablePlugin = { slot ->
          scope.launch {
            messages.currentSnackbarData?.dismiss()
            messages.showSnackbar(slot.unavailableNotice, duration = SnackbarDuration.Short)
          }
        },
        isPalm = isPalm,
        modifier =
          Modifier
            .zIndex(2.2f)
            .besideSelection(
              rect = { selectionRect },
              topClearancePx = selectionTopClearancePx,
              bottomClearancePx = selectionBottomClearancePx,
              leftClearancePx = selectionLeftClearancePx,
              rightClearancePx = selectionRightClearancePx,
            ),
      )
    }
    pasteMenuAt?.takeIf { chrome.selectionUi }?.let { point ->
      PasteMenu(
        onPaste = {
          canvas.pasteAtHeldPoint()
          pasteMenuAt = null
          pasteMenuBounds[0] = null
        },
        onClear = {
          canvas.clearClipboard()
          pasteMenuAt = null
          pasteMenuBounds[0] = null
        },
        isPalm = isPalm,
        modifier =
          Modifier
            .zIndex(2.2f)
            .besideSelection(
              rect = { Rect(point, point) },
              topClearancePx = selectionTopClearancePx,
              bottomClearancePx = selectionBottomClearancePx,
              leftClearancePx = selectionLeftClearancePx,
              rightClearancePx = selectionRightClearancePx,
            )
            .onGloballyPositioned { pasteMenuBounds[0] = it.boundsInRoot() },
      )
    }
    if (
      shouldShowFloatingPastePill(
        selectionUiVisible = chrome.selectionUi,
        hasClipboard = hasClipboard,
        hasSelection = selectionRect != null,
        pasteMenuOpen = pasteMenuAt != null,
        isLassoSelected = tools.getOrNull(selected)?.isLasso == true,
      )
    ) {
      PasteMenu(
        onPaste = canvas::paste,
        onClear = canvas::clearClipboard,
        isPalm = isPalm,
        modifier =
          Modifier
            .align(Alignment.TopCenter)
            .zIndex(2f)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = if (chrome.topBar) TOP_BUTTONS_HEIGHT + PenLabSpacing.xs else PenLabSpacing.md),
      )
    }

    val topChromeZ = if (topBarOverlayOpen()) 3f else 2f

    if (chrome.topBar) {
      // ---- Left Floating Island + Popovers ----
      Column(
        modifier =
          Modifier
            .align(Alignment.TopStart)
            .zIndex(topChromeZ)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = PenLabSpacing.md, start = PenLabSpacing.md + railLeft),
        horizontalAlignment = Alignment.Start,
      ) {
        FileChromePill(
          title = notebookInfo.title,
          folder = notebookInfo.folder,
          quickMenuOpen = fileQuickMenuOpen,
          optionsOpen = fileOptionsOpen,
          onBack = onBack,
          onToggleQuickMenu = {
            val next = !fileQuickMenuOpen
            closeOverlays(topBarToo = true)
            fileQuickMenuOpen = next
          },
          onToggleOptions = {
            val next = !fileOptionsOpen
            closeOverlays(topBarToo = true)
            fileOptionsOpen = next
          },
          isPalm = isPalm,
          isPdfNotebook = isPdfNotebook,
          modifier =
            Modifier
              .onSizeChanged { leftIslandWidthPx = it.width }
              .onGloballyPositioned { topBarBounds[0] = it.boundsInRoot() },
        )

        if (fileQuickMenuOpen) {
          Spacer(Modifier.height(PenLabSpacing.sm))
          FileQuickMenuPopover(
            current = notebookInfo,
            recentNotebooks = recentNotebooks,
            folderColorFor = folderColorFor,
            onSwitchNotebook = { target ->
              closeOverlays(topBarToo = true)
              onSwitchNotebook(target)
            },
            onRename = {
              closeOverlays(topBarToo = true)
              renamingNotebook = true
            },
            onMoveToFolder = {
              closeOverlays(topBarToo = true)
              movingNotebook = true
            },
            onTurnOrientation = onTurnNotebook?.let { turn ->
              {
                closeOverlays(topBarToo = true)
                turn()
              }
            },
            isPalm = isPalm,
            modifier = Modifier.onGloballyPositioned { topBarBounds[2] = it.boundsInRoot() },
          )
        } else if (fileOptionsOpen) {
          Spacer(Modifier.height(PenLabSpacing.sm))
          FileOptionsPopover(
            onExportPdf = {
              closeOverlays(topBarToo = true)
              onExport()
            },
            onShare = {
              closeOverlays(topBarToo = true)
              onShare()
            },
            onSyncGoogleDrive = {
              closeOverlays(topBarToo = true)
              onExport()
            },
            onPresentMode = {
              enterPresent()
            },
            projectorActive = if (projector.available) projector.active else null,
            onProjectorView = {
              closeOverlays(topBarToo = true)
              projector.toggle()
            },
            onOpenSettings = ::openSettings,
            onReportBugs = {
              closeOverlays(topBarToo = true)
              val page = if (canvas.pageLayout != null) ", page ${currentPage + 1}" else ""
              onReport("${notebookInfo.title}$page")
            },
            pluginActions =
              pluginActions(true).map { entry ->
                MenuAction(IdeaIcon, entry.capability.label, {
                  closeOverlays(topBarToo = true)
                  onPluginAction(entry)
                }, entry.capability.iconRes)
              },
            isPalm = isPalm,
            modifier = Modifier.onGloballyPositioned { topBarBounds[2] = it.boundsInRoot() },
          )
        }
      }

      // ---- Right Floating Island + Popovers ----
      val layout = canvas.pageLayout
      val isPaged = layout != null
      val pageCount = if (layout != null) contentVersion.let { layout.count } else 1
      Column(
        modifier =
          Modifier
            .align(Alignment.TopEnd)
            .zIndex(topChromeZ)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = PenLabSpacing.md, end = PenLabSpacing.md + railRight),
        horizontalAlignment = Alignment.End,
      ) {
        PageConfigChromePill(
          searchAvailable = search != null,
          searchOpen = searchOpen,
          zoomPercent = { viewZoom },
          viewLocked = viewSettings.zoomLocked || viewSettings.rotationLocked,
          viewPanelOpen = viewPanelOpen,
          isPaged = isPaged,
          currentPage = { currentPage },
          pageCount = pageCount,
          pageConfigOpen = pageConfigOpen,
          onToggleSearch = {
            val next = !searchOpen
            closeOverlays(topBarToo = true)
            searchOpen = next
            if (next) runSearch()
          },
          onZoomOut = {
            canvas.setView(zoom = stepZoomOut(viewZoom) / 100f)
          },
          onToggleViewPanel = {
            val next = !viewPanelOpen
            closeOverlays(topBarToo = true)
            viewPanelOpen = next
          },
          onZoomIn = {
            canvas.setView(zoom = stepZoomIn(viewZoom) / 100f)
          },
          onEnterFullScreen = {
            closeOverlays(topBarToo = true)
            fullScreen = true
          },
          onTogglePageConfig = {
            val next = !pageConfigOpen
            closeOverlays(topBarToo = true)
            pageConfigOpen = next
          },
          isPalm = isPalm,
          modifier =
            Modifier
              .onSizeChanged { rightIslandWidthPx = it.width }
              .onGloballyPositioned { topBarBounds[1] = it.boundsInRoot() },
        )

        if (searchOpen) {
          Spacer(Modifier.height(PenLabSpacing.sm))
          SearchPanel(
            query = searchQuery,
            onQueryChange = {
              searchQuery = it
              searchShown = null
              runSearch()
            },
            hits = searchHits,
            status = searchStatus,
            paged = isPaged,
            onOpen = ::showHit,
            isPalm = isPalm,
            modifier = Modifier.onGloballyPositioned { topBarBounds[3] = it.boundsInRoot() },
          )
        } else if (viewPanelOpen) {
          Spacer(Modifier.height(PenLabSpacing.sm))
          ViewPanel(
            settings = viewSettings,
            zoomPercent = viewZoom,
            angleDegrees = viewAngle,
            onZoomPreset = { canvas.setView(zoom = it / 100f) },
            onAnglePreset = { canvas.setView(angleDegrees = it.toFloat()) },
            isPaged = isPaged,
            onFitOrHome = {
              closeOverlays(topBarToo = true)
              if (isPaged) canvas.fitToWidth() else canvas.goHome()
            },
            onOpenSettings = ::openSettings,
            isPalm = isPalm,
            modifier = Modifier.onGloballyPositioned { topBarBounds[3] = it.boundsInRoot() },
          )
        } else if (pageConfigOpen) {
          Spacer(Modifier.height(PenLabSpacing.sm))
          PageConfigPopover(
            isPaged = isPaged,
            isPdfNotebook = isPdfNotebook,
            selectedTab = pageConfigTab,
            onSelectTab = { pageConfigTab = it },
            pageCount = pageCount,
            currentPage = currentPage,
            bookmarks = layout?.let { contentVersion.let { layout.bookmarks } }.orEmpty(),
            contentVersion = contentVersion,
            thumbnail = canvas::pageThumbnail,
            onJumpPage = {
              if (jumpBack == null) jumpBack = canvas.currentView() to currentPage
              canvas.scrollToPage(it)
              pageConfigOpen = false
              topBarBounds[3] = null
            },
            onToggleBookmark = canvas::toggleBookmark,
            onInsertPage = canvas::insertPage,
            onDeletePage = canvas::deletePage,
            pageStyle = pageStyle,
            darkPage = darkPage,
            rulingMm = rulingMm,
            onStylePicked = {
              pageStyle = it
              canvas.pageStyle = it
              rulingMm = 0f
              canvas.rulingMm = 0f
            },
            onSpacing = {
              rulingMm = it
              canvas.rulingMm = it
            },
            layerNames = layerState.names,
            hiddenLayers = layerState.hidden,
            currentLayer = layerState.current,
            onSelectLayer = canvas::selectLayer,
            onLayerVisible = canvas::setLayerVisible,
            onRenameLayer = { renamingLayer = it },
            onDeleteLayer = { index ->
              canvas.deleteLayer(index)
              scope.launch {
                messages.currentSnackbarData?.dismiss()
                val result = messages.showSnackbar("Layer deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) canvas.undo()
              }
            },
            onAddLayer = canvas::addLayer,
            isPalm = isPalm,
            modifier = Modifier.onGloballyPositioned { topBarBounds[3] = it.boundsInRoot() },
          )
        } else {
          searchShown?.let { shown ->
            Spacer(Modifier.height(PenLabSpacing.sm))
            SearchStepper(
              index = shown,
              count = searchHits.size,
              onStep = ::showHit,
              onClose = {
                searchShown = null
                topBarBounds[3] = null
              },
              isPalm = isPalm,
              modifier = Modifier.onGloballyPositioned { topBarBounds[3] = it.boundsInRoot() },
            )
          }
        }
      }
    } else {
      topBarBounds.fill(null)
    }
    jumpBack?.takeIf { chrome.messages }?.let { (view, page) ->
      val label = if (canvas.pageLayout != null) "Back to page ${page + 1}" else "Back to where I was"
      Box(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 20.dp)) {
        dev.penlab.app.ui.ToolbarMessage(label, "Go", onAction = {
          canvas.restoreView(view)
          jumpBack = null
        })
      }
    }
    if (chrome.leaveFullScreenButton) {
      // Subtle floating exit pill tucked in the top-right safe area.
      PenLabChromePill(
        isPalm = isPalm,
        contentPadding = PaddingValues(0.dp),
        modifier =
          Modifier
            .align(Alignment.TopEnd)
            .zIndex(3f)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = PenLabSpacing.md, end = PenLabSpacing.md + railRight)
            .graphicsLayer { alpha = FULLSCREEN_EXIT_ALPHA },
      ) {
        PenLabIconButton(
          icon = PenLabIcons.FullScreenExit,
          contentDescription = "Leave full screen",
          onClick = { fullScreen = false },
        )
      }
    }
    if (chrome.presentButtons) {
      // Whisper-quiet translucent Present mode corner pill anchored bottom-right out of the writing center.
      val presentPillAlphaAnim by animateFloatAsState(
        targetValue = presentPillAlpha(toolsPeeking),
        animationSpec = Motion.tween(160),
        label = "presentPillAlpha",
      )
      PenLabChromePill(
        isPalm = isPalm,
        contentPadding = PaddingValues(horizontal = 2.dp),
        modifier =
          Modifier
            .align(Alignment.BottomEnd)
            .zIndex(3f)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(end = PenLabSpacing.lg + railRight, bottom = PenLabSpacing.lg)
            .graphicsLayer { alpha = presentPillAlphaAnim },
      ) {
        PenLabIconButton(
          icon = PenLabIcons.PenTools,
          contentDescription = if (toolsPeeking) "Hide pen tools" else "Show pen tools",
          selected = toolsPeeking,
          onClick = {
            presentHintVisible = false
            if (toolsPeeking) hidePeekingTools() else toolsPeeking = true
          },
        )
        Box(
          Modifier
            .width(1.dp)
            .height(20.dp)
            .background(androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
        )
        PenLabIconButton(
          icon = PenLabIcons.Close,
          contentDescription = "Exit present mode",
          onClick = { leavePresent() },
        )
      }

      AnimatedVisibility(
        visible = presentHintVisible,
        enter = fadeIn(Motion.tween(180)),
        exit = fadeOut(Motion.tween(140)),
        modifier =
          Modifier
            .align(Alignment.BottomCenter)
            .zIndex(2.5f)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 20.dp),
      ) {
        PenLabSnackbar(
          message = PRESENT_HINT_MESSAGE,
          modifier = Modifier.ignorePalm(isPalm),
        )
      }
    }
    // Locks and input toggles are kept in settings; the canvas reads its own copy for speed.
    canvas.zoomLocked = viewSettings.zoomLocked
    canvas.rotationLocked = viewSettings.rotationLocked
    canvas.shapeSnappingEnabled = viewSettings.shapeSnapping

    colorWheelFor?.let { slot ->
      val tool = tools.getOrNull(slot)
      if (tool != null) {
        ColorWheel(
          initial = tool.paperColor,
          darkPage = darkPage,
          recent = toolSettings.recentColors,
          onPicked = { color, done ->
            tools[slot] = tools[slot].copy(paperColor = color)
            sendToolsToCanvas()
            if (done) {
              toolSettings.addRecentColor(color)
              colorWheelFor = null
            }
          },
          onEyedropper = { startEyedropper(slot) },
          onDismiss = {
            toolSettings.addRecentColor(tools[slot].paperColor)
            colorWheelFor = null
          },
          hub = if (dock.side.isCorner) dock.wheelHub else null,
        )
      }
    }

    if (eyedropperOn) {
      if (chrome.toolbar) ToolbarMessage(
        "Tap ink on the page to take its colour",
        "Cancel",
        onAction = {
          canvas.onColorSampled = null
          eyedropperOn = false
        },
        modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 72.dp),
      )
    }

    if (renamingNotebook) {
      RenameDialog(
        title = "Rename notebook",
        initial = notebookInfo.title,
        onDismiss = { renamingNotebook = false },
        onDone = { title ->
          renamingNotebook = false
          onRenameNotebook(title)
        },
      )
    }

    if (movingNotebook) {
      MoveNotebookDialog(
        notebookTitle = notebookInfo.title,
        currentFolder = notebookInfo.folder,
        folders = folders,
        onDismiss = { movingNotebook = false },
        onMove = { folder ->
          movingNotebook = false
          onMoveNotebook(folder)
        },
      )
    }

    renamingLayer?.let { index ->
      RenameDialog(
        title = "Rename layer",
        initial = layerState.names.getOrElse(index) { "" },
        onDismiss = { renamingLayer = null },
        onDone = { name ->
          canvas.renameLayer(index, name)
          renamingLayer = null
        },
      )
    }

    editingText?.let { (id, existing) ->
      TextBoxDialog(
        initial = existing,
        isNew = id == null,
        onDismiss = { editingText = null },
        onDone = { text ->
          canvas.commitText(id, text)
          editingText = null
        },
      )
    }

    SnackbarHost(
      hostState = messages,
      modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = PenLabSpacing.lg),
    ) { data ->
      if (chrome.messages) ToolbarMessage(data.visuals.message, data.visuals.actionLabel, onAction = data::performAction)
    }
  }
}

/**
 * Places the selection or paste pill centred above its box, or flips below when there is no room
 * under the top chrome, always clamped inside the safe window bounds. The rectangle is read here,
 * in layout, so following a pan does not re-run the screen's composition.
 */
private fun Modifier.besideSelection(
  rect: () -> Rect?,
  topClearancePx: Int = 0,
  bottomClearancePx: Int = 0,
  leftClearancePx: Int = 0,
  rightClearancePx: Int = 0,
): Modifier = layout { measurable, constraints ->
  val buttons = measurable.measure(Constraints())
  layout(constraints.maxWidth, constraints.maxHeight) {
    val r = rect() ?: return@layout
    val gap = 12.dp.roundToPx()
    val placed =
      placeSelectionBar(
        selectionLeft = r.left,
        selectionTop = r.top,
        selectionRight = r.right,
        selectionBottom = r.bottom,
        barWidth = buttons.width,
        barHeight = buttons.height,
        containerWidth = constraints.maxWidth,
        containerHeight = constraints.maxHeight,
        gapPx = gap,
        topClearancePx = topClearancePx,
        bottomClearancePx = bottomClearancePx,
        leftClearancePx = leftClearancePx,
        rightClearancePx = rightClearancePx,
      )
    buttons.place(placed.x, placed.y)
  }
}

/**
 * Shows an export message, and clears it only after it has been seen. (Clearing it first changed
 * the effect's key, which cancelled the snackbar at once: no export message ever appeared.)
 * "Preparing…" stays up until the next message replaces it.
 */
private suspend fun showExportMessage(message: String?, messages: SnackbarHostState, onShown: () -> Unit) {
  if (message == null) return
  val waiting = message.startsWith("Preparing") || message.startsWith("Running")
  messages.currentSnackbarData?.dismiss()
  messages.showSnackbar(message, duration = if (waiting) SnackbarDuration.Indefinite else SnackbarDuration.Short)
  onShown()
}

