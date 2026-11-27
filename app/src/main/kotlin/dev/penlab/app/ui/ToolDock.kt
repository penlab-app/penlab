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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.PathEffect
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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
import kotlin.math.abs
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
enum class DockSide(val isCorner: Boolean = false, val isVertical: Boolean = false, val isFloating: Boolean = false) {
  LEFT(isVertical = true),
  RIGHT(isVertical = true),
  TOP,
  BOTTOM,
  FLOATING(isFloating = true),
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

/**
 * True when the picked tool has no cell of its own on the bar (e.g. the highlighter, chosen from
 * More while not pinned), so the More button must show it as the active one.
 */
internal fun toolHiddenFromBar(items: List<DockItem>, selectedIndex: Int): Boolean =
  selectedIndex >= 0 && items.none { it is DockItem.Tool && it.index == selectedIndex }

/** Stands for the layers button where a cell number is needed. */
const val LAYERS_CELL = -3

/** Stands for the "More" toolbar button (Add from PDF, Add image, Highlighter, Text, Layers, Clear). */
const val MORE_CELL = -4

/** Essential tools kept visible when the toolbar is collapsed: pens + eraser. */
internal fun collapsedToolbarItems(items: List<DockItem>, tools: List<InkTool>): List<DockItem> =
  items.filter { item ->
    item is DockItem.Tool && tools.getOrNull(item.index)?.let { it.kind == InkTool.Kind.PEN || it.isEraser } == true
  }

/**
 * The dock's state: where it is (kept between launches), whether it is being dragged, and
 * where each cell is on screen (so an options panel can open next to the cell it belongs to).
 */
@Stable
class ToolDock(context: Context) {
  private val prefs = context.getSharedPreferences("toolbar", Context.MODE_PRIVATE)

  var side by mutableStateOf(DockSide.entries.firstOrNull { it.name == prefs.getString("side", null) } ?: DockSide.TOP)
    private set

  /** Top and bottom bars: where the bar's middle is, 0 = left edge, 1 = right edge. */
  var along by mutableFloatStateOf(prefs.getFloat("along", 0.5f))
    private set

  /** Free-floating bar: normalised screen position (0..1) of its centre. */
  var floatX by mutableFloatStateOf(prefs.getFloat("float_x", 0.5f))
    private set
  var floatY by mutableFloatStateOf(prefs.getFloat("float_y", 0.16f))
    private set

  // Backing field avoids a JVM setter name clash with setCollapsed(Boolean).
  private var _collapsed by mutableStateOf(prefs.getBoolean("collapsed", false))
  val collapsed: Boolean
    get() = _collapsed

  fun setCollapsed(on: Boolean) {
    _collapsed = on
    prefs.edit().putBoolean("collapsed", on).apply()
  }

  fun toggleCollapsed() {
    setCollapsed(!_collapsed)
  }

  /** Programmatically docks the toolbar to [newSide] (e.g. from Settings). */
  fun dockTo(newSide: DockSide) {
    side = newSide
    wheelOpen = false
    if (!newSide.isVertical && !newSide.isCorner && !newSide.isFloating) {
      along = 0.5f
    }
    if (newSide.isFloating) {
      floatX = floatX.coerceIn(0.08f, 0.92f)
      floatY = floatY.coerceIn(0.08f, 0.92f)
    }
    prefs
      .edit()
      .putString("side", side.name)
      .putFloat("along", along)
      .putFloat("float_x", floatX)
      .putFloat("float_y", floatY)
      .apply()
  }

  /** Corner wheel: open (tools fanned out) or just the round button. */
  var wheelOpen by mutableStateOf(false)

  /** Wheel: the cell a pull is pointing at (by [DockItem.key]), the button's middle, where each
   *  cell sits when open, and how far the finger has pulled from the middle. */
  internal var wheelHighlight by mutableStateOf<Int?>(null)
  internal var wheelHub = Offset.Zero
  internal var wheelTargets: Map<Int, Offset> = emptyMap()
  internal var wheelLocalTargets: Map<Int, Offset> = emptyMap()

  /** The wheel disc's centre (= the hub's centre), root pixels. */
  internal var wheelOrigin = Offset.Zero

  /** How the tools sit on the arc for the current corner and screen (see [WheelGeometry.ring]). */
  internal var wheelColorRing = WheelGeometry.Ring(0f, 0f, 0f, 1f, 0f)

  /** The small inner arc of colour dots between the hub and the tool ring ([wheelRing]); see [WheelGeometry.colorRing]. */
  internal var wheelRing = WheelGeometry.Ring(0f, 0f, 0f, 1f, 0f)
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

  /** The wheel disc's radius (px), and how far the gesture zones and the top buttons keep the interactive parts from the screen's edges. */
  internal var wheelRadiusPx = 0f
  internal var gestureLeftPx = 0f
  internal var gestureRightPx = 0f
  internal var gestureBottomPx = 0f
  internal var topReservePx = 0f
  internal var hubHalfPx = 0f
  internal var edgeGapPx = 0f

  /** Works out the wheel's sizes and how far the edges are kept clear (needs [screenWidth] and [screenHeight]). */
  internal fun applyWheelMetrics(density: androidx.compose.ui.unit.Density, insets: DockInsets, corner: DockSide) {
    with(density) {
      hubHalfPx = (HubSize / 2).toPx()
      edgeGapPx = EdgeGap.toPx()
      val imm = insets.immersive
      gestureLeftPx = WheelGeometry.edgeInset(insets.gestureLeft.toPx(), insets.left.toPx(), screenWidth.toFloat(), imm)
      gestureRightPx = WheelGeometry.edgeInset(insets.gestureRight.toPx(), insets.right.toPx(), screenWidth.toFloat(), imm)
      gestureBottomPx = WheelGeometry.edgeInset(insets.gestureBottom.toPx(), insets.bottom.toPx(), screenHeight.toFloat(), imm)
      val margin = if (imm) WheelGeometry.CORNER_MARGIN_DP.dp.toPx() else 0f
      topReservePx = (insets.top + (if (insets.topChromeVisible) insets.topButtons else 0.dp)).toPx()
      // The ring: cells fully on screen and clear of the strips, spaced evenly along the visible arc.
      val onLeft = corner == DockSide.TOP_LEFT || corner == DockSide.BOTTOM_LEFT
      val onTop = corner == DockSide.TOP_LEFT || corner == DockSide.TOP_RIGHT
      wheelRing = WheelGeometry.ring(
        minRadius = WheelGeometry.RING_RADIUS_DP.dp.toPx(),
        cell = WheelGeometry.CELL_DP.dp.toPx(),
        discMargin = WheelGeometry.DISC_MARGIN_DP.dp.toPx(),
        insetU = maxOf(if (onLeft) gestureLeftPx else gestureRightPx, margin),
        insetV = maxOf(if (onTop) topReservePx else gestureBottomPx, margin),
        hubHalf = hubHalfPx,
        cellGap = WheelGeometry.CELL_GAP_DP.dp.toPx(),
        hubClearance = WheelGeometry.HUB_CLEARANCE_DP.dp.toPx(),
      )
      wheelColorRing = WheelGeometry.colorRing(
        tool = wheelRing,
        toolCell = WheelGeometry.CELL_DP.dp.toPx(),
        colorCell = WheelGeometry.COLOR_CELL_DP.dp.toPx(),
        hubHalf = hubHalfPx,
        hubClearance = WheelGeometry.HUB_CLEARANCE_DP.dp.toPx(),
        cellGap = WheelGeometry.CELL_GAP_DP.dp.toPx(),
      )
      wheelRadiusPx = wheelRing.disc // the original disc: the colour row sits inside it
    }
  }

