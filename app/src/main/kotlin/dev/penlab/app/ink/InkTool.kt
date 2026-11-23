package dev.penlab.app.ink

/**
 * Something the pen can do: a pen, a highlighter or the eraser, with its colour and size.
 *
 * [paperColor] is the colour on a white page (see [InkColors]); the eraser has none.
 * [sizeDp] is the stroke width for pens and highlighters, and the circle's radius for the
 * eraser. A highlighter is wide and see-through, and its strokes go underneath the ink so
 * writing stays readable. The eraser removes the strokes its circle touches.
 */
data class InkTool(
  val kind: Kind,
  val paperColor: Int,
  val sizeDp: Float,
  /** How a pen's line behaves. Ignored by the highlighter and eraser. */
  val style: PenStyle = PenStyle.PRESSURE,
  /** Eraser only: true cuts away just the part of a stroke under the circle; false removes whole strokes. */
  val partialErase: Boolean = false,
  /** Pens only: solid, dotted, dashed, centre or phantom line (see [LineType]). */
  val line: LineType = LineType.SOLID,
) {
  enum class Kind { PEN, HIGHLIGHTER, ERASER, LASSO, TEXT, FILL }

  /** Typed text boxes: tap the page to type; [sizeDp] is the text size in millimetres. */
  val isText: Boolean
    get() = kind == Kind.TEXT

  /** Selects strokes by drawing a loop round them, then moves or deletes them. */
  val isLasso: Boolean
    get() = kind == Kind.LASSO

  val isHighlighter: Boolean
    get() = kind == Kind.HIGHLIGHTER

  val isEraser: Boolean
    get() = kind == Kind.ERASER

  /** Tap inside an outline to colour it in. */
  val isFill: Boolean
    get() = kind == Kind.FILL
}

/** The kinds of pen. See [PenBrushes] for what each one does. */
enum class PenStyle(val label: String) {
  BALLPOINT("Pen"),
  PRESSURE("Pressure"),
  FOUNTAIN("Fountain"),
}

/** A colour the toolbar offers, with the name a screen reader says. */
data class NamedColor(val name: String, val paperColor: Int)

/** The toolbar's starting tools and the choices its panels offer. Change favourites here. */
object InkTools {
  /** Stroke widths in dp. Medium is about 0.35 mm on a typical tablet. */
  private val penSizes = listOf(1.4f, 2.2f, 3.6f)

  /** Highlighter widths in dp. Medium is about the height of a line of handwriting. */
  private val highlighterSizes = listOf(10f, 14f, 20f)

  /** Eraser circle radius in dp. Medium is a circle about 5 mm across. */
  private val eraserSizes = listOf(8f, 14f, 24f)

  val penColors =
    listOf(
      NamedColor("Black", 0xFF111111.toInt()),
      NamedColor("Grey", 0xFF6B7280.toInt()),
      NamedColor("Blue", 0xFF1565C0.toInt()),
      NamedColor("Teal", 0xFF00838F.toInt()),
      NamedColor("Green", 0xFF2E7D32.toInt()),
      NamedColor("Red", 0xFFD32F2F.toInt()),
      NamedColor("Orange", 0xFFEF6C00.toInt()),
      NamedColor("Purple", 0xFF6A1B9A.toInt()),
    )

  /** All at 40% opacity so the writing underneath shows through. */
  val highlighterColors =
    listOf(
      NamedColor("Yellow", 0x66FFD600),
      NamedColor("Green", 0x6600C853),
      NamedColor("Pink", 0x66FF4081),
      NamedColor("Blue", 0x6640C4FF),
    )

  /**
   * Fill colours: soft tints first, so ink drawn over a fill stays readable (on the dark page
   * they show as deep tints, inverted like ink), then two strong ones for solid shapes.
   */
  val fillColors =
    listOf(
      NamedColor("Sky", 0xFFCFE3FF.toInt()),
      NamedColor("Mint", 0xFFCFF2DC.toInt()),
      NamedColor("Butter", 0xFFFFF0B8.toInt()),
      NamedColor("Rose", 0xFFFFD6DE.toInt()),
      NamedColor("Lilac", 0xFFE4D9FF.toInt()),
      NamedColor("Grey", 0xFFDADCE0.toInt()),
      NamedColor("Blue", 0xFF2F6FDB.toInt()),
      NamedColor("Black", 0xFF111111.toInt()),
    )

