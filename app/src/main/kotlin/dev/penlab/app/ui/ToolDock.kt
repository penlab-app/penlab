package dev.penlab.app.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import dev.penlab.app.ink.InkColors
import dev.penlab.app.ink.InkTool
import dev.penlab.app.ink.InkTools
import dev.penlab.app.ink.PenStyle
import dev.penlab.app.ui.theme.LocalPenLabColors
import dev.penlab.app.ui.theme.PenLabColors
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.PenLabLayout
import dev.penlab.app.ui.theme.PenLabSpacing
import dev.penlab.app.ui.theme.floatingChromeSurface
import dev.penlab.app.ui.theme.softShadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Where the tools live, and how they look there:
 *  - left or right edge: a rail that fills the whole edge, top to bottom;
 *  - top or bottom edge: a floating bar that stays wherever along the edge it was dropped
 *    (bottom-left keeps it away from a right hand);
 *  - a corner: a wheel. A round button sits in the corner; tap it and the tools fan out round it.
 *
 * Dragging the grip shrinks the tools into a ball that follows the finger; letting go turns it
 * into the shape for wherever it was dropped.
 */
enum class DockSide(val isCorner: Boolean = false, val isVertical: Boolean = false) {
  LEFT(isVertical = true),
  RIGHT(isVertical = true),
  TOP,
  BOTTOM,
  TOP_LEFT(isCorner = true),
  TOP_RIGHT(isCorner = true),
  BOTTOM_LEFT(isCorner = true),
  BOTTOM_RIGHT(isCorner = true),
}

/** What is on the toolbar, in order. Tools are numbered by their slot. */
sealed class DockItem(val key: Int) {
  // A data class on purpose: the wheel's gesture handler is keyed on the item list, and a list of
  // tools only compares equal (so the gesture survives a recomposition) if its items do.
  data class Tool(val index: Int) : DockItem(index)

  data object Undo : DockItem(-10)

  data object Redo : DockItem(-11)

  data object Paper : DockItem(PAPER_CELL)

  data object Instruments : DockItem(INSTRUMENTS_CELL)

  data object Clear : DockItem(-12)

  data object Layers : DockItem(LAYERS_CELL)

  data object More : DockItem(MORE_CELL)
}

/** Stands for the layers button where a cell number is needed. */
const val LAYERS_CELL = -3

/** Stands for the "More" toolbar button (Add from PDF, Add image, Highlighter, Text, Layers, Clear). */
const val MORE_CELL = -4

/**
 * The dock's state: where it is (kept between launches), whether it is being dragged, and
 * where each cell is on screen (so an options panel can open next to the cell it belongs to).
 */
@Stable
class ToolDock(context: Context) {
  private val prefs = context.getSharedPreferences("toolbar", Context.MODE_PRIVATE)

  var side by mutableStateOf(DockSide.entries.firstOrNull { it.name == prefs.getString("side", null) } ?: DockSide.TOP)
    private set

  /** Programmatically docks the toolbar to [newSide] (e.g. from Settings). */
  fun dockTo(newSide: DockSide) {
    side = newSide
    wheelOpen = false
    if (!newSide.isVertical && !newSide.isCorner) {
      along = 0.5f
    }
    prefs.edit().putString("side", side.name).putFloat("along", along).apply()
  }

  /** Top and bottom bars: where the bar's middle is, 0 = left edge, 1 = right edge. */
  var along by mutableFloatStateOf(prefs.getFloat("along", 0.5f))
    private set

  /** Corner wheel: open (tools fanned out) or just the round button. */
  var wheelOpen by mutableStateOf(false)

  /** Wheel: the cell a pull is pointing at (by [DockItem.key]), the button's middle, where each
   *  cell sits when open, and how far the finger has pulled from the middle. */
  internal var wheelHighlight by mutableStateOf<Int?>(null)
  internal var wheelHub = Offset.Zero
  internal var wheelTargets: Map<Int, Offset> = emptyMap()
  internal var wheelLocalTargets: Map<Int, Offset> = emptyMap()

  /** The wheel circle's centre (the screen corner), root pixels. */
  internal var wheelOrigin = Offset.Zero
  internal var pull by mutableStateOf(Offset.Zero)

  /** Page actions shown in the top bar (with the corner wheel): their centres, by key. */
  internal val topBarCells = HashMap<Int, Offset>()

  fun topBarCell(key: Int, centre: Offset) {
    topBarCells[key] = centre
    cellCenters[key] = centre // a press on them counts as on the toolbar
  }

  /** Where the open options panel is (root pixels), or empty. */
  internal var panelBounds = androidx.compose.ui.geometry.Rect.Zero

  /** Exact bounds of the docked bar or rail (root pixels), or empty for corner wheel. */
  internal var barBounds = androidx.compose.ui.geometry.Rect.Zero

  /** Both end grip centres (root pixels), keyed by grip slot (0 = start/top, 1 = end/bottom). */
  internal val gripCenters = HashMap<Int, Offset>()

  internal var cellReachPx = 0f

  /** The wheel disc's radius (px), and how far the gesture zones and the top buttons keep it from the screen's edges. */
  internal var wheelRadiusPx = 0f
  internal var gestureLeftPx = 0f
  internal var gestureRightPx = 0f
  internal var gestureBottomPx = 0f
  internal var topReservePx = 0f
  internal var hubHalfPx = 0f
  internal var edgeGapPx = 0f

  /** Works out the wheel's sizes and how far the edges are kept clear (needs [screenWidth] and [screenHeight]). */
  internal fun applyWheelMetrics(density: androidx.compose.ui.unit.Density, insets: DockInsets) {
    with(density) {
      wheelRadiusPx = DiscRadius.toPx()
      hubHalfPx = (HubSize / 2).toPx()
      edgeGapPx = EdgeGap.toPx()
      gestureLeftPx = WheelGeometry.edgeInset(insets.gestureLeft.toPx(), insets.left.toPx(), screenWidth.toFloat())
      gestureRightPx = WheelGeometry.edgeInset(insets.gestureRight.toPx(), insets.right.toPx(), screenWidth.toFloat())
      gestureBottomPx = WheelGeometry.edgeInset(insets.gestureBottom.toPx(), insets.bottom.toPx(), screenHeight.toFloat())
      topReservePx = (insets.top + (if (insets.topChromeVisible) insets.topButtons else 0.dp)).toPx()
    }
  }

  /** Where the wheel and its button sit for [corner] (root pixels), given the last measured screen. */
  internal fun cornerLayout(corner: DockSide): WheelGeometry.Corner =
    WheelGeometry.corner(
      onLeft = corner == DockSide.TOP_LEFT || corner == DockSide.BOTTOM_LEFT,
      onTop = corner == DockSide.TOP_LEFT || corner == DockSide.TOP_RIGHT,
      width = screenWidth.toFloat(),
      height = screenHeight.toFloat(),
      insetLeft = gestureLeftPx,
      insetRight = gestureRightPx,
      insetBottom = gestureBottomPx,
      topReserve = topReservePx,
      gap = edgeGapPx,
      hubHalf = hubHalfPx,
    )

  /** Rectangles kept out of the Back gesture while the wheel is open (root pixels); empty otherwise. */
  internal var wheelExclusion: List<WheelGeometry.PxRect> = emptyList()

  /**
   * True if a press at [p] (root pixels) is on the toolbar or its panel, so it must not close
   * them: on the bar/rail, a cell, either drag grip, the wheel button or open wheel, or the panel.
   */
  fun isOnDock(p: Offset): Boolean {
    if (!panelBounds.isEmpty && panelBounds.contains(p)) return true
    if (!side.isCorner && !barBounds.isEmpty && barBounds.contains(p)) return true
    if ((p - gripCenter).getDistance() < maxOf(cellReachPx * 1.3f, if (side.isCorner) hubHalfPx + 6f else 0f)) return true
    if (gripCenters.values.any { (it - p).getDistance() < cellReachPx * 1.2f }) return true
    if (cellCenters.values.any { (it - p).getDistance() < cellReachPx }) return true
    if (side.isCorner && wheelOpen) {
      val c = cornerLayout(side)
      val onLeft = side == DockSide.TOP_LEFT || side == DockSide.BOTTOM_LEFT
      val onTop = side == DockSide.TOP_LEFT || side == DockSide.TOP_RIGHT
      if (WheelGeometry.inQuarter(p, c.originX, c.originY, onLeft, onTop, wheelRadiusPx + 8f)) return true
    }
    if (side.isVertical) {
      val railEdge = if (side == DockSide.LEFT) cellReachPx * 2.2f else screenWidth - cellReachPx * 2.2f
      if (if (side == DockSide.LEFT) p.x < railEdge else p.x > railEdge) return true
    }
    return false
  }

  var dragging by mutableStateOf(false)
    private set

