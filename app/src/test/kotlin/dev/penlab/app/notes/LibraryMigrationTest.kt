package dev.penlab.app.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibraryMigrationTest {
  @get:Rule val tmp = TemporaryFolder()

  private val source by lazy { tmp.newFolder("private") }
  private val target by lazy { tmp.newFolder("shared") }

  /** A notebook folder with a few files, like the real thing. */
  private fun notebook(library: File, id: String, strokes: String = "strokes of $id") {
    File(library, id).apply {
      mkdirs()
      File(this, "notebook.properties").writeText("title=$id")
      File(this, "notebook.graphite").writeText(strokes)
      File(this, "images").mkdirs()
      File(this, "images/photo.jpg").writeText("photo $id")
    }
  }

  @Test
  fun planCopiesEveryNotebookButSkipsDotFolders() {
    notebook(source, "a")
    notebook(source, "b")
    File(source, ".trash/c").mkdirs()
    File(source, ".migrating-x").mkdirs()
    val plan = LibraryMigration.plan(source, target)
    assertEquals(listOf("a", "b"), plan.copy.map { it.targetId })
    assertTrue(plan.copy.none { it.renamed })
    assertTrue(plan.alreadyThere.isEmpty())
  }

  @Test
  fun planRenamesAClashingIdInsteadOfOverwriting() {
    notebook(source, "a", "private strokes")
    notebook(target, "a", "different strokes")
    notebook(target, "a-moved") // the first free name is taken too
    val plan = LibraryMigration.plan(source, target)
    assertEquals(listOf("a-moved-2"), plan.copy.map { it.targetId })
    assertTrue(plan.copy.single().renamed)
  }

  @Test
  fun planRecognisesAnIdenticalNotebookAsAlreadyMoved() {
    notebook(source, "a")
    notebook(target, "a")
    val plan = LibraryMigration.plan(source, target)
    assertTrue(plan.copy.isEmpty())
    assertEquals(listOf("a"), plan.alreadyThere.map { it.name })
  }

  @Test
  fun verifyNoticesAMissingFileADifferentSizeAndAnExtraFolder() {
    notebook(source, "a")
    notebook(target, "a")
    assertTrue(LibraryMigration.sameContents(File(source, "a"), File(target, "a")))
    File(target, "a/images/photo.jpg").writeText("photo a, longer")
    assertFalse(LibraryMigration.sameContents(File(source, "a"), File(target, "a")))
    File(target, "a/images/photo.jpg").delete()
    assertFalse(LibraryMigration.sameContents(File(source, "a"), File(target, "a")))
    File(target, "a/images/photo.jpg").writeText("photo a")
    File(target, "a/extra").mkdirs()
    assertFalse(LibraryMigration.sameContents(File(source, "a"), File(target, "a")))
  }

  @Test
  fun mergesFolderListsAndColoursWithTheTargetWinning() {
    assertEquals(listOf("A", "A/B", "C"), LibraryMigration.mergeFolderLines(listOf("C", "A/B", " A / B "), listOf("A")))
    val merged = LibraryMigration.mergeColorLines(listOf("A\tff0000", "B\t00ff00"), listOf("A\t0000ff"))
    assertEquals(listOf("A\t0000ff", "B\t00ff00"), merged)
  }

  @Test
  fun runMovesNotebooksVerifiesThemAndRemovesThePrivateCopies() {
    notebook(source, "a")
    notebook(source, "b")
    File(source, "folders.txt").writeText("Mechanics\nMechanics/Lectures")
    File(source, "folder-colors.txt").writeText("Mechanics\tffcc0000")
    File(target, "folders.txt").writeText("Physics")

    val result = LibraryMigration.run(source, target)

    assertNull(result.error)
    assertEquals(2, result.moved)
    assertEquals(0, result.renamed)
    assertEquals(listOf("a", "b"), LibraryMigration.notebookDirs(target).map { it.name })
    assertEquals("photo a", File(target, "a/images/photo.jpg").readText())
    assertFalse(LibraryMigration.pending(source))
    assertTrue(target.list()!!.none { it.startsWith(".migrating") })
    assertEquals(listOf("Mechanics", "Mechanics/Lectures", "Physics"), File(target, "folders.txt").readLines())
    assertEquals(listOf("Mechanics\tffcc0000"), File(target, "folder-colors.txt").readLines())
    assertFalse(File(source, "folders.txt").exists())
    assertFalse(File(source, "folder-colors.txt").exists())
  }

  @Test
  fun runKeepsBothNotebooksOnAnIdClash() {
    notebook(source, "a", "mine")
    notebook(target, "a", "theirs, different")
    val result = LibraryMigration.run(source, target)
    assertNull(result.error)
    assertEquals(1, result.renamed)
    assertEquals("theirs, different", File(target, "a/notebook.graphite").readText())
    assertEquals("mine", File(target, "a-moved/notebook.graphite").readText())
    assertFalse(File(source, "a").exists())
  }

  @Test
  fun runIsRepeatableAfterAnInterruptedMove() {
    // A previous run copied "a" but was killed before removing the original, and left a half-copy of "b".
    notebook(source, "a")
    notebook(target, "a")
    notebook(source, "b")
    File(target, ".migrating-b/images").mkdirs()
    File(target, ".migrating-b/notebook.properties").writeText("half")
    val result = LibraryMigration.run(source, target)
    assertNull(result.error)
    assertEquals(2, result.moved)
    assertEquals(0, result.renamed) // no duplicate of "a"
    assertEquals(listOf("a", "b"), LibraryMigration.notebookDirs(target).map { it.name })
    assertEquals("strokes of b", File(target, "b/notebook.graphite").readText())
    assertFalse(LibraryMigration.pending(source))
  }

  @Test
  fun aFailureLeavesThePrivateCopyUntouched() {
    notebook(source, "a")
    // A read-only target library: the copy cannot even start.
    val readOnly = tmp.newFolder("readonly")
    assertTrue(readOnly.setWritable(false))
    val canStillWrite = runCatching { File(readOnly, "probe").createNewFile() }.getOrDefault(false)
    if (canStillWrite) return // running as root: the folder cannot be locked, nothing to test
    val before = LibraryMigration.fingerprint(File(source, "a"))
    File(source, "folders.txt").writeText("Keep")

    val result = LibraryMigration.run(source, readOnly)

    assertTrue(result.error != null)
    assertEquals(0, result.moved)
    assertEquals(before, LibraryMigration.fingerprint(File(source, "a")))
    assertTrue(File(source, "folders.txt").exists()) // private list kept after a failure
    readOnly.setWritable(true)
  }
}
