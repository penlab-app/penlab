package dev.penlab.app.ui

import android.content.Context
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/** How fast the interface moves. [factor] stretches every animation's time; 0 turns them off. */
enum class MotionSpeed(val label: String, val factor: Float) {
  OFF("Off", 0f),
  SLOW("Slow", 1.8f),
  NORMAL("Normal", 1f),
  FAST("Fast", 0.55f),
}

/**
 * Every UI animation goes through here, so one setting (in the zoom readout's panel) can speed
 * them up, slow them down or switch them off. The page itself (ink, pan, zoom)
 * never animates, so this cannot slow writing down.
 */
object Motion {
  var speed by mutableStateOf(MotionSpeed.NORMAL)
    private set

  private var prefs: android.content.SharedPreferences? = null

  fun load(context: Context) {
    val p = context.getSharedPreferences("motion", Context.MODE_PRIVATE)
    prefs = p
    // Until the user picks a speed here, follow Android's own animation setting (off = off).
    val system = android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    val fromSystem = when {
      system == 0f -> MotionSpeed.OFF
      system < 0.8f -> MotionSpeed.FAST
      system > 1.4f -> MotionSpeed.SLOW
      else -> MotionSpeed.NORMAL
    }
    speed = MotionSpeed.entries.firstOrNull { it.name == p.getString("speed", null) } ?: fromSystem
  }

  fun set(value: MotionSpeed) {
    speed = value
    prefs?.edit()?.putString("speed", value.name)?.apply()
  }

  val enabled: Boolean
    get() = speed != MotionSpeed.OFF

  /** A spring whose settling time follows the setting (stiffness scales with 1/time²). */
  fun <T> spring(dampingRatio: Float = 1f, stiffness: Float = 500f): FiniteAnimationSpec<T> =
    if (!enabled) snap()
    else androidx.compose.animation.core.spring(dampingRatio, stiffness / (speed.factor * speed.factor))

  fun <T> tween(durationMillis: Int, delayMillis: Int = 0, easing: Easing = Standard): FiniteAnimationSpec<T> =
    if (!enabled) snap()
    else androidx.compose.animation.core.tween((durationMillis * speed.factor).roundToInt(), (delayMillis * speed.factor).roundToInt(), easing)

  /** Quick out, gentle settle: for things appearing. */
  val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
