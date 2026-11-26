package dev.penlab.app.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.Check as BoldCheck
import com.adamglin.phosphoricons.regular.Minus as RegularMinus
import com.adamglin.phosphoricons.regular.Plus as RegularPlus
import com.adamglin.phosphoricons.duotone.ArrowArcLeft
import com.adamglin.phosphoricons.duotone.ArrowArcRight
import com.adamglin.phosphoricons.duotone.ArrowsClockwise
import com.adamglin.phosphoricons.duotone.ArrowsOutCardinal
import com.adamglin.phosphoricons.duotone.ArrowsOutLineHorizontal
import com.adamglin.phosphoricons.duotone.Article
import com.adamglin.phosphoricons.duotone.BookmarkSimple
import com.adamglin.phosphoricons.duotone.Broom
import com.adamglin.phosphoricons.duotone.CaretDown
import com.adamglin.phosphoricons.duotone.CaretLeft
import com.adamglin.phosphoricons.duotone.CaretRight
import com.adamglin.phosphoricons.duotone.CaretUp
import com.adamglin.phosphoricons.duotone.Check
import com.adamglin.phosphoricons.duotone.ClipboardText
import com.adamglin.phosphoricons.duotone.CloudArrowUp
import com.adamglin.phosphoricons.duotone.CompassTool
import com.adamglin.phosphoricons.duotone.Copy
import com.adamglin.phosphoricons.duotone.CopySimple
import com.adamglin.phosphoricons.duotone.CornersIn
import com.adamglin.phosphoricons.duotone.CornersOut
import com.adamglin.phosphoricons.duotone.DeviceTablet
import com.adamglin.phosphoricons.duotone.DotsSixVertical
import com.adamglin.phosphoricons.duotone.DotsThree
import com.adamglin.phosphoricons.duotone.DotsThreeVertical
import com.adamglin.phosphoricons.duotone.DownloadSimple
import com.adamglin.phosphoricons.duotone.Eraser
import com.adamglin.phosphoricons.duotone.Export
import com.adamglin.phosphoricons.duotone.Eye
import com.adamglin.phosphoricons.duotone.EyeSlash
import com.adamglin.phosphoricons.duotone.Eyedropper
import com.adamglin.phosphoricons.duotone.FilePdf
import com.adamglin.phosphoricons.duotone.FilePlus
import com.adamglin.phosphoricons.duotone.Files
import com.adamglin.phosphoricons.duotone.FlipHorizontal
import com.adamglin.phosphoricons.duotone.FlipVertical
import com.adamglin.phosphoricons.duotone.FolderSimple
import com.adamglin.phosphoricons.duotone.FolderSimplePlus
import com.adamglin.phosphoricons.duotone.Function
import com.adamglin.phosphoricons.duotone.GearSix
import com.adamglin.phosphoricons.duotone.Hand
import com.adamglin.phosphoricons.duotone.Highlighter
import com.adamglin.phosphoricons.duotone.House
import com.adamglin.phosphoricons.duotone.Image
import com.adamglin.phosphoricons.duotone.Lasso
import com.adamglin.phosphoricons.duotone.Lightbulb
import com.adamglin.phosphoricons.duotone.LockSimple
import com.adamglin.phosphoricons.duotone.LockSimpleOpen
import com.adamglin.phosphoricons.duotone.MagnifyingGlass
import com.adamglin.phosphoricons.duotone.Moon
import com.adamglin.phosphoricons.duotone.PaintBucket
import com.adamglin.phosphoricons.duotone.Palette
import com.adamglin.phosphoricons.duotone.PenNib
import com.adamglin.phosphoricons.duotone.PencilSimple
import com.adamglin.phosphoricons.duotone.PencilSimpleLine
import com.adamglin.phosphoricons.duotone.Presentation
import com.adamglin.phosphoricons.duotone.Ruler
import com.adamglin.phosphoricons.duotone.Scissors
import com.adamglin.phosphoricons.duotone.Shapes
import com.adamglin.phosphoricons.duotone.Sparkle
import com.adamglin.phosphoricons.duotone.Stack
import com.adamglin.phosphoricons.duotone.Sun
import com.adamglin.phosphoricons.duotone.Swatches
import com.adamglin.phosphoricons.duotone.TextT
import com.adamglin.phosphoricons.duotone.Trash
import com.adamglin.phosphoricons.duotone.X
import com.adamglin.phosphoricons.fill.BookmarkSimple as FillBookmarkSimple

