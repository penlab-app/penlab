package dev.penlab.app.plugins

import dev.penlab.app.plugins.pdf.PdfPlugin
import dev.penlab.plugin.API_VERSION
import dev.penlab.plugin.Capability
import dev.penlab.plugin.EnabledWhen
import dev.penlab.plugin.ExportSource
import dev.penlab.plugin.Exporter
import dev.penlab.plugin.ImportSource
import dev.penlab.plugin.ImportedNotebook
import dev.penlab.plugin.Importer
import dev.penlab.plugin.PageAction
import dev.penlab.plugin.PageRef
import dev.penlab.plugin.Plugin
import dev.penlab.plugin.Selection
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.penlab.plugin.Result as PluginResult

@OptIn(ExperimentalCoroutinesApi::class)
class PluginRegistryTest {
  private class FakePlugin(
    override val id: String,
    override val apiVersion: Int = API_VERSION,
    private val caps: () -> List<Capability> = { emptyList() },
  ) : Plugin {
    override val name = "Fake $id"
    override fun capabilities() = caps()
  }

  private class FakeAction(
    override val id: String = "act",
    override val label: String = "Do it",
    override val enabledWhen: EnabledWhen = EnabledWhen.ALWAYS,
    val body: suspend () -> PluginResult = { PluginResult.None },
  ) : PageAction {
    override suspend fun run(selection: Selection, page: PageRef) = body()
  }

  private class FakeImporter(override val mimeTypes: List<String>) : Importer {
    override val label = "fake importer"
    override suspend fun import(source: ImportSource): ImportedNotebook? = null
  }

  private class FakeExporter(override val mimeType: String, val body: suspend () -> Unit = {}) : Exporter {
    override val label = "fake exporter"
    override val fileExtension = "x"
    override suspend fun export(source: ExportSource, out: OutputStream) = body()
  }

  private val page = PageRef("nb", 0, 210f, 297f)
  private val messages = ArrayList<String>()
  private fun registry(vararg plugins: Plugin) = PluginRegistry(plugins.toList()) { messages += it }

  // ---- loading -------------------------------------------------------------------------

  @Test fun onlyPluginsWithTheSameApiVersionAreLoaded() {
    val r = registry(FakePlugin("a"), FakePlugin("old", apiVersion = API_VERSION + 1), FakePlugin("older", apiVersion = 0))
    assertEquals(listOf("a"), r.plugins.map { it.id })
    assertEquals(2, r.skipped.size)
    assertTrue(messages.isEmpty()) // a version mismatch is not a crash: nothing to tell the user
  }

  @Test fun aDuplicateIdIsIgnored() {
    val r = registry(FakePlugin("a", caps = { listOf(FakeAction(label = "first")) }), FakePlugin("a", caps = { listOf(FakeAction(label = "second")) }))
    assertEquals(listOf("first"), r.pageActions(true).map { it.capability.label })
  }

  @Test fun aPluginWhoseCapabilitiesThrowIsSwitchedOffAtLoadAndTheRestLoad() {
    val r = registry(FakePlugin("bad", caps = { error("boom") }), FakePlugin("good", caps = { listOf(FakeAction()) }))
    assertEquals(listOf("good"), r.plugins.map { it.id })
    assertEquals(1, messages.size)
    assertTrue(messages[0].contains("Fake bad"))
    assertEquals(1, r.pageActions(true).size)
  }

  // ---- filtering -----------------------------------------------------------------------

  @Test fun importersMatchByMimeTypeWithWildcardsAndAnyCase() {
    val r = registry(FakePlugin("p", caps = { listOf(FakeImporter(listOf("application/pdf")), FakeImporter(listOf("image/*")), FakeImporter(listOf("*/*"))) }))
    assertEquals(2, r.importers("image/png").size)
    assertEquals(2, r.importers("APPLICATION/PDF").size) // exact (any case) and */*
    assertEquals(1, r.importers("text/plain").size) // only */*
    assertEquals(3, r.importers().size)
    assertEquals(listOf("application/pdf", "image/*", "*/*"), r.importMimeTypes())
  }

  @Test fun exporterIsFoundByMimeType() {
    val r = registry(FakePlugin("p", caps = { listOf(FakeExporter("application/pdf"), FakeExporter("image/png")) }))
    assertEquals("image/png", r.exporter("IMAGE/PNG")?.capability?.mimeType)
    assertNull(r.exporter("text/plain"))
    assertEquals(2, r.exporters().size)
  }

