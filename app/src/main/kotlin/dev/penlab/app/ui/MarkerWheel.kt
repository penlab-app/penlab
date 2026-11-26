package dev.penlab.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The marker colour wheel (a request from users): a big wheel standing at the left edge
 * of the screen, its colours on spokes that fan out from the middle when it opens. Drag round
 * to spin it and bring more colour families into view (a flick keeps it spinning); tap a colour
 * to pick it. Three rings of greys (neutral, cool, warm) sit round the middle.
 *
 * The palette is PenLab's own, laid out the way marker ranges are: 14 hue families, each in a
 * vivid, a soft and a muted spoke, every spoke going from deep (inside) to pale (outside). That is
 * 42 spokes of 7 shades plus 36 greys, about 330 colours. (Copic's own codes and colours are
 * Copic's trademark, licensed by other apps, so they are not copied here.)
 *
 * Colours are screen colours, as everywhere in the colour picker.
 */
@Composable
fun MarkerWheel(
  selected: Int,
  onPick: (Int) -> Unit,
  onMiss: () -> Unit,
  modifier: Modifier = Modifier,
  /**
   * Where the wheel's middle is (screen pixels): the tool wheel's hub when it sits in a corner,
   * so the colours fan out of the same corner as the tools. Null: left edge.
   */
  hub: Offset? = null,
) {
  fun centre(w: Float, h: Float) = hub ?: defaultCentre(w, h)
  fun radiusFor(h: Float) = if (hub != null) h * 0.7f else defaultRadius(h)
  val spin = remember { Animatable(0f) } // degrees
  val open = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()
  val outlineOuter = MaterialTheme.colorScheme.inverseSurface
  val outlineInner = MaterialTheme.colorScheme.inverseOnSurface
  LaunchedEffect(Unit) { open.animateTo(1f, Motion.tween(520, easing = Motion.Standard)) }

  Canvas(
    modifier
      // Drawn and driven by gestures only, so say what it is and which colour is chosen.
      .semantics { contentDescription = "Marker colour wheel. Chosen colour ${formatHexRgb(selected)}. Drag to turn, tap a colour to pick it." }
      .pointerInput(Unit) {
        val tracker = VelocityTracker()
        var lastAngle = 0f
        var turned = 0f // degrees since the finger came down, for the fling speed
        detectDragGestures(
          onDragStart = { p ->
            scope.launch { spin.stop() }
            tracker.resetTracking()
            turned = 0f
            lastAngle = angleAt(p, centre(size.width.toFloat(), size.height.toFloat()))
          },
          onDragEnd = {
            // Keep spinning at the finger's speed, slowing like a wheel on an axle.
            val degreesPerSecond = tracker.calculateVelocity().y
            scope.launch { spin.animateDecay(degreesPerSecond, exponentialDecay(frictionMultiplier = 1.4f)) }
          },
        ) { change, _ ->
          change.consume()
          val a = angleAt(change.position, centre(size.width.toFloat(), size.height.toFloat()))
          var d = a - lastAngle
          if (d > 180f) d -= 360f
          if (d < -180f) d += 360f
          lastAngle = a
          turned += d
          tracker.addPosition(change.uptimeMillis, Offset(0f, turned))
          scope.launch { spin.snapTo(spin.value + d) }
        }
      }
      .pointerInput(Unit) {
        detectTapGestures { p ->
          val w = size.width.toFloat()
          val h = size.height.toFloat()
          colourAt(p, centre(w, h), radiusFor(h), spin.value, this)?.let(onPick) ?: onMiss() // a tap off the wheel closes
        }
      }
  ) {
    val c = centre(size.width, size.height)
    val outer = radiusFor(size.height)
    val inner = SpokeStart.toPx()
    val ringW = (outer - inner) / SHADES
    val gap = 1.2.dp.toPx()
    val step = 360f / SPOKES
    val reveal = open.value

    fun segment(start: Float, sweep: Float, r0: Float, r1: Float, colour: Color, outline: Boolean) {
      val path = Path().apply {
        arcTo(Rect(c, r1), start, sweep, forceMoveTo = true)
        arcTo(Rect(c, r0), start + sweep, -sweep, forceMoveTo = false)
        close()
      }
      drawPath(path, colour)
      if (outline) {
        drawPath(path, outlineOuter, style = Stroke(3.5.dp.toPx()))
        drawPath(path, outlineInner, style = Stroke(1.6.dp.toPx()))
      }
    }

    // Greys: three rings round the middle, light at the top going round to dark.
    val greyIn = GreyStart.toPx()
    val greyW = (inner - greyIn - 6.dp.toPx()) / 3
    for (ring in 0 until 3) {
      val r0 = greyIn + ring * greyW
      for (i in 0 until GREYS) {
        val g = grey(ring, i)
        val sweep = 360f / GREYS
        segment(spin.value - 90f + i * sweep + 0.5f, (sweep - 1f) * reveal, r0 + gap / 2, r0 + greyW - gap / 2, Color(g), g == selected)
      }
    }
    // Spokes fan out from the middle: each grows outwards, a moment after the one before.
    for (s in 0 until SPOKES) {
      val angle = spin.value - 90f + s * step
      val grow = ((reveal * 1.6f) - (s % 14) / 14f * 0.6f).coerceIn(0f, 1f)
      if (grow <= 0f) continue
      for (shade in 0 until SHADES) {
        val r0 = inner + shade * ringW
        val r1 = r0 + ringW
        val reach = inner + (outer - inner) * grow
        if (r0 > reach) break
        val col = spokeColour(s, shade)
        segment(angle + 0.6f, step - 1.2f, r0 + gap / 2, minOf(r1, reach) - gap / 2, Color(col), col == selected)
      }
    }
  }
}

// ---- Geometry ------------------------------------------------------------------------------

/** The wheel's middle: just inside the left edge, half way down. */
private fun defaultCentre(w: Float, h: Float) = Offset(h * 0.08f, h / 2f)

/** Big enough that only about half the wheel is on screen; spinning brings the rest round. */
private fun defaultRadius(h: Float) = h * 0.62f

private fun angleAt(p: Offset, c: Offset): Float = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()

private fun colourAt(p: Offset, c: Offset, outer: Float, spin: Float, density: androidx.compose.ui.unit.Density): Int? {
  val r = hypot(p.x - c.x, p.y - c.y)
  with(density) {
    val inner = SpokeStart.toPx()
    val greyIn = GreyStart.toPx()
    val greyW = (inner - greyIn - 6.dp.toPx()) / 3
    var a = angleAt(p, c) + 90f - spin
    a = ((a % 360f) + 360f) % 360f
    return when {
      r in inner..outer -> {
        val spoke = (a / (360f / SPOKES)).toInt().coerceIn(0, SPOKES - 1)
        val shade = ((r - inner) / ((outer - inner) / SHADES)).toInt().coerceIn(0, SHADES - 1)
        spokeColour(spoke, shade)
      }
      r >= greyIn && r < greyIn + 3 * greyW -> {
        val ring = ((r - greyIn) / greyW).toInt().coerceIn(0, 2)
        grey(ring, (a / (360f / GREYS)).toInt().coerceIn(0, GREYS - 1))
      }
      else -> null
    }
  }
}

// ---- The palette ---------------------------------------------------------------------------

private const val FAMILIES = 14
private const val SPOKES = FAMILIES * 3
private const val SHADES = 7
private const val GREYS = 12

/** Hue of each family, round the colour circle: reds, oranges, yellows ... pinks, then earths. */
private val familyHues = floatArrayOf(355f, 18f, 35f, 50f, 70f, 100f, 145f, 172f, 192f, 212f, 232f, 262f, 292f, 325f)

/**
 * Spoke [s] (family = s / 3, then vivid, soft, muted) at [shade] (0 = deep, 6 = pale). Every
 * family has three character variants so the range has the depth of a marker set.
 */
private fun spokeColour(s: Int, shade: Int): Int {
  val family = s / 3
  val variant = s % 3
  val hue = familyHues[family] + (variant - 1) * 4f
  val t = shade / (SHADES - 1f)
  val saturation = when (variant) {
    0 -> 0.92f - 0.25f * t
    1 -> 0.65f - 0.2f * t
    else -> 0.35f - 0.12f * t
  }
  // Oranges and yellows look muddy when dark: start them lighter.
  val darkest = if (family in 1..4) 0.34f else 0.24f
  return hsl(hue, saturation, darkest + (0.9f - darkest) * t)
}

/** Ring 0 neutral, 1 cool (blue-grey), 2 warm (brown-grey); step 0 = near white. */
private fun grey(ring: Int, step: Int): Int {
  val l = 0.96f - step * (0.9f / (GREYS - 1))
  return when (ring) {
    0 -> hsl(0f, 0f, l)
    1 -> hsl(212f, 0.12f, l)
    else -> hsl(30f, 0.12f, l)
  }
}

private val GreyStart = 70.dp
private val SpokeStart = 168.dp
