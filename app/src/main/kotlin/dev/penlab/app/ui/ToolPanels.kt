package dev.penlab.app.ui

import androidx.compose.foundation.layout.widthIn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import dev.penlab.app.ink.InkColors
import dev.penlab.app.ink.InkTool
import dev.penlab.app.ink.InkTools
import dev.penlab.app.ink.PageBackground
import dev.penlab.app.ink.PageStyle
import dev.penlab.app.ink.PenStyle
import dev.penlab.app.ink.LineType
import dev.penlab.app.ui.components.PenLabPopoverSurface
import dev.penlab.app.ui.components.PenLabSnackbar
import dev.penlab.app.ui.components.PenLabSwitchRow
import dev.penlab.app.ui.theme.LocalPenLabColors
import dev.penlab.app.ui.theme.PenLabColors
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.floatingChromeSurface
import dev.penlab.app.ui.theme.softShadow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin

/**
 * The small panel that opens beside a tool when it is tapped a second time: three sizes, and
 * for pens and highlighters a row or two of colours. Picking something applies it at once.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ToolOptionsPanel(
  tool: InkTool,
  darkPage: Boolean,
  favourites: List<Float>,
  mmPerDp: Float,
  onStylePicked: (PenStyle) -> Unit,
  onLinePicked: (LineType) -> Unit,
  onEraseModePicked: (partial: Boolean) -> Unit,
  onSizeChanging: (Float) -> Unit,
  onSizePicked: (Float) -> Unit,
  onAddFavourite: () -> Unit,
  onRemoveFavourite: (Float) -> Unit,
  onColorPicked: (Int) -> Unit,
  onMoreColors: () -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
) {
  val palette = InkTools.colorsFor(tool.kind)
  // A colour picked from the wheel shows as an extra swatch, so it can be seen and re-picked.
  val colors =
    if (palette.isEmpty() || palette.any { it.paperColor == tool.paperColor }) palette
    else palette + dev.penlab.app.ink.NamedColor("Custom", tool.paperColor)
  Panel(modifier, isPalm) {
    if (tool.kind == InkTool.Kind.PEN) {
      Row(Modifier.selectableGroup().semantics { contentDescription = "Pen type" }) {
        for (style in PenStyle.entries) {
          Option(
            label = style.label,
            selected = style == tool.style,
            onClick = { onStylePicked(style) },
            modifier = Modifier.size(width = PanelCell * 4 / 3, height = 64.dp),
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              PenStylePreview(style, tool.paperColor, darkPage)
              Text(style.label, color = PenLabColors.icon, style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }
      PanelDivider()
      // Line type, for technical drawings: solid, dotted, dashed (hidden edges), centre, phantom.
      Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup().semantics { contentDescription = "Line type" }) {
        for (line in LineType.entries) {
          Option(
            label = line.label,
            selected = line == tool.line,
            onClick = { onLinePicked(line) },
            modifier = Modifier.size(width = PanelCell, height = 56.dp),
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              LinePreview(line, tool.paperColor, darkPage)
              // The panel is 5 x PanelCell (240 dp) wide and each cell has 44 dp inside its 2 dp inset, so
              // "Phantom" needs labelSmall at 10 sp without letter spacing (about 40 dp) to fit.
              Text(line.label, color = PenLabColors.icon, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp), maxLines = 1, softWrap = false)
            }
          }
        }
      }
      PanelDivider()
    }
    if (tool.isEraser) {
      Row(Modifier.selectableGroup().semantics { contentDescription = "What the eraser removes" }) {
        for ((partial, label) in listOf(false to "Whole strokes", true to "Part of a stroke")) {
          Option(
            label = label,
            selected = tool.partialErase == partial,
            onClick = { onEraseModePicked(partial) },
            modifier = Modifier.size(width = PanelCell * 5 / 2, height = PanelCell),
          ) {
            Text(label, color = PenLabColors.icon, style = MaterialTheme.typography.labelLarge)
          }
        }
      }
      PanelDivider()
    }

    // Size: a live preview, the exact value, a slider over the whole range, then favourites.
    Row(verticalAlignment = Alignment.CenterVertically) {
      SizePreview(tool, tool.sizeDp, darkPage)
      Spacer(Modifier.width(8.dp))
      val label = if (tool.isEraser) "Eraser size" else "Line width"
      Text(label, color = PenLabColors.iconDisabled, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
      Text(
        "%.2f mm".format(tool.sizeDp * mmPerDp * (if (tool.isEraser) 2 else 1)),
        color = PenLabColors.icon,
        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
      )
    }
    val range = InkTools.sizeRange(tool.kind)
    Slider(
      value = tool.sizeDp.coerceIn(range),
      onValueChange = onSizeChanging,
      valueRange = range,
      colors =
        SliderDefaults.colors(
          thumbColor = MaterialTheme.colorScheme.primary,
          activeTrackColor = MaterialTheme.colorScheme.primary,
          inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
        ),
      modifier = Modifier.semantics { contentDescription = if (tool.isEraser) "Eraser size" else "Line width" },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      for (sizeDp in favourites) {
        val shape = RoundedCornerShape(12.dp)
        Box(
          Modifier.size(width = 56.dp, height = PanelCell)
            .clip(shape)
            .background(if (sizeDp == tool.sizeDp) PenLabColors.optionSelected else Color.Transparent, shape)
            .combinedClickable(onClick = { onSizePicked(sizeDp) }, onLongClick = { onRemoveFavourite(sizeDp) })
            .semantics { contentDescription = "Favourite size $sizeDp. Long press to remove." },
          contentAlignment = Alignment.Center,
        ) {
          SizePreview(tool, sizeDp, darkPage)
        }
      }
      Box(
        Modifier.size(width = 56.dp, height = PanelCell)
          .clip(RoundedCornerShape(12.dp))
          .clickable(onClick = onAddFavourite)
          .semantics { contentDescription = "Save this size as a favourite" },
        contentAlignment = Alignment.Center,
      ) {
        Text("+", color = PenLabColors.icon, style = MaterialTheme.typography.titleMedium)
      }
    }

    if (colors.isNotEmpty()) {
      PanelDivider()
      Column(Modifier.selectableGroup().semantics { contentDescription = "Colour" }) {
        for (row in colors.chunked(4)) {
          Row {
            for (color in row) {
              Option(
                label = color.name,
                selected = color.paperColor == tool.paperColor,
                onClick = { onColorPicked(color.paperColor) },
                modifier = Modifier.size(PanelCell),
              ) {
                val shape = if (tool.isHighlighter) RoundedCornerShape(7.dp) else CircleShape
                ColorMark(color.paperColor, darkPage, 28.dp, shape)
              }
            }
          }
        }
        // The colour wheel: every other colour.
        Option(label = "More colours", selected = false, onClick = onMoreColors, modifier = Modifier.size(width = PanelCell * 5, height = PanelCell)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(26.dp)) {
              val hues = List(7) { Color.hsv(it * 60f % 360f, 0.75f, 0.95f) }
              drawCircle(androidx.compose.ui.graphics.Brush.sweepGradient(hues), radius = size.minDimension / 2 - 3.dp.toPx(), style = Stroke(5.dp.toPx()))
            }
            Spacer(Modifier.width(10.dp))
            Text("More colours", color = PenLabColors.icon, style = MaterialTheme.typography.labelLarge)
          }
        }
      }
    }
  }
}

/**
 * The panel behind the ruler button: switch the ruler and the protractor on or off. When on,
 * they lie on the page: drag with one finger, turn with two, draw along an edge with the pen.
 */
