package dev.penlab.app.plugins.external

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.DeadObjectException
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import dev.penlab.sdk.IPenLabPlugin
import dev.penlab.sdk.PenLabBridge
import dev.penlab.sdk.PluginFiles
import java.io.Closeable
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException

/** How long PenLab waits for a plugin app. */
object PluginTimeouts {
  /** Page actions (and screens' mapping). */
  const val ACTION_MS = 60_000L

  /** Importers read whole files: longer. */
  const val IMPORT_MS = 180_000L

  /** Recording controls and seeks: a recorder answers at once; a plugin that does not is not worth waiting for. */
  const val MEDIA_MS = 10_000L
}

/** Why a call to a plugin app failed (see [ExternalPluginException]). */
enum class FailureKind {
  /** No answer in time (or it never connected). */
  TIMEOUT,

  /** The plugin's process died (or the binder broke) during the call. */
  DIED,

  /** Could not bind: not installed any more, not exported, no binder. */
  UNAVAILABLE,

  /** The plugin speaks another bridge version. */
  API_MISMATCH,

  /** The plugin refused PenLab as caller (SecurityException). */
  REJECTED,

  /** The plugin threw something, or answered nonsense. */
  PLUGIN_ERROR,
}

/** A failure of the plugin app, not of the device: the registry switches that plugin off and tells the user once. Deliberately not an IOException. */
class ExternalPluginException(val kind: FailureKind, val pluginName: String, detail: String, cause: Throwable? = null) :
  RuntimeException("$pluginName: $kind: $detail", cause)

/** The files a result came with, readable by name (closed with the call). */
interface ResultFiles : Closeable {
  /** The whole file [name], or null if the result has none by that name. Throws IOException beyond [maxBytes]. */
  fun readBytes(name: String, maxBytes: Long): ByteArray?

  /** Copies file [name] to [target]; false if there is none. */
  fun copyTo(name: String, target: File): Boolean

  companion object {
    val EMPTY =
      object : ResultFiles {
        override fun readBytes(name: String, maxBytes: Long): ByteArray? = null
        override fun copyTo(name: String, target: File) = false
        override fun close() {}
      }
  }
}

/** What a call to a plugin app came to. */
sealed interface TransportOutcome {
  class Ok(val resultJson: String, val files: ResultFiles) : TransportOutcome, Closeable {
    override fun close() = files.close()
  }

  class Failed(val kind: FailureKind, val detail: String, val cause: Throwable? = null) : TransportOutcome

  data object Cancelled : TransportOutcome

  /** The result, or throws [ExternalPluginException] (or [CancellationException] when cancelled). */
  fun orThrow(pluginName: String): Ok =
    when (this) {
      is Ok -> this
      is Failed -> throw ExternalPluginException(kind, pluginName, detail, cause)
      is Cancelled -> throw CancellationException("$pluginName cancelled")
    }
}

/** One blocking call into a plugin app. Production: [BoundServiceTransport]; tests use fakes. */
fun interface PluginTransport {
  /** Never throws: every failure is a [TransportOutcome.Failed] or [TransportOutcome.Cancelled]. [files] holds request files as ParcelFileDescriptors; the caller closes them. */
  fun call(capabilityId: String, requestJson: String, files: Bundle, timeoutMs: Long): TransportOutcome

  /** Best-effort cancellation of an in-flight call for [capabilityId]. */
  fun cancel(capabilityId: String) {}
}

/** Maps what a binder call threw to a failure. Pure, so it is unit-tested. */
internal fun failureFor(e: Throwable): TransportOutcome.Failed =
  when (e) {
    is DeadObjectException -> TransportOutcome.Failed(FailureKind.DIED, "the plugin's process died", e)
    is RemoteException -> TransportOutcome.Failed(FailureKind.DIED, "the connection to the plugin broke: ${e.message}", e)
    is SecurityException -> TransportOutcome.Failed(FailureKind.REJECTED, e.message ?: "refused", e)
    else -> TransportOutcome.Failed(FailureKind.PLUGIN_ERROR, e.message ?: e.javaClass.simpleName, e)
  }

/**
 * The flags for binding a plugin for one call. Media calls ("media:command", "media:seek") also pass
 * BIND_INCLUDE_CAPABILITIES: the plugin may be asked to start recording, and a foreground service of type microphone can
 * only be started if the app has the "while in use" microphone capability, which a plugin gets while a foreground app
 * (PenLab) is bound to it with this flag. Without it Android refuses the service, or the recording is silent. Only media calls
 * get it: the other plugins have no reason to inherit PenLab's capabilities.
 */
internal fun bindFlagsFor(capabilityId: String): Int =
  if (capabilityId.startsWith("media:")) Context.BIND_AUTO_CREATE or Context.BIND_INCLUDE_CAPABILITIES else Context.BIND_AUTO_CREATE

/** [ResultFiles] over the Bundle of ParcelFileDescriptors a plugin returned. */
private class BundleResultFiles(private val bundle: Bundle) : ResultFiles {
  override fun readBytes(name: String, maxBytes: Long): ByteArray? = PluginFiles.readBytes(bundle, name, maxBytes)

  override fun copyTo(name: String, target: File): Boolean {
    val input = PluginFiles.inputStream(bundle, name) ?: return false
    input.use { i -> target.outputStream().use { i.copyTo(it) } }
    return true
  }

  @Suppress("DEPRECATION")
  override fun close() {
    for (key in bundle.keySet().toList()) (bundle.get(key) as? ParcelFileDescriptor)?.let { runCatching { it.close() } }
  }
}

