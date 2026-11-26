package dev.penlab.app.plugins.external

import android.content.ComponentName
import android.graphics.Bitmap
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import dev.penlab.app.plugins.ExtensionImporter
import dev.penlab.app.plugins.PluginConvert
import dev.penlab.app.plugins.ScreenPageAction
import dev.penlab.app.plugins.ScreenPlacement
import dev.penlab.plugin.API_VERSION
import dev.penlab.plugin.Capability
import dev.penlab.plugin.EnabledWhen
import dev.penlab.plugin.ImportSource
import dev.penlab.plugin.ImportedNotebook
import dev.penlab.plugin.PageAction
import dev.penlab.plugin.PageRef
import dev.penlab.plugin.Plugin
import dev.penlab.plugin.PluginStroke
import dev.penlab.plugin.Selection
import dev.penlab.sdk.PageInfo
import dev.penlab.sdk.PenLabBridge
import dev.penlab.sdk.PluginRequest
import dev.penlab.sdk.ResultCodec
import dev.penlab.sdk.SdkStroke
import dev.penlab.sdk.SelectionInfo
import dev.penlab.sdk.SelectionItem
import dev.penlab.sdk.StrokeFamily
import dev.penlab.sdk.StrokesBinary
import dev.penlab.sdk.SourceInfo
import dev.penlab.sdk.StrokesJson
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.penlab.plugin.Result as PluginResult

/** A scratch folder `cache/plugin-io/<uuid>/` for the files of one call; deleted when the call is over. */
class IoSession private constructor(val dir: File) {
  fun delete() {
    dir.deleteRecursively()
  }

  companion object {
    fun create(root: File): IoSession = IoSession(File(root, UUID.randomUUID().toString()).apply { mkdirs() })

    /** Removes sessions left by a killed app (older than [maxAgeMs]). */
    fun purge(root: File, maxAgeMs: Long = 60L * 60 * 1000) {
      val cutoff = System.currentTimeMillis() - maxAgeMs
      root.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    }
  }
}

/** Writing a selection as request files, and opening files for the transport. */
object RequestFiles {
  fun sdkStroke(s: PluginStroke) = SdkStroke(s.xs, s.ys, s.pressures, s.times, s.tool.name, s.argb, s.widthMm, StrokeFamily.fromId(s.family), s.layer)

  /**
   * Writes `selection.png`, `strokes.json` and (with [withItems]) the selected pictures as `item-<n>` into [dir], and lists
   * the selected items; null for an empty selection. Pictures: at most [PenLabBridge.MAX_ITEM_FILES], each prepared by
   * [ItemPictures.prepare]; any further or unreadable picture is listed without a file. Items without an id are not listed.
   * Screens pass `withItems = false` (they do not get items yet).
   */
  fun writeSelection(dir: File, selection: Selection, withItems: Boolean = true): SelectionInfo? {
    if (selection.isEmpty) return null
    var image: String? = null
    selection.bitmap?.let { bitmap ->
      File(dir, PenLabBridge.FILE_SELECTION_IMAGE).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      image = PenLabBridge.FILE_SELECTION_IMAGE
    }
    File(dir, PenLabBridge.FILE_STROKES).writeText(StrokesJson.encode(selection.strokes.map(::sdkStroke)), Charsets.UTF_8)
    val b = selection.boundsMm
    val items = if (withItems) selectedItems(dir, selection) else emptyList()
    return SelectionInfo(floatArrayOf(b.left, b.top, b.right, b.bottom), selection.strokes.size, selection.items.size, image, PenLabBridge.FILE_STROKES, items)
  }

  private fun selectedItems(dir: File, selection: Selection): List<SelectionItem> {
    var files = 0
    return selection.items.mapNotNull { item ->
      val id = item.id ?: return@mapNotNull null
      val u = item.unrotatedBoundsMm
      val box = floatArrayOf(u.left, u.top, u.right, u.bottom)
      val isPicture = item.typeId == PluginConvert.TYPE_IMAGE || item.typeId == PluginConvert.TYPE_INK_IMAGE
      var file: String? = null
      var mime: String? = null
      if (isPicture && files < PenLabBridge.MAX_ITEM_FILES) {
        val source = selection.imageFiles[id]
        val name = PenLabBridge.itemFileName(files)
        val written = if (source == null) null else try { ItemPictures.prepare(source, File(dir, name)) } catch (e: IOException) { null }
        if (written != null) {
          file = name
          mime = written
          files++
        }
      }
      val text = if (item.typeId == PluginConvert.TYPE_TEXT) item.payload.toString(Charsets.UTF_8) else null
      SelectionItem(id, item.typeId, box, item.rotationDeg, file, mime, text)
    }
  }

