package dev.penlab.app.plugins

import dev.penlab.plugin.API_VERSION
import dev.penlab.plugin.Capability
import dev.penlab.plugin.EnabledWhen
import dev.penlab.plugin.ImportSource
import dev.penlab.plugin.ImportedNotebook
import dev.penlab.plugin.PageAction
import dev.penlab.plugin.PageRef
import dev.penlab.plugin.Plugin
import dev.penlab.plugin.Selection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.penlab.plugin.Result as PluginResult

/** The registry's handling of plugin apps found at run time (rescans, ids, extension routing, screens). */
@OptIn(ExperimentalCoroutinesApi::class)
class PluginRegistryExternalTest {
  private class P(override val id: String, private val caps: List<Capability> = emptyList()) : Plugin {
    override val name = "P $id"
    override val apiVersion = API_VERSION
    override fun capabilities() = caps
  }

  private class Act(override val id: String, val failing: Boolean = false) : PageAction {
    override val label = id
    override suspend fun run(selection: Selection, page: PageRef): PluginResult = if (failing) throw IllegalStateException("x") else PluginResult.None
  }

  private class Screen(override val id: String, override val placement: ScreenPlacement) : ScreenPageAction {
    override val label = id
    override val enabledWhen = EnabledWhen.ALWAYS
    override suspend fun run(selection: Selection, page: PageRef): PluginResult = PluginResult.None
  }

  private class ExtImporter(override val extensions: List<String>, override val mimeTypes: List<String> = emptyList()) : ExtensionImporter {
    override val label = "ext"
    override suspend fun import(source: ImportSource): ImportedNotebook? = null
  }

  private val page = PageRef("nb", 0, 210f, 297f)

  @Test
  fun aRescanKeepsWhatWasSwitchedOffThisSession() = runTest {
    val registry = PluginRegistry(emptyList())
    val bad = P("bad", listOf(Act("go", failing = true)))
    registry.replaceExternal(listOf(bad))
    registry.runAction(registry.pageActions(true).single(), Selection.empty(), page)
    assertTrue(registry.isSwitchedOff("bad"))
    registry.replaceExternal(listOf(P("bad", listOf(Act("go"))), P("good", listOf(Act("ok")))))
    assertFalse(registry.isEnabled("bad")) // still off after the rescan (e.g. on resume)
    assertEquals(listOf("ok"), registry.pageActions(true).map { it.capability.id })
  }

  @Test
  fun builtInPluginsKeepTheirIdsAndComeFirst() {
    val registry = PluginRegistry(listOf(P("dev.penlab.pdf", listOf(Act("builtin")))))
    registry.replaceExternal(listOf(P("dev.penlab.pdf", listOf(Act("imposter"))), P("other", listOf(Act("fine")))))
    assertEquals(listOf("builtin", "fine"), registry.pageActions(true).map { it.capability.id })
    assertEquals(listOf(false, true), registry.pageActions(true).map { it.external })
    assertTrue(registry.skipped.any { "duplicate id" in it })
  }

  @Test
  fun replacingTheExternalSetRemovesWhatIsGone() {
    val registry = PluginRegistry(emptyList())
    registry.replaceExternal(listOf(P("a", listOf(Act("one")))))
    assertEquals(1, registry.pageActions(true).size)
    registry.replaceExternal(emptyList()) // the plugin app was uninstalled
    assertEquals(0, registry.pageActions(true).size)
    assertEquals(0, registry.plugins.size)
  }

  @Test
  fun extensionImportersAreFoundByFileNameButNeverByTheFallback() {
    val registry = PluginRegistry(emptyList())
    registry.replaceExternal(listOf(P("a", listOf(ExtImporter(listOf("sdocx"))))))
    assertEquals(1, registry.importersFor("application/octet-stream", "Note.SDocX").size)
    assertEquals(0, registry.importersFor("application/octet-stream", "Note.pdf").size)
    assertEquals(0, registry.importersFor("application/octet-stream", "noextension").size)
    assertEquals(0, registry.builtInImporters().size) // an unclaimed file is never offered to a plugin app
    assertEquals(1, registry.importers().size)
  }

  @Test
  fun importersForStillMatchesByMimeLikeBefore() {
    val registry = PluginRegistry(listOf(P("a", listOf(ExtImporter(emptyList(), listOf("application/pdf"))))))
    assertEquals(1, registry.importersFor("application/pdf", "x.pdf").size)
    assertEquals(1, registry.builtInImporters().size)
  }

  @Test
  fun moreMenuScreensAreListedSeparately() {
    val registry = PluginRegistry(emptyList())
    registry.replaceExternal(listOf(P("a", listOf(Screen("more", ScreenPlacement.MORE_MENU), Screen("sel", ScreenPlacement.SELECTION_MENU), Act("act")))))
    assertEquals(listOf("more"), registry.moreMenuActions().map { it.capability.id })
    assertEquals(listOf("sel", "act"), registry.pageActions(false).map { it.capability.id })
    assertEquals(listOf("sel", "act"), registry.pageActions(true).map { it.capability.id }) // screens here report ALWAYS in this fake
  }
}
