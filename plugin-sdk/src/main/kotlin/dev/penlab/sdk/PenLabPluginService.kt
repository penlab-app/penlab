package dev.penlab.sdk

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import java.io.File
import java.io.InputStream

/**
 * What `run` is asked: the capability, its [request] and the request's files. A plugin reads files
 * with [readBytes] / [inputStream] / [strokes] / [selectionImage]; descriptors are closed for you
 * when the stream is closed.
 */
class PluginCall internal constructor(val capabilityId: String, val request: PluginRequest, private val files: Bundle) {
  /** The request file called [name] as a stream, or null. */
  fun inputStream(name: String): InputStream? = PluginFiles.inputStream(files, name)

  /** The whole request file called [name], or null. */
  fun readBytes(name: String): ByteArray? = PluginFiles.readBytes(files, name)

  /** The selection as a PNG (dark ink on transparent), or null when nothing is selected. */
  fun selectionImage(): ByteArray? = readBytes(PenLabBridge.FILE_SELECTION_IMAGE)

  /** The selected strokes, in millimetres; empty when nothing is selected. */
  fun strokes(): List<SdkStroke> = readBytes(PenLabBridge.FILE_STROKES)?.let { StrokesJson.decode(String(it, Charsets.UTF_8)) } ?: emptyList()

  /** The selected page items (items format 1; empty from an older PenLab, see [PluginRequest.itemsFormat]). */
  val selectionItems: List<SelectionItem> get() = request.selectionItems

  /** The original picture of a selected photo as a stream (null if PenLab sent none). Items format 1. */
  fun itemInputStream(item: SelectionItem): InputStream? = item.file?.let { inputStream(it) }

  /** The whole original picture of a selected photo (null if PenLab sent none). Items format 1. */
  fun itemBytes(item: SelectionItem, maxBytes: Long = PenLabBridge.MAX_ITEM_FILE_BYTES): ByteArray? = item.file?.let { PluginFiles.readBytes(files, it, maxBytes) }

  /** For an importer: the file the user picked, or null. */
  fun source(): InputStream? = inputStream(PenLabBridge.FILE_SOURCE)
}

/**
 * Base class of a plugin's service. Declare the subclass in the manifest, exported, with the intent
 * filter action `dev.penlab.PLUGIN` and the `dev.penlab.plugin.manifest` meta-data (see the sample plugin).
 *
 * Every call is checked before [onRun] sees it: the caller must be PenLab (`dev.penlab.app`) and,
 * if [requireSameSignature] is true, signed with the same certificate as this plugin. Calls are made
 * on a binder thread, one per call, possibly several at once: do not assume they arrive one at a time.
 */
abstract class PenLabPluginService : Service() {
  /**
   * Runs the capability named by [PluginCall.capabilityId] and returns its result. May take a
   * while (PenLab waits 60 s, importers 180 s). An exception becomes [PluginResult.Error], not a crash.
   */
  protected abstract fun onRun(call: PluginCall): PluginResult

  /** PenLab stopped waiting for [capabilityId] (timeout). Stop work if you can; the default does nothing. */
  protected open fun onCancel(capabilityId: String) {}

  /**
   * Also demand that PenLab is signed with this plugin's certificate. Off by default: PenLab's own
   * "Trusted" label and the user's consent already decide which plugins it talks to; switch this on
   * for a plugin that must only ever work with a PenLab you built.
   */
  protected open val requireSameSignature: Boolean get() = false

  private val binder =
    object : IPenLabPlugin.Stub() {
      override fun apiVersion(): Int = PenLabBridge.API_VERSION

      override fun run(capabilityId: String, requestJson: String, files: Bundle): Bundle {
        verifyCaller()
        val result =
          try {
            onRun(PluginCall(capabilityId, PluginRequest.fromJson(requestJson), files))
          } catch (e: Exception) {
            PluginResult.Error(e.message ?: e.javaClass.simpleName)
          }
        val encoded = ResultCodec.encode(result)
        return PluginFiles.toBundle(File(cacheDir, "penlab-out"), encoded.files).apply { putString(PenLabBridge.KEY_RESULT, encoded.json) }
      }

      override fun cancel(capabilityId: String) {
        verifyCaller()
        onCancel(capabilityId)
      }
    }

  final override fun onBind(intent: Intent?): IBinder = binder

  /** Throws [SecurityException] unless the caller may use this plugin (see [CallerPolicy]). */
  private fun verifyCaller() {
    val uid = Binder.getCallingUid()
    if (uid == Process.myUid()) return // a call inside this app (tests), not over the binder
    val packages = packageManager.getPackagesForUid(uid)?.toList().orEmpty()
    val allowed = CallerPolicy.isAllowed(packages, requireSameSignature, { packageManager.checkSignatures(uid, Process.myUid()) == PackageManager.SIGNATURE_MATCH })
    if (!allowed) throw SecurityException("Only ${PenLabBridge.HOST_PACKAGE} may call this plugin")
  }
}
