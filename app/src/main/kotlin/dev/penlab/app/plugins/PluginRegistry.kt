package dev.penlab.app.plugins

import android.util.Log
import dev.penlab.plugin.API_VERSION
import dev.penlab.plugin.Capability
import dev.penlab.plugin.Exporter
import dev.penlab.plugin.Importer
import dev.penlab.plugin.PageAction
import dev.penlab.plugin.PageRef
import dev.penlab.plugin.Plugin
import dev.penlab.plugin.PluginItem
import dev.penlab.plugin.PluginStroke
import dev.penlab.plugin.Selection
import dev.penlab.app.plugins.external.ExternalMedia
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import dev.penlab.plugin.EnabledWhen
import dev.penlab.plugin.Result as PluginResult

/**
 * The plugins this PenLab build runs, and the one place every call into a plugin goes through.
 *
 * [candidates] is an explicit list (see [BuiltInPlugins]); nothing is found by reflection. Only
 * plugins whose [Plugin.apiVersion] equals [API_VERSION] are loaded.
 *
 * Crash isolation: [guarded] catches everything a plugin throws, switches that plugin off for
 * the session and tells the user once through [onDisabled] (PenLab shows it in the snackbar). It
 * must never be used from touch or drawing code: plugins are not called on the ink path.
 */
class PluginRegistry(candidates: List<Plugin>, private val onDisabled: (message: String) -> Unit = {}) {
  /** A capability together with the plugin that offers it ([external]: a plugin app found at run time, not built in). */
  class Entry<out T : Capability>(val plugin: Plugin, val capability: T, val external: Boolean = false)

  /**
   * The outcome of a guarded call: its value, or [Failed]. A failure is a crash (the plugin is
   * switched off, [pluginDisabled] true) unless it was the device's doing (see [guarded]).
   */
  sealed interface Call<out T> {
    class Ok<out T>(val value: T) : Call<T>

    class Failed(val cause: Throwable, val pluginDisabled: Boolean) : Call<Nothing>
  }

  /** What running a [PageAction] came to. */
  sealed interface ActionOutcome {
    /** Items and strokes (real ink, in millimetres) to place on the page. */
    class Place(val items: List<PluginItem>, val strokes: List<PluginStroke> = emptyList()) : ActionOutcome

    class ShowText(val text: String) : ActionOutcome

    class ShowMessage(val message: String) : ActionOutcome

    /** The action had nothing to show. */
    data object Nothing : ActionOutcome

    /** The action threw. If [pluginDisabled], its plugin is switched off and the user has been told. */
    class Failed(val pluginDisabled: Boolean) : ActionOutcome
  }

  private class Loaded(val plugin: Plugin, val capabilities: List<Capability>, val external: Boolean = false)

  private val builtIn = ArrayList<Loaded>()

  /** Plugin apps found at run time; swapped whole by [replaceExternal] (rescans), read from any thread. */
  @Volatile private var external: List<Loaded> = emptyList()
  private val loaded: List<Loaded> get() = builtIn + external
  private val disabled: MutableSet<String> = ConcurrentHashMap.newKeySet()

