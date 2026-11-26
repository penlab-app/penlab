package dev.penlab.app.plugins.external

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.penlab.app.plugins.PluginRegistry
import dev.penlab.sdk.PenLabBridge
import java.io.File
import java.util.concurrent.Executor

/** A plugin app found on the device: its manifest (or why it is unusable), who it is, and whether it shares PenLab's certificate. */
class DiscoveredPlugin(
  val packageName: String,
  val serviceName: String,
  /** The manifest, or null when [problem] says why not. */
  val manifest: ExternalManifest?,
  val problem: String?,
  /** Signed with the same certificate as PenLab. */
  val trusted: Boolean,
)

/** What Settings → Plugins shows for one plugin. */
class PluginRow(
  val packageName: String,
  val name: String,
  /** "Word count · page action" per capability. */
  val capabilities: List<String>,
  /** The permissions the plugin declares. */
  val permissions: List<String>,
  val trusted: Boolean,
  /** The user's saved choice (trusted default included), whatever happened this session. */
  val enabled: Boolean,
  /**
   * Crashed or timed out this session: off until PenLab restarts or the user presses "Try again".
   * Kept apart from [enabled], which is only the user's saved choice and survives a crash.
   */
  val switchedOff: Boolean,
  /** Why it cannot be used (another bridge version, a broken manifest), or null. */
  val problem: String?,
) {
  /** What the switch shows: on only if the user wants it AND it is not crashed right now. */
  val switchOn: Boolean get() = enabled && !switchedOff

  /** Whether the switch can be flipped: not while it is crashed (use [canRetry]) or unusable. */
  val switchEnabled: Boolean get() = problem == null && !switchedOff

  /** Show "Try again": the user wants it on and it crashed this session. */
  val canRetry: Boolean get() = enabled && switchedOff
}

/** Finds plugin apps: every exported service with the `dev.penlab.PLUGIN` action (PenLab's manifest asks for visibility of those with `<queries>`). */
class PluginScanner(private val context: Context) {
  @Suppress("DEPRECATION")
  fun scan(): List<DiscoveredPlugin> {
    val pm = context.packageManager
    val found = pm.queryIntentServices(Intent(PenLabBridge.ACTION), PackageManager.GET_META_DATA)
    return found
      .mapNotNull { it.serviceInfo }
      .filter { it.exported && it.packageName != context.packageName }
      .map { si ->
        val trusted = pm.checkSignatures(context.packageName, si.packageName) == PackageManager.SIGNATURE_MATCH
        val resource = si.metaData?.getInt(PenLabBridge.META_MANIFEST, 0) ?: 0
        if (resource == 0) {
          DiscoveredPlugin(si.packageName, si.name, null, "no ${PenLabBridge.META_MANIFEST} meta-data", trusted)
        } else {
          val text =
            try {
              pm.getResourcesForApplication(si.applicationInfo).openRawResource(resource).use { dev.penlab.sdk.PluginFiles.readLimited(it, MAX_MANIFEST_BYTES) }.toString(Charsets.UTF_8)
            } catch (e: Exception) {
              Log.w(TAG, "Cannot read the manifest of ${si.packageName}", e)
              null
            }
          when (val parsed = text?.let(ManifestParser::parse)) {
            null -> DiscoveredPlugin(si.packageName, si.name, null, "the manifest could not be read", trusted)
            is ManifestParse.Rejected -> DiscoveredPlugin(si.packageName, si.name, null, parsed.reason, trusted)
            is ManifestParse.Ok -> DiscoveredPlugin(si.packageName, si.name, parsed.manifest, null, trusted)
          }
        }
      }
      .sortedBy { it.packageName }
  }

  private companion object {
    const val TAG = "PluginScanner"
    const val MAX_MANIFEST_BYTES = 256L * 1024
  }
}

/**
 * Keeps the registry's plugin apps in step with the device: [rescan] looks for plugin apps (on resume, when
 * Settings → Plugins opens, when a package is added or removed), applies the user's switches, and swaps the
 * enabled ones into the [PluginRegistry]. Menus read [version] so they redraw after a swap.
 */