  /** Opens [names] (files in [dir]) as descriptors in a Bundle; close the result when the call is over. Missing files are left out. */
  fun open(dir: File, names: List<String>): OpenedFiles {
    val bundle = Bundle()
    val opened = ArrayList<ParcelFileDescriptor>()
    for (name in names) {
      val file = File(dir, name)
      if (!file.isFile) continue
      val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
      opened += pfd
      bundle.putParcelable(name, pfd)
    }
    return OpenedFiles(bundle, opened)
  }
}

/** The request files of one call as a Bundle of ParcelFileDescriptors; closing it closes them. */
class OpenedFiles(val bundle: Bundle, private val opened: List<ParcelFileDescriptor> = emptyList()) : java.io.Closeable {
  override fun close() {
    opened.forEach { runCatching { it.close() } }
  }
}

/** What the adapters need from the app: the folder for per-call scratch files (cache/plugin-io). */
class ExternalEnv(val ioRoot: File)

/**
 * A plugin app as a v1 [Plugin]: its capabilities are adapter objects, so PenLab's menus, import
 * routing and crash isolation treat it like a built-in plugin. [apiVersion] is the in-process API
 * the adapters speak; the bridge's own version (2) was checked when the plugin was discovered.
 */
class ExternalPlugin(
  private val manifest: ExternalManifest,
  private val packageName: String,
  private val transport: PluginTransport,
  private val env: ExternalEnv,
  private val icons: Map<String, ImageBitmap> = emptyMap(),
) : Plugin {
  override val id get() = manifest.id
  override val name get() = manifest.name
  override val apiVersion = API_VERSION

  override fun capabilities(): List<Capability> =
    manifest.capabilities.mapNotNull { decl ->
      val icon = decl.icon?.let(icons::get)
      when (decl) {
        is CapabilityDecl.PageActionDecl -> ExternalPageAction(name, decl, transport, env, icon)
        is CapabilityDecl.ImporterDecl -> ExternalImporter(name, decl, transport, env)
        is CapabilityDecl.ScreenDecl -> ExternalScreen(name, ComponentName(packageName, className(decl.activity, packageName)), decl.id, decl.label, decl.placement, icon, decl.inputs)
        is CapabilityDecl.MediaDecl -> ExternalMedia(name, packageName, decl, transport, icon)
        is CapabilityDecl.RecogniserDecl -> null // read, not hosted yet
      }
    }

  companion object {
    /** A manifest's activity name as a full class name: ".Chat" and "Chat" are relative to [packageName]. */
    fun className(activity: String, packageName: String): String =
      when {
        activity.startsWith(".") -> packageName + activity
        '.' !in activity -> "$packageName.$activity"
        else -> activity
      }
  }
}

/** A plugin's page action: the selection goes out as files, the answer comes back as a v1 [PluginResult]. */
class ExternalPageAction(
  private val pluginName: String,
  private val decl: CapabilityDecl.PageActionDecl,
  private val transport: PluginTransport,
  private val env: ExternalEnv,
  override val externalIcon: ImageBitmap? = null,
) : PageAction, HasExternalIcon {
  override val id get() = decl.id
  override val label get() = decl.label
  override val enabledWhen: EnabledWhen =
    when (decl.selection) {
      SelectionRule.REQUIRED -> EnabledWhen.SELECTION_NOT_EMPTY
      SelectionRule.NONE -> EnabledWhen.SELECTION_EMPTY
      SelectionRule.ANY -> EnabledWhen.ALWAYS
    }

  /** Asks the plugin transport to cancel an in-flight call for this capability. */
  fun cancel() = transport.cancel(decl.id)

  override suspend fun run(selection: Selection, page: PageRef): PluginResult =
    withContext(Dispatchers.IO) {
      val session = IoSession.create(env.ioRoot)
      var files = OpenedFiles(Bundle())
      try {
        val info = RequestFiles.writeSelection(session.dir, selection)
        val itemFiles = info?.items.orEmpty().mapNotNull { it.file }
        files = RequestFiles.open(session.dir, listOfNotNull(info?.imageFile, info?.strokesFile) + itemFiles)
        // darkPage is only known to screens (the v1 PageRef does not carry it).
        val request = PluginRequest(decl.id, PageInfo(page.notebookId, page.pageIndex, page.widthMm, page.heightMm, darkPage = false), info, strokesFormat = StrokesBinary.VERSION, itemsFormat = PenLabBridge.ITEMS_FORMAT)
        // `replaces` is honoured only for items this very request listed.
        val replaceable = info?.items.orEmpty().map { it.id }.toSet()
        transport.call(decl.id, request.toJson(), files.bundle, PluginTimeouts.ACTION_MS).orThrow(pluginName).use {
          ResultMapper.toResult(ResultCodec.decode(it.resultJson), it.files, pluginName, replaceable)
        }
      } finally {
        files.close()
        session.delete()
      }
    }
}