  /** Centre of the ball while dragging, in root pixels. */
  internal val ball = Animatable(Offset.Zero, Offset.VectorConverter)

  /** Centre of each cell on screen (root pixels), by [DockItem.key]. */
  internal val cellCenters = mutableStateMapOf<Int, Offset>()

  /** Where the last touched grip (or wheel hub) was seen (root pixels): the ball starts there. */
  internal var gripCenter = Offset.Zero

  internal var screenWidth = 0
  internal var screenHeight = 0
  internal var cornerReachPx = 0f

  internal suspend fun startDrag(from: Offset = gripCenter) {
    // Caught mid-flight: carry on from where the ball is now, never jump back.
    if (!(dragging && ball.isRunning)) ball.snapTo(from) else ball.stop()
    dragging = true
    wheelOpen = false
  }

  /** Where each shape's middle ends up (root pixels), so a thrown ball can fly there. */
  internal var railHalfPx = 0f
  internal var topBarY = 0f
  internal var bottomBarY = 0f

  /** The last drag step, smoothed: how fast and which way the ball is moving (pixels per event). */
  internal var velocity by mutableStateOf(Offset.Zero)

  internal suspend fun dragBy(delta: Offset) {
    velocity = velocity * 0.6f + delta * 0.4f
    ball.snapTo(ball.value + delta)
  }

  /**
   * Lets go. The throw's speed is projected forward (as a flick of a scroll list would glide)
   * and the edge or corner nearest that point wins, so a flick towards a corner lands there.
   * The ball then flies to its new home, keeping the finger's speed, and becomes that shape.
   */
  internal suspend fun drop(release: Offset) {
    velocity = Offset.Zero
    val w = screenWidth.toFloat()
    val h = screenHeight.toFloat()
    val projected = Offset(
      (ball.value.x + project(release.x)).coerceIn(0f, w),
      (ball.value.y + project(release.y)).coerceIn(0f, h),
    )
    val target = targetFor(projected)
    val newAlong = if (!target.isCorner && !target.isVertical) (projected.x / w.coerceAtLeast(1f)).coerceIn(0.05f, 0.95f) else along
    val home =
      when (target) {
        DockSide.LEFT -> Offset(railHalfPx, ball.value.y.coerceIn(0f, h))
        DockSide.RIGHT -> Offset(w - railHalfPx, ball.value.y.coerceIn(0f, h))
        DockSide.TOP -> Offset(newAlong * w, topBarY)
        DockSide.BOTTOM -> Offset(newAlong * w, bottomBarY)
        DockSide.TOP_LEFT, DockSide.TOP_RIGHT, DockSide.BOTTOM_LEFT, DockSide.BOTTOM_RIGHT ->
          cornerLayout(target).let { Offset(it.hubX, it.hubY) } // where the button will sit, clear of the gesture zones
      }
    // A little bounce, because a throw carried it there.
    ball.animateTo(home, Motion.spring(dampingRatio = 0.8f, stiffness = 380f), initialVelocity = release)
    side = target
    along = newAlong
    dragging = false
    prefs.edit().putString("side", side.name).putFloat("along", along).apply()
  }

  /** How far a throw glides: the same deceleration as a scrolled list (0.998 per ms). */
  private fun project(velocityPerSecond: Float): Float = velocityPerSecond / 1000f * 0.998f / (1f - 0.998f) * 0.6f

  /** Which shape a drop at [p] makes: a corner if close to two edges, else the nearest edge. */
  internal fun targetFor(p: Offset): DockSide {
    val w = screenWidth.toFloat()
    val h = screenHeight.toFloat()
    val r = cornerReachPx
    val left = p.x < r
    val right = p.x > w - r
    val top = p.y < r
    val bottom = p.y > h - r
    return when {
      left && top -> DockSide.TOP_LEFT
      right && top -> DockSide.TOP_RIGHT
      left && bottom -> DockSide.BOTTOM_LEFT
      right && bottom -> DockSide.BOTTOM_RIGHT
      else -> {
        val distances = listOf(DockSide.LEFT to p.x, DockSide.RIGHT to w - p.x, DockSide.TOP to p.y, DockSide.BOTTOM to h - p.y)
        distances.minBy { it.second }.first
      }
    }
  }
}

/** Makes [this] one of the dock's end grips: dragging either handle moves the whole toolbar. */
internal fun Modifier.dockGrip(dock: ToolDock, gripId: Int, scope: CoroutineScope, onDragStart: () -> Unit): Modifier =
  this
    .onGloballyPositioned {
      val center = it.boundsInRoot().center
      dock.gripCenters[gripId] = center
      if (gripId == 0) dock.gripCenter = center
    }
    .pointerInput(dock, gripId) {
      val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
      detectDragGestures(
        onDragStart = {
          tracker.resetTracking()
          onDragStart()
          val origin = dock.gripCenters[gripId] ?: dock.gripCenter
          dock.gripCenter = origin
          scope.launch { dock.startDrag(origin) }
        },
        onDragEnd = {
          val v = tracker.calculateVelocity()
          scope.launch { dock.drop(Offset(v.x, v.y)) }
        },
        onDragCancel = { scope.launch { dock.drop(Offset.Zero) } },
      ) { change, amount ->
        change.consume()
        tracker.addPosition(change.uptimeMillis, change.position)
        scope.launch { dock.dragBy(amount) }
      }
    }

// ---- Drawing the dock ------------------------------------------------------------------------

/**
 * The toolbar in its current shape, the ball while it is dragged, and the open options panel
 * placed beside the cell it belongs to. Fills the screen; only its pieces take touches.
 */