  /** The size slider's range for each kind of tool, in dp. */
  fun sizeRange(kind: InkTool.Kind): ClosedFloatingPointRange<Float> =
    when (kind) {
      InkTool.Kind.PEN -> 0.5f..8f
      InkTool.Kind.HIGHLIGHTER -> 4f..40f
      InkTool.Kind.ERASER -> 4f..48f
      InkTool.Kind.LASSO -> 0f..0f
      InkTool.Kind.TEXT -> 2f..20f
      InkTool.Kind.FILL -> 0f..0f
    }

  /** Starting favourite sizes (the user can change them). */
  fun sizesFor(kind: InkTool.Kind): List<Float> =
    when (kind) {
      InkTool.Kind.PEN -> penSizes
      InkTool.Kind.HIGHLIGHTER -> highlighterSizes
      InkTool.Kind.ERASER -> eraserSizes
      InkTool.Kind.LASSO -> emptyList()
      InkTool.Kind.TEXT -> listOf(3.5f, 5f, 7f)
      InkTool.Kind.FILL -> emptyList()
    }

  fun colorsFor(kind: InkTool.Kind): List<NamedColor> =
    when (kind) {
      InkTool.Kind.PEN -> penColors
      InkTool.Kind.HIGHLIGHTER -> highlighterColors
      InkTool.Kind.ERASER, InkTool.Kind.LASSO -> emptyList()
      InkTool.Kind.TEXT -> penColors
      InkTool.Kind.FILL -> fillColors
    }

  /** What a screen reader calls the tool, e.g. "Blue pen, size 2.2". */
  fun describe(tool: InkTool): String {
    val size = if (tool.isLasso || tool.isFill) null else "size ${Math.round(tool.sizeDp * 10f) / 10f}"
    val color = colorsFor(tool.kind).firstOrNull { it.paperColor == tool.paperColor }?.name
    val name =
      when (tool.kind) {
        InkTool.Kind.PEN ->
          when (tool.style) {
            PenStyle.BALLPOINT -> "${color ?: "Custom"} pen"
            PenStyle.PRESSURE -> "${color ?: "Custom"} pressure pen"
            PenStyle.FOUNTAIN -> "${color ?: "Custom"} fountain pen"
          }
        InkTool.Kind.HIGHLIGHTER -> "${color ?: "Custom"} highlighter"
        InkTool.Kind.ERASER -> "Eraser"
        InkTool.Kind.LASSO -> "Lasso select"
        InkTool.Kind.TEXT -> "${color ?: "Custom"} text"
        InkTool.Kind.FILL -> "${color ?: "Custom"} fill"
      }
    return if (size == null) name else "$name, $size"
  }

  /** The toolbar as it starts: three pens, a highlighter, the eraser (all medium), the lasso. */
  /**
   * In the user's chosen order: the three pens (black on paper = white on the dark page, blue,
   * red) and the lasso first, where the wheel shows them; then eraser, text and, last, the
   * highlighter, which he rarely uses.
   */
  val defaults =
    listOf(
      InkTool(InkTool.Kind.PEN, penColors[0].paperColor, penSizes[1]),
      InkTool(InkTool.Kind.PEN, penColors[2].paperColor, penSizes[1]),
      InkTool(InkTool.Kind.PEN, penColors[5].paperColor, penSizes[1]),
      InkTool(InkTool.Kind.LASSO, 0, 0f),
      InkTool(InkTool.Kind.ERASER, 0, eraserSizes[1]),
      InkTool(InkTool.Kind.TEXT, penColors[0].paperColor, 5f),
      InkTool(InkTool.Kind.HIGHLIGHTER, highlighterColors[0].paperColor, highlighterSizes[1]),
      InkTool(InkTool.Kind.FILL, fillColors[0].paperColor, 0f),
    )
}
