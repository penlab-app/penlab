package dev.penlab.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// The app's icons, kept as path data so no icon library is needed. Each is drawn on a
// 24 x 24 grid and tinted where it is used. Undo, redo and clear are Material icons
// (Apache 2.0); the eraser and paper icons are drawn for PenLab.

private fun icon(name: String, pathData: String): ImageVector =
  ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(
      pathData = addPathNodes(pathData),
      fill = SolidColor(Color.Black),
      // Even-odd: a shape drawn inside another one cuts a hole in it.
      pathFillType = PathFillType.EvenOdd,
    )
    .build()

internal val UndoIcon =
  icon(
    "Undo",
    "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 " +
      "3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z",
  )

internal val RedoIcon =
  icon(
    "Redo",
    "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 " +
      "4.05,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z",
  )

internal val ClearIcon =
  icon(
    "Clear page",
    "M16,9v10H8V9h8m-1.5,-6h-5l-1,1H5v2h14V4h-3.5l-1,-1zM18,7H6v12c0,1.1 0.9,2 2,2h8c1.1,0 " +
      "2,-0.9 2,-2V7z",
  )

/** A slanted eraser block resting on a baseline. */
internal val EraserIcon =
  icon(
    "Eraser",
    // Block (outer outline, then the cut-out that leaves the rubber end hollow), then baseline.
    "M14.6,3.6L21.4,10.4a1.5,1.5 0 0 1 0,2.1L13.9,20H7.1L2.6,15.5a1.5,1.5 0 0 1 0,-2.1L12.5,3.6" +
      "a1.5,1.5 0 0 1 2.1,0zM8.3,10.6L4.4,14.5L7.9,18H12.1L14.8,15.3L8.3,10.6z" +
      "M15,20H22V22H15z",
  )

/** A lasso: a loop with a short tail. */
internal val LassoIcon =
  icon(
    "Lasso select",
    "M3,10a9,6 0 1,0 18,0a9,6 0 1,0 -18,0z" +
      "M4.8,10a7.2,4.4 0 1,0 14.4,0a7.2,4.4 0 1,0 -14.4,0z" +
      "M7.3,14.9L5.4,21H7.5L9.1,15.5z",
  )

/** A cross, for closing or clearing (Material "close"). */
internal val CloseIcon =
  icon(
    "Close",
    "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z",
  )

/** Mirror: two halves either side of a dashed middle line (Material "flip"). */
internal val MirrorIcon =
  icon(
    "Mirror",
    "M15,21h2v-2h-2v2zM19,9h2L21,7h-2v2zM3,5v14c0,1.1 0.9,2 2,2h4v-2L5,19L5,5h4L9,3L5,3c-1.1,0 -2,0.9 -2,2z" +
      "M19,3v2h2c0,-1.1 -0.9,-2 -2,-2zM11,23h2L13,1h-2v22zM19,17h2v-2h-2v2zM15,5h2L17,3h-2v2z" +
      "M19,13h2v-2h-2v2zM19,21c1.1,0 2,-0.9 2,-2h-2v2z",
  )

/** Closed padlock (Material "lock"). */
internal val LockIcon =
  icon(
    "Locked",
    "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12" +
      "c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM9,6c0,-1.66 1.34,-3 3,-3s3,1.34 3,3v2H9V6z" +
      "M18,20H6V10h12v10zM12,17c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2z",
  )

/** Open padlock (Material "lock_open"). */
internal val LockOpenIcon =
  icon(
    "Unlocked",
    "M12,17c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM18,8h-1V6c0,-2.76 -2.24,-5 -5,-5" +
      "S7,3.24 7,6h1.9c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2H6c-1.1,0 -2,0.9 -2,2v10" +
      "c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM18,20H6V10h12v10z",
  )

/** Back arrow (Material "arrow_back_ios_new"). */
internal val BackIcon =
  icon("Back", "M17.77,3.77L16,2L6,12l10,10l1.77,-1.77L9.54,12z")

/** Bookmark ribbon, filled (Material "bookmark"). */
internal val BookmarkIcon = icon("Bookmarked", "M17,3H7c-1.1,0 -2,0.9 -2,2v16l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z")

/** Bookmark ribbon, outline (Material "bookmark_border"). */
internal val BookmarkOutlineIcon =
  icon("Bookmark", "M17,3H7c-1.1,0 -2,0.9 -2,2v16l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2zM17,18l-5,-2.18L7,18V5h10v13z")

/** Stacked pages (Material "auto_stories"-like, simplified): two sheets. */
internal val PagesIcon =
  icon(
    "Pages",
    "M8,2h10a2,2 0 0 1 2,2v14h-2V4H8V2zM4,6h10a2,2 0 0 1 2,2v12a2,2 0 0 1 -2,2H4a2,2 0 0 1 -2,-2V8" +
      "a2,2 0 0 1 2,-2zM4,8v12h10V8H4z",
  )

/** Three dots: the "more" menu (Material "more_horiz"). */
internal val MoreIcon =
  icon(
    "More",
    "M6,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM18,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z" +
      "M12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
  )

