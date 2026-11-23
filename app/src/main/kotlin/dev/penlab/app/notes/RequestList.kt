package dev.penlab.app.notes

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One thing the user noticed: a bug to fix or a feature to add. */
data class Request(val done: Boolean, val kind: String, val time: String, val where: String, val text: String)

/**
 * The user's running list of bugs and feature ideas, written from the app's quick form.
 *
 * It is a plain Markdown checklist, `requests.md`, in the app's external files folder
 * (`/sdcard/Android/data/dev.penlab.app/files/`), so it can be read over USB without
 * special access and opened in any text editor:
 *
 * ```
 * - [ ] Bug · 2026-11-12 19:40 · Mechanics L7, page 2 · the eraser misses thin lines
 * - [x] Feature · 2026-11-11 09:05 · library · sort notebooks by name
 * ```
 *
 * Fixed items are ticked ([x]) rather than deleted, so the list doubles as a record.
 */
class RequestList(private val file: File) {

  fun add(kind: String, where: String, text: String) {
    val clean = text.trim().replace("\n", " ")
    if (clean.isEmpty()) return
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.UK).format(Date())
    file.parentFile?.mkdirs()
    file.appendText("- [ ] $kind · $time · ${where.ifBlank { "app" }} · $clean\n")
  }

  /** Newest first. */
  fun read(): List<Request> =
    if (!file.exists()) emptyList()
    else file.readLines().mapNotNull(::parse).asReversed()

  private fun parse(line: String): Request? {
    val match = LINE.matchEntire(line.trim()) ?: return null
    val (box, kind, time, where, text) = match.destructured
    return Request(box.equals("x", ignoreCase = true), kind, time, where, text)
  }

  private companion object {
    val LINE = Regex("""- \[([ xX])] (\w+) · ([^·]+?) · ([^·]+?) · (.*)""")
  }
}
