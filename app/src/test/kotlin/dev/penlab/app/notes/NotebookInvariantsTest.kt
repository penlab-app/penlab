package dev.penlab.app.notes

import dev.penlab.app.ink.PageLayout
import dev.penlab.app.ink.PageStyle
import dev.penlab.plugin.ImportedNotebook
import dev.penlab.plugin.ImportedPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NotebookInvariantsTest {
  @get:Rule val tmp = TemporaryFolder()

  private fun newStore(): NotebookStore =
    NotebookStore(LibraryLocation(tmp.newFolder("files"), File(tmp.root, "Documents/PenLab")) { false })

  // ---- createFromImport non-PDF flag --------------------------------------------------------

  @Test
  fun createFromImportDoesNotFlagNonPdfImportEvenIfBackgroundPdfFileIsPresent() {
    val store = newStore()
    val work = tmp.newFolder("work-nonpdf")
    val strayPdf = File(work, "unused.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    val imported = ImportedNotebook(
      title = "",
      pageWidthMm = 210f,
      pageHeightMm = 297f,
      pages = listOf(ImportedPage(backgroundPdfPage = -1), ImportedPage(backgroundPdfPage = -1)),
      backgroundPdf = strayPdf,
    )
    assertFalse(importedHasPdfBackground(imported))
    val created = store.createFromImport(imported, "Math")
    assertNotNull(created)
    assertTrue(created!!.pdfPages.isEmpty())
    assertEquals("Imported notebook", created.title)
    assertFalse(store.pdfFile(created).exists())
  }

  @Test
  fun createFromImportFlagsPdfNotebookWhenAnyPageReferencesPdfPage() {
    val store = newStore()
    val work = tmp.newFolder("work-pdf")
    val pdf = File(work, "doc.pdf").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
    val imported = ImportedNotebook(
      title = "",
      pageWidthMm = 210f,
      pageHeightMm = 297f,
      pages = listOf(ImportedPage(backgroundPdfPage = -1), ImportedPage(backgroundPdfPage = 0)),
      backgroundPdf = pdf,
    )
    assertTrue(importedHasPdfBackground(imported))
    val created = store.createFromImport(imported, "Physics")
    assertNotNull(created)
    assertEquals(listOf(-1, 0), created!!.pdfPages)
    assertEquals("Imported PDF", created.title)
    assertTrue(store.pdfFile(created).exists())
  }

  @Test
  fun createFromImportWithNullPdfNeverFlagsAsPdfEvenIfPageIndexNonNegative() {
    val store = newStore()
    val imported = ImportedNotebook(
      title = "Notes",
      pageWidthMm = 210f,
      pageHeightMm = 297f,
      pages = listOf(ImportedPage(backgroundPdfPage = 0)),
      backgroundPdf = null,
    )
    assertFalse(importedHasPdfBackground(imported))
    val created = store.createFromImport(imported, "")
    assertNotNull(created)
    assertTrue(created!!.pdfPages.isEmpty())
  }

  // ---- turnOpenNotebook / metadata sync helpers ---------------------------------------------

  @Test
  fun syncNotebookMetadataMergesLiveCanvasStateWithoutLosingIdentityOrRecordings() {
    val base = NotebookInfo(
      id = "nb-1",
      title = "Calc 2",
      folder = "Courses/Math",
      kind = NotebookKind.A4,
      pageStyle = PageStyle.BLANK,
      pageCount = 1,
      bookmarks = emptyList(),
      created = 100L,
      modified = 200L,
      recordings = listOf("rec1.m4a"),
    )
    val synced = syncNotebookMetadata(
      info = base,
      pageStyle = PageStyle.GRID,
      rulingMm = 5f,
      layerNames = listOf("Ink", "Scratch", "Answers"),
      hiddenLayers = setOf(2, 0, 99),
      currentLayer = 1,
      pageCount = 3,
      bookmarks = setOf(2, 0, 5),
      pdfPages = emptyList(),
      now = 999L,
    )
    assertEquals("nb-1", synced.id)
    assertEquals("Calc 2", synced.title)
    assertEquals("Courses/Math", synced.folder)
    assertEquals(listOf("rec1.m4a"), synced.recordings)
    assertEquals(PageStyle.GRID, synced.pageStyle)
    assertEquals(5f, synced.rulingMm, 0f)
    assertEquals(listOf("Ink", "Scratch", "Answers"), synced.layerNames)
    assertEquals(listOf(0, 2), synced.hiddenLayers)
    assertEquals(1, synced.currentLayer)
    assertEquals(3, synced.pageCount)
    assertEquals(listOf(0, 2), synced.bookmarks)
    assertEquals(999L, synced.modified)
  }

  @Test
  fun turnNotebookInfoSwapsDimensionsOnlyForPlainA4() {
    val plainA4 = NotebookInfo("a", "A4", "", NotebookKind.A4, PageStyle.LINED, 2, listOf(1), 1L, 2L, 210f, 297f)
    val landscape = turnNotebookInfo(plainA4, 500L)
    assertNotNull(landscape)
    assertEquals(297f, landscape!!.pageWidthMm, 0f)
    assertEquals(210f, landscape.pageHeightMm, 0f)
    assertEquals(500L, landscape.modified)

    val pdfA4 = plainA4.copy(pdfPages = listOf(0, 1))
    assertNull(turnNotebookInfo(pdfA4, 500L))

    val infinite = plainA4.copy(kind = NotebookKind.INFINITE)
    assertNull(turnNotebookInfo(infinite, 500L))
  }

  @Test
  fun turnShiftDyKeepsStrokesAtSameOffsetFromEachPageTop() {
    val mm = PageLayout.UNITS_PER_MM
    val portraitPitch = 297f * mm + 12f * mm
    val landscapePitch = 210f * mm + 12f * mm

    // Page 0: top stays 0 -> dy = 0.
    assertEquals(0f, turnShiftDy(centerY = 100f * mm, oldHeightMm = 297f, newHeightMm = 210f, pageCount = 3), 1e-3f)

    // Page 2 (index 2): centerY at 2 * portraitPitch + 40mm moves to 2 * landscapePitch + 40mm.
    val oldY = 2 * portraitPitch + 40f * mm
    val dy = turnShiftDy(centerY = oldY, oldHeightMm = 297f, newHeightMm = 210f, pageCount = 3)
    assertEquals(2 * landscapePitch + 40f * mm, oldY + dy, 1e-3f)
  }
}
