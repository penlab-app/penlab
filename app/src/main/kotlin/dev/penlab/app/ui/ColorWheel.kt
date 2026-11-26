package dev.penlab.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.penlab.app.ink.InkColors
import dev.penlab.app.ui.components.PenLabChromePill
import dev.penlab.app.ui.components.PenLabIconButton
import dev.penlab.app.ui.components.PenLabSegmentedControl
import dev.penlab.app.ui.theme.LocalPenLabColors
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.PenLabShapes
import dev.penlab.app.ui.theme.PenLabSpacing
import dev.penlab.app.ui.theme.PenLabTextStyles
import dev.penlab.app.ui.theme.softShadow
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class ColorPickerMode(val label: String) {
  MARKERS("Markers"),
  HSL("HSL"),
  RGB("RGB"),
}

/** Formats an ARGB integer as an uppercase `#RRGGBB` string. */
internal fun formatHexRgb(argb: Int): String =
  String.format(Locale.US, "#%06X", argb and 0xFFFFFF)

/** Computes the HSL/RGB wheel diameter to fit cleanly inside the available safe vertical zone. */
internal fun computeColorWheelDiameter(availableWidth: Dp, availableHeight: Dp): Dp =
  minOf(availableWidth, availableHeight, MaxWheelDiameter).coerceAtLeast(0.dp)

/**
 * The colour wheel (a wheel, as users asked for). Three ways to pick:
 *  - Markers: the big spinning marker wheel fanned out from the left edge (or corner wheel hub).
 *  - HSL: a hue ring round a square of saturation (across) and lightness (down). Drag to pick.
 *  - RGB: three sliders, for matching an exact colour.
 *
 * The floating bottom pill switches modes, displays the active swatch + tabular `#RRGGBB` code,
 * offers recent colours, the Phosphor Duotone Eyedropper, and a primary Done button.
 *
 * Colours are stored as paper colours (as on white paper); on the dark page they show inverted
 * like ink. Highlighters preserve their translucent alpha whatever colour is picked.
 */
