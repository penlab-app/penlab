package dev.penlab.app.ui

import dev.penlab.app.ink.PageStyle
import dev.penlab.app.notes.NotebookInfo
import dev.penlab.app.notes.NotebookKind
import java.time.Instant
import java.time.ZoneId

/*
 * The library's decisions, kept free of Compose and Android so plain JVM tests can check them:
 * breadcrumbs, the Import menu, the Continue strip, relative dates, the first-launch gate and the
 * per-folder paper memory. The composables only draw what these return (icons and colours are
 * chosen there, never here).
 */

// ---- Breadcrumbs ---------------------------------------------------------------------------

/** One step of the folder trail. [path] is what to open when tapped ("" = the top level). */
data class Crumb(val label: String, val path: String)

/** The trail for [current]: "Notebooks", then each folder down to it ("A/B" gives 3 crumbs). */
fun breadcrumbs(current: String, rootLabel: String = "Notebooks"): List<Crumb> {
  val parts = current.split("/").filter { it.isNotEmpty() }
  return buildList {
    add(Crumb(rootLabel, ""))
    parts.forEachIndexed { i, part -> add(Crumb(part, parts.subList(0, i + 1).joinToString("/"))) }
  }
}

// ---- Import menu ---------------------------------------------------------------------------

enum class ImportOption { PDF_FILES, FOLDER }

data class ImportMenuEntry(val option: ImportOption, val label: String, val subtitle: String)

/** Where an import will land, in words: "Notebooks" at the top level, else the open folder's name. */
fun importDestination(current: String): String =
  if (current.isEmpty()) "Notebooks" else current.substringAfterLast("/")

/** The single Import menu that replaces the two wide header buttons. Order is the order shown. */
fun importMenuEntries(current: String): List<ImportMenuEntry> {
  val where = importDestination(current)
  return listOf(
    ImportMenuEntry(ImportOption.PDF_FILES, "Import PDF files", "Each file becomes a notebook in $where"),
    ImportMenuEntry(ImportOption.FOLDER, "Import folder", "Every PDF inside, subfolders included, into $where"),
  )
}

// ---- Continue strip and dates --------------------------------------------------------------

/** How many notebooks the Continue strip shows at most. */
const val CONTINUE_LIMIT = 4

/**
 * The notebooks touched last, newest first, for the top level only. With fewer than two notebooks
 * the strip would only repeat the shelf below it, so it is left out (empty list).
 */
fun continueNotebooks(notebooks: List<NotebookInfo>, current: String, limit: Int = CONTINUE_LIMIT): List<NotebookInfo> {
  if (current.isNotEmpty() || notebooks.size < 2) return emptyList()
  return notebooks.sortedByDescending { it.modified }.take(limit)
}

/**
 * "Today", "Yesterday" or "3 days ago" for the last week; null beyond that, so the caller prints
 * the full date. Whole calendar days in [zone], not 24-hour blocks, so 23:50 to 00:10 is "Yesterday".
 */
fun relativeDayLabel(nowMs: Long, thenMs: Long, zone: ZoneId = ZoneId.systemDefault()): String? {
  val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
  val day = Instant.ofEpochMilli(thenMs).atZone(zone).toLocalDate()
  val ago = java.time.temporal.ChronoUnit.DAYS.between(day, today)
  return when {
    ago < 0 -> null // a clock in the future: show the real date
    ago == 0L -> "Today"
    ago == 1L -> "Yesterday"
    ago < 7 -> "$ago days ago"
    else -> null
  }
}

/** "4 pages", "1 page". */
fun pagesLabel(count: Int): String = "$count ${if (count == 1) "page" else "pages"}"

/** "1 notebook", "3 notebooks". */
fun notebooksLabel(count: Int): String = if (count == 1) "1 notebook" else "$count notebooks"

// ---- First launch --------------------------------------------------------------------------

/** Where the "seen the welcome" flag lives (the same `look` file as the theme choices). */
const val LOOK_PREFS = "look"
const val FIRST_LAUNCH_DONE_KEY = "first_launch_done"

enum class FirstLaunchAction {
  /** Nothing to show (yet or ever). */
  HIDE,