@Composable
fun InstrumentsPanel(
  rulerOn: Boolean,
  protractorOn: Boolean,
  onRuler: (Boolean) -> Unit,
  onProtractor: (Boolean) -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
) {
  Panel(modifier, isPalm) {
    SwitchRow("Ruler", rulerOn, onRuler)
    SwitchRow("Protractor", protractorOn, onProtractor)
    Text(
      "Drag with one finger, turn with two. Draw along an edge with the pen for a straight line.",
      color = PenLabColors.iconDisabled,
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(top = 6.dp),
    )
  }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  PenLabSwitchRow(label = label, checked = checked, onCheckedChange = onChange)
}

/** The panel behind the paper button: blank, lined or dotted. */
@Composable
fun PaperPanel(
  style: PageStyle,
  darkPage: Boolean,
  onStylePicked: (PageStyle) -> Unit,
  spacingMm: Float,
  onSpacing: (Float) -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
) {
  Panel(modifier, isPalm) {
    val presets = PageBackground.spacingPresetsMm(style)
    FlowRow(Modifier.selectableGroup().semantics { contentDescription = "Paper style" }) {
      for (option in PageStyle.entries) {
        Option(
          label = option.label,
          selected = option == style,
          onClick = { onStylePicked(option) },
          modifier = Modifier.size(width = 76.dp, height = 76.dp),
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PaperPreview(option, darkPage)
            Spacer(Modifier.height(6.dp))
            Text(option.label, color = PenLabColors.icon, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
          }
        }
      }
    }
    if (presets.isNotEmpty()) {
      // Spacing: how far apart the lines, dots or squares are (user request: "less dots per square cm").
      PanelDivider()
      val current = if (spacingMm > 0f) spacingMm else PageBackground.defaultSpacingMm(style)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Spacing", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { onSpacing((current - 0.5f).coerceAtLeast(2f)) }) { Text("−", color = PenLabColors.icon, style = MaterialTheme.typography.titleMedium) }
        Text("%.1f mm".format(current), color = PenLabColors.icon, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
        TextButton(onClick = { onSpacing((current + 0.5f).coerceAtMost(20f)) }) { Text("+", color = PenLabColors.icon, style = MaterialTheme.typography.titleMedium) }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (mm in presets) {
          val shape = RoundedCornerShape(10.dp)
          Box(
            Modifier.size(width = 56.dp, height = 40.dp)
              .clip(shape)
              .background(if (abs(current - mm) < 0.05f) PenLabColors.optionSelected else Color.Transparent, shape)
              .clickable { onSpacing(mm) }
              .semantics { contentDescription = "Spacing ${mm.toInt()} millimetres" },
            contentAlignment = Alignment.Center,
          ) {
            Text("${mm.toInt()} mm", color = PenLabColors.icon, style = MaterialTheme.typography.labelMedium)
          }
        }
      }
    }
  }
}

