package dev.penlab.app.plugins.host

import dev.penlab.sdk.PageInfo

/**
 * How "Record lecture" reaches a media plugin. A microphone foreground service may only be started by an app that is visible,
 * and a plugin's bound service is not (BIND_INCLUDE_CAPABILITIES did not change that on Android 16), so a plugin can declare a
 * `startActivity`: PenLab opens it, and it starts the service from the foreground. Without one, PenLab sends `media:command start`
 * over the binder as before (older plugins).
 */
sealed interface MediaStartRoute {
  /** Open the plugin's activity [className] (full class name). */
  class Activity(val className: String) : MediaStartRoute

  /** Send `media:command start` over the binder. */
  data object Binder : MediaStartRoute

  /** The plugin has a start activity but no notebook is open to record for. */
  data object NoNotebook : MediaStartRoute

  companion object {
    /** [startActivityClass] is [dev.penlab.app.plugins.external.ExternalMedia.startActivityClass]; [page] is the open notebook's page, or null. */
    fun choose(startActivityClass: String?, page: PageInfo?): MediaStartRoute =
      when {
        startActivityClass == null -> Binder
        page == null -> NoNotebook
        else -> Activity(startActivityClass)
      }
  }
}
