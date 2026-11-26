package dev.penlab.app.notes

import android.util.Log
import androidx.core.util.AtomicFile
import dev.penlab.app.ink.PageStore
import dev.penlab.app.ink.PageStyle
import java.io.File
import java.util.Properties
import java.util.UUID

/** A4 pages stacked like a notebook, or one endless canvas. */
enum class NotebookKind { A4, INFINITE }

/**
 * What the library knows about a notebook without opening it. [folder] is a path such as
 * "Mechanics/Lectures" ("" = no folder). [bookmarks] are page numbers, starting at 0.
 */
data class NotebookInfo(
  val id: String,
  val title: String,
  val folder: String,
  val kind: NotebookKind,
  val pageStyle: PageStyle,
  val pageCount: Int,
  val bookmarks: List<Int>,
  val created: Long,
  val modified: Long,
  /** Page size; A4 unless the notebook was made from a PDF. */
  val pageWidthMm: Float = 210f,
  val pageHeightMm: Float = 297f,
  /** For each page, the page of `document.pdf` under it (-1 = plain paper). Empty: no PDF. */
  val pdfPages: List<Int> = emptyList(),
  /** Ruling spacing in mm chosen for this notebook (0 = the paper's usual spacing). */
  val rulingMm: Float = 0f,
  /** Layer names, bottom first; which are hidden; which one the pen draws on. */
  val layerNames: List<String> = listOf("Layer 1"),
  /**
   * Paths of audio recordings made in this notebook by earlier versions. Nothing here uses them;
   * they are kept so saving does not lose them (a stroke's MediaMark refers into this list).
   */
  val recordings: List<String> = emptyList(),
  val hiddenLayers: List<Int> = emptyList(),
  val currentLayer: Int = 0,
)

/**
 * The library on disk: one folder per notebook under [LibraryLocation.root] (Documents/PenLab, or
 * the app's private `files/library/` without "All files access"), each holding
 * `notebook.properties` (title, folder, kind, page count, ...) and `notebook.graphite` (the
 * strokes, see PageFile). Deleting moves a notebook to `.trash/` beside them, so it can be
 * brought back.
 *
 * All methods do file work; call them off the main thread except where noted.
 */
class NotebookStore(private val location: LibraryLocation) {
  // Read from the location each time: it moves to Documents/PenLab when "All files access" is granted.
  private val library: File get() = location.root
  private val trash: File get() = File(library, ".trash")
  private val oldSinglePage get() = File(location.privateDir, "current-page.graphite")
  private val folderList: File get() = File(library, "folders.txt")
  private val folderColorList: File get() = File(library, "folder-colors.txt")

  /**
   * Every folder, as paths like "Mechanics" and "Mechanics/Lectures". Kept in `folders.txt` so
   * empty folders exist too; folders used by notebooks are always included, and so is every
   * parent of a listed folder.
   */
  fun folders(): List<String> {
    val listed = if (folderList.exists()) folderList.readLines().map(::cleanFolder) else emptyList()
    val used = list().map { it.folder }
    return (listed + used).filter { it.isNotEmpty() }.flatMap(::withParents).distinct().sorted()
  }

  fun createFolder(path: String) {
    val clean = cleanFolder(path)
    if (clean.isEmpty()) return
    writeFolders(folders() + clean)
  }

  /**
   * Renames a folder and everything inside it (subfolders and notebooks). Moving a folder is a
   * rename too: "Mechanics" -> "Fall 2026/Mechanics".
   */
  fun renameFolder(from: String, to: String) {
    val target = cleanFolder(to)
    if (target.isEmpty() || target == from || target.startsWith("$from/")) return // not into itself
    fun moved(path: String) = if (path == from || path.startsWith("$from/")) target + path.removePrefix(from) else path
    writeFolders(folders().map(::moved))
    for (info in list()) if (info.folder != moved(info.folder)) write(info.copy(folder = moved(info.folder)))
    // Colours move with their folders.
    val colors = folderColors()
    if (colors.keys.any { it == from || it.startsWith("$from/") }) writeFolderColors(colors.mapKeys { moved(it.key) })
  }