@Composable
fun DockedToolbar(
  dock: ToolDock,
  items: List<DockItem>,
  tools: List<InkTool>,
  selectedIndex: Int,
  darkPage: Boolean,
  mmPerDp: Float,
  canUndo: Boolean,
  canRedo: Boolean,
  canClear: Boolean,
  onItem: (DockItem) -> Unit,
  isPalm: () -> Boolean,
  scope: CoroutineScope,
  onDragStart: () -> Unit,
  insets: DockInsets,
  panelKey: Int?,
  onSidesCovered: (left: Float, right: Float) -> Unit,
  modifier: Modifier = Modifier,
  instrumentsActive: Boolean = false,
  panel: @Composable () -> Unit,
) {
  val side = dock.side
  // The new shape grows in where the ball was let go.
  val appear = remember(side) { Animatable(if (dock.cellCenters.isEmpty()) 1f else 0f) }
  androidx.compose.runtime.LaunchedEffect(side, dock.dragging) {
    if (!dock.dragging) appear.animateTo(1f, Motion.spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
  }
  val origin =
    when (side) {
      DockSide.LEFT -> TransformOrigin(0f, 0.5f)
      DockSide.RIGHT -> TransformOrigin(1f, 0.5f)
      DockSide.TOP -> TransformOrigin(0.5f, 0f)
      DockSide.BOTTOM -> TransformOrigin(0.5f, 1f)
      DockSide.TOP_LEFT -> TransformOrigin(0f, 0f)
      DockSide.TOP_RIGHT -> TransformOrigin(1f, 0f)
      DockSide.BOTTOM_LEFT -> TransformOrigin(0f, 1f)
      DockSide.BOTTOM_RIGHT -> TransformOrigin(1f, 1f)
    }
  val shapeLayer =
    Modifier.graphicsLayer {
      val p = appear.value
      alpha = if (dock.dragging) 0f else p.coerceIn(0f, 1f)
      scaleX = 0.85f + 0.15f * p
      scaleY = 0.85f + 0.15f * p
      transformOrigin = origin
    }
  val isHorizontalBar = !side.isVertical && !side.isCorner
  val cell: @Composable (DockItem, Boolean) -> Unit = { item, round ->
    DockCell(
      item = item,
      tools = tools,
      selectedIndex = selectedIndex,
      darkPage = darkPage,
      mmPerDp = mmPerDp,
      canUndo = canUndo,
      canRedo = canRedo,
      canClear = canClear,
      instrumentsActive = instrumentsActive,
      openPanelKey = panelKey,
      round = round,
      compactHorizontal = isHorizontalBar,
      dock = dock,
      onItem = onItem,
    )
  }
  val startGrip = Modifier.dockGrip(dock, gripId = 0, scope = scope, onDragStart = onDragStart)
  val endGrip = Modifier.dockGrip(dock, gripId = 1, scope = scope, onDragStart = onDragStart)

  val density = androidx.compose.ui.platform.LocalDensity.current
  androidx.compose.runtime.SideEffect {
    val rail = with(density) { (RailWidth + 8.dp).toPx() }
    when {
      dock.dragging -> Unit
      side == DockSide.LEFT -> onSidesCovered(rail, 0f)
      side == DockSide.RIGHT -> onSidesCovered(0f, rail)
      else -> onSidesCovered(0f, 0f)
    }
  }
  // Keep the wheel's edge clearances current when insets change without the screen resizing.
  androidx.compose.runtime.SideEffect { if (dock.screenWidth > 0) dock.applyWheelMetrics(density, insets) }
  androidx.compose.runtime.DisposableEffect(side) {
    if (side.isCorner) dock.barBounds = androidx.compose.ui.geometry.Rect.Zero
    onDispose {}
  }

  // Raised above the other chrome while a panel or the wheel is open. Read here, not in the
  // screen's composable: opening the wheel must recompose only the toolbar, never the whole
  // screen (that restarted the wheel's gesture and made the opening stutter).
  val raised = dock.dragging || panelKey != null || dock.wheelOpen
  Box(
    modifier.zIndex(if (raised) 2.5f else 1.5f).onGloballyPositioned {
      dock.screenWidth = it.size.width
      dock.screenHeight = it.size.height
      with(density) {
        dock.cornerReachPx = CornerReach.toPx()
        dock.cellReachPx = 26.dp.toPx()
        dock.applyWheelMetrics(density, insets)
        dock.railHalfPx = (RailWidth / 2).toPx()
        dock.topBarY = (insets.top + EdgeGap + BarHeight / 2).toPx()
        dock.bottomBarY = it.size.height - (insets.bottom + EdgeGap + BarHeight / 2).toPx()
      }
    }
  ) {
    when {
      side.isVertical -> Rail(dock, side, items, tools, insets, startGrip, endGrip, isPalm, cell, shapeLayer.align(if (side == DockSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd))
      side.isCorner -> Wheel(dock, side, items, tools, selectedIndex, darkPage, mmPerDp, insets, scope, onDragStart, onItem, isPalm, cell, shapeLayer)
      else -> Bar(dock, side, items, tools, insets, startGrip, endGrip, isPalm, cell, shapeLayer)
    }
    if (dock.dragging) DragBall(dock, tools.getOrNull(selectedIndex), darkPage, mmPerDp)

    if (panelKey != null && !dock.dragging) {
      PanelBeside(dock, side, panelKey, panel)
    }
  }
}

/** Space the dock must leave for system bars and the left/right floating top chrome islands. */
data class DockInsets(
  val top: Dp,
  val bottom: Dp,
  val left: Dp,
  val right: Dp,
  val topButtons: Dp,
  val topRowLeftClearance: Dp = 0.dp,
  val topRowRightClearance: Dp = 0.dp,
  val topChromeVisible: Boolean = true,
  /** System gesture zones (Back on the sides, Home at the bottom): the wheel keeps clear of them. */
  val gestureLeft: Dp = 0.dp,
  val gestureRight: Dp = 0.dp,
  val gestureBottom: Dp = 0.dp,
)

/** Left or right: a rail filling the whole edge, tools centred on it, with handles at both ends. */
@Composable
private fun Rail(
  dock: ToolDock,
  side: DockSide,
  items: List<DockItem>,
  tools: List<InkTool>,
  insets: DockInsets,
  startGrip: Modifier,
  endGrip: Modifier,
  isPalm: () -> Boolean,
  cell: @Composable (DockItem, Boolean) -> Unit,
  modifier: Modifier,
) {
  val edgeInset = if (side == DockSide.LEFT) insets.left else insets.right
  Box(
    modifier
      .fillMaxHeight()
      .width(RailWidth + edgeInset)
      .onGloballyPositioned { dock.barBounds = it.boundsInRoot() }
      .background(PenLabComponentTokens.chromePillBg)
      .drawInnerEdge(side)
      .ignorePalm(isPalm)
      .semantics { contentDescription = "Drawing tools" },
  ) {
    Column(
      Modifier.fillMaxHeight()
        .padding(top = insets.top, bottom = insets.bottom, start = if (side == DockSide.LEFT) edgeInset else 0.dp)
        .width(RailWidth)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Grip(vertical = true, modifier = startGrip)
      DockCells(items, tools, vertical = true, cell)
      Grip(vertical = true, modifier = endGrip)
    }
  }
}

/** A hairline on the rail's page side, so it reads as a panel set into the edge. */
private fun Modifier.drawInnerEdge(side: DockSide): Modifier =
  drawBehind {
    val x = if (side == DockSide.LEFT) size.width - 0.5f else 0.5f
    drawLine(PenLabColors.hairline, Offset(x, 0f), Offset(x, size.height), 1f)
  }

/**
 * Top or bottom: a floating capsule pill (`48.dp` tall) with `||` drag handles on both ends.
 *
 * When [side] is [DockSide.TOP] on wide landscape (`>= 1060.dp`) and the pill fits cleanly between
 * the left and right floating top islands, it sits inline on the exact same top row (`statusBar + 12dp`)
 * for a balanced 3-island header. On narrower viewports or ultra-wide customized bars, it drops
 * cleanly onto row 2 below the top islands with zero overlap.
 */
@Composable
private fun Bar(
  dock: ToolDock,
  side: DockSide,
  items: List<DockItem>,
  tools: List<InkTool>,
  insets: DockInsets,
  startGrip: Modifier,
  endGrip: Modifier,
  isPalm: () -> Boolean,
  cell: @Composable (DockItem, Boolean) -> Unit,
  modifier: Modifier,
) {
  val shape = CircleShape
  val isDark = LocalPenLabColors.current.isDark
  Layout(
    content = {
      Row(
        modifier
          .height(BarHeight)
          .onGloballyPositioned { dock.barBounds = it.boundsInRoot() }
          .floatingChromeSurface(
            shape = shape,
            background = PenLabComponentTokens.chromePillBg,
            elevation = 8.dp,
            subtleLightHairline = true,
            borderColor = PenLabComponentTokens.chromePillBorder,
            isDark = isDark,
          )
          .ignorePalm(isPalm)
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 2.dp)
          .semantics { contentDescription = "Drawing tools" },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Grip(vertical = false, modifier = startGrip)
        DockCells(items, tools, vertical = false, cell)
        Grip(vertical = false, modifier = endGrip)
      }
    }
  ) { measurables, constraints ->
    val gap = EdgeGap.roundToPx()
    val left = insets.left.roundToPx() + gap
    val right = constraints.maxWidth - insets.right.roundToPx() - gap
    val bar = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = (right - left).coerceAtLeast(0)))
    layout(constraints.maxWidth, constraints.maxHeight) {
      val centre = dock.along * constraints.maxWidth
      val screenWidthDp = constraints.maxWidth.toDp()
      val topRowY = (insets.top + EdgeGap).roundToPx()
      val row2Y = (insets.top + insets.topButtons).roundToPx()

      val (x, y) =
        if (side == DockSide.TOP) {
          if (!insets.topChromeVisible) {
            val bx = (centre - bar.width / 2f).roundToInt().coerceIn(left, maxOf(left, right - bar.width))
            bx to topRowY
          } else {
            val inlineLeft = left + insets.topRowLeftClearance.roundToPx() + gap
            val inlineRight = right - insets.topRowRightClearance.roundToPx() - gap
            val fitsInline = screenWidthDp >= INLINE_TOP_ROW_MIN_WIDTH && (inlineRight - inlineLeft) >= bar.width
            if (fitsInline) {
              val bx = (centre - bar.width / 2f).roundToInt().coerceIn(inlineLeft, maxOf(inlineLeft, inlineRight - bar.width))
              bx to topRowY
            } else {
              val bx = (centre - bar.width / 2f).roundToInt().coerceIn(left, maxOf(left, right - bar.width))
              bx to row2Y
            }
          }
        } else {
          val bx = (centre - bar.width / 2f).roundToInt().coerceIn(left, maxOf(left, right - bar.width))
          val by = constraints.maxHeight - insets.bottom.roundToPx() - gap - bar.height
          bx to by
        }
      if (side == DockSide.TOP) {
        dock.topBarY = y + bar.height / 2f
      }
      bar.place(x, y)
    }
  }
}

/**
 * A corner: a round button showing the current tool, and beside it, when open, the wheel: a
 * quarter circle with rounded corners. Its corner holds the current tool;
 * the inner ring holds every tool and scrolls along the arc with a swipe (it keeps gliding, and
 * springs back at the ends); the outer ring holds paper, ruler and clear. Nothing ever shows
 * outside the quarter. It opens out along its two edges with a little overshoot, and folds back
 * the same way.
 *
 * The button:
 *  - tap: open or close;
 *  - press and pull: the wheel opens, the cell pointed at grows (with a tick), letting go picks it;
 *  - hold still for half a second: the button lifts (a buzz) and drags the toolbar elsewhere.
 */
