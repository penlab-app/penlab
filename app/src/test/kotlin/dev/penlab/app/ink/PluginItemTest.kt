package dev.penlab.app.ink

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Items the app does not understand (a plugin's, or from a newer version) must come back from a
 * save exactly as they were read, and the stored form of the app's own items must not change.
 */
class PluginItemTest {
  private fun page(vararg items: SavedItem) = SavedPage("BLANK", FloatArray(9) { if (it % 4 == 0) 1f else 0f }, emptyList(), items.toList())

  private fun bytes(page: SavedPage) = ByteArrayOutputStream().also { PageFile.write(page, it) }.toByteArray()

  /** Loads the file's items as the app does (SavedItem -> PageItem) and saves them again. */
  private fun loadAndSave(file: ByteArray): ByteArray {
    val read = PageFile.read(ByteArrayInputStream(file))
    val loaded = read.items.map { it.toItem() }
    return bytes(SavedPage(read.pageStyle, read.view, read.strokes, loaded.map { it.toSaved() }))
  }

  private val own = listOf(
    SavedItem(0, "a", 10f, 20f, 300f, 200f, 15f, "photo.jpg", 0, 0f),
    SavedItem(1, "b", 50f, 60f, 400f, 80f, 0f, "Shear force V = dM/dx\nsecond line", 0xFF1565C0.toInt(), 45f),
    SavedItem(2, "c", 40f, 50f, 600f, 90f, 0f, "equation.png", 0, 0f),
  )

  @Test fun anUnknownItemKindLoadsAndSavesByteIdentical() {
    val future = SavedItem(9, "x", 1f, 2f, 30f, 40f, 5f, "whatever a newer version put here\u0000\né", 0x12345678, 7.5f)
    val file = bytes(page(future))
    assertArrayEquals(file, loadAndSave(file))
  }

  @Test fun anUnknownKindKeepsItsPlaceBetweenKnownItems() {
    val file = bytes(page(own[0], SavedItem(42, "mid", 1f, 1f, 1f, 1f, 0f, "?", 3, 4f), own[1]))
    val result = PageFile.read(ByteArrayInputStream(loadAndSave(file)))
    assertEquals(listOf(0, 42, 1), result.items.map { it.kind })
    assertEquals(PageFile.read(ByteArrayInputStream(file)).items, result.items)
  }

  @Test fun anUnknownKindShowsAsAGreyBoxNamedAfterItsKind() {
    val item = SavedItem(9, "x", 1f, 2f, 30f, 40f, 5f, "c", 0, 0f).toItem() as PluginPageItem
    assertEquals("kind 9", item.typeId)
    assertEquals(0, item.payload.size)
  }

  @Test fun theAppsOwnItemsAreStoredExactlyAsBefore() {
    val file = bytes(page(*own.toTypedArray()))
    assertArrayEquals(file, loadAndSave(file))
    for (saved in own) assertEquals(saved, saved.toItem().toSaved())
    assertTrue(own.map { it.toItem() }.map { it::class.simpleName } == listOf("ImageItem", "TextItem", "ImageItem"))
  }

  @Test fun aPluginItemRoundTripsItsTypeAndEveryPayloadByte() {
    val payload = ByteArray(256) { it.toByte() } // all byte values, including ones that are not valid text
    val made = PluginPageItem.of("com.example.diagram", payload, 100f, 200f, 300f, 150f)
    assertEquals(PluginPageItem.KIND_PLUGIN, made.toSaved().kind)
    val file = bytes(page(made.toSaved(), own[1]))
    val back = PageFile.read(ByteArrayInputStream(loadAndSave(file))).items
    val item = back[0].toItem() as PluginPageItem
    assertEquals("com.example.diagram", item.typeId)
    assertArrayEquals(payload, item.payload)
    assertEquals(made.id, item.id)
    assertEquals(100f, item.cx, 0f)
    assertEquals(300f, item.width, 0f)
    assertArrayEquals(file, loadAndSave(file))
  }

  @Test fun anEmptyPayloadIsFine() {
    val item = PluginPageItem.of("p.empty", ByteArray(0), 0f, 0f, 10f, 10f)
    assertEquals("p.empty", item.typeId)
    assertEquals(0, item.payload.size)
    assertEquals(item, item.toSaved().toItem())
  }

  @Test fun aBadTypeIdIsRefused() {
    for (bad in listOf("", "two\nlines")) {
      var refused = false
      try {
        PluginPageItem.of(bad, ByteArray(0), 0f, 0f, 1f, 1f)
      } catch (e: IllegalArgumentException) {
        refused = true
      }
      assertTrue("'$bad'", refused)
    }
  }
}
