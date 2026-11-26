package dev.penlab.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import dev.penlab.app.ink.InkColors
import dev.penlab.app.ink.InkTool
import dev.penlab.app.ink.InkTools
import dev.penlab.app.ui.theme.PenLabColors
import dev.penlab.app.ui.theme.PenLabLayout
import dev.penlab.app.ui.theme.softShadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The tool strip, docked to a side edge. Top to bottom:
 *  - a grip to drag the strip to another spot;
 *  - tools (pens, highlighter, eraser): pick one; tap the picked one again for its options;
 *  - undo and redo;
 *  - the page: paper style and clear.
 *
 * It only reports taps; the screen that hosts it decides what they do. [showHistory] is false in
 * short windows, where undo and redo give way (the 2- and 3-finger taps cover them).
 *
 * Pens are round dots, the highlighter is a square chip and the eraser is an icon, so the
 * kinds are told apart by shape as well as colour.
 */
@Composable
fun InkToolbar(
  tools: List<InkTool>,
  selectedIndex: Int,
  onToolTap: (Int) -> Unit,
  darkPage: Boolean,
  canUndo: Boolean,
  canRedo: Boolean,
  canClear: Boolean,
  showHistory: Boolean,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  onPaperTap: () -> Unit,
  onInstrumentsTap: () -> Unit,
  onClear: () -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
  gripModifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(ToolbarWidth / 2)
  Column(
    modifier =
      modifier
        .width(ToolbarWidth)
        .softShadow(shape).clip(shape)
        .background(PenLabColors.toolbar)
        .ignorePalm(isPalm)
        .verticalScroll(rememberScrollState()) // last resort for very short windows
        .padding(bottom = PillPadding)
        .semantics { contentDescription = "Drawing tools" },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // The grip: drag it to move the strip. A short bar, the usual sign for "drag me".
    Box(
      gripModifier.size(ToolbarWidth, GripHeight).semantics {
        contentDescription = "Move toolbar. Drag to either side edge."
      },
      contentAlignment = Alignment.Center,
    ) {
      Box(Modifier.size(width = 18.dp, height = 4.dp).background(PenLabColors.iconDisabled, CircleShape))
    }

    Column(Modifier.selectableGroup(), horizontalAlignment = Alignment.CenterHorizontally) {
      tools.forEachIndexed { index, tool ->
        ToolCell(tool, selected = index == selectedIndex, darkPage, onClick = { onToolTap(index) })
      }
    }

    if (showHistory) {
      ToolbarDivider()
      ToolbarAction(UndoIcon, "Undo", enabled = canUndo, onClick = onUndo)
      ToolbarAction(RedoIcon, "Redo", enabled = canRedo, onClick = onRedo)
    }

    ToolbarDivider()
    ToolbarAction(PaperIcon, "Paper style", onClick = onPaperTap)
    ToolbarAction(RulerIcon, "Ruler and protractor", onClick = onInstrumentsTap)
    ToolbarAction(ClearIcon, "Clear page", enabled = canClear, onClick = onClear)
  }
}

/** Stands for the paper button where a cell number is needed (tools use their own index). */
const val PAPER_CELL = -1

/** Stands for the ruler-and-protractor button. */
const val INSTRUMENTS_CELL = -2

/** How tall the strip is, so the host can tell whether undo and redo fit in the window. */
fun toolbarHeight(toolCount: Int, showHistory: Boolean): Dp =
  GripHeight +
    CellSize * (toolCount + 3 + if (showHistory) 2 else 0) +
    DividerHeight * (if (showHistory) 2 else 1) +
    PillPadding

/** Distance from the top of the strip to the middle of a cell: where its panel lines up. */
fun toolbarCellCenter(cell: Int, toolCount: Int, showHistory: Boolean): Dp =
  if (cell == PAPER_CELL) {
    toolbarHeight(toolCount, showHistory) - PillPadding - CellSize * 2 - CellSize / 2
  } else if (cell == INSTRUMENTS_CELL) {
    toolbarHeight(toolCount, showHistory) - PillPadding - CellSize - CellSize / 2
  } else {
    GripHeight + CellSize * cell + CellSize / 2
  }

/**
 * Where the strip sits. It is docked to the left or right edge at some height; dragging its
 * grip moves it freely, and letting go docks it to the nearer edge at the height it was
 * dropped (it is never pulled back to the middle).
 *
 * The position is stored as "which edge" and "how far down, 0 to 1", not as pixels, so it
 * still makes sense after the tablet is rotated or the window resized.
 */
