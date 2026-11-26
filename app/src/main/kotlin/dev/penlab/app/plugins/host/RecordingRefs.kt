package dev.penlab.app.plugins.host

import dev.penlab.sdk.PenLabBridge

/**
 * The notebook's list of recordings (`recordings` in notebook.properties): opaque strings the plugin gave, joined by
 * the unit separator. A stroke's mark holds an INDEX into this list, so the one invariant is that an entry never moves:
 * entries are only appended, never removed or reordered, and an entry can never read back as two or none.
 * (The file reader splits on U+001F and drops empty entries, so a ref with that character, or an empty one, would
 * shift every later index. Such refs are refused here.)
 */
object RecordingRefs {
  /** True if [ref] may be stored: not blank, within [PenLabBridge.MAX_MEDIA_REF_LENGTH], no control characters (U+001F included). */
  fun isStorable(ref: String): Boolean = ref.isNotBlank() && ref.length <= PenLabBridge.MAX_MEDIA_REF_LENGTH && ref.none { it.code < 0x20 || it.code == 0x7F }

  /** The list after adding [ref] and the index it has: an existing entry with the same text is reused, else it is appended. Null if [ref] is not storable. */
  fun add(list: List<String>, ref: String): Pair<List<String>, Int>? {
    if (!isStorable(ref)) return null
    val existing = list.indexOf(ref)
    return if (existing >= 0) list to existing else (list + ref) to list.size
  }

  /** How the list is written in notebook.properties: entries joined by U+001F. */
  fun encode(list: List<String>): String = list.joinToString(SEPARATOR)

  /** The list as [encode] wrote it. Empty entries are dropped (as they always were), which is why [isStorable] refuses empty refs. */
  fun decode(text: String?): List<String> = text?.split(SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()

  private const val SEPARATOR = "\u001F"
}
