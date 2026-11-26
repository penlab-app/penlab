package dev.penlab.app.ink

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.RenderNode
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Draws the finished strokes fast by remembering how to draw them.
 *
 * Turning a stroke into drawing commands is the slow part (about 20 µs each, so 2000 strokes
 * cost several frames). This cache does that work once per group of strokes ("chunk") and
 * keeps the result as a [RenderNode], a recorded list of GPU commands. Each frame then only
 * replays the recordings of the chunks on screen, which costs the app almost nothing, so
 * panning and zooming stay smooth however full the page is.
 *
 * Chunks hold strokes in drawing order: chunk 1 is the oldest strokes, the last chunk the
 * newest. Strokes written one after another are usually close together, so each chunk covers
 * a small area and the renderer can skip off-screen chunks whole.
 *
 * Big notebooks: only chunks near the screen are kept recorded. Around the screen there are
 * two zones, measured in screens:
 *  - "near" (one screen out each way): chunks here are prepared ahead of time, their strokes
 *    built on a background thread ([StrokeMeshes.prefetch]) and then recorded, so scrolling
 *    onto them never waits;
 *  - "far" (beyond three screens): recordings here are thrown away, which frees their GPU
 *    memory. A 100-page notebook then costs about what a few pages cost.
 *
 * What triggers work:
 *  - a stroke added or erased → only its chunk is recorded again (at most [CHUNK_SIZE] strokes);
 *  - panning → nothing near the screen; chunks entering the near zone are prepared;
 *  - zooming → recordings are replayed scaled (still sharp, they are vectors), then refreshed a
 *    few per frame once the fingers stop, so edge smoothing matches the new zoom.
 */