/**
 * A short message with one action ("Page cleared · Undo"), in the same shape as the toolbar so
 * it reads as the toolbar speaking, not as a system alert.
 */
@Composable
fun ToolbarMessage(message: String, actionLabel: String?, onAction: () -> Unit, modifier: Modifier = Modifier) {
  PenLabSnackbar(
    message = message,
    actionLabel = actionLabel,
    onAction = if (actionLabel != null) onAction else null,
    modifier = modifier,
  )
}

@Composable
private fun PanelDivider() {
  Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(PenLabColors.hairline))
}

/**
 * A short wavy stroke drawn the way each pen type behaves: even for the ballpoint, swelling in
 * the middle for the pressure pen, thick-and-thin by direction for the fountain pen.
 */
/** A short line drawn in [line]'s pattern, for the line type buttons and the tool cells. */
@Composable
internal fun LinePreview(line: LineType, paperColor: Int, darkPage: Boolean, modifier: Modifier = Modifier.size(width = 40.dp, height = 18.dp)) {
  val color = Color(InkColors.onScreen(paperColor, darkPage) or 0xFF000000.toInt())
  Canvas(modifier) {
    val d = 2.dp.toPx()
    val effect = line.pattern?.let { pattern ->
      // Zero-length "on" parts are dots: a round cap makes them visible.
      PathEffect.dashPathEffect(FloatArray(pattern.size) { i -> maxOf(pattern[i] * d * 0.55f, if (i % 2 == 0) 0.01f else d) })
    }
    drawLine(color, Offset(2.dp.toPx(), size.height / 2), Offset(size.width - 2.dp.toPx(), size.height / 2), strokeWidth = d, cap = StrokeCap.Round, pathEffect = effect)
  }
}

@Composable
private fun PenStylePreview(style: PenStyle, paperColor: Int, darkPage: Boolean) {
  val color = Color(InkColors.onScreen(paperColor, darkPage) or 0xFF000000.toInt())
  Canvas(Modifier.size(width = 44.dp, height = 26.dp)) {
    val steps = 28
    val base = 2.2.dp.toPx()
    var previous = Offset(4.dp.toPx(), size.height / 2)
    for (i in 1..steps) {
      val t = i / steps.toFloat()
      val point =
        Offset(
          4.dp.toPx() + t * (size.width - 8.dp.toPx()),
          size.height / 2 - sin(t * 2f * PI.toFloat()) * size.height * 0.3f,
        )
      val width =
        when (style) {
          PenStyle.BALLPOINT -> base
          PenStyle.PRESSURE -> base * (0.45f + 1.1f * sin(t * PI.toFloat()))
          PenStyle.FOUNTAIN -> {
            // Thick when the stroke runs across a nib held at 45 degrees, thin along it.
            val direction = atan2(point.y - previous.y, point.x - previous.x)
            base * (0.4f + 1.4f * abs(sin(direction + PI.toFloat() / 4f)))
          }
        }
      drawLine(color, previous, point, strokeWidth = width, cap = StrokeCap.Round)
      previous = point
    }
  }
}