@Composable
private fun Wheel(
  dock: ToolDock,
  side: DockSide,
  items: List<DockItem>,
  tools: List<InkTool>,
  selectedIndex: Int,
  darkPage: Boolean,
  mmPerDp: Float,
  insets: DockInsets,
  scope: CoroutineScope,
  onDragStart: () -> Unit,
  onItem: (DockItem) -> Unit,
  isPalm: () -> Boolean,
  cell: @Composable (DockItem, Boolean) -> Unit,
  modifier: Modifier,
) {
  val view = androidx.compose.ui.platform.LocalView.current
  val density = androidx.compose.ui.platform.LocalDensity.current
  val scheme = MaterialTheme.colorScheme
  val isDarkTheme = LocalPenLabColors.current.isDark
  val wheelCore = scheme.surfaceContainerHighest
  val wheelGroove = scheme.surfaceContainerLow
  val wheelInnerRim = scheme.outlineVariant
  val hubWell = hubWellColor(tools.getOrNull(selectedIndex), darkPage, scheme, isDarkTheme)
  val ringItems = items.filter { it is DockItem.Tool || it == DockItem.Instruments || it == DockItem.More || it == DockItem.Undo || it == DockItem.Redo }
  // Opening and closing carry momentum: a spring that overshoots a little and settles.
  val open = remember { Animatable(0f) }
  androidx.compose.runtime.LaunchedEffect(dock.wheelOpen) {
    open.animateTo(if (dock.wheelOpen) 1f else 0f, Motion.spring(dampingRatio = 0.62f, stiffness = 420f))
  }
  // How far the tool ring is scrolled along the arc, in degrees.
  val scroll = remember { Animatable(0f) }
  // How far it can scroll: until the last tool reaches the last fully visible spot.
  val span = ((ringItems.size - 1) * RingStepDeg - (RingEndDeg - RingStartDeg)).coerceAtLeast(0f)
  val spanState = androidx.compose.runtime.rememberUpdatedState(span)
  val highlighted = dock.wheelHighlight
  val lifted = remember { Animatable(1f) }
  val onLeft = side == DockSide.TOP_LEFT || side == DockSide.BOTTOM_LEFT
  val onTop = side == DockSide.TOP_LEFT || side == DockSide.TOP_RIGHT
  // The disc is cut to its quarter at the gesture-safe lines (not by the screen edge any more).
  val shape = remember(onLeft, onTop) { QuarterShape(onLeft, onTop) }
  // The press-and-pull handler below lives for the whole gesture. Whatever it reads that can change
  // while it runs comes through these, so a recomposition never has to restart it.
  val currentItems by androidx.compose.runtime.rememberUpdatedState(items)
  val currentOnItem by androidx.compose.runtime.rememberUpdatedState(onItem)
  val currentOnDragStart by androidx.compose.runtime.rememberUpdatedState(onDragStart)
  // A dial, not a list: a light tick each time a tool passes a slot (15 Nov wheel redesign).
  androidx.compose.runtime.LaunchedEffect(Unit) {
    androidx.compose.runtime.snapshotFlow { kotlin.math.round(scroll.value / RingStepDeg).toInt() }
      .collect { if (dock.wheelOpen) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }
  }
  // While the wheel is open its area is kept out of the system Back gesture, so dragging on it near
  // a side edge never navigates away. Cleared when it folds, and when it leaves (docked elsewhere).
  androidx.compose.runtime.DisposableEffect(dock.wheelOpen, side) {
    setGestureExclusion(view, if (dock.wheelOpen) dock.wheelExclusion else emptyList())
    onDispose { setGestureExclusion(view, emptyList()) }
  }

  Layout(
    // After every layout (the wheel moved, the screen turned), refresh the rectangles if open.
    modifier = modifier.onGloballyPositioned { if (dock.wheelOpen) setGestureExclusion(view, dock.wheelExclusion) },
    content = {
      // The wheel: a quarter disc turning round the (gesture-safe) corner, so the screen edges cut it to a
      // quarter. Everything inside is clipped to the circle.
      Box(
        Modifier.size(DiscRadius * 2)
          // Touches are read before the opening animation's scale (positions stay real numbers),
          // and only while open, so the folded wheel never blocks the pen.
          .then(if (!dock.wheelOpen) Modifier else Modifier.pointerInput(onLeft, onTop) {
            // Swipe along the arc to scroll the tool ring; it glides on and springs back at the ends.
            // Angle round the circle's centre (the screen corner): 0° along the bottom/top edge,
            // 90° along the side edge.
            fun angleOf(p: Offset): Float {
              val c = size.width / 2f
              val u = if (onLeft) p.x - c else c - p.x
              val v = if (onTop) p.y - c else c - p.y
              return Math.toDegrees(kotlin.math.atan2(v.toDouble(), u.toDouble())).toFloat()
            }
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false)
              val slop = viewConfiguration.touchSlop
              val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
              var dragging = false
              var last = angleOf(down.position)
              var turned = 0f
              while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (!dragging && (change.position - down.position).getDistance() > slop) {
                  dragging = true
                  scope.launch { scroll.stop() }
                  last = angleOf(change.position)
                }
                if (!dragging) continue
                change.consume() // a swipe, not a tap on a cell
                val a = angleOf(change.position)
                val d = a - last
                last = a
                turned += d
                tracker.addPosition(change.uptimeMillis, Offset(0f, turned))
                // Past the ends it follows only part way, then springs back on release.
                val max = spanState.value
                val next = scroll.value + d
                val resisted = if (next > 0f || next < -max) scroll.value + d * 0.35f else next
                scope.launch { scroll.snapTo(resisted) }
              }
              if (dragging) {
                val velocity = tracker.calculateVelocity().y
                scope.launch {
                  // Lands with a tool exactly in a slot (or at the end), like a dial's detent.
                  val free = (scroll.value + velocity * 0.25f).coerceIn(-spanState.value, 0f)
                  val target = (kotlin.math.round(free / RingStepDeg) * RingStepDeg).coerceIn(-spanState.value, 0f)
                  scroll.animateTo(target, Motion.spring(dampingRatio = 0.8f, stiffness = 200f), initialVelocity = velocity)
                }
              }
            }
          })
          .graphicsLayer {
            val p = open.value.coerceAtLeast(0f)
            scaleX = p
            scaleY = p
            alpha = (p * 3f).coerceIn(0f, 1f)
            transformOrigin = TransformOrigin(0.5f, 0.5f) // grows out from the corner
            this.shape = shape
            clip = true
            shadowElevation = 18.dp.toPx() * p.coerceAtMost(1f) // lifts off the page as it opens
          }
          .drawBehind {
            // Depth instead of a flat plate: a little lighter at the corner, darker at the rim, and
            // a recessed groove the tools run in, so the ring reads as something that turns.
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
              androidx.compose.ui.graphics.Brush.radialGradient(
                0f to wheelCore, 1f to PenLabColors.toolbar, center = c, radius = size.width / 2f,
              ),
            )
            val ring = RingRadius.toPx()
            val band = WheelCell.toPx() + 10.dp.toPx()
            drawCircle(wheelGroove, radius = ring, center = c, style = Stroke(band))
            drawCircle(PenLabColors.hairline, radius = ring + band / 2f, center = c, style = Stroke(1.dp.toPx()))
            drawCircle(wheelInnerRim, radius = ring - band / 2f, center = c, style = Stroke(1.dp.toPx()))
          }
          .border(1.dp, PenLabColors.hairline, shape)
          .ignorePalm(isPalm)
      ) {
        Layout(content = {
          for (item in ringItems) {
            val hot = highlighted == item.key
            val grow by animateFloatAsState(if (hot) 1.22f else 1f, Motion.spring(dampingRatio = 0.6f, stiffness = 900f), label = "hot")
            Box(
              Modifier.graphicsLayer {
                scaleX = grow
                scaleY = grow
              }.then(if (hot) Modifier.background(PenLabColors.optionSelected, CircleShape) else Modifier)
            ) { cell(item, true) }
          }
        }) { measurables, constraints ->
          val loose = constraints.copy(minWidth = 0, minHeight = 0)
          val placeables = measurables.map { it.measure(loose) }
          val r = DiscRadius.toPx()
          layout((2 * r).roundToInt(), (2 * r).roundToInt()) {
            // (u, v): from the circle's centre (the screen corner) along the bottom/top edge and
            // along the side edge, into the screen.
            fun place(index: Int, u: Float, v: Float) {
              val p = placeables[index]
              val x = r + if (onLeft) u else -u
              val y = r + if (onTop) v else -v
              p.place((x - p.width / 2f).roundToInt(), (y - p.height / 2f).roundToInt())
            }
            val targets = HashMap<Int, Offset>()
            fun polar(index: Int, item: DockItem, radius: Float, angleDeg: Float) {
              val (u, v) = WheelGeometry.ringSlot(radius, angleDeg)
              // Fan-out: each tool trails the one before it out along its spoke while opening.
              // Pull targets use the settled spot, so a quick pull during the animation still hits.
              val k = (index - (-scroll.value / RingStepDeg)).coerceIn(0f, 3f) * FAN_LAG
              val lag = ((open.value - k) / (1f - k)).coerceIn(0f, 1.06f) // 1 at rest; the spring's overshoot shows
              val f = 0.55f + 0.45f * lag
              place(index, u * f, v * f)
              if (WheelGeometry.slotOnScreen(angleDeg)) targets[item.key] = Offset(u, v) // on screen
            }
            // Tool ring: the first tool along the bottom edge, the rest climbing towards the side
            // edge, shifted by the scroll.
            // Tools past either end are round the circle, behind the screen edge, until spun in.
            ringItems.forEachIndexed { i, item -> polar(i, item, RingRadius.toPx(), RingStartDeg + i * RingStepDeg + scroll.value) } // tools move with the finger
            // Pull targets in screen terms, refreshed whenever the ring moves.
            val o = dock.wheelOrigin
            dock.wheelTargets = targets.mapValues { (_, uv) -> Offset(o.x + if (onLeft) uv.x else -uv.x, o.y + if (onTop) uv.y else -uv.y) }
          }
        }
      }
      // The button, on top.
      val shape = CircleShape
      Box(
        Modifier
          .onGloballyPositioned { dock.gripCenter = it.boundsInRoot().center }
          .ignorePalm(isPalm)
          .graphicsLayer {
            scaleX = lifted.value
            scaleY = lifted.value
          }
          .size(HubSize)
          .softShadow(shape, elevation = if (lifted.value > 1.01f) 14.dp else 8.dp)
          .clip(shape)
          .hubFace(coreColor = wheelCore, wellColor = hubWell)
          // Keyed on the dock and its corner only. It used to be keyed on the item list too, which is a
          // fresh list on every recomposition; opening the wheel recomposed the screen, the key
          // changed, and the gesture was cancelled half way (the "do it twice" bug).
          .pointerInput(dock, onLeft, onTop) {
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false)
              val half = size.width / 2f
              val slop = viewConfiguration.touchSlop
              var mode = 0 // 0 undecided, 1 pulling to a cell, 2 moving the toolbar
              val wasOpen = dock.wheelOpen
              // Undecided until the finger moves (pull) or holds still long enough (move).
              val decided = withTimeoutOrNull(HOLD_TO_MOVE_MS) {
                while (true) {
                  val event = awaitPointerEvent()
                  val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                  if (!change.pressed) return@withTimeoutOrNull false // a tap
                  if ((change.position - down.position).getDistance() > slop) return@withTimeoutOrNull true
                }
                @Suppress("UNREACHABLE_CODE") false
              }
              when (decided) {
                false -> {
                  dock.wheelOpen = !dock.wheelOpen // tap
                  return@awaitEachGesture
                }
                true -> mode = 1
                null -> mode = 2
              }
              if (mode == 2) {
                // Lift: the button grows and buzzes, then drags the whole toolbar.
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                scope.launch { lifted.animateTo(1.15f, Motion.spring(0.6f, 900f)) }
                currentOnDragStart()
                scope.launch { dock.startDrag() }
                val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                var last = down.position
                while (true) {
                  val event = awaitPointerEvent()
                  val change = event.changes.firstOrNull { it.id == down.id } ?: break
                  tracker.addPosition(change.uptimeMillis, change.position)
                  if (!change.pressed) break
                  val delta = change.position - last
                  last = change.position
                  change.consume()
                  scope.launch { dock.dragBy(delta) }
                }
                val v = tracker.calculateVelocity()
                scope.launch {
                  lifted.snapTo(1f)
                  dock.drop(Offset(v.x, v.y))
                }
                return@awaitEachGesture
              }
              // Pulling: open, follow the finger, pick on release.
              dock.wheelOpen = true
              val hub = dock.wheelHub
              val dead = with(density) { PullDeadZone.toPx() }
              val reach = with(density) { PullReach.toPx() }
              val ringPx = with(density) { RingRadius.toPx() }
              var at = hub
              var released = false
              while (!released) {
                // Wake every frame even without movement, so holding near an edge keeps scrolling.
                val event = withTimeoutOrNull(16L) { awaitPointerEvent() }
                if (event != null) {
                  val change = event.changes.firstOrNull { it.id == down.id } ?: break
                  change.consume()
                  at = hub + (change.position - Offset(half, half))
                  dock.pull = (change.position - Offset(half, half))
                  if (!change.pressed) released = true
                }
                // Near either screen edge (out by the tools), the ring rolls on by itself: faster
                // the closer to the edge, bringing the hidden tools round.
                if (!released) {
                  val o = dock.wheelOrigin
                  val u = if (onLeft) at.x - o.x else o.x - at.x
                  val v = if (onTop) at.y - o.y else o.y - at.y
                  if (kotlin.math.hypot(u, v) > ringPx * 0.6f) {
                    val phi = Math.toDegrees(kotlin.math.atan2(v.toDouble(), u.toDouble())).toFloat()
                    val step = when {
                      phi < EDGE_ZONE_DEG -> (EDGE_ZONE_DEG - phi) / EDGE_ZONE_DEG // near the bottom/top edge: tools come up
                      phi > 90f - EDGE_ZONE_DEG -> -(phi - (90f - EDGE_ZONE_DEG)) / EDGE_ZONE_DEG // near the side edge: tools come down
                      else -> 0f
                    } * AUTO_SCROLL_DEG_PER_FRAME
                    if (step != 0f) {
                      val next = (scroll.value + step).coerceIn(-spanState.value, 0f)
                      if (next != scroll.value) scope.launch { scroll.snapTo(next) }
                    }
                  }
                }
                val target = WheelGeometry.pickTarget(dock.wheelTargets, at, hub, dead, reach)
                if (target != dock.wheelHighlight) {
                  dock.wheelHighlight = target
                  if (target != null) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                }
              }
              val picked = dock.wheelHighlight?.let { key -> currentItems.firstOrNull { it.key == key } }
              dock.wheelHighlight = null
              dock.pull = Offset.Zero
              if (picked != null) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                currentOnItem(picked)
                // Undo and redo keep the wheel open for another go; everything else closes it.
                if (picked != DockItem.Undo && picked != DockItem.Redo) dock.wheelOpen = false
              } else if (!wasOpen) {
                dock.wheelOpen = false // pulled out and back: nothing picked, as it was
              }
            }
          }
          .semantics {
            contentDescription = "Tools. Tap to " + (if (dock.wheelOpen) "close" else "open") + "; press and pull to pick; hold to move."
          },
        contentAlignment = Alignment.Center,
      ) {
        // The tool sample leans a little towards the finger while pulling.
        val pull = dock.pull
        tools.getOrNull(selectedIndex)?.let {
          ToolGlyph(
            it, darkPage, mmPerDp,
            minStroke = 3.dp,
            modifier = Modifier.size(40.dp, 28.dp).offset {
              val len = pull.getDistance()
              val max = 8.dp.toPx()
              if (len < 1f) IntOffset.Zero else (pull * (minOf(len * 0.12f, max) / len)).round()
            },
          )
        }
      }
    },
  ) { measurables, constraints ->
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val wheel = measurables[0].measure(loose)
    val hub = measurables[1].measure(loose)
    layout(constraints.maxWidth, constraints.maxHeight) {
      // The wheel turns round a point pulled in from the screen corner by the system gesture zones
      // (Back on the sides, Home at the bottom) or, at the top, by the top buttons; the button sits
      // the same distance from both of that point's axes. Nothing interactive is in a gesture zone.
      dock.screenWidth = constraints.maxWidth
      dock.screenHeight = constraints.maxHeight
      dock.applyWheelMetrics(this, insets)
      val c = dock.cornerLayout(side)
      val hubHalf = HubSize.roundToPx() / 2
      dock.wheelHub = Offset(c.hubX, c.hubY)
      val origin = Offset(c.originX, c.originY)
      dock.wheelOrigin = origin
      dock.wheelExclusion = WheelGeometry.exclusionRects(
        onLeft, onTop, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), c,
        dock.wheelRadiusPx, MaxExclusionHeight.roundToPx().toFloat(),
      )
      wheel.place((origin.x - wheel.width / 2f).roundToInt(), (origin.y - wheel.height / 2f).roundToInt())
      hub.place((c.hubX - hubHalf).roundToInt(), (c.hubY - hubHalf).roundToInt()) // on top of the disc
      // (pull targets are worked out with the ring, in its own layout)
    }
  }
}


