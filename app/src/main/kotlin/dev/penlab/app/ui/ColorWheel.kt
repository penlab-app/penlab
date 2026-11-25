package dev.penlab.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.penlab.app.ink.InkColors
import dev.penlab.app.ui.theme.PenLabColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The colour wheel (a wheel, as users asked for). Three ways to pick:
 *  - Swatches: a ring of greys round the middle, then 24 hues as spokes, each going from deep
 *    (inside) to pale (outside). One tap picks.
 *  - HSL: a hue ring round a square of saturation (across) and lightness (down). Drag to pick.
 *  - RGB: three sliders, for matching an exact colour.
 * The middle shows the colour, its hex code, recently used colours and the eyedropper, which
 * closes the wheel so the next touch on the page picks up the colour of the ink there.
 *
 * Colours are paper colours (as on white paper); on the dark page they show inverted like ink.
 * A highlighter keeps its see-through amount whatever colour is picked.
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
  hub: androidx.compose.ui.geometry.Offset? = null,
) {
  val alpha = initial ushr 24
  // The wheel works in screen colours, so what you tap is exactly what the ink will look like;
  // on the dark page the stored (paper) colour is its lightness-inverted twin.
  fun toScreen(paper: Int) = if (darkPage) InkColors.invertLightness(paper or 0xFF000000.toInt()) else paper or 0xFF000000.toInt()
  fun toPaper(screen: Int) = toScreen(screen) // inverting twice gives the colour back
  var color by remember { mutableIntStateOf(toScreen(initial)) }
  var mode by remember { mutableIntStateOf(0) } // 0 swatches, 1 HSL, 2 RGB
  val grow = remember { Animatable(0.85f) }
  LaunchedEffect(Unit) { grow.animateTo(1f, Motion.spring(dampingRatio = 1f, stiffness = 300f)) }
  fun pick(c: Int, close: Boolean) {
    color = c or 0xFF000000.toInt()
    val withAlpha = (toPaper(c) and 0x00FFFFFF) or (alpha shl 24)
    onPicked(withAlpha, close)
  }
  fun screen(c: Int) = Color(c or 0xFF000000.toInt())

  Box(
    Modifier.fillMaxSize()
      .background(MaterialTheme.colorScheme.scrim)
      .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    contentAlignment = Alignment.Center,
  ) {
    if (mode == 0) {
      // Markers: the big spinning wheel at the left edge (taps on it never close the picker).
      MarkerWheel(color, { pick(it, close = true) }, onDismiss, Modifier.fillMaxSize(), hub = hub)
    }
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = if (mode == 0) Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp) else Modifier,
    ) {
      if (mode != 0) Box(
        Modifier.size(WheelDiameter)
          .graphicsLayer {
            scaleX = grow.value
            scaleY = grow.value
            rotationZ = (1f - grow.value) * -60f // turns the last few degrees into place, no overshoot
            this.alpha = ((grow.value - 0.85f) / 0.15f).coerceIn(0f, 1f)
          }
          .pointerInput(Unit) { detectTapGestures { } }, // taps inside never close it
        contentAlignment = Alignment.Center,
      ) {
        when (mode) {
          1 -> HslWheel(color, ::screen) { pick(it, close = false) }
          else -> RgbPicker(color, screen(color)) { pick(it, close = false) }
        }
      }
      if (mode != 0) Spacer(Modifier.height(14.dp))
      // Below the wheel: modes, code, recent colours, eyedropper, done.
      Row(
        Modifier.clip(RoundedCornerShape(26.dp)).background(PenLabColors.toolbar).border(1.dp, PenLabColors.hairline, RoundedCornerShape(26.dp))
          .pointerInput(Unit) { detectTapGestures { } }
          .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
          for ((i, label) in listOf("Markers", "HSL", "RGB").withIndex()) {
            val selected = mode == i
            Box(
              Modifier.clip(RoundedCornerShape(10.dp))
                .background(if (selected) PenLabColors.icon else Color.Transparent)
                .clickable { mode = i }
                .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
              Text(label, color = if (selected) PenLabColors.toolbar else PenLabColors.icon, style = MaterialTheme.typography.labelLarge)
            }
          }
        }
        Box(Modifier.size(28.dp).background(screen(color), CircleShape).border(1.dp, PenLabColors.swatchEdge, CircleShape))
        Text("#%06X".format(color and 0xFFFFFF), color = PenLabColors.iconDisabled, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
        if (recent.isNotEmpty()) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in recent.take(8)) {
              Box(
                Modifier.size(24.dp).clip(CircleShape).background(screen(toScreen(c))).border(1.dp, PenLabColors.swatchEdge, CircleShape)
                  .clickable { pick(toScreen(c), close = true) }
                  .semantics { contentDescription = "Recent colour" }
              )
            }
          }
        }
        Box(
          Modifier.size(40.dp).clip(CircleShape).background(PenLabColors.chip).clickable(onClick = onEyedropper)
            .semantics { contentDescription = "Eyedropper: pick a colour from the page" },
          contentAlignment = Alignment.Center,
        ) { Icon(EyedropperIcon, contentDescription = null, tint = PenLabColors.icon, modifier = Modifier.size(20.dp)) }
        if (mode != 0) {
          Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(PenLabColors.icon)
              .clickable { onDismiss() }
              .padding(horizontal = 16.dp, vertical = 8.dp)
          ) { Text("Done", color = PenLabColors.toolbar, style = MaterialTheme.typography.labelLarge) }
        }
      }
    }
  }
}