/** A plugin's importer: the picked file is copied into a scratch folder and handed over; the answer becomes an [ImportedNotebook]. */
class ExternalImporter(
  private val pluginName: String,
  private val decl: CapabilityDecl.ImporterDecl,
  private val transport: PluginTransport,
  private val env: ExternalEnv,
) : ExtensionImporter {
  override val label get() = decl.label
  override val mimeTypes get() = decl.mimeTypes
  override val extensions get() = decl.extensions

  /** Asks the plugin transport to cancel an in-flight import call for this capability. */
  fun cancel() = transport.cancel(decl.id)

  override suspend fun import(source: ImportSource): ImportedNotebook? =
    withContext(Dispatchers.IO) {
      val session = IoSession.create(env.ioRoot)
      var files = OpenedFiles(Bundle())
      try {
        try {
          source.open().use { input -> File(session.dir, PenLabBridge.FILE_SOURCE).outputStream().use { input.copyTo(it) } }
        } catch (e: IOException) {
          return@withContext null // an unreadable file is a failed import, not a broken plugin
        } catch (e: SecurityException) {
          return@withContext null
        }
        files = RequestFiles.open(session.dir, listOf(PenLabBridge.FILE_SOURCE))
        val request = PluginRequest(decl.id, source = SourceInfo(source.displayName, source.mimeType, source.suggestedTitle, PenLabBridge.FILE_SOURCE), strokesFormat = StrokesBinary.VERSION)
        transport.call(decl.id, request.toJson(), files.bundle, PluginTimeouts.IMPORT_MS).orThrow(pluginName).use {
          ResultMapper.toNotebook(ResultCodec.decode(it.resultJson), it.files, source.workDir)
        }
      } finally {
        files.close()
        session.delete()
      }
    }
}

/**
 * A plugin's `media` capability (lecture recording): PenLab sends it recording controls ([command]) and finger taps on
 * marked ink ([seek]) as ordinary bound calls; what the plugin reports back goes the other way, through
 * `dev.penlab.app.plugins.host.MediaHostService`. Both calls block on the binder, so they run off the main thread.
 */
class ExternalMedia(
  val pluginName: String,
  /** The plugin app's package: who the host service lets report for this capability. */
  val packageName: String,
  val decl: CapabilityDecl.MediaDecl,
  private val transport: PluginTransport,
  override val externalIcon: ImageBitmap? = null,
) : Capability, HasExternalIcon {
  val label get() = decl.label
  val placement get() = decl.placement

  /** The full class name of the plugin's `startActivity` (see [CapabilityDecl.MediaDecl.startActivity]), or null: then Start goes over the binder. */
  val startActivityClass: String? = decl.startActivity?.let { ExternalPlugin.className(it, packageName) }

  /** Sends `start`, `pause`, `resume` or `stop` (PenLabBridge.MEDIA_*), with the open notebook in [page] (see [MediaPages.forCommand]). Returns a message for the user if the plugin sent one, else null. A broken or silent plugin throws [ExternalPluginException]. */
  suspend fun command(command: String, page: PageInfo? = null): String? = send(PenLabBridge.CAP_MEDIA_COMMAND, PluginRequest.mediaCommand(command, page))

  /**
   * Asks the plugin to play the recording [ref] from [positionMs]; [recordingIndex] is its index in the notebook's list (the
   * plugin reports it back in `playbackPosition`, so the glow follows even for a reference it does not know). Same answer rules as [command].
   */
  suspend fun seek(ref: String, positionMs: Long, recordingIndex: Int = -1): String? = send(PenLabBridge.CAP_MEDIA_SEEK, PluginRequest.mediaSeek(ref, positionMs, recordingIndex))

  private suspend fun send(capability: String, requestJson: String): String? =
    withContext(Dispatchers.IO) {
      transport.call(capability, requestJson, Bundle(), PluginTimeouts.MEDIA_MS).orThrow(pluginName).use {
        when (val result = ResultCodec.decode(it.resultJson)) {
          is dev.penlab.sdk.PluginResult.Error -> result.message
          is dev.penlab.sdk.PluginResult.Message -> result.message
          else -> null
        }
      }
    }
}

/** A plugin's screen: PenLab starts [component] for a result (see MainActivity); [run] is never called. */
class ExternalScreen(
  val pluginName: String,
  val component: ComponentName,
  override val id: String,
  override val label: String,
  override val placement: ScreenPlacement,
  override val externalIcon: ImageBitmap? = null,
  /** What PenLab prepares for this screen besides the selection (see [ScreenInputs]). */
  val inputs: Set<String> = emptySet(),
) : ScreenPageAction, HasExternalIcon {
  override val enabledWhen: EnabledWhen =
    when (placement) {
      ScreenPlacement.SELECTION_MENU -> EnabledWhen.SELECTION_NOT_EMPTY
      ScreenPlacement.FILE_MENU -> EnabledWhen.SELECTION_EMPTY
      ScreenPlacement.MORE_MENU, ScreenPlacement.TOP_BAR -> EnabledWhen.ALWAYS
    }

  override suspend fun run(selection: Selection, page: PageRef): PluginResult = PluginResult.None
}
