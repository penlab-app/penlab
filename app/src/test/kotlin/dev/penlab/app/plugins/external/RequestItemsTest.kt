package dev.penlab.app.plugins.external

import android.graphics.RectF
import dev.penlab.app.plugins.PluginRegistry
import dev.penlab.plugin.PageAction
import dev.penlab.plugin.PageRef
import dev.penlab.plugin.PluginItem
import dev.penlab.plugin.Selection
import dev.penlab.sdk.PenLabBridge
import dev.penlab.sdk.PluginRequest
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Building the request's items (bridge items format 1) and honouring `replaces` only for what the request listed. */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestItemsTest {
  @get:Rule val tmp = TemporaryFolder()

  private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

  private fun photo(id: String, type: String = "penlab.image", rotation: Float = 0f) = PluginItem(type, RectF(), ByteArray(0), id = id, rotationDeg = rotation, unrotatedBoundsMm = RectF())

  private fun selection(items: List<PluginItem>, files: Map<String, File> = emptyMap()) = Selection(emptyList(), items, RectF(), null, files)

  private fun file(name: String, bytes: ByteArray = png) = tmp.newFile(name).also { it.writeBytes(bytes) }

  @Test
  fun photosAreSentAsRequestFilesWithTheirIdTurnAndMimeType() {
    val dir = tmp.newFolder("io")
    val info = RequestFiles.writeSelection(dir, selection(listOf(photo("a", rotation = 15f), photo("ink", "penlab.ink-image")), mapOf("a" to file("a.png"), "ink" to file("ink.png"))))!!
    assertEquals(2, info.itemCount)
    val a = info.items[0]
    assertEquals("a", a.id)
    assertEquals("penlab.image", a.typeId)
    assertEquals(15f, a.rotationDeg, 0f)
    assertEquals("item-0", a.file)
    assertEquals("image/png", a.mimeType)
    assertArrayEquals(png, File(dir, "item-0").readBytes()) // not re-encoded
    assertEquals("item-1", info.items[1].file)
    assertEquals("penlab.ink-image", info.items[1].typeId)
  }

  @Test
  fun textAndPluginItemsCarryNoFileAndAnItemWithoutAnIdIsNotListed() {
    val items = listOf(PluginItem("penlab.text", RectF(), "héllo".toByteArray(), id = "t"), PluginItem("acme.thing", RectF(), byteArrayOf(1), id = "p"), PluginItem("penlab.text", RectF(), "x".toByteArray()))
    val info = RequestFiles.writeSelection(tmp.newFolder("io"), selection(items))!!
    assertEquals(listOf("t", "p"), info.items.map { it.id })
    assertEquals("héllo", info.items[0].text)
    assertNull(info.items[0].file)
    assertNull(info.items[1].text)
    assertNull(info.items[1].file)
  }

  @Test
  fun atMostEightPicturesAreSentAndTheRestAreListedWithoutAFile() {
    val ids = (1..10).map { "p$it" }
    val files = ids.associateWith { file("$it.png") }
    val info = RequestFiles.writeSelection(tmp.newFolder("io"), selection(ids.map { photo(it) }, files))!!
    assertEquals(10, info.items.size)
    assertEquals(PenLabBridge.MAX_ITEM_FILES, info.items.count { it.file != null })
    assertNull(info.items[8].file)
    assertNull(info.items[9].file)
  }

  @Test
  fun aPictureThatCannotBeReadIsListedWithoutAFileAndDoesNotUseUpANumber() {
    val info = RequestFiles.writeSelection(tmp.newFolder("io"), selection(listOf(photo("gone"), photo("ok")), mapOf("gone" to File(tmp.root, "missing.png"), "ok" to file("ok.png"))))!!
    assertNull(info.items[0].file)
    assertEquals("item-0", info.items[1].file)
  }

  @Test
  fun screensGetNoItems() {
    val info = RequestFiles.writeSelection(tmp.newFolder("io"), selection(listOf(photo("a")), mapOf("a" to file("a.png"))), withItems = false)!!
    assertTrue(info.items.isEmpty())
    assertEquals(1, info.itemCount)
  }

  // ---- through a page action ----------------------------------------------------------------

  private val manifest =
    (ManifestParser.parse("""{"id":"acme.tools","name":"Acme","apiVersion":2,"capabilities":[{"kind":"pageAction","id":"cut","label":"Cut","selection":"required"}]}""") as ManifestParse.Ok).manifest

  private fun action(transport: PluginTransport): Pair<PluginRegistry, PluginRegistry.Entry<PageAction>> {
    val registry = PluginRegistry(emptyList()) { }
    registry.replaceExternal(listOf(ExternalPlugin(manifest, "acme.pkg", transport, ExternalEnv(tmp.newFolder()))))
    @Suppress("UNCHECKED_CAST")
    return registry to (registry.pageActions(selectionEmpty = false).first { it.capability.id == "cut" } as PluginRegistry.Entry<PageAction>)
  }

  @Test
  fun theRequestAdvertisesItemsFormatAndListsTheSelectedItems() = runTest {
    var json: String? = null
    val (registry, entry) = action { _, requestJson, _, _ -> json = requestJson; TransportOutcome.Ok("""{"type":"none"}""", ResultFiles.EMPTY) }
    registry.runAction(entry, selection(listOf(PluginItem("penlab.text", RectF(), "hi".toByteArray(), id = "t1"))), PageRef("nb", 0, 210f, 297f))
    val request = PluginRequest.fromJson(json!!)
    assertEquals(PenLabBridge.ITEMS_FORMAT, request.itemsFormat)
    assertEquals(listOf("t1"), request.selectionItems.map { it.id })
    assertEquals("hi", request.selectionItems.single().text)
  }

  @Test
  fun replacesIsHonouredOnlyForListedItems() = runTest {
    val result = """{"type":"items","items":[
      {"typeId":"penlab.text","boundsMm":[0,0,5,5],"text":"new","replaces":"t1","rotationDeg":10},
      {"typeId":"penlab.text","boundsMm":[0,0,5,5],"text":"other","replaces":"someone-elses"}]}"""
    val (registry, entry) = action { _, _, _, _ -> TransportOutcome.Ok(result, ResultFiles.EMPTY) }
    val outcome = registry.runAction(entry, selection(listOf(PluginItem("penlab.text", RectF(), "hi".toByteArray(), id = "t1"))), PageRef("nb", 0, 210f, 297f))
    val placed = (outcome as PluginRegistry.ActionOutcome.Place).items
    assertEquals(listOf("t1", null), placed.map { it.replaces })
    assertEquals(10f, placed[0].rotationDeg, 0f)
  }
}