// ---- Swatches ------------------------------------------------------------------------------

private const val HUES = 24
private const val SHADES = 6
private const val GREYS = 12

/** The spoke colours: hue [h] (0..1), shade 0 = deep and saturated, last = pale. */
private fun swatch(h: Int, shade: Int): Int {
  val t = shade / (SHADES - 1f)
  return hsl(h * 360f / HUES, 0.88f - 0.28f * t, 0.30f + 0.52f * t)
}

private fun grey(i: Int): Int {
  val v = (255 * (1f - i / (GREYS - 1f))).toInt()
  return (0xFF shl 24) or (v shl 16) or (v shl 8) or v
}

@Composable
private fun SwatchWheel(selected: Int, screen: (Int) -> Color, onPick: (Int) -> Unit) {
  Canvas(
    Modifier.fillMaxSize().pointerInput(Unit) {
      detectTapGestures { p ->
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = hypot(p.x - c.x, p.y - c.y)
        var angle = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat() + 90f
        if (angle < 0) angle += 360f
        val greyIn = GreyInner.toPx()
        val greyOut = GreyOuter.toPx()
        val spokeIn = SpokeInner.toPx()
        val spokeOut = size.width / 2f
        when {
          r in greyIn..greyOut -> onPick(grey((angle / (360f / GREYS)).toInt().coerceIn(0, GREYS - 1)))
          r in spokeIn..spokeOut -> {
            val h = ((angle + 180f / HUES) / (360f / HUES)).toInt() % HUES
            val shade = ((r - spokeIn) / ((spokeOut - spokeIn) / SHADES)).toInt().coerceIn(0, SHADES - 1)
            onPick(swatch(h, shade))
          }
        }
      }
    }
  ) {
    val c = center
    val gap = 1.5.dp.toPx()
    fun segment(start: Float, sweep: Float, inner: Float, outer: Float, color: Color, outline: Boolean) {
      val path = Path().apply {
        arcTo(androidx.compose.ui.geometry.Rect(c, outer), start, sweep, forceMoveTo = true)
        arcTo(androidx.compose.ui.geometry.Rect(c, inner), start + sweep, -sweep, forceMoveTo = false)
        close()
      }
      drawPath(path, color)
      if (outline) drawPath(path, Color.White, style = Stroke(2.5.dp.toPx()))
    }
    // Greys, white at the top going round to black.
    val greyStep = 360f / GREYS
    for (i in 0 until GREYS) {
      val g = grey(i)
      segment(-90f + i * greyStep + 0.6f, greyStep - 1.2f, GreyInner.toPx(), GreyOuter.toPx(), screen(g), g == selected)
    }
    // Hue spokes; each shade is one ring segment, a hairline apart.
    val step = 360f / HUES
    val inner = SpokeInner.toPx()
    val outer = size.width / 2f
    val ring = (outer - inner) / SHADES
    for (h in 0 until HUES) {
      for (s in 0 until SHADES) {
        val col = swatch(h, s)
        segment(-90f - step / 2 + h * step + 0.7f, step - 1.4f, inner + s * ring + gap / 2, inner + (s + 1) * ring - gap / 2, screen(col), col == selected)
      }
    }
  }
}

// ---- HSL -----------------------------------------------------------------------------------

