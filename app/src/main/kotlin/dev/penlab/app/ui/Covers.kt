package dev.penlab.app.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Library covers: each notebook's first page as a small JPEG (`cover.jpg`, written when an edited
 * notebook is closed). Decoded off the main thread and kept in a small cache, so scrolling the
 * library never decodes on the UI thread twice.
 */
object Covers {
  private val cache = LruCache<String, ImageBitmap>(80)

  fun cached(id: String): ImageBitmap? = cache.get(id)

  /** Decodes [file] (worker thread); null if there is no cover yet. */
  fun load(id: String, file: File): ImageBitmap? {
    cache.get(id)?.let { return it }
    if (!file.exists()) return null
    val bitmap = BitmapFactory.decodeFile(file.path)?.also { it.prepareToDraw() } ?: return null
    return bitmap.asImageBitmap().also { cache.put(id, it) }
  }

  fun forget(id: String) {
    cache.remove(id)
  }
}
