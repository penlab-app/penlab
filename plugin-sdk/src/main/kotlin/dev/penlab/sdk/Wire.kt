package dev.penlab.sdk

import java.io.File
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/*
 * The JSON that crosses the bridge. Every position is in millimetres of paper (see the PenLab plugin
 * spec). Large things (pictures, PDFs, stroke lists) are never inside the JSON: they are files, named
 * in the JSON and sent as ParcelFileDescriptors next to it.
 */

/** The JSON was not in the shape the contract describes. Thrown by every decoder here. */
class WireFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Where a call happens: the notebook, the page the user is on, and that page's size. */
class PageInfo(val notebookId: String, val pageIndex: Int, val widthMm: Float, val heightMm: Float, val darkPage: Boolean)

/**
 * What the user selected with the lasso. [boundsMm] is left, top, right, bottom. [imageFile] and
 * [strokesFile] name request files ([PenLabBridge.FILE_SELECTION_IMAGE], [PenLabBridge.FILE_STROKES]),
 * or are null when there is no such file.
 */
class SelectionInfo(
  val boundsMm: FloatArray,
  val strokeCount: Int,
  val itemCount: Int,
  val imageFile: String? = null,
  val strokesFile: String? = null,
)

/** The file an importer is asked to read; it is the request file named [file]. */
class SourceInfo(val displayName: String, val mimeType: String, val suggestedTitle: String, val file: String)

/** What PenLab sends to `run`: which capability, and the context it runs in. Fields not relevant to the capability are null. */
class PluginRequest(
  val capabilityId: String,
  val page: PageInfo? = null,
  val selection: SelectionInfo? = null,
  val source: SourceInfo? = null,
  /**
   * The newest [StrokesBinary] format the host reads (0 = it cannot read a strokes file: an older PenLab,
   * which would ignore `strokesFile` and place no ink). A plugin that returns strokes checks this first.
   */
  val strokesFormat: Int = 0,
) {
  fun toJson(): String =
    JSONObject().apply {
      put("apiVersion", PenLabBridge.API_VERSION)
      put("strokesFormat", strokesFormat)
      put("capabilityId", capabilityId)
      page?.let {
        put("page", JSONObject().put("notebookId", it.notebookId).put("pageIndex", it.pageIndex).put("widthMm", it.widthMm.toDouble()).put("heightMm", it.heightMm.toDouble()).put("darkPage", it.darkPage))
      }
      selection?.let {
        put("selection", JSONObject().put("boundsMm", floats(it.boundsMm)).put("strokeCount", it.strokeCount).put("itemCount", it.itemCount).putOpt("imageFile", it.imageFile).putOpt("strokesFile", it.strokesFile))
      }
      source?.let {
        put("source", JSONObject().put("displayName", it.displayName).put("mimeType", it.mimeType).put("suggestedTitle", it.suggestedTitle).put("file", it.file))
      }
    }.toString()

  companion object {
    /** Reads a request; throws [WireFormatException] if it is malformed. */
    fun fromJson(json: String): PluginRequest =
      wire {
        val o = JSONObject(json)
        PluginRequest(
          o.getString("capabilityId"),
          o.optJSONObject("page")?.let { PageInfo(it.getString("notebookId"), it.getInt("pageIndex"), it.getDouble("widthMm").toFloat(), it.getDouble("heightMm").toFloat(), it.optBoolean("darkPage")) },
          o.optJSONObject("selection")?.let {
            SelectionInfo(readFloats(it.getJSONArray("boundsMm"), 4), it.getInt("strokeCount"), it.getInt("itemCount"), it.optString("imageFile").ifEmpty { null }, it.optString("strokesFile").ifEmpty { null })
          },
          o.optJSONObject("source")?.let { SourceInfo(it.getString("displayName"), it.getString("mimeType"), it.getString("suggestedTitle"), it.getString("file")) },
          o.optInt("strokesFormat", 0),
        )
      }
  }
}

