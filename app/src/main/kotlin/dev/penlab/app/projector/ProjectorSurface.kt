package dev.penlab.app.projector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.HandlerThread
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import dev.penlab.app.ink.InkCanvasView
import dev.penlab.app.ink.InkColors
import dev.penlab.app.ink.InkStroke
import dev.penlab.app.ink.ItemPainter
import dev.penlab.app.ink.PageBackground
import dev.penlab.app.ink.PageItem
import dev.penlab.app.ink.PageStyle
import java.io.File
import java.util.IdentityHashMap

/** A visible page slice in a [ProjectorSnapshot] (page units). */
class ProjectorPage(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
  val isPdf: Boolean,
  val pdfBitmap: Bitmap?,
)

/** A visible non-ink page item in a [ProjectorSnapshot] along with whether it sits on dark paper. */
class ProjectorItem(val item: PageItem, val onDarkPage: Boolean)

/**
 * Immutable hand-off from the tablet's main thread to the projector's render thread.
 * Everything here is either a primitive, a never-mutated array/list, or an immutable ink [InkStroke] /
 * [PageItem], so the render thread can read it without locks.
 */
class ProjectorSnapshot(
  /** page -> projector pixels (9 values), null until the surface has a size. */
  val matrix: FloatArray?,
  val deskColor: Int,
  /** Paper colour for plain pages; 0 on an endless canvas (the desk colour is the page). */
  val pageColor: Int,
  val pages: List<ProjectorPage> = emptyList(),
  val strokes: List<ProjectorInk>,
  val items: List<ProjectorItem> = emptyList(),
  val imagesDir: File? = null,
  /** Live stroke points x,y,x,y... (page units) or null when no stroke is live. */
  val livePoints: FloatArray?,
  val liveCount: Int,
  val liveColor: Int,
  val liveSize: Float,
  /** Strokes whose pen has lifted but whose committed version is not in [strokes] yet; drawn like the live line. */
  val pendingLines: List<dev.penlab.app.ink.PendingLine> = emptyList(),
  /** Paper ruling; null style = none (e.g. empty snapshot). */
  val pageStyle: PageStyle? = null,
  val spacingMm: Float = 0f,
  val unitsPerDp: Float = 1f,
  val darkPage: Boolean = false,
  /** Page size in page units (Cornell etc.); 0 on an endless canvas. */
  val pageWidth: Float = 0f,
  val pageHeight: Float = 0f,
  /** The visible area in page units: left,top,right,bottom. */
  val visible: FloatArray? = null,
  /** Projector pixels per page unit. */
  val zoom: Float = 1f,
  /** Page edge colour (ARGB) and its width in projector pixels. */
  val edgeColor: Int = 0,
  val edgeWidthPx: Float = 1f,
  /** Monotonic version bumped whenever static scene contents change on the main thread. */
  val sceneVersion: Long = 0L,
)

/** A committed stroke for the projector: the immutable ink, drawn as a round polyline in its screen colour. */
class ProjectorInk(val ink: InkStroke, val color: Int, val size: Float, val highlighter: Boolean)

/** What the canvas publishes to. */
interface ProjectorSink {
  val targetWidth: Int
  val targetHeight: Int
  fun onSnapshot(s: ProjectorSnapshot)
}

/**
 * The projector's own surface with its own render thread (HandlerThread + Choreographer on that
 * thread), so projector frames never wait for the tablet's UI thread or RenderThread queue.
 * Draws only when a new snapshot arrived or a stroke is live, caching the committed static scene
 * during live writing so frames stay fast even on dense pages.
 */
internal class ProjectorSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, ProjectorSink {
  @Volatile override var targetWidth = 0
  @Volatile override var targetHeight = 0
  @Volatile private var snapshot: ProjectorSnapshot? = null

  /** The tablet canvas being followed; main thread only. Used to ask for a republish on resize. */
  var source: InkCanvasView? = null

  private val thread = HandlerThread("PenLabProjector").also { it.start() }
  private val handler = Handler(thread.looper)
  private var choreographer: Choreographer? = null // created ON the render thread
  private var surface: Surface? = null // render thread only
  private var scheduled = false // render thread only
  private var lastDrawn: ProjectorSnapshot? = null // render thread only