/** Save to the tablet or Drive: an arrow into a tray (Material "download"). */
internal val SaveIcon =
  icon("Save", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z")

/** Share: a tray with an arrow coming out (Material "ios_share"). */
internal val ExportIcon =
  icon(
    "Export",
    "M16,5l-1.42,1.42 -1.59,-1.59V16h-1.98V4.83L9.42,6.42 8,5l4,-4 4,4zM20,10v11c0,1.1 -0.9,2 -2,2H6" +
      "c-1.11,0 -2,-0.9 -2,-2V10c0,-1.11 0.89,-2 2,-2h3v2H6v11h12V10h-3V8h3c1.1,0 2,0.89 2,2z",
  )

/** A ruler with ticks (Material "straighten"). */
internal val RulerIcon =
  icon(
    "Ruler",
    "M21,6H3c-1.1,0 -2,0.9 -2,2v8c0,1.1 0.9,2 2,2h18c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2z" +
      "M21,16H3V8h2v4h2V8h2v4h2V8h2v4h2V8h2v4h2V8h2v8z",
  )

/** Fit to width: a page between two side bars with arrows pointing out to them. */
val FitWidthIcon =
  icon(
    "Fit width",
    "M2,4h2v16H2zM20,4h2v16h-2zM7,11h10v2H7zM9.5,7.5L5.5,12l4,4.5zM14.5,7.5l4,4.5 -4,4.5z",
  )

/** A light bulb, for noting a bug or an idea (Material "lightbulb"). */
val IdeaIcon =
  icon(
    "Bug or idea",
    "M9,21c0,0.55 0.45,1 1,1h4c0.55,0 1,-0.45 1,-1v-1H9v1zM12,2C8.14,2 5,5.14 5,9c0,2.38 1.19,4.47 3,5.74V17" +
      "c0,0.55 0.45,1 1,1h6c0.55,0 1,-0.45 1,-1v-2.26c1.81,-1.27 3,-3.36 3,-5.74 0,-3.86 -3.14,-7 -7,-7z",
  )

/** A capital T, for the text tool (Material "title"). */
internal val TextIcon = icon("Text", "M5,4v3h5.5v12h3V7H19V4z")

/** A paint bucket (Material "format_color_fill"), for the fill tool. */
internal val FillIcon =
  icon(
    "Fill",
    "M16.56,8.94L7.62,0 6.21,1.41l2.38,2.38 -5.15,5.15c-0.59,0.59 -0.59,1.54 0,2.12l5.5,5.5c0.29,0.29 0.68,0.44 1.06,0.44s0.77,-0.15 1.06,-0.44l5.5,-5.5c0.59,-0.58 0.59,-1.53 0,-2.12z" +
      "M5.21,10L10,5.21 14.79,10H5.21zM19,11.5s-2,2.17 -2,3.5c0,1.1 0.9,2 2,2s2,-0.9 2,-2c0,-1.33 -2,-3.5 -2,-3.5z",
  )

/** A picture (Material "image"), for inserting a photo. */
val ImageIcon =
  icon(
    "Insert photo",
    "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2z" +
      "M8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z",
  )

/** A small bin, for deleting a selection. */
internal val DeleteIcon = ClearIcon

/** A sheet of ruled paper. */
internal val PaperIcon =
  icon(
    "Paper style",
    "M6,3H18a2,2 0 0 1 2,2V19a2,2 0 0 1 -2,2H6a2,2 0 0 1 -2,-2V5a2,2 0 0 1 2,-2z" +
      "M6,5V19H18V5H6zM8,8H16V9.5H8zM8,11.25H16V12.75H8zM8,14.5H16V16H8z",
  )

/** Material "colorize" (Apache 2.0): pick a colour from the page. */
internal val EyedropperIcon =
  icon(
    "Eyedropper",
    "M20.71,5.63l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-3.12,3.12 -1.93,-1.91 -1.41,1.41 1.42,1.42L3,16.25V21h4.75l8.92,-8.92 " +
      "1.42,1.42 1.41,-1.41 -1.92,-1.92 3.12,-3.12c0.4,-0.4 0.4,-1.03 0.01,-1.42zM6.92,19L5,17.08l8.06,-8.06 1.92,1.92L6.92,19z",
  )

/** A ring of colour segments: open the colour wheel. Drawn for PenLab. */
internal val WheelIcon =
  icon(
    "More colours",
    "M12,2A10,10 0 1,0 12,22A10,10 0 1,0 12,2ZM12,7A5,5 0 1,1 12,17A5,5 0 1,1 12,7Z",
  )

/** Material "home" (Apache 2.0): back to where you were writing on an endless canvas. */
val HomeIcon = icon("Home", "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z")

/** Material "layers" (Apache 2.0). */
internal val LayersIcon =
  icon("Layers", "M11.99,18.54l-7.37,-5.73L3,14.07l9,7 9,-7 -1.63,-1.27 -7.38,5.74zM12,16l7.36,-5.73L21,9l-9,-7 -9,7 1.63,1.27L12,16z")

/** Material "visibility" (Apache 2.0): a layer that shows. */
internal val EyeIcon =
  icon(
    "Shown",
    "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5z" +
      "M12,7c2.76,0 5,2.24 5,5s-2.24,5 -5,5 -5,-2.24 -5,-5 2.24,-5 5,-5z" +
      "M12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
  )

/** Material "visibility off" (Apache 2.0): a hidden layer. */
internal val EyeOffIcon =
  icon(
    "Hidden",
    "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.43,-4.75 -1.73,-4.39 -6,-7.5 -11,-7.5 " +
      "-1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7z" +
      "M2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5 1.55,0 3.03,-0.3 4.38,-0.84l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27z",
  )

/** Material "search" (Apache 2.0): handwriting search. */
val SearchIcon =
  icon(
    "Search",
    "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z",
  )

/** Material "fullscreen" / "fullscreen_exit" (Apache 2.0). */
val FullScreenIcon = icon("Full screen", "M7,14H5v5h5v-2H7v-3zM5,10h2V7h3V5H5v5zM17,17h-3v2h5v-5h-2v3zM14,5v2h3v3h2V5h-5z")
val FullScreenExitIcon = icon("Leave full screen", "M5,16h3v3h2v-5H5v2zM8,8H5v2h5V5H8v3zM14,19h2v-3h3v-2h-5v5zM16,8V5h-2v5h5V8h-3z")
