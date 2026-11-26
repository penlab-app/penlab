package dev.penlab.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layer 1 — Primitives.
 *
 * Raw tonal ramps for PenLab's two architectural looks (Blueprint slate and Copper warm stone)
 * plus semantic status ramps. UI components must never read these directly; use
 * [MaterialTheme.colorScheme], [LocalPenLabColors], or [PenLabComponentTokens] instead.
 */
object PenLabPrimitives {
  // Slate Neutral (Blueprint)
  val Slate0 = Color(0xFFFFFFFF)
  val Slate50 = Color(0xFFF6F8FB)
  val Slate100 = Color(0xFFECEFF5)
  val Slate200 = Color(0xFFDCE2EC)
  // Blueprint `outline`: 3.3:1 on the canvas, 3.0:1 on the pale chip fill (WCAG 1.4.11 UI boundaries).
  val Slate300 = Color(0xFF7F8A9C)
  val Slate400 = Color(0xFF8E99A8)
  val Slate450 = Color(0xFF9CA5B3)
  val Slate500 = Color(0xFF525D6D)
  val Slate600 = Color(0xFF3E4856)
  // Dark `outline`: 4.2:1 on the canvas, 3.2:1 on the translucent chip fill.
  val Slate650 = Color(0xFF707B8D)
  val Slate700 = Color(0xFF353D49)
  val Slate750 = Color(0xFF2B313B)
  val Slate800 = Color(0xFF232830)
  val Slate850 = Color(0xFF1C2027)
  val Slate900 = Color(0xFF14171C)
  val Slate950 = Color(0xFF0D1014)
  val SlateOnDark = Color(0xFFF2F5FA)

  // Warm Stone Neutral (Copper)
  val Stone0 = Color(0xFFFFFFFF)
  val Stone50 = Color(0xFFFAF7F4)
  val Stone75 = Color(0xFFF8F4F0)
  val Stone100 = Color(0xFFF1ECE6)
  val Stone200 = Color(0xFFE2D9D0)
  // Copper `outline`: 3.1:1 on the pale chip fill, 3.4:1 on the canvas (WCAG 1.4.11 UI boundaries).
  val Stone300 = Color(0xFF8F857B)
  val Stone400 = Color(0xFF9B9189)
  val Stone450 = Color(0xFFAFA59D)
  val Stone500 = Color(0xFF625851)
  val Stone600 = Color(0xFF4E4640)
  // Dark Copper `outline`: 4.2:1 on the canvas, 3.2:1 on the chip fill.
  val Stone650 = Color(0xFF827870)
  val Stone700 = Color(0xFF403934)
  val Stone750 = Color(0xFF35302C)
  val Stone800 = Color(0xFF2C2825)
  val Stone850 = Color(0xFF23201D)
  val Stone900 = Color(0xFF191614)
  val Stone950 = Color(0xFF12100E)
  val StoneOnDark = Color(0xFFF7F2ED)

  // Blueprint Accents
  val BlueLight500 = Color(0xFF2563EB)
  val BlueLight600 = Color(0xFF1D4ED8)
  val BlueLight100 = Color(0xFFDBEAFE)
  val BlueLight900 = Color(0xFF1E3A8A)
  val BlueDark400 = Color(0xFF5AA2FF)
  val BlueDark950 = Color(0xFF0B1626)
  val BlueDark900 = Color(0xFF1E3554)
  val BlueDark100 = Color(0xFFD6E8FF)

  // Copper Accents (light primary tuned to 5.45:1 on white / 5.12:1 on warm canvas)
  val CopperLight600 = Color(0xFFAD4D1B)
  val CopperLight700 = Color(0xFF8F3D14)
  val CopperLight100 = Color(0xFFFCE8DC)
  val CopperLight900 = Color(0xFF6B2D0F)
  val CopperDark400 = Color(0xFFE59158)
  val CopperDark950 = Color(0xFF24130A)
  val CopperDark900 = Color(0xFF4A2B1A)
  val CopperDark100 = Color(0xFFFCE8DC)

  // Semantic Status
  val RedLight = Color(0xFFDC2626)
  val RedLightContainer = Color(0xFFFEE2E2)
  val RedLightOnContainer = Color(0xFF7F1D1D)
  val RedDark = Color(0xFFF87171)
  val RedDarkOn = Color(0xFF2B0909)
  val RedDarkContainer = Color(0xFF451A1A)
  val RedDarkOnContainer = Color(0xFFFECACA)