  /** A genuine first run, or the user asked for it in Settings: show the sheet. */
  SHOW,

  /** Returning user whose flag was never set (they already have notebooks): record it, show nothing. */
  MARK_DONE,
}

/**
 * Whether the Welcome sheet appears. The library list is empty on the first frame and fills a
 * moment later, so "no notebooks" only counts once [libraryLoaded]; before that nothing shows
 * (otherwise every returning user would see a flash of the welcome). A notebook already open
 * (e.g. an incoming PDF) also keeps it away. [requested] (Settings, "Welcome guide") bypasses
 * every other rule.
 */
fun firstLaunchAction(
  done: Boolean,
  libraryLoaded: Boolean,
  notebookCount: Int,
  notebookOpen: Boolean,
  requested: Boolean,
): FirstLaunchAction =
  when {
    requested -> FirstLaunchAction.SHOW
    done -> FirstLaunchAction.HIDE
    !libraryLoaded -> FirstLaunchAction.HIDE
    notebookCount > 0 -> FirstLaunchAction.MARK_DONE
    notebookOpen -> FirstLaunchAction.HIDE
    else -> FirstLaunchAction.SHOW
  }

/**
 * "Start writing" makes a first notebook only for someone who has none; reopened from Settings by
 * a user with notebooks it just closes the guide (no stray "Untitled" notebooks).
 */
fun startWritingCreatesNotebook(notebookCount: Int): Boolean = notebookCount == 0

// ---- New notebook: format and per-folder paper memory --------------------------------------

/** The three formats of the segmented control. */
enum class NotebookFormat(val label: String) {
  A4_PORTRAIT("A4 Portrait"),
  A4_LANDSCAPE("A4 Landscape"),
  ENDLESS("Endless");

  val kind: NotebookKind get() = if (this == ENDLESS) NotebookKind.INFINITE else NotebookKind.A4
  val landscape: Boolean get() = this == A4_LANDSCAPE

  companion object {
    fun of(kind: NotebookKind, landscape: Boolean): NotebookFormat =
      when {
        kind == NotebookKind.INFINITE -> ENDLESS
        landscape -> A4_LANDSCAPE
        else -> A4_PORTRAIT
      }
  }
}

/** What a folder remembers for the next notebook made in it. */
data class PaperDefaults(val style: PageStyle, val kind: NotebookKind, val landscape: Boolean)

/** The built-in default when nothing is remembered anywhere up the path: dotted A4 portrait. */
val FallbackPaperDefaults = PaperDefaults(PageStyle.DOTTED, NotebookKind.A4, false)

/** Key in the `folder_paper` preferences: the folder path, or "/" for the top level. */
fun paperPrefKey(folder: String): String = folder.ifEmpty { "/" }

/** `STYLE:KIND:portrait|landscape`, the format already stored by earlier versions. */
fun encodePaperDefaults(d: PaperDefaults): String =
  "${d.style.name}:${d.kind.name}:${if (d.landscape) "landscape" else "portrait"}"

/** Reads a stored value; a missing or unknown field falls back to dotted / A4 / portrait. */
fun decodePaperDefaults(stored: String): PaperDefaults {
  val f = stored.split(":")
  return PaperDefaults(
    style = PageStyle.entries.firstOrNull { it.name == f.getOrNull(0) } ?: PageStyle.DOTTED,
    kind = NotebookKind.entries.firstOrNull { it.name == f.getOrNull(1) } ?: NotebookKind.A4,
    landscape = f.getOrNull(2) == "landscape",
  )
}

/**
 * The defaults for [folder]: its own, else its nearest parent's, up to the top level, else
 * [FallbackPaperDefaults]. [lookup] reads one stored value by [paperPrefKey].
 */
fun paperDefaultsFor(folder: String, lookup: (String) -> String?): PaperDefaults {
  val stored =
    generateSequence(folder) { f -> if (f.isEmpty()) null else f.substringBeforeLast("/", "") }
      .firstNotNullOfOrNull { lookup(paperPrefKey(it)) }
  return stored?.let(::decodePaperDefaults) ?: FallbackPaperDefaults
}