/**
 * PenLab's cohesive Phosphor Duotone icon registry.
 *
 * Every UI icon resolves lazily to a cached Phosphor [ImageVector] (20% secondary fill + 100%
 * crisp stroke), with filled variants reserved strictly for unambiguous active toggle states such
 * as bookmarked pages.
 */
object PenLabIcons {
  // History & canvas editing
  val Undo: ImageVector get() = PhosphorIcons.Duotone.ArrowArcLeft
  val Redo: ImageVector get() = PhosphorIcons.Duotone.ArrowArcRight
  val Clear: ImageVector get() = PhosphorIcons.Duotone.Broom
  val Eraser: ImageVector get() = PhosphorIcons.Duotone.Eraser
  val Lasso: ImageVector get() = PhosphorIcons.Duotone.Lasso
  val Pen: ImageVector get() = PhosphorIcons.Duotone.PenNib
  val Hand: ImageVector get() = PhosphorIcons.Duotone.Hand
  val Highlighter: ImageVector get() = PhosphorIcons.Duotone.Highlighter
  val PenTools: ImageVector get() = PhosphorIcons.Duotone.PencilSimple
  val Text: ImageVector get() = PhosphorIcons.Duotone.TextT
  val Fill: ImageVector get() = PhosphorIcons.Duotone.PaintBucket
  val Image: ImageVector get() = PhosphorIcons.Duotone.Image
  val Eyedropper: ImageVector get() = PhosphorIcons.Duotone.Eyedropper
  val ColorWheel: ImageVector get() = PhosphorIcons.Duotone.Swatches
  val Palette: ImageVector get() = PhosphorIcons.Duotone.Palette

  // Instruments & shapes
  val Ruler: ImageVector get() = PhosphorIcons.Duotone.Ruler
  val Protractor: ImageVector get() = PhosphorIcons.Duotone.CompassTool
  val Shapes: ImageVector get() = PhosphorIcons.Duotone.Shapes
  val Sparkles: ImageVector get() = PhosphorIcons.Duotone.Sparkle
  val Function: ImageVector get() = PhosphorIcons.Duotone.Function

  // Selection & clipboard actions
  val Copy: ImageVector get() = PhosphorIcons.Duotone.Copy
  val Cut: ImageVector get() = PhosphorIcons.Duotone.Scissors
  val Paste: ImageVector get() = PhosphorIcons.Duotone.ClipboardText
  val Duplicate: ImageVector get() = PhosphorIcons.Duotone.CopySimple
  val Delete: ImageVector get() = PhosphorIcons.Duotone.Trash
  val FlipHorizontal: ImageVector get() = PhosphorIcons.Duotone.FlipHorizontal
  val FlipVertical: ImageVector get() = PhosphorIcons.Duotone.FlipVertical

  // Navigation, chrome & view
  val Back: ImageVector get() = PhosphorIcons.Duotone.CaretLeft
  val Close: ImageVector get() = PhosphorIcons.Duotone.X
  val More: ImageVector get() = PhosphorIcons.Duotone.DotsThree
  val MoreVert: ImageVector get() = PhosphorIcons.Duotone.DotsThreeVertical
  val DragHandle: ImageVector get() = PhosphorIcons.Duotone.DotsSixVertical
  /** Regular weight on purpose: the duotone Plus/Minus fill a translucent box and read as a heavy double bar. */
  val Plus: ImageVector get() = PhosphorIcons.Regular.RegularPlus
  val Minus: ImageVector get() = PhosphorIcons.Regular.RegularMinus
  val ChevronDown: ImageVector get() = PhosphorIcons.Duotone.CaretDown
  val ChevronUp: ImageVector get() = PhosphorIcons.Duotone.CaretUp
  val ChevronRight: ImageVector get() = PhosphorIcons.Duotone.CaretRight
  val Rotate: ImageVector get() = PhosphorIcons.Duotone.ArrowsClockwise
  val Check: ImageVector get() = PhosphorIcons.Duotone.Check
  /** Bare bold checkmark stroke (no duotone background box) for compact circular swatches. */
  val CheckBare: ImageVector get() = PhosphorIcons.Bold.BoldCheck
  val Search: ImageVector get() = PhosphorIcons.Duotone.MagnifyingGlass
  val Home: ImageVector get() = PhosphorIcons.Duotone.House
  val FitWidth: ImageVector get() = PhosphorIcons.Duotone.ArrowsOutLineHorizontal
  val FullScreen: ImageVector get() = PhosphorIcons.Duotone.CornersOut
  val FullScreenExit: ImageVector get() = PhosphorIcons.Duotone.CornersIn
  val Present: ImageVector get() = PhosphorIcons.Duotone.Presentation
  val Lock: ImageVector get() = PhosphorIcons.Duotone.LockSimple
  val LockOpen: ImageVector get() = PhosphorIcons.Duotone.LockSimpleOpen