  /**
   * Folder colours (ARGB), by folder path, from `folder-colors.txt` ("path<TAB>colour" per line).
   * A folder without its own colour shows its parent's (see [colorOf]).
   */
  fun folderColors(): Map<String, Int> =
    try {
      if (!folderColorList.exists()) emptyMap()
      else folderColorList.readLines().mapNotNull { line ->
        val tab = line.lastIndexOf('\t')
        if (tab <= 0) null else line.substring(0, tab) to line.substring(tab + 1).toLong(16).toInt()
      }.toMap()
    } catch (e: Exception) {
      emptyMap() // a broken colours file only loses colours
    }

  /** Sets (or with null, clears) a folder's own colour. */
  fun setFolderColor(path: String, color: Int?) {
    val colors = folderColors().toMutableMap()
    if (color == null) colors.remove(path) else colors[path] = color
    writeFolderColors(colors)
  }

  private fun writeFolderColors(colors: Map<String, Int>) {
    val file = AtomicFile(folderColorList)
    val out = file.startWrite()
    try {
      out.write(colors.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}\t${Integer.toHexString(it.value)}" }.toByteArray())
      file.finishWrite(out)
    } catch (e: Exception) {
      file.failWrite(out)
    }
  }

  /**
   * Removes an empty folder and its own colour (a new folder of the same name must not come back
   * coloured). Returns false if it still holds notebooks or folders.
   */
  fun deleteFolder(path: String): Boolean {
    val all = folders()
    if (list().any { it.folder == path || it.folder.startsWith("$path/") }) return false
    if (all.any { it.startsWith("$path/") }) return false
    writeFolders(all - path)
    if (path in folderColors()) setFolderColor(path, null)
    return true
  }

  /** Undo of [deleteFolder]: the folder back under its parent, with the colour it had of its own (null = none). */
  fun restoreFolder(path: String, color: Int?) {
    createFolder(path)
    if (color != null) setFolderColor(path, color)
  }

  private fun writeFolders(paths: List<String>) {
    val file = AtomicFile(folderList)
    val out = file.startWrite()
    try {
      out.write(paths.distinct().sorted().joinToString("\n").toByteArray())
      file.finishWrite(out)
    } catch (e: Exception) {
      file.failWrite(out)
    }
  }

  private fun withParents(path: String): List<String> {
    val parts = path.split("/")
    return parts.indices.map { parts.subList(0, it + 1).joinToString("/") }
  }

  fun list(): List<NotebookInfo> =
    library.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
      ?.mapNotNull(::readInfo)
      ?.sortedByDescending { it.modified }
      .orEmpty()

  fun create(title: String, folder: String, kind: NotebookKind, pageStyle: PageStyle): NotebookInfo {
    val now = System.currentTimeMillis()
    val info = NotebookInfo(UUID.randomUUID().toString(), title.trim().ifEmpty { "Untitled" }, cleanFolder(folder), kind, pageStyle, 1, emptyList(), now, now)
    File(library, info.id).mkdirs()
    write(info)
    return info
  }

  /** Saves changed details (title, folder, page count, bookmarks, ...). */
  fun write(info: NotebookInfo) {
    val props = Properties().apply {
      setProperty("title", info.title)
      setProperty("folder", info.folder)
      setProperty("kind", info.kind.name)
      setProperty("pageStyle", info.pageStyle.name)
      setProperty("pageCount", info.pageCount.toString())
      setProperty("bookmarks", info.bookmarks.joinToString(","))
      setProperty("created", info.created.toString())
      setProperty("modified", info.modified.toString())
      setProperty("pageWidthMm", info.pageWidthMm.toString())
      setProperty("pageHeightMm", info.pageHeightMm.toString())
      setProperty("pdfPages", info.pdfPages.joinToString(","))
      setProperty("rulingMm", info.rulingMm.toString())
      // Names separated by a character nobody types (Unit Separator).
      setProperty("layers", info.layerNames.joinToString("\u001F"))
      setProperty("recordings", info.recordings.joinToString("\u001F"))
      setProperty("hiddenLayers", info.hiddenLayers.joinToString(","))
      setProperty("currentLayer", info.currentLayer.toString())
    }
    val file = AtomicFile(File(File(library, info.id), META))
    val out = file.startWrite()
    try {
      props.store(out, "PenLab notebook")
      file.finishWrite(out)
    } catch (e: Exception) {
      file.failWrite(out)
      Log.e(TAG, "Could not save notebook details", e)
    }
  }

