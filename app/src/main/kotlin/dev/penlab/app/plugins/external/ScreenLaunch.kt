package dev.penlab.app.plugins.external

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import dev.penlab.plugin.PageRef
import dev.penlab.plugin.Selection
import dev.penlab.sdk.PenLabBridge
import dev.penlab.sdk.PenLabScreenContract
import dev.penlab.sdk.ScreenTheme
import java.io.File
import java.io.IOException

/** A screen launch ready to start: the [intent] and the scratch folder to delete when the screen has answered. */
class StagedScreen(val intent: Intent, val session: IoSession)

/** Building the Intent that opens a plugin's screen, and reading the answer. Calls that touch files belong on a worker thread. */
object ScreenLaunch {
  /**
   * The intent for [screen]: explicit component, the page's facts as extras, and (with a selection)
   * its PNG and strokes as content URIs of PenLab's FileProvider, and PenLab's current look ([theme]). Extras are not covered by a URI grant,
   * so the URIs are also in the intent's ClipData, which is.
   */
  fun stage(context: Context, screen: ExternalScreen, page: PageRef, darkPage: Boolean, selection: Selection, ioRoot: File, theme: ScreenTheme): StagedScreen {
    val session = IoSession.create(ioRoot)
    try {
      val info = RequestFiles.writeSelection(session.dir, selection, withItems = false)
      val intent =
        Intent().setComponent(screen.component)
          .putExtra(PenLabScreenContract.EXTRA_CAPABILITY_ID, screen.id)
          .putExtra(PenLabScreenContract.EXTRA_NOTEBOOK_ID, page.notebookId)
          .putExtra(PenLabScreenContract.EXTRA_PAGE_INDEX, page.pageIndex)
          .putExtra(PenLabScreenContract.EXTRA_PAGE_WIDTH_MM, page.widthMm)
          .putExtra(PenLabScreenContract.EXTRA_PAGE_HEIGHT_MM, page.heightMm)
          .putExtra(PenLabScreenContract.EXTRA_DARK_PAGE, darkPage)
      PenLabScreenContract.putTheme(intent, theme)
      var clip: ClipData? = null
      fun share(extra: String, name: String?) {
        if (name == null) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(session.dir, name))
        intent.putExtra(extra, uri)
        val existing = clip
        if (existing == null) clip = ClipData.newRawUri("penlab-selection", uri) else existing.addItem(ClipData.Item(uri))
      }
      share(PenLabScreenContract.EXTRA_SELECTION_IMAGE, info?.imageFile)
      share(PenLabScreenContract.EXTRA_SELECTION_STROKES, info?.strokesFile)
      clip?.let {
        intent.clipData = it
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      return StagedScreen(intent, session)
    } catch (e: Throwable) {
      session.delete()
      throw e
    }
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
