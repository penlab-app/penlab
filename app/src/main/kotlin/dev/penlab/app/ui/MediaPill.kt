package dev.penlab.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.penlab.app.ui.components.PenLabChromePill
import dev.penlab.app.ui.theme.PenLabSpacing
import dev.penlab.app.ui.theme.PenLabTextStyles
import kotlinx.coroutines.delay

/** "1:02:05" / "12:34": the recording pill's clock (hours only once there are some). */
fun formatRecordingTime(ms: Long): String {
  val s = ms.coerceAtLeast(0L) / 1000
  val h = s / 3600
  val m = (s % 3600) / 60
  val sec = s % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** How many of [bars] meter bars light for a microphone [level] (0..1): a little offset so quiet speech still shows one. */
fun litBars(level: Float, bars: Int = LEVEL_BARS): Int = (level.coerceIn(0f, 1f) * bars + 0.2f).toInt().coerceIn(0, bars)

private const val LEVEL_BARS = 5

/**
 * The recording pill (the "media" plugin capability): a dot, the elapsed time, a level meter, pause or resume, and stop.
 * It sits in the top chrome while a plugin records and is the same chrome pill as the islands (so palm rejection is
 * built in: [isPalm] drops finger touches while the pen writes, and the buttons are 48 dp targets).
 *
 * [elapsedMs] and [level] are read on a timer here, not by the caller, so the page screen does not recompose for them;
 * the meter's bars are drawn from state read in the draw phase.
 */
@Composable
fun RecordingPill(
  paused: Boolean,
  elapsedMs: () -> Long,
  level: () -> Float,
  onPauseOrResume: () -> Unit,
  onStop: () -> Unit,
  isPalm: () -> Boolean,
  modifier: Modifier = Modifier,
) {
  val elapsedSource by rememberUpdatedState(elapsedMs)
  val levelSource by rememberUpdatedState(level)
  var shown by remember { mutableLongStateOf(elapsedMs()) }
  var lit by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(paused) {
    shown = elapsedSource()
    if (paused) {
      lit = 0f
      return@LaunchedEffect
    }
    var tick = 0
    while (true) {
      lit = levelSource()
      if (tick++ % 5 == 0) shown = elapsedSource() // the clock changes twice a second, the meter ten times
      delay(100)
    }
  }
  val scheme = MaterialTheme.colorScheme
  val state = if (paused) "Recording paused" else "Recording"
  PenLabChromePill(
    modifier = modifier,
    isPalm = isPalm,
    contentPadding = PaddingValues(start = PenLabSpacing.md, end = PenLabSpacing.xs),
    horizontalArrangement = Arrangement.spacedBy(0.dp),
  ) {
    Box(
      Modifier
        .size(10.dp)
        .clip(CircleShape)
        .background(if (paused) scheme.onSurfaceVariant else scheme.error)
        .semantics { contentDescription = state },
    )
    Spacer(Modifier.width(PenLabSpacing.sm))
    Text(
      text = formatRecordingTime(shown) + if (paused) " · paused" else "",
      color = scheme.onSurface,
      style = PenLabTextStyles.tabularLabel,
    )
    if (!paused) {
      Spacer(Modifier.width(PenLabSpacing.sm))
      LevelMeter(level = { lit })
    }
    Spacer(Modifier.width(PenLabSpacing.xs))
    ChromePillIconButton(
      icon = if (paused) PenLabIcons.Microphone else PenLabIcons.Pause,
      contentDescription = if (paused) "Resume recording" else "Pause recording",
      onClick = onPauseOrResume,
    )
    ChromePillIconButton(icon = PenLabIcons.Stop, contentDescription = "Stop recording", onClick = onStop)
  }
}

/** The idle state of a media plugin whose manifest asks for the top bar: one button that starts recording. */
@Composable
fun RecordLectureButton(label: String, onClick: () -> Unit, isPalm: () -> Boolean, modifier: Modifier = Modifier) {
  PenLabChromePill(modifier = modifier, isPalm = isPalm) {
    ChromePillIconButton(icon = PenLabIcons.Microphone, contentDescription = label, onClick = onClick)
  }
}

/** Five bars for the microphone level; [level] is read in the draw phase, so only the meter redraws. */
@Composable
private fun LevelMeter(level: () -> Float) {
  val on = MaterialTheme.colorScheme.primary
  val off = MaterialTheme.colorScheme.outlineVariant
  Canvas(Modifier.size(width = 26.dp, height = 16.dp)) {
    val gap = size.width / (LEVEL_BARS * 2 - 1)
    val lit = litBars(level())
    for (i in 0 until LEVEL_BARS) {
      val h = size.height * (0.35f + 0.65f * (i + 1) / LEVEL_BARS)
      drawRoundRect(
        color = if (i < lit) on else off,
        topLeft = Offset(i * gap * 2, size.height - h),
        size = Size(gap, h),
        cornerRadius = CornerRadius(gap / 2),
      )
    }
  }
}