class ExternalPluginManager(
  private val context: Context,
  private val registry: PluginRegistry,
  private val consent: PluginConsent,
  private val worker: Executor,
  private val scanner: PluginScanner = PluginScanner(context),
  private val transportFor: (ComponentName) -> PluginTransport = { BoundServiceTransport(context, it) },
) {
  private val main = Handler(Looper.getMainLooper())
  private val env = ExternalEnv(File(context.cacheDir, "plugin-io"))
  private var discovered: List<DiscoveredPlugin> = emptyList()

  /** What Settings lists. */
  var rows by mutableStateOf<List<PluginRow>>(emptyList())
    private set

  /** Bumped whenever the set of hosted plugins changes (read it in composition to redraw menus). */
  var version by mutableIntStateOf(0)
    private set

  /** The folder for scratch files (`cache/plugin-io`): leftover sessions are purged. */
  val ioRoot: File get() = env.ioRoot

  /** Looks for plugin apps now (off the main thread) and applies the result on the main thread. */
  fun rescan() {
    worker.execute {
      val scanned =
        try {
          IoSession.purge(env.ioRoot)
          scanner.scan()
        } catch (e: Exception) {
          Log.e(TAG, "Plugin scan failed", e)
          return@execute
        }
      main.post {
        discovered = scanned
        apply()
      }
    }
  }

  /** The user flipped [pkg]'s switch. Returns false if a consent dialog must be shown first ([accept] finishes it). */
  fun setEnabled(pkg: String, on: Boolean): Boolean {
    val plugin = discovered.firstOrNull { it.packageName == pkg } ?: return true
    return when (val step = consent.step(pkg, plugin.trusted, on)) {
      is ConsentStep.Apply -> {
        consent.apply(pkg, step.on)
        apply()
        true
      }
      ConsentStep.AskConsent -> false
    }
  }

  /** "Try again": clears [pkg]'s session crash flag (the user's choice is untouched) and rescans. */
  fun retry(pkg: String) {
    discovered.firstOrNull { it.packageName == pkg }?.manifest?.id?.let(registry::reenable)
    apply()
    rescan()
  }

  /** The user accepted [pkg]'s consent dialog. */
  fun accept(pkg: String) {
    consent.accept(pkg)
    apply()
  }

  /** Recomputes [rows] and swaps the enabled plugins into the registry. Main thread. */
  private fun apply() {
    val enabled = discovered.filter { it.manifest != null && consent.isEnabled(it.packageName, it.trusted) }
    registry.replaceExternal(
      enabled.map { ExternalPlugin(it.manifest!!, it.packageName, transportFor(ComponentName(it.packageName, it.serviceName)), env) }
    )
    rows = discovered.map { PluginRows.row(it, consent.isEnabled(it.packageName, it.trusted), it.manifest?.id?.let(registry::isSwitchedOff) == true) }
    version++
  }

  private companion object {
    const val TAG = "ExternalPlugins"
  }
}

/** Turning discovered plugins into what Settings shows. */
object PluginRows {
  fun row(plugin: DiscoveredPlugin, enabled: Boolean, switchedOff: Boolean): PluginRow {
    val m = plugin.manifest
    return PluginRow(
      packageName = plugin.packageName,
      name = m?.name ?: plugin.packageName,
      capabilities = m?.capabilities.orEmpty().map { "${it.label} · ${kindLabel(it)}" },
      permissions = m?.permissions.orEmpty(),
      trusted = plugin.trusted,
      enabled = enabled && m != null,
      switchedOff = switchedOff && enabled,
      problem = plugin.problem,
    )
  }

  private fun kindLabel(c: CapabilityDecl): String =
    when (c) {
      is CapabilityDecl.PageActionDecl -> "page action"
      is CapabilityDecl.ImporterDecl -> "importer"
      is CapabilityDecl.ScreenDecl -> "screen"
      is CapabilityDecl.RecogniserDecl -> "recogniser (not used yet)"
      is CapabilityDecl.MediaDecl -> "media (not used yet)"
    }
}