  val GreenLight = Color(0xFF15803D)
  val GreenDark = Color(0xFF4ADE80)

  val AmberLight = Color(0xFFD97706)
  val AmberLightContainer = Color(0xFFFEF3C7)
  val AmberLightOnContainer = Color(0xFF78350F)
  val AmberLightHighlight = Color(0xFFF59E0B)
  val AmberDark = Color(0xFFFFC34D)
  val AmberDarkContainer = Color(0xFF422F0A)
  val AmberDarkOnContainer = Color(0xFFFFE7B3)

  // Neutral scrim & edge overlays
  val SwatchEdgeLight = Color(0x26000000)
  val SwatchEdgeDark = Color(0x66FFFFFF)
  val ShadowAmbientLight = Color(0x330F172A)
  val ShadowSpotLight = Color(0x470F172A)
  val ShadowAmbientDark = Color(0x99000000)
  val ShadowSpotDark = Color(0xCC000000)
  val ScrimLight = Color(0x52000000)
  val ScrimDark = Color(0x80000000)
}

/**
 * The two UI looks: cool drafting Blueprint slate and warm architectural Copper stone.
 */
enum class AccentTheme(
  val label: String,
  val lightPrimary: Color,
  val darkPrimary: Color,
) {
  BLUEPRINT("Blueprint", PenLabPrimitives.BlueLight500, PenLabPrimitives.BlueDark400),
  COPPER("Copper", PenLabPrimitives.CopperLight600, PenLabPrimitives.CopperDark400);

  fun primaryFor(isDark: Boolean): Color = if (isDark) darkPrimary else lightPrimary

  /** Active accent colour for the current UI theme mode. */
  val accent: Color get() = primaryFor(Accent.isDark)
}

/**
 * App chrome theme mode (persisted in SharedPreferences("look")).
 */
enum class ThemeMode(val label: String) {
  LIGHT("Light"),
  DARK("Dark"),
  SYSTEM("System");

  fun resolveDark(systemIsDark: Boolean): Boolean =
    when (this) {
      LIGHT -> false
      DARK -> true
      SYSTEM -> systemIsDark
    }
}

/**
 * Default paper look for plain notebooks (PDF notebooks always stay light/white).
 */
enum class PageThemeMode(val label: String) {
  LIGHT("Light"),
  DARK("Dark");

  val isDark: Boolean get() = this == DARK
}

/**
 * Persisted appearance preferences (accent look, UI Light/Dark/System mode, and plain-notebook
 * paper mode). Kept in SharedPreferences("look") so changing any preference recolours the UI
 * immediately.
 */
object Accent {
  private var _theme by mutableStateOf(AccentTheme.BLUEPRINT)
  private var _themeMode by mutableStateOf(ThemeMode.DARK)
  private var _pageThemeMode by mutableStateOf(PageThemeMode.DARK)
  private var _resolvedDark by mutableStateOf(true)

  val theme: AccentTheme get() = _theme
  val themeMode: ThemeMode get() = _themeMode
  val pageThemeMode: PageThemeMode get() = _pageThemeMode

  /**
   * Resolved dark-mode flag synchronized by [PenLabTheme] (using `isSystemInDarkTheme()` for
   * [ThemeMode.SYSTEM]) and updated eagerly for explicit [ThemeMode.LIGHT] / [ThemeMode.DARK].
   */
  val isDark: Boolean get() = _resolvedDark
  val isDarkPage: Boolean get() = _pageThemeMode.isDark

  private var prefs: SharedPreferences? = null

