package dev.penlab.app.ui

import dev.penlab.app.ink.PageStyle
import dev.penlab.app.notes.NotebookInfo
import dev.penlab.app.notes.NotebookKind
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryLogicTest {
  private fun nb(id: String, modified: Long, folder: String = "") =
    NotebookInfo(id, id, folder, NotebookKind.A4, PageStyle.DOTTED, 1, emptyList(), 0L, modified)

  // ---- breadcrumbs
  @Test
  fun rootTrailIsJustNotebooks() {
    assertEquals(listOf(Crumb("Notebooks", "")), breadcrumbs(""))
  }

  @Test
  fun nestedTrailCarriesFullPaths() {
    assertEquals(
      listOf(Crumb("Notebooks", ""), Crumb("Fall 2026", "Fall 2026"), Crumb("Mechanics", "Fall 2026/Mechanics")),
      breadcrumbs("Fall 2026/Mechanics"),
    )
  }

  @Test
  fun trailIgnoresStraySlashes() {
    assertEquals(3, breadcrumbs("/A//B/").size)
    assertEquals("A/B", breadcrumbs("/A//B/").last().path)
  }

  // ---- import menu
  @Test
  fun importMenuHasPdfFilesThenFolder() {
    val entries = importMenuEntries("")
    assertEquals(listOf(ImportOption.PDF_FILES, ImportOption.FOLDER), entries.map { it.option })
    assertEquals(listOf("Import PDF files", "Import folder"), entries.map { it.label })
  }

  @Test
  fun importMenuNamesTheDestination() {
    assertTrue(importMenuEntries("").all { it.subtitle.endsWith("Notebooks") })
    assertTrue(importMenuEntries("Fall/Circuits").all { it.subtitle.endsWith("Circuits") })
    assertEquals("Circuits", importDestination("Fall/Circuits"))
  }

  // ---- continue strip
  @Test
  fun continueIsNewestFirstAndCappedAtFour() {
    val list = (1..6).map { nb("n$it", it.toLong()) }
    assertEquals(listOf("n6", "n5", "n4", "n3"), continueNotebooks(list, "").map { it.id })
  }

  @Test
  fun continueNeedsTopLevelAndTwoNotebooks() {
    assertTrue(continueNotebooks(listOf(nb("a", 1)), "").isEmpty())
    assertTrue(continueNotebooks(listOf(nb("a", 1), nb("b", 2)), "Some folder").isEmpty())
    assertEquals(2, continueNotebooks(listOf(nb("a", 1), nb("b", 2)), "").size)
  }

  // ---- dates and counts
  private val cairo = ZoneId.of("Africa/Cairo")
  private fun ms(y: Int, m: Int, d: Int, h: Int, min: Int) = ZonedDateTime.of(y, m, d, h, min, 0, 0, cairo).toInstant().toEpochMilli()

  @Test
  fun relativeDaysAreCalendarDays() {
    val now = ms(2026, 11, 26, 0, 10)
    assertEquals("Today", relativeDayLabel(now, ms(2026, 11, 26, 0, 1), cairo))
    assertEquals("Yesterday", relativeDayLabel(now, ms(2026, 11, 25, 23, 50), cairo))
    assertEquals("3 days ago", relativeDayLabel(now, ms(2026, 11, 23, 12, 0), cairo))
    assertEquals("6 days ago", relativeDayLabel(now, ms(2026, 11, 20, 12, 0), cairo))
  }

  @Test
  fun relativeDaysFallBackToRealDate() {
    val now = ms(2026, 11, 26, 12, 0)
    assertNull(relativeDayLabel(now, ms(2026, 11, 19, 12, 0), cairo)) // a week ago
    assertNull(relativeDayLabel(now, ms(2026, 11, 27, 12, 0), cairo)) // clock skew
  }

  @Test
  fun countLabelsPluralise() {
    assertEquals("1 page", pagesLabel(1))
    assertEquals("4 pages", pagesLabel(4))
    assertEquals("1 notebook", notebooksLabel(1))
    assertEquals("0 notebooks", notebooksLabel(0))
  }

  // ---- first launch
  private fun action(
    done: Boolean = false,
    loaded: Boolean = true,
    count: Int = 0,
    open: Boolean = false,
    requested: Boolean = false,
  ) = firstLaunchAction(done, loaded, count, open, requested)

  @Test
  fun genuineFirstRunShowsTheSheet() {
    assertEquals(FirstLaunchAction.SHOW, action())
  }

  @Test
  fun nothingShowsBeforeTheLibraryHasLoaded() {
    assertEquals(FirstLaunchAction.HIDE, action(loaded = false))
  }

  @Test
  fun returningUserWithNotebooksIsSkippedAndFlagged() {
    assertEquals(FirstLaunchAction.MARK_DONE, action(count = 3))
  }

  @Test
  fun doneFlagWinsAndNeverShowsAgain() {
    assertEquals(FirstLaunchAction.HIDE, action(done = true))
    assertEquals(FirstLaunchAction.HIDE, action(done = true, count = 5))
  }

  @Test
  fun openNotebookKeepsItAway() {
    assertEquals(FirstLaunchAction.HIDE, action(open = true))
  }

  @Test
  fun settingsRequestBypassesEveryRule() {
    assertEquals(FirstLaunchAction.SHOW, action(done = true, count = 9, loaded = false, open = true, requested = true))
  }

  @Test
  fun startWritingOnlyCreatesForAnEmptyLibrary() {
    assertTrue(startWritingCreatesNotebook(0))
    assertTrue(!startWritingCreatesNotebook(1))
  }

  // ---- format and paper memory
  @Test
  fun formatMapsBothWays() {
    for (f in NotebookFormat.entries) assertEquals(f, NotebookFormat.of(f.kind, f.landscape))
    assertEquals(NotebookKind.INFINITE, NotebookFormat.ENDLESS.kind)
    assertEquals(listOf("A4 Portrait", "A4 Landscape", "Endless"), NotebookFormat.entries.map { it.label })
  }

  @Test
  fun storedFormatIsUnchanged() {
    val d = PaperDefaults(PageStyle.GRID, NotebookKind.A4, true)
    assertEquals("GRID:A4:landscape", encodePaperDefaults(d))
    assertEquals(d, decodePaperDefaults("GRID:A4:landscape"))
    assertEquals("CP:INFINITE:portrait", encodePaperDefaults(PaperDefaults(PageStyle.CP, NotebookKind.INFINITE, false)))
  }

  @Test
  fun damagedStoredValueFallsBackPerField() {
    assertEquals(FallbackPaperDefaults, decodePaperDefaults(""))
    assertEquals(PaperDefaults(PageStyle.LINED, NotebookKind.A4, false), decodePaperDefaults("LINED:NOPE"))
  }

  @Test
  fun foldersInheritFromTheNearestParent() {
    val store = mapOf("/" to "LINED:A4:portrait", "Circuits" to "GRID:A4:landscape")
    val lookup: (String) -> String? = { store[it] }
    assertEquals(PageStyle.GRID, paperDefaultsFor("Circuits/Lab 1", lookup).style)
    assertEquals(PageStyle.LINED, paperDefaultsFor("Other", lookup).style)
    assertEquals(PageStyle.LINED, paperDefaultsFor("", lookup).style)
    assertEquals(FallbackPaperDefaults, paperDefaultsFor("Any", { null }))
    assertEquals("/", paperPrefKey(""))
    assertEquals("A/B", paperPrefKey("A/B"))
  }
}