/**
 * Binds to a plugin's service by its explicit component for the length of one call: bind (on a worker
 * thread, never the main looper), check the bridge version, call `run`, unbind. A hung plugin cannot be
 * interrupted from here, so the call runs on its own thread and is waited for with a deadline; after
 * the deadline PenLab tells the plugin to `cancel`, unbinds and reports [FailureKind.TIMEOUT].
 */
class BoundServiceTransport(private val context: Context, private val component: ComponentName) : PluginTransport {
  private class InFlight(
    val connected: CountDownLatch,
    val binder: AtomicReference<IBinder?>,
    val task: AtomicReference<Future<TransportOutcome>?> = AtomicReference(),
    val cancelled: AtomicBoolean = AtomicBoolean(false),
  )

  private val inFlight = ConcurrentHashMap<String, InFlight>()

  override fun cancel(capabilityId: String) {
    val entry = inFlight[capabilityId] ?: return
    entry.cancelled.set(true)
    entry.connected.countDown()
    entry.task.get()?.cancel(true)
    val b = entry.binder.get() ?: return
    dispatchCancel(IPenLabPlugin.Stub.asInterface(b), capabilityId)
  }

  override fun call(capabilityId: String, requestJson: String, files: Bundle, timeoutMs: Long): TransportOutcome {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    fun remaining() = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L)

    val connected = CountDownLatch(1)
    val binder = AtomicReference<IBinder?>()
    val gone = AtomicBoolean(false)
    val entry = InFlight(connected, binder)
    inFlight[capabilityId] = entry
    val connection =
      object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
          binder.set(service)
          connected.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) {
          gone.set(true) // the plugin's process died; a pending call fails on its own
        }

        override fun onBindingDied(name: ComponentName) {
          gone.set(true)
          connected.countDown()
        }

        override fun onNullBinding(name: ComponentName) = connected.countDown()
      }
    val bound =
      try {
        context.bindService(Intent().setComponent(component), bindFlagsFor(capabilityId), POOL, connection)
      } catch (e: SecurityException) {
        false
      }
    try {
      if (!bound) return TransportOutcome.Failed(FailureKind.UNAVAILABLE, "could not bind ${component.flattenToShortString()}")
      val awaited =
        try {
          connected.await(remaining(), TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
          entry.cancelled.set(true)
          return TransportOutcome.Cancelled
        }
      if (entry.cancelled.get()) return TransportOutcome.Cancelled
      if (!awaited) return TransportOutcome.Failed(FailureKind.TIMEOUT, "did not connect in time")
      val rawBinder = binder.get()
        ?: return if (entry.cancelled.get()) TransportOutcome.Cancelled
        else TransportOutcome.Failed(if (gone.get()) FailureKind.DIED else FailureKind.UNAVAILABLE, "no connection")
      val service = IPenLabPlugin.Stub.asInterface(rawBinder)
      val task = POOL.submit(Callable { remote(service, capabilityId, requestJson, files) })
      entry.task.set(task)
      if (entry.cancelled.get()) {
        task.cancel(true)
        dispatchCancel(service, capabilityId)
        return TransportOutcome.Cancelled
      }
      val outcome =
        try {
          task.get(remaining(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
          task.cancel(true)
          dispatchCancel(service, capabilityId) // best effort: the plugin may be hung
          TransportOutcome.Failed(FailureKind.TIMEOUT, "no answer within ${timeoutMs / 1000} s")
        } catch (_: InterruptedException) {
          task.cancel(true)
          dispatchCancel(service, capabilityId)
          TransportOutcome.Cancelled
        } catch (_: java.util.concurrent.CancellationException) {
          task.cancel(true)
          dispatchCancel(service, capabilityId)
          TransportOutcome.Cancelled
        } catch (e: ExecutionException) {
          if (entry.cancelled.get()) TransportOutcome.Cancelled else failureFor(e.cause ?: e)
        }
      return if (entry.cancelled.get()) TransportOutcome.Cancelled else outcome
    } finally {
      inFlight.remove(capabilityId, entry)
      try {
        context.unbindService(connection)
      } catch (e: IllegalArgumentException) {
        Log.w(TAG, "unbind: $e") // never bound
      }
    }
  }

  private fun dispatchCancel(service: IPenLabPlugin, capabilityId: String) {
    POOL.execute { runCatching { service.cancel(capabilityId) } }
  }

  /** The calls into the plugin, on a pool thread. */
  private fun remote(service: IPenLabPlugin, capabilityId: String, requestJson: String, files: Bundle): TransportOutcome =
    try {
      val version = service.apiVersion()
      if (version != PenLabBridge.API_VERSION) {
        TransportOutcome.Failed(FailureKind.API_MISMATCH, "plugin speaks bridge API $version, PenLab speaks ${PenLabBridge.API_VERSION}")
      } else {
        val answer = service.run(capabilityId, requestJson, files)
        val json = answer?.getString(PenLabBridge.KEY_RESULT)
        if (json == null) TransportOutcome.Failed(FailureKind.PLUGIN_ERROR, "the plugin returned no result") else TransportOutcome.Ok(json, BundleResultFiles(answer))
      }
    } catch (e: Exception) {
      failureFor(e)
    }

  private companion object {
    const val TAG = "PluginTransport"

    /** Bind callbacks and blocking binder calls; threads exit when idle. */
    val POOL = Executors.newCachedThreadPool { r -> Thread(r, "penlab-plugin").apply { isDaemon = true } }
  }
}