  fun load(context: Context) {
    val p = context.getSharedPreferences("look", Context.MODE_PRIVATE)
    prefs = p
    _theme = AccentTheme.entries.firstOrNull { it.name == p.getString(KEY_ACCENT, null) } ?: AccentTheme.BLUEPRINT
    _themeMode = ThemeMode.entries.firstOrNull { it.name == p.getString(KEY_THEME_MODE, null) } ?: ThemeMode.DARK
    _pageThemeMode = PageThemeMode.entries.firstOrNull { it.name == p.getString(KEY_PAGE_THEME_MODE, null) } ?: PageThemeMode.DARK
    val systemDark =
      (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    _resolvedDark = _themeMode.resolveDark(systemDark)
  }

  fun set(value: AccentTheme) {
    _theme = value
    prefs?.edit()?.putString(KEY_ACCENT, value.name)?.apply()
  }

  fun setThemeMode(value: ThemeMode, systemIsDark: Boolean = _resolvedDark) {
    _themeMode = value
    _resolvedDark = value.resolveDark(systemIsDark)
    prefs?.edit()?.putString(KEY_THEME_MODE, value.name)?.apply()
  }

  fun setPageThemeMode(value: PageThemeMode) {
    _pageThemeMode = value
    prefs?.edit()?.putString(KEY_PAGE_THEME_MODE, value.name)?.apply()
  }

  internal fun syncResolvedDark(isDark: Boolean) {
    if (_resolvedDark != isDark) _resolvedDark = isDark
  }

  private const val KEY_ACCENT = "accent"
  private const val KEY_THEME_MODE = "theme_mode"
  private const val KEY_PAGE_THEME_MODE = "page_theme_mode"
}

/**
 * Layer 2 — PenLab-specific semantic roles beyond Material 3's [ColorScheme].
 */
@Immutable
data class PenLabExtendedColors(
  val canvasBackdrop: Color,
  val floatingChrome: Color,
  val floatingChromeBorder: Color,
  val toolSelectedRing: Color,
  val toolSelectedFill: Color,
  val iconDisabled: Color,
  val chipIdle: Color,
  val searchMatch: Color,
  val searchMatchBg: Color,
  val swatchEdge: Color,
  val success: Color,
  val warning: Color,
  val isDark: Boolean,
)

private val BlueprintLightScheme: ColorScheme =
  lightColorScheme(
    primary = PenLabPrimitives.BlueLight500,
    onPrimary = PenLabPrimitives.Slate0,
    primaryContainer = PenLabPrimitives.BlueLight100,
    onPrimaryContainer = PenLabPrimitives.BlueLight900,
    secondary = PenLabPrimitives.BlueLight600,
    onSecondary = PenLabPrimitives.Slate0,
    secondaryContainer = PenLabPrimitives.Slate100,
    onSecondaryContainer = PenLabPrimitives.Slate900,
    tertiary = PenLabPrimitives.AmberLight,
    onTertiary = PenLabPrimitives.Slate0,
    tertiaryContainer = PenLabPrimitives.AmberLightContainer,
    onTertiaryContainer = PenLabPrimitives.AmberLightOnContainer,
    error = PenLabPrimitives.RedLight,
    onError = PenLabPrimitives.Slate0,
    errorContainer = PenLabPrimitives.RedLightContainer,
    onErrorContainer = PenLabPrimitives.RedLightOnContainer,
    background = PenLabPrimitives.Slate50,
    onBackground = PenLabPrimitives.Slate900,
    surface = PenLabPrimitives.Slate0,
    onSurface = PenLabPrimitives.Slate900,
    surfaceVariant = PenLabPrimitives.Slate100,
    onSurfaceVariant = PenLabPrimitives.Slate500,
    surfaceContainerLowest = PenLabPrimitives.Slate0,
    surfaceContainerLow = PenLabPrimitives.Slate50,
    surfaceContainer = PenLabPrimitives.Slate0,
    surfaceContainerHigh = PenLabPrimitives.Slate50,
    surfaceContainerHighest = PenLabPrimitives.Slate100,
    outline = PenLabPrimitives.Slate300,
    outlineVariant = PenLabPrimitives.Slate200,
    inverseSurface = PenLabPrimitives.Slate850,
    inverseOnSurface = PenLabPrimitives.SlateOnDark,
    inversePrimary = PenLabPrimitives.BlueDark400,
    scrim = PenLabPrimitives.ScrimLight,
  )

private val BlueprintDarkScheme: ColorScheme =
  darkColorScheme(
    primary = PenLabPrimitives.BlueDark400,
    onPrimary = PenLabPrimitives.BlueDark950,
    primaryContainer = PenLabPrimitives.BlueDark900,
    onPrimaryContainer = PenLabPrimitives.BlueDark100,
    secondary = PenLabPrimitives.BlueDark400,
    onSecondary = PenLabPrimitives.BlueDark950,
    secondaryContainer = PenLabPrimitives.Slate750,
    onSecondaryContainer = PenLabPrimitives.SlateOnDark,
    tertiary = PenLabPrimitives.AmberDark,
    onTertiary = PenLabPrimitives.Slate950,
    tertiaryContainer = PenLabPrimitives.AmberDarkContainer,
    onTertiaryContainer = PenLabPrimitives.AmberDarkOnContainer,
    error = PenLabPrimitives.RedDark,
    onError = PenLabPrimitives.RedDarkOn,
    errorContainer = PenLabPrimitives.RedDarkContainer,
    onErrorContainer = PenLabPrimitives.RedDarkOnContainer,
    background = PenLabPrimitives.Slate900,
    onBackground = PenLabPrimitives.SlateOnDark,
    surface = PenLabPrimitives.Slate850,
    onSurface = PenLabPrimitives.SlateOnDark,
    surfaceVariant = PenLabPrimitives.Slate800,
    onSurfaceVariant = PenLabPrimitives.Slate450,
    surfaceContainerLowest = PenLabPrimitives.Slate950,
    surfaceContainerLow = PenLabPrimitives.Slate850,
    surfaceContainer = PenLabPrimitives.Slate800,
    surfaceContainerHigh = PenLabPrimitives.Slate750,
    surfaceContainerHighest = PenLabPrimitives.Slate700,
    outline = PenLabPrimitives.Slate650,
    outlineVariant = PenLabPrimitives.Slate700,
    inverseSurface = PenLabPrimitives.Slate100,
    inverseOnSurface = PenLabPrimitives.Slate900,
    inversePrimary = PenLabPrimitives.BlueLight500,
    scrim = PenLabPrimitives.ScrimDark,
  )

private val CopperLightScheme: ColorScheme =
  lightColorScheme(
    primary = PenLabPrimitives.CopperLight600,
    onPrimary = PenLabPrimitives.Stone0,
    primaryContainer = PenLabPrimitives.CopperLight100,
    onPrimaryContainer = PenLabPrimitives.CopperLight900,
    secondary = PenLabPrimitives.CopperLight700,
    onSecondary = PenLabPrimitives.Stone0,
    secondaryContainer = PenLabPrimitives.Stone100,
    onSecondaryContainer = PenLabPrimitives.Stone900,
    tertiary = PenLabPrimitives.AmberLight,
    onTertiary = PenLabPrimitives.Stone0,
    tertiaryContainer = PenLabPrimitives.AmberLightContainer,
    onTertiaryContainer = PenLabPrimitives.AmberLightOnContainer,
    error = PenLabPrimitives.RedLight,
    onError = PenLabPrimitives.Stone0,
    errorContainer = PenLabPrimitives.RedLightContainer,
    onErrorContainer = PenLabPrimitives.RedLightOnContainer,
    background = PenLabPrimitives.Stone50,
    onBackground = PenLabPrimitives.Stone900,
    surface = PenLabPrimitives.Stone0,
    onSurface = PenLabPrimitives.Stone900,
    surfaceVariant = PenLabPrimitives.Stone100,
    onSurfaceVariant = PenLabPrimitives.Stone500,
    surfaceContainerLowest = PenLabPrimitives.Stone0,
    surfaceContainerLow = PenLabPrimitives.Stone50,
    surfaceContainer = PenLabPrimitives.Stone0,
    surfaceContainerHigh = PenLabPrimitives.Stone75,
    surfaceContainerHighest = PenLabPrimitives.Stone100,
    outline = PenLabPrimitives.Stone300,
    outlineVariant = PenLabPrimitives.Stone200,
    inverseSurface = PenLabPrimitives.Stone850,
    inverseOnSurface = PenLabPrimitives.StoneOnDark,
    inversePrimary = PenLabPrimitives.CopperDark400,
    scrim = PenLabPrimitives.ScrimLight,
  )

private val CopperDarkScheme: ColorScheme =
  darkColorScheme(
    primary = PenLabPrimitives.CopperDark400,
    onPrimary = PenLabPrimitives.CopperDark950,
    primaryContainer = PenLabPrimitives.CopperDark900,
    onPrimaryContainer = PenLabPrimitives.CopperDark100,
    secondary = PenLabPrimitives.CopperDark400,
    onSecondary = PenLabPrimitives.CopperDark950,
    secondaryContainer = PenLabPrimitives.Stone750,
    onSecondaryContainer = PenLabPrimitives.StoneOnDark,
    tertiary = PenLabPrimitives.AmberDark,
    onTertiary = PenLabPrimitives.Stone950,
    tertiaryContainer = PenLabPrimitives.AmberDarkContainer,
    onTertiaryContainer = PenLabPrimitives.AmberDarkOnContainer,
    error = PenLabPrimitives.RedDark,
    onError = PenLabPrimitives.RedDarkOn,
    errorContainer = PenLabPrimitives.RedDarkContainer,
    onErrorContainer = PenLabPrimitives.RedDarkOnContainer,
    background = PenLabPrimitives.Stone900,
    onBackground = PenLabPrimitives.StoneOnDark,
    surface = PenLabPrimitives.Stone850,
    onSurface = PenLabPrimitives.StoneOnDark,
    surfaceVariant = PenLabPrimitives.Stone800,
    onSurfaceVariant = PenLabPrimitives.Stone450,
    surfaceContainerLowest = PenLabPrimitives.Stone950,
    surfaceContainerLow = PenLabPrimitives.Stone850,
    surfaceContainer = PenLabPrimitives.Stone800,
    surfaceContainerHigh = PenLabPrimitives.Stone750,
    surfaceContainerHighest = PenLabPrimitives.Stone700,
    outline = PenLabPrimitives.Stone650,
    outlineVariant = PenLabPrimitives.Stone700,
    inverseSurface = PenLabPrimitives.Stone100,
    inverseOnSurface = PenLabPrimitives.Stone900,
    inversePrimary = PenLabPrimitives.CopperLight600,
    scrim = PenLabPrimitives.ScrimDark,
  )

private val BlueprintLightExtended =
  PenLabExtendedColors(
    canvasBackdrop = PenLabPrimitives.Slate50,
    floatingChrome = PenLabPrimitives.Slate0,
    floatingChromeBorder = PenLabPrimitives.Slate200,
    toolSelectedRing = PenLabPrimitives.BlueLight500,
    toolSelectedFill = PenLabPrimitives.BlueLight100,
    iconDisabled = PenLabPrimitives.Slate500.copy(alpha = 0.42f),
    chipIdle = PenLabPrimitives.Slate100,
    searchMatch = PenLabPrimitives.AmberLightHighlight,
    searchMatchBg = PenLabPrimitives.AmberLightHighlight.copy(alpha = 0.28f),
    swatchEdge = PenLabPrimitives.SwatchEdgeLight,
    success = PenLabPrimitives.GreenLight,
    warning = PenLabPrimitives.AmberLight,
    isDark = false,
  )

private val BlueprintDarkExtended =
  PenLabExtendedColors(
    canvasBackdrop = PenLabPrimitives.Slate900,
    floatingChrome = PenLabPrimitives.Slate800,
    floatingChromeBorder = PenLabPrimitives.Slate700,
    toolSelectedRing = PenLabPrimitives.BlueDark400,
    toolSelectedFill = PenLabPrimitives.BlueDark900,
    iconDisabled = PenLabPrimitives.Slate450.copy(alpha = 0.42f),
    chipIdle = PenLabPrimitives.SlateOnDark.copy(alpha = 0.10f),
    searchMatch = PenLabPrimitives.AmberDark,
    searchMatchBg = PenLabPrimitives.AmberDark.copy(alpha = 0.32f),
    swatchEdge = PenLabPrimitives.SwatchEdgeDark,
    success = PenLabPrimitives.GreenDark,
    warning = PenLabPrimitives.AmberDark,
    isDark = true,
  )

private val CopperLightExtended =
  PenLabExtendedColors(
    canvasBackdrop = PenLabPrimitives.Stone50,
    floatingChrome = PenLabPrimitives.Stone0,
    floatingChromeBorder = PenLabPrimitives.Stone200,
    toolSelectedRing = PenLabPrimitives.CopperLight600,
    toolSelectedFill = PenLabPrimitives.CopperLight100,
    iconDisabled = PenLabPrimitives.Stone500.copy(alpha = 0.42f),
    chipIdle = PenLabPrimitives.Stone100,
    searchMatch = PenLabPrimitives.AmberLightHighlight,
    searchMatchBg = PenLabPrimitives.AmberLightHighlight.copy(alpha = 0.28f),
    swatchEdge = PenLabPrimitives.SwatchEdgeLight,
    success = PenLabPrimitives.GreenLight,
    warning = PenLabPrimitives.AmberLight,
    isDark = false,
  )

private val CopperDarkExtended =
  PenLabExtendedColors(
    canvasBackdrop = PenLabPrimitives.Stone900,
    floatingChrome = PenLabPrimitives.Stone800,
    floatingChromeBorder = PenLabPrimitives.Stone700,
    toolSelectedRing = PenLabPrimitives.CopperDark400,
    toolSelectedFill = PenLabPrimitives.CopperDark900,
    iconDisabled = PenLabPrimitives.Stone450.copy(alpha = 0.42f),
    chipIdle = PenLabPrimitives.StoneOnDark.copy(alpha = 0.10f),
    searchMatch = PenLabPrimitives.AmberDark,
    searchMatchBg = PenLabPrimitives.AmberDark.copy(alpha = 0.32f),
    swatchEdge = PenLabPrimitives.SwatchEdgeDark,
    success = PenLabPrimitives.GreenDark,
    warning = PenLabPrimitives.AmberDark,
    isDark = true,
  )

/** Resolves the Material 3 [ColorScheme] for any [AccentTheme] × Light/Dark combination. */
fun penLabColorScheme(accent: AccentTheme, isDark: Boolean): ColorScheme =
  when (accent) {
    AccentTheme.BLUEPRINT -> if (isDark) BlueprintDarkScheme else BlueprintLightScheme
    AccentTheme.COPPER -> if (isDark) CopperDarkScheme else CopperLightScheme
  }

/** Resolves the [PenLabExtendedColors] for any [AccentTheme] × Light/Dark combination. */
fun penLabExtendedColors(accent: AccentTheme, isDark: Boolean): PenLabExtendedColors =
  when (accent) {
    AccentTheme.BLUEPRINT -> if (isDark) BlueprintDarkExtended else BlueprintLightExtended
    AccentTheme.COPPER -> if (isDark) CopperDarkExtended else CopperLightExtended
  }

val LocalPenLabColors = staticCompositionLocalOf { BlueprintDarkExtended }

/**
 * Layer 3 — Component Aliases.
 *
 * Semantic component tokens for floating pills, toolbar cells, popovers, cards, chips, dialogs,
 * snackbars, and the contextual selection bar.
 */
object PenLabComponentTokens {
  // Floating top islands & center toolbar pill
  val chromePillBg: Color
    @Composable @ReadOnlyComposable get() = LocalPenLabColors.current.floatingChrome
  val chromePillBorder: Color
    @Composable @ReadOnlyComposable get() = LocalPenLabColors.current.floatingChromeBorder

