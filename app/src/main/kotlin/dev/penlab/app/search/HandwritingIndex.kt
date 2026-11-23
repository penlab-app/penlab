package dev.penlab.app.search

import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.AtomicFile
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import dev.penlab.app.ink.InkStroke
import dev.penlab.app.ink.PageLayout
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Handwriting search for one notebook.
 *
 * Google's ML Kit reads the handwriting on the tablet itself (nothing is sent anywhere, no key,
 * no cost). The model (English, about 20 MB) is downloaded once, the first time.
 *
 * How it works:
 *  1. After each autosave, [update] gets the notebook's strokes. [LineFinder] groups them into
 *     lines of writing (that part takes milliseconds).
 *  2. Each line gets a signature made from its strokes. Lines read before are taken from the
 *     saved index (`search.txt` in the notebook's folder); only new or changed lines are read.
 *  3. Reading runs on a low-priority background thread, one line at a time, and starts over if
 *     the notebook changes meanwhile. It never runs on the drawing thread.
 *
 * [search] then matches the words against every line ML Kit's top guesses (it keeps 3 per
 * line, so a word it misread as its second guess is still found).
 */
class HandwritingIndex(private val file: File) {

  /** One line of writing: where it is and what ML Kit read there (best guess first). */
  data class Line(val signature: Long, val page: Int, val rect: RectF, val guesses: List<String>) {
    val text: String
      get() = guesses.firstOrNull().orEmpty()
  }

  /** A search result: the line, and where in its text the words matched (or null if only a lesser guess matched). */
  data class Hit(val line: Line, val match: IntRange?)

  sealed class Status {
    /** Nothing to read yet, or everything is read. */
    data object Ready : Status()

    data object Downloading : Status()

    data class Reading(val done: Int, val total: Int) : Status()

    data class Failed(val message: String) : Status()
  }

  private val main = Handler(Looper.getMainLooper())

  // The worker waits for ML Kit, so ML Kit gets its own thread (one shared thread would deadlock).
  private val worker = Executors.newSingleThreadExecutor { lowPriorityThread(it, "penlab-search") }
  private val generation = AtomicInteger()

  @Volatile private var lines: List<Line> = emptyList()

  /** Called on the main thread whenever the status changes. */
  var onStatus: (Status) -> Unit = {}
  var status: Status = Status.Ready
    private set

  init {
    worker.execute { lines = readLines(file) }
  }

  /**
   * Re-reads what changed. [strokes] must be a copy the caller will not change (strokes
   * themselves never change; edits replace them). Highlighter strokes and hidden layers should
   * already be left out.
   */
  fun update(strokes: List<InkStroke>, layout: PageLayout?, unitsPerMm: Float) {
    val run = generation.incrementAndGet()
    // Page boundaries are read now, on the main thread: the layout changes there.
    val pageOf = pageFinder(layout)
    worker.execute {
      // Search is an extra: whatever goes wrong here must never close the app (and lose ink).
      try {
        index(run, strokes, pageOf, unitsPerMm)
      } catch (e: Throwable) {
        Log.e(TAG, "Handwriting search failed", e)
        post(Status.Failed("Search hit a problem reading this notebook; it will try again after the next change."))
      }
    }
  }

  /** Lines that contain [query] (letters and digits only, any case), in page order. */
  fun search(query: String): List<Hit> = matches(lines, query)

  /**
   * Reads what changed on the calling thread (a background thread), for notebooks that are not
   * open (library search catching up). [cancel] stops it between lines.
   */
  fun indexNow(strokes: List<InkStroke>, pageOf: (Float) -> Int, unitsPerMm: Float) {
    val run = generation.incrementAndGet()
    lines = readLines(file)
    index(run, strokes, pageOf, unitsPerMm)
  }

  /** Stops a reading in progress (it carries on from the saved index next time). */
  fun cancel() {
    generation.incrementAndGet()
  }

  /** How many lines are in the index (for the panel). */
  val lineCount: Int
    get() = lines.size