  /** Plugins that were not loaded, with the reason (for logs and tests). */
  val skipped: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())

  init {
    builtIn += load(candidates, emptySet(), external = false)
  }

  /** Loads [candidates]: skips duplicates (of [takenIds] or each other) and plugins of another API version. */
  private fun load(candidates: List<Plugin>, takenIds: Set<String>, external: Boolean): List<Loaded> {
    val result = ArrayList<Loaded>()
    for (plugin in candidates) {
      val id = try { plugin.id } catch (e: Throwable) { skipped += "unreadable plugin: $e"; continue }
      if (id in takenIds || result.any { it.plugin.id == id }) {
        skipped += "$id: duplicate id"
        continue
      }
      val version = try { plugin.apiVersion } catch (e: Throwable) { skipped += "$id: $e"; continue }
      if (version != API_VERSION) {
        skipped += "$id: needs API $version, this PenLab speaks $API_VERSION"
        continue
      }
      try {
        result += Loaded(plugin, plugin.capabilities().toList(), external)
      } catch (e: Throwable) {
        skipped += "$id: capabilities() threw $e"
        disable(plugin, e)
      }
    }
    return result
  }

  /**
   * Replaces the plugin apps found at run time (a rescan). Built-in plugins keep their ids: an
   * external plugin claiming one is skipped. What was switched off this session stays switched off,
   * so a crashing plugin does not come back on the next rescan.
   */
  fun replaceExternal(candidates: List<Plugin>) {
    external = load(candidates, builtIn.map { it.plugin.id }.toSet(), external = true)
  }

  /** The plugins in use (loaded and not switched off). */
  val plugins: List<Plugin>
    get() = loaded.map { it.plugin }.filter { it.id !in disabled }

  fun isEnabled(pluginId: String) = loaded.any { it.plugin.id == pluginId } && pluginId !in disabled

  /** True if [pluginId] crashed or timed out and is off until PenLab restarts. */
  fun isSwitchedOff(pluginId: String) = pluginId in disabled

  /**
   * Clears the session crash flag of [pluginId] ("Try again"). The user's own on/off choice is not
   * touched (that lives in PluginConsent); this only lets the plugin run again. A plugin that crashes
   * again is switched off again, with a message.
   */
  fun reenable(pluginId: String) {
    disabled.remove(pluginId)
  }

  /**
   * Switches [plugin] off until PenLab restarts and tells the user once. Idempotent: a second
   * failure of the same plugin adds no second message.
   */
  fun disable(plugin: Plugin, cause: Throwable?) {
    val id = plugin.id
    if (!disabled.add(id)) return
    Log.e(TAG, "Plugin $id switched off", cause)
    onDisabled("“${plugin.name}” stopped working and is switched off until PenLab restarts.")
  }

  private inline fun <reified T : Capability> entries(): List<Entry<T>> =
    loaded.filter { it.plugin.id !in disabled }.flatMap { l -> l.capabilities.filterIsInstance<T>().map { Entry(l.plugin, it, l.external) } }

  /** Importers that read [mime] (an exact match, or a wildcard subtype, case-insensitive). */
  fun importers(mime: String): List<Entry<Importer>> = entries<Importer>().filter { e -> e.capability.mimeTypes.any { matches(it, mime) } }

  /**
   * Importers for a file called [fileName] of type [mime]: those that read the MIME type, plus
   * [ExtensionImporter]s that claim the file's extension (formats Android has no MIME type for).
   */
  fun importersFor(mime: String, fileName: String): List<Entry<Importer>> {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return entries<Importer>().filter { e ->
      e.capability.mimeTypes.any { matches(it, mime) } || (extension.isNotEmpty() && (e.capability as? ExtensionImporter)?.extensions?.contains(extension) == true)
    }
  }

  /** Importers that are built in: what is tried on a file nobody claims (never a plugin app). */
  fun builtInImporters(): List<Entry<Importer>> = importers().filter { !it.external }

  /** Every importer, whatever it reads. */
  fun importers(): List<Entry<Importer>> = entries()

  /** The MIME types all importers read (for a file picker). */
  fun importMimeTypes(): List<String> = importers().flatMap { it.capability.mimeTypes }.distinct()

  fun exporters(): List<Entry<Exporter>> = entries()

  /** The exporter that writes [mime], if there is one. */
  fun exporter(mime: String): Entry<Exporter>? = exporters().firstOrNull { it.capability.mimeType.equals(mime, ignoreCase = true) }

  /** Page actions offered now: [selectionEmpty] picks the "more" menu (true) or the lasso menu (false). */
  fun pageActions(selectionEmpty: Boolean): List<Entry<PageAction>> =
    entries<PageAction>().filter {
      // A screen placed in the toolbar's More panel is listed by [moreMenuActions] only.
      if ((it.capability as? ScreenPageAction)?.placement == ScreenPlacement.MORE_MENU) return@filter false
      when (it.capability.enabledWhen) {
        EnabledWhen.ALWAYS -> true
        EnabledWhen.SELECTION_EMPTY -> selectionEmpty
        EnabledWhen.SELECTION_NOT_EMPTY -> !selectionEmpty
      }
    }

  /** The `media` capabilities of the plugin apps in use (on and not crashed), in registry order. */
  fun mediaPlugins(): List<Entry<ExternalMedia>> = entries<ExternalMedia>()

  /** Plugin screens that ask to be in the toolbar's More panel. */
  fun moreMenuActions(): List<Entry<PageAction>> = entries<PageAction>().filter { (it.capability as? ScreenPageAction)?.placement == ScreenPlacement.MORE_MENU }

  /**
   * Runs [block] (a call into [plugin]) and catches anything it throws: the plugin is switched
   * off ([disable]) and the result is [Call.Failed]. Cancellation passes through. Two kinds of
   * throw are the device's fault, not the plugin's, and only fail the call: [IOException] (full
   * storage, unreadable file) and [OutOfMemoryError] (a huge notebook).
   */
  suspend fun <T> guarded(plugin: Plugin, block: suspend () -> T): Call<T> =
    try {
      Call.Ok(block())
    } catch (e: CancellationException) {
      throw e
    } catch (e: IOException) {
      Log.w(TAG, "Plugin ${plugin.id}: I/O failure", e)
      Call.Failed(e, pluginDisabled = false)
    } catch (e: OutOfMemoryError) {
      Log.w(TAG, "Plugin ${plugin.id}: out of memory", e)
      Call.Failed(e, pluginDisabled = false)
    } catch (e: Throwable) {
      disable(plugin, e)
      Call.Failed(e, pluginDisabled = true)
    }

  /** [guarded] for callers that are already on a worker thread and are not coroutines. */
  fun <T> guardedBlocking(plugin: Plugin, block: suspend () -> T): Call<T> = runBlocking { guarded(plugin, block) }

  /**
   * Runs a page action off the main thread and says what to do with the result. The caller
   * builds [selection] on the main thread first (it reads the canvas).
   */
  suspend fun runAction(entry: Entry<PageAction>, selection: Selection, page: PageRef): ActionOutcome =
    outcome(entry.plugin) { entry.capability.run(selection, page) }

  /**
   * Like [runAction] for a result that was produced elsewhere (a plugin screen's answer): [block]
   * turns it into a [PluginResult] off the main thread, under the same crash isolation (a malformed
   * answer switches the plugin off).
   */
  suspend fun outcome(plugin: Plugin, block: suspend () -> PluginResult): ActionOutcome =
    withContext(Dispatchers.Default) {
      when (val call = guarded(plugin, block)) {
        is Call.Failed -> ActionOutcome.Failed(call.pluginDisabled)
        is Call.Ok ->
          when (val result = call.value) {
            is PluginResult.Items -> if (result.items.isEmpty() && result.strokes.isEmpty()) ActionOutcome.Nothing else ActionOutcome.Place(result.items, result.strokes)
            is PluginResult.Text -> ActionOutcome.ShowText(result.text)
            is PluginResult.Message -> ActionOutcome.ShowMessage(result.message)
            PluginResult.None -> ActionOutcome.Nothing
          }
      }
    }

  private fun matches(pattern: String, mime: String): Boolean {
    if (pattern == "*/*") return true
    if (pattern.endsWith("/*")) return mime.startsWith(pattern.dropLast(1), ignoreCase = true)
    return pattern.equals(mime, ignoreCase = true)
  }

  private companion object {
    const val TAG = "PluginRegistry"
  }
}