/**
 * The buttons that float by a lasso selection. The selection itself is moved by dragging it
 * with the pen; these cover what dragging cannot.
 */
@Composable
fun SelectionActions(
  onCut: () -> Unit,
  onCopy: () -> Unit,
  onDuplicate: () -> Unit,
  onMirror: (sideways: Boolean) -> Unit,
  onDelete: () -> Unit,
  onDone: () -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
  /** Fill the inside of the selected closed shapes. */
  onFill: (() -> Unit)? = null,
  /** Buttons added by plugins (label, action); none with only the built-in plugins. */
  extra: List<Pair<String, () -> Unit>> = emptyList(),
) {
  val shape = RoundedCornerShape(14.dp)
  Row(
    modifier
      .floatingChromeSurface(
        shape = shape,
        background = PenLabComponentTokens.selectionBarBg,
        elevation = 8.dp,
        subtleLightHairline = true,
        borderColor = PenLabComponentTokens.selectionBarBorder,
        isDark = LocalPenLabColors.current.isDark,
      )
      .ignorePalm(isPalm)
      .padding(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    PillButton("Cut", onCut)
    PillButton("Copy", onCopy)
    PillButton("Duplicate", onDuplicate)
    if (onFill != null) PillButton("Fill", onFill)
    for ((label, action) in extra) PillButton(label, action)
    PillButton("Delete", onDelete)
    PillDivider()
    IconButton(onClick = { onMirror(true) }, modifier = Modifier.size(44.dp)) {
      Icon(MirrorIcon, contentDescription = "Mirror left to right", tint = PenLabColors.icon, modifier = Modifier.size(20.dp))
    }
    IconButton(onClick = { onMirror(false) }, modifier = Modifier.size(44.dp)) {
      Icon(
        MirrorIcon,
        contentDescription = "Mirror top to bottom",
        tint = PenLabColors.icon,
        modifier = Modifier.size(20.dp).rotate(90f),
      )
    }
    PillDivider()
    PillButton("Done", onDone)
  }
}

@Composable
private fun PillDivider() {
  Box(Modifier.size(width = 1.dp, height = 20.dp).background(PenLabColors.hairline))
}

/**
 * Opens where the pen was held still on the page, when something has been copied: Paste puts
 * it there, the x empties the clipboard.
 */
@Composable
fun PasteMenu(onPaste: () -> Unit, onClear: () -> Unit, isPalm: () -> Boolean, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(14.dp)
  Row(
    modifier
      .floatingChromeSurface(
        shape = shape,
        background = PenLabComponentTokens.selectionBarBg,
        elevation = 8.dp,
        subtleLightHairline = true,
        borderColor = PenLabComponentTokens.selectionBarBorder,
        isDark = LocalPenLabColors.current.isDark,
      )
      .ignorePalm(isPalm)
      .padding(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    PillButton("Paste", onPaste)
    Box(Modifier.size(width = 1.dp, height = 20.dp).background(PenLabColors.hairline))
    IconButton(onClick = onClear, modifier = Modifier.size(48.dp)) {
      Icon(CloseIcon, contentDescription = "Clear clipboard", tint = PenLabColors.icon, modifier = Modifier.size(18.dp))
    }
  }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
  TextButton(onClick = onClick) {
    Text(label, color = PenLabColors.icon, style = MaterialTheme.typography.labelLarge)
  }
}

/** Top-left on a notebook: back to the library, with the notebook's title. */
@Composable
fun BackToLibrary(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(12.dp)
  Row(
    modifier
      .softShadow(shape).clip(shape)
      // Bold accent: the open notebook's name carries the look's colour.
      .background(PenLabColors.accent)
      .clickable(onClick = onClick)
      .semantics { contentDescription = "Back to notebooks. Open notebook: $title" }
      .padding(start = 8.dp, end = 14.dp)
      .size(height = 36.dp, width = Dp.Unspecified),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(BackIcon, contentDescription = null, tint = PenLabColors.onAccent, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(4.dp))
    Text(title, color = PenLabColors.onAccent, style = MaterialTheme.typography.titleSmall, maxLines = 1)
  }
}

/**
 * Another open notebook, as a tab beside the open one's name: tap to switch, long press to close
 * the tab. Quieter than the current notebook's pill, with its folder's colour as a dot.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NotebookTab(title: String, folderColor: Int?, onClick: () -> Unit, onClose: () -> Unit) {
  val shape = RoundedCornerShape(12.dp)
  Row(
    Modifier.clip(shape)
      .background(PenLabColors.toolbar.copy(alpha = 0.75f))
      .border(1.dp, PenLabColors.hairline.copy(alpha = 0.6f), shape)
      .combinedClickable(onClick = onClick, onLongClick = onClose)
      .semantics { contentDescription = "Switch to $title. Long press to close this tab." }
      .padding(horizontal = 12.dp)
      .size(height = 36.dp, width = Dp.Unspecified)
      .widthIn(max = 180.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(8.dp).background(folderColor?.let { androidx.compose.ui.graphics.Color(it) } ?: PenLabColors.iconDisabled, CircleShape))
    Spacer(Modifier.width(8.dp))
    Text(title, color = PenLabColors.icon.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
  }
}

/** A small round-cornered button with just an icon, matching the pills along the top. */
@Composable
fun TopIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(12.dp)
  Box(
    modifier
      .floatingChromeSurface(
        shape = shape,
        background = PenLabComponentTokens.chromePillBg,
        elevation = 8.dp,
        subtleLightHairline = true,
        borderColor = PenLabComponentTokens.chromePillBorder,
        isDark = LocalPenLabColors.current.isDark,
      )
      .clickable(onClick = onClick)
      .size(width = 44.dp, height = 36.dp)
      .semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = null, tint = PenLabColors.icon, modifier = Modifier.size(20.dp))
  }
}