class StrokeChunkCache(
  private val renderer: CanvasStrokeRenderer,
  /** Gives the stroke as it should be drawn (the dark page swaps in screen colours). */
  private val screenVersion: (InkStroke) -> Stroke,
  /** Called (on any thread) when strokes built in the background are ready to record. */
  private val onPrefetched: () -> Unit = {},
) : StrokeHistory.Listener<InkStroke> {

  private class Chunk {
    val strokes = ArrayList<InkStroke>(CHUNK_SIZE)
    val node = RenderNode("stroke chunk")

    /** Area on the page the chunk's strokes cover. */
    val bounds = RectF()
    var boundsStale = true

    /** Strokes changed since [node] was recorded. */
    var needsRecording = true

    /** The zoom [node] was recorded for. */
    var recordedZoom = 0f

    fun updateBounds() {
      if (!boundsStale) return
      bounds.setEmpty()
      for (stroke in strokes) bounds.union(stroke.bounds)
      boundsStale = false
    }
  }

  private val chunks = ArrayList<Chunk>()

  /** Layers that are switched off: their strokes are left out of every recording. */
  var hiddenLayers: Set<Int> = emptySet()
    set(value) {
      if (value == field) return
      field = value
      invalidateAll()
    }

  /** Marks every chunk stale so recordings rebuild on the next draw (e.g. after toggling dark paper). */
  fun invalidateAll() {
    for (chunk in chunks) chunk.needsRecording = true
  }

  /** Strokes left out of the recordings (a selection being dragged is drawn elsewhere). */
  private val hidden: MutableSet<InkStroke> = Collections.newSetFromMap(IdentityHashMap())

  /** Leaves [strokes] out of the drawing until called again; re-records only their chunks. */
  fun setHidden(strokes: Collection<InkStroke>) {
    val affected: MutableSet<InkStroke> = Collections.newSetFromMap(IdentityHashMap())
    affected += hidden
    affected += strokes
    hidden.clear()
    hidden += strokes
    if (affected.isEmpty()) return
    for (chunk in chunks) {
      if (chunk.strokes.any { it in affected }) chunk.needsRecording = true
    }
  }

  /** The strokes whose boxes touch [area], in drawing order (chunks rule most out at once). */
  fun strokesIn(area: RectF, skip: Set<InkStroke> = emptySet()): List<InkStroke> {
    val out = ArrayList<InkStroke>()
    for (chunk in chunks) {
      chunk.updateBounds()
      if (!RectF.intersects(chunk.bounds, area)) continue
      for (stroke in chunk.strokes) if (RectF.intersects(stroke.bounds, area) && stroke !in skip) out += stroke
    }
    return out
  }

  /** Number of strokes in chunks that touch [area] (an upper bound on what is on screen). */
  fun countIn(area: RectF): Int {
    var n = 0
    for (chunk in chunks) {
      chunk.updateBounds()
      if (RectF.intersects(chunk.bounds, area)) n += chunk.strokes.size
    }
    return n
  }

  // ---- Keeping the chunks equal to the page ----------------------------------------------

  override fun onInserted(index: Int, stroke: InkStroke) {
    // Most inserts are at the end (a new stroke): check the last chunk first.
    val last = chunks.lastOrNull()
    val total = totalStrokes
    if (index >= total) {
      val target = last?.takeIf { it.strokes.size < CHUNK_SIZE }
      if (target != null) {
        target.strokes += stroke
        target.needsRecording = true
        if (!target.boundsStale) target.bounds.union(stroke.bounds)
      } else {
        chunks += Chunk().apply {
          strokes += stroke
          bounds.set(stroke.bounds)
          boundsStale = false
        }
      }
      totalStrokes++
      return
    }
    var chunkStart = 0
    for ((position, chunk) in chunks.withIndex()) {
      if (index < chunkStart + chunk.strokes.size) {
        // Goes into the middle of the page (undo of an erase, or a highlight under the ink).
        chunk.strokes.add(index - chunkStart, stroke)
        chunk.needsRecording = true
        if (!chunk.boundsStale) chunk.bounds.union(stroke.bounds)
        totalStrokes++
        if (chunk.strokes.size > 2 * CHUNK_SIZE) splitInHalf(position)
        return
      }
      chunkStart += chunk.strokes.size
    }
  }

  private var totalStrokes = 0

  /** Keeps a chunk that grew from the middle small, so changing it stays cheap. */
  private fun splitInHalf(position: Int) {
    val chunk = chunks[position]
    val secondHalf = chunk.strokes.subList(chunk.strokes.size / 2, chunk.strokes.size)
    chunks.add(position + 1, Chunk().also { it.strokes.addAll(secondHalf) })
    secondHalf.clear()
    chunk.boundsStale = true
  }

  override fun onRemoved(index: Int, stroke: InkStroke) {
    var chunkStart = 0
    val iterator = chunks.iterator()
    for (chunk in iterator) {
      if (index < chunkStart + chunk.strokes.size) {
        chunk.strokes.removeAt(index - chunkStart)
        chunk.needsRecording = true
        chunk.boundsStale = true
        totalStrokes--
        if (chunk.strokes.isEmpty()) {
          chunk.node.discardDisplayList()
          iterator.remove()
        }
        return
      }
      chunkStart += chunk.strokes.size
    }
  }

  // ---- Drawing ---------------------------------------------------------------------------

  private val near = RectF()
  private val far = RectF()

  /**
   * Draws the chunks onto [canvas] (a hardware canvas). [visiblePage] is the part of the page
   * on screen.
   *
   * [refreshForZoom] says the page is holding still, so chunks recorded at another zoom may be
   * re-recorded. Returns true if there is more to do: call again next frame to continue.
   */
  fun draw(canvas: Canvas, pageToScreen: Matrix, visiblePage: RectF, refreshForZoom: Boolean): Boolean {
    val zoom = pageToScreen.mapRadius(1f)
    var refreshesLeft = if (refreshForZoom) ZOOM_REFRESHES_PER_FRAME else 0
    var preparesLeft = PREPARES_PER_FRAME
    var moreToDo = false
    val w = visiblePage.width()
    val h = visiblePage.height()
    near.set(visiblePage.left - w, visiblePage.top - 2 * h, visiblePage.right + w, visiblePage.bottom + 2 * h) // two screens up/down (was one)
    far.set(visiblePage.left - 3 * w, visiblePage.top - 4 * h, visiblePage.right + 3 * w, visiblePage.bottom + 4 * h)

    canvas.save()
    canvas.concat(pageToScreen)
    for (chunk in chunks) {
      chunk.updateBounds()
      if (!RectF.intersects(chunk.bounds, far)) {
        // Far away: drop the recording (and its GPU memory); it is rebuilt when we come back.
        if (chunk.node.hasDisplayList()) chunk.node.discardDisplayList()
        continue
      }
      val onScreen = RectF.intersects(chunk.bounds, visiblePage)
      val isNear = onScreen || RectF.intersects(chunk.bounds, near)
      // Android may throw a recording away (it does so when a node stops being drawn).
      if (chunk.needsRecording || !chunk.node.hasDisplayList()) {
        when {
          // On screen with only a few strokes still to build (what was just written): build them
          // now. Many unbuilt (just opened a big notebook): build them in the background and show
          // the page meanwhile; building them here took 316 ms of the first frame (20 Nov).
          onScreen && unbuiltCount(chunk) <= SYNC_BUILD_LIMIT -> record(chunk, pageToScreen, zoom)
          onScreen -> {
            StrokeMeshes.prefetch(chunk.strokes.toList(), onPrefetched)
            if (!chunk.node.hasDisplayList()) continue // nothing older to show yet
          }
          isNear && allBuilt(chunk) && preparesLeft > 0 -> {
            record(chunk, pageToScreen, zoom)
            preparesLeft--
          }
          isNear -> {
            if (!allBuilt(chunk)) StrokeMeshes.prefetch(chunk.strokes.toList(), onPrefetched) else moreToDo = true
            continue
          }
          else -> continue // between near and far, not recorded: nothing to draw yet
        }
      } else if (chunk.recordedZoom != zoom && onScreen) {
        if (refreshesLeft > 0) {
          record(chunk, pageToScreen, zoom)
          refreshesLeft--
        } else {
          moreToDo = true
        }
      }
      // Off-screen chunks within the far zone are drawn too: the renderer rejects them by their
      // bounds almost for free, and a node that is drawn every frame keeps its recording.
      canvas.drawRenderNode(chunk.node)
    }
    canvas.restore()
    return moreToDo
  }

  private fun allBuilt(chunk: Chunk): Boolean = chunk.strokes.all { StrokeMeshes.isBuilt(it) }

  private fun unbuiltCount(chunk: Chunk): Int {
    var n = 0
    for (stroke in chunk.strokes) if (!StrokeMeshes.isBuilt(stroke) && ++n > SYNC_BUILD_LIMIT) break
    return n
  }

  private fun record(chunk: Chunk, pageToScreen: Matrix, zoom: Float) {
    chunk.updateBounds()
    // The recording is placed at the chunk's area on the page, in whole units.
    val left = floor(chunk.bounds.left).toInt()
    val top = floor(chunk.bounds.top).toInt()
    chunk.node.setPosition(left, top, ceil(chunk.bounds.right).toInt(), ceil(chunk.bounds.bottom).toInt())

    val recording = chunk.node.beginRecording()
    try {
      recording.translate(-left.toFloat(), -top.toFloat())
      for (stroke in chunk.strokes) {
        if (stroke !in hidden && stroke.layer !in hiddenLayers) renderer.draw(recording, screenVersion(stroke), pageToScreen)
      }
    } finally {
      chunk.node.endRecording()
    }
    chunk.needsRecording = false
    chunk.recordedZoom = zoom
  }

  /** The stroke's bounding box on the page (with margin). Do not modify it. */
  fun boundsOf(stroke: InkStroke): RectF = stroke.bounds

  private companion object {
    /** Strokes per chunk: the most that get re-recorded when one stroke changes. */
    const val CHUNK_SIZE = 16

    /** Chunks re-recorded per frame after a zoom, to spread that work out. */
    const val ZOOM_REFRESHES_PER_FRAME = 8

    /** Off-screen chunks recorded ahead per frame (their strokes are already built). */
    const val PREPARES_PER_FRAME = 6

    /** On-screen strokes built straight away in a frame; more than this are built in the background. */
    const val SYNC_BUILD_LIMIT = 12
  }
}
