package dev.penlab.app.ink

import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.util.Log
import androidx.ink.brush.InputToolType
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.Executors

/**
 * What the canvas hands over for saving: the strokes themselves (they never change, so they
 * are safe to read on another thread), which of them are highlights, and the page settings.
 */
class PageSnapshot(
  val strokes: List<InkStroke>,
  val pageStyle: PageStyle,
  val view: FloatArray,
  val items: List<PageItem> = emptyList(),
  /** Undo and redo steps to keep (oldest first), or null to leave the saved history alone. */
  val history: Pair<List<StrokeHistory.Edit<InkStroke>>, List<StrokeHistory.Edit<InkStroke>>>? = null,
)

/** Kinds of non-stroke state in the undo history (see [StrokeHistory.recordState]). */
object HistoryStates {
  /** The photos and text boxes: a `List<PageItem>`. */
  const val ITEMS = 1

  /** Page count, bookmarks, PDF pages: a [SavedPages]. */
  const val PAGES = 2

  /** The layers' names: a `List<String>`. */
  const val LAYERS = 3
}

/** A page read back from disk, ready for the canvas. */
class LoadedPage(
  val strokes: List<InkStroke>,
  val pageStyle: PageStyle,
  val view: FloatArray,
  val items: List<PageItem> = emptyList(),
  /** Saved undo and redo steps, if they match these strokes. */
  val history: Pair<List<StrokeHistory.Edit<InkStroke>>, List<StrokeHistory.Edit<InkStroke>>>? = null,
)

/**
 * Keeps the page on disk. All file work happens on one background thread, in order, so saving
 * never holds up the pen. Writes go through [AtomicFile]: the new file is written beside the
 * old one and swapped in only when complete, so a crash mid-save leaves the last good save.
 *
 * A file that cannot be read is renamed to `*.unreadable-<time>` and never overwritten, so a
 * bug can cost the newest edits at worst, never the notes already saved.
 */
class PageStore(directory: File, fileName: String = "current-page.graphite") {
  private val file = AtomicFile(File(directory, fileName))

  /** Undo history beside the strokes (`notebook.history`); losing it only loses undo. */
  private val historyFile = AtomicFile(File(directory, fileName.substringBeforeLast('.') + ".history"))
  private val io = Executors.newSingleThreadExecutor { Thread(it, "penlab-save").apply { priority = Thread.MIN_PRIORITY } }
  private val main = Handler(Looper.getMainLooper())

  /** Called on the main thread when a save fails (storage full, ...); the last good save is kept. */
  var onSaveFailed: () -> Unit = {}

  /** Reads the page in the background and hands it to [onLoaded] on the main thread (null if none). */
  fun load(onLoaded: (LoadedPage?) -> Unit) {
    io.execute {
      val page =
        try {
          val started = android.os.SystemClock.uptimeMillis()
          val saved = file.openRead().use { PageFile.read(it) }
          val read = android.os.SystemClock.uptimeMillis()
          val history =
            try {
              historyFile.openRead().use { HistoryFile.read(it) }.takeIf { it.strokeCount == saved.strokes.size }
            } catch (_: Exception) {
              null // no history yet, or unreadable: undo just starts fresh
            }
          saved.toLoadedPage(history).also {
            Log.i(PERF, "load: read ${read - started} ms, strokes ${android.os.SystemClock.uptimeMillis() - read} ms, ${it.strokes.size} strokes")
          }
        } catch (_: FileNotFoundException) {
          null // first run: nothing saved yet
        } catch (e: Exception) {
          Log.e(TAG, "Saved page could not be read; keeping it aside", e)
          file.baseFile.renameTo(File(file.baseFile.path + ".unreadable-" + System.currentTimeMillis()))
          null
        }
      main.post { onLoaded(page) }
    }
  }

  /**
   * Just the strokes, read on the calling thread (a background thread), with no side effects:
   * an unreadable file is left alone here (opening the notebook deals with it). Null if none.
   */
  fun readStrokesNow(): List<InkStroke>? =
    try {
      file.openRead().use { PageFile.read(it) }.toLoadedPage(null).strokes
    } catch (_: Exception) {
      null
    }