  // Toolbar cells
  val toolCellSelectedBg: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer
  val toolCellSelectedRing: Color
    @Composable @ReadOnlyComposable get() = LocalPenLabColors.current.toolSelectedRing
  val toolIconActive: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onPrimaryContainer
  val toolIconDefault: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
  val toolIconDisabled: Color
    @Composable @ReadOnlyComposable get() = LocalPenLabColors.current.iconDisabled

  // Anchored popovers & tool panels
  val panelBg: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHigh
  // `outline`, not `outlineVariant`: on a light page the popover has only this hairline and a soft
  // shadow to separate it from the paper, and outlineVariant (about 1.4:1 on white) vanished (QA 26 Nov).
  // (Dark chrome draws no border: see floatingChromeSurface.)
  val panelBorder: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline

  // Library cards & folder tiles (Level 1 flat surface + hairline border)
  val cardBg: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
  val cardBorder: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant

  // Chips & segmented buttons
  val chipIdleBg: Color
    @Composable @ReadOnlyComposable get() = LocalPenLabColors.current.chipIdle
  val chipSelectedBg: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer
  val chipSelectedContent: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onPrimaryContainer

  // Segmented controls: the chosen segment is solid primary (clear >= 3:1 against the track),
  // not the pale container tint, which was too close to the track to read as "selected".
  val segmentSelectedBg: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
  val segmentSelectedContent: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onPrimary