@Composable
fun ColorWheel(
  initial: Int,
  darkPage: Boolean,
  recent: List<Int>,
  /** A colour was chosen (with the tool's see-through amount); [done] closes the wheel. */
  onPicked: (color: Int, done: Boolean) -> Unit,
  onEyedropper: () -> Unit,
  onDismiss: () -> Unit,
  /** The tool wheel's hub when it is in a corner: the colours fan out from there. */
  hub: Offset? = null,
) {
  val scheme = MaterialTheme.colorScheme
  val swatchEdge = LocalPenLabColors.current.swatchEdge
  val alpha = initial ushr 24

  // The wheel works in screen colours, so what you tap is exactly what the ink will look like;
  // on the dark page the stored (paper) colour is its lightness-inverted twin.
  fun toScreen(paper: Int) =
    if (darkPage) InkColors.invertLightness(paper or 0xFF000000.toInt())
    else paper or 0xFF000000.toInt()
  fun toPaper(screen: Int) = toScreen(screen) // inverting twice gives the colour back

  var color by remember { mutableIntStateOf(toScreen(initial)) }
  var mode by remember { mutableStateOf(ColorPickerMode.MARKERS) }
  val grow = remember { Animatable(0.85f) }
  LaunchedEffect(Unit) { grow.animateTo(1f, Motion.spring(dampingRatio = 1f, stiffness = 300f)) }

  fun pick(c: Int, close: Boolean) {
    color = c or 0xFF000000.toInt()
    val withAlpha = (toPaper(c) and 0x00FFFFFF) or (alpha shl 24)
    onPicked(withAlpha, close)
  }
  fun screen(c: Int) = Color(c or 0xFF000000.toInt())

  Box(
    Modifier
      .fillMaxSize()
      .background(scheme.scrim)
      .pointerInput(Unit) { detectTapGestures { onDismiss() } },
  ) {
    if (mode == ColorPickerMode.MARKERS) {
      // Markers: the big spinning wheel at the left edge (taps on it never close the picker).
      MarkerWheel(color, { pick(it, close = true) }, onDismiss, Modifier.fillMaxSize(), hub = hub)
    }

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
        Modifier
          .fillMaxSize()
          .statusBarsPadding()
          .navigationBarsPadding()
          .padding(bottom = PenLabSpacing.lg),
    ) {
      // Safe vertical picker zone between the top floating toolbar and the fixed bottom pill.
      BoxWithConstraints(
        modifier =
          Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(top = TopChromeClearance, bottom = PenLabSpacing.lg, start = PenLabSpacing.xl, end = PenLabSpacing.xl),
        contentAlignment = Alignment.Center,
      ) {
        if (mode != ColorPickerMode.MARKERS) {
          val diameter = computeColorWheelDiameter(maxWidth, maxHeight)
          Box(
            Modifier
              .size(diameter)
              .graphicsLayer {
                scaleX = grow.value
                scaleY = grow.value
                rotationZ = (1f - grow.value) * -60f
                this.alpha = ((grow.value - 0.85f) / 0.15f).coerceIn(0f, 1f)
              }
              .pointerInput(Unit) { detectTapGestures { } }, // taps inside never close it
            contentAlignment = Alignment.Center,
          ) {
            when (mode) {
              ColorPickerMode.HSL -> HslWheel(color, ::screen, diameter) { pick(it, close = false) }
              else -> RgbPicker(color, screen(color), diameter) { pick(it, close = false) }
            }
          }
        }
      }

      // Floating bottom control pill: anchored identically across Markers, HSL, and RGB modes.
      PenLabChromePill(
        modifier =
          Modifier
            .padding(horizontal = PenLabSpacing.lg)
            .pointerInput(Unit) { detectTapGestures { } }
            .horizontalScroll(rememberScrollState()),
        elevation = 14.dp,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        PenLabSegmentedControl(
          options = ColorPickerMode.entries,
          selected = mode,
          onSelect = { mode = it },
          label = { it.label },
          modifier = Modifier.width(248.dp),
        )

        Box(Modifier.size(width = 1.dp, height = 24.dp).background(scheme.outlineVariant))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
        ) {
          Box(
            Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(screen(color))
              .border(1.dp, swatchEdge, CircleShape)
              .semantics { contentDescription = "Active colour ${formatHexRgb(color)}" },
          )
          Text(
            text = formatHexRgb(color),
            color = scheme.onSurface,
            style = PenLabTextStyles.tabularLabel,
          )
        }

        if (recent.isNotEmpty()) {
          Box(Modifier.size(width = 1.dp, height = 24.dp).background(scheme.outlineVariant))
          Row(
            horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            for (c in recent.take(8)) {
              val recentScreen = toScreen(c)
              val isCurrent = recentScreen == color
              Box(
                modifier =
                  Modifier
                    .size(PenLabSpacing.touchTarget)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { pick(recentScreen, close = true) }
                    .semantics { contentDescription = "Recent colour ${formatHexRgb(recentScreen)}" },
                contentAlignment = Alignment.Center,
              ) {
                Box(
                  modifier =
                    Modifier
                      .size(32.dp)
                      .then(
                        if (isCurrent) Modifier.border(2.dp, scheme.primary, CircleShape)
                        else Modifier
                      ),
                  contentAlignment = Alignment.Center,
                ) {
                  Box(
                    Modifier
                      .size(26.dp)
                      .clip(CircleShape)
                      .background(screen(recentScreen))
                      .border(1.dp, swatchEdge, CircleShape),
                  )
                }
              }
            }
          }
        }

        Box(Modifier.size(width = 1.dp, height = 24.dp).background(scheme.outlineVariant))

        PenLabIconButton(
          icon = PenLabIcons.Eyedropper,
          contentDescription = "Eyedropper: pick a colour from the page",
          onClick = onEyedropper,
        )

        Button(
          onClick = onDismiss,
          shape = PenLabShapes.pill,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = scheme.primary,
              contentColor = scheme.onPrimary,
            ),
          modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
          contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        ) {
          Text(
            text = "Done",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
          )
        }
      }
    }
  }
}

// ---- HSL -----------------------------------------------------------------------------------