/** One entry of the top bar's "more" menu. */
class MenuAction(
  val icon: androidx.compose.ui.graphics.vector.ImageVector,
  val label: String,
  val onClick: () -> Unit,
  /** A drawable resource shown instead of [icon] (plugins bring their own); 0 = use [icon]. */
  val iconRes: Int = 0,
)

/** The three-dot button at the end of the top bar: the actions you don't need while writing. */
@Composable
fun TopMoreMenu(actions: List<MenuAction>, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
  // The open state lives in the screen so it counts as an overlay (a press outside only closes it).
  Box(modifier) {
    TopIconButton(MoreIcon, "More: save, share, present, photo, settings", onClick = { onExpandedChange(true) })
    androidx.compose.material3.DropdownMenu(
      expanded = expanded,
      onDismissRequest = { onExpandedChange(false) },
      containerColor = PenLabComponentTokens.panelBg,
    ) {
      for (a in actions) {
        androidx.compose.material3.DropdownMenuItem(
          text = { Text(a.label, color = PenLabColors.icon) },
          leadingIcon = {
            if (a.iconRes != 0) Icon(androidx.compose.ui.res.painterResource(a.iconRes), contentDescription = null, tint = PenLabColors.icon, modifier = Modifier.size(20.dp))
            else Icon(a.icon, contentDescription = null, tint = PenLabColors.icon, modifier = Modifier.size(20.dp))
          },
          onClick = { onExpandedChange(false); a.onClick() },
        )
      }
    }
  }
}

/** Next to the page counter: share this notebook as a PDF (WhatsApp, Drive, mail, ...). */
@Composable
fun ShareButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(12.dp)
  Box(
    modifier
      .softShadow(shape).clip(shape)
      .background(PenLabColors.toolbar)
      .clickable(onClick = onClick)
      .size(width = 44.dp, height = 36.dp)
      .semantics { contentDescription = "Share as PDF" },
    contentAlignment = Alignment.Center,
  ) {
    Icon(ExportIcon, contentDescription = null, tint = PenLabColors.icon, modifier = Modifier.size(20.dp))
  }
}

/** Next to the page counter: save this notebook as a PDF (to Drive or any folder). */
@Composable
fun ExportButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(12.dp)
  Box(
    modifier
      .softShadow(shape).clip(shape)
      .background(PenLabColors.toolbar)
      .clickable(onClick = onClick)
      .size(width = 44.dp, height = 36.dp)
      .semantics { contentDescription = "Save as PDF" },
    contentAlignment = Alignment.Center,
  ) {
    Icon(SaveIcon, contentDescription = null, tint = PenLabColors.icon, modifier = Modifier.size(20.dp))
  }
}