// Sizes live in WheelGeometry so they are unit-tested: about 25% bigger than the first wheel, with
// ring cells of 50 dp (the touch-target minimum is 48 dp) about 59 dp apart.
private val WheelCell = WheelGeometry.CELL_DP.dp

/** The tool ring's radius round the wheel's centre: clear of the round button. */
private val RingRadius = WheelGeometry.RING_RADIUS_DP.dp

/** The disc: the ring plus half a tool plus a small margin. */
private val DiscRadius = WheelGeometry.DISC_RADIUS_DP.dp

/** Press-and-pull: no cell is pointed at within [PullDeadZone] of the button; a cell counts within [PullReach]. */
private val PullDeadZone = 55.dp
private val PullReach = 104.dp

/** Android honours at most 200 dp of exclusion per edge. */
private val MaxExclusionHeight = WheelGeometry.MAX_EXCLUSION_HEIGHT_DP.dp

/** Keeps [rects] (root pixels) out of the system Back gesture; an empty list clears it. */
private fun setGestureExclusion(view: android.view.View, rects: List<WheelGeometry.PxRect>) {
  view.systemGestureExclusionRects = rects.map { android.graphics.Rect(it.left, it.top, it.right, it.bottom) }
}

/**
 * The wheel disc cut to the quarter that is on the screen side of both axes through its centre:
 * a pie slice, so the tools parked round the back of the circle are never drawn or touched.
 */