@Composable
private fun HslWheel(selected: Int, screen: (Int) -> Color, diameter: Dp, onPick: (Int) -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val ringOuter = scheme.inverseSurface
  val ringInner = scheme.inverseOnSurface
  val ringWidth = (diameter * 0.085f).coerceIn(28.dp, 38.dp)
  val start = remember { toHsl(selected) }
  var hue by remember { mutableFloatStateOf(start[0]) }
  var sat by remember { mutableFloatStateOf(start[1]) }
  var light by remember { mutableFloatStateOf(start[2]) }
  var dragging by remember { mutableIntStateOf(0) } // 1 ring, 2 square

  fun update(p: Offset, w: Float, ringIn: Float, half: Float, startDrag: Boolean) {
    val c = Offset(w / 2f, w / 2f)
    val r = hypot(p.x - c.x, p.y - c.y)
    if (startDrag) {
      dragging =
        when {
          r >= ringIn -> 1
          kotlin.math.abs(p.x - c.x) <= half && kotlin.math.abs(p.y - c.y) <= half -> 2
          else -> 0
        }
    }
    when (dragging) {
      1 -> {
        var a = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat() + 90f
        if (a < 0) a += 360f
        hue = a
      }
      2 -> {
        sat = ((p.x - (c.x - half)) / (2 * half)).coerceIn(0f, 1f)
        light = 1f - ((p.y - (c.y - half)) / (2 * half)).coerceIn(0f, 1f)
      }
    }
    if (dragging != 0) onPick(hsl(hue, sat, light))
  }

  Canvas(
    Modifier
      .fillMaxSize()
      .pointerInput(ringWidth) {
        detectDragGestures(
          onDragStart = { p ->
            val ringIn = size.width / 2f - ringWidth.toPx()
            val half = (ringIn - 14.dp.toPx()) / 1.4142f
            update(p, size.width.toFloat(), ringIn, half, true)
          },
        ) { change, _ ->
          val ringIn = size.width / 2f - ringWidth.toPx()
          val half = (ringIn - 14.dp.toPx()) / 1.4142f
          update(change.position, size.width.toFloat(), ringIn, half, false)
        }
      }
      .pointerInput(ringWidth) {
        detectTapGestures { p ->
          val ringIn = size.width / 2f - ringWidth.toPx()
          val half = (ringIn - 14.dp.toPx()) / 1.4142f
          update(p, size.width.toFloat(), ringIn, half, true)
        }
      }
  ) {
    val outer = size.width / 2f
    val ringIn = outer - ringWidth.toPx()
    val hues = List(13) { screen(hsl(it * 30f, 1f, 0.5f)) }
    // Sweep gradients start at 3 o'clock; the wheel's 0° is at 12, so rotate the stops.
    rotateCompat(-90f) {
      drawCircle(Brush.sweepGradient(hues, center), radius = (outer + ringIn) / 2f, style = Stroke(outer - ringIn))
    }
    val a = Math.toRadians((hue - 90f).toDouble())
    val mid = (outer + ringIn) / 2f
    val ringMarker = Offset(center.x + mid * cos(a).toFloat(), center.y + mid * sin(a).toFloat())
    drawCircle(ringOuter, 10.dp.toPx(), ringMarker, style = Stroke(4.dp.toPx()))
    drawCircle(ringInner, 10.dp.toPx(), ringMarker, style = Stroke(2.dp.toPx()))

    // Saturation across, lightness down, for the chosen hue.
    val half = (ringIn - 14.dp.toPx()) / 1.4142f
    val topLeft = Offset(center.x - half, center.y - half)
    val cells = 44 // fine enough that no banding shows
    val cell = 2f * half / cells
    for (i in 0 until cells) {
      for (j in 0 until cells) {
        drawRect(
          color = screen(hsl(hue, (i + 0.5f) / cells, 1f - (j + 0.5f) / cells)),
          topLeft = Offset(topLeft.x + i * cell, topLeft.y + j * cell),
          size = Size(cell + 0.6f, cell + 0.6f),
        )
      }
    }
    val sqMarker = Offset(topLeft.x + sat * 2f * half, topLeft.y + (1f - light) * 2f * half)
    drawCircle(ringOuter, 9.dp.toPx(), sqMarker, style = Stroke(4.dp.toPx()))
    drawCircle(ringInner, 9.dp.toPx(), sqMarker, style = Stroke(2.dp.toPx()))
  }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.rotateCompat(
  degrees: Float,
  block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
  drawContext.transform.rotate(degrees, center)
  block()
  drawContext.transform.rotate(-degrees, center)
}

// ---- RGB -----------------------------------------------------------------------------------

@Composable
private fun RgbPicker(selected: Int, preview: Color, diameter: Dp, onPick: (Int) -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val swatchEdge = LocalPenLabColors.current.swatchEdge
  var r by remember { mutableFloatStateOf(((selected shr 16) and 0xFF).toFloat()) }
  var g by remember { mutableFloatStateOf(((selected shr 8) and 0xFF).toFloat()) }
  var b by remember { mutableFloatStateOf((selected and 0xFF).toFloat()) }
  fun emit() = onPick((0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt())

  // Keep inner controls comfortably inside the circular chord even at compact diameters.
  val contentWidth = (diameter * 0.68f).coerceAtMost(308.dp)
  val swatchSize = (diameter * 0.20f).coerceIn(48.dp, 88.dp)
  val rowSpacing = if (diameter >= 380.dp) 4.dp else 0.dp

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .softShadow(CircleShape, elevation = 16.dp)
        .clip(CircleShape)
        .background(PenLabComponentTokens.panelBg)
        .border(1.dp, scheme.outlineVariant, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.width(contentWidth),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(rowSpacing),
    ) {
      Box(
        Modifier
          .size(swatchSize)
          .clip(CircleShape)
          .background(preview)
          .border(1.5.dp, swatchEdge, CircleShape),
      )
      Text(
        text = formatHexRgb(selected),
        color = scheme.onSurface,
        style = PenLabTextStyles.tabularLabel,
      )
      if (diameter >= 380.dp) Spacer(Modifier.height(PenLabSpacing.xs))
      for ((label, value, set) in listOf(
        Triple("R", r) { v: Float -> r = v },
        Triple("G", g) { v: Float -> g = v },
        Triple("B", b) { v: Float -> b = v },
      )) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = label,
            color = scheme.onSurface,
            style = PenLabTextStyles.tabularLabel,
            modifier = Modifier.width(20.dp),
          )
          Slider(
            value = value,
            onValueChange = {
              set(it.roundToInt().toFloat())
              emit()
            },
            valueRange = 0f..255f,
            colors =
              SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                inactiveTrackColor = scheme.outlineVariant,
              ),
            modifier = Modifier.weight(1f).semantics { contentDescription = "$label channel" },
          )
          Text(
            text = "${value.toInt()}",
            color = scheme.onSurfaceVariant,
            style = PenLabTextStyles.tabularLabel,
            textAlign = TextAlign.End,
            modifier = Modifier.width(38.dp),
          )
        }
      }
    }
  }
}

