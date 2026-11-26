package dev.penlab.app.plugins.external

import android.content.Context

/** Where a plugin's on/off choice and consent are remembered (by package name). */
interface ConsentStore {
  /** The user's switch for [pkg], or null if never touched. */
  fun enabled(pkg: String): Boolean?

  fun setEnabled(pkg: String, enabled: Boolean)

  /** The user already accepted the one-time consent dialog for [pkg]. */
  fun consented(pkg: String): Boolean

  fun setConsented(pkg: String)
}

/** What flipping a plugin's switch needs. */
sealed interface ConsentStep {
  /** Just do it: set the switch to [on]. */
  class Apply(val on: Boolean) : ConsentStep

  /** Show the consent dialog first; apply only if the user accepts. */
  data object AskConsent : ConsentStep
}

/**
 * Who is switched on. A plugin signed with PenLab's own certificate ("Trusted") is on until the
 * user switches it off; any other plugin is off until the user switches it on, and the first time
 * for each plugin that goes through a consent dialog naming what it can reach.
 */
class PluginConsent(private val store: ConsentStore) {
  fun isEnabled(pkg: String, trusted: Boolean): Boolean = store.enabled(pkg) ?: trusted

  /** What to do when the user asks to set [pkg]'s switch to [wantOn]. Switching off never asks. */
  fun step(pkg: String, trusted: Boolean, wantOn: Boolean): ConsentStep =
    if (wantOn && !trusted && !store.consented(pkg)) ConsentStep.AskConsent else ConsentStep.Apply(wantOn)

  fun apply(pkg: String, on: Boolean) = store.setEnabled(pkg, on)

  /** The user accepted the dialog: remembered, and the plugin is switched on. */
  fun accept(pkg: String) {
    store.setConsented(pkg)
    store.setEnabled(pkg, true)
  }
}

/** [ConsentStore] in a private SharedPreferences file, so the choice survives restarts. */
class PrefsConsentStore(context: Context) : ConsentStore {
  private val prefs = context.getSharedPreferences("plugins", Context.MODE_PRIVATE)

  override fun enabled(pkg: String): Boolean? = if (prefs.contains("enabled.$pkg")) prefs.getBoolean("enabled.$pkg", false) else null

  override fun setEnabled(pkg: String, enabled: Boolean) = prefs.edit().putBoolean("enabled.$pkg", enabled).apply()

  override fun consented(pkg: String): Boolean = prefs.getBoolean("consented.$pkg", false)

  override fun setConsented(pkg: String) = prefs.edit().putBoolean("consented.$pkg", true).apply()
}
