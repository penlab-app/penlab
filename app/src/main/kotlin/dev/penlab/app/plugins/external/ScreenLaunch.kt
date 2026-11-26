package dev.penlab.app.plugins.external

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import dev.penlab.plugin.PageRef
import dev.penlab.plugin.Selection
import dev.penlab.sdk.PageInfo
import dev.penlab.sdk.PenLabBridge
import dev.penlab.sdk.PenLabScreenContract
import dev.penlab.sdk.PluginRequest
import dev.penlab.sdk.ScreenTheme
import java.io.File
import java.io.IOException

/**
 * A screen launch ready to start: the [intent], the scratch folder to delete when the screen has answered, and [replaceable], the ids
 * of the selected items the screen was given (a result item's `replaces` is honoured only for these).
 */
class StagedScreen(val intent: Intent, val session: IoSession, val replaceable: Set<String> = emptySet())

/**
 * What PenLab prepares for a screen on top of the selection (the capability's `inputs`, docs/plugin-bridge.md "Screen inputs").
 * The page picture is drawn by the caller on the main thread (it reads the canvas); the notebook pages are drawn lazily, one bitmap at a
 * time, on the worker thread that stages the screen, so sixty pages never sit in memory together or freeze the ink.
 */
class ScreenData(
  val title: String,
  val folder: String,
  /** The current page (paper colours), or null when the screen did not ask for `page`. */
  val pageImage: Bitmap? = null,
  /** 1-based numbers of the written pages and a way to draw them in that order (null when the screen did not ask for `notebook`). */
  val notebookPages: List<Int> = emptyList(),
  val notebookDraw: (() -> Sequence<Bitmap>)? = null,
)

/** Building the Intent that opens a plugin's screen, and reading the answer. Calls that touch files belong on a worker thread. */
object ScreenLaunch {
  /** Quality of the page pictures. The paper is opaque, so JPEG is much smaller than PNG and quick to write; a plugin may pass the bytes on as they are. */
  private const val JPEG_QUALITY = 90

  /** What this PenLab can do for screens, sent as [PenLabScreenContract.EXTRA_HOST_FEATURES]. */
  val HOST_FEATURES = arrayOf(PenLabBridge.FEATURE_SCREEN_INPUTS, PenLabBridge.FEATURE_RENAME)

  /**
   * The intent for [screen]: explicit component, the page's facts as extras, and (with a selection)
   * its PNG, strokes and box as content URIs / extras, then what the screen asked for in `inputs` ([data]), and PenLab's current look ([theme]).
   * Extras are not covered by a URI grant, so the URIs are also in the intent's ClipData, which is.
   */
  fun stage(context: Context, screen: ExternalScreen, page: PageRef, darkPage: Boolean, selection: Selection, ioRoot: File, theme: ScreenTheme, data: ScreenData): StagedScreen {
    val session = IoSession.create(ioRoot)
    try {
      val wantItems = PenLabBridge.INPUT_ITEMS in screen.inputs
      val info = RequestFiles.writeSelection(session.dir, selection, withItems = wantItems)
      val intent =
        Intent().setComponent(screen.component)
          .putExtra(PenLabScreenContract.EXTRA_CAPABILITY_ID, screen.id)
          .putExtra(PenLabScreenContract.EXTRA_NOTEBOOK_ID, page.notebookId)
          .putExtra(PenLabScreenContract.EXTRA_PAGE_INDEX, page.pageIndex)
          .putExtra(PenLabScreenContract.EXTRA_PAGE_WIDTH_MM, page.widthMm)
          .putExtra(PenLabScreenContract.EXTRA_PAGE_HEIGHT_MM, page.heightMm)
          .putExtra(PenLabScreenContract.EXTRA_DARK_PAGE, darkPage)
          .putExtra(PenLabScreenContract.EXTRA_NOTEBOOK_TITLE, data.title)
          .putExtra(PenLabScreenContract.EXTRA_NOTEBOOK_FOLDER, data.folder)
          .putExtra(PenLabScreenContract.EXTRA_HOST_FEATURES, HOST_FEATURES)
      PenLabScreenContract.putTheme(intent, theme)
      info?.let { intent.putExtra(PenLabScreenContract.EXTRA_SELECTION_BOUNDS_MM, it.boundsMm) }
      var clip: ClipData? = null
      fun uriOf(name: String): Uri {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(session.dir, name))
        val existing = clip
        if (existing == null) clip = ClipData.newRawUri("penlab-selection", uri) else existing.addItem(ClipData.Item(uri))
        return uri
      }
      fun share(extra: String, name: String?) {
        if (name != null) intent.putExtra(extra, uriOf(name))
      }
      share(PenLabScreenContract.EXTRA_SELECTION_IMAGE, info?.imageFile)
      share(PenLabScreenContract.EXTRA_SELECTION_STROKES, info?.strokesFile)

      var replaceable = emptySet<String>()
      if (wantItems && info != null) {
        intent.putExtra(PenLabScreenContract.EXTRA_SELECTION_ITEMS, PluginRequest.selectionItemsJson(info.items))
        val files = Bundle()
        for (item in info.items) item.file?.let { files.putParcelable(it, uriOf(it)) }
        intent.putExtra(PenLabScreenContract.EXTRA_SELECTION_ITEM_FILES, files)
        replaceable = info.items.map { it.id }.toSet()
      }

      val written = writeInputs(session.dir, data)
      share(PenLabScreenContract.EXTRA_PAGE_IMAGE, written.pageFile)
      if (written.notebookFiles.isNotEmpty()) {
        intent.putParcelableArrayListExtra(PenLabScreenContract.EXTRA_NOTEBOOK_IMAGES, ArrayList(written.notebookFiles.map(::uriOf)))
        intent.putExtra(PenLabScreenContract.EXTRA_NOTEBOOK_PAGES, written.notebookPages.toIntArray())
      }
      clip?.let {
        intent.clipData = it
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      return StagedScreen(intent, session, replaceable)
    } catch (e: Throwable) {
      session.delete()
      throw e
    }
  }

