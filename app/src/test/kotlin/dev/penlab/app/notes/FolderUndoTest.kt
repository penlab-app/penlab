package dev.penlab.app.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Deleting an empty folder can be undone: same path (name and parent) and the colour it had of its own. */
class FolderUndoTest {
  @get:Rule val tmp = TemporaryFolder()

  private val store by lazy { NotebookStore(LibraryLocation(tmp.newFolder("files"), File(tmp.root, "Documents/PenLab")) { false }) }

  @Test
  fun undoBringsBackNameParentAndColour() {
    store.createFolder("Fall 2026/Circuits")
    store.setFolderColor("Fall 2026/Circuits", 0xFF3366CC.toInt())
    store.setFolderColor("Fall 2026", 0xFFAA0000.toInt())
    val before = store.folders()

    assertTrue(store.deleteFolder("Fall 2026/Circuits"))
    assertFalse("Fall 2026/Circuits" in store.folders())
    assertTrue("the parent stays", "Fall 2026" in store.folders())

    store.restoreFolder("Fall 2026/Circuits", 0xFF3366CC.toInt())
    assertEquals(before, store.folders())
    assertEquals(0xFF3366CC.toInt(), store.folderColors()["Fall 2026/Circuits"])
    assertEquals("the parent's colour is untouched", 0xFFAA0000.toInt(), store.folderColors()["Fall 2026"])
  }

  @Test
  fun deletingDropsTheOwnColourSoANewFolderOfThatNameStartsPlain() {
    store.createFolder("Physics")
    store.setFolderColor("Physics", 0xFF00AA00.toInt())
    assertTrue(store.deleteFolder("Physics"))
    assertNull(store.folderColors()["Physics"])
    store.createFolder("Physics")
    assertNull(store.folderColors()["Physics"])
  }

  @Test
  fun aFolderWithoutItsOwnColourComesBackWithoutOne() {
    store.createFolder("A/B")
    store.setFolderColor("A", 0xFF112233.toInt()) // B only inherits this
    assertTrue(store.deleteFolder("A/B"))
    store.restoreFolder("A/B", null)
    assertTrue("A/B" in store.folders())
    assertNull(store.folderColors()["A/B"])
  }

  @Test
  fun aNonEmptyFolderIsNotDeleted() {
    store.createFolder("A/B")
    assertFalse(store.deleteFolder("A"))
    assertTrue("A/B" in store.folders())
  }
}