/** The shared look of a panel: same surface and hairline as the toolbar it belongs to. */
@Composable
private fun Panel(modifier: Modifier, isPalm: () -> Boolean, content: @Composable () -> Unit) {
  PenLabPopoverSurface(
    isPalm = isPalm,
    modifier = modifier.width(PanelCell * 5 + 20.dp),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
    verticalArrangement = Arrangement.Top,
  ) {
    content()
  }
}

/** One choice in a panel. The chosen one gets a soft background. */
@Composable
private fun Option(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val shape = RoundedCornerShape(12.dp)
  // The whole cell is the touch target; the soft background is inset 2 dp so neighbours
  // do not merge.
  Box(
    modifier
      .clip(shape)
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
      .semantics { contentDescription = label }
      .padding(2.dp)
      .background(if (selected) PenLabColors.optionSelected else Color.Transparent, shape),
    contentAlignment = Alignment.Center,
  ) {
    content()
  }
}

/** Shows a size the way it will look: a stroke at true width, or the eraser's circle. */
@Composable
private fun SizePreview(tool: InkTool, sizeDp: Float, darkPage: Boolean) {
  val color =
    if (tool.isEraser) PenLabColors.icon
    else Color(InkColors.onScreen(tool.paperColor, darkPage) or 0xFF000000.toInt())
  Canvas(Modifier.size(width = 40.dp, height = 40.dp)) {
    if (tool.isEraser) {
      // Radii up to 24 dp do not fit a 40 dp cell; show them at 60% so the steps still compare.
      drawCircle(color, radius = sizeDp.dp.toPx() * 0.6f, style = Stroke(1.5.dp.toPx()))
    } else {
      val y = size.height / 2
      drawLine(
        color,
        Offset(8.dp.toPx(), y),
        Offset(size.width - 8.dp.toPx(), y),
        strokeWidth = sizeDp.dp.toPx(),
        cap = if (tool.isHighlighter) StrokeCap.Butt else StrokeCap.Round,
      )
    }
  }
}

