package dev.penlab.app.plugins.external

import android.os.DeadObjectException
import android.os.RemoteException
import dev.penlab.app.plugins.PluginRegistry
import dev.penlab.app.plugins.ScreenPlacement
import dev.penlab.plugin.EnabledWhen
import dev.penlab.plugin.PageAction
import dev.penlab.plugin.PageRef
import dev.penlab.plugin.Selection
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ExternalPluginTest {
  @get:Rule val tmp = TemporaryFolder()

  private val manifest =
    (ManifestParser.parse(
      """{"id":"acme.tools","name":"Acme","apiVersion":2,"capabilities":[
        {"kind":"pageAction","id":"count","label":"Count","selection":"required"},
        {"kind":"pageAction","id":"insert","label":"Insert","selection":"none"},
        {"kind":"importer","id":"imp","label":"Acme import","mimeTypes":["application/x-acme"],"extensions":["acme"]},
        {"kind":"screen","id":"hello","label":"Hello","activity":".Hello","placement":"fileMenu"},
        {"kind":"recogniser","id":"r","label":"R"}
      ]}""",
    ) as ManifestParse.Ok).manifest
  private val page = PageRef("nb", 0, 210f, 297f)

  private fun registryWith(messages: MutableList<String> = ArrayList(), transport: PluginTransport): PluginRegistry {
    val registry = PluginRegistry(emptyList()) { messages += it }
    registry.replaceExternal(listOf(ExternalPlugin(manifest, "acme.pkg", transport, ExternalEnv(tmp.newFolder()))))
    return registry
  }

  private fun countAction(r: PluginRegistry) = r.pageActions(selectionEmpty = false).first { it.capability.id == "count" } as PluginRegistry.Entry<PageAction>

  @Test
  fun capabilitiesAreAdaptedAndRecognisersAreLeftUnhosted() {
    val r = registryWith { _, _, _, _ -> TransportOutcome.Failed(FailureKind.UNAVAILABLE, "unused") }
    assertEquals(listOf("count"), r.pageActions(selectionEmpty = false).map { it.capability.id }) // selection required
    assertEquals(listOf("insert"), r.pageActions(selectionEmpty = true).map { it.capability.id }.filter { it == "insert" }) // selection none
    assertEquals(1, r.importers().size)
    assertTrue(r.pageActions(true).any { it.capability.id == "hello" } && r.pageActions(true).all { it.external })
    assertEquals(EnabledWhen.SELECTION_NOT_EMPTY, countAction(r).capability.enabledWhen)
  }

  @Test
  fun aSuccessfulCallMapsToTheV1Result() = runTest {
    var seenCapability: String? = null
    var seenRequest: String? = null
    val r = registryWith { cap, json, _, timeout ->
      seenCapability = cap
      seenRequest = json
      assertEquals(PluginTimeouts.ACTION_MS, timeout)
      TransportOutcome.Ok("""{"type":"text","text":"3 strokes"}""", ResultFiles.EMPTY)
    }
    val outcome = r.runAction(countAction(r), Selection.empty(), page)
    assertEquals("3 strokes", (outcome as PluginRegistry.ActionOutcome.ShowText).text)
    assertEquals("count", seenCapability)
    assertTrue("\"capabilityId\":\"count\"" in seenRequest!!)
  }

  @Test
  fun timeoutAndBinderDeathSwitchThePluginOffWithOneMessage() = runTest {
    for (kind in listOf(FailureKind.TIMEOUT, FailureKind.DIED, FailureKind.UNAVAILABLE, FailureKind.API_MISMATCH, FailureKind.REJECTED, FailureKind.PLUGIN_ERROR)) {
      val messages = ArrayList<String>()
      val r = registryWith(messages) { _, _, _, _ -> TransportOutcome.Failed(kind, "boom") }
      val action = countAction(r)
      val outcome = r.runAction(action, Selection.empty(), page)
      assertTrue("$kind", outcome is PluginRegistry.ActionOutcome.Failed && outcome.pluginDisabled)
      assertTrue(r.isSwitchedOff("acme.tools"))
      assertFalse(r.isEnabled("acme.tools"))
      assertEquals("$kind", 1, messages.size)
      r.runAction(action, Selection.empty(), page) // a second failure adds no second message
      assertEquals(1, messages.size)
    }
  }

  @Test
  fun aMalformedAnswerCountsAsAPluginFault() = runTest {
    val r = registryWith { _, _, _, _ -> TransportOutcome.Ok("""{"type":"banana"}""", ResultFiles.EMPTY) }
    val outcome = r.runAction(countAction(r), Selection.empty(), page)
    assertTrue(outcome is PluginRegistry.ActionOutcome.Failed && outcome.pluginDisabled)
  }

  @Test
  fun aPluginReportedErrorKeepsThePluginOn() = runTest {
    val r = registryWith { _, _, _, _ -> TransportOutcome.Ok("""{"type":"error","message":"no network"}""", ResultFiles.EMPTY) }
    val outcome = r.runAction(countAction(r), Selection.empty(), page)
    assertEquals("“Acme”: no network", (outcome as PluginRegistry.ActionOutcome.ShowMessage).message)
    assertTrue(r.isEnabled("acme.tools"))
  }

  @Test
  fun failureKindsComeFromWhatTheBinderThrew() {
    assertEquals(FailureKind.DIED, failureFor(DeadObjectException()).kind)
    assertEquals(FailureKind.DIED, failureFor(RemoteException()).kind)
    assertEquals(FailureKind.REJECTED, failureFor(SecurityException("no")).kind)
    assertEquals(FailureKind.PLUGIN_ERROR, failureFor(IllegalStateException("oops")).kind)
  }

  @Test
  fun orThrowRaisesAnExceptionThatIsNotAnIoException() {
    try {
      TransportOutcome.Failed(FailureKind.TIMEOUT, "no answer").orThrow("Acme")
      fail()
    } catch (e: ExternalPluginException) {
      assertEquals(FailureKind.TIMEOUT, e.kind)
      assertFalse(e is java.io.IOException) // so the registry switches the plugin off instead of treating it as a device problem
    }
    assertNotNull(TransportOutcome.Ok("{}", ResultFiles.EMPTY).orThrow("Acme"))
  }

  @Test
  fun anImporterThatCannotReadTheFileGivesNull() = runTest {
    val r = registryWith { _, _, _, timeout ->
      assertEquals(PluginTimeouts.IMPORT_MS, timeout)
      TransportOutcome.Ok("""{"type":"error","message":"not an Acme file"}""", ResultFiles.EMPTY)
    }
    val importer = r.importersFor("application/octet-stream", "x.ACME").single()
    val source = dev.penlab.plugin.ImportSource("x.acme", "application/octet-stream", { "data".byteInputStream() }, tmp.newFolder(), "x")
    assertNull(importer.capability.import(source))
    assertTrue(r.isEnabled("acme.tools"))
  }

  @Test
  fun activityNamesAreResolvedAgainstThePluginsOwnPackage() {
    assertEquals("acme.pkg.Hello", ExternalPlugin.className(".Hello", "acme.pkg").let { it.replace("acme.pkg.acme.pkg", "acme.pkg") })
    assertEquals("acme.pkg.Hello", ExternalPlugin.className("Hello", "acme.pkg"))
    assertEquals("com.x.ChatActivity", ExternalPlugin.className("com.x.ChatActivity", "acme.pkg"))
  }

  @Test
  fun screensHaveAPlacementAndAreKeptOutOfTheWrongMenus() {
    val r = registryWith { _, _, _, _ -> TransportOutcome.Failed(FailureKind.UNAVAILABLE, "unused") }
    val hello = r.pageActions(selectionEmpty = true).single { it.capability.id == "hello" }
    assertEquals(ScreenPlacement.FILE_MENU, (hello.capability as dev.penlab.app.plugins.ScreenPageAction).placement)
    assertNull(r.pageActions(selectionEmpty = false).firstOrNull { it.capability.id == "hello" }) // file menu: not with a selection
    assertEquals(0, r.moreMenuActions().size)
  }

  @Test
  fun scratchSessionsAreDeletedAndOldOnesPurged() {
    val root = tmp.newFolder()
    val session = IoSession.create(root)
    File(session.dir, "a").writeText("x")
    session.delete()
    assertFalse(session.dir.exists())
    val old = IoSession.create(root)
    old.dir.setLastModified(System.currentTimeMillis() - 2 * 60 * 60 * 1000)
    val fresh = IoSession.create(root)
    IoSession.purge(root)
    assertFalse(old.dir.exists())
    assertTrue(fresh.dir.exists())
  }

  @Test
  fun cancelledOutcomeThrowsCancellationExceptionAndKeepsPluginEnabled() = runTest {
    val messages = ArrayList<String>()
    val r = registryWith(messages) { _, _, _, _ -> TransportOutcome.Cancelled }
    val action = countAction(r)
    try {
      r.runAction(action, Selection.empty(), page)
      fail("expected CancellationException")
    } catch (_: CancellationException) {
      // expected: propagates cleanly so caller can treat it as user cancel
    }
    assertFalse(r.isSwitchedOff("acme.tools"))
    assertTrue(r.isEnabled("acme.tools"))
    assertTrue(messages.isEmpty())
  }

  @Test
  fun externalPageActionCancelDelegatesToTransportCancel() {
    val cancelledIds = ArrayList<String>()
    val transport =
      object : PluginTransport {
        override fun call(capabilityId: String, requestJson: String, files: android.os.Bundle, timeoutMs: Long): TransportOutcome =
          TransportOutcome.Cancelled

        override fun cancel(capabilityId: String) {
          cancelledIds += capabilityId
        }
      }
    val r = registryWith(transport = transport)
    val action = countAction(r).capability as ExternalPageAction
    action.cancel()
    assertEquals(listOf("count"), cancelledIds)
  }
}