  fun close() {
    generation.incrementAndGet()
    worker.execute {
      recognizer?.close()
      recognizer = null
    }
    worker.shutdown()
  }

  // ---- The background work ---------------------------------------------------------------

  private var recognizer: DigitalInkRecognizer? = null

  private fun index(run: Int, strokes: List<InkStroke>, pageOf: (Float) -> Int, unitsPerMm: Float) {
    val found = LineFinder.find(strokes, pageOf, unitsPerMm)
    val known = lines.associateBy { it.signature }
    val result = ArrayList<Line>(found.size)
    val toRead = ArrayList<LineFinder.FoundLine>()
    for (line in found) {
      val old = known[line.signature]
      if (old != null) result += old.copy(page = line.page, rect = line.rect) else toRead += line
    }
    if (toRead.isEmpty()) {
      if (result.size != lines.size || !file.exists()) {
        lines = result
        writeFile(result)
      } else {
        file.setLastModified(System.currentTimeMillis()) // "checked, up to date" (library search reads the date)
      }
      post(Status.Ready)
      return
    }
    val reader = recognizerOrNull(run) ?: return
    val started = System.nanoTime()
    var done = 0
    for (line in toRead) {
      if (generation.get() != run) {
        // Stopped (the notebook changed, or library reading was interrupted): keep what was read,
        // but date the file as old so the rest is still read later.
        if (done > 0) {
          writeFile(result)
          file.setLastModified(1000L)
        }
        return
      }
      post(Status.Reading(done, toRead.size))
      val guesses =
        try {
          val ink = line.toInk()
          val context = RecognitionContext.builder()
            .setPreContext("")
            .setWritingArea(WritingArea(line.rect.width(), line.rect.height()))
            .build()
          Tasks.await(reader.recognize(ink, context), 10, TimeUnit.SECONDS).candidates.take(MAX_GUESSES).map { it.text }
        } catch (e: Exception) {
          Log.w(TAG, "Could not read a line", e)
          emptyList()
        }
      result += Line(line.signature, line.page, line.rect, guesses)
      done++
      // Keep what is read so far, so search works while a big notebook is still being read.
      if (done % PUBLISH_EVERY == 0) lines = result.toList()
    }
    if (generation.get() != run) return
    lines = result
    writeFile(result)
    Log.i(TAG, "Read ${toRead.size} new lines (of ${found.size}) in ${(System.nanoTime() - started) / 1_000_000} ms")
    post(Status.Ready)
  }

  /** The recognizer, after downloading the model the first time. Null (with a status) if it can't be had. */
  private fun recognizerOrNull(run: Int): DigitalInkRecognizer? {
    recognizer?.let { return it }
    return try {
      val model = DigitalInkRecognitionModel.builder(DigitalInkRecognitionModelIdentifier.EN_US).build()
      val models = RemoteModelManager.getInstance()
      if (!Tasks.await(models.isModelDownloaded(model))) {
        post(Status.Downloading)
        Tasks.await(models.download(model, DownloadConditions.Builder().build()), 5, TimeUnit.MINUTES)
      }
      if (generation.get() != run) return null
      DigitalInkRecognition.getClient(
        DigitalInkRecognizerOptions.builder(model).setMaxResultCount(MAX_GUESSES).setExecutor(mlKitThread).build()
      ).also { recognizer = it }
    } catch (e: Exception) {
      Log.w(TAG, "Handwriting model not available", e)
      post(Status.Failed("The handwriting model could not be downloaded (needs the internet once). Search will try again after the next change."))
      null
    }
  }

  private fun post(new: Status) {
    main.post {
      status = new
      onStatus(new)
    }
  }

  // ---- The saved index ---------------------------------------------------------------------
  // A small text file: a header, then one line per line of writing:
  //   signature <tab> page <tab> left <tab> top <tab> right <tab> bottom <tab> guess1 <US> guess2 ...

  private fun writeFile(list: List<Line>) = writeLines(file, list)

