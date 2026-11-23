package dev.penlab.app.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import java.util.concurrent.Executors

/**
 * Pictures of each page's ink, for when the notebook is zoomed out.
 *
 * Zoomed out, a screen can show ten pages and 4000 strokes; drawing them one by one each frame
 * made scrolling stutter (48 ms frames at 23% in the 100-page stress test). When pages are small
 * on screen, [InkCanvasView] draws these pictures instead: one bitmap per page, rendered on a
 * background thread at about the size the page shows. A page whose ink changed keeps showing its
 * old picture (plus the new strokes drawn live) until the new one is ready, a few frames later.
 *
 * Only ink is in the picture; paper, ruling, PDF pages and photos are still drawn live.
 */
class PageRasters(private val onReady: (page: Int, version: Int) -> Unit) {

  private class Raster(val bitmap: Bitmap, val version: Int)

  private val cache =
    object : LruCache<Int, Raster>(BUDGET_BYTES) {
      override fun sizeOf(key: Int, value: Raster) = value.bitmap.byteCount
    }

  /** Page → how many times its ink has changed; a picture is current if its version matches. */
  private val versions = HashMap<Int, Int>()

  /** Pages being rendered now (page → the version and width asked for). */
  private val pending = HashMap<Int, Pair<Int, Int>>()

  private val worker = Executors.newSingleThreadExecutor { Thread(it, "penlab-pages").apply { priority = Thread.NORM_PRIORITY - 1 } }
  private val main = Handler(Looper.getMainLooper())
  private val renderer by lazy { CanvasStrokeRenderer.create() }

  fun versionOf(page: Int): Int = versions[page] ?: 0

  /** The ink on [page] changed. */
  fun invalidate(page: Int) {
    versions[page] = versionOf(page) + 1
  }

  /** Everything changed (pages inserted or deleted renumber them all). */
  fun invalidateAll() {
    for (page in versions.keys.toList()) invalidate(page)
    cache.evictAll()
  }

  /**
   * The newest picture of [page] (null if none yet) and whether it is current at about
   * [widthPx]. Asks for a fresh one in the background if not; [strokes] gives that page's
   * strokes in screen colours and is called only then.
   */
  fun get(
    page: Int,
    widthPx: Int,
    pageTop: Float,
    pageWidth: Float,
    pageHeight: Float,
    strokes: () -> List<InkStroke>,
    build: (InkStroke) -> Stroke,
  ): Pair<Bitmap?, Boolean> {
    val raster = cache.get(page)
    val wanted = bucket(widthPx)
    val current = raster != null && raster.version == versionOf(page) && raster.bitmap.width == wanted
    if (!current) request(page, wanted, pageTop, pageWidth, pageHeight, strokes, build)
    return raster?.bitmap to (raster != null && raster.version == versionOf(page))
  }

  /** True if the newest picture of [page] shows its ink as it is now. */
  fun isCurrent(page: Int): Boolean = cache.get(page)?.version == versionOf(page)

  private fun request(page: Int, width: Int, pageTop: Float, pageWidth: Float, pageHeight: Float, strokes: () -> List<InkStroke>, build: (InkStroke) -> Stroke) {
    val version = versionOf(page)
    if (pending[page] == version to width) return
    pending[page] = version to width
    val list = strokes() // on the main thread: the page's strokes as they are now
    worker.execute {
      val scale = width / pageWidth
      val bitmap = Bitmap.createBitmap(width, (pageHeight * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      val matrix = Matrix().apply {
        setTranslate(0f, -pageTop) // strokes are in notebook coordinates; the picture starts at the page top
        postScale(scale, scale)
      }
      canvas.concat(matrix)
      // Built here and not cached, so a zoomed-out notebook never fills memory with meshes.
      val started = android.os.SystemClock.uptimeMillis()
      for (stroke in list) renderer.draw(canvas, build(stroke), matrix)
      android.util.Log.i("PenLabPerf", "page $page picture: ${list.size} strokes, ${width}px, ${android.os.SystemClock.uptimeMillis() - started} ms")
      main.post {
        if (pending[page] == version to width) pending.remove(page)
        // Keep it even if the ink changed meanwhile: an almost-current picture beats none.
        cache.put(page, Raster(bitmap, version))
        onReady(page, version)
      }
    }
  }

  /** Picture widths come in a few sizes, so small zoom changes reuse the picture. */
  private fun bucket(widthPx: Int): Int =
    when {
      widthPx <= 256 -> 256
      widthPx <= 512 -> 512
      widthPx <= 768 -> 768
      else -> 1024
    }

  companion object {
    /** Pages narrower than this on screen are drawn from pictures. */
    const val RASTER_BELOW_PX = 1000

    private const val BUDGET_BYTES = 96 * 1024 * 1024
  }
}
