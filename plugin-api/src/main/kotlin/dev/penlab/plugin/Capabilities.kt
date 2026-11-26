package dev.penlab.plugin

import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** When a [PageAction] is offered. */
enum class EnabledWhen {
  /** Only with nothing selected: the notebook's "more" menu. */
  SELECTION_EMPTY,

  /** Only with something selected: the lasso's menu. */
  SELECTION_NOT_EMPTY,

  /** Both menus. */
  ALWAYS,
}

/**
 * A button in the notebook's "more" menu (nothing selected) and/or the lasso menu (something
 * selected), see [enabledWhen].
 */
interface PageAction : Capability {
  /** Unique within the plugin. */
  val id: String

  /** Button text. */
  val label: String

  /** A drawable resource of the plugin's own, or 0 for the host's default icon. */
  val iconRes: Int get() = 0

  val enabledWhen: EnabledWhen get() = EnabledWhen.ALWAYS

  /**
   * Runs when the user taps the button. Called from a background dispatcher, never during
   * drawing, and may take a long time: PenLab shows a busy message until it returns. [selection]
   * is empty for actions run from the "more" menu.
   */
  suspend fun run(selection: Selection, page: PageRef): Result
}

/**
 * A new kind of page object. Until the host wiring lands (next step) PenLab draws every plugin
 * item as a grey box carrying [typeId]; the hooks below will then run on the main thread during
 * drawing, so they must be fast and allocation-free.
 */
interface PageItemType : Capability {
  /** The [PluginItem.typeId] this type owns. */
  val typeId: String

  /** Draws [item] filling [PluginItem.boundsMm] of [canvas] (the canvas is already moved and scaled to the item's box, in millimetres). */
  fun draw(canvas: Canvas, item: PluginItem)

  /** True if a touch at ([xMm], [yMm]) hits [item]. */
  fun hitTest(item: PluginItem, xMm: Float, yMm: Float): Boolean = item.boundsMm.contains(xMm, yMm)
}

/** What a [Recogniser] works from. */
enum class RecogniserInput { STROKES, BITMAP }

/** What a [Recogniser] found. */
sealed interface Recognition {
  class LaTeX(val source: String) : Recognition
  class PlainText(val text: String) : Recognition
  class Svg(val markup: String) : Recognition
  data object Nothing : Recognition
}

/** Turns handwriting (strokes or a picture of them) into LaTeX, text or SVG. PenLab shows the result and places it. (Host wiring comes in a later step.) */
interface Recogniser : Capability {
  val id: String
  val label: String

  /** What it can read; PenLab passes only these. */
  val inputs: Set<RecogniserInput>

  /** Called from a background dispatcher; may take a long time. An argument is null if it is not in [inputs]. */
  suspend fun recognise(strokes: List<PluginStroke>?, bitmap: Bitmap?): Recognition
}

/** A file offered to an [Importer]. */
class ImportSource(
  /** The file's name, as the user sees it. */
  val displayName: String,
  val mimeType: String,
  /** Opens the file's bytes (the caller closes the stream). May be called more than once. */
  val open: () -> InputStream,
  /** A private folder for temporary files; PenLab deletes what is left in it afterwards. */
  val workDir: File,
  /** The notebook title PenLab would use (the file name without its extension). */
  val suggestedTitle: String,
)

/** One page of an imported notebook. */
class ImportedPage(
  /** Page of [ImportedNotebook.backgroundPdf] shown under this page, or -1 for plain paper. */
  val backgroundPdfPage: Int = -1,
  /** Objects for this page; positions in millimetres from the top-left of THIS page. */
  val items: List<PluginItem> = emptyList(),
  /** Real ink for this page; positions in millimetres from the top-left of THIS page. */
  val strokes: List<PluginStroke> = emptyList(),
)

/**
 * A notebook made by an [Importer]. [backgroundPdf], if given, must be a file inside
 * [ImportSource.workDir]: PenLab moves it into the new notebook.
 */
class ImportedNotebook(
  val title: String,
  val pageWidthMm: Float,
  val pageHeightMm: Float,
  val pages: List<ImportedPage>,
  val backgroundPdf: File? = null,
)

/** Reads a file type into a new notebook. */
interface Importer : Capability {
  /** Shown in pickers. */
  val label: String

  /** MIME types it reads; a wildcard subtype ("image" with a star) is allowed. */
  val mimeTypes: List<String>

  /**
   * Called from a background dispatcher. Returns null if the file cannot be read (PenLab counts
   * it as failed). May take a long time and may use [ImportSource.workDir].
   */
  suspend fun import(source: ImportSource): ImportedNotebook?
}

/** What an [Exporter] is given: a notebook as the user sees it. Converting is done only when asked for. */
interface ExportSource {
  val notebookId: String
  val title: String
  val pageWidthMm: Float
  val pageHeightMm: Float
  val pageCount: Int

  /** True when the user chose the dark look for this export. */
  val dark: Boolean

  /** Every visible stroke, in notebook millimetres (see [PluginStroke]). Converted on each call: call once. */
  fun strokes(): List<PluginStroke>

  /** Every page object, in notebook millimetres. */
  fun items(): List<PluginItem>
}

/** Writes a notebook as a file type. */
interface Exporter : Capability {
  val label: String

  /** The MIME type of what it writes. */
  val mimeType: String

  /** File name extension without the dot, e.g. `pdf`. */
  val fileExtension: String

  /**
   * Called from a background dispatcher; may take a long time. Writes the whole file to [out]
   * (the caller closes the stream). Throwing means the export failed.
   */
  suspend fun export(source: ExportSource, out: OutputStream)
}

/** Kind of one [SettingField]. */
enum class FieldKind { TEXT, SECRET, TOGGLE }

/** One setting. [FieldKind.SECRET] values (API keys) are stored encrypted by PenLab. */
class SettingField(val key: String, val label: String, val kind: FieldKind, val default: String = "")

/**
 * One screen of settings for the plugin, drawn by PenLab from [fields]; PenLab stores the values
 * and keeps secrets encrypted. (Host wiring comes in a later step.)
 */
interface SettingsEntry : Capability {
  val title: String
  val fields: List<SettingField>

  /** Android permissions the plugin needs (e.g. `android.permission.INTERNET`); PenLab lists them in Settings. */
  val permissions: List<String> get() = emptyList()
}

/**
 * IN-PROCESS ONLY (built-in plugins; never offered to external plugins): a side sheet with
 * Compose content. (Host wiring comes in a later step.)
 */
interface Panel : Capability {
  val id: String
  val title: String

  /** Draws the panel's content. Runs on the main thread like any composable. */
  @androidx.compose.runtime.Composable
  fun Content()
}

/**
 * IN-PROCESS ONLY: told about strokes after the pen lifts. (Host wiring comes in a later step.)
 * Never called while the pen is down; called off the main thread with a time budget, and a
 * listener that overruns it is switched off.
 */
interface StrokeListener : Capability {
  /** [strokes] were just committed to the page at wall-clock time [timeMs]. */
  fun onStrokesCommitted(strokes: List<PluginStroke>, timeMs: Long)
}
