package dev.penlab.app.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Pictures of an endless canvas in square tiles, for when it is zoomed out over a lot of ink.
 *
 * The endless canvas has no pages to picture (see [PageRasters] for notebooks), so it is cut
 * into a grid of squares. Each square's ink is drawn once, in the background, into a
 * [TILE_PX]-pixel bitmap; while panning, the screen only shows those bitmaps. Squares come in
 * sizes that double ("levels"), chosen so a square shows at about 380 to 770 pixels: zooming
 * switches level, and until the new squares are ready the old level's squares stand in.
 *
 * Any change to the ink makes every square out of date (one counter, [version]); squares that
 * are out of date keep showing until their replacement arrives, with the newest strokes drawn
 * live on top meanwhile.
 */
class TileRasters(private val onReady: () -> Unit) {

  private class Tile(val bitmap: Bitmap, val version: Int)

  private val cache =
    object : LruCache<Long, Tile>(BUDGET_BYTES) {
      override fun sizeOf(key: Long, value: Tile) = value.bitmap.byteCount
    }

  /** Changes every time the ink changes. */
  var version = 0
    private set

  private val pending = HashMap<Long, Int>()
  private val worker = Executors.newSingleThreadExecutor { Thread(it, "penlab-tiles").apply { priority = Thread.NORM_PRIORITY - 1 } }
  private val main = Handler(Looper.getMainLooper())
  private val renderer by lazy { CanvasStrokeRenderer.create() }

  fun invalidateAll() {
    version++
  }

  /** The square size level for [zoom] (screen px per page unit): squares are 2^level units. */
  fun levelFor(zoom: Float): Int = (ln(TILE_PX / zoom.toDouble()) / ln(2.0)).roundToInt()

  fun sizeOf(level: Int): Float = Math.scalb(1f, level)

  private fun key(level: Int, ix: Int, iy: Int): Long =
    ((level + 64).toLong() shl 56) or ((ix.toLong() and 0xFFFFFFF) shl 28) or (iy.toLong() and 0xFFFFFFF)

  private val rect = RectF()

  /**
   * Draws every square of the current level over [visible] (page units) onto [canvas] (already
   * in page units). Missing squares are asked for, with the next bigger level's square standing
   * in. Returns true if every square drawn is up to date. [strokesIn] gives the strokes touching
   * a square, [build] a drawable stroke in screen colours (called on the worker).
   */
  fun draw(
    canvas: Canvas,
    visible: RectF,
    zoom: Float,
    strokesIn: (RectF) -> List<InkStroke>,
    build: (InkStroke) -> Stroke,
    paint: android.graphics.Paint,
  ): Boolean {
    val level = levelFor(zoom)
    val size = sizeOf(level)
    val x0 = floor(visible.left / size).toInt()
    val x1 = ceil(visible.right / size).toInt()
    val y0 = floor(visible.top / size).toInt()
    val y1 = ceil(visible.bottom / size).toInt()
    var allCurrent = true
    for (iy in y0 until y1) for (ix in x0 until x1) {
      rect.set(ix * size, iy * size, (ix + 1) * size, (iy + 1) * size)
      val k = key(level, ix, iy)
      val tile = cache.get(k)
      if (tile == null || tile.version != version) {
        allCurrent = false
        request(k, RectF(rect), strokesIn, build)
      }
      if (tile != null) {
        canvas.drawBitmap(tile.bitmap, null, rect, paint)
      } else {
        // Stand in with the bigger square (one level up) that contains this one, if there is one.
        val parent = cache.get(key(level + 1, Math.floorDiv(ix, 2), Math.floorDiv(iy, 2)))
        if (parent != null) {
          val half = TILE_PX / 2
          val sx = Math.floorMod(ix, 2) * half
          val sy = Math.floorMod(iy, 2) * half
          canvas.drawBitmap(parent.bitmap, android.graphics.Rect(sx, sy, sx + half, sy + half), rect, paint)
        }
      }
    }
    return allCurrent
  }

  private fun request(key: Long, area: RectF, strokesIn: (RectF) -> List<InkStroke>, build: (InkStroke) -> Stroke) {
    val v = version
    if (pending[key] == v) return
    pending[key] = v
    val list = strokesIn(area) // on the main thread, as the page is now
    worker.execute {
      val bitmap = Bitmap.createBitmap(TILE_PX, TILE_PX, Bitmap.Config.ARGB_8888)
      val scale = TILE_PX / area.width()
      val matrix = Matrix().apply {
        setTranslate(-area.left, -area.top)
        postScale(scale, scale)
      }
      val canvas = Canvas(bitmap)
      canvas.concat(matrix)
      for (stroke in list) renderer.draw(canvas, build(stroke), matrix)
      main.post {
        if (pending[key] == v) pending.remove(key)
        cache.put(key, Tile(bitmap, v))
        onReady()
      }
    }
  }

  companion object {
    const val TILE_PX = 512

    /** Use squares when more strokes than this are on screen; go back below [EXIT_STROKES]. */
    const val ENTER_STROKES = 1800
    const val EXIT_STROKES = 1200

    private const val BUDGET_BYTES = 80 * 1024 * 1024
  }
}
