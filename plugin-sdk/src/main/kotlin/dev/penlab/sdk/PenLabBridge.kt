package dev.penlab.sdk

/** Names and numbers PenLab and its plugin apps agree on (the bridge contract, version 2). */
object PenLabBridge {
  /** The intent action a plugin's exported service declares so PenLab can find it. */
  const val ACTION = "dev.penlab.PLUGIN"

  /** The `<meta-data>` name on that service; its resource is the raw JSON manifest (`@raw/penlab_plugin`). */
  const val META_MANIFEST = "dev.penlab.plugin.manifest"

  /** The bridge version. PenLab and a plugin must agree on it exactly. */
  const val API_VERSION = 2

  /** The only app allowed to call a plugin's service. */
  const val HOST_PACKAGE = "dev.penlab.app"

  /** Key of the result JSON in the Bundle `IPenLabPlugin.run` returns. */
  const val KEY_RESULT = "result"

  /** Item type: a typed text box. The item's [ResultItem.text] is the text. */
  const val TYPE_TEXT = "penlab.text"

  /** Item type: a picture (a photo). The picture is the item's file, as PNG or JPEG. */
  const val TYPE_IMAGE = "penlab.image"

  /** Item type: a picture that is ink (dark on transparent), shown light on the dark page like handwriting. */
  const val TYPE_INK_IMAGE = "penlab.ink-image"

  /** Request file: a PNG of exactly the selected ink and items (dark ink on transparent, at most 1200 px). */
  const val FILE_SELECTION_IMAGE = "selection.png"

  /** Request file: the selected strokes as [StrokesJson]. */
  const val FILE_STROKES = "strokes.json"

  /** Result file holding real strokes ([StrokesBinary]); the result JSON names it in `strokesFile`. */
  const val FILE_RESULT_STROKES = "strokes.bin"

  /** Request file of an importer: the file the user picked. */
  const val FILE_SOURCE = "source"

  /** Result file name of a notebook's background PDF. */
  const val FILE_PDF = "pdf"
}