private class QuarterShape(private val onLeft: Boolean, private val onTop: Boolean) : androidx.compose.ui.graphics.Shape {
  override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.width / 2f
    // Degrees clockwise from +x (y points down): the sweep that covers the screen-side quadrant.
    val start = when {
      onTop && onLeft -> 0f
      onTop -> 90f
      !onLeft -> 180f
      else -> 270f
    }
    val path = Path().apply {
      moveTo(c.x, c.y)
      arcTo(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r), start, 90f, false)
      close()
    }
    return androidx.compose.ui.graphics.Outline.Generic(path)
  }
}

/** Tools sit between these angles (0° = along the bottom edge), four at a time, fully inside. */
private const val RingStartDeg = 12f

/** Press-and-pull: within this many degrees of a screen edge the ring scrolls by itself. */
private const val EDGE_ZONE_DEG = 8f // only right at the edge, past the outermost tools (12° and 78°)
private const val AUTO_SCROLL_DEG_PER_FRAME = 2.5f
private const val RingEndDeg = 78f
private const val RingStepDeg = (RingEndDeg - RingStartDeg) / 3f

/** Fan-out: how far (in opening progress) each tool trails the one before it. */
private const val FAN_LAG = 0.08f

/**
 * Picks a high-contrast well behind the hub's tool sample across both Light and Dark UI themes:
 * dark ink on a dark UI gets an inverse (light) well, and pale ink on a light UI gets an inverse
 * (dark) well so the sample never disappears into the hub.
 */
private fun hubWellColor(
  tool: InkTool?,
  darkPage: Boolean,
  scheme: androidx.compose.material3.ColorScheme,
  isDarkTheme: Boolean,
): Color {
  if (tool == null || tool.isEraser || tool.isLasso || tool.isText || tool.isFill) {
    return scheme.surfaceContainerLow
  }
  val lum = Color(InkColors.onScreen(tool.paperColor or 0xFF000000.toInt(), darkPage)).luminance()
  return when {
    isDarkTheme && lum < 0.3f -> scheme.inverseSurface
    !isDarkTheme && lum > 0.72f -> scheme.inverseSurface
    else -> scheme.surfaceContainerLow
  }
}


/** What a cell is called, for the label shown while pulling. */
private fun dockLabel(item: DockItem, tools: List<InkTool>): String =
  when (item) {
    is DockItem.Tool -> tools.getOrNull(item.index)?.let { InkTools.describe(it).substringBefore(",") } ?: ""
    DockItem.Undo -> "Undo"
    DockItem.Redo -> "Redo"
    DockItem.Paper -> "Paper"
    DockItem.Instruments -> "Tools"
    DockItem.Clear -> "Clear page"
    DockItem.Layers -> "Layers"
    DockItem.More -> "More tools"
  }

private const val HOLD_TO_MOVE_MS = 650L // was 450: a slow hub pull turned into toolbar-move (QA 26 Nov)

/** The toolbar squeezed into a ball while it is dragged, with a hint of where it will land. */
@Composable
private fun DragBall(dock: ToolDock, tool: InkTool?, darkPage: Boolean, mmPerDp: Float) {
  val target = dock.targetFor(dock.ball.value)
  // The landing hint: a soft glow along the edge, or in the corner.
  Canvas(Modifier.fillMaxSize()) {
    val hint = PenLabColors.icon.copy(alpha = 0.18f)
    val t = 10.dp.toPx()
    val r = CornerReach.toPx()
    when (target) {
      DockSide.LEFT -> drawRect(hint, Offset(0f, 0f), androidx.compose.ui.geometry.Size(t, size.height))
      DockSide.RIGHT -> drawRect(hint, Offset(size.width - t, 0f), androidx.compose.ui.geometry.Size(t, size.height))
      DockSide.TOP -> drawRect(hint, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, t))
      DockSide.BOTTOM -> drawRect(hint, Offset(0f, size.height - t), androidx.compose.ui.geometry.Size(size.width, t))
      DockSide.TOP_LEFT -> drawCircle(hint, r, Offset(0f, 0f))
      DockSide.TOP_RIGHT -> drawCircle(hint, r, Offset(size.width, 0f))
      DockSide.BOTTOM_LEFT -> drawCircle(hint, r, Offset(0f, size.height))
      DockSide.BOTTOM_RIGHT -> drawCircle(hint, r, Offset(size.width, size.height))
    }
  }
  val squeeze = remember { Animatable(1.6f) }
  androidx.compose.runtime.LaunchedEffect(Unit) { squeeze.animateTo(1f, Motion.spring(dampingRatio = 0.55f, stiffness = 700f)) }
  // Stretches along the way it is thrown, like a drop of ink.
  val v = dock.velocity
  val speed = if (Motion.enabled) hypot(v.x, v.y) else 0f
  val stretch = 1f + (speed / 90f).coerceAtMost(0.35f)
  val heading = Math.toDegrees(kotlin.math.atan2(v.y, v.x).toDouble()).toFloat()
  val scheme = MaterialTheme.colorScheme
  val isDarkTheme = LocalPenLabColors.current.isDark
  val wheelCore = scheme.surfaceContainerHighest
  val hubWell = hubWellColor(tool, darkPage, scheme, isDarkTheme)
  Box(
    Modifier
      .offset { (dock.ball.value - Offset(BallSize.toPx() / 2, BallSize.toPx() / 2)).round() }
      .graphicsLayer {
        rotationZ = heading
        scaleX = squeeze.value * stretch
        scaleY = squeeze.value / stretch
      }
      .size(BallSize)
      .softShadow(CircleShape, elevation = 12.dp)
      .clip(CircleShape)
      .hubFace(coreColor = wheelCore, wellColor = hubWell),
    contentAlignment = Alignment.Center,
  ) {
    tool?.let { ToolGlyph(it, darkPage, mmPerDp, Modifier.size(32.dp, 22.dp), minStroke = 3.dp) }
  }
}


/**
 * The open options panel, beside its cell: right of a left rail, below a top bar, towards the
 * middle of the screen from a corner wheel. Always inside the screen.
 */
@Composable
private fun PanelBeside(dock: ToolDock, side: DockSide, key: Int, panel: @Composable () -> Unit) {
  val appear = remember(key) { Animatable(0f) }
  androidx.compose.runtime.LaunchedEffect(key) { appear.animateTo(1f, Motion.spring(dampingRatio = 1f, stiffness = 440f)) }
  val origin =
    when (side) {
      DockSide.LEFT, DockSide.TOP_LEFT, DockSide.BOTTOM_LEFT -> TransformOrigin(0f, 0.5f)
      DockSide.RIGHT, DockSide.TOP_RIGHT, DockSide.BOTTOM_RIGHT -> TransformOrigin(1f, 0.5f)
      DockSide.TOP -> TransformOrigin(0.5f, 0f)
      DockSide.BOTTOM -> TransformOrigin(0.5f, 1f)
    }
  androidx.compose.runtime.DisposableEffect(key) { onDispose { dock.panelBounds = androidx.compose.ui.geometry.Rect.Zero } }
  val content: @Composable () -> Unit = {
    Box(
      Modifier.onGloballyPositioned { dock.panelBounds = it.boundsInRoot() }.graphicsLayer {
        val p = appear.value
        alpha = p.coerceIn(0f, 1f)
        scaleX = 0.9f + 0.1f * p
        scaleY = 0.9f + 0.1f * p
        transformOrigin = origin
      }
    ) { panel() }
  }
  Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
    val placeable = measurables.firstOrNull()?.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
      val p = placeable ?: return@layout
      val fromTopBar = side.isCorner && key in dock.topBarCells
      val fallbackAnchor = dock.cellCenters[MORE_CELL] ?: dock.barBounds.center.takeIf { !dock.barBounds.isEmpty }
      val anchor = (if (fromTopBar) dock.topBarCells[key] else if (side.isCorner) dock.gripCenter else dock.cellCenters[key] ?: fallbackAnchor) ?: return@layout
      val reach = if (side.isVertical) 34.dp.toPx() else 30.dp.toPx()
      val w = p.width
      val h = p.height
      val (x, y) =
        if (fromTopBar) anchor.x - w / 2f to anchor.y + 28.dp.toPx() // just below the top bar button
        else when (side) {
          DockSide.LEFT -> anchor.x + reach to anchor.y - h / 2f
          DockSide.RIGHT -> anchor.x - reach - w to anchor.y - h / 2f
          DockSide.TOP -> anchor.x - w / 2f to anchor.y + reach
          DockSide.BOTTOM -> anchor.x - w / 2f to anchor.y - reach - h
          // From the wheel: the wheel has folded away, so the panel opens beside the round button,
          // where the wheel was, level with the button's outer edge.
          DockSide.TOP_LEFT -> dock.gripCenter.x + reach to dock.gripCenter.y - reach
          DockSide.TOP_RIGHT -> dock.gripCenter.x - reach - w to dock.gripCenter.y - reach
          DockSide.BOTTOM_LEFT -> dock.gripCenter.x + reach to dock.gripCenter.y + reach - h
          DockSide.BOTTOM_RIGHT -> dock.gripCenter.x - reach - w to dock.gripCenter.y + reach - h
        }
      val margin = 8.dp.roundToPx()
      p.place(
        x.roundToInt().coerceIn(margin, maxOf(margin, constraints.maxWidth - w - margin)),
        y.roundToInt().coerceIn(margin, maxOf(margin, constraints.maxHeight - h - margin)),
      )
    }
  }
}