/**
 * One finished stroke. [xs], [ys], [pressures] have one entry per point (pressure 0..1, or -1 when the
 * pen gave none); [times] is milliseconds since the stroke began (empty when unknown: PenLab then
 * invents an even pace). [tool] is `PEN` or `HIGHLIGHTER`; [argb] the colour as stored (paper colours:
 * dark ink for a white page). [family] says which pen (see [StrokeFamily]; it defaults from [tool]) and
 * [layer] the 0-based layer, or -1 for the notebook's current layer.
 *
 * A plugin returns these in [PluginResult.Items.strokes] / [NotebookPage.strokes] (sent as the strokes
 * file, see [StrokesBinary]); PenLab also sends the selection's strokes this way in `strokes.json`.
 */
class SdkStroke(
  val xs: FloatArray,
  val ys: FloatArray,
  val pressures: FloatArray,
  val times: LongArray,
  val tool: String,
  val argb: Int,
  val widthMm: Float,
  val family: StrokeFamily = if (tool == "HIGHLIGHTER") StrokeFamily.HIGHLIGHTER else StrokeFamily.PRESSURE,
  val layer: Int = -1,
) {
  val pointCount: Int get() = xs.size
}

/** The strokes request file: `{"strokes":[{"tool","family","layer","argb","widthMm","xs","ys","pressures","times"}, ...]}` (`family` and `layer` are optional when reading). */
object StrokesJson {
  fun encode(strokes: List<SdkStroke>): String {
    val sb = StringBuilder("{\"strokes\":[")
    strokes.forEachIndexed { i, s ->
      if (i > 0) sb.append(',')
      sb.append("{\"tool\":").append(JSONObject.quote(s.tool)).append(",\"family\":").append(JSONObject.quote(s.family.id)).append(",\"layer\":").append(s.layer).append(",\"argb\":").append(s.argb).append(",\"widthMm\":").append(num(s.widthMm))
      sb.append(",\"xs\":"); floatsInto(sb, s.xs)
      sb.append(",\"ys\":"); floatsInto(sb, s.ys)
      sb.append(",\"pressures\":"); floatsInto(sb, s.pressures)
      sb.append(",\"times\":[")
      s.times.forEachIndexed { j, t -> if (j > 0) sb.append(','); sb.append(t) }
      sb.append("]}")
    }
    return sb.append("]}").toString()
  }

  fun decode(json: String): List<SdkStroke> =
    wire {
      val array = JSONObject(json).getJSONArray("strokes")
      List(array.length()) { i ->
        val o = array.getJSONObject(i)
        val times = o.getJSONArray("times")
        SdkStroke(readFloats(o.getJSONArray("xs")), readFloats(o.getJSONArray("ys")), readFloats(o.getJSONArray("pressures")), LongArray(times.length()) { times.getLong(it) }, o.getString("tool"), o.getInt("argb"), o.getDouble("widthMm").toFloat(), if (o.has("family")) StrokeFamily.fromId(o.getString("family")) else if (o.getString("tool") == "HIGHLIGHTER") StrokeFamily.HIGHLIGHTER else StrokeFamily.PRESSURE, o.optInt("layer", -1))
      }
    }

  private fun floatsInto(sb: StringBuilder, a: FloatArray) {
    sb.append('[')
    a.forEachIndexed { i, v -> if (i > 0) sb.append(','); sb.append(num(v)) }
    sb.append(']')
  }

  // JSON has no NaN or Infinity.
  private fun num(v: Float): String = if (v.isFinite()) v.toString() else "0"
}

/**
 * One thing a plugin asks PenLab to put on the page. [typeId] is [PenLabBridge.TYPE_TEXT],
 * [PenLabBridge.TYPE_IMAGE], [PenLabBridge.TYPE_INK_IMAGE], or a plugin's own id (shown as a grey box
 * if PenLab has no drawing for it). [boundsMm] is left, top, right, bottom. A plugin sets [text] or [data]
 * (picture bytes); when PenLab reads a result, [file] names the result file that holds the data.
 */