@Composable
private fun HslWheel(selected: Int, screen: (Int) -> Color, onPick: (Int) -> Unit) {
  val start = remember { toHsl(selected) }
  var hue by remember { mutableFloatStateOf(start[0]) }
  var sat by remember { mutableFloatStateOf(start[1]) }
  var light by remember { mutableFloatStateOf(start[2]) }
  var dragging by remember { mutableIntStateOf(0) } // 1 ring, 2 square
  fun update(p: Offset, w: Float, ringIn: Float, half: Float, startDrag: Boolean) {
    val c = Offset(w / 2f, w / 2f)
    val r = hypot(p.x - c.x, p.y - c.y)
    if (startDrag) dragging = if (r >= ringIn) 1 else if (kotlin.math.abs(p.x - c.x) <= half && kotlin.math.abs(p.y - c.y) <= half) 2 else 0
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
    Modifier.fillMaxSize()
      .pointerInput(Unit) {
        val ringIn = size.width / 2f - HslRing.toPx()
        val half = (ringIn - 14.dp.toPx()) / 1.4142f
        detectDragGestures(onDragStart = { update(it, size.width.toFloat(), ringIn, half, true) }) { change, _ ->
          update(change.position, size.width.toFloat(), ringIn, half, false)
        }
      }
      .pointerInput(Unit) {
        val ringIn = size.width / 2f - HslRing.toPx()
        val half = (ringIn - 14.dp.toPx()) / 1.4142f
        detectTapGestures { update(it, size.width.toFloat(), ringIn, half, true) }
      }
  ) {
    val outer = size.width / 2f
    val ringIn = outer - HslRing.toPx()
    val hues = List(13) { screen(hsl(it * 30f, 1f, 0.5f)) }
    // Sweep gradients start at 3 o'clock; the wheel's 0° is at 12, so rotate the stops.
    rotateCompat(-90f) {
      drawCircle(Brush.sweepGradient(hues, center), radius = (outer + ringIn) / 2, style = Stroke(outer - ringIn))
    }
    val a = Math.toRadians((hue - 90f).toDouble())
    val mid = (outer + ringIn) / 2
    drawCircle(Color.White, 9.dp.toPx(), Offset(center.x + mid * cos(a).toFloat(), center.y + mid * sin(a).toFloat()), style = Stroke(3.dp.toPx()))
    // Saturation across, lightness down, for the chosen hue.
    val half = (ringIn - 14.dp.toPx()) / 1.4142f
    val topLeft = Offset(center.x - half, center.y - half)
    val cells = 44 // fine enough that no banding shows
    val cell = 2 * half / cells
    for (i in 0 until cells) for (j in 0 until cells) {
      drawRect(screen(hsl(hue, (i + 0.5f) / cells, 1f - (j + 0.5f) / cells)), Offset(topLeft.x + i * cell, topLeft.y + j * cell), Size(cell + 0.6f, cell + 0.6f))
    }
    drawCircle(Color.White, 8.dp.toPx(), Offset(topLeft.x + sat * 2 * half, topLeft.y + (1f - light) * 2 * half), style = Stroke(3.dp.toPx()))
  }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.rotateCompat(degrees: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
  drawContext.transform.rotate(degrees, center)
  block()
  drawContext.transform.rotate(-degrees, center)
}

// ---- RGB -----------------------------------------------------------------------------------

@Composable
private fun RgbPicker(selected: Int, preview: Color, onPick: (Int) -> Unit) {
  var r by remember { mutableFloatStateOf(((selected shr 16) and 0xFF).toFloat()) }
  var g by remember { mutableFloatStateOf(((selected shr 8) and 0xFF).toFloat()) }
  var b by remember { mutableFloatStateOf((selected and 0xFF).toFloat()) }
  fun emit() = onPick((0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt())
  // The sliders sit in a band round the middle, on a dark disc like the other modes.
  Box(Modifier.fillMaxSize().clip(CircleShape).background(PenLabColors.toolbar.copy(alpha = 0.97f)), contentAlignment = Alignment.Center) {
    Column(Modifier.width(340.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Box(Modifier.size(120.dp).background(preview, CircleShape).border(1.dp, PenLabColors.swatchEdge, CircleShape))
      Spacer(Modifier.height(20.dp))
      for ((label, value, set) in listOf(
        Triple("R", r) { v: Float -> r = v },
        Triple("G", g) { v: Float -> g = v },
        Triple("B", b) { v: Float -> b = v },
      )) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(340.dp)) {
          Text(label, color = PenLabColors.icon, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(18.dp))
          Slider(
            value = value,
            onValueChange = {
              set(Math.round(it).toFloat())
              emit()
            },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
              thumbColor = MaterialTheme.colorScheme.primary,
              activeTrackColor = MaterialTheme.colorScheme.primary,
              inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.weight(1f),
          )
          Text("${value.toInt()}", color = PenLabColors.iconDisabled, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), modifier = Modifier.width(32.dp))
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

private val WheelDiameter = 580.dp
private val CentreDiameter = 190.dp
private val GreyInner = 100.dp
private val GreyOuter = 128.dp
private val SpokeInner = 136.dp
private val HslRing = 40.dp
