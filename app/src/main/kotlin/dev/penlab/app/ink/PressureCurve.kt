package dev.penlab.app.ink

/**
 * S Pen / stylus pressure sensitivity calibration presets.
 *
 * Kept pure Kotlin (no `androidx.ink.*` JNI imports) so JVM unit tests can validate every curve
 * without native libraries.
 */
enum class PressureSensitivity(val label: String) {
  LIGHT("Light"),
  BALANCED("Balanced"),
  FIRM("Firm"),
}

val DEFAULT_PRESSURE_SENSITIVITY = PressureSensitivity.BALANCED

/** Resolves a persisted [PressureSensitivity] name, falling back to [DEFAULT_PRESSURE_SENSITIVITY]. */
fun pressureSensitivityFromStorage(stored: String?): PressureSensitivity =
  PressureSensitivity.entries.firstOrNull { it.name == stored } ?: DEFAULT_PRESSURE_SENSITIVITY

/** Linear clamped mapping from stylus pressure `[sourceStart, sourceEnd]` to `[minMultiplier, maxMultiplier]`. */
data class PressureCurveSpec(
  val sourceStart: Float,
  val sourceEnd: Float,
  val minMultiplier: Float,
  val maxMultiplier: Float,
) {
  fun evaluateMultiplier(pressure: Float): Float {
    if (!pressure.isFinite() || pressure < 0f) return 1f
    val t = ((pressure - sourceStart) / (sourceEnd - sourceStart)).coerceIn(0f, 1f)
    return minMultiplier + t * (maxMultiplier - minMultiplier)
  }
}

/** Returns the pressure response curve for [style] under [sensitivity], or null for fixed-width ballpoint. */
fun pressureCurveFor(style: PenStyle, sensitivity: PressureSensitivity): PressureCurveSpec? =
  when (style) {
    PenStyle.BALLPOINT -> null
    PenStyle.PRESSURE ->
      when (sensitivity) {
        PressureSensitivity.LIGHT -> PressureCurveSpec(sourceStart = 0.02f, sourceEnd = 0.48f, minMultiplier = 0.22f, maxMultiplier = 1.85f)
        PressureSensitivity.BALANCED -> PressureCurveSpec(sourceStart = 0.03f, sourceEnd = 0.65f, minMultiplier = 0.25f, maxMultiplier = 1.75f)
        PressureSensitivity.FIRM -> PressureCurveSpec(sourceStart = 0.05f, sourceEnd = 0.85f, minMultiplier = 0.35f, maxMultiplier = 1.60f)
      }
    PenStyle.FOUNTAIN ->
      when (sensitivity) {
        PressureSensitivity.LIGHT -> PressureCurveSpec(sourceStart = 0.02f, sourceEnd = 0.52f, minMultiplier = 0.35f, maxMultiplier = 1.75f)
        PressureSensitivity.BALANCED -> PressureCurveSpec(sourceStart = 0.03f, sourceEnd = 0.68f, minMultiplier = 0.40f, maxMultiplier = 1.68f)
        PressureSensitivity.FIRM -> PressureCurveSpec(sourceStart = 0.05f, sourceEnd = 0.88f, minMultiplier = 0.48f, maxMultiplier = 1.55f)
      }
  }
