package dev.penlab.app.ui

import android.content.Context
import android.view.MotionEvent
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.penlab.app.ui.theme.PageThemeMode

/**
 * The page-view presets and locks, kept between launches (Android's SharedPreferences: a small
 * key-value file in the app's private storage).
 *
 * Zoom presets are percentages, rotation presets degrees. The user can add the current view as a
 * preset and remove presets they do not want.
 */
@Stable
class ViewSettings(context: Context) {
  private val prefs = context.getSharedPreferences("view", Context.MODE_PRIVATE)

  var zoomPresets by mutableStateOf(readList(KEY_ZOOMS, listOf(50, 100, 200)))
    private set

  var anglePresets by mutableStateOf(readList(KEY_ANGLES, listOf(0, 90, -90)))
    private set

  var zoomLocked by mutableStateOf(prefs.getBoolean(KEY_ZOOM_LOCK, false))
    private set

  var rotationLocked by mutableStateOf(prefs.getBoolean(KEY_ROTATION_LOCK, false))
    private set

  /** True once any stylus or eraser-end event has reached the app on this device (kept for good). */
  var stylusSeen by mutableStateOf(prefs.getBoolean(KEY_STYLUS_SEEN, false))
    private set

  /** What the user chose for "Draw with finger", or null while they never touched the switch. */
  private var fingerDrawChoice by mutableStateOf(if (prefs.contains(KEY_FINGER_DRAW)) prefs.getBoolean(KEY_FINGER_DRAW, false) else null)

  /** Whether a single finger draws (see [fingerDrawDefault] for the rule). */
  val drawWithFinger: Boolean
    get() = fingerDrawDefault(stylusSeen, fingerDrawChoice)

  fun setDrawWithFinger(on: Boolean) {
    fingerDrawChoice = on
    prefs.edit().putBoolean(KEY_FINGER_DRAW, on).apply()
  }

  /** Hold-to-snap-shapes preference (on by default). */
  private var _shapeSnapping by mutableStateOf(
    shapeSnappingDefault(if (prefs.contains(KEY_SHAPE_SNAPPING)) prefs.getBoolean(KEY_SHAPE_SNAPPING, DEFAULT_SHAPE_SNAPPING) else null),
  )

  val shapeSnapping: Boolean
    get() = _shapeSnapping

  fun setShapeSnapping(enabled: Boolean) {
    _shapeSnapping = enabled
    prefs.edit().putBoolean(KEY_SHAPE_SNAPPING, enabled).apply()
  }

  /** Call for every touch or hover event: remembers the first time a pen shows up. */
  fun noteMotionEvent(event: MotionEvent) {
    if (stylusSeen) return // cheap exit for the rest of the app's life
    for (index in 0 until event.pointerCount) {
      val type = event.getToolType(index)
      if (type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER) {
        stylusSeen = true
        prefs.edit().putBoolean(KEY_STYLUS_SEEN, true).apply()
        return
      }
    }
  }

  fun addZoomPreset(percent: Int) = updateZooms((zoomPresets + percent).distinct().sorted())

  fun removeZoomPreset(percent: Int) = updateZooms(zoomPresets - percent)

  fun addAnglePreset(degrees: Int) = updateAngles((anglePresets + degrees).distinct())

  fun removeAnglePreset(degrees: Int) = updateAngles(anglePresets - degrees)

  fun setZoomLock(locked: Boolean) {
    zoomLocked = locked
    prefs.edit().putBoolean(KEY_ZOOM_LOCK, locked).apply()
  }

  fun setRotationLock(locked: Boolean) {
    rotationLocked = locked
    prefs.edit().putBoolean(KEY_ROTATION_LOCK, locked).apply()
  }

  private fun updateZooms(list: List<Int>) {
    zoomPresets = list
    prefs.edit().putString(KEY_ZOOMS, list.joinToString(",")).apply()
  }

  private fun updateAngles(list: List<Int>) {
    anglePresets = list
    prefs.edit().putString(KEY_ANGLES, list.joinToString(",")).apply()
  }

  private fun readList(key: String, default: List<Int>): List<Int> =
    prefs.getString(key, null)?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: default

  private companion object {
    const val KEY_ZOOMS = "zoom_presets"
    const val KEY_ANGLES = "angle_presets"
    const val KEY_ZOOM_LOCK = "zoom_locked"
    const val KEY_ROTATION_LOCK = "rotation_locked"
    const val KEY_STYLUS_SEEN = "stylus_seen"
    const val KEY_FINGER_DRAW = "finger_draw"
    const val KEY_SHAPE_SNAPPING = "shape_snapping"
  }
}

/** Default for "Hold to snap shapes" when the user has never changed the switch. */
const val DEFAULT_SHAPE_SNAPPING = true

/**
 * The "Hold to snap shapes" rule: an explicit [storedChoice] wins, otherwise defaults to on.
 */
fun shapeSnappingDefault(storedChoice: Boolean?): Boolean = storedChoice ?: DEFAULT_SHAPE_SNAPPING

/**
 * Resolves whether a notebook canvas renders in dark-paper mode.
 * PDF notebooks always stay on white paper regardless of [pageThemeMode].
 */
fun resolveCanvasDarkPage(pageThemeMode: PageThemeMode, isPdfNotebook: Boolean): Boolean =
  pageThemeMode.isDark && !isPdfNotebook

/**
 * The "Draw with finger" rule. An explicit [userChoice] always wins. Without one it is on for a
 * device where no stylus has ever been seen (a tablet without a pen) and off once one has.
 */
fun fingerDrawDefault(stylusSeen: Boolean, userChoice: Boolean?): Boolean = userChoice ?: !stylusSeen