  @Test fun pageActionsFollowTheirEnabledWhen() {
    val r = registry(
      FakePlugin(
        "p",
        caps = {
          listOf(
            FakeAction("always", "A", EnabledWhen.ALWAYS),
            FakeAction("empty", "E", EnabledWhen.SELECTION_EMPTY),
            FakeAction("sel", "S", EnabledWhen.SELECTION_NOT_EMPTY),
          )
        },
      ),
    )
    assertEquals(listOf("A", "E"), r.pageActions(selectionEmpty = true).map { it.capability.label })
    assertEquals(listOf("A", "S"), r.pageActions(selectionEmpty = false).map { it.capability.label })
  }

  @Test fun noPluginsMeansNoActionsImportersOrExporters() {
    val r = registry()
    assertTrue(r.pageActions(true).isEmpty() && r.pageActions(false).isEmpty() && r.importers().isEmpty() && r.exporters().isEmpty())
  }

  // ---- PageAction host logic -----------------------------------------------------------

  @Test fun runActionMapsEveryResultKind() = runTest {
    fun outcome(result: PluginResult): PluginRegistry.ActionOutcome {
      val r = registry(FakePlugin("p", caps = { listOf(FakeAction(body = { result })) }))
      return kotlinx.coroutines.runBlocking { r.runAction(r.pageActions(true).single(), Selection.empty(), page) }
    }
    assertTrue(outcome(PluginResult.Items(listOf(dev.penlab.plugin.PluginItem("x", android.graphics.RectF(), ByteArray(1))))) is PluginRegistry.ActionOutcome.Place)
    assertEquals("hi", (outcome(PluginResult.Text("hi")) as PluginRegistry.ActionOutcome.ShowText).text)
    assertEquals("done", (outcome(PluginResult.Message("done")) as PluginRegistry.ActionOutcome.ShowMessage).message)
    assertEquals(PluginRegistry.ActionOutcome.Nothing, outcome(PluginResult.None))
    assertEquals("New name", (outcome(PluginResult.Rename("New name")) as PluginRegistry.ActionOutcome.Rename).title)
    assertEquals(PluginRegistry.ActionOutcome.Nothing, outcome(PluginResult.Items(emptyList()))) // nothing to place
    // ink alone is something to place
    val ink = dev.penlab.plugin.PluginStroke(floatArrayOf(0f), floatArrayOf(0f), floatArrayOf(0.5f), longArrayOf(0), dev.penlab.plugin.Tool.PEN, 0, 0.4f)
    val place = outcome(PluginResult.Items(emptyList(), listOf(ink))) as PluginRegistry.ActionOutcome.Place
    assertEquals(1, place.strokes.size)
  }

  @Test fun theActionReceivesTheSelectionAndPageItWasGiven() = runTest {
    var seen: Pair<Selection, PageRef>? = null
    val sel = Selection.empty()
    val r = registry(FakePlugin("p", caps = { listOf(object : PageAction by FakeAction() { override suspend fun run(selection: Selection, page: PageRef): PluginResult { seen = selection to page; return PluginResult.None } }) }))
    r.runAction(r.pageActions(true).single(), sel, page)
    assertTrue(seen!!.first === sel && seen!!.second === page)
  }

  // ---- crash isolation -----------------------------------------------------------------

  @Test fun aThrowingActionSwitchesItsPluginOffOnceAndTheAppCarriesOn() = runTest {
    val bad = FakePlugin("bad", caps = { listOf(FakeAction(label = "bad", body = { throw IllegalStateException("boom") })) })
    val good = FakePlugin("good", caps = { listOf(FakeAction(label = "good", body = { PluginResult.Message("ok") })) })
    val r = registry(bad, good)
    val badEntry = r.pageActions(true).first { it.capability.label == "bad" }

    val first = r.runAction(badEntry, Selection.empty(), page)
    assertTrue(first is PluginRegistry.ActionOutcome.Failed && first.pluginDisabled)
    assertFalse(r.isEnabled("bad"))
    assertEquals(1, messages.size)
    assertEquals(listOf("good"), r.pageActions(true).map { it.capability.label }) // gone from the menus

    r.runAction(badEntry, Selection.empty(), page) // a stale button tapped again
    assertEquals(1, messages.size) // still one message

    val other = r.runAction(r.pageActions(true).single(), Selection.empty(), page)
    assertEquals("ok", (other as PluginRegistry.ActionOutcome.ShowMessage).message)
    assertTrue(r.isEnabled("good"))
  }

  @Test fun anErrorThatIsNotAnExceptionIsCaughtToo() = runTest {
    val r = registry(FakePlugin("p", caps = { listOf(FakeAction(body = { throw NoSuchMethodError("api mismatch") })) }))
    val out = r.runAction(r.pageActions(true).single(), Selection.empty(), page)
    assertTrue(out is PluginRegistry.ActionOutcome.Failed && out.pluginDisabled)
  }