  /** Strokes and items (photos, text boxes, diagrams), read on the calling thread; null if none or unreadable. */
  fun readNow(): LoadedPage? =
    try {
      file.openRead().use { PageFile.read(it) }.toLoadedPage(null)
    } catch (_: Exception) {
      null
    }

  /** When the strokes were last saved (0 if never). */
  val savedAt: Long
    get() = file.baseFile.lastModified()

  /** Forgets the undo history (after the strokes were changed outside the canvas). */
  fun clearHistoryNow() {
    historyFile.delete()
  }

  /** Writes the snapshot in the background. Calls queue up and run in order. */
  fun save(snapshot: PageSnapshot) {
    io.execute { saveNow(snapshot) }
  }

  /** Writes the snapshot on the calling thread (for imports, which already run in the background). */
  fun saveNow(snapshot: PageSnapshot) {
    run {
      val started = android.os.SystemClock.uptimeMillis()
      val stream = file.startWrite()
      try {
        val saved = snapshot.toSavedPage()
        val converted = android.os.SystemClock.uptimeMillis()
        PageFile.write(saved, stream)
        file.finishWrite(stream)
        snapshot.history?.let { (undo, redo) -> saveHistory(SavedHistory(snapshot.strokes.size, undo.map { it.toSaved() }, redo.map { it.toSaved() })) }
        Log.i(PERF, "save: convert ${converted - started} ms, write ${android.os.SystemClock.uptimeMillis() - converted} ms, ${snapshot.strokes.size} strokes, ${file.baseFile.length() / 1024} KB")
      } catch (e: Exception) {
        Log.e(TAG, "Saving failed; the previous save is kept", e)
        file.failWrite(stream)
        main.post { onSaveFailed() }
      }
    }
  }

  private fun saveHistory(history: SavedHistory) {
    val stream = historyFile.startWrite()
    try {
      HistoryFile.write(history, stream)
      historyFile.finishWrite(stream)
    } catch (e: Exception) {
      Log.e(TAG, "Saving undo history failed", e)
      historyFile.failWrite(stream)
    }
  }

  private companion object {
    const val TAG = "PageStore"
    const val PERF = "PenLabPerf"
  }
}

// ---- Strokes <-> plain numbers -------------------------------------------------------------

private fun PageSnapshot.toSavedPage() =
  SavedPage(pageStyle.name, view, strokes.map { it.toSaved() }, items.map { it.toSaved() })

internal fun PageItem.toSaved(): SavedItem =
  when (this) {
    is ImageItem -> SavedItem(if (isInk) 2 else 0, id, cx, cy, width, height, rotation, fileName, 0, 0f)
    is TextItem -> SavedItem(1, id, cx, cy, width, height, rotation, text, paperColor, textSize)
    is PluginPageItem -> SavedItem(kind, id, cx, cy, width, height, rotation, content, color, textSize)
  }

internal fun SavedItem.toItem(): PageItem =
  when (kind) {
    0 -> ImageItem(id, content, cx, cy, width, height, rotation)
    2 -> ImageItem(id, content, cx, cy, width, height, rotation, isInk = true) // a typeset equation
    1 -> TextItem(id, content, color, textSize, cx, cy, width, height, rotation)
    // A plugin's item, or a kind from a newer version: kept untouched (see PluginPageItem).
    else -> PluginPageItem(id, kind, content, color, textSize, cx, cy, width, height, rotation)
  }

private fun InkStroke.toSaved() =
  SavedStroke(
    brushFamily = PenBrushes.idOf(brush.family),
    colorArgb = brush.colorIntArgb,
    size = brush.size,
    epsilon = brush.epsilon,
    isHighlighter = isHighlighter,
    toolType = toolType.value,
    strokeUnitLengthCm = unitLengthCm,
    points = points,
    times = times,
    layer = layer,
    mediaMark = mediaMark,
    group = group,
    lineType = lineType.ordinal, // LineType entries are only ever appended, so ordinals stay valid
  )