class ResultItem(
  val typeId: String,
  val boundsMm: FloatArray,
  val text: String? = null,
  val data: ByteArray? = null,
  val file: String? = null,
) {
  init {
    require(boundsMm.size == 4) { "boundsMm is left, top, right, bottom" }
  }

  companion object {
    /** A text box of [text] over [boundsMm]. */
    fun text(text: String, left: Float, top: Float, right: Float, bottom: Float) = ResultItem(PenLabBridge.TYPE_TEXT, floatArrayOf(left, top, right, bottom), text = text)

    /** A picture ([png] or JPEG bytes) over [boundsMm]; [ink] makes it ink (dark on transparent) instead of a photo. */
    fun image(png: ByteArray, left: Float, top: Float, right: Float, bottom: Float, ink: Boolean = false) =
      ResultItem(if (ink) PenLabBridge.TYPE_INK_IMAGE else PenLabBridge.TYPE_IMAGE, floatArrayOf(left, top, right, bottom), data = png)
  }
}

/**
 * One page of an imported notebook: the PDF page shown under it (-1 for plain paper), its items and its
 * [strokes] (real ink), all in millimetres from this page's top-left.
 */
class NotebookPage(val backgroundPdfPage: Int = -1, val items: List<ResultItem> = emptyList(), val strokes: List<SdkStroke> = emptyList())

/**
 * An importer's notebook. [backgroundPdf] (a file of the plugin's) is sent to PenLab, which moves
 * it into the new notebook; when PenLab reads the result, [backgroundPdfName] names that result file.
 */
class NotebookResult(
  val title: String,
  val pageWidthMm: Float,
  val pageHeightMm: Float,
  val pages: List<NotebookPage>,
  val backgroundPdf: File? = null,
  val backgroundPdfName: String? = null,
  /** When PenLab reads a result: the result file holding the pages' strokes (section i = page i, see [StrokesBinary]); the pages' [NotebookPage.strokes] are then empty until it is read. */
  val strokesName: String? = null,
)

/** What a capability hands back. */
sealed interface PluginResult {
  /**
   * Things to place on the page (one undo step): [items] (text, pictures) and [strokes] (real ink, in
   * notebook millimetres like the items). When PenLab reads a result, [strokesName] names the result
   * file that holds the strokes and [strokes] is empty.
   */
  class Items(val items: List<ResultItem>, val strokes: List<SdkStroke> = emptyList(), val strokesName: String? = null) : PluginResult

  /** Text to show the user. */
  class Text(val text: String) : PluginResult

  /** A short message. */
  class Message(val message: String) : PluginResult

  /** Nothing to do. */
  data object None : PluginResult

  /** An importer's notebook. */
  class Notebook(val notebook: NotebookResult) : PluginResult

  /** The plugin could not do it and says why. Not a crash: PenLab shows the message and keeps the plugin on. */
  class Error(val message: String) : PluginResult
}

/** A file in a result: bytes in memory, or a file on disk. */
sealed interface ResultFile {
  class Bytes(val bytes: ByteArray) : ResultFile

  class OnDisk(val file: File) : ResultFile
}

/** [json] plus the [files] it names. */
class EncodedResult(val json: String, val files: Map<String, ResultFile>)

/** Result JSON: `{"type":"items"|"text"|"message"|"none"|"notebook"|"error", ...}`. */
object ResultCodec {
  fun encode(result: PluginResult): EncodedResult {
    val files = LinkedHashMap<String, ResultFile>()
    val o = JSONObject()
    when (result) {
      is PluginResult.Items -> {
        o.put("type", "items").put("items", itemsJson(result.items, "item-", files))
        if (result.strokes.isNotEmpty()) {
          files[PenLabBridge.FILE_RESULT_STROKES] = ResultFile.Bytes(StrokesBinary.encode(result.strokes))
          o.put("strokesFile", PenLabBridge.FILE_RESULT_STROKES)
        }
      }
      is PluginResult.Text -> o.put("type", "text").put("text", result.text)
      is PluginResult.Message -> o.put("type", "message").put("message", result.message)
      PluginResult.None -> o.put("type", "none")
      is PluginResult.Error -> o.put("type", "error").put("message", result.message)
      is PluginResult.Notebook -> {
        val n = result.notebook
        o.put("type", "notebook").put("title", n.title).put("pageWidthMm", n.pageWidthMm.toDouble()).put("pageHeightMm", n.pageHeightMm.toDouble())
        n.backgroundPdf?.let {
          files[PenLabBridge.FILE_PDF] = ResultFile.OnDisk(it)
          o.put("pdfFile", PenLabBridge.FILE_PDF)
        }
        if (n.pages.any { it.strokes.isNotEmpty() }) {
          files[PenLabBridge.FILE_RESULT_STROKES] = ResultFile.Bytes(StrokesBinary.encodeSections(n.pages.map { it.strokes }))
          o.put("strokesFile", PenLabBridge.FILE_RESULT_STROKES)
        }
        val pages = JSONArray()
        n.pages.forEachIndexed { i, page ->
          pages.put(JSONObject().put("backgroundPdfPage", page.backgroundPdfPage).put("items", itemsJson(page.items, "p$i-item-", files)))
        }
        o.put("pages", pages)
      }
    }
    return EncodedResult(o.toString(), files)
  }

