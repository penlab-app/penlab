package dev.penlab.app.plugins.host

import android.os.SystemClock

/**
 * The process-wide meeting point between [MediaHostService] (created by Android, knows nothing of the activity) and
 * the screen that owns the registry and the open notebook. The activity installs itself in `onCreate` and removes
 * itself in `onDestroy`; with nothing installed (PenLab's process alive without its screen) every host call does
 * nothing, so a plugin telling PenLab "stopped" after PenLab was closed never crashes anyone.
 */
object MediaHost {
  /** What the activity gives the service. */
  class Installed(
    /** The plugin apps that may call right now: on, not crashed, declaring `media`. Read on every call (the user can switch a plugin off at any time). */
    val hosted: () -> List<MediaPluginRef>,
  )

  /** The recording state, for the whole process: the screen can be recreated while a plugin records. */
  val sessions = MediaSessions(clock = { SystemClock.elapsedRealtime() })

  @Volatile private var installed: Installed? = null

  fun install(host: Installed) {
    installed = host
  }

  /** Removes [host] if it is the one installed (a new activity may already have replaced it). */
  fun uninstall(host: Installed) {
    if (installed === host) installed = null
  }

  /** What is installed, or null. */
  fun current(): Installed? = installed
}
