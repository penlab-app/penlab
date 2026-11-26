package dev.penlab.app.ui

import dev.penlab.app.ink.DEFAULT_PRESSURE_SENSITIVITY
import dev.penlab.app.ink.PressureSensitivity
import dev.penlab.app.ink.pressureSensitivityFromStorage
import dev.penlab.app.ui.theme.PageThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewSettingsLogicTest {

  @Test
  fun shapeSnappingDefaultsToEnabledWhenUnset() {
    assertTrue(DEFAULT_SHAPE_SNAPPING)
    assertTrue(shapeSnappingDefault(storedChoice = null))
  }

  @Test
  fun shapeSnappingRespectsExplicitToggleChoice() {
    assertFalse(shapeSnappingDefault(storedChoice = false))
    assertTrue(shapeSnappingDefault(storedChoice = true))
  }

  @Test
  fun plainNotebooksFollowPageThemeMode() {
    assertFalse(resolveCanvasDarkPage(PageThemeMode.LIGHT, isPdfNotebook = false))
    assertTrue(resolveCanvasDarkPage(PageThemeMode.DARK, isPdfNotebook = false))
  }

  @Test
  fun pdfNotebooksAlwaysStayLightRegardlessOfPageThemeMode() {
    assertFalse(resolveCanvasDarkPage(PageThemeMode.LIGHT, isPdfNotebook = true))
    assertFalse(resolveCanvasDarkPage(PageThemeMode.DARK, isPdfNotebook = true))
  }

  @Test
  fun libraryStatusBarFollowsTheAppTheme() {
    assertTrue(systemBarsOverDarkSurface(appIsDark = true, notebookOpen = false, paperIsDark = false))
    assertFalse(systemBarsOverDarkSurface(appIsDark = false, notebookOpen = false, paperIsDark = true))
  }

  @Test
  fun openNotebookStatusBarFollowsThePaperNotTheTheme() {
    // Light UI on Dark paper used to give dark icons on a dark page: invisible.
    assertTrue(systemBarsOverDarkSurface(appIsDark = false, notebookOpen = true, paperIsDark = true))
    assertFalse(systemBarsOverDarkSurface(appIsDark = true, notebookOpen = true, paperIsDark = false))
  }

  @Test
  fun aPdfNotebookOnDarkPaperIsStillWhiteSoIconsAreDark() {
    val paperDark = resolveCanvasDarkPage(PageThemeMode.DARK, isPdfNotebook = true)
    assertFalse(systemBarsOverDarkSurface(appIsDark = true, notebookOpen = true, paperIsDark = paperDark))
  }

  @Test
  fun pressureSensitivityDefaultsToBalancedWhenUnsetOrUnknown() {
    assertEquals(PressureSensitivity.BALANCED, DEFAULT_PRESSURE_SENSITIVITY)
    assertEquals(PressureSensitivity.BALANCED, pressureSensitivityFromStorage(null))
    assertEquals(PressureSensitivity.BALANCED, pressureSensitivityFromStorage(""))
    assertEquals(PressureSensitivity.BALANCED, pressureSensitivityFromStorage("UNKNOWN_PRESET"))
  }

  @Test
  fun pressureSensitivityRoundTripsAllPresets() {
    for (preset in PressureSensitivity.entries) {
      assertEquals(preset, pressureSensitivityFromStorage(preset.name))
    }
  }
}