  @Test fun ioFailuresFailTheCallButDoNotSwitchThePluginOff() = runTest {
    val r = registry(FakePlugin("p", caps = { listOf(FakeAction(body = { throw IOException("disk full") })) }))
    val out = r.runAction(r.pageActions(true).single(), Selection.empty(), page)
    assertTrue(out is PluginRegistry.ActionOutcome.Failed && !out.pluginDisabled)
    assertTrue(r.isEnabled("p"))
    assertTrue(messages.isEmpty())
  }

  @Test fun cancellationPassesThroughUntouched() = runTest {
    val r = registry(FakePlugin("p"))
    var cancelled = false
    try {
      r.guarded(r.plugins.single()) { throw CancellationException("stop") }
    } catch (e: CancellationException) {
      cancelled = true
    }
    assertTrue(cancelled)
    assertTrue(r.isEnabled("p"))
  }

  @Test fun guardedBlockingReturnsOkOrFailed() {
    val r = registry(FakePlugin("p"))
    val plugin = r.plugins.single()
    assertEquals(5, (r.guardedBlocking(plugin) { 5 } as PluginRegistry.Call.Ok).value)
    assertTrue(r.guardedBlocking(plugin) { error("x") } is PluginRegistry.Call.Failed)
    assertFalse(r.isEnabled("p"))
  }

  @Test fun aThrowingExporterIsSwitchedOff() {
    val r = registry(FakePlugin("p", caps = { listOf(FakeExporter("application/pdf") { throw IllegalStateException("bad file") }) }))
    val e = r.exporter("application/pdf")!!
    val source = object : ExportSource {
      override val notebookId = "n"; override val title = "t"; override val pageWidthMm = 1f; override val pageHeightMm = 1f
      override val pageCount = 1; override val dark = false
      override fun strokes() = emptyList<dev.penlab.plugin.PluginStroke>()
      override fun items() = emptyList<dev.penlab.plugin.PluginItem>()
    }
    val call = r.guardedBlocking(e.plugin) { e.capability.export(source, java.io.ByteArrayOutputStream()) }
    assertTrue(call is PluginRegistry.Call.Failed && call.pluginDisabled)
    assertNull(r.exporter("application/pdf"))
  }

  // ---- the built-in PDF plugin ---------------------------------------------------------

  @Test fun pdfPluginIsBuiltInAndRegisteredForPdf() {
    val r = registry(*BuiltInPlugins.all().toTypedArray())
    assertEquals(listOf(PdfPlugin.ID), r.plugins.map { it.id })
    assertEquals(1, r.importers("application/pdf").size)
    assertEquals(0, r.importers("image/png").size)
    assertEquals(listOf("application/pdf"), r.importMimeTypes())
    val exporter = r.exporter("application/pdf")!!.capability
    assertEquals("pdf", exporter.fileExtension)
    // With only built-in plugins there are no page actions: the menus look as before.
    assertTrue(r.pageActions(true).isEmpty() && r.pageActions(false).isEmpty())
  }

  @Test fun pdfImporterReturnsNullForAnUnreadableFile() {
    val work = File.createTempFile("pdfimport", "").apply { delete(); mkdirs() }
    try {
      val importer = PdfPlugin.PdfImporter
      val unreadable = ImportSource("x.pdf", "application/pdf", { throw IOException("gone") }, work, "x")
      assertNull(kotlinx.coroutines.runBlocking { importer.import(unreadable) })
      val garbage = ImportSource("x.pdf", "application/pdf", { "not a pdf".byteInputStream() }, work, "x")
      assertNull(kotlinx.coroutines.runBlocking { importer.import(garbage) })
    } finally {
      work.deleteRecursively()
    }
  }

  @Test fun pdfExporterRefusesASourceThatIsNotTheAppsOwn() {
    val foreign = object : ExportSource {
      override val notebookId = "n"; override val title = "t"; override val pageWidthMm = 1f; override val pageHeightMm = 1f
      override val pageCount = 1; override val dark = false
      override fun strokes() = emptyList<dev.penlab.plugin.PluginStroke>()
      override fun items() = emptyList<dev.penlab.plugin.PluginItem>()
    }
    var failed = false
    try {
      kotlinx.coroutines.runBlocking { PdfPlugin.PdfWriter.export(foreign, java.io.ByteArrayOutputStream()) }
    } catch (e: IllegalArgumentException) {
      failed = true
    }
    assertTrue(failed)
  }
}