  /** Where the wheel's centre (the button) sits for [corner] (root pixels), from the last [applyWheelMetrics]. */
  internal fun cornerLayout(corner: DockSide): WheelGeometry.Corner =
    WheelGeometry.corner(
      onLeft = corner == DockSide.TOP_LEFT || corner == DockSide.BOTTOM_LEFT,
      onTop = corner == DockSide.TOP_LEFT || corner == DockSide.TOP_RIGHT,
      width = screenWidth.toFloat(),
      height = screenHeight.toFloat(),
      hubU = wheelRing.hubU,
      hubV = wheelRing.hubV,
    )

  /** Rectangles kept out of the Back gesture while the wheel is open (root pixels); empty otherwise. */
  internal var wheelExclusion: List<WheelGeometry.PxRect> = emptyList()

  /** The same for the folded wheel: just the button's neighbourhood at the side edge. */
  internal var hubExclusion: List<WheelGeometry.PxRect> = emptyList()

  /** What to keep out of the Back gesture right now: the open wheel, or the folded button. */
  internal fun gestureRects(): List<WheelGeometry.PxRect> = if (wheelOpen) wheelExclusion else hubExclusion

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
      if (WheelGeometry.inDisc(p, Offset(c.hubX, c.hubY), wheelRadiusPx + 8f)) return true
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
  internal var edgeSnapPx = 0f

  /** Release speed (px/s) from which a drop counts as a throw. */
  internal var flickMinSpeedPx = 0f

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
    // Where it lands is judged where the ball is shown (what the landing hint is glowing for), not
    // where the finger is (the ball trails it by the grab offset and the touch slop). Only a real
    // flick carries it further.
    val projected = DockDrop.landingPoint(ball.value, release, w, h, flickMinSpeedPx)
    val target = targetFor(projected)
    val newAlong =
      if (!target.isCorner && !target.isVertical && !target.isFloating) {
        (projected.x / w.coerceAtLeast(1f)).coerceIn(0.05f, 0.95f)
      } else {
        along
      }
    val newFloatX = if (target.isFloating) (projected.x / w.coerceAtLeast(1f)).coerceIn(0.06f, 0.94f) else floatX
    val newFloatY = if (target.isFloating) (projected.y / h.coerceAtLeast(1f)).coerceIn(0.06f, 0.94f) else floatY
    val home =
      when (target) {
        DockSide.LEFT -> Offset(edgeGapPx + railHalfPx, ball.value.y.coerceIn(0f, h))
        DockSide.RIGHT -> Offset(w - edgeGapPx - railHalfPx, ball.value.y.coerceIn(0f, h))
        DockSide.TOP -> Offset(newAlong * w, topBarY)
        DockSide.BOTTOM -> Offset(newAlong * w, bottomBarY)
        DockSide.FLOATING -> Offset(newFloatX * w, newFloatY * h)
        DockSide.TOP_LEFT, DockSide.TOP_RIGHT, DockSide.BOTTOM_LEFT, DockSide.BOTTOM_RIGHT ->
          cornerLayout(target).let { Offset(it.hubX, it.hubY) } // where the button will sit, clear of the gesture zones
      }
    // A little bounce, because a throw carried it there.
    ball.animateTo(home, Motion.spring(dampingRatio = 0.8f, stiffness = 380f), initialVelocity = release)
    side = target
    along = newAlong
    floatX = newFloatX
    floatY = newFloatY
    dragging = false
    prefs
      .edit()
      .putString("side", side.name)
      .putFloat("along", along)
      .putFloat("float_x", floatX)
      .putFloat("float_y", floatY)
      .apply()
  }

  /** Which shape a drop at [p] makes: a corner if close to two edges, an edge if near one, else floating. */
  internal fun targetFor(p: Offset): DockSide =
    DockDrop.resolveTarget(
      x = p.x,
      y = p.y,
      width = screenWidth.toFloat(),
      height = screenHeight.toFloat(),
      cornerReachPx = cornerReachPx,
      edgeSnapPx = edgeSnapPx,
    )
}

/** Pure rules for where a dropped toolbar lands (unit-tested). */
internal object DockDrop {
  /** A release slower than this (dp per second) is a placement, not a throw. */
  const val FLICK_MIN_SPEED_DP = 700f

  /** Distance from screen edges within which a dropped bar snaps to an edge instead of floating. */
  const val EDGE_SNAP_DP = 76f

  /** Resolves where a drop at ([x], [y]) lands: corner wheel, edge pill/bar, or free-floating pill. */
  fun resolveTarget(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    cornerReachPx: Float,
    edgeSnapPx: Float,
  ): DockSide {
    val left = x < cornerReachPx
    val right = x > width - cornerReachPx
    val top = y < cornerReachPx
    val bottom = y > height - cornerReachPx
    when {
      left && top -> return DockSide.TOP_LEFT
      right && top -> return DockSide.TOP_RIGHT
      left && bottom -> return DockSide.BOTTOM_LEFT
      right && bottom -> return DockSide.BOTTOM_RIGHT
    }
    val distances =
      listOf(
        DockSide.LEFT to x,
        DockSide.RIGHT to width - x,
        DockSide.TOP to y,
        DockSide.BOTTOM to height - y,
      )
    val nearest = distances.minBy { it.second }
    return if (nearest.second <= edgeSnapPx) nearest.first else DockSide.FLOATING
  }

  /** How far a throw glides: the same deceleration as a scrolled list (0.998 per ms). */
  fun project(velocityPerSecond: Float): Float = velocityPerSecond / 1000f * 0.998f / (1f - 0.998f) * 0.6f

