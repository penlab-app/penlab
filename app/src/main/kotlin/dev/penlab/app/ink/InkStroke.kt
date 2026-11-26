package dev.penlab.app.ink

import android.graphics.Matrix
import android.graphics.RectF
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A finished stroke as the notebook keeps it: the pen's points as plain numbers plus the brush.
 *
 * Why not keep Jetpack Ink's [Stroke]? A Stroke carries its full drawing mesh, about 16 KB for
 * one handwritten letter, so a 100-page notebook (40,000 strokes) needed 650 MB and crashed the
 * app (stress test, 12 Nov). The points alone are about 1 KB. So every stroke is an InkStroke,
 * and the drawable [stroke] is built only when it is needed (on or near the screen, or for an
 * exact eraser test) and kept in [StrokeMeshes], which forgets the least recently used ones.
 *
 * An InkStroke never changes; moving or recolouring makes a new one (that keeps undo simple).
 * Identity is the object itself, like Ink's strokes.
 *
 * [points] holds [FLOATS_PER_POINT] floats per point: x, y (page units), pressure, tilt,
 * orientation (Ink's "no value" markers where the pen gave none). [times] in ms from the start.
 */
class InkStroke(
  val brush: Brush,
  val points: FloatArray,
  val times: LongArray,
  val toolType: InputToolType = InputToolType.STYLUS,
  val unitLengthCm: Float = StrokeInput.NO_STROKE_UNIT_LENGTH,
  /** Drawn under the ink with see-through colour. */
  val isHighlighter: Boolean = false,
  /** Which layer it is on (0 = the first). Hidden layers are not drawn, erased or selected. */
  val layer: Int = 0,
  /**
   * When it was written during an audio recording: [MediaMark] packs the notebook's recording
   * number and the position in it (ms). [MediaMark.NONE] if nothing was recording.
   */
  val mediaMark: Long = MediaMark.NONE,
) {
  val pointCount: Int
    get() = times.size

  /**
   * Area on the page the stroke can cover: its points plus a full brush width all round (a
   * generous margin, since recordings are clipped to this box and a fountain nib reaches out).
   *
   * PUBLICATION, not NONE: handwriting search reads it on a background thread while the canvas
   * reads it on the main thread; NONE crashed when both asked at the same moment (13 Nov).
   * Two threads may both compute it; they get the same box.
   */
  val bounds: RectF by lazy(LazyThreadSafetyMode.PUBLICATION) {
    if (pointCount == 0) return@lazy RectF()
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (i in 0 until pointCount) {
      val x = points[i * FLOATS_PER_POINT]
      val y = points[i * FLOATS_PER_POINT + 1]
      if (x < minX) minX = x
      if (x > maxX) maxX = x
      if (y < minY) minY = y
      if (y > maxY) maxY = y
    }
    val margin = brush.size + 2f
    RectF(minX - margin, minY - margin, maxX + margin, maxY + margin)
  }

  /** The drawable Ink stroke (built on first use, cached while recently used). */
  val stroke: Stroke
    get() = StrokeMeshes.get(this)

  fun x(i: Int) = points[i * FLOATS_PER_POINT]

  fun y(i: Int) = points[i * FLOATS_PER_POINT + 1]

  /** Builds the Ink stroke from the points. About 100 µs for a letter; [StrokeMeshes] calls it. */
  internal fun build(): Stroke = Stroke(brush, inputBatch())

  /**
   * The points as Ink wants them. Ink rejects a point that repeats the previous one exactly
   * (same place, same time) and times that go backwards; imported files can contain
   * both, and one bad stroke must never crash the page, so those points are skipped here.
   */
  fun inputBatch(): MutableStrokeInputBatch {
    val batch = MutableStrokeInputBatch()
    var lastX = Float.NaN
    var lastY = Float.NaN
    var lastT = Long.MIN_VALUE
    for (i in 0 until pointCount) {
      val b = i * FLOATS_PER_POINT
      val x = points[b]
      val y = points[b + 1]
      if (!x.isFinite() || !y.isFinite()) continue
      val t = maxOf(times[i], if (lastT == Long.MIN_VALUE) times[i] else lastT)
      if (x == lastX && y == lastY && t == lastT) continue
      try {
        batch.add(StrokeInput.create(x, y, t, toolType, unitLengthCm, points[b + 2], points[b + 3], points[b + 4]))
      } catch (e: IllegalArgumentException) {
        continue // a value Ink does not accept (pressure, tilt out of range): drop the point
      }
      lastX = x
      lastY = y
      lastT = t
    }
    return batch
  }

  /** The same stroke on another layer. */
  fun withLayer(newLayer: Int) = InkStroke(brush, points, times, toolType, unitLengthCm, isHighlighter, newLayer, mediaMark)

  /** The same points with another brush (a pasted highlight keeps its flag). */
  fun withBrush(newBrush: Brush) = InkStroke(newBrush, points, times, toolType, unitLengthCm, isHighlighter, layer, mediaMark)

  /**
   * A copy with every point moved by [matrix] (page units): any mix of move, scale, rotate and
   * mirror. [widthScale] makes the line thicker or thinner to match.
   */
  fun mapped(matrix: Matrix, widthScale: Float = 1f): InkStroke {
    val moved = points.copyOf()
    val xy = FloatArray(2)
    for (i in 0 until pointCount) {
      val b = i * FLOATS_PER_POINT
      xy[0] = moved[b]
      xy[1] = moved[b + 1]
      matrix.mapPoints(xy)
      moved[b] = xy[0]
      moved[b + 1] = xy[1]
    }
    val newBrush = if (widthScale == 1f) brush else Brushes.scaled(brush, widthScale)
    return InkStroke(newBrush, moved, times, toolType, unitLengthCm, isHighlighter, layer, mediaMark)
  }

  /** Scaled about an anchor, then moved; the line width follows the average scale. */
  fun transformed(scaleX: Float, scaleY: Float, anchorX: Float, anchorY: Float, dx: Float, dy: Float): InkStroke {
    val matrix = Matrix().apply {
      setScale(scaleX, scaleY, anchorX, anchorY)
      postTranslate(dx, dy)
    }
    return mapped(matrix, sqrt(abs(scaleX * scaleY)))
  }

  fun translated(dx: Float, dy: Float): InkStroke {
    val moved = points.copyOf()
    for (i in 0 until pointCount) {
      moved[i * FLOATS_PER_POINT] += dx
      moved[i * FLOATS_PER_POINT + 1] += dy
    }
    return InkStroke(brush, moved, times, toolType, unitLengthCm, isHighlighter, layer, mediaMark)
  }

  companion object {
    const val FLOATS_PER_POINT = 5

    /** Takes over a stroke Ink just drew; the mesh Ink already built goes straight into the cache. */
    fun from(stroke: Stroke, isHighlighter: Boolean = false, layer: Int = 0, mediaMark: Long = MediaMark.NONE): InkStroke {
      val inputs = stroke.inputs
      val n = inputs.size
      val points = FloatArray(n * FLOATS_PER_POINT)
      val times = LongArray(n)
      val p = StrokeInput()
      var toolType = InputToolType.STYLUS
      var unitLength = StrokeInput.NO_STROKE_UNIT_LENGTH
      for (i in 0 until n) {
        inputs.populate(i, p)
        val b = i * FLOATS_PER_POINT
        points[b] = p.x
        points[b + 1] = p.y
        points[b + 2] = p.pressure
        points[b + 3] = p.tiltRadians
        points[b + 4] = p.orientationRadians
        times[i] = p.elapsedTimeMillis
        toolType = p.toolType
        unitLength = p.strokeUnitLengthCm
      }
      return InkStroke(Brushes.intern(stroke.brush), points, times, toolType, unitLength, isHighlighter, layer, mediaMark).also {
        StrokeMeshes.put(it, stroke)
      }
    }
  }
}

/**
 * One Brush object per distinct pen (family, colour, size, detail), shared by all its strokes,
 * instead of one per stroke.
 */
object Brushes {
  private data class Key(val family: Any, val color: Int, val size: Float, val epsilon: Float)

  private val known = HashMap<Key, Brush>()

  @Synchronized
  fun intern(brush: Brush): Brush = known.getOrPut(Key(brush.family, brush.colorIntArgb, brush.size, brush.epsilon)) { brush }

  @Synchronized
  fun get(family: androidx.ink.brush.BrushFamily, color: Int, size: Float, epsilon: Float): Brush =
    known.getOrPut(Key(family, color, size, epsilon)) { Brush.createWithColorIntArgb(family, color, size, epsilon) }

  fun scaled(brush: Brush, factor: Float): Brush = get(brush.family, brush.colorIntArgb, brush.size * factor, brush.epsilon * factor)

  fun recoloured(brush: Brush, color: Int): Brush = get(brush.family, color, brush.size, brush.epsilon)
}

/**
 * The drawable Ink strokes of recently used [InkStroke]s, up to a budget of points (about
 * 150 MB of meshes). Least recently used ones are dropped; they are rebuilt if needed again.
 * [prefetch] builds strokes on a background thread ahead of the page scrolling to them, so
 * coming back to a page never waits on the main thread.
 */
object StrokeMeshes {
  /** About 10,000 handwritten letters: several dense pages, far more than one screen shows. */
  private const val BUDGET_POINTS = 700_000 // was 400k; raised 15 Nov so a whole notebook stays built

  private val built = LinkedHashMap<InkStroke, Stroke>(4096, 0.75f, true)
  private var points = 0
  private val worker = Executors.newSingleThreadExecutor { Thread(it, "penlab-meshes").apply { priority = Thread.NORM_PRIORITY - 1 } }
  private val queued: MutableSet<InkStroke> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())

  fun get(ink: InkStroke): Stroke {
    synchronized(this) { built[ink]?.let { return it } }
    val stroke = ink.build() // outside the lock, so the worker never makes the main thread wait
    put(ink, stroke)
    return stroke
  }

  @Synchronized
  fun put(ink: InkStroke, stroke: Stroke) {
    if (built.put(ink, stroke) == null) points += ink.pointCount
    if (points > BUDGET_POINTS) {
      val iterator = built.entries.iterator()
      while (points > BUDGET_POINTS * 9 / 10 && iterator.hasNext()) {
        val oldest = iterator.next()
        points -= oldest.key.pointCount
        iterator.remove()
      }
    }
  }

  /** The drawable stroke if it is already built, else null (never builds or evicts anything). */
  @Synchronized
  fun ifBuilt(ink: InkStroke): Stroke? = built[ink]

  /** True if the drawable stroke is ready (does not count as a use). */
  @Synchronized
  fun isBuilt(ink: InkStroke): Boolean = built.containsKey(ink)

  /** Builds the missing ones in the background, then calls [onReady] on the worker thread. */
  fun prefetch(strokes: List<InkStroke>, onReady: () -> Unit) {
    val todo = synchronized(this) { strokes.filter { !built.containsKey(it) && queued.add(it) } }
    if (todo.isEmpty()) return
    worker.execute {
      for (ink in todo) {
        if (!isBuilt(ink)) put(ink, ink.build())
      }
      synchronized(this) { queued.removeAll(todo.toSet()) }
      onReady()
    }
  }

  @Synchronized
  fun debug() = "${built.size} strokes/$points pts"

  @Synchronized
  fun budgetLeft(): Int = BUDGET_POINTS - points

  const val BUDGET = BUDGET_POINTS
}

/**
 * A stroke's place in a recording (the "media" plugin capability, docs/plugin-bridge.md): the recording's number in
 * the notebook's list (high 32 bits) and the position in milliseconds (low 32 bits, up to 49 days) packed in one
 * Long. The packing is Graphite's AudioMark, so notebooks written by Graphite keep working. PenLab stamps it at pen-up
 * while a media plugin records; with no media plugin installed the mark is only carried through loading, editing and
 * saving.
 */
object MediaMark {
  const val NONE = -1L

  /** The mark of position [positionMs] in recording number [recording] (an index into the notebook's recordings list). */
  fun of(recording: Int, positionMs: Long): Long = (recording.toLong() shl 32) or (positionMs and 0xFFFFFFFFL)

  /** The recording number of [mark] (not meaningful for [NONE]). */
  fun recording(mark: Long): Int = (mark ushr 32).toInt()

  /** The position in the recording of [mark], in milliseconds. */
  fun positionMs(mark: Long): Long = mark and 0xFFFFFFFFL
}