/** A thumbnail of a sheet with its ruling, in the page's on-screen colours. */
@Composable
private fun PaperPreview(style: PageStyle, darkPage: Boolean) {
  val paper = Color(InkColors.pageOnScreen(darkPage))
  val ruling = Color(InkColors.onScreen(PageBackground.RULING_PAPER_COLOR, darkPage))
  val shape = RoundedCornerShape(5.dp)
  Canvas(
    Modifier.size(width = 44.dp, height = 34.dp)
      .clip(shape)
      .background(paper)
      .border(1.dp, PenLabColors.swatchEdge, shape)
  ) {
    val step = size.height / 4
    when (style) {
      PageStyle.BLANK -> Unit
      PageStyle.LINED ->
        for (i in 1..3) {
          drawLine(ruling, Offset(0f, step * i), Offset(size.width, step * i), strokeWidth = 1.dp.toPx())
        }
      PageStyle.DOTTED ->
        for (row in 1..3) {
          for (column in 1..4) {
            drawCircle(ruling, radius = 1.1.dp.toPx(), center = Offset(size.width / 5 * column, step * row))
          }
        }
      PageStyle.CORNELL -> {
        for (i in 1..3) drawLine(ruling.copy(alpha = 0.5f), Offset(0f, step * i), Offset(size.width, step * i), strokeWidth = 0.6f)
        val w = 1.2.dp.toPx()
        drawLine(ruling, Offset(0f, step * 0.8f), Offset(size.width, step * 0.8f), strokeWidth = w)
        drawLine(ruling, Offset(size.width * 0.3f, step * 0.8f), Offset(size.width * 0.3f, step * 3.2f), strokeWidth = w)
        drawLine(ruling, Offset(0f, step * 3.2f), Offset(size.width, step * 3.2f), strokeWidth = w)
      }
      PageStyle.CP -> {
        val cell = 5.5.dp.toPx()
        var x = 0f
        while (x <= size.width) { drawLine(ruling.copy(alpha = 0.45f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.6f); x += cell }
        var y = 0f
        while (y <= size.height) { drawLine(ruling.copy(alpha = 0.45f), Offset(0f, y), Offset(size.width, y), strokeWidth = 0.6f); y += cell }
        val w = 1.2.dp.toPx()
        drawLine(ruling, Offset(0f, step * 0.7f), Offset(size.width, step * 0.7f), strokeWidth = w)
        drawLine(ruling, Offset(size.width * 0.18f, step * 0.7f), Offset(size.width * 0.18f, size.height), strokeWidth = w)
      }
      PageStyle.ISOMETRIC -> {
        val gap = 7.dp.toPx()
        var x = 0f
        while (x <= size.width) { drawLine(ruling.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.6f); x += gap }
        var c = -size.width
        while (c <= size.height + size.width) {
          drawLine(ruling.copy(alpha = 0.6f), Offset(0f, c), Offset(size.width, c + size.width * 0.577f), strokeWidth = 0.6f)
          drawLine(ruling.copy(alpha = 0.6f), Offset(0f, c), Offset(size.width, c - size.width * 0.577f), strokeWidth = 0.6f)
          c += gap * 1.155f
        }
      }
      PageStyle.GRID, PageStyle.ENGINEERING, PageStyle.GRAPH -> {
        val cell = if (style == PageStyle.GRAPH) 3.dp.toPx() else 5.5.dp.toPx()
        val heavyEvery = when (style) { PageStyle.ENGINEERING -> 4; PageStyle.GRAPH -> 5; else -> 1 }
        var i = 0
        var x = 0f
        while (x <= size.width) {
          val heavy = i % heavyEvery == 0
          drawLine(if (heavy) ruling else ruling.copy(alpha = 0.45f), Offset(x, 0f), Offset(x, size.height), strokeWidth = if (heavy) 1.dp.toPx() else 0.6f)
          x += cell; i++
        }
        i = 0
        var y = 0f
        while (y <= size.height) {
          val heavy = i % heavyEvery == 0
          drawLine(if (heavy) ruling else ruling.copy(alpha = 0.45f), Offset(0f, y), Offset(size.width, y), strokeWidth = if (heavy) 1.dp.toPx() else 0.6f)
          y += cell; i++
        }
      }
    }
  }
}

private val PanelCell = 48.dp

/**
 * The layers, top layer first (as they stack). Tap a layer to draw on it; the eye shows or hides
 * it; long-press to rename; the bin deletes it with its ink (undo brings it back).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LayersPanel(
  names: List<String>,
  hidden: Set<Int>,
  current: Int,
  onSelect: (Int) -> Unit,
  onVisible: (Int, Boolean) -> Unit,
  onRename: (Int) -> Unit,
  onDelete: (Int) -> Unit,
  onAdd: () -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
) {
  Panel(modifier, isPalm) {
    Text("Layers", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
    for (index in names.indices.reversed()) {
      val shape = RoundedCornerShape(12.dp)
      val isCurrent = index == current
      Row(
        Modifier.fillMaxWidth()
          .height(PanelCell)
          .clip(shape)
          .background(if (isCurrent) PenLabColors.optionSelected else Color.Transparent, shape)
          .combinedClickable(onClick = { onSelect(index) }, onLongClick = { onRename(index) })
          .semantics { contentDescription = "${names[index]}${if (isCurrent) ", drawing here" else ""}. Long press to rename." },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val shown = index !in hidden
        IconButton(onClick = { onVisible(index, !shown) }) {
          Icon(if (shown) EyeIcon else EyeOffIcon, contentDescription = if (shown) "Hide ${names[index]}" else "Show ${names[index]}", tint = if (shown) PenLabColors.icon else PenLabColors.iconDisabled, modifier = Modifier.size(20.dp))
        }
        Text(
          names[index],
          color = if (shown) PenLabColors.icon else PenLabColors.iconDisabled,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
          modifier = Modifier.weight(1f),
        )
        if (names.size > 1) {
          IconButton(onClick = { onDelete(index) }) {
            Icon(DeleteIcon, contentDescription = "Delete ${names[index]} and its ink", tint = PenLabColors.iconDisabled, modifier = Modifier.size(18.dp))
          }
        }
      }
    }
    PanelDivider()
    Box(
      Modifier.fillMaxWidth().height(PanelCell).clip(RoundedCornerShape(12.dp)).clickable(onClick = onAdd),
      contentAlignment = Alignment.Center,
    ) {
      Text("+ New layer", color = PenLabColors.icon, style = MaterialTheme.typography.labelLarge)
    }
  }
}