  /** Files written for a screen's `page` and `notebook` inputs (names inside the session folder). */
  class WrittenInputs(val pageFile: String?, val notebookFiles: List<String>, val notebookPages: List<Int>)

  /** Writes the page picture and the notebook pages as JPEGs into [dir]; each page bitmap is recycled as soon as it is written. */
  fun writeInputs(dir: File, data: ScreenData): WrittenInputs {
    var pageFile: String? = null
    data.pageImage?.let { bitmap ->
      File(dir, "page.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
      pageFile = "page.jpg"
    }
    val files = ArrayList<String>()
    val numbers = ArrayList<Int>()
    data.notebookDraw?.let { draw ->
      for ((i, bitmap) in draw().withIndex()) {
        val name = "notebook-$i.jpg"
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
        files += name
        numbers += data.notebookPages.getOrElse(i) { i + 1 }
      }
    }
    return WrittenInputs(pageFile, files, numbers)
  }

  /**
   * The intent for a media plugin's `startActivity` ("Record lecture"): an explicit component and the same extras a screen gets
   * (capability id, notebook, page, theme; no selection). It is started FOR A RESULT, so the plugin can check [PenLabScreenContract.isCalledByPenLab].
   */
  fun mediaStart(component: ComponentName, capabilityId: String, page: PageInfo, theme: ScreenTheme): Intent {
    val intent =
      Intent().setComponent(component)
        .putExtra(PenLabScreenContract.EXTRA_CAPABILITY_ID, capabilityId)
        .putExtra(PenLabScreenContract.EXTRA_NOTEBOOK_ID, page.notebookId)
        .putExtra(PenLabScreenContract.EXTRA_PAGE_INDEX, page.pageIndex)
        .putExtra(PenLabScreenContract.EXTRA_PAGE_WIDTH_MM, page.widthMm)
        .putExtra(PenLabScreenContract.EXTRA_PAGE_HEIGHT_MM, page.heightMm)
        .putExtra(PenLabScreenContract.EXTRA_DARK_PAGE, page.darkPage)
    return PenLabScreenContract.putTheme(intent, theme)
  }

  /**
   * Reads a screen's answer: the result JSON and its files. Only URIs of a provider owned by
   * [pluginPackage] are followed (a plugin must not make PenLab read someone else's content). Null if the
   * answer has no JSON.
   */
  @Suppress("DEPRECATION")
  fun read(context: Context, data: Intent, pluginPackage: String): Pair<String, ResultFiles>? {
    val json = data.getStringExtra(PenLabScreenContract.EXTRA_RESULT_JSON) ?: return null
    val bundle = data.getBundleExtra(PenLabScreenContract.EXTRA_RESULT_FILES) ?: Bundle()
    val uris = HashMap<String, Uri>()
    for (name in bundle.keySet()) {
      val uri = bundle.get(name) as? Uri ?: continue
      if (ownedBy(context, uri, pluginPackage)) uris[name] = uri
    }
    return json to UriResultFiles(context, uris)
  }

  private fun ownedBy(context: Context, uri: Uri, pluginPackage: String): Boolean {
    if (uri.scheme != "content") return false
    val authority = uri.authority ?: return false
    return context.packageManager.resolveContentProvider(authority, 0)?.packageName == pluginPackage
  }
}

/** [ResultFiles] over content URIs a screen returned. */
private class UriResultFiles(private val context: Context, private val uris: Map<String, Uri>) : ResultFiles {
  override fun readBytes(name: String, maxBytes: Long): ByteArray? {
    val uri = uris[name] ?: return null
    return (context.contentResolver.openInputStream(uri) ?: throw IOException("cannot open $name")).use { dev.penlab.sdk.PluginFiles.readLimited(it, maxBytes) }
  }

  override fun copyTo(name: String, target: File): Boolean {
    val uri = uris[name] ?: return false
    (context.contentResolver.openInputStream(uri) ?: throw IOException("cannot open $name")).use { i -> target.outputStream().use { i.copyTo(it) } }
    return true
  }

  override fun close() {}
}
