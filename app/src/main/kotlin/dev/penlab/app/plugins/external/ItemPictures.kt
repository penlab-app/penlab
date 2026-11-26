package dev.penlab.app.plugins.external

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.penlab.sdk.PenLabBridge
import java.io.File

/**
 * Prepares the original picture of a selected photo for a plugin app (bridge items format 1): the notebook's file
 * is copied as it is when its long side is at most [PenLabBridge.ITEM_PICTURE_MAX_SIDE] px, and scaled down to that
 * size otherwise (a 12 MP photo would only slow down the Binder call and the plugin's own decoding). Runs on a worker
 * thread; the decisions that need no Android ([sniffMime], [scaledSize]) are plain functions and unit tested.
 */
object ItemPictures {
  /** The picture's MIME type from its first bytes; `application/octet-stream` if it is not one PenLab knows. */
  fun sniffMime(head: ByteArray): String =
    when {
      head.size >= 8 && head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() && head[2] == 'N'.code.toByte() && head[3] == 'G'.code.toByte() -> "image/png"
      head.size >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte() -> "image/jpeg"
      head.size >= 4 && head[0] == 'G'.code.toByte() && head[1] == 'I'.code.toByte() && head[2] == 'F'.code.toByte() && head[3] == '8'.code.toByte() -> "image/gif"
      head.size >= 12 && String(head, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(head, 8, 4, Charsets.ISO_8859_1) == "WEBP" -> "image/webp"
      head.size >= 2 && head[0] == 'B'.code.toByte() && head[1] == 'M'.code.toByte() -> "image/bmp"
      else -> "application/octet-stream"
    }

  /** The size a [width] x [height] picture is sent at: unchanged if its long side is at most [maxSide], else scaled to it (never below 1 px). */
  fun scaledSize(width: Int, height: Int, maxSide: Int = PenLabBridge.ITEM_PICTURE_MAX_SIDE): Pair<Int, Int> {
    val longSide = maxOf(width, height)
    if (longSide <= maxSide) return width to height
    val f = maxSide.toFloat() / longSide
    return maxOf(1, Math.round(width * f)) to maxOf(1, Math.round(height * f))
  }

  /**
   * Writes the picture of [source] to [dest] and returns the MIME type of what was written, or null when it cannot be
   * sent (missing, empty, or larger than [PenLabBridge.MAX_ITEM_FILE_BYTES] and not reducible).
   */
  fun prepare(source: File, dest: File): String? {
    if (!source.isFile || source.length() <= 0L) return null
    val head = ByteArray(16)
    val read = source.inputStream().use { it.read(head) }
    val mime = sniffMime(head.copyOf(maxOf(read, 0)))
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source.path, bounds)
    if (bounds.outWidth > 0 && bounds.outHeight > 0 && maxOf(bounds.outWidth, bounds.outHeight) > PenLabBridge.ITEM_PICTURE_MAX_SIDE) {
      scaledMime(source, dest, bounds.outWidth, bounds.outHeight, mime)?.let { return it }
    }
    if (source.length() > PenLabBridge.MAX_ITEM_FILE_BYTES) return null
    source.copyTo(dest, overwrite = true)
    return mime
  }

  /** Decodes [source] sub-sampled, scales it to [scaledSize] and writes it (PNG when the source is PNG or has transparency, else JPEG). Null if that fails. */
  private fun scaledMime(source: File, dest: File, width: Int, height: Int, sourceMime: String): String? {
    val (w, h) = scaledSize(width, height)
    var sample = 1
    while (width / (sample * 2) >= w && height / (sample * 2) >= h) sample *= 2
    val decoded =
      try {
        BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
      } catch (e: OutOfMemoryError) {
        null
      } ?: return null
    val scaled = if (decoded.width == w && decoded.height == h) decoded else Bitmap.createScaledBitmap(decoded, w, h, true)
    try {
      val png = sourceMime == "image/png" || scaled.hasAlpha()
      dest.outputStream().use { scaled.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (png) 100 else 92, it) }
      return if (png) "image/png" else "image/jpeg"
    } finally {
      if (scaled !== decoded) scaled.recycle()
      decoded.recycle()
    }
  }
}
