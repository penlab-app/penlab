package dev.penlab.app.ui

import dev.penlab.app.ui.theme.PageThemeMode
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
}