  // Pages, paper & layers
  val Pages: ImageVector get() = PhosphorIcons.Duotone.Files
  val Paper: ImageVector get() = PhosphorIcons.Duotone.Article
  val BookmarkFilled: ImageVector get() = PhosphorIcons.Fill.FillBookmarkSimple
  val BookmarkOutline: ImageVector get() = PhosphorIcons.Duotone.BookmarkSimple
  val Layers: ImageVector get() = PhosphorIcons.Duotone.Stack
  val Eye: ImageVector get() = PhosphorIcons.Duotone.Eye
  val EyeOff: ImageVector get() = PhosphorIcons.Duotone.EyeSlash

  // Files, folders, export & settings
  val Save: ImageVector get() = PhosphorIcons.Duotone.DownloadSimple
  val Export: ImageVector get() = PhosphorIcons.Duotone.Export
  val CloudSync: ImageVector get() = PhosphorIcons.Duotone.CloudArrowUp
  val Folder: ImageVector get() = PhosphorIcons.Duotone.FolderSimple
  val FolderPlus: ImageVector get() = PhosphorIcons.Duotone.FolderSimplePlus
  val FilePlus: ImageVector get() = PhosphorIcons.Duotone.FilePlus
  val Pdf: ImageVector get() = PhosphorIcons.Duotone.FilePdf
  val Rename: ImageVector get() = PhosphorIcons.Duotone.PencilSimpleLine
  val Move: ImageVector get() = PhosphorIcons.Duotone.ArrowsOutCardinal
  val Settings: ImageVector get() = PhosphorIcons.Duotone.GearSix
  val Idea: ImageVector get() = PhosphorIcons.Duotone.Lightbulb
  val Sun: ImageVector get() = PhosphorIcons.Duotone.Sun
  val Moon: ImageVector get() = PhosphorIcons.Duotone.Moon
  val DeviceTablet: ImageVector get() = PhosphorIcons.Duotone.DeviceTablet
}

// Top-level aliases preserved for concise imports across existing and new UI screens.
val UndoIcon: ImageVector get() = PenLabIcons.Undo
val RedoIcon: ImageVector get() = PenLabIcons.Redo
val ClearIcon: ImageVector get() = PenLabIcons.Clear
val EraserIcon: ImageVector get() = PenLabIcons.Eraser
val LassoIcon: ImageVector get() = PenLabIcons.Lasso
val CloseIcon: ImageVector get() = PenLabIcons.Close
val LockIcon: ImageVector get() = PenLabIcons.Lock
val LockOpenIcon: ImageVector get() = PenLabIcons.LockOpen
val RulerIcon: ImageVector get() = PenLabIcons.Ruler
val IdeaIcon: ImageVector get() = PenLabIcons.Idea
val TextIcon: ImageVector get() = PenLabIcons.Text
val FillIcon: ImageVector get() = PenLabIcons.Fill
val PaperIcon: ImageVector get() = PenLabIcons.Paper
val SearchIcon: ImageVector get() = PenLabIcons.Search
val FullScreenIcon: ImageVector get() = PenLabIcons.FullScreen
val FullScreenExitIcon: ImageVector get() = PenLabIcons.FullScreenExit
val PresentIcon: ImageVector get() = PenLabIcons.Present
val PenToolsIcon: ImageVector get() = PenLabIcons.PenTools