@Suppress("UNCHECKED_CAST")
private fun StrokeHistory.Edit<InkStroke>.toSaved(): SavedEdit =
  when (this) {
    is StrokeHistory.Edit.Add -> SavedEdit.Add(index, stroke.toSaved())
    is StrokeHistory.Edit.Remove -> SavedEdit.Remove(steps.map { step -> step.map { it.index to it.value.toSaved() } })
    is StrokeHistory.Edit.Replace -> SavedEdit.Replace(swaps.map { Triple(it.index, it.old.toSaved(), it.new.toSaved()) })
    is StrokeHistory.Edit.Insert -> SavedEdit.Insert(steps.map { it.index to it.value.toSaved() })
    is StrokeHistory.Edit.Group -> SavedEdit.Group(edits.map { it.toSaved() })
    is StrokeHistory.Edit.State ->
      when (kind) {
        HistoryStates.ITEMS -> SavedEdit.Items((before as List<PageItem>).map { it.toSaved() }, (after as List<PageItem>).map { it.toSaved() })
        HistoryStates.LAYERS -> SavedEdit.Layers(before as List<String>, after as List<String>)
        else -> SavedEdit.Pages(before as SavedPages, after as SavedPages)
      }
    is StrokeHistory.Edit.Action -> error("closures are never saved") // saveableSteps leaves them out
  }

private fun SavedEdit.toEdit(): StrokeHistory.Edit<InkStroke> =
  when (this) {
    is SavedEdit.Add -> StrokeHistory.Edit.Add(index, stroke.toStroke())
    is SavedEdit.Remove -> StrokeHistory.Edit.Remove(steps.map { step -> step.map { (i, s) -> IndexedValue(i, s.toStroke()) } }.toMutableList())
    is SavedEdit.Replace -> StrokeHistory.Edit.Replace(swaps.map { (i, old, new) -> StrokeHistory.Swap(i, old.toStroke(), new.toStroke()) })
    is SavedEdit.Insert -> StrokeHistory.Edit.Insert(steps.map { (i, s) -> IndexedValue(i, s.toStroke()) })
    is SavedEdit.Group -> StrokeHistory.Edit.Group(edits.map { it.toEdit() })
    is SavedEdit.Items -> StrokeHistory.Edit.State(HistoryStates.ITEMS, before.map { it.toItem() }, after.map { it.toItem() })
    is SavedEdit.Pages -> StrokeHistory.Edit.State(HistoryStates.PAGES, before, after)
    is SavedEdit.Layers -> StrokeHistory.Edit.State(HistoryStates.LAYERS, before, after)
  }

private fun SavedPage.toLoadedPage(history: SavedHistory? = null): LoadedPage {
  val style = PageStyle.entries.firstOrNull { it.name == pageStyle } ?: PageStyle.BLANK
  return LoadedPage(
    strokes = strokes.map { it.toStroke() },
    pageStyle = style,
    view = view,
    items = items.map { it.toItem() },
    history = history?.let { h -> h.undo.map { it.toEdit() } to h.redo.map { it.toEdit() } },
  )
}

/**
 * No mesh is built here: that happens only for strokes that come on screen (see [InkStroke]).
 * `InputToolType.fromInt` is the only way to restore the stored tool type; lint flags it as
 * library-group-only, but it is public in Ink 1.0.
 */
@android.annotation.SuppressLint("RestrictedApi")
private fun SavedStroke.toStroke() =
  InkStroke(
    Brushes.get(PenBrushes.familyById(brushFamily), colorArgb, size, epsilon),
    points,
    times,
    InputToolType.fromInt(toolType),
    strokeUnitLengthCm,
    isHighlighter,
    layer,
    mediaMark,
    group,
    LineType.entries.getOrElse(lineType) { LineType.SOLID },
  )
