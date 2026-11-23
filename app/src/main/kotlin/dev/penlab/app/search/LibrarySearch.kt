package dev.penlab.app.search

import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import dev.penlab.app.ink.PageLayout
import dev.penlab.app.notes.NotebookInfo
import dev.penlab.app.notes.NotebookKind
import dev.penlab.app.notes.NotebookStore
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Search across every notebook, from the library.
 *
 * Each notebook keeps what was read of its handwriting in `search.txt` (see [HandwritingIndex]).
 * Searching reads those files (kept in memory until they change), so it is quick and needs no
 * notebook to be open.
 *
 * Catching up: notebooks written in before search existed, imported PDFs and any
 * saved after their last read are read here, one at a time, while the library is on screen.
 * Opening a notebook stops it ([stopCatchingUp]); the open notebook reads itself.
 */
class LibrarySearch(private val store: NotebookStore, private val endlessUnitsPerMm: Float) {

  /** A matching line, and the notebook it is in. */
  data class Result(val notebook: NotebookInfo, val hit: HandwritingIndex.Hit)

  private val main = Handler(Looper.getMainLooper())
  private val searcher = Executors.newSingleThreadExecutor { lowPriority(it, "penlab-library-search") }
  private val reader = Executors.newSingleThreadExecutor { lowPriority(it, "penlab-library-read") }
  private val searches = AtomicInteger()
  private val catchUps = AtomicInteger()
  @Volatile private var reading: HandwritingIndex? = null

  /** Parsed index files by notebook id, with the file date they were read at. */
  private val cache = HashMap<String, Pair<Long, List<HandwritingIndex.Line>>>()

  /**
   * Searches [notebooks] for [query] in the background; [onResults] gets the matches on the
   * main thread, grouped by notebook (newest notebook first). A newer search replaces an older one.
   */
  fun search(query: String, notebooks: List<NotebookInfo>, onResults: (List<Result>) -> Unit) {
    val id = searches.incrementAndGet()
    if (query.isBlank()) {
      onResults(emptyList())
      return
    }
    searcher.execute {
      val results = ArrayList<Result>()
      for (notebook in notebooks.sortedByDescending { it.modified }) {
        if (searches.get() != id) return@execute // typed on: this search is out of date
        for (hit in HandwritingIndex.matches(linesOf(notebook), query)) results += Result(notebook, hit)
      }
      main.post { if (searches.get() == id) onResults(results) }
    }
  }

  private fun linesOf(notebook: NotebookInfo): List<HandwritingIndex.Line> {
    val file = store.searchFile(notebook)
    val date = file.lastModified()
    synchronized(cache) { cache[notebook.id]?.let { (at, lines) -> if (at == date) return lines } }
    val lines = HandwritingIndex.readLines(file)
    synchronized(cache) { cache[notebook.id] = date to lines }
    return lines
  }

  /**
   * Reads the handwriting of every notebook in [notebooks] whose index is missing or older than
   * its last save. [onProgress] (main thread) gets (notebooks done, notebooks to read), and
   * (0, 0) when there is nothing (left) to read.
   */
  fun catchUp(notebooks: List<NotebookInfo>, onProgress: (done: Int, total: Int) -> Unit) {
    val run = catchUps.incrementAndGet()
    reader.execute {
      val stale = notebooks.filter { notebook ->
        val saved = store.pageStore(notebook).savedAt
        saved > 0 && store.searchFile(notebook).lastModified() < saved
      }
      stale.forEachIndexed { done, notebook ->
        if (catchUps.get() != run) return@execute
        main.post { if (catchUps.get() == run) onProgress(done, stale.size) }
        try {
          read(notebook)
        } catch (e: Throwable) {
          Log.e(TAG, "Could not read ${notebook.title}", e) // the next notebook still gets read
        }
      }
      main.post { if (catchUps.get() == run) onProgress(0, 0) }
    }
  }

  private fun read(notebook: NotebookInfo) {
    val strokes = store.pageStore(notebook).readStrokesNow() ?: return
    val hidden = notebook.hiddenLayers.toSet()
    val searchable = strokes.filter { !it.isHighlighter && it.layer !in hidden }
    val paged = notebook.kind == NotebookKind.A4
    val layout = if (paged) PageLayout.paged(notebook.pageWidthMm, notebook.pageHeightMm, notebook.pageCount, notebook.bookmarks, notebook.pdfPages) else null
    val index = HandwritingIndex(store.searchFile(notebook))
    reading = index
    try {
      index.indexNow(searchable, HandwritingIndex.pageFinder(layout), if (paged) PageLayout.UNITS_PER_MM else endlessUnitsPerMm)
    } finally {
      reading = null
      index.close()
    }
  }

  /** Stops catching up (a notebook is being opened); what was read so far is kept. */
  fun stopCatchingUp() {
    catchUps.incrementAndGet()
    reading?.cancel()
  }

  private companion object {
    const val TAG = "LibrarySearch"

    fun lowPriority(task: Runnable, name: String) =
      Thread({
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        task.run()
      }, name)
  }
}