  /**
   * The point a drop is judged at: the ball's displayed position, or, for a flick of at least
   * [flickMinSpeedPx], the point the throw would glide to. Always on the screen.
   */
  fun landingPoint(ball: Offset, release: Offset, width: Float, height: Float, flickMinSpeedPx: Float): Offset {
    val flick = hypot(release.x, release.y) >= flickMinSpeedPx
    val x = if (flick) ball.x + project(release.x) else ball.x
    val y = if (flick) ball.y + project(release.y) else ball.y
    return Offset(x.coerceIn(0f, width), y.coerceIn(0f, height))
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
  /** Height the bottom bar covers (px; 0 when it is not docked at the bottom), so the page's end clears it. */
  onBottomCovered: (Float) -> Unit = {},
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
      DockSide.FLOATING -> TransformOrigin(0.5f, 0.5f)
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
  val shownItems = if (!side.isCorner && dock.collapsed) collapsedToolbarItems(items, tools).ifEmpty { items } else items
  // A tool picked from More (highlighter, text box...) has no cell on the bar: More shows it as active.
  val moreHoldsTool = toolHiddenFromBar(if (side.isCorner) items else shownItems, selectedIndex)
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
      moreHoldsTool = moreHoldsTool,
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
    val rail = with(density) { (RailWidth + EdgeGap + 8.dp).toPx() }
    when {
      dock.dragging -> Unit
      side == DockSide.LEFT -> onSidesCovered(rail, 0f)
      side == DockSide.RIGHT -> onSidesCovered(0f, rail)
      else -> onSidesCovered(0f, 0f)
    }
    if (!dock.dragging) {
      onBottomCovered(if (side == DockSide.BOTTOM) with(density) { (BarHeight + EdgeGap + insets.bottom + 8.dp).toPx() } else 0f)
    }
  }
  // Keep the wheel's edge clearances current when insets change without the screen resizing.
  androidx.compose.runtime.SideEffect { if (dock.screenWidth > 0) dock.applyWheelMetrics(density, insets, side) }
  androidx.compose.runtime.DisposableEffect(side) {
    if (side.isCorner) dock.barBounds = androidx.compose.ui.geometry.Rect.Zero
    onDispose {}
  }
  androidx.compose.runtime.DisposableEffect(side, shownItems, dock.collapsed) {
    val activeKeys = shownItems.mapTo(HashSet()) { it.key }
    dock.cellCenters.keys.retainAll(activeKeys)
    if (side.isCorner) {
      dock.gripCenters.clear()
    } else {
      dock.topBarCells.clear()
    }
    onDispose {}
  }
  androidx.compose.runtime.DisposableEffect(Unit) {
    onDispose {
      dock.cellCenters.clear()
      dock.gripCenters.clear()
      dock.topBarCells.clear()
      dock.barBounds = androidx.compose.ui.geometry.Rect.Zero
      dock.panelBounds = androidx.compose.ui.geometry.Rect.Zero
    }
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
        dock.edgeSnapPx = DockDrop.EDGE_SNAP_DP.dp.toPx()
        dock.flickMinSpeedPx = DockDrop.FLICK_MIN_SPEED_DP.dp.toPx()
        dock.cellReachPx = 26.dp.toPx()
        dock.applyWheelMetrics(density, insets, side)
        dock.railHalfPx = (RailWidth / 2).toPx()
        dock.topBarY = (insets.top + EdgeGap + BarHeight / 2).toPx()
        dock.bottomBarY = it.size.height - (insets.bottom + EdgeGap + BarHeight / 2).toPx()
      }
    }
  ) {
    when {
      side.isVertical -> Rail(dock, side, shownItems, tools, insets, startGrip, endGrip, onDragStart, isPalm, cell, shapeLayer.align(if (side == DockSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd))
      side.isCorner -> Wheel(dock, side, items, tools, selectedIndex, darkPage, mmPerDp, insets, scope, onDragStart, onItem, isPalm, cell, shapeLayer)
      else -> Bar(dock, side, shownItems, tools, insets, startGrip, endGrip, onDragStart, isPalm, cell, shapeLayer)
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
  /**
   * The system bars are hidden (wheel immersive, full screen or present): a swipe from the bottom
   * only reveals them, so the wheel sits in the real corner. Only the side (Back) strips remain,
   * covered by the exclusion rects.
   */
  val immersive: Boolean = false,
)

/** Left or right: a floating vertical capsule pill centred along the edge, with handles at both ends. */
@Composable
private fun Rail(
  dock: ToolDock,
  side: DockSide,
  items: List<DockItem>,
  tools: List<InkTool>,
  insets: DockInsets,
  startGrip: Modifier,
  endGrip: Modifier,
  onDragStart: () -> Unit,
  isPalm: () -> Boolean,
  cell: @Composable (DockItem, Boolean) -> Unit,
  modifier: Modifier,
) {
  val topPad = insets.top + (if (insets.topChromeVisible) insets.topButtons else EdgeGap)
  val bottomPad = insets.bottom + EdgeGap
  val startPad = if (side == DockSide.LEFT) insets.left + EdgeGap else 0.dp
  val endPad = if (side == DockSide.RIGHT) insets.right + EdgeGap else 0.dp
  val shape = RoundedCornerShape(28.dp)
  val isDark = LocalPenLabColors.current.isDark
  // Dragging a grip goes inward from the screen edge, which the system would take as Back (and leave
  // the notebook). So keep a window round each grip out of the Back gesture, like the open wheel.
  // measured = [edge-to-pill reach, screen height, start grip centre y, end grip centre y] in root pixels.
  val view = androidx.compose.ui.platform.LocalView.current
  val measured = remember { floatArrayOf(0f, 0f, Float.NaN, Float.NaN) }
  val refreshExclusion = {
    val screenW = if (dock.screenWidth > 0) dock.screenWidth.toFloat() else view.width.toFloat()
    val screenH = if (dock.screenHeight > 0) dock.screenHeight.toFloat() else view.height.toFloat()
    setGestureExclusion(
      view,
      WheelGeometry.railExclusionRects(
        side == DockSide.LEFT, screenW, screenH, measured[0],
        listOf(measured[2], measured[3]), MaxExclusionHeight.value * view.resources.displayMetrics.density,
      ),
    )
  }
  androidx.compose.runtime.DisposableEffect(side) { onDispose { setGestureExclusion(view, emptyList()) } }
  fun Modifier.trackGrip(slot: Int) = onGloballyPositioned {
    measured[2 + slot] = it.positionInRoot().y + it.size.height / 2f // unclipped: a scrolled-away grip reads off screen
    refreshExclusion()
  }
  Box(
    modifier
      .padding(start = startPad, end = endPad, top = topPad, bottom = bottomPad)
      .width(RailWidth)
      .onGloballyPositioned {
        val bounds = it.boundsInRoot()
        dock.barBounds = bounds
        val screenW = if (dock.screenWidth > 0) dock.screenWidth.toFloat() else view.width.toFloat()
        measured[0] = if (side == DockSide.LEFT) bounds.right else (screenW - bounds.left).coerceAtLeast(0f)
        measured[1] = it.size.height.toFloat()
        refreshExclusion()
      }
      .floatingChromeSurface(
        shape = shape,
        background = PenLabComponentTokens.chromePillBg,
        elevation = 8.dp,
        subtleLightHairline = true,
        borderColor = PenLabComponentTokens.chromePillBorder,
        isDark = isDark,
      )
      .ignorePalm(isPalm)
      .semantics { contentDescription = "Drawing tools" },
  ) {
    Column(
      Modifier
        .width(RailWidth)
        .verticalScroll(rememberScrollState())
        .padding(vertical = 2.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Grip(vertical = true, modifier = startGrip.trackGrip(0))
      DockCells(items, tools, vertical = true, cell)
      CollapseCell(dock = dock, vertical = true, onDragStart = onDragStart)
      Grip(vertical = true, modifier = endGrip.trackGrip(1))
    }
  }
}

/**
 * Top, bottom, or free-floating: a floating capsule pill (`48.dp` tall) with `||` drag handles on both ends.
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
  onDragStart: () -> Unit,
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
        CollapseCell(dock = dock, vertical = false, onDragStart = onDragStart)
        Grip(vertical = false, modifier = endGrip)
      }
    }
  ) { measurables, constraints ->
    val gap = EdgeGap.roundToPx()
    val left = insets.left.roundToPx() + gap
    val right = constraints.maxWidth - insets.right.roundToPx() - gap
    val topSafe = insets.top.roundToPx() + gap
    val bottomSafe = constraints.maxHeight - insets.bottom.roundToPx() - gap
    val bar = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = (right - left).coerceAtLeast(0)))
    layout(constraints.maxWidth, constraints.maxHeight) {
      // A bar dropped near the middle snaps to it (also for older saved positions), so it sits
      // on the screen's centre whenever it fits; further out it stays where it was dropped.
      val along = snappedAlong(dock.along)
      val centre = along * constraints.maxWidth
      val screenWidthDp = constraints.maxWidth.toDp()
      val topRowY = (insets.top + EdgeGap).roundToPx()
      val row2Y = (insets.top + insets.topButtons).roundToPx()

      val (x, y) =
        when (side) {
          DockSide.TOP -> {
            if (!insets.topChromeVisible) {
              val bx = (centre - bar.width / 2f).roundToInt().coerceIn(left, maxOf(left, right - bar.width))
              bx to topRowY
            } else {
              val inlineLeft = left + insets.topRowLeftClearance.roundToPx() + gap
              val inlineRight = right - insets.topRowRightClearance.roundToPx() - gap
              val fitsInline = screenWidthDp >= INLINE_TOP_ROW_MIN_WIDTH && (inlineRight - inlineLeft) >= bar.width
              if (fitsInline) {
                val bx = inlineToolbarX(centre, bar.width, inlineLeft, inlineRight, centreInGap = along == 0.5f)
                bx to topRowY
              } else {
                val bx = (centre - bar.width / 2f).roundToInt().coerceIn(left, maxOf(left, right - bar.width))
                bx to row2Y
              }
            }
          }
          DockSide.FLOATING -> {
            val bx = (dock.floatX * constraints.maxWidth - bar.width / 2f).roundToInt().coerceIn(left, maxOf(left, right - bar.width))
            val by = (dock.floatY * constraints.maxHeight - bar.height / 2f).roundToInt().coerceIn(topSafe, maxOf(topSafe, bottomSafe - bar.height))
            bx to by
          }
          else -> {
            val bx = (centre - bar.width / 2f).roundToInt().coerceIn(left, maxOf(left, right - bar.width))
            val by = constraints.maxHeight - insets.bottom.roundToPx() - gap - bar.height
            bx to by
          }
        }
      if (side == DockSide.TOP) {
        dock.topBarY = y + bar.height / 2f
      }
      bar.place(x, y)
    }
  }
}

/** How far from the middle (as a share of the screen width) a dropped bar still snaps to the centre. */
private const val ALONG_SNAP = 0.15f

/** [along] (0..1 across the screen), pulled to exactly 0.5 when it is within [ALONG_SNAP] of it. */
internal fun snappedAlong(along: Float): Float = if (abs(along - 0.5f) <= ALONG_SNAP) 0.5f else along

/**
 * Left edge of the toolbar island in the top row, between the side pills. [gapLeft] and [gapRight]
 * are the free space with the 12 dp clearance already taken off each end. The island sits on the
 * screen's centre ([desiredCentre]) whenever that leaves the clearance on both sides; otherwise it
 * is centred in the gap ([centreInGap], the default spot) or, if the user dropped it elsewhere,
 * pushed just far enough to fit. The caller has checked the bar fits the gap.
 */
internal fun inlineToolbarX(desiredCentre: Float, barWidth: Int, gapLeft: Int, gapRight: Int, centreInGap: Boolean): Int {
  val wanted = (desiredCentre - barWidth / 2f).roundToInt()
  val maxX = maxOf(gapLeft, gapRight - barWidth)
  if (wanted in gapLeft..maxX) return wanted
  return if (centreInGap) ((gapLeft + gapRight - barWidth) / 2f).roundToInt().coerceIn(gapLeft, maxX) else wanted.coerceIn(gapLeft, maxX)
}

/**
 * Which cells go on which wheel ring. Inner: the colours, i.e. the pen tools (their cell is a
 * colour dot). Outer: every tool whose cell is an icon (eraser, lasso, highlighter, fill, text)
 * and the actions Instruments (ruler), Paper, Clear, More, Undo and Redo. Layers stays off the
 * wheel. Each ring keeps the order of [items]; a tool with no entry in [tools] is dropped.
 */
internal fun wheelRingItems(items: List<DockItem>, tools: List<InkTool>): Pair<List<DockItem>, List<DockItem>> {
  val inner = ArrayList<DockItem>()
  val outer = ArrayList<DockItem>()
  for (item in items) {
    when (item) {
      is DockItem.Tool -> when (tools.getOrNull(item.index)?.kind) {
        null -> {}
        InkTool.Kind.PEN -> inner.add(item)
        else -> outer.add(item)
      }
      DockItem.Instruments, DockItem.Paper, DockItem.Clear, DockItem.More, DockItem.Undo, DockItem.Redo -> outer.add(item)
      DockItem.Layers -> {}
    }
  }
  return inner to outer
}

/**
 * A corner: a round button showing the current tool, and beside it, when open, the wheel: a
 * quarter circle with rounded corners. Its corner holds the current tool;
 * a small inner arc holds the colours (the pens, drawn as colour dots) in the lighter zone round the
 * button, and the ring beyond it the tools drawn as icons (eraser, lasso, highlighter, fill, text) and the actions (ruler, paper, clear, more,
 * undo, redo; see [wheelRingItems]). Each ring scrolls along its arc with a swipe (it keeps gliding, and
 * springs back at the ends). Nothing ever shows
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
  val (innerItems, outerItems) = wheelRingItems(items, tools)
  val ringItems = innerItems + outerItems // content order: inner ring first, then the outer one
  // Opening and closing carry momentum: a spring that overshoots a little and settles.
  val open = remember { Animatable(0f) }
  androidx.compose.runtime.LaunchedEffect(dock.wheelOpen) {
    open.animateTo(if (dock.wheelOpen) 1f else 0f, Motion.spring(dampingRatio = 0.62f, stiffness = 420f))
  }
  // How far each ring is scrolled along its arc, in degrees (inner = colours, outer = tools and actions).
  val scrollIn = remember { Animatable(0f) }
  val scrollOut = remember { Animatable(0f) }
  fun scrollOf(outer: Boolean) = if (outer) scrollOut else scrollIn
  // How far a ring can scroll: until its last cell reaches the last fully visible spot.
  // (the ring's step comes from dock.wheelColorRing / wheelRing, which depend on the screen and its gesture zones;
  // they are read where used, never captured, so a rotation or inset change is picked up)
  val innerCount = androidx.compose.runtime.rememberUpdatedState(innerItems.size)
  val outerCount = androidx.compose.runtime.rememberUpdatedState(outerItems.size)
  fun spanNow(outer: Boolean): Float {
    val ring = if (outer) dock.wheelRing else dock.wheelColorRing
    return WheelGeometry.scrollSpan(if (outer) outerCount.value else innerCount.value, ring)
  }
  val highlighted = dock.wheelHighlight
  val lifted = remember { Animatable(1f) }
  val onLeft = side == DockSide.TOP_LEFT || side == DockSide.BOTTOM_LEFT
  val onTop = side == DockSide.TOP_LEFT || side == DockSide.TOP_RIGHT
  // The disc is centred on the hub, well in from both screen edges; the edges only clip its rim.
  val shape = CircleShape
  // The press-and-pull handler below lives for the whole gesture. Whatever it reads that can change
  // while it runs comes through these, so a recomposition never has to restart it.
  val currentItems by androidx.compose.runtime.rememberUpdatedState(items)
  val currentOnItem by androidx.compose.runtime.rememberUpdatedState(onItem)
  val currentOnDragStart by androidx.compose.runtime.rememberUpdatedState(onDragStart)
  // A dial, not a list: a light tick each time a tool passes a slot (15 Nov wheel redesign).
  androidx.compose.runtime.LaunchedEffect(Unit) {
    androidx.compose.runtime.snapshotFlow {
      kotlin.math.round(scrollIn.value / dock.wheelColorRing.stepDeg.coerceAtLeast(1f)).toInt() to
        kotlin.math.round(scrollOut.value / dock.wheelRing.stepDeg.coerceAtLeast(1f)).toInt()
    }
      .collect { if (dock.wheelOpen) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }
  }
  // While the wheel is open its area (folded: its button) is kept out of the system Back gesture, so
  // dragging on it near a side edge never navigates away. Cleared when it leaves (docked elsewhere).
  androidx.compose.runtime.DisposableEffect(dock.wheelOpen, side) {
    setGestureExclusion(view, dock.gestureRects())
    onDispose { setGestureExclusion(view, emptyList()) }
  }

  Layout(
    // After every layout (the wheel moved, the screen turned), refresh the rectangles if open.
    modifier = modifier.onGloballyPositioned { setGestureExclusion(view, dock.gestureRects()) },
    content = {
      // The wheel: a disc turning round the hub, which sits in from the corner so more than a half
      // circle of it is on screen (the edges clip the rim). Everything inside is clipped to the circle. Its size comes from the ring
      // layout (dock.wheelColorRing), worked out in the outer layout just before this is measured.
      Box(
        Modifier.layout { m, _ ->
          val d = (dock.wheelRadiusPx * 2f).roundToInt().coerceAtLeast(1)
          val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(d, d))
          layout(p.width, p.height) { p.place(0, 0) }
        }
          // Touches are read before the opening animation's scale (positions stay real numbers),
          // and only while open, so the folded wheel never blocks the pen.
          .then(if (!dock.wheelOpen) Modifier else Modifier.pointerInput(onLeft, onTop) {
            // Swipe along the arc to scroll the tool ring; it glides on and springs back at the ends.
            // Angle round the circle's centre (the hub): 0° along the bottom/top edge,
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
              // The ring under the finger is the one that turns: outside the gap between the two rings.
              val centre = size.width / 2f
              val outerRing = (down.position - Offset(centre, centre)).getDistance() > WheelGeometry.ringSplitRadius(dock.wheelColorRing, dock.wheelRing)
              val scroll = scrollOf(outerRing)
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
                val max = spanNow(outerRing)
                val next = scroll.value + d
                val resisted = if (next > 0f || next < -max) scroll.value + d * 0.35f else next
                scope.launch { scroll.snapTo(resisted) }
              }
              if (dragging) {
                val velocity = tracker.calculateVelocity().y
                scope.launch {
                  // Lands with a tool exactly in a slot (or at the end), like a dial's detent.
                  val span = spanNow(outerRing)
                  val free = (scroll.value + velocity * 0.25f).coerceIn(-span, 0f)
                  val step = (if (outerRing) dock.wheelRing else dock.wheelColorRing).stepDeg.coerceAtLeast(1f)
                  val target = (kotlin.math.round(free / step) * step).coerceIn(-span, 0f)
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
            // The darker groove is the tool ring's; the lighter zone inside its inner rim holds the colour dots.
            val ring = dock.wheelRing.radius
            val band = WheelCell.toPx() + 10.dp.toPx()
            // The light zone is only as wide as the colour dots need (Akram 26 Nov: thinner); the groove fills the rest.
            val outer = ring + band / 2f
            val inner = (dock.wheelColorRing.radius + WheelGeometry.COLOR_CELL_DP.dp.toPx() / 2f + 8.dp.toPx()).coerceAtMost(ring - band / 2f)
            drawCircle(wheelGroove, radius = (outer + inner) / 2f, center = c, style = Stroke(outer - inner))
            drawCircle(PenLabColors.hairline, radius = outer, center = c, style = Stroke(1.dp.toPx()))
            drawCircle(wheelInnerRim, radius = inner, center = c, style = Stroke(1.dp.toPx()))
          }
          .border(1.dp, PenLabColors.hairline, shape)
          .ignorePalm(isPalm)
      ) {
        Layout(content = {
          for (item in ringItems) {
            val hot = highlighted == item.key
            val grow by animateFloatAsState(if (hot) 1.22f else 1f, Motion.spring(dampingRatio = 0.6f, stiffness = 900f), label = "hot")
            val fit = if (item in innerItems) WheelGeometry.COLOR_CELL_DP / WheelGeometry.CELL_DP else 1f // colour dots are smaller
            Box(
              Modifier.graphicsLayer {
                scaleX = grow * fit
                scaleY = grow * fit
              }.then(if (hot) Modifier.background(PenLabColors.optionSelected, CircleShape) else Modifier)
            ) { cell(item, true) }
          }
        }) { measurables, constraints ->
          val loose = constraints.copy(minWidth = 0, minHeight = 0)
          val placeables = measurables.map { it.measure(loose) }
          val r = dock.wheelRadiusPx
          layout((2 * r).roundToInt(), (2 * r).roundToInt()) {
            // (u, v): from the circle's centre (the hub) along the bottom/top edge and
            // along the side edge, into the screen (negative: back towards the edge).
            fun place(index: Int, u: Float, v: Float, alpha: Float) {
              val p = placeables[index]
              val x = r + if (onLeft) u else -u
              val y = r + if (onTop) v else -v
              // Placed with a layer so a tool spinning past either end of the arc fades out
              // instead of showing half cut by the screen edge or sitting in a gesture strip.
              p.placeWithLayer((x - p.width / 2f).roundToInt(), (y - p.height / 2f).roundToInt()) { this.alpha = alpha }
            }
            val targets = HashMap<Int, Offset>()
            fun polar(index: Int, item: DockItem, ringLayout: WheelGeometry.Ring, slot: Int, scrollDeg: Float) {
              val angleDeg = ringLayout.startDeg + slot * ringLayout.stepDeg + scrollDeg // cells move with the finger
              val (u, v) = WheelGeometry.ringSlot(ringLayout.radius, angleDeg)
              // Fan-out: each tool trails the one before it out along its spoke while opening.
              // Pull targets use the settled spot, so a quick pull during the animation still hits.
              val k = (slot - (-scrollDeg / ringLayout.stepDeg.coerceAtLeast(1f))).coerceIn(0f, 3f) * FAN_LAG
              val lag = ((open.value - k) / (1f - k)).coerceIn(0f, 1.06f) // 1 at rest; the spring's overshoot shows
              val f = 0.55f + 0.45f * lag
              place(index, u * f, v * f, WheelGeometry.ringAlpha(angleDeg, ringLayout))
              if (WheelGeometry.slotOnScreen(angleDeg, ringLayout)) targets[item.key] = Offset(u, v) // visible on the arc
            }
            // Two rings: the first cell along the bottom edge, the rest climbing towards the side
            // edge, shifted by that ring's scroll. Cells past either end are round the circle,
            // behind the screen edge, until spun in.
            innerItems.forEachIndexed { i, item -> polar(i, item, dock.wheelColorRing, i, scrollIn.value) }
            outerItems.forEachIndexed { j, item -> polar(innerItems.size + j, item, dock.wheelRing, j, scrollOut.value) }
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
                // Off either end of the arc (out by the tools), the ring rolls on by itself: faster
                // the further past the end, bringing the hidden tools round.
                if (!released) {
                  val o = dock.wheelOrigin
                  val u = if (onLeft) at.x - o.x else o.x - at.x
                  val v = if (onTop) at.y - o.y else o.y - at.y
                  val dist = kotlin.math.hypot(u, v)
                  // The ring that rolls is the one the finger is nearer to (inner or outer).
                  val useOuter = dist > WheelGeometry.ringSplitRadius(dock.wheelColorRing, dock.wheelRing)
                  val ring = if (useOuter) dock.wheelRing else dock.wheelColorRing
                  val scroll = scrollOf(useOuter)
                  if (dist > dock.wheelColorRing.radius * 0.6f) {
                    // Auto-scroll zone: beyond the outermost visible cell's angular extent, off either end of the arc.
                    val cellDp = if (useOuter) WheelGeometry.CELL_DP else WheelGeometry.COLOR_CELL_DP
                    val cellHalfDeg = Math.toDegrees(kotlin.math.asin((cellDp * density.density / 2f / ring.radius).toDouble().coerceAtMost(1.0))).toFloat()
                    val zoneDeg = maxOf(ring.stepDeg * 0.8f, 8f)
                    val mid = (ring.startDeg + ring.endDeg) / 2f
                    val raw = Math.toDegrees(kotlin.math.atan2(v.toDouble(), u.toDouble())).toFloat()
                    // The angle on the same turn as the arc's middle, so the gap behind the hub is one stretch.
                    val phi = mid + ((raw - mid + 540f) % 360f - 180f)
                    val lowEnd = ring.startDeg - cellHalfDeg
                    val highEnd = ring.endDeg + cellHalfDeg
                    val step = when {
                      phi < lowEnd -> ((lowEnd - phi) / zoneDeg).coerceAtMost(1.5f) // past the first tool: tools come round
                      phi > highEnd -> -((phi - highEnd) / zoneDeg).coerceAtMost(1.5f)
                      else -> 0f
                    } * AUTO_SCROLL_DEG_PER_FRAME
                    if (step != 0f) {
                      val next = (scroll.value + step).coerceIn(-spanNow(useOuter), 0f)
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
            role = Role.Button
            // The wheel is driven by raw pointer gestures, which TalkBack's double tap does not send.
            onClick(label = if (dock.wheelOpen) "Close tools" else "Open tools") {
              dock.wheelOpen = !dock.wheelOpen
              true
            }
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
    // The ring layout (and so the disc's size) is worked out first: the wheel is measured from it.
    dock.screenWidth = constraints.maxWidth
    dock.screenHeight = constraints.maxHeight
    dock.applyWheelMetrics(this, insets, side)
    val wheel = measurables[0].measure(loose)
    val hub = measurables[1].measure(loose)
    layout(constraints.maxWidth, constraints.maxHeight) {
      // The disc turns round the hub, which is placed (WheelGeometry.ring) so the ring cells stay
      // out of the system gesture zones (Back on the sides, Home at the bottom) and, at the top,
      // out from under the top buttons; only its rim runs off the screen edges.
      val c = dock.cornerLayout(side)
      val hubHalf = HubSize.roundToPx() / 2
      dock.wheelHub = Offset(c.hubX, c.hubY)
      val origin = Offset(c.hubX, c.hubY)
      dock.wheelOrigin = origin
      dock.wheelExclusion = WheelGeometry.exclusionRects(
        onLeft, onTop, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), c,
        dock.wheelRadiusPx, MaxExclusionHeight.roundToPx().toFloat(),
      )
      dock.hubExclusion = WheelGeometry.hubExclusionRects(
        onLeft, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), c, hubHalf.toFloat(), dock.edgeGapPx,
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

/** Press-and-pull: no cell is pointed at within [PullDeadZone] of the button; a cell counts within [PullReach]. */
private val PullDeadZone = 55.dp
private val PullReach = 104.dp

/** Android honours at most 200 dp of exclusion per edge. */
private val MaxExclusionHeight = WheelGeometry.MAX_EXCLUSION_HEIGHT_DP.dp

/** Keeps [rects] (root pixels) out of the system Back gesture; an empty list clears it. */
private fun setGestureExclusion(view: android.view.View, rects: List<WheelGeometry.PxRect>) {
  view.systemGestureExclusionRects = rects.map { android.graphics.Rect(it.left, it.top, it.right, it.bottom) }
}

/** Press-and-pull: near a screen edge, past the outermost visible tools, the ring scrolls by itself. */
private const val AUTO_SCROLL_DEG_PER_FRAME = 2.5f

/** Fan-out: how far (in opening progress) each tool trails the one before it. */
private const val FAN_LAG = 0.08f

/** Returns true when the hub's tool preview well needs the inverse surface for contrast. */
internal fun shouldUseInverseHubWell(luminance: Float, isDarkTheme: Boolean): Boolean =
  (isDarkTheme && luminance < 0.3f) || (!isDarkTheme && luminance > 0.42f)

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
  return if (shouldUseInverseHubWell(lum, isDarkTheme)) scheme.inverseSurface else scheme.surfaceContainerLow
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
      DockSide.FLOATING ->
        drawCircle(
          color = PenLabColors.icon.copy(alpha = 0.28f),
          radius = BallSize.toPx() * 0.85f,
          center = dock.ball.value,
          style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)),
        )
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


/** Available measurement constraints and opening side for [PanelBeside]. */
internal data class PanelBesidePlan(
  val maxWidthPx: Int,
  val maxHeightPx: Int,
  val openBelow: Boolean,
)

/**
 * Computes maximum width/height and opening direction for a popover beside the docked toolbar
 * so the popover shrinks/scrolls cleanly in the clear viewport space and never covers [barTop..barBottom].
 */
internal fun computePanelBesidePlan(
  side: DockSide,
  fromTopBar: Boolean,
  anchorY: Float,
  barLeft: Float,
  barTop: Float,
  barRight: Float,
  barBottom: Float,
  barEmpty: Boolean,
  screenWidth: Int,
  screenHeight: Int,
  marginPx: Int,
  gapPx: Int,
  topBarGapPx: Int,
): PanelBesidePlan {
  val fullW = (screenWidth - 2 * marginPx).coerceAtLeast(1)
  val fullH = (screenHeight - 2 * marginPx).coerceAtLeast(1)
  if (fromTopBar) {
    val maxH = ((screenHeight - marginPx) - (anchorY + topBarGapPx)).roundToInt().coerceIn(minOf(160, fullH), fullH)
    return PanelBesidePlan(maxWidthPx = fullW, maxHeightPx = maxH, openBelow = true)
  }
  if (side.isCorner || barEmpty) {
    val openBelow = anchorY < screenHeight * 0.55f
    return PanelBesidePlan(maxWidthPx = fullW, maxHeightPx = fullH, openBelow = openBelow)
  }
  if (side.isVertical) {
    val availW =
      if (side == DockSide.LEFT) ((screenWidth - marginPx) - (barRight + gapPx)).roundToInt()
      else ((barLeft - gapPx) - marginPx).roundToInt()
    val maxW = availW.coerceIn(minOf(200, fullW), fullW)
    return PanelBesidePlan(maxWidthPx = maxW, maxHeightPx = fullH, openBelow = true)
  }
  val spaceBelow = ((screenHeight - marginPx) - (barBottom + gapPx)).roundToInt()
  val spaceAbove = ((barTop - gapPx) - marginPx).roundToInt()
  val minPreferred = minOf(240, fullH)
  val openBelow =
    when (side) {
      DockSide.TOP -> spaceBelow >= minPreferred || spaceBelow >= spaceAbove
      DockSide.BOTTOM -> spaceBelow > spaceAbove && spaceAbove < minPreferred
      else -> spaceBelow >= spaceAbove
    }
  val maxH = (if (openBelow) spaceBelow else spaceAbove).coerceIn(minOf(120, fullH), fullH)
  return PanelBesidePlan(maxWidthPx = fullW, maxHeightPx = maxH, openBelow = openBelow)
}

/**
 * Places a measured popover (`panelW × panelH`) strictly outside `barBounds` (when non-empty)
 * and clamped within `[marginPx, screen - marginPx]`.
 */
internal fun computePanelBesidePosition(
  panelW: Int,
  panelH: Int,
  side: DockSide,
  fromTopBar: Boolean,
  anchorX: Float,
  anchorY: Float,
  gripCenterX: Float,
  gripCenterY: Float,
  barLeft: Float,
  barTop: Float,
  barRight: Float,
  barBottom: Float,
  barEmpty: Boolean,
  screenWidth: Int,
  screenHeight: Int,
  marginPx: Int,
  gapPx: Int,
  reachPx: Float,
  topBarGapPx: Float,
  openBelow: Boolean,
): Pair<Int, Int> {
  val (x, y) =
    if (fromTopBar) {
      (anchorX - panelW / 2f) to (anchorY + topBarGapPx)
    } else if (!barEmpty && !side.isCorner) {
      when {
        side == DockSide.LEFT -> (barRight + gapPx) to (anchorY - panelH / 2f)
        side == DockSide.RIGHT -> (barLeft - gapPx - panelW) to (anchorY - panelH / 2f)
        openBelow -> (anchorX - panelW / 2f) to (barBottom + gapPx)
        else -> (anchorX - panelW / 2f) to (barTop - gapPx - panelH)
      }
    } else {
      when (side) {
        DockSide.LEFT -> (anchorX + reachPx) to (anchorY - panelH / 2f)
        DockSide.RIGHT -> (anchorX - reachPx - panelW) to (anchorY - panelH / 2f)
        DockSide.TOP -> (anchorX - panelW / 2f) to (anchorY + reachPx)
        DockSide.BOTTOM -> (anchorX - panelW / 2f) to (anchorY - reachPx - panelH)
        DockSide.FLOATING ->
          if (openBelow) (anchorX - panelW / 2f) to (anchorY + reachPx)
          else (anchorX - panelW / 2f) to (anchorY - reachPx - panelH)
        // From the wheel: the wheel has folded away, so the panel opens beside the round button,
        // where the wheel was, level with the button's outer edge.
        DockSide.TOP_LEFT -> (gripCenterX + reachPx) to (gripCenterY - reachPx)
        DockSide.TOP_RIGHT -> (gripCenterX - reachPx - panelW) to (gripCenterY - reachPx)
        DockSide.BOTTOM_LEFT -> (gripCenterX + reachPx) to (gripCenterY + reachPx - panelH)
        DockSide.BOTTOM_RIGHT -> (gripCenterX - reachPx - panelW) to (gripCenterY + reachPx - panelH)
      }
    }
  val clampedX = x.roundToInt().coerceIn(marginPx, maxOf(marginPx, screenWidth - panelW - marginPx))
  val clampedY = y.roundToInt().coerceIn(marginPx, maxOf(marginPx, screenHeight - panelH - marginPx))
  return clampedX to clampedY
}

/**
 * The open options panel, beside its cell: right of a left rail, below a top bar, towards the
 * middle of the screen from a corner wheel. Always inside the screen.
 */
@Composable
private fun PanelBeside(dock: ToolDock, side: DockSide, key: Int, panel: @Composable () -> Unit) {
  val appear = remember(key) { Animatable(0f) }
  androidx.compose.runtime.LaunchedEffect(key) { appear.animateTo(1f, Motion.spring(dampingRatio = 1f, stiffness = 440f)) }
  val barEmpty = dock.barBounds.isEmpty
  val screenH = dock.screenHeight.coerceAtLeast(1)
  val openBelowFloat =
    if (!barEmpty) {
      ((screenH - dock.barBounds.bottom) >= (dock.barBounds.top))
    } else {
      dock.floatY < 0.55f
    }
  val origin =
    when (side) {
      DockSide.LEFT, DockSide.TOP_LEFT, DockSide.BOTTOM_LEFT -> TransformOrigin(0f, 0.5f)
      DockSide.RIGHT, DockSide.TOP_RIGHT, DockSide.BOTTOM_RIGHT -> TransformOrigin(1f, 0.5f)
      DockSide.TOP -> TransformOrigin(0.5f, 0f)
      DockSide.BOTTOM -> TransformOrigin(0.5f, 1f)
      DockSide.FLOATING -> TransformOrigin(0.5f, if (openBelowFloat) 0f else 1f)
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
    val fromTopBar = side.isCorner && key in dock.topBarCells
    val fallbackAnchor = dock.cellCenters[MORE_CELL] ?: dock.barBounds.center.takeIf { !dock.barBounds.isEmpty }
    val anchor = (if (fromTopBar) dock.topBarCells[key] else if (side.isCorner) dock.gripCenter else dock.cellCenters[key] ?: fallbackAnchor)
    val margin = 8.dp.roundToPx()
    val gap = 8.dp.roundToPx()
    val topBarGap = 28.dp.toPx()
    val plan =
      computePanelBesidePlan(
        side = side,
        fromTopBar = fromTopBar,
        anchorY = anchor?.y ?: (constraints.maxHeight / 2f),
        barLeft = dock.barBounds.left,
        barTop = dock.barBounds.top,
        barRight = dock.barBounds.right,
        barBottom = dock.barBounds.bottom,
        barEmpty = dock.barBounds.isEmpty,
        screenWidth = constraints.maxWidth,
        screenHeight = constraints.maxHeight,
        marginPx = margin,
        gapPx = gap,
        topBarGapPx = topBarGap.roundToInt(),
      )
    val placeable =
      measurables.firstOrNull()?.measure(
        constraints.copy(
          minWidth = 0,
          minHeight = 0,
          maxWidth = minOf(constraints.maxWidth, plan.maxWidthPx),
          maxHeight = minOf(constraints.maxHeight, plan.maxHeightPx),
        )
      )
    layout(constraints.maxWidth, constraints.maxHeight) {
      val p = placeable ?: return@layout
      val resolvedAnchor = anchor ?: return@layout
      val reach = if (side.isVertical) 34.dp.toPx() else 30.dp.toPx()
      val (x, y) =
        computePanelBesidePosition(
          panelW = p.width,
          panelH = p.height,
          side = side,
          fromTopBar = fromTopBar,
          anchorX = resolvedAnchor.x,
          anchorY = resolvedAnchor.y,
          gripCenterX = dock.gripCenter.x,
          gripCenterY = dock.gripCenter.y,
          barLeft = dock.barBounds.left,
          barTop = dock.barBounds.top,
          barRight = dock.barBounds.right,
          barBottom = dock.barBounds.bottom,
          barEmpty = dock.barBounds.isEmpty,
          screenWidth = constraints.maxWidth,
          screenHeight = constraints.maxHeight,
          marginPx = margin,
          gapPx = gap,
          reachPx = reach,
          topBarGapPx = topBarGap,
          openBelow = plan.openBelow,
        )
      p.place(x, y)
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
  moreHoldsTool: Boolean,
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
    DockItem.More -> ActionCell(PenLabIcons.ChevronDown, if (moreHoldsTool) "More tools, a tool from here is active" else "More tools", enabled = true, active = openPanelKey == MORE_CELL || moreHoldsTool, showActiveDot = moreHoldsTool, round = round, compactHorizontal = compactHorizontal, modifier = track) { onItem(item) }
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

@Composable
private fun CollapseCell(dock: ToolDock, vertical: Boolean, onDragStart: () -> Unit) {
  val collapsed = dock.collapsed
  ActionCell(
    icon = if (collapsed) PenLabIcons.FullScreen else PenLabIcons.FullScreenExit,
    label = if (collapsed) "Expand toolbar" else "Collapse toolbar",
    enabled = true,
    active = false,
    showActiveDot = false,
    round = false,
    compactHorizontal = !vertical,
    modifier = Modifier,
    onClick = {
      onDragStart()
      dock.toggleCollapsed()
    },
  )
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
