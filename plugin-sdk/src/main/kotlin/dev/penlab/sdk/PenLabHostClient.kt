package dev.penlab.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * A media plugin's connection to PenLab ([IPenLabHost]): bind PenLab's exported `MediaHostService`, then tell PenLab
 * when a recording starts, pauses, resumes and stops, how far playback is, and how loud the microphone is.
 *
 * ```
 * val host = PenLabHostClient(applicationContext, "dev.example.penlab.audio")
 * host.connectBlocking()                          // on a worker thread, not the main thread
 * val index = host.mediaStarted("content://.../rec/123")   // -1: no notebook is open; nothing will be stamped
 * ```
 *
 * Every call is safe to make when PenLab is not there: it does nothing and returns the "no" value (-1, false), never
 * throws. Calls are binder transactions (fast, but not free): make them from the recorder's thread, not from drawing code.
 * [pluginId] is the `id` of your `penlab_plugin.json`; PenLab refuses a call whose [pluginId] does not belong to the
 * calling app.
 */
class PenLabHostClient(private val context: Context, private val pluginId: String) {
  private val host = AtomicReference<IPenLabHost?>()
  private var connection: ServiceConnection? = null
  private var onConnected: (() -> Unit)? = null

  /** True while bound and PenLab's process is alive. */
  val isConnected: Boolean get() = host.get()?.asBinder()?.isBinderAlive == true

  /**
   * Starts binding and returns at once (false: PenLab is not installed or refuses). [whenConnected] runs on the main
   * thread once the connection is up (and again after PenLab restarts and reconnects).
   */
  @Synchronized
  fun connect(whenConnected: (() -> Unit)? = null): Boolean {
    if (connection != null) return true
    onConnected = whenConnected
    val c =
      object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
          host.set(IPenLabHost.Stub.asInterface(service))
          onConnected?.invoke()
        }

        override fun onServiceDisconnected(name: ComponentName) {
          host.set(null) // PenLab's process died; Android reconnects by itself while we stay bound
        }
      }
    val bound =
      try {
        context.bindService(Intent().setComponent(component()), c, Context.BIND_AUTO_CREATE)
      } catch (e: SecurityException) {
        false
      }
    if (!bound) {
      runCatching { context.unbindService(c) }
      return false
    }
    connection = c
    return true
  }

  /** [connect], then waits up to [timeoutMs] for the connection. Not on the main thread. Returns [isConnected]. */
  fun connectBlocking(timeoutMs: Long = 5_000L): Boolean {
    val latch = CountDownLatch(1)
    if (!connect { latch.countDown() }) return false
    if (isConnected) return true
    latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    return isConnected
  }

  /** Lets go of the connection. */
  @Synchronized
  fun disconnect() {
    connection?.let { runCatching { context.unbindService(it) } }
    connection = null
    host.set(null)
  }

  /** The host interface version PenLab speaks, or 0 when not connected. */
  fun apiVersion(): Int = call(0) { it.apiVersion() }

  /**
   * A recording of the notebook open in PenLab starts now ([startElapsedRealtimeMs] on [SystemClock.elapsedRealtime]'s
   * clock). [ref] is your opaque reference to the recording (a content URI, a path): PenLab stores it in the notebook and
   * hands it back in `media:seek`. Returns the recording's index (also what [playbackPosition] takes), or -1: no notebook
   * is open, PenLab is not connected, or the reference was refused (blank, too long, control characters). With -1 the
   * recording still works; its ink just carries no marks.
   */
  fun mediaStarted(ref: String, startElapsedRealtimeMs: Long = SystemClock.elapsedRealtime()): Int = call(-1) { it.mediaStarted(pluginId, ref, startElapsedRealtimeMs) }

  /** The recording paused at [atElapsedRealtimeMs]: ink written during the pause gets no mark, and the pill shows "paused". */
  fun mediaPaused(atElapsedRealtimeMs: Long = SystemClock.elapsedRealtime()) {
    call(Unit) { it.mediaPaused(pluginId, atElapsedRealtimeMs) }
  }

  /** The recording went on at [atElapsedRealtimeMs]. */
  fun mediaResumed(atElapsedRealtimeMs: Long = SystemClock.elapsedRealtime()) {
    call(Unit) { it.mediaResumed(pluginId, atElapsedRealtimeMs) }
  }

  /** The recording ended (saved or not): the pill goes away. */
  fun mediaStopped() {
    call(Unit) { it.mediaStopped(pluginId) }
  }

  /** Playback is at [positionMs] of recording [recordingIndex]: PenLab glows the ink written around then. Send a few times a second while playing, once when paused or stopped. */
  fun playbackPosition(recordingIndex: Int, positionMs: Long, playing: Boolean) {
    call(Unit) { it.playbackPosition(pluginId, recordingIndex, positionMs, playing) }
  }

  /** The microphone level 0..1 for the pill's meter; at most about ten a second. */
  fun level(level: Float) {
    call(Unit) { it.level(pluginId, level) }
  }

  private fun component() = ComponentName(PenLabBridge.HOST_PACKAGE, PenLabBridge.HOST_MEDIA_SERVICE)

  /** Runs one binder call; any failure (not connected, PenLab died, caller refused) gives [fallback]. */
  private inline fun <T> call(fallback: T, block: (IPenLabHost) -> T): T {
    val h = host.get() ?: return fallback
    return try {
      block(h)
    } catch (e: Exception) {
      fallback
    }
  }
}
