package dev.penlab.app.ink

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors

/**
 * Draws the pages of an imported PDF as pictures for the canvas to put under the ink.
 *
 * Android's PdfRenderer works on one page at a time from one thread, so all rendering happens
 * on a single background thread. The canvas asks for a page at the width it needs
 * ([bitmapFor]); it gets the best picture already made (or null) straight away, and [onReady]
 * is called when a sharper one is finished. Pictures are kept in a memory-limited cache.
 */
class PdfBackground(file: File, private val onReady: () -> Unit) : Closeable {
  private val thread = Executors.newSingleThreadExecutor { Thread(it, "penlab-pdf").apply { priority = Thread.NORM_PRIORITY - 1 } }
  private val main = Handler(Looper.getMainLooper())
  private var descriptor: ParcelFileDescriptor? = null
  private var renderer: PdfRenderer? = null
  private val pending = HashSet<Int>() // main thread only

  private val cache =
    // A third of the memory: room for the pages just above and below too.
    object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 3).toInt()) {
      override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }

  // Previews: a small picture of EVERY page, made on a second thread with its own PdfRenderer
  // as soon as the notebook opens, nearest the page being looked at first. Scrolling then always
  // has something to show while the sharp picture is made (user request: "other apps do this
  // fine"). 512 px wide in RGB_565: about 0.75 MB a page.
  private val previews =
    object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
      override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }
  @Volatile private var lookingAt = 0
  @Volatile private var closed = false
  private val previewThread = Thread({ makePreviews(file) }, "penlab-pdf-previews").apply {
    priority = Thread.MIN_PRIORITY
    isDaemon = true
  }

  private fun makePreviews(file: File) {
    try {
      ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { pdf ->
          val done = BooleanArray(pdf.pageCount)
          var left = pdf.pageCount
          while (left > 0 && !closed) {
            // The nearest page to the one in view that has no preview yet.
            val at = lookingAt.coerceIn(0, pdf.pageCount - 1)
            var next = -1
            for (d in 0 until pdf.pageCount) {
              if (at + d < pdf.pageCount && !done[at + d]) { next = at + d; break }
              if (at - d >= 0 && !done[at - d]) { next = at - d; break }
            }
            if (next < 0) break
            done[next] = true
            left--
            val bitmap = pdf.openPage(next).use { page ->
              val height = (PREVIEW_WIDTH.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
              val argb = Bitmap.createBitmap(PREVIEW_WIDTH, height, Bitmap.Config.ARGB_8888)
              argb.eraseColor(Color.WHITE)
              page.render(argb, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
              argb.copy(Bitmap.Config.RGB_565, false).also { argb.recycle(); it.prepareToDraw() }
            }
            val page = next
            main.post {
              previews.put(page, bitmap)
              if (cache.get(page) == null) onReady()
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Could not make PDF previews", e)
    }
  }

  init {
    previewThread.start()
    thread.execute {
      try {
        descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(descriptor!!)
      } catch (e: Exception) {
        Log.e(TAG, "Could not open the PDF", e)
      }
    }
  }

  /**
   * The picture of PDF page [pdfPage], or null if none is ready yet. If the one in the cache is
   * much narrower than [wantedWidth] pixels (and [mayRender] is true), a sharper one is made in
   * the background. Main thread only.
   */
  fun bitmapFor(pdfPage: Int, wantedWidth: Int, mayRender: Boolean): Bitmap? {
    lookingAt = pdfPage
    val have = cache.get(pdfPage)
    val shown = have ?: previews.get(pdfPage)
    if (pdfPage in pending) return shown
    val target = bucket(wantedWidth)
    // The sharp picture is made once the view is still; until then the preview shows. With no
    // preview yet (just opened), a sharp one is made straight away.
    if ((mayRender || shown == null) && (have == null || have.width < target * 0.8f)) render(pdfPage, target)
    return shown
  }

  /** Renders a page that is about to come into view (the ones just above and below), if it has no picture. */
  fun prefetch(pdfPage: Int, wantedWidth: Int) {
    if (pdfPage < 0 || cache.get(pdfPage) != null || pdfPage in pending) return
    render(pdfPage, bucket(wantedWidth))
  }

  private fun render(pdfPage: Int, width: Int) {
    pending += pdfPage
    thread.execute {
      val bitmap =
        try {
          renderer?.let { pdf ->
            if (pdfPage !in 0 until pdf.pageCount) return@let null
            pdf.openPage(pdfPage).use { page ->
              val height = (width.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
              Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                it.eraseColor(Color.WHITE) // PDFs assume white paper
                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                // Hand it to the GPU now, off the main thread: the first frame that drew a new
                // page picture used to stall uploading it ("slow bitmap uploads" while scrolling).
                it.prepareToDraw()
              }
            }
          }
        } catch (e: Exception) {
          Log.e(TAG, "Could not draw PDF page $pdfPage", e)
          null
        }
      main.post {
        pending -= pdfPage
        if (bitmap != null) {
          cache.put(pdfPage, bitmap)
          onReady()
        }
      }
    }
  }

  /**
   * Renders PDF page [pdfPage] [width] px wide and waits for it, bypassing the cache (the caller
   * owns the bitmap). NEVER on the main thread: it blocks until the PDF thread is free.
   */
  fun renderNow(pdfPage: Int, width: Int): Bitmap? =
    try {
      thread.submit<Bitmap?> {
        renderer?.let { pdf ->
          if (pdfPage !in 0 until pdf.pageCount) return@let null
          pdf.openPage(pdfPage).use { page ->
            val height = (width.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
              it.eraseColor(Color.WHITE)
              page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
          }
        }
      }.get()
    } catch (e: Exception) {
      Log.e(TAG, "Could not draw PDF page $pdfPage", e)
      null
    }

  /** Widths come in steps of 512 px (up to 4096), so small zoom changes do not re-render. */
  private fun bucket(width: Int) = (((width + 511) / 512) * 512).coerceIn(512, 4096)

  override fun close() {
    closed = true
    thread.execute {
      renderer?.close()
      descriptor?.close()
    }
    thread.shutdown()
    cache.evictAll()
    previews.evictAll()
  }

  companion object {
    private const val TAG = "PdfBackground"

    /** Width of the quick first picture of a page (fast to render, sharp enough mid-scroll). */
    private const val PREVIEW_WIDTH = 512

    /** Page count and the first page's size in millimetres, or null if it is not a readable PDF. */
    fun inspect(file: File): Triple<Int, Float, Float>? =
      try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
          PdfRenderer(fd).use { pdf ->
            if (pdf.pageCount == 0) return null
            pdf.openPage(0).use { page ->
              // PDF sizes are in points: 72 per inch.
              Triple(pdf.pageCount, page.width * 25.4f / 72f, page.height * 25.4f / 72f)
            }
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Not a readable PDF", e)
        null
      }

    /** Every page's size in millimetres (width, height), or null if it is not a readable PDF. */
    fun pageSizes(file: File): List<Pair<Float, Float>>? =
      try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
          PdfRenderer(fd).use { pdf ->
            List(pdf.pageCount) { i -> pdf.openPage(i).use { it.width * 25.4f / 72f to it.height * 25.4f / 72f } }
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Not a readable PDF", e)
        null
      }
  }
}
