package dev.penlab.app.plugins.external

import dev.penlab.app.plugins.ScreenPlacement
import dev.penlab.sdk.PenLabBridge
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Whether a page action needs ink selected. */
enum class SelectionRule { REQUIRED, NONE, ANY }

/** One capability a plugin app declares in its manifest JSON. */
sealed interface CapabilityDecl {
  val id: String
  val label: String

  /** The manifest's `kind`, for display. */
  val kind: String

  class PageActionDecl(override val id: String, override val label: String, val selection: SelectionRule) : CapabilityDecl {
    override val kind get() = "pageAction"
  }

  class ImporterDecl(override val id: String, override val label: String, val mimeTypes: List<String>, val extensions: List<String>) : CapabilityDecl {
    override val kind get() = "importer"
  }

  /** [activity] is as written in the manifest: a full class name, or one starting with "." relative to the plugin's package. */
  class ScreenDecl(override val id: String, override val label: String, val activity: String, val placement: ScreenPlacement) : CapabilityDecl {
    override val kind get() = "screen"
  }

  /** Read but not hosted yet. */
  class RecogniserDecl(override val id: String, override val label: String) : CapabilityDecl {
    override val kind get() = "recogniser"
  }

  /** Read but not hosted yet. */
  class MediaDecl(override val id: String, override val label: String) : CapabilityDecl {
    override val kind get() = "media"
  }
}

/** A plugin app's manifest JSON, read. */
class ExternalManifest(
  val id: String,
  val name: String,
  val apiVersion: Int,
  val capabilities: List<CapabilityDecl>,
  /** What the plugin says it needs (e.g. "network", "microphone"); PenLab only lists it. */
  val permissions: List<String>,
)

/** The outcome of reading a manifest: usable, or the reason it is not. */
sealed interface ManifestParse {
  class Ok(val manifest: ExternalManifest) : ManifestParse

  class Rejected(val reason: String) : ManifestParse
}

/**
 * Reads the `dev.penlab.plugin.manifest` JSON of a plugin app. Lenient where the contract says so:
 * unknown capability kinds are ignored, and so are capabilities that lack an id or a label or have
 * an unknown value. Strict about the plugin as a whole: the id must be plain, and `apiVersion` must
 * be exactly [PenLabBridge.API_VERSION].
 */
object ManifestParser {
  private val plainId = Regex("[A-Za-z0-9._-]{1,128}")

  fun parse(json: String): ManifestParse {
    val o =
      try {
        JSONObject(json)
      } catch (e: JSONException) {
        return ManifestParse.Rejected("the manifest is not valid JSON")
      }
    val id = o.optString("id")
    if (!plainId.matches(id)) return ManifestParse.Rejected("the plugin id is missing or not plain text")
    val name = o.optString("name").trim()
    if (name.isEmpty()) return ManifestParse.Rejected("the plugin has no name")
    if (!o.has("apiVersion")) return ManifestParse.Rejected("no apiVersion")
    val version = o.optInt("apiVersion", -1)
    if (version != PenLabBridge.API_VERSION) return ManifestParse.Rejected("needs bridge API $version, this PenLab speaks ${PenLabBridge.API_VERSION}")
    val seen = HashSet<String>()
    val capabilities = ArrayList<CapabilityDecl>()
    val array = o.optJSONArray("capabilities") ?: JSONArray()
    for (i in 0 until array.length()) {
      val c = array.optJSONObject(i) ?: continue
      val decl = capability(c) ?: continue
      if (seen.add(decl.kind + "/" + decl.id)) capabilities += decl
    }
    return ManifestParse.Ok(ExternalManifest(id, name, version, capabilities, strings(o.optJSONArray("permissions"))))
  }

  private fun capability(c: JSONObject): CapabilityDecl? {
    val id = c.optString("id")
    val label = c.optString("label").trim()
    if (!plainId.matches(id) || label.isEmpty()) return null
    return when (c.optString("kind")) {
      "pageAction" ->
        CapabilityDecl.PageActionDecl(
          id, label,
          when (c.optString("selection", "any")) {
            "required" -> SelectionRule.REQUIRED
            "none" -> SelectionRule.NONE
            "any" -> SelectionRule.ANY
            else -> return null
          },
        )
      "importer" ->
        CapabilityDecl.ImporterDecl(id, label, strings(c.optJSONArray("mimeTypes")), strings(c.optJSONArray("extensions")).map { it.removePrefix(".").lowercase() }.filter { it.isNotEmpty() })
      "screen" -> {
        val activity = c.optString("activity").trim()
        if (activity.isEmpty()) return null
        val placement =
          when (c.optString("placement", "moreMenu")) {
            "selectionMenu" -> ScreenPlacement.SELECTION_MENU
            "moreMenu" -> ScreenPlacement.MORE_MENU
            "fileMenu" -> ScreenPlacement.FILE_MENU
            else -> return null
          }
        CapabilityDecl.ScreenDecl(id, label, activity, placement)
      }
      "recogniser" -> CapabilityDecl.RecogniserDecl(id, label)
      "media" -> CapabilityDecl.MediaDecl(id, label)
      else -> null // a kind this PenLab does not know: ignored, as the contract says
    }
  }

  private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else List(a.length()) { a.optString(it) }.filter { it.isNotBlank() }
}
