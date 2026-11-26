package dev.penlab.app.plugins.host

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import dev.penlab.sdk.IPenLabHost
import dev.penlab.sdk.PenLabBridge

/**
 * PenLab's side of the "media" capability (docs/plugin-bridge.md, "Media"): the exported service a recording plugin
 * binds ([dev.penlab.sdk.PenLabHostClient]) to say when it starts, pauses, resumes and stops, how far its player is,
 * and how loud the microphone is.
 *
 * Exported, so the check is on every call: the caller's UID must belong to a plugin that PenLab hosts right now
 * (switched on, not crashed, declares `media`), and the `pluginId` it names must be that plugin's own
 * ([MediaCallerPolicy]). Anyone else gets a [SecurityException]. The calls themselves only change [MediaSessions]'
 * state; nothing here touches the canvas, and nothing runs on the pen's path.
 */
class MediaHostService : Service() {
  private val binder =
    object : IPenLabHost.Stub() {
      override fun apiVersion(): Int = PenLabBridge.HOST_API_VERSION

      override fun mediaStarted(pluginId: String, ref: String, startElapsedRealtimeMs: Long): Int = withCaller(pluginId, -1) { it.started(pluginId, ref, startElapsedRealtimeMs) }

      override fun mediaPaused(pluginId: String, atElapsedRealtimeMs: Long) = withCaller(pluginId, Unit) { it.paused(pluginId, atElapsedRealtimeMs) }

      override fun mediaResumed(pluginId: String, atElapsedRealtimeMs: Long) = withCaller(pluginId, Unit) { it.resumed(pluginId, atElapsedRealtimeMs) }

      override fun mediaStopped(pluginId: String) = withCaller(pluginId, Unit) { it.stopped(pluginId) }

      override fun playbackPosition(pluginId: String, recordingIndex: Int, positionMs: Long, playing: Boolean) = withCaller(pluginId, Unit) { it.playbackPosition(pluginId, recordingIndex, positionMs, playing) }

      override fun level(pluginId: String, level: Float) = withCaller(pluginId, Unit) { it.level(pluginId, level) }
    }

  override fun onBind(intent: Intent?): IBinder = binder

  /** Checks the caller, then runs [block] on the session state; [idle] when nothing is installed (no screen). */
  private inline fun <T> withCaller(pluginId: String, idle: T, block: (MediaSessions) -> T): T {
    val host = MediaHost.current() ?: return idle
    val uid = Binder.getCallingUid()
    val packages = packageManager.getPackagesForUid(uid)?.toList().orEmpty()
    val caller = MediaCallerPolicy.resolve(packages, pluginId, host.hosted())
    if (caller == null) throw SecurityException("Not a media plugin PenLab hosts: $pluginId")
    return block(MediaHost.sessions)
  }
}
