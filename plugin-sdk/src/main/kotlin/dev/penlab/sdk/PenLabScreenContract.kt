package dev.penlab.sdk

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.io.File
import java.io.InputStream
import java.util.UUID

/** What PenLab passes to a screen activity (see [PenLabScreenContract.readRequest]). */
class ScreenRequest(
  val capabilityId: String,
  val notebookId: String,
  val pageIndex: Int,
  val pageWidthMm: Float,
  val pageHeightMm: Float,
  val darkPage: Boolean,
  /** PNG of the selection (dark ink on transparent), or null when nothing is selected. Readable with [openSelectionImage]. */
  val selectionImage: Uri?,
  /** The selected strokes as [StrokesJson], or null. Readable with [selectionStrokes]. */
  val selectionStrokes: Uri?,
) {
  /** Opens the selection PNG (the caller closes it), or returns null. */
  fun openSelectionImage(context: Context): InputStream? = selectionImage?.let { context.contentResolver.openInputStream(it) }

  /** The selected strokes; empty when nothing is selected. */
  fun selectionStrokes(context: Context): List<SdkStroke> =
    selectionStrokes?.let { uri -> context.contentResolver.openInputStream(uri)?.use { StrokesJson.decode(String(it.readBytes(), Charsets.UTF_8)) } } ?: emptyList()
}

/**
 * PenLab's current look, so a plugin screen can match it (see [PenLabScreenContract.theme]).
 * Plain values, no Android types, so any UI toolkit can use them.
 */
data class ScreenTheme(
  /** PenLab's interface is in its dark theme. */
  val darkUi: Boolean,
  /** The accent colour of that theme, as an ARGB int (alpha 0xFF). */
  val accentArgb: Int,
  /** The paper of the open notebook is dark. (Not the same as [darkUi]: they are separate settings.) */
  val darkPaper: Boolean,
) {
  companion object {
    /** What [PenLabScreenContract.theme] returns when an older PenLab sent nothing: light, a neutral blue. */
    val DEFAULT = ScreenTheme(darkUi = false, accentArgb = 0xFF2F6FD6.toInt(), darkPaper = false)
  }
}

/**
 * Helpers for a plugin's `screen` activity: PenLab starts the activity (declared exported, named in the
 * manifest) with [Activity.startActivityForResult]; the activity reads what it was given with [readRequest]
 * and answers with `setResult(RESULT_OK, PenLabScreenContract.resultIntent(this, result))` before it finishes.
 * Cancelling (back, RESULT_CANCELED) places nothing.
 *
 * An exported activity can be started by any app, so check [isCalledByPenLab] and finish if it is false.
 */
object PenLabScreenContract {
  const val EXTRA_CAPABILITY_ID = "dev.penlab.extra.CAPABILITY_ID"
  const val EXTRA_NOTEBOOK_ID = "dev.penlab.extra.NOTEBOOK_ID"
  const val EXTRA_PAGE_INDEX = "dev.penlab.extra.PAGE_INDEX"
  const val EXTRA_PAGE_WIDTH_MM = "dev.penlab.extra.PAGE_WIDTH_MM"
  const val EXTRA_PAGE_HEIGHT_MM = "dev.penlab.extra.PAGE_HEIGHT_MM"
  const val EXTRA_DARK_PAGE = "dev.penlab.extra.DARK_PAGE"

  /** Boolean: PenLab's interface is dark. */
  const val EXTRA_THEME_DARK_UI = "dev.penlab.extra.THEME_DARK_UI"

  /** Int: PenLab's accent colour, ARGB. */
  const val EXTRA_THEME_ACCENT = "dev.penlab.extra.THEME_ACCENT"

  /** Boolean: the open notebook's paper is dark. */
  const val EXTRA_THEME_DARK_PAPER = "dev.penlab.extra.THEME_DARK_PAPER"

  /** Uri of the selection PNG; granted for reading (also through the intent's ClipData). */
  const val EXTRA_SELECTION_IMAGE = "dev.penlab.extra.SELECTION_IMAGE"

  /** Uri of the selected strokes ([StrokesJson]); granted for reading. */
  const val EXTRA_SELECTION_STROKES = "dev.penlab.extra.SELECTION_STROKES"