  // Render thread only: one Path per committed stroke, built once, plus cached static scene bitmap.
  private val pathCache = IdentityHashMap<InkStroke, android.graphics.Path>()
  private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }
  private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
  private val pdfBitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
  private val whitePaint = Paint().apply { color = Color.WHITE }
  private val ruled = RectF()
  private val vis = RectF()
  private var background: PageBackground? = null
  private var backgroundKey: List<Any>? = null
  private var itemPainter: ItemPainter? = null
  private var itemPainterDir: File? = null
  private val cacheTracker = StaticCacheTracker()
  private var staticCache: Bitmap? = null
  private var staticCacheCanvas: Canvas? = null

  private fun itemPainterFor(dir: File?): ItemPainter {
    var painter = itemPainter
    if (painter == null || dir != itemPainterDir) {
      painter = ItemPainter(dir)
      itemPainter = painter
      itemPainterDir = dir
    }
    return painter
  }

  private fun ensureStaticCache(width: Int, height: Int): Bitmap? {
    val existing = staticCache
    if (existing != null && !existing.isRecycled && existing.width == width && existing.height == height) {
      return existing
    }
    staticCache?.recycle()
    staticCache = null
    staticCacheCanvas = null
    return try {
      val created = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      staticCache = created
      staticCacheCanvas = Canvas(created)
      created
    } catch (_: OutOfMemoryError) {
      staticCache = null
      staticCacheCanvas = null
      null
    }
  }

  private fun releaseRenderResources() {
    cacheTracker.invalidate()
    staticCache?.recycle()
    staticCache = null
    staticCacheCanvas = null
    itemPainter = null
    itemPainterDir = null
  }

  private fun backgroundFor(s: ProjectorSnapshot): PageBackground {
    val key = listOf(s.unitsPerDp, s.darkPage, s.pageWidth, s.pageHeight)
    var b = background
    if (b == null || key != backgroundKey) {
      b = PageBackground(s.unitsPerDp, s.darkPage)
      b.pageWidth = s.pageWidth
      b.pageHeight = s.pageHeight
      background = b
      backgroundKey = key
    }
    b.spacingMm = s.spacingMm
    return b
  }

  private fun pathOf(ink: InkStroke): android.graphics.Path {
    pathCache[ink]?.let { return it }
    val p = android.graphics.Path()
    val n = ink.pointCount
    if (n > 0) {
      p.moveTo(ink.x(0), ink.y(0))
      if (n == 1) p.lineTo(ink.x(0) + 0.01f, ink.y(0))
      for (i in 1 until n) p.lineTo(ink.x(i), ink.y(i))
    }
    if (pathCache.size > 4000) pathCache.clear()
    pathCache[ink] = p
    return p
  }
  private val matrix = Matrix()
  private val paint = Paint()
  private val rect = RectF()
  private val liveStroke = android.graphics.Path()
  private val livePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }

  init {
    holder.addCallback(this)
    handler.post { choreographer = Choreographer.getInstance() }
  }

  override fun onSnapshot(s: ProjectorSnapshot) {
    snapshot = s
    handler.post(poke)
  }

  private val poke = Runnable { schedule() }

  private fun schedule() {
    val c = choreographer ?: return
    if (scheduled || surface == null) return
    scheduled = true
    c.postFrameCallback(frame)
  }

  private val frame = Choreographer.FrameCallback {
    scheduled = false
    val s = snapshot ?: return@FrameCallback
    val surf = surface ?: return@FrameCallback
    val live = s.livePoints != null
    if (!live && s === lastDrawn) return@FrameCallback
    draw(surf, s)
    lastDrawn = s
    if (live) schedule()
  }

  private fun drawStaticScene(canvas: Canvas, s: ProjectorSnapshot) {
    canvas.drawColor(s.deskColor)
    val m = s.matrix ?: return
    matrix.setValues(m)
    val style = s.pageStyle
    val v = s.visible
    if (v != null) vis.set(v[0], v[1], v[2], v[3])
    canvas.save()
    canvas.concat(matrix)
    if (s.pages.isNotEmpty()) {
      for (p in s.pages) {
        rect.set(p.left, p.top, p.right, p.bottom)
        if (p.isPdf) {
          canvas.drawRect(rect, whitePaint)
          val bmp = p.pdfBitmap
          if (bmp != null && !bmp.isRecycled) {
            canvas.drawBitmap(bmp, null, rect, pdfBitmapPaint)
          }
        } else {
          if (s.pageColor != 0) {
            paint.color = s.pageColor
            canvas.drawRect(rect, paint)
          }
          if (style != null && v != null) {
            canvas.save()
            canvas.clipRect(rect)
            canvas.translate(0f, rect.top)
            ruled.set(vis)
            ruled.intersect(rect)
            ruled.offset(0f, -rect.top)
            backgroundFor(s).draw(canvas, style, ruled, s.zoom)
            canvas.restore()
          }
        }
        if (s.edgeColor != 0) {
          edgePaint.color = s.edgeColor
          edgePaint.strokeWidth = s.edgeWidthPx / s.zoom
          canvas.drawRect(rect, edgePaint)
        }
      }
    } else if (s.pageColor == 0 && style != null && v != null) {
      backgroundFor(s).draw(canvas, style, vis, s.zoom)
    }
    if (s.items.isNotEmpty()) {
      val painter = itemPainterFor(s.imagesDir)
      for (pi in s.items) {
        painter.draw(canvas, pi.item, if (pi.onDarkPage) DARK_COLOR_MAP else LIGHT_COLOR_MAP)
      }
    }
    // Committed ink as round polylines (Ink's renderer draws nothing on this surface). Highlighters come first in the list.
    for (pi in s.strokes) {
      inkPaint.color = pi.color
      inkPaint.strokeWidth = pi.size
      canvas.drawPath(pathOf(pi.ink), inkPaint)
    }
    canvas.restore()
  }

  private fun draw(surf: Surface, s: ProjectorSnapshot) {
    val canvas: Canvas = try {
      surf.lockHardwareCanvas()
    } catch (e: Exception) {
      return
    }
    try {
      val m = s.matrix
      if (m == null) {
        canvas.drawColor(s.deskColor)
        return
      }
      val pts = s.livePoints
      val hasLive = (pts != null && s.liveCount > 0) || s.pendingLines.isNotEmpty()
      val mode =
        if (maxCacheDimensionOk(canvas.width, canvas.height)) {
          cacheTracker.decide(canvas.width, canvas.height, s.sceneVersion, m, hasLive)
        } else {
          cacheTracker.invalidate()
          ProjectorDrawMode.DIRECT
        }
      when (mode) {
        ProjectorDrawMode.USE_CACHE -> {
          val bmp = staticCache
          if (bmp != null && !bmp.isRecycled) {
            canvas.drawBitmap(bmp, 0f, 0f, null)
          } else {
            cacheTracker.invalidate()
            drawStaticScene(canvas, s)
          }
        }
        ProjectorDrawMode.REBUILD_CACHE -> {
          val bmp = ensureStaticCache(canvas.width, canvas.height)
          val cacheCanvas = if (bmp != null) staticCacheCanvas else null
          if (bmp != null && cacheCanvas != null) {
            drawStaticScene(cacheCanvas, s)
            cacheTracker.markBuilt(canvas.width, canvas.height, s.sceneVersion, m)
            canvas.drawBitmap(bmp, 0f, 0f, null)
          } else {
            cacheTracker.invalidate()
            drawStaticScene(canvas, s)
          }
        }
        ProjectorDrawMode.DIRECT -> drawStaticScene(canvas, s)
      }
      if (hasLive) {
        matrix.setValues(m)
        canvas.save()
        canvas.concat(matrix)
        // Lines the pen has lifted from but the page has not committed yet, oldest first, then the live one.
        for (line in s.pendingLines) drawLine(canvas, line.points, line.count, line.color, line.size)
        if (pts != null && s.liveCount > 0) drawLine(canvas, pts, s.liveCount, s.liveColor, s.liveSize)
        canvas.restore()
      }
    } finally {
      surf.unlockCanvasAndPost(canvas)
    }
  }

  /** One hand-drawn line (x,y pairs in page units) as a round polyline; the canvas already maps page units. */
  private fun drawLine(canvas: Canvas, pts: FloatArray, count: Int, color: Int, size: Float) {
    if (count <= 0) return
    livePaint.color = color
    livePaint.strokeWidth = size
    if (count == 1) {
      canvas.drawPoint(pts[0], pts[1], livePaint)
    } else {
      liveStroke.rewind()
      liveStroke.moveTo(pts[0], pts[1])
      for (i in 1 until count) liveStroke.lineTo(pts[2 * i], pts[2 * i + 1])
      canvas.drawPath(liveStroke, livePaint)
    }
  }

  override fun surfaceCreated(holder: SurfaceHolder) {
    val surf = holder.surface
    handler.post { surface = surf; lastDrawn = null; schedule() }
  }

  override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
    targetWidth = w
    targetHeight = h
    source?.requestProjectorPublish()
    handler.post { lastDrawn = null; schedule() }
  }

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    // Wait until the render thread let go of the surface before it is torn down.
    val latch = java.util.concurrent.CountDownLatch(1)
    if (handler.post {
        surface = null
        releaseRenderResources()
        latch.countDown()
      }
    ) {
      latch.await(200, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
  }

  /** Called by the presentation when it stops. */
  fun shutdown() {
    handler.removeCallbacksAndMessages(null)
    val latch = java.util.concurrent.CountDownLatch(1)
    if (handler.post {
        releaseRenderResources()
        latch.countDown()
      }
    ) {
      latch.await(200, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
    thread.quitSafely()
  }

  private companion object {
    val LIGHT_COLOR_MAP: (Int) -> Int = { it }
    val DARK_COLOR_MAP: (Int) -> Int = { InkColors.onScreen(it, true) }
  }
}