/** Semantic group for hairline dividers on the toolbar: Pens | Utilities | History. */
private fun dockGroup(item: DockItem, tools: List<InkTool>): Int =
  when (item) {
    is DockItem.Tool -> if (tools.getOrNull(item.index)?.kind == InkTool.Kind.PEN) 0 else 1
    DockItem.Undo, DockItem.Redo -> 2
    else -> 1
  }

/** The cells in order, with a subtle hairline between pens, utilities, and history. */
@Composable
private fun DockCells(
  items: List<DockItem>,
  tools: List<InkTool>,
  vertical: Boolean,
  cell: @Composable (DockItem, Boolean) -> Unit,
) {
  var previousGroup: Int? = null
  for (item in items) {
    val group = dockGroup(item, tools)
    if (previousGroup != null && previousGroup != group) DockDivider(vertical)
    cell(item, false)
    previousGroup = group
  }
}

@Composable
private fun DockDivider(vertical: Boolean) {
  val hairline = MaterialTheme.colorScheme.outlineVariant
  if (vertical) {
    Box(Modifier.height(9.dp).width(RailWidth), contentAlignment = Alignment.Center) {
      Box(Modifier.width(24.dp).height(1.dp).background(hairline))
    }
  } else {
    Box(Modifier.width(9.dp).height(BarHeight), contentAlignment = Alignment.Center) {
      Box(Modifier.height(22.dp).width(1.dp).background(hairline))
    }
  }
}

/** The double-bar drag handle (`||` horizontal, `=` vertical) on both ends of the toolbar. */
@Composable
private fun Grip(vertical: Boolean, modifier: Modifier) {
  val gripTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
  Box(
    modifier
      .size(if (vertical) RailWidth else 22.dp, if (vertical) 24.dp else BarHeight)
      .semantics { contentDescription = "Move toolbar. Drag to an edge or a corner." },
    contentAlignment = Alignment.Center,
  ) {
    if (vertical) {
      Column(verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(16.dp, 2.dp).background(gripTint, CircleShape))
        Box(Modifier.size(16.dp, 2.dp).background(gripTint, CircleShape))
      }
    } else {
      Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(2.dp, 16.dp).background(gripTint, CircleShape))
        Box(Modifier.size(2.dp, 16.dp).background(gripTint, CircleShape))
      }
    }
  }
}

/** One cell: a tool (pen dot or utility icon) or an action (Tools, More, Undo, Redo, Layers, Clear). */
@Composable
private fun DockCell(
  item: DockItem,
  tools: List<InkTool>,
  selectedIndex: Int,
  darkPage: Boolean,
  mmPerDp: Float,
  canUndo: Boolean,
  canRedo: Boolean,
  canClear: Boolean,
  instrumentsActive: Boolean,
  openPanelKey: Int?,
  round: Boolean,
  compactHorizontal: Boolean,
  dock: ToolDock,
  onItem: (DockItem) -> Unit,
) {
  val track = Modifier.onGloballyPositioned { dock.cellCenters[item.key] = it.boundsInRoot().center }
  when (item) {
    is DockItem.Tool -> {
      val tool = tools.getOrNull(item.index) ?: return
      ToolCellView(
        tool = tool,
        selected = item.index == selectedIndex,
        darkPage = darkPage,
        round = round,
        compactHorizontal = compactHorizontal,
        onClick = { onItem(item) },
        modifier = track,
      )
    }
    DockItem.Undo -> ActionCell(PenLabIcons.Undo, "Undo", enabled = canUndo, active = false, showActiveDot = false, round = round, compactHorizontal = compactHorizontal, modifier = track) { onItem(item) }
    DockItem.Redo -> ActionCell(PenLabIcons.Redo, "Redo", enabled = canRedo, active = false, showActiveDot = false, round = round, compactHorizontal = compactHorizontal, modifier = track) { onItem(item) }
    DockItem.Paper -> ActionCell(PenLabIcons.Paper, "Paper style", enabled = true, active = openPanelKey == PAPER_CELL, showActiveDot = false, round = round, compactHorizontal = compactHorizontal, modifier = track) { onItem(item) }
    DockItem.Instruments -> ActionCell(PenLabIcons.Ruler, "Tools: ruler, protractor, shape snap", enabled = true, active = openPanelKey == INSTRUMENTS_CELL || instrumentsActive, showActiveDot = instrumentsActive, round = round, compactHorizontal = compactHorizontal, modifier = track) { onItem(item) }
    DockItem.More -> ActionCell(PenLabIcons.ChevronDown, "More tools", enabled = true, active = openPanelKey == MORE_CELL, showActiveDot = false, round = round, compactHorizontal = compactHorizontal, modifier = track) { onItem(item) }
    DockItem.Clear -> ActionCell(PenLabIcons.Clear, "Clear page", enabled = canClear, active = false, showActiveDot = false, round = round, compactHorizontal = compactHorizontal, modifier = track) { onItem(item) }
    DockItem.Layers -> ActionCell(PenLabIcons.Layers, "Layers", enabled = true, active = openPanelKey == LAYERS_CELL, showActiveDot = false, round = round, compactHorizontal = compactHorizontal, modifier = track) { onItem(item) }
  }
}

@Composable
private fun ActionCell(
  icon: ImageVector,
  label: String,
  enabled: Boolean,
  active: Boolean,
  showActiveDot: Boolean,
  round: Boolean,
  compactHorizontal: Boolean,
  modifier: Modifier,
  onClick: () -> Unit,
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val pressScale by animateFloatAsState(
    targetValue = if (pressed && enabled) 0.94f else 1f,
    animationSpec = Motion.spring(dampingRatio = 0.85f, stiffness = 700f),
    label = "actionPress",
  )
  val activeAlpha by animateFloatAsState(
    targetValue = if (active && enabled) 1f else 0f,
    animationSpec = Motion.tween(120),
    label = "actionActive",
  )
  val cellW = when {
    round -> WheelCell
    compactHorizontal -> HorizontalCellWidth
    else -> RailCellWidth
  }
  val cellH = when {
    round -> WheelCell
    compactHorizontal -> BarHeight
    else -> RailCellHeight
  }
  val tint = when {
    !enabled -> PenLabComponentTokens.toolIconDisabled
    active -> PenLabComponentTokens.toolIconActive
    else -> PenLabComponentTokens.toolIconDefault
  }

  Box(
    modifier
      .size(cellW, cellH)
      .clip(CircleShape)
      .selectable(
        selected = active,
        enabled = enabled,
        interactionSource = interaction,
        indication = ripple(bounded = true, radius = if (round) 25.dp else 22.dp),
        role = Role.Button,
        onClick = onClick,
      )
      .semantics { contentDescription = label }
      .graphicsLayer {
        scaleX = pressScale
        scaleY = pressScale
      },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier
        .size(if (round) 42.dp else PenLabLayout.controlVisual)
        .graphicsLayer { alpha = activeAlpha }
        .clip(CircleShape)
        .background(PenLabComponentTokens.toolCellSelectedBg),
    )
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(if (round) 19.dp else 20.dp))
    if (showActiveDot) {
      Box(
        Modifier
          .align(Alignment.TopEnd)
          .padding(top = PenLabSpacing.sm, end = 6.dp)
          .size(6.dp)
          .background(MaterialTheme.colorScheme.primary, CircleShape),
      )
    }
  }
}