  /** Moves the notebook to the trash. Returns false if it was not found. */
  fun delete(info: NotebookInfo): Boolean {
    trash.mkdirs()
    return File(library, info.id).renameTo(File(trash, info.id))
  }

  /** Brings a deleted notebook back from the trash. */
  fun restore(info: NotebookInfo): Boolean = File(trash, info.id).renameTo(File(library, info.id))

  /** The notebook's photos folder (created on demand). Safe to call on the main thread. */
  fun imagesDir(info: NotebookInfo) = File(File(library, info.id), "images")

  /** The notebook's imported PDF (may not exist). Safe to call on the main thread. */
  fun pdfFile(info: NotebookInfo) = File(File(library, info.id), PDF)

  /** What handwriting search has read in this notebook (see HandwritingIndex). */
  fun searchFile(info: NotebookInfo) = File(File(library, info.id), "search.txt")

  /**
   * Makes a notebook from what an importer produced (see the plugin API's ImportedNotebook): one
   * page per imported page at the given page size. A background PDF is moved in as `document.pdf`
   * (page numbers in [dev.penlab.plugin.ImportedPage.backgroundPdfPage] refer to it); page items
   * are saved into the notebook. Returns null if there is nothing to make a notebook from.
   */
  fun createFromImport(imported: dev.penlab.plugin.ImportedNotebook, folder: String): NotebookInfo? {
    if (imported.pages.isEmpty()) return null
    val id = UUID.randomUUID().toString()
    val dir = File(library, id).apply { mkdirs() }
    val pdf = imported.backgroundPdf
    try {
      if (pdf != null) {
        val target = File(dir, PDF)
        if (!pdf.renameTo(target)) pdf.copyTo(target, overwrite = true)
      }
    } catch (e: java.io.IOException) {
      dir.deleteRecursively() // no half-made notebook
      throw e
    }
    val pageCount = imported.pages.size
    val now = System.currentTimeMillis()
    val pdfPages = if (pdf != null) imported.pages.map { it.backgroundPdfPage } else emptyList()
    // Imported strokes may name layers beyond the first; the notebook gets one layer for each (capped).
    val layerCount = (imported.pages.maxOf { page -> page.strokes.maxOfOrNull { it.layer } ?: 0 } + 1).coerceIn(1, MAX_IMPORT_LAYERS)
    val info = NotebookInfo(
      id, imported.title.ifBlank { "Imported PDF" }, cleanFolder(folder), NotebookKind.A4, PageStyle.BLANK, pageCount, emptyList(), now, now,
      imported.pageWidthMm, imported.pageHeightMm, pdfPages, layerNames = List(layerCount) { "Layer ${it + 1}" },
    )
    write(info)
    if (imported.pages.any { it.items.isNotEmpty() || it.strokes.isNotEmpty() }) {
      val layout = dev.penlab.app.ink.PageLayout.paged(info.pageWidthMm, info.pageHeightMm, pageCount, emptyList(), emptyList())
      // Pictures are saved into the notebook's images/ and text becomes real text boxes (not grey plugin boxes).
      val images = imagesDir(info)
      val items = imported.pages.flatMapIndexed { index, page ->
        dev.penlab.app.plugins.PluginItemMaterialiser.stage(page.items, images).map {
          dev.penlab.app.plugins.PluginItemMaterialiser.toPageItem(it, dev.penlab.app.ink.PageLayout.UNITS_PER_MM, 0f, layout.top(index))
        }
      }
      // Strokes become ordinary ink (same objects as pen strokes), page by page, in the saved draw order; this all runs on the importer's worker thread.
      val strokes = dev.penlab.app.plugins.PluginStrokes.inDrawOrder(
        imported.pages.flatMapIndexed { index, page ->
          page.strokes.mapNotNull { dev.penlab.app.plugins.PluginStrokes.toInk(it, dev.penlab.app.ink.PageLayout.UNITS_PER_MM, 0f, layout.top(index), 0, layerCount) }
        }
      )
      val identity = FloatArray(9).also { android.graphics.Matrix().getValues(it) }
      pageStore(info).saveNow(dev.penlab.app.ink.PageSnapshot(strokes, PageStyle.BLANK, identity, items))
    }
    return info
  }

