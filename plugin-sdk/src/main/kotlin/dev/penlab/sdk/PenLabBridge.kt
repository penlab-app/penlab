package dev.penlab.sdk

/** Names and numbers PenLab and its plugin apps agree on (the bridge contract, version 2). */
object PenLabBridge {
  /** The intent action a plugin's exported service declares so PenLab can find it. */
  const val ACTION = "dev.penlab.PLUGIN"

  /** The `<meta-data>` name on that service; its resource is the raw JSON manifest (`@raw/penlab_plugin`). */
  const val META_MANIFEST = "dev.penlab.plugin.manifest"

  /** The bridge version. PenLab and a plugin must agree on it exactly. */
  const val API_VERSION = 2

  /**
   * Items format 1: the request lists the selected page items ([SelectionItem], with the original pictures as request files)
   * and a result item may name one it replaces ([ResultItem.replaces]). Sent as `itemsFormat` in the request; the bridge
   * version stays [API_VERSION] because everything is additive.
   */
  const val ITEMS_FORMAT = 1

  /** The most picture files PenLab sends with one call; further selected pictures are listed without a file. */
  const val MAX_ITEM_FILES = 8

  /** The largest picture file PenLab sends (bytes). */
  const val MAX_ITEM_FILE_BYTES = 32L * 1024 * 1024

  /** A picture PenLab sends is scaled down to this many pixels on its long side (smaller ones are sent as they are). */
  const val ITEM_PICTURE_MAX_SIDE = 2048

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

  /** Request files of the selected pictures are named `item-0`, `item-1`, ...; [SelectionItem.file] says which is which. */
  fun itemFileName(n: Int): String = "item-$n"

  /** Request file of an importer: the file the user picked. */
  const val FILE_SOURCE = "source"

  /** Result file name of a notebook's background PDF. */
  const val FILE_PDF = "pdf"

  // ---- Media (lecture recording): the host side of the "media" capability -----------------------------

  /** The version of [IPenLabHost], the interface PenLab offers a media plugin. */
  const val HOST_API_VERSION = 1

  /** PenLab's exported media service (class name; its package is [HOST_PACKAGE]). [PenLabHostClient] binds it. */
  const val HOST_MEDIA_SERVICE = "dev.penlab.app.plugins.host.MediaHostService"

  /**
   * `run` capability id of PenLab's recording controls (the pill and the "Record lecture" entry). The request JSON has
   * `command` = [MEDIA_START], [MEDIA_PAUSE], [MEDIA_RESUME] or [MEDIA_STOP]. The answer is a normal result: `none`, or `error` with a message.
   */
  const val CAP_MEDIA_COMMAND = "media:command"

  /** `run` capability id of a finger tap on marked ink: the request JSON has `ref` (the recording's reference) and `positionMs`; the plugin plays from there. */
  const val CAP_MEDIA_SEEK = "media:seek"

  const val MEDIA_START = "start"
  const val MEDIA_PAUSE = "pause"
  const val MEDIA_RESUME = "resume"
  const val MEDIA_STOP = "stop"

  /** The longest recording reference PenLab stores (characters). Longer, blank or control-character refs are refused (`mediaStarted` returns -1). */
  const val MAX_MEDIA_REF_LENGTH = 1024
}