/**
 * A tool slot:
 * - Pens show a crisp circular ink mark sized proportionally to their stroke weight, with a
 *   `2.dp` `primary` active ring and tonal `primaryContainer` fill when selected.
 * - Utilities (Eraser, Select, Fill, Highlighter, Text) show Phosphor Duotone icons (with a
 *   compact colour bar under Fill and Highlighter) and a crisp active pill highlight.
 */
@Composable
private fun ToolCellView(
  tool: InkTool,
  selected: Boolean,
  darkPage: Boolean,
  round: Boolean,
  compactHorizontal: Boolean,
  onClick: () -> Unit,
  modifier: Modifier,
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val press by animateFloatAsState(
    targetValue = if (pressed) 0.92f else 1f,
    animationSpec = Motion.spring(dampingRatio = 0.6f, stiffness = 1500f), // Graphite's snappy press
    label = "press",
  )
  val pick by animateFloatAsState(if (selected) 1f else 0f, Motion.tween(160), label = "pick")
  // A picked tool's sample gives a small hop (Graphite's springy select).
  val hop by animateFloatAsState(if (selected) 1f else 0f, Motion.spring(dampingRatio = 0.45f, stiffness = 700f), label = "hop")
  val cellW = when {
    round -> WheelCell
    compactHorizontal -> HorizontalCellWidth
    else -> RailCellWidth
  }
  val cellH = when {
    round -> WheelCell
    compactHorizontal -> BarHeight
    else -> RailCellHeight
  }
  val ringColor = PenLabComponentTokens.toolCellSelectedRing
  val activeBg = PenLabComponentTokens.toolCellSelectedBg
  val swatchEdge = LocalPenLabColors.current.swatchEdge
  val iconTint = if (selected) PenLabComponentTokens.toolIconActive else PenLabComponentTokens.toolIconDefault

  Box(
    modifier
      .size(cellW, cellH)
      .clip(CircleShape)
      .selectable(
        selected = selected,
        interactionSource = interaction,
        indication = ripple(bounded = true, radius = if (round) 25.dp else 22.dp),
        role = Role.RadioButton,
        onClick = onClick,
      )
      .semantics {
        contentDescription = InkTools.describe(tool)
        if (selected) stateDescription = if (tool.isLasso) "Selected" else "Selected. Tap again for options."
      }
      .graphicsLayer {
        scaleX = press
        scaleY = press
      },
    contentAlignment = Alignment.Center,
  ) {
    // Active tonal halo + crisp 2dp primary ring (read in graphicsLayer: zero recomposition per frame).
    Box(
      Modifier
        .size(if (round) 42.dp else PenLabLayout.controlVisual)
        .graphicsLayer { alpha = pick }
        .clip(CircleShape)
        .background(activeBg)
        .border(2.dp, ringColor, CircleShape),
    )

    Box(
      Modifier.graphicsLayer {
        scaleX = 1f + 0.12f * hop
        scaleY = 1f + 0.12f * hop
      },
      contentAlignment = Alignment.Center,
    ) {
    if (tool.kind == InkTool.Kind.PEN) {
      val inkColor = Color(InkColors.onScreen(tool.paperColor, darkPage) or 0xFF000000.toInt())
      val normalizedSize = ((tool.sizeDp - 0.5f) / 7.5f).coerceIn(0f, 1f)
      val dotSize = (15f + normalizedSize * 8f).dp
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
          Modifier
            .size(dotSize)
            .clip(CircleShape)
            .background(inkColor)
            .border(1.dp, swatchEdge, CircleShape),
        )
        if (tool.line != dev.penlab.app.ink.LineType.SOLID) {
          Box(
            Modifier
              .padding(top = 2.dp)
              .size(width = 12.dp, height = 2.dp)
              .background(inkColor.copy(alpha = 0.85f), CircleShape),
          )
        }
      }
    } else if (tool.isFill || tool.isHighlighter) {
      val color = Color(InkColors.onScreen(tool.paperColor or 0xFF000000.toInt(), darkPage))
      val icon = if (tool.isFill) PenLabIcons.Fill else PenLabIcons.Highlighter
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(PenLabLayout.iconCompact))
        Box(
          Modifier
            .padding(top = 2.dp)
            .size(width = 16.dp, height = 3.dp)
            .background(color, CircleShape)
            .border(0.5.dp, swatchEdge, CircleShape),
        )
      }
    } else {
      val icon = when {
        tool.isLasso -> PenLabIcons.Lasso
        tool.isText -> PenLabIcons.Text
        else -> PenLabIcons.Eraser
      }
      Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(PenLabLayout.iconMedium))
    }
    }
  }
}

/**
 * The wheel button's face (and the dragged ball's): a raised disc with an accent ring and a dark
 * well behind the tool sample, so a light ink or a grey icon stands out (user request: the
 * icon in the circle "looks bland"; it was a light sample on a flat grey disc).
 */
private fun Modifier.hubFace(coreColor: Color, wellColor: Color): Modifier = this.drawBehind {
  val c = Offset(size.width / 2f, size.height / 2f)
  val r = size.minDimension / 2f
  drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(0f to coreColor, 1f to PenLabColors.toolbar, center = c.copy(y = c.y - r * 0.4f), radius = r * 1.4f), r, c)
  drawCircle(wellColor, r - 7.dp.toPx(), c)
  drawCircle(PenLabColors.accent, r - 1.5.dp.toPx(), c, style = Stroke(2.5.dp.toPx()))
}

/**
 * What a tool looks like in a cell: pens and highlighters as a short S-shaped stroke in their
 * colour, thicker for bigger sizes (the fountain pen's stroke swells); the others as icons.
 */
@Composable
internal fun ToolGlyph(tool: InkTool, darkPage: Boolean, mmPerDp: Float, modifier: Modifier, minStroke: androidx.compose.ui.unit.Dp = 1.5.dp) {
  if (tool.isFill) {
    // The bucket with a bar of the fill colour under it.
    val fill = Color(InkColors.onScreen(tool.paperColor or 0xFF000000.toInt(), darkPage))
    Box(modifier, contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(FillIcon, contentDescription = null, tint = PenLabColors.icon, modifier = Modifier.size(PenLabLayout.iconCompact))
        Box(Modifier.padding(top = 2.dp).size(18.dp, 4.dp).background(fill, RoundedCornerShape(2.dp)).border(0.5.dp, PenLabColors.swatchEdge, RoundedCornerShape(2.dp)))
      }
    }
    return
  }
  if (tool.isEraser || tool.isLasso || tool.isText) {
    val icon = when {
      tool.isLasso -> LassoIcon
      tool.isText -> TextIcon
      else -> EraserIcon
    }
    Box(modifier, contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, tint = PenLabColors.icon, modifier = Modifier.size(PenLabLayout.iconDefault))
    }
    return
  }
  // Highlighters are see-through on the page; their sample is drawn nearly solid so it reads.
  val color = Color(InkColors.onScreen(tool.paperColor or 0xFF000000.toInt(), darkPage)).copy(alpha = if (tool.isHighlighter) 0.85f else 1f)
  Canvas(modifier) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
      moveTo(w * 0.08f, h * 0.75f)
      cubicTo(w * 0.35f, h * 0.05f, w * 0.55f, h * 1.0f, w * 0.92f, h * 0.25f)
    }
    val mm = tool.sizeDp * mmPerDp
    val width =
      if (tool.isHighlighter) (h * 0.55f)
      else (mm * 6f).dp.toPx().coerceIn(minStroke.toPx(), h * 0.4f)
    val strokeWidth = if (tool.style == PenStyle.FOUNTAIN && !tool.isHighlighter) width * 1.3f else width
    val dash = tool.line.pattern?.takeIf { tool.kind == InkTool.Kind.PEN }?.let { pattern ->
      val d = maxOf(strokeWidth, 1.5.dp.toPx()) * 0.8f
      androidx.compose.ui.graphics.PathEffect.dashPathEffect(FloatArray(pattern.size) { i -> maxOf(pattern[i] * d * 0.5f, if (i % 2 == 0) 0.01f else d) })
    }
    drawPath(path, color, style = Stroke(strokeWidth, cap = if (tool.isHighlighter) StrokeCap.Butt else StrokeCap.Round, pathEffect = dash))
  }
}

internal val RailWidth = 58.dp
internal val BarHeight = 48.dp
private val HorizontalCellWidth = PenLabSpacing.touchTarget
private val RailCellWidth = 54.dp
private val RailCellHeight = PenLabSpacing.touchTarget
private val HubSize = WheelGeometry.HUB_DP.dp
private val BallSize = 56.dp
private val CornerReach = 150.dp
private val EdgeGap = WheelGeometry.EDGE_GAP_DP.dp
private val INLINE_TOP_ROW_MIN_WIDTH = 1060.dp