  /** Where this notebook's strokes are saved. Safe to call on the main thread. */
  fun pageStore(info: NotebookInfo) = PageStore(File(library, info.id), STROKES)

  /**
   * The app saved one page before notebooks existed. On first launch with notebooks, that page
   * becomes a notebook called "First notes" (an infinite canvas, like it was).
   */
  fun migrateSinglePage() {
    if (!oldSinglePage.exists() || list().isNotEmpty()) return
    val info = create("First notes", "", NotebookKind.INFINITE, PageStyle.BLANK)
    val target = File(File(library, info.id), STROKES)
    if (!oldSinglePage.renameTo(target)) Log.e(TAG, "Could not move the old page into a notebook")
  }

  private fun readInfo(dir: File): NotebookInfo? =
    try {
      val props = Properties().apply { File(dir, META).inputStream().use(::load) }
      NotebookInfo(
        id = dir.name,
        title = props.getProperty("title", "Untitled"),
        folder = props.getProperty("folder", ""),
        kind = NotebookKind.entries.firstOrNull { it.name == props.getProperty("kind") } ?: NotebookKind.A4,
        pageStyle = PageStyle.entries.firstOrNull { it.name == props.getProperty("pageStyle") } ?: PageStyle.BLANK,
        pageCount = props.getProperty("pageCount")?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        bookmarks = props.getProperty("bookmarks", "").split(",").mapNotNull { it.trim().toIntOrNull() },
        created = props.getProperty("created")?.toLongOrNull() ?: dir.lastModified(),
        modified = props.getProperty("modified")?.toLongOrNull() ?: dir.lastModified(),
        pageWidthMm = props.getProperty("pageWidthMm")?.toFloatOrNull() ?: 210f,
        pageHeightMm = props.getProperty("pageHeightMm")?.toFloatOrNull() ?: 297f,
        pdfPages = props.getProperty("pdfPages", "").split(",").mapNotNull { it.trim().toIntOrNull() },
        rulingMm = props.getProperty("rulingMm")?.toFloatOrNull() ?: 0f,
        layerNames = props.getProperty("layers")?.split("\u001F")?.filter { it.isNotEmpty() }?.ifEmpty { null } ?: listOf("Layer 1"),
        recordings = props.getProperty("recordings")?.split("\u001F")?.filter { it.isNotEmpty() } ?: emptyList(),
        hiddenLayers = props.getProperty("hiddenLayers", "").split(",").mapNotNull { it.trim().toIntOrNull() },
        currentLayer = props.getProperty("currentLayer")?.toIntOrNull() ?: 0,
      )
    } catch (e: Exception) {
      Log.e(TAG, "Unreadable notebook details in ${dir.name}", e)
      null
    }

  private companion object {
    const val TAG = "NotebookStore"
    const val META = "notebook.properties"
    const val STROKES = "notebook.graphite"
    const val PDF = "document.pdf"

    /** Most layers an import creates (a plugin cannot make an unusable layer list). */
    const val MAX_IMPORT_LAYERS = 16
  }
}

/** "  Mechanics / Lectures/ " → "Mechanics/Lectures". */
fun cleanFolder(folder: String): String = folder.split("/").map { it.trim() }.filter { it.isNotEmpty() }.joinToString("/")
