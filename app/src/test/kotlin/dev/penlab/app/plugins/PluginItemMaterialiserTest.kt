package dev.penlab.app.plugins

import android.graphics.RectF
import dev.penlab.app.ink.ImageItem
import dev.penlab.app.ink.PageItem
import dev.penlab.app.ink.PluginPageItem
import dev.penlab.app.ink.StrokeHistory
import dev.penlab.plugin.PluginItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginItemMaterialiserTest {
  @get:Rule val tmp = TemporaryFolder()

  @Test
  fun aPluginsOwnTypeStaysAPluginItemAndKeepsItsBytes() {
    val staged = PluginItemMaterialiser.stage(listOf(PluginItem("acme.thing", RectF(0f, 0f, 10f, 10f), byteArrayOf(1, 2, 3))), tmp.newFolder())
    assertEquals(1, staged.size)
    val page = PluginItemMaterialiser.toPageItem(staged.single(), 10f) as PluginPageItem
    assertEquals("acme.thing", page.typeId)
    assertEquals(listOf<Byte>(1, 2, 3), page.payload.toList())
  }

  @Test
  fun pictureBytesThatAreNotAnImageAreDroppedAndWriteNothing() {
    val dir = tmp.newFolder("images")
    val staged = PluginItemMaterialiser.stage(listOf(PluginItem("penlab.image", RectF(0f, 0f, 10f, 10f), byteArrayOf(1, 2, 3))), dir)
    assertTrue(staged.isEmpty())
    assertEquals(0, dir.listFiles()!!.size)
  }

  @Test
  fun picturesAreDroppedWhenThereIsNoImagesFolder() {
    assertTrue(PluginItemMaterialiser.stage(listOf(PluginItem("penlab.ink-image", RectF(0f, 0f, 10f, 10f), byteArrayOf(1))), null).isEmpty())
  }

  // ---- items format 1: replacing a selected item ---------------------------------------------

  private fun image(id: String, cx: Float = 100f, cy: Float = 200f, w: Float = 400f, h: Float = 300f, rotation: Float = 0f) = ImageItem(id, "$id.png", cx, cy, w, h, rotation)

  @Test
  fun aResultBoxWithAreaIsUsedAsGivenAndTurned() {
    val f = PluginItemMaterialiser.pictureFrame(10f, 20f, 30f, 60f, 25f, 10f, 5f, 7f, 1.5f, image("old"))
    assertEquals(PluginItemMaterialiser.Frame(205f, 407f, 200f, 400f, 25f), f)
  }

  @Test
  fun anEmptyBoxThatReplacesAnItemKeepsItsCentreAndTurnAndFitsInsideIt() {
    val old = image("old", cx = 100f, cy = 200f, w = 400f, h = 300f, rotation = 30f)
    val wide = PluginItemMaterialiser.pictureFrame(0f, 0f, 0f, 0f, 0f, 10f, 0f, 0f, 2f, old) // 2:1 is wider than the 4:3 box
    assertEquals(PluginItemMaterialiser.Frame(100f, 200f, 400f, 200f, 30f), wide)
    val tall = PluginItemMaterialiser.pictureFrame(0f, 0f, 0f, 0f, 0f, 10f, 0f, 0f, 1f, old)
    assertEquals(PluginItemMaterialiser.Frame(100f, 200f, 300f, 300f, 30f), tall)
  }

  @Test
  fun anEmptyBoxWithNothingToReplaceGetsTheDefaultWidth() {
    val f = PluginItemMaterialiser.pictureFrame(1f, 1f, 1f, 1f, 0f, 10f, 0f, 0f, 2f, null)
    assertEquals(600f, f.width, 0f)
    assertEquals(300f, f.height, 0f)
  }

  @Test
  fun placeSwapsAReplacedItemInItsOwnStackingSlotAndAppendsTheRest() {
    val current = listOf(image("a"), image("photo"), image("c"))
    val cutout = image("cut")
    val extra = image("extra")
    val placement = PluginItemMaterialiser.place(current, listOf(cutout to "photo", extra to null))
    assertEquals(listOf("a", "cut", "c", "extra"), placement.items.map { it.id })
    assertEquals(mapOf("photo" to "cut"), placement.swapped)
  }

  @Test
  fun placeTreatsAnUnknownOrAlreadyReplacedIdAsAnAdd() {
    val current = listOf(image("photo"))
    val placement = PluginItemMaterialiser.place(current, listOf(image("one") to "photo", image("two") to "photo", image("three") to "gone"))
    assertEquals(listOf("one", "two", "three"), placement.items.map { it.id })
    assertEquals(mapOf("photo" to "one"), placement.swapped)
  }

  @Test
  fun aReplaceIsOneUndoStepTogetherWithTheStrokesAddedWithIt() {
    // The canvas does exactly this: one history group holding the items change (a saved state) and the strokes.
    var items: List<PageItem> = listOf(image("a"), image("photo"))
    val history = StrokeHistory<String>()
    history.stateHandler = { _, state ->
      @Suppress("UNCHECKED_CAST")
      items = state as List<PageItem>
    }
    val before = items
    history.grouped {
      items = PluginItemMaterialiser.place(items, listOf(image("cut") to "photo", image("note") to null)).items
      history.recordState(6, before, items)
      history.add("stroke")
    }
    assertEquals(listOf("a", "cut", "note"), items.map { it.id })
    assertEquals(listOf("stroke"), history.strokes)

    assertTrue(history.undo())
    assertEquals(listOf("a", "photo"), items.map { it.id })
    assertEquals(emptyList<String>(), history.strokes)
    assertTrue(!history.undo()) // nothing else to undo: it was one step

    assertTrue(history.redo())
    assertEquals(listOf("a", "cut", "note"), items.map { it.id })
    assertEquals(listOf("stroke"), history.strokes)
  }
}