  /** Reads result JSON; throws [WireFormatException] for anything outside the contract (including an unknown type). */
  fun decode(json: String): PluginResult =
    wire {
      val o = JSONObject(json)
      when (val type = o.getString("type")) {
        "items" -> PluginResult.Items(readItems(o.getJSONArray("items")), strokesName = o.optString("strokesFile").ifEmpty { null })
        "text" -> PluginResult.Text(o.getString("text"))
        "message" -> PluginResult.Message(o.getString("message"))
        "none" -> PluginResult.None
        "error" -> PluginResult.Error(o.optString("message", "failed"))
        "notebook" -> {
          val pages = o.getJSONArray("pages")
          PluginResult.Notebook(
            NotebookResult(
              o.getString("title"),
              o.getDouble("pageWidthMm").toFloat(),
              o.getDouble("pageHeightMm").toFloat(),
              List(pages.length()) { i ->
                val p = pages.getJSONObject(i)
                NotebookPage(p.optInt("backgroundPdfPage", -1), readItems(p.optJSONArray("items") ?: JSONArray()))
              },
              backgroundPdfName = o.optString("pdfFile").ifEmpty { null },
              strokesName = o.optString("strokesFile").ifEmpty { null },
            )
          )
        }
        else -> throw WireFormatException("unknown result type \"$type\"")
      }
    }

  private fun itemsJson(items: List<ResultItem>, prefix: String, files: MutableMap<String, ResultFile>): JSONArray {
    val array = JSONArray()
    items.forEach { item ->
      val o = JSONObject().put("typeId", item.typeId).put("boundsMm", floats(item.boundsMm))
      item.text?.let { o.put("text", it) }
      val data = item.data
      if (data != null) {
        val name = "$prefix${files.size}"
        files[name] = ResultFile.Bytes(data)
        o.put("file", name)
      }
      array.put(o)
    }
    return array
  }

  private fun readItems(array: JSONArray): List<ResultItem> =
    List(array.length()) { i ->
      val o = array.getJSONObject(i)
      ResultItem(o.getString("typeId"), readFloats(o.getJSONArray("boundsMm"), 4), o.optString("text").ifEmpty { null }, null, o.optString("file").ifEmpty { null })
    }
}

private fun floats(a: FloatArray): JSONArray = JSONArray().also { array -> a.forEach { array.put(if (it.isFinite()) it.toDouble() else 0.0) } }

private fun readFloats(a: JSONArray, expected: Int = -1): FloatArray {
  if (expected >= 0 && a.length() != expected) throw WireFormatException("expected $expected numbers, got ${a.length()}")
  return FloatArray(a.length()) { a.getDouble(it).toFloat() }
}

/** Runs [block], turning a JSON error into a [WireFormatException]. */
private inline fun <T> wire(block: () -> T): T =
  try {
    block()
  } catch (e: JSONException) {
    throw WireFormatException(e.message ?: "bad JSON", e)
  } catch (e: IllegalArgumentException) {
    throw WireFormatException(e.message ?: "bad value", e)
  }
