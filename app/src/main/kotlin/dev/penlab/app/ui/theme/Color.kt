package dev.penlab.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * PenLab's palette: matte greys like pencil lead. Every colour the UI uses is named here;
 * change a value here to restyle the app. (The page and ink colours live in ink/InkColors.kt
 * and ink/InkTool.kt, because they are part of the drawing, not the UI.)
 */
/**
 * The two looks (blueprint blue and copper, switchable). Each one
 * tints the neutrals a little towards its hue and owns one accent, used only for meaning: the
 * picked tool, buttons that act, switches and progress. Contrast on the toolbar:
 * blueprint accent 5.5:1, copper accent 5.4:1, icons over 13:1, secondary text about 5:1.
 * (user request: "a bit more contrast everywhere": icons, hairlines, secondary text and chips
 * were raised one step; secondary text was 3:1 before.)
 */
enum class AccentTheme(
  val label: String,
  val toolbar: Color,
  val hairline: Color,
  val icon: Color,
  val iconDisabled: Color,
  val accent: Color,
  val onAccent: Color,
  val backdrop: Color,
) {
  BLUEPRINT("Blueprint", Color(0xFF262A31), Color(0xFF4A5463), Color(0xFFF2F5FA), Color(0xFF8E97A4), Color(0xFF5AA2FF), Color(0xFF0B1626), Color(0xFF14171C)),
  COPPER("Copper", Color(0xFF2D2A27), Color(0xFF5A524B), Color(0xFFF7F2ED), Color(0xFF9C928A), Color(0xFFE08A50), Color(0xFF24130A), Color(0xFF191614)),
}

/** The chosen look, kept in preferences; changing it recolours the whole UI at once. */
object Accent {
  var theme by mutableStateOf(AccentTheme.BLUEPRINT)
    private set

  private var prefs: android.content.SharedPreferences? = null

  fun load(context: android.content.Context) {
    val p = context.getSharedPreferences("look", android.content.Context.MODE_PRIVATE)
    prefs = p
    theme = AccentTheme.entries.firstOrNull { it.name == p.getString("accent", null) } ?: AccentTheme.BLUEPRINT
  }

  fun set(value: AccentTheme) {
    theme = value
    prefs?.edit()?.putString("accent", value.name)?.apply()
  }
}

object PenLabColors {
  /** Toolbar body: one step lighter than the dark page so it reads as resting on it. */
  val toolbar: Color get() = Accent.theme.toolbar

  /** Hairline around the toolbar and the divider inside it. */
  val hairline: Color get() = Accent.theme.hairline

  /** Icons and text. */
  val icon: Color get() = Accent.theme.icon

  /** Icons of actions that are not available right now (nothing to undo, empty page). */
  val iconDisabled: Color get() = Accent.theme.iconDisabled

  /** The one accent: picked tool, acting buttons, switches, progress. */
  val accent: Color get() = Accent.theme.accent

  /** Text or icons on a filled accent. */
  val onAccent: Color get() = Accent.theme.onAccent

  /** Library and full-screen backgrounds behind the cards. */
  val backdrop: Color get() = Accent.theme.backdrop

  /** Thin edge on every colour swatch, so dark colours do not sink into the toolbar. */
  val swatchEdge = Color(0x73E4E5E7)

  /** Background of the chosen option inside a panel: the accent, faint. */
  val optionSelected: Color get() = Accent.theme.accent.copy(alpha = 0.28f)

  /** Unpicked chips and quiet buttons: a neutral lift off the surface. */
  val chip = Color(0x29FFFFFF)

  /** Matched words in search results, the same amber as the highlight on the page. */
  val searchMatch = Color(0xFFFFC34D)
}

/**
 * How floating chrome separates from the page (user request: "soft shadow"): a soft drop
 * shadow instead of a hairline, and only that (one depth cue per surface). Must come before the
 * clip in a modifier chain, or the clip cuts the shadow off.
 */
fun Modifier.softShadow(shape: Shape): Modifier =
  this.shadow(elevation = 10.dp, shape = shape, clip = false, ambientColor = Color(0x99000000), spotColor = Color(0xCC000000))