// ---- Colour maths ----------------------------------------------------------------------------

/** HSL (hue in degrees, saturation and lightness 0..1) to opaque ARGB. */
internal fun hsl(h: Float, s: Float, l: Float): Int {
  val c = (1f - kotlin.math.abs(2 * l - 1f)) * s
  val hp = ((h % 360f + 360f) % 360f) / 60f
  val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
  val (r1, g1, b1) =
    when {
      hp < 1 -> Triple(c, x, 0f)
      hp < 2 -> Triple(x, c, 0f)
      hp < 3 -> Triple(0f, c, x)
      hp < 4 -> Triple(0f, x, c)
      hp < 5 -> Triple(x, 0f, c)
      else -> Triple(c, 0f, x)
    }
  val m = l - c / 2
  fun ch(v: Float) = ((v + m) * 255f).toInt().coerceIn(0, 255)
  return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
}

internal fun toHsl(argb: Int): FloatArray {
  val r = ((argb shr 16) and 0xFF) / 255f
  val g = ((argb shr 8) and 0xFF) / 255f
  val b = (argb and 0xFF) / 255f
  val max = maxOf(r, g, b)
  val min = minOf(r, g, b)
  val l = (max + min) / 2
  val d = max - min
  if (d == 0f) return floatArrayOf(0f, 0f, l)
  val s = d / (1f - kotlin.math.abs(2 * l - 1f))
  val h =
    when (max) {
      r -> 60f * (((g - b) / d) % 6f)
      g -> 60f * ((b - r) / d + 2f)
      else -> 60f * ((r - g) / d + 4f)
    }
  return floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), l)
}

private val MaxWheelDiameter = 440.dp
private val TopChromeClearance = 80.dp