@Stable
class ToolbarPlacement {
  var onRightEdge by mutableStateOf(false)
    private set

  private var heightFraction by mutableFloatStateOf(0.5f)

  /** How far the strip is from its docked spot while it is dragged or settling back. */
  private val dragOffset = Animatable(Offset.Zero, Offset.VectorConverter)

  // Sizes from the latest layout pass, needed to turn the fractions into pixels.
  private var areaWidth = 0
  private var areaHeight = 0
  private var stripWidth = 0
  private var stripHeight = 0
  private var edgeGap = 0

  internal fun onLayout(areaWidth: Int, areaHeight: Int, stripWidth: Int, stripHeight: Int, edgeGap: Int) {
    this.areaWidth = areaWidth
    this.areaHeight = areaHeight
    this.stripWidth = stripWidth
    this.stripHeight = stripHeight
    this.edgeGap = edgeGap
  }

  private val lowestTop: Int
    get() = maxOf(edgeGap, areaHeight - stripHeight - edgeGap)

  private fun dockedPosition(): Offset {
    val x = if (onRightEdge) areaWidth - stripWidth - edgeGap else edgeGap
    val y = edgeGap + (lowestTop - edgeGap) * heightFraction
    return Offset(x.toFloat(), y)
  }

  /** True when the strip is docked (not being dragged or gliding into place). */
  internal val isDocked: Boolean
    get() = dragOffset.value == Offset.Zero && !dragOffset.isRunning

  /** Top-left corner of the strip right now. Read during layout, so moving it never recomposes. */
  internal fun position(): IntOffset = (dockedPosition() + dragOffset.value).round()

  suspend fun dragBy(delta: Offset) = dragOffset.snapTo(dragOffset.value + delta)

  /** Docks to the nearer edge at the current height and glides the last bit of the way. */
  suspend fun settle() {
    val dropped = dockedPosition() + dragOffset.value
    onRightEdge = dropped.x + stripWidth / 2f > areaWidth / 2f
    val range = (lowestTop - edgeGap).coerceAtLeast(1)
    heightFraction = ((dropped.y - edgeGap) / range).coerceIn(0f, 1f)
    dragOffset.snapTo(dropped - dockedPosition())
    dragOffset.animateTo(Offset.Zero, spring(dampingRatio = 0.85f, stiffness = 500f))
  }
}

/** Makes the strip's grip drag the strip. [onDragStart] lets the host close an open panel. */
fun Modifier.movesToolbar(placement: ToolbarPlacement, scope: CoroutineScope, onDragStart: () -> Unit): Modifier =
  pointerInput(placement) {
    detectDragGestures(
      onDragStart = { onDragStart() },
      onDragEnd = { scope.launch { placement.settle() } },
      onDragCancel = { scope.launch { placement.settle() } },
    ) { change, dragAmount ->
      change.consume()
      scope.launch { placement.dragBy(dragAmount) }
    }
  }

/**
 * Lays out the strip and its options panel as one unit: the strip goes where [placement] says,
 * and the panel sits on its page side, level with the cell [panelCellCenter] below the strip's
 * top, kept inside the window. Doing this in one layout pass means the panel never lags a
 * frame behind the strip, and no position callbacks are needed.
 *
 * [content] is the strip, then optionally the panel (a hidden panel is simply not there).
 */
@Composable
fun ToolbarWithPanel(
  placement: ToolbarPlacement,
  panelCellCenter: Dp,
  modifier: Modifier = Modifier,
  /** Told how much of the screen's left and right the docked strip covers (pixels). */
  onSidesCovered: (left: Float, right: Float) -> Unit = { _, _ -> },
  content: @Composable () -> Unit,
) {
  Layout(content, modifier) { measurables, constraints ->
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val strip = measurables[0].measure(loose)
    val panel = measurables.getOrNull(1)?.measure(loose) // absent while no panel is shown
    placement.onLayout(constraints.maxWidth, constraints.maxHeight, strip.width, strip.height, EdgeGap.roundToPx())

    layout(constraints.maxWidth, constraints.maxHeight) {
      val stripAt = placement.position()
      strip.place(stripAt)
      // The strip plus a small gap is off limits for the page (on the side it is docked to).
      if (placement.isDocked) {
        val gap = PanelGap.roundToPx()
        if (placement.onRightEdge) onSidesCovered(0f, (constraints.maxWidth - stripAt.x + gap).toFloat())
        else onSidesCovered((stripAt.x + strip.width + gap).toFloat(), 0f)
      }

      if (panel != null) {
        val margin = PanelGap.roundToPx()
        val lowest = maxOf(margin, constraints.maxHeight - panel.height - margin)
        val y = (stripAt.y + panelCellCenter.roundToPx() - panel.height / 2).coerceIn(margin, lowest)
        val x = if (placement.onRightEdge) stripAt.x - margin - panel.width else stripAt.x + strip.width + margin
        panel.place(x, y)
      }
    }
  }
}

