package dev.penlab.app.plugins.external

import dev.penlab.app.ui.pluginConsentText
import dev.penlab.app.ui.pluginSubtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginConsentTest {
  private class MemoryStore : ConsentStore {
    val enabled = HashMap<String, Boolean>()
    val consented = HashSet<String>()
    override fun enabled(pkg: String) = enabled[pkg]
    override fun setEnabled(pkg: String, enabled: Boolean) { this.enabled[pkg] = enabled }
    override fun consented(pkg: String) = pkg in consented
    override fun setConsented(pkg: String) { consented += pkg }
  }

  private val store = MemoryStore()
  private val consent = PluginConsent(store)

  @Test
  fun trustedIsOnByDefaultAndOthersAreOff() {
    assertTrue(consent.isEnabled("trusted.pkg", trusted = true))
    assertFalse(consent.isEnabled("other.pkg", trusted = false))
  }

  @Test
  fun theUsersChoiceBeatsTheDefault() {
    consent.apply("trusted.pkg", false)
    assertFalse(consent.isEnabled("trusted.pkg", trusted = true))
    consent.apply("trusted.pkg", true)
    assertTrue(consent.isEnabled("trusted.pkg", trusted = true))
  }

  @Test
  fun anUntrustedPluginAsksOnceThenNeverAgain() {
    assertTrue(consent.step("other.pkg", trusted = false, wantOn = true) is ConsentStep.AskConsent)
    consent.accept("other.pkg") // the user pressed "Switch on"
    assertTrue(consent.isEnabled("other.pkg", trusted = false))
    consent.apply("other.pkg", false) // switched off later
    assertFalse(consent.isEnabled("other.pkg", trusted = false))
    val again = consent.step("other.pkg", trusted = false, wantOn = true)
    assertTrue(again is ConsentStep.Apply && again.on) // no second dialog
  }

  @Test
  fun decliningTheDialogChangesNothing() {
    consent.step("other.pkg", trusted = false, wantOn = true) // the dialog is shown, the user cancels: accept() never runs
    assertFalse(consent.isEnabled("other.pkg", trusted = false))
    assertNull(store.enabled("other.pkg"))
    assertFalse(store.consented("other.pkg"))
  }

  @Test
  fun switchingOffAndTrustedNeverAsk() {
    val off = consent.step("other.pkg", trusted = false, wantOn = false)
    assertTrue(off is ConsentStep.Apply && !off.on)
    val on = consent.step("trusted.pkg", trusted = true, wantOn = true)
    assertTrue(on is ConsentStep.Apply && on.on)
  }

  @Test
  fun consentOfOnePluginDoesNotCoverAnother() {
    consent.accept("a.pkg")
    assertTrue(consent.step("b.pkg", trusted = false, wantOn = true) is ConsentStep.AskConsent)
  }

  @Test
  fun settingsTextSaysWhatThePluginCanReach() {
    val row = PluginRow("x.pkg", "X", listOf("Do it · page action"), listOf("network"), trusted = false, enabled = false, switchedOff = false, problem = null)
    assertTrue("Not verified" in pluginSubtitle(row))
    assertTrue("Declares: network" in pluginSubtitle(row))
    assertTrue("network" in pluginConsentText(row))
    assertTrue("select" in pluginConsentText(row))
    val trusted = PluginRow("y.pkg", "Y", emptyList(), emptyList(), trusted = true, enabled = true, switchedOff = true, problem = null)
    assertTrue("PenLab's own certificate" in pluginSubtitle(trusted))
    assertTrue("Declares no permissions" in pluginSubtitle(trusted))
    assertTrue("off until PenLab restarts" in pluginSubtitle(trusted))
    assertEquals(1, pluginSubtitle(row.let { PluginRow(it.packageName, it.name, emptyList(), emptyList(), true, true, false, "needs bridge API 3") }).lines().count { "Cannot be used" in it })
  }
}

/** The honest state of a plugin row after a crash, and "Try again". */
class PluginCrashStateTest {
  private class P(override val id: String) : dev.penlab.plugin.Plugin {
    override val apiVersion = dev.penlab.plugin.API_VERSION
    override val name = "P $id"
    override fun capabilities() = emptyList<dev.penlab.plugin.Capability>()
  }

  private fun row(enabled: Boolean, crashed: Boolean, problem: String? = null) =
    PluginRow("x.pkg", "X", emptyList(), emptyList(), trusted = true, enabled = enabled, switchedOff = crashed, problem = problem)

  @Test
  fun aCrashedPluginShowsTheSwitchOffAndOffersTryAgain() {
    val r = row(enabled = true, crashed = true)
    assertTrue(r.enabled) // the saved choice is kept
    assertFalse(r.switchOn) // but the switch does not lie
    assertFalse(r.switchEnabled)
    assertTrue(r.canRetry)
  }

  @Test
  fun aHealthyOrUserDisabledPluginHasNoTryAgain() {
    assertTrue(row(enabled = true, crashed = false).let { it.switchOn && it.switchEnabled && !it.canRetry })
    assertTrue(row(enabled = false, crashed = false).let { !it.switchOn && it.switchEnabled && !it.canRetry })
  }

  @Test
  fun rowsBuiltFromTheRegistryFlagKeepTheUsersChoiceSeparate() {
    val plugin = DiscoveredPlugin("x.pkg", "x.Svc", null, "broken", trusted = true)
    val crashed = PluginRows.row(plugin, enabled = true, switchedOff = true)
    assertFalse(crashed.switchOn)
    val userOff = PluginRows.row(plugin, enabled = false, switchedOff = true)
    assertFalse(userOff.canRetry) // crash flag is irrelevant when the user had it off
  }

  @Test
  fun reenableClearsOnlyTheSessionFlag() {
    val store = HashMap<String, Boolean>()
    val consent = PluginConsent(
      object : ConsentStore {
        override fun enabled(pkg: String) = store[pkg]
        override fun setEnabled(pkg: String, enabled: Boolean) { store[pkg] = enabled }
        override fun consented(pkg: String) = true
        override fun setConsented(pkg: String) {}
      },
    )
    val registry = dev.penlab.app.plugins.PluginRegistry(emptyList())
    val plugin = P("p.id")
    registry.replaceExternal(listOf(plugin))
    registry.disable(plugin, RuntimeException("boom"))
    assertTrue(registry.isSwitchedOff("p.id"))
    assertTrue(consent.isEnabled("x.pkg", trusted = true)) // crash did not touch the saved choice
    registry.reenable("p.id")
    assertFalse(registry.isSwitchedOff("p.id"))
    assertTrue(registry.isEnabled("p.id"))
  }
}
