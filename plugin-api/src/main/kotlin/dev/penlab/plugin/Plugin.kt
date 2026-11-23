package dev.penlab.plugin

/** The plugin API version this build of PenLab speaks. PenLab loads plugins whose [Plugin.apiVersion] equals it. */
const val API_VERSION = 1

/**
 * A plugin: an id, a name, and the things it adds to PenLab ([capabilities]).
 *
 * Built-in plugins are listed by name in PenLab's `BuiltInPlugins` (no reflection, no scanning).
 * Everything here is called by PenLab, never on the ink path: no method of a plugin runs while
 * the pen is down. Any exception a plugin throws is caught by the host and switches that plugin
 * off for the rest of the session (the app carries on).
 */
interface Plugin {
  /** Stable reverse-domain style id, e.g. `dev.penlab.pdf`. Used in file names, so keep it plain ASCII with no `/`. */
  val id: String

  /** Name shown to the user (in messages and Settings). */
  val name: String

  /** The [API_VERSION] this plugin was written against. */
  val apiVersion: Int

  /**
   * What the plugin adds. Called once, on the main thread, when PenLab starts; must be quick and
   * must not do I/O. The list is kept for the session.
   */
  fun capabilities(): List<Capability>
}

/**
 * Marker for everything a [Plugin] can offer: [PageAction], [PageItemType], [Recogniser],
 * [Importer], [Exporter], [SettingsEntry] and, for built-in plugins only, [Panel] and
 * [StrokeListener].
 */
interface Capability
