package dev.penlab.app.plugins.external

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.roundToInt

/** Implemented by external plugin capabilities that may carry a loaded `@drawable` menu icon. */
interface HasExternalIcon {
  val externalIcon: ImageBitmap?
}

/**
 * Loads and caches small menu icons declared as `@drawable/...` in an external plugin's manifest.
 * Runs off the main thread during plugin scanning so menu composition never hits PackageManager.
 */
object ExternalPluginIcons {
  const val MAX_ICON_PX = 96
  private const val TAG = "ExternalPluginIcons"

  private class CachedIcon(val bitmap: ImageBitmap?)

  private val cache = LruCache<String, CachedIcon>(64)

  /** Bounds drawable dimensions to at most [maxPx] on either side while preserving aspect ratio. */
  internal fun boundedSize(intrinsicW: Int, intrinsicH: Int, maxPx: Int = MAX_ICON_PX): Pair<Int, Int> {
    if (intrinsicW <= 0 || intrinsicH <= 0) return maxPx to maxPx
    val maxSide = maxOf(intrinsicW, intrinsicH)
    if (maxSide <= maxPx) return intrinsicW to intrinsicH
    val scale = maxPx.toFloat() / maxSide
    return (intrinsicW * scale).roundToInt().coerceIn(1, maxPx) to
      (intrinsicH * scale).roundToInt().coerceIn(1, maxPx)
  }

  fun clear() {
    synchronized(cache) { cache.evictAll() }
  }

  fun load(context: Context, packageName: String, drawableName: String): ImageBitmap? {
    val key = "$packageName/$drawableName"
    synchronized(cache) { cache.get(key) }?.let { return it.bitmap }
    val loaded =
      try {
        val res = context.packageManager.getResourcesForApplication(packageName)
        @Suppress("DiscouragedApi")
        val id = res.getIdentifier(drawableName, "drawable", packageName)
        if (id == 0) null
        else {
          val drawable = res.getDrawable(id, null)
          val (w, h) = boundedSize(drawable.intrinsicWidth, drawable.intrinsicHeight)
          val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
          val canvas = Canvas(bmp)
          drawable.setBounds(0, 0, w, h)
          drawable.draw(canvas)
          bmp.asImageBitmap()
        }
      } catch (t: Throwable) {
        Log.w(TAG, "Could not load icon $drawableName from $packageName", t)
        null
      }
    synchronized(cache) { cache.put(key, CachedIcon(loaded)) }
    return loaded
  }
}
