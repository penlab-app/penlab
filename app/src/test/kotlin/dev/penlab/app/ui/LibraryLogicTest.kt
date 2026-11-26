package dev.penlab.app.ui

import dev.penlab.app.ink.PageStyle
import dev.penlab.app.notes.NotebookInfo
import dev.penlab.app.notes.NotebookKind
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryLogicTest {
  private fun nb(id: String, modified: Long, folder: String = "", title: String = id) =
    NotebookInfo(id, title, folder, NotebookKind.A4, PageStyle.DOTTED, 1, emptyList(), 0L, modified)

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
  fun continueNeedsTopLevelAndMoreThanFourNotebooks() {
    // Four or fewer are all on screen below anyway: no strip.
    assertTrue(continueNotebooks((1..4).map { nb("n$it", it.toLong()) }, "").isEmpty())
    assertTrue(continueNotebooks(listOf(nb("a", 1), nb("b", 2)), "").isEmpty())
    val five = (1..5).map { nb("n$it", it.toLong()) }
    assertEquals(4, continueNotebooks(five, "").size)
    assertTrue(continueNotebooks(five, "Some folder").isEmpty())
  }

  // ---- section headings
  @Test
  fun headingNeverRepeatsThePageTitle() {
    // Top level: the page is titled "Notebooks", so the loose-notebooks heading would say it twice.
    val top = sectionHeadings(hasContinue = true, hasFolders = true, hasNotebooks = true, current = "")
    assertEquals(SectionHeadings(continueStrip = true, folders = true, notebooks = false), top)
    // Inside a folder the page is titled with the folder, so "Notebooks" tells the sections apart.
    val inside = sectionHeadings(hasContinue = false, hasFolders = true, hasNotebooks = true, current = "Fall/Mechanics")
    assertEquals(SectionHeadings(continueStrip = false, folders = true, notebooks = true), inside)
    // A folder that happens to be called "Notebooks" hides that heading too.
    assertFalse(sectionHeadings(false, true, true, "Notebooks").notebooks)
  }

  @Test
  fun headingsNeedMoreThanOneSection() {
    assertEquals(SectionHeadings(false, false, false), sectionHeadings(false, false, true, "Fall"))
    assertEquals(SectionHeadings(false, false, false), sectionHeadings(false, true, false, ""))
  }

  // ---- title search
  @Test
  fun titleMatchesIgnoreCasePunctuationAndSpaces() {
    val list = listOf(nb("a", 1, title = "Diff. Eq midterm"), nb("b", 2, title = "Circuits"))
    assertEquals(listOf("a"), titleMatches("diffeq", list).map { it.id })
    assertEquals(listOf("b"), titleMatches("  CIRC ", list).map { it.id })
    assertTrue(titleMatches("", list).isEmpty())
    assertTrue(titleMatches("???", list).isEmpty())
  }

  @Test
  fun titlesStartingWithTheQueryComeFirstThenNewest() {
    val list = listOf(
      nb("old-start", 1, title = "Calculus 1"),
      nb("new-inside", 9, title = "Notes on calculus"),
      nb("new-start", 5, title = "Calculus 2"),
    )
    assertEquals(listOf("new-start", "old-start", "new-inside"), titleMatches("calc", list).map { it.id })
  }

  @Test
  fun searchSummaryCountsTitlesAndLines() {
    assertEquals("No matches for “x”", searchSummary("x", 0, 0, 0))
    assertEquals("1 notebook title", searchSummary("x", 1, 0, 0))
    assertEquals("2 notebook titles · 1 line of handwriting in 1 notebook", searchSummary("x", 2, 1, 1))
    assertEquals("3 lines of handwriting in 2 notebooks", searchSummary("x", 0, 3, 2))
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
  fun cardDatesAreRelativeForAWeekThenShort() {
    val now = ms(2026, 11, 26, 12, 0)
    val nb = '\u00A0'
    assertEquals("Today", cardDateLabel(now, ms(2026, 11, 26, 8, 0), cairo, java.util.Locale.ENGLISH))
    assertEquals("3${nb}days${nb}ago", cardDateLabel(now, ms(2026, 11, 23, 8, 0), cairo, java.util.Locale.ENGLISH))
    // Older: day and month only, so "1 page · 26 Nov" fits a 150dp card; the year only for another year.
    assertEquals("12${nb}Nov", cardDateLabel(now, ms(2026, 11, 12, 8, 0), cairo, java.util.Locale.ENGLISH))
    assertEquals("26${nb}Nov${nb}2025", cardDateLabel(now, ms(2025, 11, 26, 8, 0), cairo, java.util.Locale.ENGLISH))
  }

  @Test
  fun cardMetaWrapsBetweenPartsNeverInsideOne() {
    val nb = '\u00A0'
    assertEquals("12${nb}pages$nb· 26${nb}Nov", cardMeta(null, false, 12, "26${nb}Nov"))
    assertEquals("Canvas$nb· Today", cardMeta(null, true, 3, "Today"))
    assertEquals("Mechanics of Materials$nb· 1${nb}page$nb· Today", cardMeta("Mechanics of Materials", false, 1, "Today"))
    // The only breakable space is the one after the dot.
    assertEquals(1, cardMeta(null, false, 12, "26${nb}Nov").count { it == ' ' })
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

  // ---- card titles, covers, search heading
  @Test
  fun longTitlesGetBreakOpportunitiesAfterSeparators() {
    val z = "\u200B"
    assertEquals("MoM-${z}Assignment2-${z}Solutions", breakableTitle("MoM-Assignment2-Solutions"))
    assertEquals("a_${z}b", breakableTitle("a_b"))
    assertEquals("Diff. Eq", breakableTitle("Diff. Eq")) // plain words are untouched
    assertEquals("trailing-", breakableTitle("trailing-")) // nothing follows: no break needed
    assertEquals("a - b", breakableTitle("a - b")) // already next to a space
    assertEquals("a--${z}b", breakableTitle("a--b")) // one break per run, after the last separator
  }

  @Test
  fun landscapeCoversFillTheBoxWidthAndTallOnesItsHeight() {
    assertTrue(coverFillsBoxWidth(297f / 210f))
    assertTrue(coverFillsBoxWidth(COVER_BOX_ASPECT))
    assertFalse(coverFillsBoxWidth(0.5f))
  }

  @Test
  fun titlesHeadingOnlyWhenTwoSectionsHaveResults() {
    assertFalse(showTitlesHeading(titleHits = 1, handwritingLines = 0))
    assertFalse(showTitlesHeading(titleHits = 0, handwritingLines = 3))
    assertTrue(showTitlesHeading(titleHits = 2, handwritingLines = 3))
  }
}
