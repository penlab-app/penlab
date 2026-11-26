package dev.penlab.app.plugins.host

/** A plugin app that may use the media host: it is switched on, not crashed, and declares a `media` capability. */
class MediaPluginRef(
  /** The `id` of its manifest. */
  val pluginId: String,
  /** Its Android package. */
  val packageName: String,
)

/**
 * Who may call [MediaHostService]. Kept apart from the Android calls that feed it so it can be unit-tested.
 *
 * The rule: the calling UID's packages must include the package that OWNS the [pluginId] named in the call, and that
 * plugin must be one PenLab currently hosts (switched on by the user's consent or by trust, not crashed, declaring
 * `media`). So an app that is not a plugin, a plugin that is off, and an enabled plugin speaking as another plugin
 * are all refused.
 */
object MediaCallerPolicy {
  /** The plugin the caller is, or null if the call must be refused. */
  fun resolve(callerPackages: List<String>, pluginId: String, hosted: List<MediaPluginRef>): MediaPluginRef? =
    hosted.firstOrNull { it.pluginId == pluginId && it.packageName in callerPackages }
}
