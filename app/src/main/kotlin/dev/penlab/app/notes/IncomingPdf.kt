package dev.penlab.app.notes

/**
 * Which PDF, if any, another app handed to PenLab ("Open with" from Files, "Share" from WhatsApp or
 * Drive). Pure logic over plain values, so it is unit-tested without Android: [U] is the Uri type.
 *
 * - ACTION_VIEW carries the file in the intent's data.
 * - ACTION_SEND carries it in EXTRA_STREAM.
 * Only a PDF counts (the manifest filter already says so; this refuses anything else that reaches
 * the activity). An intent that was already handled, or that only brings the app back from the
 * recent-apps list, gives null: rotating the tablet or reopening from history never imports twice.
 */
object IncomingPdf {
  const val ACTION_VIEW = "android.intent.action.VIEW"
  const val ACTION_SEND = "android.intent.action.SEND"
  const val MIME = "application/pdf"

  fun <U : Any> pick(
    action: String?,
    mimeType: String?,
    data: U?,
    stream: U?,
    handled: Boolean,
    fromHistory: Boolean,
  ): U? {
    if (handled || fromHistory) return null
    if (!MIME.equals(mimeType?.trim(), ignoreCase = true)) return null
    return when (action) {
      ACTION_VIEW -> data
      ACTION_SEND -> stream
      else -> null
    }
  }

  /** A name to give the importer: it decides the file's type from the extension, so keep ".pdf". */
  fun fileName(displayName: String?): String {
    val name = displayName?.trim().orEmpty().ifBlank { "Imported" }
    return if (name.endsWith(".pdf", ignoreCase = true)) name else "$name.pdf"
  }
}