  // Dialogs
  val dialogBg: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHigh
  val dialogScrim: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.scrim

  // Snackbars
  val snackbarBg: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.inverseSurface
  val snackbarContent: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.inverseOnSurface
  val snackbarAction: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.inversePrimary

  // Lasso selection floating bar
  val selectionBarBg: Color
    @Composable @ReadOnlyComposable get() = LocalPenLabColors.current.floatingChrome
  val selectionBarBorder: Color
    @Composable @ReadOnlyComposable get() = LocalPenLabColors.current.floatingChromeBorder
  val selectionBarDestructive: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
}

/**
 * Backward-compatible bridge to the active Light/Dark semantic tokens.
 *
 * Existing screens (including `DrawScope` canvas code that cannot read `CompositionLocal`) read
 * these properties and automatically react to Light/Dark and Blueprint/Copper switching with zero
 * per-read allocation. New components should prefer [MaterialTheme.colorScheme],
 * [LocalPenLabColors], and [PenLabComponentTokens].
 */
object PenLabColors {
  private val scheme: ColorScheme get() = penLabColorScheme(Accent.theme, Accent.isDark)
  private val extended: PenLabExtendedColors get() = penLabExtendedColors(Accent.theme, Accent.isDark)

  val toolbar: Color get() = scheme.surfaceContainer
  val hairline: Color get() = scheme.outlineVariant
  val icon: Color get() = scheme.onSurface
  // Used across legacy screens for both secondary copy and inactive icons; kept at >= 5:1 contrast.
  val iconDisabled: Color get() = scheme.onSurfaceVariant
  val accent: Color get() = scheme.primary
  val onAccent: Color get() = scheme.onPrimary
  val backdrop: Color get() = scheme.background
  val swatchEdge: Color get() = extended.swatchEdge
  val optionSelected: Color get() = extended.toolSelectedFill
  val chip: Color get() = extended.chipIdle
  val searchMatch: Color get() = extended.searchMatch
}

/**
 * Soft offset drop shadow for floating canvas chrome (one depth cue per surface).
 * Must precede [clip] in the modifier chain so the clip does not cut off the shadow.
 */
fun Modifier.softShadow(shape: Shape, elevation: Dp = 10.dp): Modifier {
  val isDark = Accent.isDark
  return this.shadow(
    elevation = if (isDark) elevation else (elevation * 0.7f),
    shape = shape,
    clip = false,
    ambientColor = if (isDark) PenLabPrimitives.ShadowAmbientDark else PenLabPrimitives.ShadowAmbientLight,
    spotColor = if (isDark) PenLabPrimitives.ShadowSpotDark else PenLabPrimitives.ShadowSpotLight,
  )
}

/**
 * Level 2 / Level 3 floating chrome & popover surface modifier.
 *
 * Enforces the single-depth-cue rule: uses a clean soft offset drop shadow without stacking a
 * heavy border ("ghost card"). On light theme where white floating chrome may sit over white
 * paper, pass [subtleLightHairline] = true for a crisp 0.5dp boundary.
 */
fun Modifier.floatingChromeSurface(
  shape: Shape,
  background: Color,
  elevation: Dp = 8.dp,
  subtleLightHairline: Boolean = false,
  borderColor: Color = Color.Transparent,
  isDark: Boolean = Accent.isDark,
): Modifier {
  val shadowed =
    this.shadow(
      elevation = if (isDark) elevation else (elevation * 0.75f),
      shape = shape,
      clip = false,
      ambientColor = if (isDark) PenLabPrimitives.ShadowAmbientDark else PenLabPrimitives.ShadowAmbientLight,
      spotColor = if (isDark) PenLabPrimitives.ShadowSpotDark else PenLabPrimitives.ShadowSpotLight,
    )
      .clip(shape)
      .background(background)
  return if (!isDark && subtleLightHairline && borderColor.alpha > 0f) {
    shadowed.border(0.5.dp, borderColor, shape)
  } else {
    shadowed
  }
}

/**
 * Level 1 flat card surface modifier: crisp 1dp hairline border with zero shadow (one depth cue).
 */
fun Modifier.flatCardSurface(
  shape: Shape,
  background: Color,
  borderColor: Color,
): Modifier =
  this.clip(shape)
    .background(background)
    .border(1.dp, borderColor, shape)