/** One tool. The whole 48 dp square is the touch target; the mark inside is smaller. */
@Composable
private fun ToolCell(
  tool: InkTool,
  selected: Boolean,
  darkPage: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  // These three are only ever read inside graphicsLayer blocks, so an animation frame redraws
  // the cell without re-running this function.
  val pressScale by animateFloatAsState(if (pressed) 0.94f else 1f, PressSpring, label = "press")
  val markScale by animateFloatAsState(if (selected) SelectedMarkScale else 1f, SelectTween, label = "mark")
  val ringAlpha by animateFloatAsState(if (selected) 1f else 0f, SelectTween, label = "ring")

  val isChip = tool.isHighlighter
  val ringShape = if (isChip) RoundedCornerShape(10.dp) else CircleShape

  Box(
    modifier =
      modifier
        .size(CellSize)
        .selectable(
          selected = selected,
          interactionSource = interaction,
          indication = ripple(bounded = false, radius = 20.dp),
          role = Role.RadioButton,
          onClick = onClick,
        )
        .semantics {
          contentDescription = InkTools.describe(tool)
          if (selected) {
            stateDescription = if (tool.isLasso) "Selected" else "Selected. Double tap for size and colour."
          }
        }
        .graphicsLayer {
          scaleX = pressScale
          scaleY = pressScale
        },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier.size(36.dp)
        .graphicsLayer { alpha = ringAlpha }
        .border(2.dp, PenLabColors.icon, ringShape)
    )
    val grow = Modifier.graphicsLayer {
      scaleX = markScale
      scaleY = markScale
    }
    if (tool.isEraser || tool.isLasso || tool.isText || tool.isFill) {
      val icon = when {
        tool.isFill -> FillIcon
        tool.isLasso -> LassoIcon
        tool.isText -> TextIcon
        else -> EraserIcon
      }
      Icon(icon, contentDescription = null, tint = PenLabColors.icon, modifier = grow.size(MarkSize))
    } else {
      ColorMark(tool.paperColor, darkPage, MarkSize, if (isChip) RoundedCornerShape(5.dp) else CircleShape, grow)
    }
  }
}

/** A dot or chip in the colour a tool draws on screen, opaque so see-through colours show. */
@Composable
internal fun ColorMark(
  paperColor: Int,
  darkPage: Boolean,
  size: Dp,
  shape: Shape,
  modifier: Modifier = Modifier,
) {
  val onScreen = Color(InkColors.onScreen(paperColor, darkPage) or 0xFF000000.toInt())
  Box(modifier.size(size).background(onScreen, shape).border(1.dp, PenLabColors.swatchEdge, shape))
}

@Composable
private fun ToolbarAction(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(CellSize)) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = if (enabled) PenLabColors.icon else PenLabColors.iconDisabled,
      modifier = Modifier.size(PenLabLayout.iconDefault),
    )
  }
}

@Composable
private fun ToolbarDivider() {
  Box(Modifier.height(DividerHeight), contentAlignment = Alignment.Center) {
    Box(Modifier.width(24.dp).height(1.dp).background(PenLabColors.hairline))
  }
}

/**
 * Drops finger touches while [isPalm] says the pen is writing, so the writing hand resting on
 * the toolbar cannot press anything. The pen itself always gets through.
 */
internal fun Modifier.ignorePalm(isPalm: () -> Boolean): Modifier =
  pointerInput(Unit) {
    awaitPointerEventScope {
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (isPalm()) {
          event.changes.forEach { if (it.type == PointerType.Touch) it.consume() }
        }
      }
    }
  }

internal val ToolbarWidth = 56.dp
internal val CellSize = 48.dp
private val DividerHeight = 13.dp
private val PillPadding = 6.dp
private val GripHeight = 26.dp
private val MarkSize = 22.dp
private const val SelectedMarkScale = 26f / 22f

/** Strip to screen edge. */
private val EdgeGap = 12.dp

/** Strip to panel, and panel to window edge. */
private val PanelGap = 8.dp

/** Tool switching happens constantly, so it is quick and never bounces. */
private val SelectTween = tween<Float>(durationMillis = 120, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
private val PressSpring = spring<Float>(dampingRatio = 1f, stiffness = 1500f)
