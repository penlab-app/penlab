package dev.penlab.app.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** Resolved cover image together with whether a light-cover fallback needs lightness inversion. */
internal data class CoverVariant<T>(val image: T, val invertFallback: Boolean)

/**
 * Picks the cover variant to display:
 * - For plain notebooks on a dark page (`darkPage && !isPdf`), prefers the dark variant (`dark = true`)
 *   with `invertFallback = false`; if missing, falls back to the light variant (`dark = false`)
 *   with `invertFallback = true`.
 * - For light pages or PDFs, uses the light variant (`dark = false`) with `invertFallback = false`.
 */
internal inline fun <T : Any> resolveCoverVariant(
  darkPage: Boolean,
  isPdf: Boolean,
  fetch: (dark: Boolean) -> T?,
): CoverVariant<T>? {
  if (darkPage && !isPdf) {
    fetch(true)?.let { return CoverVariant(it, invertFallback = false) }
    fetch(false)?.let { return CoverVariant(it, invertFallback = true) }
    return null
  }
  return fetch(false)?.let { CoverVariant(it, invertFallback = false) }
}

/**
 * Library covers: each notebook's first page as a small JPEG (`cover.jpg` and `cover-dark.jpg`,
 * written when an edited notebook is closed). Decoded off the main thread and kept in a small
 * cache, so scrolling the library never decodes on the UI thread twice.
 */
object Covers {
  private val cache = LruCache<String, ImageBitmap>(80)

  internal fun cacheKey(id: String, dark: Boolean): String = if (dark) "$id#dark" else id

  fun cached(id: String, dark: Boolean = false): ImageBitmap? = cache.get(cacheKey(id, dark))

  /** Decodes [file] (worker thread); null if there is no cover yet. */
  fun load(id: String, file: File, dark: Boolean = false): ImageBitmap? {
    val key = cacheKey(id, dark)
    cache.get(key)?.let { return it }
    if (!file.exists()) return null
    val bitmap = BitmapFactory.decodeFile(file.path)?.also { it.prepareToDraw() } ?: return null
    return bitmap.asImageBitmap().also { cache.put(key, it) }
  }

  internal fun cachedVariant(id: String, darkPage: Boolean, isPdf: Boolean): CoverVariant<ImageBitmap>? =
    resolveCoverVariant(darkPage, isPdf) { dark -> cached(id, dark = dark) }

  internal fun loadVariant(
    id: String,
    darkPage: Boolean,
    isPdf: Boolean,
    coverFile: (dark: Boolean) -> File,
  ): CoverVariant<ImageBitmap>? =
    resolveCoverVariant(darkPage, isPdf) { dark -> load(id, coverFile(dark), dark = dark) }

  // Bumped whenever a cover is rewritten: a card that already looked for it and found none (the
  // cover is drawn after the library lists a new notebook) must look again, not wait to be reopened.
  private val revisions = mutableStateMapOf<String, Int>()

  /** Read in composition: changes when [id]'s cover file was written. */
  fun revision(id: String): Int = revisions[id] ?: 0

  private val main = android.os.Handler(android.os.Looper.getMainLooper())

  /**
   * Called from background threads (after a cover file is written). The bitmap cache is thread-safe; [revisions] is Compose
   * state read by the library, so it is bumped on the main thread (audit BUG-16: writing it from two threads could conflict).
   */
  fun forget(id: String) {
    cache.remove(cacheKey(id, false))
    cache.remove(cacheKey(id, true))
    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) revisions[id] = (revisions[id] ?: 0) + 1
    else main.post { revisions[id] = (revisions[id] ?: 0) + 1 }
  }
}