  companion object {
    private const val TAG = "HandwritingIndex"
    private const val HEADER = "GRSI 1"
    private const val SEP = '\u001F'
    private const val MAX_GUESSES = 3
    private const val PUBLISH_EVERY = 20

    /** Lines in [list] that contain [query] (letters and digits only, any case), in page order. */
    fun matches(list: List<Line>, query: String): List<Hit> {
      val wanted = normalise(query)
      if (wanted.isEmpty()) return emptyList()
      val words = query.trim()
      return list.mapNotNull { line ->
        val direct = line.text.indexOf(words, ignoreCase = true)
        when {
          direct >= 0 -> Hit(line, direct until direct + words.length)
          line.guesses.any { normalise(it).contains(wanted) } -> Hit(line, null)
          else -> null
        }
      }.sortedWith(compareBy({ it.line.page }, { it.line.rect.top }, { it.line.rect.left }))
    }

    /** A saved index (empty if there is none, it is unreadable, or from another version). */
    fun readLines(file: File): List<Line> =
      try {
        if (!file.exists()) emptyList()
        else AtomicFile(file).openRead().bufferedReader().useLines { rows ->
          val list = ArrayList<Line>()
          var first = true
          for (row in rows) {
            if (first) {
              first = false
              if (row != HEADER) return@useLines emptyList() // another version: read everything again
              continue
            }
            val f = row.split('\t')
            if (f.size < 7) continue
            list += Line(f[0].toLong(), f[1].toInt(), RectF(f[2].toFloat(), f[3].toFloat(), f[4].toFloat(), f[5].toFloat()), f[6].split(SEP).filter { it.isNotEmpty() })
          }
          list
        }
      } catch (e: Exception) {
        Log.w(TAG, "Search index unreadable; reading the notebook again", e)
        emptyList()
      }

    private fun writeLines(file: File, list: List<Line>) {
      val atomic = AtomicFile(file)
      val out = try { atomic.startWrite() } catch (e: Exception) { return }
      try {
        val writer = out.bufferedWriter()
        writer.write(HEADER)
        writer.write("\n")
        for (line in list) {
          val r = line.rect
          val text = line.guesses.joinToString(SEP.toString()) { it.replace('\t', ' ').replace('\n', ' ').replace(SEP, ' ') }
          writer.write("${line.signature}\t${line.page}\t${r.left}\t${r.top}\t${r.right}\t${r.bottom}\t$text\n")
        }
        writer.flush()
        atomic.finishWrite(out)
      } catch (e: Exception) {
        atomic.failWrite(out)
      }
    }

    private val mlKitThread = Executors.newSingleThreadExecutor { lowPriorityThread(it, "penlab-mlkit") }

    private fun lowPriorityThread(task: Runnable, name: String) =
      Thread({
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        task.run()
      }, name)

    /** Lower case, letters and digits only: "Shear-force" finds "shear force". */
    fun normalise(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    /** Which page a height is on, fixed now (the layout itself changes on the main thread). */
    fun pageFinder(layout: PageLayout?): (Float) -> Int {
      if (layout == null) return { 0 }
      val pitch = layout.pageHeight + layout.gap
      val last = layout.count - 1
      return { y -> kotlin.math.floor(y / pitch).toInt().coerceIn(0, last) }
    }

    /** The line's strokes as ML Kit's Ink, in writing order, with times that keep going forward. */
    private fun LineFinder.FoundLine.toInk(): Ink {
      val builder = Ink.builder()
      var clock = 0L
      for (stroke in strokes) {
        val s = Ink.Stroke.builder()
        val start = clock
        for (i in 0 until stroke.pointCount) {
          val t = start + stroke.times[i]
          s.addPoint(Ink.Point.create(stroke.x(i), stroke.y(i), t))
          clock = maxOf(clock, t)
        }
        builder.addStroke(s.build())
        clock += 100 // a short pause between strokes
      }
      return builder.build()
    }
  }
}
