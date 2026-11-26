package dev.penlab.app.plugins

import android.graphics.RectF
import dev.penlab.app.ink.PluginPageItem
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
}
