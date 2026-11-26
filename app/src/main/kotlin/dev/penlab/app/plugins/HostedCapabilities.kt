package dev.penlab.app.plugins

import dev.penlab.plugin.Importer
import dev.penlab.plugin.PageAction

/** Where an external plugin's screen entry appears. */
enum class ScreenPlacement {
  /** The lasso's More menu (needs a selection). */
  SELECTION_MENU,

  /** The toolbar's More panel. */
  MORE_MENU,

  /** The file menu (the notebook's options popover). */
  FILE_MENU,

  /** A button of its own in the top-right bar, beside search (the plugin's icon). */
  TOP_BAR,
}

/**
 * A plugin's own activity that PenLab opens for a result. It is a [PageAction] so it shares the menus
 * and the registry with ordinary actions, but PenLab never calls [run]: the host starts [activity]
 * instead (see MainActivity.runPluginAction).
 */
interface ScreenPageAction : PageAction {
  val placement: ScreenPlacement
}

/** An [Importer] that also claims file name extensions (without the dot, lower case): many formats have no MIME type Android knows. */
interface ExtensionImporter : Importer {
  val extensions: List<String>
}
