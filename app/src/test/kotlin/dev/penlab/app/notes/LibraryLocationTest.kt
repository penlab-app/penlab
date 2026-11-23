package dev.penlab.app.notes

import dev.penlab.app.ink.PageStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The one library root, and that NotebookStore's atomic writes and renames work under it. */
class LibraryLocationTest {
  @get:Rule val tmp = TemporaryFolder()

  private var granted = false
  private val location by lazy { LibraryLocation(tmp.newFolder("files"), File(tmp.root, "Documents/PenLab")) { granted } }

  @Test
  fun usesThePrivateLibraryUntilAllFilesAccessIsGranted() {
    assertFalse(location.isShared)
    assertEquals(File(location.privateDir, "library"), location.root)
    assertTrue(location.root.isDirectory)

    granted = true
    assertEquals("cached until refresh() is called", location.privateLibrary, location.root)
    assertTrue(location.refresh())
    assertTrue(location.isShared)
    assertEquals(location.sharedLibrary, location.root)
    assertTrue(location.sharedLibrary.isDirectory)
    assertFalse("nothing changed", location.refresh())

    granted = false
    assertTrue(location.refresh())
    assertEquals(location.privateLibrary, location.root)
  }

  @Test
  fun fallsBackToPrivateWhenTheSharedFolderCannotBeMade() {
    granted = true
    // A plain file where Documents should be: mkdirs fails.
    File(tmp.root, "Documents").writeText("not a folder")
    assertFalse(location.refresh())
    assertFalse(location.isShared)
    assertEquals(location.privateLibrary, location.root)
  }

  @Test
  fun notebooksSaveListTrashAndRestoreUnderTheRoot() {
    granted = true
    location.refresh()
    val store = NotebookStore(location)
    val made = store.create("Lecture 1", "Mechanics", NotebookKind.A4, PageStyle.BLANK)
    store.write(made.copy(title = "Lecture 1 (edited)", pageCount = 3)) // AtomicFile write, replacing the first
    assertTrue(File(location.sharedLibrary, "${made.id}/notebook.properties").isFile)
    assertTrue("no temp or backup file left", File(location.sharedLibrary, made.id).list()!!.none { it.endsWith(".new") || it.endsWith(".bak") })

    val listed = store.list().single()
    assertEquals("Lecture 1 (edited)", listed.title)
    assertEquals(3, listed.pageCount)
    assertEquals(listOf("Mechanics"), store.folders())
    assertEquals(File(location.sharedLibrary, "${made.id}/images"), store.imagesDir(made))

    assertTrue(store.delete(made)) // directory rename into .trash
    assertTrue(store.list().isEmpty())
    assertTrue(File(location.sharedLibrary, ".trash/${made.id}/notebook.properties").isFile)
    assertTrue(store.restore(made)) // and back
    assertEquals(made.id, store.list().single().id)
  }

  @Test
  fun notebooksAlreadyInTheSharedFolderAppearAfterAReinstall() {
    // First "install": a notebook is saved in Documents/PenLab.
    granted = true
    location.refresh()
    val id = NotebookStore(location).create("Kept", "", NotebookKind.INFINITE, PageStyle.DOTTED).id

    // Reinstall: the private files directory is new and empty, the shared folder is not.
    val fresh = LibraryLocation(tmp.newFolder("files-after-reinstall"), location.sharedLibrary) { true }
    assertEquals(listOf(id), NotebookStore(fresh).list().map { it.id })
  }
}