  /** Result extra: the [ResultCodec] JSON. */
  const val EXTRA_RESULT_JSON = "dev.penlab.extra.RESULT_JSON"

  /** Result extra: a Bundle of result file name to content Uri (granted for reading). */
  const val EXTRA_RESULT_FILES = "dev.penlab.extra.RESULT_FILES"

  /** Result files older than this are removed when the next result is made. */
  private const val KEEP_MS = 60L * 60 * 1000

  /** True if [activity] was started for a result by PenLab. */
  fun isCalledByPenLab(activity: Activity): Boolean = activity.callingActivity?.packageName == PenLabBridge.HOST_PACKAGE

  /** PenLab's look at launch time: UI dark or light, accent colour, dark paper. Missing extras give [ScreenTheme.DEFAULT]'s values. */
  fun theme(intent: Intent): ScreenTheme =
    ScreenTheme(
      darkUi = intent.getBooleanExtra(EXTRA_THEME_DARK_UI, ScreenTheme.DEFAULT.darkUi),
      accentArgb = intent.getIntExtra(EXTRA_THEME_ACCENT, ScreenTheme.DEFAULT.accentArgb),
      darkPaper = intent.getBooleanExtra(EXTRA_THEME_DARK_PAPER, ScreenTheme.DEFAULT.darkPaper),
    )

  /** The three theme extras for [theme], to put on a screen's launch intent. */
  fun putTheme(intent: Intent, theme: ScreenTheme): Intent =
    intent.putExtra(EXTRA_THEME_DARK_UI, theme.darkUi).putExtra(EXTRA_THEME_ACCENT, theme.accentArgb).putExtra(EXTRA_THEME_DARK_PAPER, theme.darkPaper)

  /** What PenLab passed in [intent]; throws [IllegalArgumentException] if it is not a PenLab screen launch. */
  @Suppress("DEPRECATION")
  fun readRequest(intent: Intent): ScreenRequest =
    ScreenRequest(
      capabilityId = requireNotNull(intent.getStringExtra(EXTRA_CAPABILITY_ID)) { "not started by PenLab" },
      notebookId = intent.getStringExtra(EXTRA_NOTEBOOK_ID).orEmpty(),
      pageIndex = intent.getIntExtra(EXTRA_PAGE_INDEX, 0),
      pageWidthMm = intent.getFloatExtra(EXTRA_PAGE_WIDTH_MM, 0f),
      pageHeightMm = intent.getFloatExtra(EXTRA_PAGE_HEIGHT_MM, 0f),
      darkPage = intent.getBooleanExtra(EXTRA_DARK_PAGE, false),
      selectionImage = intent.getParcelableExtra(EXTRA_SELECTION_IMAGE) as? Uri,
      selectionStrokes = intent.getParcelableExtra(EXTRA_SELECTION_STROKES) as? Uri,
    )

  /**
   * The Intent to pass to `setResult(RESULT_OK, ...)`: the result as JSON, and its files as content
   * Uris PenLab may read (one-time grant, served by [PenLabResultProvider]).
   */
  fun resultIntent(context: Context, result: PluginResult): Intent {
    val encoded = ResultCodec.encode(result)
    val dir = PenLabResultProvider.resultsDir(context).apply { mkdirs() }
    val cutoff = System.currentTimeMillis() - KEEP_MS
    dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    val uris = Bundle()
    var clip: ClipData? = null
    for ((name, file) in encoded.files) {
      val target = File(dir, "${UUID.randomUUID()}-${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
      when (file) {
        is ResultFile.Bytes -> target.writeBytes(file.bytes)
        is ResultFile.OnDisk -> file.file.copyTo(target, overwrite = true)
      }
      val uri = Uri.Builder().scheme("content").authority(PenLabResultProvider.authority(context)).appendPath(target.name).build()
      uris.putParcelable(name, uri)
      if (clip == null) clip = ClipData.newRawUri("penlab-result", uri) else clip.addItem(ClipData.Item(uri))
    }
    return Intent().apply {
      putExtra(EXTRA_RESULT_JSON, encoded.json)
      putExtra(EXTRA_RESULT_FILES, uris)
      clip?.let {
        clipData = it
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    }
  }
}
