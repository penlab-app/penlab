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
