package dev.penlab.app.ui

/**
 * What the note screen shows, from the three switches that change it. Kept free of Compose so the
 * rules can be unit-tested.
 *
 * Present mode is for projecting the tablet: the projector mirrors the screen, so the students
 * see exactly what the teacher sees. While it is on, only the page and her ink are shown; the
 * pencil corner button toggles the toolbar ([toolsPeeking]); it stays until tapped again or Present exits.
 */
data class ScreenChrome(
  /** Full screen: the top bar hides, the tools stay. */
  val fullScreen: Boolean = false,
  /** Present mode: nothing but the page and the ink. */
  val present: Boolean = false,
  /** Present mode only: the toolbar is shown (toggled by the pencil corner button). */
  val toolsPeeking: Boolean = false,
  /**
   * The tool dock is the corner wheel. The wheel sits in the real screen corner, where a swipe
   * for Home or Back would start, so the system bars hide (a swipe only reveals them).
   */
  val wheelDocked: Boolean = false,
) {
  /** The row of buttons along the top, with the page counter and the tabs in it. */
  val topBar: Boolean get() = !fullScreen && !present

  /**
   * The recording pill (and the "Record lecture" button): everywhere except Present, where only the page and the ink show
   * (the recording itself goes on). Full screen keeps it: the pill is the only way to see and stop a recording there.
   */
  val mediaPill: Boolean get() = !present

  /** The tool dock (rail, bar, wheel) and, with it, every panel that opens from it. */
  val toolbar: Boolean get() = !present || toolsPeeking

  /** Snackbars, toasts, the "back to page" hint: nothing that pops up over the page. */
  val messages: Boolean get() = !present

  /** The lasso box with its handles, the lasso menu and the paste menu. */
  val selectionUi: Boolean get() = !present

  /** The small faint button in the corner that leaves full screen. */
  val leaveFullScreenButton: Boolean get() = fullScreen && !present

  /** The faint corner buttons of present mode: pen tools and the exit cross. */
  val presentButtons: Boolean get() = present

  /**
   * The system status and navigation bars hide in full screen, in present mode and while the
   * corner wheel is docked and showing (wheel immersive: the same state as full screen's bars,
   * without hiding the top bar). Leaving the wheel brings the bars back unless full screen or
   * present still holds them hidden.
   */
  val systemBarsHidden: Boolean get() = fullScreen || present || (wheelDocked && toolbar)

  /** The screen must not go to sleep: the class is looking at it. */
  val keepScreenOn: Boolean get() = present

  /** Back leaves present mode first, before it would close the notebook. */
  val backLeavesPresent: Boolean get() = present
}

/** What the Back button does on the note screen, most local first. */
enum class BackAction { CLOSE_OVERLAYS, CLEAR_SELECTION, LEAVE_PRESENT, LEAVE_FULL_SCREEN, LEAVE_NOTEBOOK }

/**
 * Back peels one layer: an open popover, panel or wheel first, then a lasso selection, then Present, then
 * full screen, and only when nothing is left does it leave the notebook. Pure so the order can be unit-tested.
 */
fun backAction(overlayOpen: Boolean, present: Boolean, fullScreen: Boolean, selection: Boolean = false): BackAction = when {
  overlayOpen -> BackAction.CLOSE_OVERLAYS
  selection -> BackAction.CLEAR_SELECTION
  present -> BackAction.LEAVE_PRESENT
  fullScreen -> BackAction.LEAVE_FULL_SCREEN
  else -> BackAction.LEAVE_NOTEBOOK
}

/** The corner buttons are faint so they do not draw the class's eyes (same as Leave full screen). */
const val CORNER_BUTTON_ALPHA = 0.55f

/** Whisper-quiet translucent opacity for the Present mode corner pill at rest. */
const val PRESENT_PILL_REST_ALPHA = 0.60f

/** Full opacity for the Present mode corner pill while writing tools are peeking. */
const val PRESENT_PILL_ACTIVE_ALPHA = 1.0f

/** Subtle translucent opacity for the Fullscreen exit floating pill. */
const val FULLSCREEN_EXIT_ALPHA = 0.72f

/** Duration of the non-intrusive entry hint shown when entering Present mode. */
const val PRESENT_HINT_MS = 2800L

/** Concise hint shown briefly upon entering Present mode. */
const val PRESENT_HINT_MESSAGE = "Presenting · Pencil button shows or hides the tools"

/** Resolves the Present mode floating pill alpha from whether writing tools are peeking. */
fun presentPillAlpha(toolsPeeking: Boolean): Float =
  if (toolsPeeking) PRESENT_PILL_ACTIVE_ALPHA else PRESENT_PILL_REST_ALPHA

