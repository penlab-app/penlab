package dev.penlab.app.ink

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.geometry.AffineTransform
import androidx.ink.geometry.ImmutableParallelogram
import androidx.ink.geometry.ImmutableSegment
import androidx.ink.geometry.ImmutableVec
import androidx.ink.geometry.Intersection.intersects
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import dev.penlab.app.projector.ProjectorItem
import dev.penlab.app.projector.ProjectorPage
import dev.penlab.app.projector.ProjectorSnapshot
import androidx.input.motionprediction.MotionEventPredictor
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.StrokeInput
import kotlin.math.cos
import kotlin.math.sin
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * True if the press that started at [downTime] is the very press that closed a panel, the wheel or
 * the paste menu at [closedAt] (both in uptime ms; a couple of ms apart at most). That press must
 * not also leave a dot of ink, whether it came from the pen or from a drawing finger.
 */
internal fun pressOnlyClosed(downTime: Long, closedAt: Long): Boolean = abs(downTime - closedAt) <= 2L

/**
 * The drawing surface: a white page you write on with the S Pen.
 *
 * Two layers, stacked:
 *  - [finishedView] (bottom): every stroke that is already done, drawn the normal Android way.
 *  - [inProgressView] (top): only the stroke under the pen right now. Jetpack Ink draws it
 *    straight to the screen's front buffer, skipping the usual frame queue. That shortcut is
 *    where the low latency comes from.
 *
 * When the pen lifts, Ink hands the stroke over as a [Stroke] (vector data, not pixels) and it
 * moves from the top layer to the bottom one.
 *
 * Two coordinate systems:
 *  - page coordinates: where a stroke is on the paper. Strokes are stored in these and never
 *    change when you pan or zoom.
 *  - screen coordinates: pixels on the tablet. [pageToScreen] converts page → screen and is the
 *    only thing pan/zoom changes; [screenToPage] is its inverse, used for pen input.
 *
 * Colours: strokes are stored in paper colours (white page). With [darkPage] the screen shows
 * the page inverted; see [InkColors].
 *
 * Shapes: hold the pen still for half a second at the end of a stroke and a line, triangle,
 * rectangle or ellipse is replaced by a clean one (see [ShapeRecognizer]).
 *
 * Input rules: the pen draws; with the eraser tool or the side button held it erases every
 * stroke its circle touches; fingers
 * pan and pinch-zoom; a 2-finger tap undoes and a 3-finger tap redoes; fingers that land while
 * the pen is near the screen (the writing hand's palm) are ignored.
 */
@SuppressLint("ViewConstructor")
class InkCanvasView(
  context: Context,
  darkPage: Boolean = true,
  /** Pages of paper (an A4 notebook), or null for one endless canvas. */
  val pageLayout: PageLayout? = null,
  /**
   * The imported PDF's pages, for notebooks made from a PDF. Its pages stay white with ink in
   * its real colours even on the dark theme (PDF slides are made for white paper).
   */
  private val pdf: PdfBackground? = null,
  /** The notebook's `images/` folder, where photos on its pages are kept. */
  private val imagesDir: java.io.File? = null,
) : FrameLayout(context) {

  /** Whether plain pages render dark (display-only lightness-inverted ink). PDFs always stay light. */
  @Volatile
  private var _darkPage: Boolean = darkPage && pdf == null

  val darkPage: Boolean
    get() = _darkPage

  /**
   * Switches the canvas between light and dark paper live without dropping ink or view state.
   * Rebuilds display-only colour caches, page/tile rasters, thumbnails, and overlay paints.
   * No-op when unchanged or when this canvas displays a PDF notebook.
   */
  fun setDarkPage(dark: Boolean) {
    val effective = dark && pdf == null
    if (_darkPage == effective) return
    _darkPage = effective
    setBackgroundColor(if (pageLayout == null) InkColors.pageOnScreen(darkPage) else InkColors.deskOnScreen(darkPage))
    pageBackground.setDarkPage(darkPage)
    instrumentStyle = InstrumentStyle(darkPage)
    synchronized(screenVersions) { screenVersions.clear() }
    chunkCache.invalidateAll()
    rasters.invalidateAll()
    tiles.invalidateAll()
    freshInk.clear()
    freshTileInk.clear()
    clearThumbnails()
    finishedView.updateDarkPage()
    selectionView.updateDarkPage()
    eraserCursor.updateDarkPage()
    finishedView.invalidate()
    instrumentView.invalidate()
    selectionView.invalidate()
    eraserCursor.invalidate()
    onPicturesUpdated()
    // The projector's strokes carry their screen colour, so rebuild them and send the new paper/desk/ink colours.
    projectorHistoryVersion++
    invalidateProjectorContent()
  }

  // ---- Photos and text boxes ---------------------------------------------------------------

  /** Photos and text boxes, drawn under the ink in this order. */
  private val items = ArrayList<PageItem>()
  private val itemPainter = ItemPainter(imagesDir)
  private val selectedItems = LinkedHashSet<String>()
  private val hiddenItems = HashSet<String>()
  private var clipboardItems: List<PageItem>
    get() = SharedClipboard.items
    set(value) {
      SharedClipboard.items = value
    }

  private val hasAnySelection: Boolean
    get() = selection.isNotEmpty() || selectedItems.isNotEmpty()

  /** A text box's screen colour: inverted on the dark page, real colour on a white PDF page. */
  private fun itemTextColor(item: PageItem, paperColor: Int): Int {
    val light = pageLayout?.let { isLightPage(it.pageAt(item.cy)) } ?: false
    return InkColors.onScreen(paperColor, darkPage && !light)
  }

  private fun drawItems(canvas: Canvas, visible: RectF, skipHidden: Boolean = true) {
    for (item in items) {
      if (skipHidden && item.id in hiddenItems) continue
      if (!RectF.intersects(item.bounds(itemBox), visible)) continue
      itemPainter.draw(canvas, item) { itemTextColor(item, it) }
    }
  }

  private val itemBox = RectF()

  /** The colour mapping [drawItems] uses for [item], captured for drawing on another thread. */
  private fun itemTextColorFor(item: PageItem): (Int) -> Int = { itemTextColor(item, it) }

  /**
   * Changes the items through [block] and records the change so undo puts it back. Inside a
   * history group it becomes part of that one step.
   */
  private fun changeItems(block: (MutableList<PageItem>) -> Unit) {
    val before = items.toList()
    block(items)
    val after = items.toList()
    if (before == after) return
    history.recordState(HistoryStates.ITEMS, before, after)
    itemsChanged()
  }

  private fun setItems(list: List<PageItem>) {
    items.clear()
    items.addAll(list)
    selectedItems.retainAll(list.map { it.id }.toSet())
    itemsChanged()
  }

  private fun itemsChanged() {
    clearThumbnails()
    finishedView.invalidate()
    invalidateProjectorContent()
    onHistoryChanged()
  }

  /** Units per millimetre of paper (see [unitsPerMm]); exposed for sizing text. */
  private val itemMm: Float
    get() = if (pageLayout != null) PageLayout.UNITS_PER_MM else resources.displayMetrics.xdpi / 25.4f

  /**
   * Adds a photo (a file already in [imagesDir]) in the middle of the screen, half the screen
   * wide, under the ink. One undo step.
   */
  fun addImage(fileName: String, adaptToDarkPaper: Boolean = false) {
    val aspect = itemPainter.aspect(fileName) ?: return
    val visible = RectF(0f, 0f, width.toFloat(), height.toFloat()).also { screenToPage.mapRect(it) }
    var w = visible.width() * 0.5f
    var h = w / aspect
    val maxH = visible.height() * 0.72f
    if (h > maxH && maxH > 0f) {
      h = maxH
      w = h * aspect
    }
    val item = ImageItem(PageItem.newId(), fileName, visible.centerX(), visible.centerY(), w, h, isInk = adaptToDarkPaper)
    changeItems { it += item }
  }

  /**
   * Puts a typeset equation (a picture already in [imagesDir], dark on transparent) on the page
   * just below the lasso selection it was made from (or mid-screen), about as wide as it. One
   * undo step.
   */
  /** The selection's bounds (page units), or null: kept by callers that place something later. */
  val selectionRect: RectF?
    get() = if (selectionBounds.isEmpty) null else RectF(selectionBounds)

  /**
   * The look's accent for the page edges (the "bold accent" option), or null for the plain grey
   * edge. Set from the UI when the look changes.
   */
  var pageEdgeAccent: Int? = null
    set(value) {
      if (field == value) return
      field = value
      finishedView.invalidate()
      invalidateProjectorContent()
    }

  /** A short message for the user (shown as a snackbar), e.g. why a fill didn't happen. */
  var onNotice: (String) -> Unit = {}

  /**
   * Fill tool: colours the closed region around a screen point, within the visible page (see
   * [RegionFill]). Worked out off the main thread; the fill lands under the ink as one undo step.
   */
  private fun fillAt(screenX: Float, screenY: Float, color: Int) {
    val dir = imagesDir ?: return
    val point = floatArrayOf(screenX, screenY).also(screenToPage::mapPoints)
    val area = RectF(0f, 0f, width.toFloat(), height.toFloat()).also { screenToPage.mapRect(it) }
    pageLayout?.let { layout -> area.intersect(layout.rect(layout.pageAt(point[1]), RectF())) }
    val strokes = chunkCache.strokesIn(area).filter { !isHiddenLayer(it) && !it.isHighlighter }
    Thread({
      // A dashed or slightly open outline leaks: try again with its gaps sealed (2.5 mm).
      val result = try {
        RegionFill.fillAt(strokes, area, point[0], point[1], color)
          ?: RegionFill.fillAt(strokes, area, point[0], point[1], color, wallExtra = 2.5f * itemMm)
      } catch (e: Throwable) { null }
      val name = result?.let { saveFillPicture(dir, it.bitmap) }
      post {
        if (result == null || name == null) {
          onNotice("Nothing to fill there: tap inside a closed outline that fits on screen")
          return@post
        }
        addFill(name, result.bounds)
      }
    }, "penlab-fill").start()
  }

  /**
   * What a plugin action may see: the selection as plain data in millimetres (see the plugin API's
   * Selection), with a picture of just the selected strokes and items on transparent paper.
   * Main thread; an empty selection gives an empty Selection.
   */
  fun pluginSelection(): dev.penlab.plugin.Selection {
    if (!hasAnySelection) return dev.penlab.plugin.Selection.empty()
    val k = itemMm
    val chosen = history.strokes.filter { it in selection && !isHiddenLayer(it) }
    val chosenItems = items.filter { it.id in selectedItems }
    val area = RectF(selectionBounds)
    val bounds = RectF(area.left / k, area.top / k, area.right / k, area.bottom / k)
    var picture: Bitmap? = null
    if (!area.isEmpty) {
      val scale = 1200f / maxOf(area.width(), area.height())
      picture = Bitmap.createBitmap((area.width() * scale).toInt().coerceAtLeast(1), (area.height() * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
      val matrix = Matrix().apply {
        setTranslate(-area.left, -area.top)
        postScale(scale, scale)
      }
      val c = Canvas(picture)
      c.save()
      c.concat(matrix)
      for (item in chosenItems) itemPainter.draw(c, item) { it }
      drawPaperInk(c, chosen, matrix)
      c.restore()
    }
    return dev.penlab.plugin.Selection(
      chosen.map { dev.penlab.app.plugins.PluginConvert.stroke(it, k) },
      chosenItems.map { dev.penlab.app.plugins.PluginConvert.item(it, k) },
      bounds,
      picture,
      // The photos' original files (paths only: nothing is read on the main thread).
      imagesDir?.let { dir -> chosenItems.filterIsInstance<ImageItem>().associate { it.id to java.io.File(dir, it.fileName) } }.orEmpty(),
    )
  }

  /**
   * Puts what a plugin returned on the page, as one undo step: [items] come from
   * `PluginItemMaterialiser.stage` (pictures already saved in the notebook's `images/`), [strokes] are
   * real ink in millimetres (they become ordinary strokes, on their own layer or the current one). With
   * nothing selected when the action ran ([wasEmpty]) everything is centred as one group on the visible
   * page; otherwise it goes where its bounds say. A staged item with `replaces` (an id the registry already checked against the
   * call's selection) takes the place of that page item, if it is still there, in the same undo step; the new item stays
   * selected in its stead.
   */
  fun placePluginItems(items: List<dev.penlab.app.plugins.StagedItem>, strokes: List<dev.penlab.plugin.PluginStroke>, wasEmpty: Boolean) {
    if (items.isEmpty() && strokes.isEmpty()) return
    val k = itemMm
    var dx = 0f
    var dy = 0f
    if (wasEmpty) {
      val union = RectF()
      var any = false
      for (item in items) {
        if (any) union.union(item.item.boundsMm) else union.set(item.item.boundsMm)
        any = true
      }
      dev.penlab.app.plugins.PluginStrokes.boundsMm(strokes)?.let { b ->
        if (any) union.union(b[0], b[1], b[2], b[3]) else union.set(b[0], b[1], b[2], b[3])
        any = true
      }
      val visible = RectF(0f, 0f, width.toFloat(), height.toFloat()).also { screenToPage.mapRect(it) }
      dx = visible.centerX() - (union.centerX() * k)
      dy = visible.centerY() - (union.centerY() * k)
    }
    val byId = this.items.associateBy { it.id }
    val made = items.map { dev.penlab.app.plugins.PluginItemMaterialiser.toPageItem(it, k, dx, dy, it.item.replaces?.let(byId::get)) }
    val ink = dev.penlab.app.plugins.PluginStrokes.inDrawOrder(strokes.mapNotNull { dev.penlab.app.plugins.PluginStrokes.toInk(it, k, dx, dy, layers.current, layers.names.size) })
    if (made.isEmpty() && ink.isEmpty()) return
    var swapped = emptyMap<String, String>()
    history.grouped {
      if (made.isNotEmpty()) {
        val placement = dev.penlab.app.plugins.PluginItemMaterialiser.place(this.items, made.zip(items.map { it.item.replaces }))
        swapped = placement.swapped
        changeItems { list ->
          list.clear()
          list.addAll(placement.items)
        }
      }
      for (stroke in ink) history.add(stroke, insertIndexFor(stroke))
    }
    if (ink.isNotEmpty()) pageChanged()
    if (swapped.isNotEmpty()) {
      for ((old, new) in swapped) if (selectedItems.remove(old)) selectedItems += new
      updateSelectionBounds()
      selectionView.invalidate()
      reportSelection()
    }
  }

  /** The folder the notebook's pictures live in (null for a stress test, which must not write). */
  val picturesDir: java.io.File? get() = imagesDir

  /** The selection has strokes (so Fill has something to fill). */
  val selectionHasStrokes: Boolean
    get() = selection.isNotEmpty()

  /** Fills the inside of the selected closed strokes (snapped shapes, drawn loops) with [color]. */
  fun fillSelection(color: Int) {
    val dir = imagesDir ?: return
    val strokes = history.strokes.filter { it in selection }
    // One closed stroke each (snapped shapes, drawn loops); else the selection's middle with gaps
    // sealed, for outlines made of several strokes (a dashed circle is one stroke per dash).
    val result = RegionFill.fillShapes(strokes, color) ?: run {
      if (strokes.isEmpty()) return@run null
      val b = RectF(strokes[0].bounds).apply { strokes.forEach { union(it.bounds) } }
      val area = RectF(b).apply { inset(-b.width() * 0.1f - 5 * itemMm, -b.height() * 0.1f - 5 * itemMm) }
      try { RegionFill.fillAt(strokes, area, b.centerX(), b.centerY(), color, wallExtra = 2.5f * itemMm) } catch (e: Throwable) { null }
    }
    val name = result?.let { saveFillPicture(dir, it.bitmap) }
    if (result == null || name == null) {
      onNotice("Select a closed shape to fill it")
      return
    }
    addFill(name, result.bounds)
  }

  private fun saveFillPicture(dir: java.io.File, bitmap: android.graphics.Bitmap): String? =
    try {
      dir.mkdirs()
      val file = java.io.File(dir, "fill-${java.util.UUID.randomUUID()}.png")
      file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
      file.name
    } catch (e: Exception) {
      null
    }

  /** Puts a fill picture at the bottom of the items (under photos, text and ink). One undo step. */
  private fun addFill(fileName: String, bounds: RectF) {
    val item = ImageItem(PageItem.newId(), fileName, bounds.centerX(), bounds.centerY(), bounds.width(), bounds.height(), isInk = true)
    changeItems { it.add(0, item) }
    onHistoryChanged()
  }

  /** Called when the text tool touches the page: [id] of the box touched (null = new box) and its text. */
  var onTextRequest: (id: String?, existing: String) -> Unit = { _, _ -> }

  private var textAtX = 0f
  private var textAtY = 0f

  /** Finishes the text form: creates a box where the page was touched, or changes box [id]. Empty text removes it. */
  fun commitText(id: String?, text: String) {
    val clean = text.trimEnd()
    history.grouped {
      changeItems { list ->
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
          val old = list[index] as TextItem
          if (clean.isEmpty()) list.removeAt(index)
          else list[index] = old.copy(text = clean).let { it.copy(height = TextItems.measureHeight(it)) }
        } else if (clean.isNotEmpty()) {
          val size = tool.sizeDp * itemMm // the text tool's size is in millimetres
          val boxWidth = TEXT_BOX_WIDTH_MM * itemMm
          val draft = TextItem(PageItem.newId(), clean, tool.paperColor, size, textAtX + boxWidth / 2, textAtY, boxWidth, size)
          val measured = draft.copy(height = TextItems.measureHeight(draft))
          list += measured.copy(cy = textAtY + measured.height / 2)
        }
      }
    }
    onHistoryChanged() // after the group is recorded, so Undo lights up
  }

  /** The text tool touched the page at a screen point: edit the box there, or start a new one. */
  private fun textToolAt(screenX: Float, screenY: Float) {
    val point = floatArrayOf(screenX, screenY).also(screenToPage::mapPoints)
    val hit = items.lastOrNull { it is TextItem && it.bounds(itemBox).contains(point[0], point[1]) } as TextItem?
    textAtX = point[0]
    textAtY = point[1]
    onTextRequest(hit?.id, hit?.text ?: "")
  }

  private fun PageItem.withNewId(): PageItem =
    when (this) {
      is ImageItem -> copy(id = PageItem.newId())
      is TextItem -> copy(id = PageItem.newId())
      is PluginPageItem -> copy(id = PageItem.newId())
    }

  /** Called when pictures (PDF pages) finished drawing, so page thumbnails can refresh. */
  var onPicturesUpdated: () -> Unit = {}

  /** True for pages that show a PDF page: they keep white paper and true-colour ink. */
  private fun isLightPage(page: Int): Boolean = pdf != null && (pageLayout?.backgroundOf(page) ?: -1) >= 0

  private fun isOnLightPage(stroke: InkStroke): Boolean {
    val layout = pageLayout ?: return false
    if (pdf == null) return false
    return isLightPage(layout.pageAt(chunkCache.boundsOf(stroke).centerY()))
  }

  /** Called by the PDF renderer when a sharper page picture is ready. */
  fun pdfPageReady() {
    // Only thumbnails drawn without their PDF picture need redrawing (all of them used to be).
    for (page in thumbnailsMissingPdf) thumbnails.remove(page)
    thumbnailsMissingPdf.clear()
    finishedView.invalidate()
    invalidateProjectorContent()
    onPicturesUpdated()
  }

  /**
   * Page units per dp of pen width. On an endless canvas a unit is a screen pixel at 100%; in a
   * notebook a unit is a tenth of a millimetre of paper, so a 0.4 mm pen is 0.4 mm on the page,
   * whatever the screen.
   */
  private val strokeUnitsPerDp: Float =
    if (pageLayout == null) resources.displayMetrics.density
    else resources.displayMetrics.let { it.density * 25.4f / it.xdpi } * PageLayout.UNITS_PER_MM

  /** The zoom at which the page shows at its true size (100% in the readout). */
  private val trueSizeZoom: Float =
    if (pageLayout == null) 1f else resources.displayMetrics.xdpi / 25.4f / PageLayout.UNITS_PER_MM

  private val minZoom: Float
    get() = minZoomFor(trueSizeZoom)

  private val maxZoom: Float
    get() = maxZoomFor(trueSizeZoom)

  /** Called when pages are added, removed or bookmarked (read [pageLayout] for the new state). */
  var onPagesChanged: () -> Unit = {}

  /** Called when the page in the middle of the screen changes (A4 notebooks). */
  var onCurrentPageChanged: (Int) -> Unit = {}

  /** What the pen draws with. Set by the toolbar. */
  var tool: InkTool = InkTools.defaults.first()
    set(value) {
      field = value
      if (!value.isLasso) clearSelection()
    }

  /**
   * Where the current selection is on screen (canvas pixels), or null when nothing is selected
   * or it is being dragged. The screen uses it to show the selection's buttons.
   */
  var onSelectionChanged: (RectF?) -> Unit = {}

  /** Radius of the eraser circle in dp, for the eraser tool and the S Pen side button alike. */
  var eraserRadiusDp: Float = InkTools.sizesFor(InkTool.Kind.ERASER)[1]

  /** Whether the eraser (tool or side button) cuts strokes instead of removing whole ones. */
  var partialErase: Boolean = false

  /**
   * Set by the screen when the press now starting closed a panel, menu or the wheel; that pen
   * press then draws nothing (other note apps work the same way).
   */
  var closedSomethingAt = Long.MIN_VALUE / 2

  /** Called when the pen touches the page or a finger taps it, so menus and panels can close. */
  var onPageTouched: () -> Unit = {}

  /**
   * True while the pen is writing or has just lifted. The toolbar ignores fingers then, so the
   * writing hand cannot press a button. (A hovering pen does not count here: tapping a tool
   * with the other hand while the pen waits above the page must work.)
   */
  val isPenWriting: Boolean
    get() =
      penPointerId != MotionEvent.INVALID_POINTER_ID ||
        SystemClock.uptimeMillis() - lastPenUpTime < PEN_NEARBY_GRACE_MS

  /** Called when the paper style changes (either from UI or after async page load). */
  var onPageStyleChanged: (PageStyle) -> Unit = {}

  /** The paper's ruling: blank, lined or dotted. */
  var pageStyle: PageStyle = PageStyle.BLANK
    set(value) {
      field = value
      finishedView.invalidate()
      invalidateProjectorContent()
      onPageStyleChanged(value)
      onHistoryChanged() // worth saving too
    }

  /** Ruling spacing in mm for this notebook (0 = the paper's usual spacing). */
  var rulingMm: Float = 0f
    set(value) {
      field = value
      pageBackground.spacingMm = value
      clearThumbnails()
      finishedView.invalidate()
      invalidateProjectorContent()
      onHistoryChanged() // worth saving
    }

  /** Called after anything is added, erased, undone or redone, so the toolbar can update. */
  var onHistoryChanged: () -> Unit = {}

  val canUndo: Boolean
    get() = history.canUndo

  val canRedo: Boolean
    get() = history.canRedo

  val isEmpty: Boolean
    get() = history.strokes.isEmpty() && items.isEmpty()

  /**
   * True when the current page (or the endless canvas) contains at least one non-hidden stroke or
   * any [PageItem] that [clear] will remove.
   */
  val canClearCurrentPage: Boolean
    get() {
      val layout = pageLayout
      val top = layout?.top(currentPage) ?: 0f
      val bottom = if (layout != null) top + layout.pitch else 0f
      return canClearPage(
        isPaged = layout != null,
        pageTop = top,
        pageBottom = bottom,
        strokes = history.strokes,
        items = items,
        hiddenLayers = layers.hidden,
        strokeLayer = { it.layer },
        strokeCenterY = { chunkCache.boundsOf(it).centerY() },
        itemCenterY = { it.cy },
      )
    }

  private val history = StrokeHistory<InkStroke>()
  private val chunkCache = StrokeChunkCache(CanvasStrokeRenderer.create(), ::screenVersion) { finishedView.postInvalidateOnAnimation() }
  // ---- Zoomed out: pages drawn from pictures (see [PageRasters]) ----------------------------

  private val rasters: PageRasters = PageRasters { page, version ->
    // A current picture shows everything, so the strokes drawn live on top can go.
    if (version == rasters.versionOf(page)) freshInk.remove(page)
    finishedView.invalidate()
  }

  /** Per page, strokes added since its picture was made, drawn live on top of it. */
  private val freshInk = HashMap<Int, MutableList<InkStroke>>()

  /** True while a saved notebook is put on the canvas (not "new" ink). */
  private var loadingStrokes = false

  /** Endless canvas, zoomed out over a lot of ink: drawn from square pictures (see [TileRasters]). */
  private val tiles = TileRasters { finishedView.invalidate() }
  private val freshTileInk = ArrayList<InkStroke>()
  private var tileMode = false

  /** Strokes left out of pictures (a selection being dragged). */
  private val hiddenFromRasters: MutableSet<InkStroke> = Collections.newSetFromMap(IdentityHashMap())

  private fun inkChanged(stroke: InkStroke, added: Boolean) {
    val layout = pageLayout
    if (layout == null) {
      // Endless canvas: every square picture is now out of date; the newest strokes are drawn
      // live over the old pictures until the new ones arrive.
      tiles.invalidateAll()
      if (!loadingStrokes) {
        if (added) freshTileInk += stroke else freshTileInk.removeAll { it === stroke }
      }
      return
    }
    val page = layout.pageAt(stroke.bounds.centerY())
    rasters.invalidate(page)
    if (loadingStrokes) return
    if (added) freshInk.getOrPut(page) { ArrayList() } += stroke else freshInk[page]?.removeAll { it === stroke }
  }

  /** The strokes on [page] (by their middle), minus any being dragged. */
  private fun strokesOnPage(page: Int): List<InkStroke> {
    val layout = pageLayout ?: return emptyList()
    val top = layout.top(page)
    val bottom = top + layout.pitch
    return history.strokes.filter { val y = it.bounds.centerY(); y >= top && y < bottom && it !in hiddenFromRasters && !isHiddenLayer(it) }
  }

  /** A fresh drawable stroke in screen colours, not cached (for pictures, on any thread). */
  private fun buildForPicture(ink: InkStroke): Stroke {
    val color = ink.brush.colorIntArgb
    val brush = if (!darkPage || isOnLightPage(ink)) ink.brush else Brushes.recoloured(ink.brush, InkColors.onScreen(color, true))
    // Reuse the mesh if the stroke is already built (a colour change keeps the shape).
    if (StrokeMeshes.isBuilt(ink)) return ink.stroke.copy(brush)
    return Stroke(brush, ink.inputBatch())
  }

  private val pageBackground =
    PageBackground(if (pageLayout == null) resources.displayMetrics.density else PAPER_UNITS_PER_DP, darkPage).apply {
      pageWidth = pageLayout?.pageWidth ?: 0f
      pageHeight = pageLayout?.pageHeight ?: 0f
    }
  private val finishedView = FinishedStrokesView(context)
  private val inProgressView = InProgressStrokesView(context)
  private val eraserCursor = EraserCursorView(context)
  private val selectionView = SelectionView(context)
  private val instrumentView = InstrumentView(context)

  private val pageToScreen = Matrix()
  private val screenToPage = Matrix()
  private val identity = Matrix()

  /** Guesses where the pen will be a few ms ahead, so the line reaches the tip sooner. */
  private val predictor = MotionEventPredictor.newInstance(this)

  /** A stroke being drawn: which tool made it, and the brush to store it with. */
  private class PendingStroke(val tool: InkTool, val paperBrush: Brush)

  private val pendingStrokes = HashMap<InProgressStrokeId, PendingStroke>()

  /** When the live stroke began (uptime clock), for the mark of a hold-to-snap shape. */
  private var strokeDownUptime = 0L

  // Both forget a stroke by themselves once nothing else (page, undo stack) holds it.
  /** Dark page only: stored (paper-colour) stroke → the same stroke in its screen colour. */
  private val screenVersions = WeakHashMap<Stroke, Stroke>()

  // A touch is either a tap or a pan, never both: both detectors share one movement threshold.
  private val fingerSlopPx = FINGER_SLOP_FACTOR * ViewConfiguration.get(context).scaledTouchSlop

  private val tapDetector =
    FingerTapDetector(maxMovePx = fingerSlopPx) { fingers ->
      when (fingers) {
        2 -> undo()
        3 -> redo()
      }
    }

  private val panZoomDetector = PanZoomDetector(startSlopPx = fingerSlopPx, listener = ::panZoomBy)

  // ---- "Draw with finger" (tablets without a pen) ------------------------------------------
  // A single finger is handed to the pen code below as a copy of its events that says "stylus"
  // with a fixed pressure, so it draws, erases and lassos exactly like the pen. A second finger
  // (or a pen) landing cancels that stroke and the fingers pan and zoom instead.

  /** Asked at every touch, so the switch takes effect at once. Set by the activity. */
  var fingerDrawEnabled: () -> Boolean = { false }

  /** True from a drawing finger's DOWN until it lifts or the stroke is cancelled. */
  private var fingerPenActive = false
  private var fingerPenId = MotionEvent.INVALID_POINTER_ID

  /** True while a finger press that only closed a panel is on the glass: it must leave no mark. */
  private var fingerSwallowed = false

  /** True only while the copied event is being run through [onTouchEvent] again. */
  private var runningFingerPenCopy = false

  // State of the pen gesture currently on the glass.
  private var penPointerId = MotionEvent.INVALID_POINTER_ID
  private var activeStrokeId: InProgressStrokeId? = null
  private var lastEraserX = 0f
  private var lastEraserY = 0f
  private var erasedDuringGesture = false

  // Hold to perfect a shape: the stroke's points so far (page units) and its pressures, where
  // the pen last moved noticeably, and whether this stroke was already replaced by a shape.
  private val strokePoints = ArrayList<Point>()
  private var pressureSum = 0f

  /** The stroke under the pen as the projector draws it live; reads [strokePoints] (see [LiveStrokeMirror]). */
  private val liveMirror = LiveStrokeMirror(strokePoints)
  private var holdAnchorX = 0f
  private var holdAnchorY = 0f
  private var shapeSnapped = false
  private val holdSlopPx = HOLD_SLOP_DP * resources.displayMetrics.density
  private val minShapePx = MIN_SHAPE_DP * resources.displayMetrics.density
  private val snapToShape = Runnable { replaceWithShape() }

  /** Hold-to-perfect-shapes can be switched off (it is on by default). */
  var shapeSnappingEnabled = true

  /** Stylus pressure sensitivity preset applied to newly started pressure/fountain strokes. */
  var pressureSensitivity: PressureSensitivity = DEFAULT_PRESSURE_SENSITIVITY

  // Is the pen close to the screen? Used to tell the writing hand's palm from a real finger.
  private var penHovering = false
  private var lastHoverTime = 0L
  private var lastPenEventTime = 0L
  private var lastPenUpTime = 0L

  /**
   * Hovering counts only while hover events keep coming (a pen held above the glass sends them
   * many times a second). If the pen leaves over a dialog (a separate window), this view never
   * gets the HOVER_EXIT; without the time limit the palm rule then ignored real fingers until
   * the pen came back (user request: "after closing the Ask menu I can't move the paper").
   */
  private val penNearby: Boolean
    get() {
      val now = SystemClock.uptimeMillis()
      return penPointerId != MotionEvent.INVALID_POINTER_ID ||
        (penHovering && now - lastHoverTime < HOVER_STALE_MS) ||
        now - lastPenEventTime < PEN_NEARBY_GRACE_MS
    }

  init {
    setBackgroundColor(if (pageLayout == null) InkColors.pageOnScreen(darkPage) else InkColors.deskOnScreen(darkPage))
    addView(finishedView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    addView(instrumentView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    addView(inProgressView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    addView(selectionView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    addView(eraserCursor, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    // Undo and redo of photo, text-box and page changes (saved states, see StrokeHistory.recordState).
    @Suppress("UNCHECKED_CAST")
    history.stateHandler = { kind, state ->
      when (kind) {
        HistoryStates.ITEMS -> setItems(state as List<PageItem>)
        HistoryStates.PAGES -> applyPages(state as SavedPages)
        HistoryStates.LAYERS -> applyLayers(state as SavedLayers)
      }
    }
    history.listener =
      object : StrokeHistory.Listener<InkStroke> {
        override fun onInserted(index: Int, stroke: InkStroke) {
          chunkCache.onInserted(index, stroke)
          projectorHistoryVersion++
          inkChanged(stroke, added = true)
        }

        override fun onRemoved(index: Int, stroke: InkStroke) {
          chunkCache.onRemoved(index, stroke)
          projectorHistoryVersion++
          inkChanged(stroke, added = false)
        }
      }

    inProgressView.addFinishedStrokesListener(
      object : InProgressStrokesFinishedListener {
        override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
          // Draw them on the bottom layer first, then drop them from the top layer, so the
          // stroke never disappears for a frame in between.
          strokes.forEach { (id, drawn) -> store(drawn, pendingStrokes.remove(id)) }
          // The committed stroke is in the chunk cache now. The projector keeps its own copy of the
          // line until a snapshot that contains the committed stroke has been published.
          strokes.keys.forEach { liveMirror.committed(it, projectorHistoryVersion) }
          if (projectorSink != null) requestProjectorPublish()
          pageChanged()
          inProgressView.removeFinishedStrokes(strokes.keys)
        }
      }
    )
    // Set up the front buffer now instead of on the first pen-down (avoids a first-stroke hitch).
    inProgressView.eagerInit()
  }

  /**
   * Clears the page (can be undone). In an A4 notebook that is the page in the middle of the
   * screen, not the whole notebook. Returns true if anything was cleared.
   */
  fun clear(): Boolean {
    clearSelection()
    inProgressView.cancelUnfinishedStrokes()
    activeStrokeId = null
    pendingStrokes.clear()
    liveMirror.endAll()
    val layout = pageLayout
    val cleared =
      if (layout == null) {
        history.grouped {
          val strokesGone = history.removeAll { !isHiddenLayer(it) } // hidden layers are left alone
          val hadItems = items.isNotEmpty()
          changeItems { it.clear() }
          strokesGone || hadItems
        }
      } else {
        val top = layout.top(currentPage)
        val bottom = top + layout.pitch
        history.grouped {
          val strokesGone = history.removeAll { !isHiddenLayer(it) && chunkCache.boundsOf(it).centerY().let { y -> y >= top && y < bottom } }
          val before = items.size
          changeItems { list -> list.removeAll { it.cy >= top && it.cy < bottom } }
          strokesGone || items.size != before
        }
      }
    if (cleared) pageChanged()
    return cleared
  }

  fun undo() {
    clearSelection()
    if (history.undo()) pageChanged()
  }

  fun redo() {
    clearSelection()
    if (history.redo()) pageChanged()
  }

  private fun pageChanged() {
    clearThumbnails()
    finishedView.invalidate()
    onHistoryChanged()
  }

  /** Zooms around the middle of the screen, e.g. 1.25 = 25% closer. Used by keyboard zoom. */
  fun zoomBy(factor: Float) = panZoomBy(0f, 0f, factor, 0f, width / 2f, height / 2f)

  // ---- Saving -------------------------------------------------------------------------------

  /** Everything needed to save the page. Cheap: it copies references, not stroke data. */
  fun snapshot(): PageSnapshot {
    val strokes = history.strokes.toList()
    val view = FloatArray(9).also(pageToScreen::getValues)
    return PageSnapshot(strokes, pageStyle, view, items.toList(), history.saveableSteps(MAX_SAVED_UNDO_STEPS))
  }

  /** Puts a saved page on the (empty) canvas, including where it was panned and zoomed to. */
  fun load(page: LoadedPage) {
    awaitingLoad = false
    loadingStrokes = true
    history.load(page.strokes)
    loadingStrokes = false
    // Undo carries on from the last session.
    page.history?.let { (undo, redo) ->
      history.restoreSteps(undo, redo)
      onHistoryChanged()
    }
    items.clear()
    items.addAll(page.items)
    pageToScreen.setValues(page.view)
    // No saved view (an imported or turned notebook): start fitted to the screen width.
    if (pageToScreen.isIdentity && pageLayout != null) post { fitPage() }
    // A view saved in landscape is too wide for portrait: fit the width instead.
    else if (pageLayout != null) post { if (height > width && pageOverflows()) fitToWidth() }
    pageStyle = page.pageStyle
    invalidateProjectorContent()
    pageChanged()
    viewMoved()
    // Saved while pulled past the last page: settle back to the end.
    if (pullDistance > 0f) post { springBackFromPull() }
    post { warmMeshes() }
  }

  /**
   * Builds the drawable strokes of the whole notebook in the background right after opening,
   * nearest the view first, up to most of the mesh budget (user request: strokes appeared late
   * on the first visit to a page, then stayed). Batches of 300, so pages fill in as they go.
   */
  private fun warmMeshes() {
    val centre = floatArrayOf(width / 2f, height / 2f).also { screenToPage.mapPoints(it) }
    val middle = centre[1]
    var points = 0
    val limit = StrokeMeshes.BUDGET * 3 / 4
    val ordered = history.strokes.sortedBy { abs(it.bounds.centerY() - middle) }.takeWhile {
      points += it.pointCount
      points <= limit
    }
    for (batch in ordered.chunked(300)) StrokeMeshes.prefetch(batch) { finishedView.postInvalidateOnAnimation() }
  }

  /** Adds ready-made strokes (page coordinates, paper colours). Used by the stress test. */
  fun addStrokes(strokes: List<InkStroke>) {
    loadingStrokes = true
    history.load(strokes) // bulk, not undoable: test pages only
    loadingStrokes = false
    pageChanged()
  }

  /** The first pen's brush, for building test strokes that look like real ones. */
  val brush: Brush
    get() = paperBrush(InkTools.defaults.first(), PEN_DETAIL_PX)

  // ---- Input -----------------------------------------------------------------------------

  /** Pen hovering above the glass: no drawing, but it tells us the writing hand is here. */
  override fun dispatchHoverEvent(event: MotionEvent): Boolean {
    if (event.isPen(0)) {
      penHovering = event.actionMasked != MotionEvent.ACTION_HOVER_EXIT
      lastHoverTime = event.eventTime
      lastPenEventTime = event.eventTime
    }
    return super.dispatchHoverEvent(event)
  }

  /** A dialog or panel window opened or closed over us: the pen's hover state here is unknown, start fresh. */
  override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
    super.onWindowFocusChanged(hasWindowFocus)
    penHovering = false
  }

  /**
   * False while the notebook's saved page is still being read: [load] replaces every stroke, so ink written before it
   * finished was wiped and never saved (audit BUG-15). Until then the pen draws nothing (fingers still pan and zoom),
   * so nothing is lost silently. The host clears it in its load callback.
   */
  var awaitingLoad = false

  @SuppressLint("ClickableViewAccessibility") // a drawing surface has no "click"
  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (awaitingLoad && (0 until event.pointerCount).any { event.getToolType(it) == MotionEvent.TOOL_TYPE_STYLUS || event.getToolType(it) == MotionEvent.TOOL_TYPE_ERASER }) return true
    // We always return true: if the palm lands first and we refused that gesture, Android would
    // also withhold the pen that joins it a moment later.
    // Any new touch stops a glide in progress, so the page is exactly where the finger finds it.
    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
      stopFling()
      pullAtDown = pullDistance
      pullTouching = true
      pullPastSince = PullToAdd.NOT_PAST
      if (!runningFingerPenCopy) fingerPenActive = false // a new touch starts clean
    }

    // Eyedropper: the next touch picks up the colour of the ink under it.
    onColorSampled?.let { done ->
      if (event.actionMasked == MotionEvent.ACTION_UP) {
        onColorSampled = null
        done(colorAt(event.x, event.y))
      }
      return true
    }

    // A finger on the ruler or protractor moves and turns it instead of panning.
    if (handleInstrumentFingers(event)) return true

    // A finger on the selection moves or resizes it instead of panning.
    if (handleSelectionFinger(event)) return true

    if (!runningFingerPenCopy) tapDetector.onTouchEvent(event)
    prepareFingerPen(event)
    if (swallowFingerPress(event)) return true
    // Diagnostic (14 Nov, the random "can't pan" bug): when a finger lands while the palm rule is
    // on, log why, so `adb logcat -s PalmRule` shows the cause the next time it happens.
    if (event.actionMasked == MotionEvent.ACTION_DOWN && event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER && penNearby) {
      val now = SystemClock.uptimeMillis()
      android.util.Log.i(
        "PalmRule",
        "finger ignored: penDown=${penPointerId != MotionEvent.INVALID_POINTER_ID} hovering=$penHovering " +
          "lastHover=${now - lastHoverTime}ms lastPen=${now - lastPenEventTime}ms",
      )
    }
    panZoomDetector.onTouchEvent(event, penNearby || fingerPenActive)
    trackFingerTap(event)
    if (fingerPenActive && !runningFingerPenCopy && runFingerPenCopy(event)) return true

    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN,
      MotionEvent.ACTION_POINTER_DOWN -> {
        val index = event.actionIndex
        // Only this very press (a press that landed on a menu button never reaches here).
        val onlyCloses = pressOnlyClosed(event.downTime, closedSomethingAt)
        if (penPointerId == MotionEvent.INVALID_POINTER_ID && event.isPen(index)) {
          penPointerId = event.getPointerId(index)
          panZoomDetector.cancel() // the page must hold still under the pen
          onPageTouched()
          if (onlyCloses) {
            // This press closed a panel or the wheel: it does not also leave a dot of ink.
            shapeSnapped = true
            return true
          }
          // Deliver pen samples as they arrive (up to 240/s) instead of once per frame.
          requestUnbufferedDispatch(event)
          predictor.record(event)
          when {
            isErasing(event, index) -> startErasing(event, index)
            // A selection (from the lasso or a paste) can be moved or resized with any tool.
            tool.isLasso || isOnSelection(event, index) -> startLasso(event, index)
            tool.isText -> {
              clearSelection()
              textToolAt(event.getX(index), event.getY(index))
              shapeSnapped = true // nothing else to do with this pen gesture
            }
            tool.isFill -> {
              clearSelection()
              fillAt(event.getX(index), event.getY(index), tool.paperColor)
              shapeSnapped = true
            }
            else -> {
              clearSelection() // writing elsewhere lets go of the selection
              val edge = instrumentEdgeAt(event, index)
              if (edge != null) startRulerStroke(event, index, edge.first, edge.second) else startStroke(event)
            }
          }
        }
      }

      MotionEvent.ACTION_MOVE -> {
        val index = event.findPointerIndex(penPointerId)
        if (index < 0) return true
        predictor.record(event)
        if (shapeSnapped) return true // the stroke became a shape; the rest of the gesture is ignored
        if (rulerInstrument != null) {
          continueRulerStroke(event, index)
          return true
        }
        if (lassoGesture != LassoGesture.NONE) {
          continueLasso(event, index)
          return true
        }
        if (erasingGesture || isErasing(event, index)) {
          // Button pressed mid-stroke: drop the half-drawn line and erase instead.
          if (activeStrokeId != null) {
            cancelStroke(event)
            startErasing(event, index)
          }
          eraseAlong(event, index)
        } else {
          continueStroke(event)
        }
      }

      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_POINTER_UP -> {
        if (event.getPointerId(event.actionIndex) == penPointerId) {
          val id = activeStrokeId
          if (id != null && rulerInstrument != null) finishRulerStroke(event, id)
          else if (id != null) {
            inProgressView.finishStroke(event, penPointerId, id)
            // Pen-up: the projector keeps the line it drew live until the committed stroke arrives.
            if (projectorSink != null) liveMirror.lift(id, SystemClock.uptimeMillis()) else liveMirror.end(id)
          }
          if (lassoGesture != LassoGesture.NONE) finishLasso()
          endPenGesture(event)
        }
      }

      MotionEvent.ACTION_CANCEL -> {
        cancelStroke(event)
        cancelLasso()
        endPenGesture(event)
      }
    }
    // The last finger lifting after pulling past the last page adds a page; otherwise a quick
    // flick keeps the page gliding.
    if (event.actionMasked == MotionEvent.ACTION_UP) {
      // Only a pull made by this touch adds a page (the view may already have been past the end).
      if (pullReady && pullDistance > pullAtDown + 8f * density) {
        addPage()
      } else if (pullDistance > 0f && event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) {
        springBackFromPull() // not far enough: cancelled
      } else if (event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) {
        if (pinchedThisGesture) snapToWidthIfClose() else startFlingIfFast()
      }
      pinchedThisGesture = false
    }
    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
      pullTouching = false
      removeCallbacks(pullHoldTick)
      pullPastSince = PullToAdd.NOT_PAST
      if (pullDistance != 0f) {
        pullDistance = 0f
        pullReady = false
        finishedView.invalidate()
      }
    }
    return true
  }

  /**
   * Decides, before pan/zoom sees the event, whether a finger starts drawing or a drawing finger
   * must give up (a second finger or the pen landed). Giving up hands the first finger to
   * pan/zoom so the pair works as a pinch.
   */
  private fun prepareFingerPen(event: MotionEvent) {
    if (runningFingerPenCopy) return
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        fingerPenActive = false
        fingerSwallowed = false
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER && !penNearby && fingerDrawEnabled()) {
          if (pressOnlyClosed(event.downTime, closedSomethingAt)) {
            // Same rule as the pen: the press that closed a panel or the wheel draws nothing.
            fingerSwallowed = true
          } else {
            fingerPenActive = true
            fingerPenId = event.getPointerId(0)
          }
        }
      }
      MotionEvent.ACTION_POINTER_DOWN ->
        if (fingerPenActive) {
          fingerPenActive = false
          cancelStroke(event) // not committed: a pinch leaves no mark
          cancelLasso()
          penPointerId = MotionEvent.INVALID_POINTER_ID // so no "pen nearby" grace follows
          endPenGesture(event)
          if (!event.isPen(event.actionIndex)) {
            // The finger that was drawing joins the pinch.
            event.findPointerIndex(fingerPenId).takeIf { it >= 0 }?.let { panZoomDetector.adopt(event, it) }
          }
        }
    }
  }

  /**
   * Ignores the rest of a finger gesture whose first press only closed a panel (see
   * [fingerSwallowed]). A second finger ends the swallowing so a pinch still works.
   */
  private fun swallowFingerPress(event: MotionEvent): Boolean {
    if (!fingerSwallowed || runningFingerPenCopy) return false
    when (event.actionMasked) {
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> fingerSwallowed = false
      MotionEvent.ACTION_POINTER_DOWN -> {
        fingerSwallowed = false
        return false
      }
    }
    return true
  }

  /**
   * Runs a one-pointer stylus copy of [event] through [onTouchEvent] (the pen code). Returns
   * true if it did, so the caller can stop.
   */
  private fun runFingerPenCopy(event: MotionEvent): Boolean {
    val index = event.findPointerIndex(fingerPenId)
    if (index < 0) return false
    val action =
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> event.actionMasked
        else -> return false
      }
    val last = event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL
    val properties = arrayOf(MotionEvent.PointerProperties().apply { id = fingerPenId; toolType = MotionEvent.TOOL_TYPE_STYLUS })
    val coords = arrayOf(MotionEvent.PointerCoords())
    fun sample(h: Int) { // h < 0: the newest sample
      coords[0].clear()
      coords[0].x = if (h < 0) event.getX(index) else event.getHistoricalX(index, h)
      coords[0].y = if (h < 0) event.getY(index) else event.getHistoricalY(index, h)
      coords[0].pressure = FINGER_PEN_PRESSURE
      coords[0].size = event.getSize(index)
    }
    // Start with the oldest sample, then add the rest, so Ink sees every point of the batch.
    val oldest = if (event.historySize > 0) 0 else -1
    sample(oldest)
    val copy =
      MotionEvent.obtain(
        event.downTime,
        if (oldest == 0) event.getHistoricalEventTime(0) else event.eventTime,
        action, 1, properties, coords,
        event.metaState, event.buttonState, event.xPrecision, event.yPrecision,
        event.deviceId, event.edgeFlags, event.source, event.flags,
      )
    try {
      if (oldest == 0) {
        for (h in 1 until event.historySize) {
          sample(h)
          copy.addBatch(event.getHistoricalEventTime(h), coords, event.metaState)
        }
        sample(-1)
        copy.addBatch(event.eventTime, coords, event.metaState)
      }
      runningFingerPenCopy = true
      onTouchEvent(copy)
    } finally {
      runningFingerPenCopy = false
      copy.recycle()
    }
    if (last) fingerPenActive = false
    return true
  }

  // ---- Notebook pages --------------------------------------------------------------------

  /** How far (screen px) the view is pulled past the end of the last page; 0 when it is not. */
  private var pullDistance = 0f
  private var pullReady = false

  /** How far it was already pulled when the current touch began. */
  private var pullAtDown = 0f

  /** A touch is down (set at ACTION_DOWN, cleared at UP/CANCEL). */
  private var pullTouching = false

  /** When this touch's pull went past the threshold and stayed (uptime ms), or [PullToAdd.NOT_PAST]. */
  private var pullPastSince = PullToAdd.NOT_PAST

  /** The finger may be perfectly still at the threshold (no events), so a timer flips "ready" and redraws. */
  private val pullHoldTick = Runnable {
    updatePull()
    finishedView.invalidate()
  }

  /**
   * Whether the "pull to add" hint belongs on screen: only while a touch is pulling the page past
   * the end (further than it already was when the touch began), or while the page springs back.
   * Being past the end by geometry alone (a short notebook, a turned tablet, a fitted page) is
   * not a pull, so it shows nothing and can never be "ready".
   */
  private fun pullIsLive(): Boolean =
    PullToAdd.isLive(pullDistance, pullAtDown, pullTouching, pullBack?.isRunning == true, 8f * density)

  private fun updatePull() {
    val layout = pageLayout ?: return
    val bottom = floatArrayOf(width / 2f, height - bottomInset)
    screenToPage.mapPoints(bottom)
    val past = (bottom[1] - layout.totalHeight - layout.gap) * pageToScreen.mapRadius(1f)
    pullDistance = past.coerceAtLeast(0f)
    val wasReady = pullReady
    val now = SystemClock.uptimeMillis()
    val over = PullToAdd.isPastThreshold(pullIsLive(), pullTouching, pullDistance, PULL_TO_ADD_DP * density)
    val wasPast = pullPastSince != PullToAdd.NOT_PAST
    pullPastSince = PullToAdd.heldSince(pullPastSince, over, now)
    pullReady = PullToAdd.isReady(pullPastSince, now)
    if (over || wasPast) removeCallbacks(pullHoldTick) // only while a pull is in progress: panning stays cheap
    if (over && !pullReady) postDelayed(pullHoldTick, PullToAdd.remainingMs(pullPastSince, now))
    if (pullReady != wasReady) performHapticFeedback(if (pullReady) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.TEXT_HANDLE_MOVE)
  }

  /** Adds a blank page at the end. */
  fun addPage() {
    val layout = pageLayout ?: return
    pullReady = false
    pullPastSince = PullToAdd.NOT_PAST
    removeCallbacks(pullHoldTick)
    pullDistance = 0f
    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    insertPage(layout.count)
  }

  /**
   * Inserts a blank page so it becomes page [at] (0 = first): everything from that page on
   * moves down one page, bookmarks included. One undo step.
   */
  fun insertPage(at: Int) {
    val layout = pageLayout ?: return
    val index = at.coerceIn(0, layout.count)
    clearSelection()
    history.grouped {
      val backgrounds = layout.backgrounds.takeIf { it.isNotEmpty() }?.toMutableList()?.apply { add(index, -1) }
      changePages(layout.count + 1, backgrounds) { mark -> if (mark >= index) mark + 1 else mark }
      shiftStrokesBelow(layout.top(index), layout.pitch)
    }
    pageChanged()
  }

  /** Removes page [page] and its strokes; later pages move up. One undo step. */
  fun deletePage(page: Int) {
    val layout = pageLayout ?: return
    if (layout.count <= 1 || page !in 0 until layout.count) return
    clearSelection()
    val top = layout.top(page)
    val bottom = top + layout.pitch
    history.grouped {
      history.removeAll { chunkCache.boundsOf(it).centerY().let { y -> y >= top && y < bottom } }
      changeItems { list -> list.removeAll { it.cy >= top && it.cy < bottom } }
      shiftStrokesBelow(bottom, -layout.pitch)
      val backgrounds = layout.backgrounds.takeIf { it.isNotEmpty() }?.toMutableList()?.apply { removeAt(page) }
      changePages(layout.count - 1, backgrounds) { mark -> if (mark == page) null else if (mark > page) mark - 1 else mark }
    }
    pageChanged()
  }

  fun toggleBookmark(page: Int) {
    val layout = pageLayout ?: return
    layout.bookmarks = if (page in layout.bookmarks) layout.bookmarks - page else layout.bookmarks + page
    pagesChanged()
  }

  /** Where the view is (pan, zoom, angle), to come back to later with [restoreView]. */
  fun currentView(): FloatArray = FloatArray(9).also(pageToScreen::getValues)

  fun restoreView(values: FloatArray) {
    stopFling()
    pageToScreen.setValues(values)
    viewMoved()
  }

  /** Scrolls so the top of [page] is near the top of the screen, keeping zoom and angle. */
  fun scrollToPage(page: Int) {
    val layout = pageLayout ?: return
    val point = floatArrayOf(layout.pageWidth / 2f, layout.top(page.coerceIn(0, layout.count - 1)))
    pageToScreen.mapPoints(point)
    pageToScreen.postTranslate(width / 2f - point[0], topClearance() - point[1])
    viewMoved()
  }

  /** The page in the middle of the screen. */
  val currentPage: Int
    get() {
      val layout = pageLayout ?: return 0
      val middle = floatArrayOf(width / 2f, height / 2f)
      screenToPage.mapPoints(middle)
      return layout.pageAt(middle[1])
    }

  private var reportedPage = -1

  /** Moves every stroke whose middle is at or below [y] by [dy] (one replace step). */
  private fun shiftStrokesBelow(y: Float, dy: Float) {
    if (items.any { it.cy >= y }) {
      val move = Matrix().apply { setTranslate(0f, dy) }
      changeItems { list -> for (i in list.indices) if (list[i].cy >= y) list[i] = list[i].mapped(move) }
    }
    val moving = history.strokes.filter { chunkCache.boundsOf(it).centerY() >= y }
    if (moving.isEmpty()) return
    val copies = moving.associateWith { it.translated(0f, dy) }
    history.replace(copies)
  }

  /** Changes the page count and renumbers bookmarks, recorded so undo puts both back. */
  /**
   * Changes the page count, renumbers bookmarks and (for PDF notebooks) sets which PDF page is
   * under each page, recorded so undo puts it all back.
   */
  private fun changePages(newCount: Int, newBackgrounds: List<Int>?, renumber: (Int) -> Int?) {
    val layout = pageLayout ?: return
    val oldCount = layout.count
    val oldMarks = layout.bookmarks
    val oldBackgrounds = layout.backgrounds
    val newMarks = oldMarks.mapNotNull(renumber).filter { it in 0 until newCount }.toSet()
    val backgrounds = newBackgrounds ?: oldBackgrounds
    val before = SavedPages(oldCount, oldMarks.sorted(), oldBackgrounds)
    val after = SavedPages(newCount, newMarks.sorted(), backgrounds)
    applyPages(after)
    history.recordState(HistoryStates.PAGES, before, after)
  }

  /** Puts a page count, bookmarks and PDF pages in place (a page change, its undo or redo). */
  private fun applyPages(state: SavedPages) {
    val layout = pageLayout ?: return
    layout.count = state.count
    layout.bookmarks = state.bookmarks.toSet()
    layout.backgrounds = state.backgrounds
    screenVersions.clear() // which pages are white may have changed
    pagesChanged()
  }

  private fun pagesChanged() {
    clearThumbnails()
    rasters.invalidateAll() // page numbers moved
    freshInk.clear()
    // The last page was deleted while showing it: go to the new last page, and let the page
    // counter catch up (it showed "5 / 4").
    pageLayout?.let { layout ->
      if (width > 0) {
        val middle = floatArrayOf(width / 2f, height / 2f).also(screenToPage::mapPoints)
        if (middle[1] > layout.totalHeight + layout.gap) post { scrollToPage(layout.count - 1) }
      }
      reportedPage = -1
      post { reportView() }
    }
    updatePull()
    finishedView.invalidate()
    onPagesChanged()
    onHistoryChanged() // worth saving
  }

  // ---- Page thumbnails -------------------------------------------------------------------

  private val thumbnails = HashMap<Int, Bitmap>()
  private val thumbnailRenderer by lazy { CanvasStrokeRenderer.create() }

  // Thumbnails are drawn on their own thread (user request: "the pages thing is slow": every
  // tile used to be drawn on the main thread, and every PDF page finishing threw them all away).
  private val thumbnailThread =
    // One thread at most, gone after 5 s idle (a new canvas is made for every notebook opened).
    java.util.concurrent.ThreadPoolExecutor(0, 1, 5, java.util.concurrent.TimeUnit.SECONDS, java.util.concurrent.LinkedBlockingQueue()) {
      Thread(it, "penlab-thumbs").apply { priority = Thread.MIN_PRIORITY }
    }
  private val thumbnailsPending = HashSet<Int>() // main thread only
  /** Pages whose thumbnail was drawn before their PDF picture was ready: redrawn when it is. */
  private val thumbnailsMissingPdf = HashSet<Int>()
  /** Bumped whenever thumbnails are thrown away, so a drawing already under way is dropped. */
  private var thumbnailGeneration = 0
  private val thumbnailItemPainter by lazy { ItemPainter(imagesDir) }
  private val thumbnailBackground by lazy {
    PageBackground(PAPER_UNITS_PER_DP, darkPage).apply {
      pageWidth = pageLayout?.pageWidth ?: 0f
      pageHeight = pageLayout?.pageHeight ?: 0f
    }
  }

  private fun clearThumbnails() {
    thumbnails.clear()
    thumbnailsMissingPdf.clear()
    thumbnailGeneration++
  }

  /**
   * A small picture of [page], [widthPx] wide, in the colours the page shows on screen, or null
   * while it is being drawn (then [onPicturesUpdated] fires when it is ready). Main thread only.
   */
  fun pageThumbnail(page: Int, widthPx: Int): Bitmap? {
    val layout = pageLayout ?: return null
    thumbnails[page]?.takeIf { it.width == widthPx }?.let { return it }
    if (!thumbnailsPending.add(page)) return null
    // Gathered here (main thread); drawn on the thumbnail thread.
    val rect = layout.rect(page, RectF())
    val pdfPage = if (pdf != null) layout.backgroundOf(page) else -1
    val slide = if (pdfPage >= 0) pdf?.bitmapFor(pdfPage, widthPx, mayRender = true) else null
    val strokes = history.strokes.filter { !isHiddenLayer(it) && RectF.intersects(it.bounds, rect) }.map { screenVersion(it) }
    val pageItems = items.filter { RectF.intersects(it.bounds(RectF()), rect) }.map { it to itemTextColorFor(it) }
    val style = pageStyle
    val spacing = rulingMm
    val dark = darkPage
    val generation = thumbnailGeneration
    thumbnailThread.execute {
      val scale = widthPx / layout.pageWidth
      val bitmap = Bitmap.createBitmap(widthPx, (layout.pageHeight * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      if (pdfPage >= 0) {
        canvas.drawColor(android.graphics.Color.WHITE)
        slide?.let { canvas.drawBitmap(it, null, RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG)) }
      } else {
        canvas.drawColor(InkColors.pageOnScreen(dark))
        canvas.save()
        canvas.scale(scale, scale)
        thumbnailBackground.setDarkPage(dark)
        thumbnailBackground.spacingMm = spacing
        thumbnailBackground.draw(canvas, style, RectF(0f, 0f, layout.pageWidth, layout.pageHeight), scale)
        canvas.restore()
      }
      val matrix = Matrix().apply {
        setTranslate(0f, -rect.top)
        postScale(scale, scale)
      }
      canvas.concat(matrix)
      for ((item, colour) in pageItems) thumbnailItemPainter.draw(canvas, item, colour)
      for (stroke in strokes) thumbnailRenderer.draw(canvas, stroke, matrix)
      post {
        thumbnailsPending.remove(page)
        if (generation != thumbnailGeneration) return@post // content changed meanwhile: drawn again on request
        thumbnails[page] = bitmap
        if (pdfPage >= 0 && slide == null) thumbnailsMissingPdf += page
        onPicturesUpdated()
      }
    }
    return null
  }

  // ---- Pictures of pages (paper colours: dark ink on white, as printed) ----------------------

  private val paperRenderer by lazy { CanvasStrokeRenderer.create() }

  /** Draws [strokes] through [matrix] (built fresh, not cached), recolouring for dark paper when [dark] is true. */
  private fun drawPictureInk(canvas: Canvas, strokes: List<InkStroke>, matrix: Matrix, dark: Boolean) {
    for (ink in strokes) {
      if (isHiddenLayer(ink)) continue
      val brush = if (dark) Brushes.recoloured(ink.brush, InkColors.onScreen(ink.brush.colorIntArgb, true)) else ink.brush
      paperRenderer.draw(canvas, Stroke(brush, ink.inputBatch()), matrix)
    }
  }

  /** Draws [strokes] in their paper colours through [matrix] (built fresh, not cached). */
  private fun drawPaperInk(canvas: Canvas, strokes: List<InkStroke>, matrix: Matrix) {
    drawPictureInk(canvas, strokes, matrix, dark = false)
  }

  /**
   * A page as it would print (or in dark-paper screen colours when [darkPage] is true on a plain
   * page), [widthPx] wide. On an endless canvas: what is on screen.
   */
  fun pagePicture(page: Int = currentPage, widthPx: Int = 1200, darkPage: Boolean = false): Bitmap {
    val layout = pageLayout
    val area = if (layout != null) layout.rect(page, RectF()) else RectF(0f, 0f, width.toFloat(), height.toFloat()).also { screenToPage.mapRect(it) }
    val scale = widthPx / area.width()
    val bitmap = Bitmap.createBitmap(widthPx, (area.height() * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val pdfPage = if (layout != null && pdf != null) layout.backgroundOf(page) else -1
    val effectiveDark = darkPage && pdfPage < 0
    canvas.drawColor(InkColors.pageOnScreen(effectiveDark))
    if (pdfPage >= 0) {
      pdf?.bitmapFor(pdfPage, widthPx, mayRender = true)?.let {
        canvas.drawBitmap(it, null, RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
      }
    }
    val matrix = Matrix().apply {
      setTranslate(-area.left, -area.top)
      postScale(scale, scale)
    }
    canvas.save()
    canvas.concat(matrix)
    val colorMap: (Int) -> Int = { InkColors.onScreen(it, effectiveDark) }
    for (item in items) if (RectF.intersects(item.bounds(itemBox), area)) itemPainter.draw(canvas, item, colorMap)
    val visibleStrokes = history.strokes.filter { RectF.intersects(it.bounds, area) }
    if (!effectiveDark) drawPaperInk(canvas, visibleStrokes, matrix) else drawPictureInk(canvas, visibleStrokes, matrix, dark = true)
    canvas.restore()
    return bitmap
  }

  /**
   * The written pages of the notebook (1-based numbers; a page counts if it has ink, a photo, a text box or a PDF slide) and a way
   * to draw them that is safe OFF the main thread, at most [max], each [widthPx] wide in paper colours on white paper. The strokes and
   * items are copied now (main thread, cheap: references), and each page is drawn only when the returned sequence reaches it, so a
   * 60-page notebook never freezes the ink or sits in memory as 60 bitmaps. PDF slides are rendered on the PDF thread and waited for.
   * An endless canvas has no pages: it gives one picture of what is on screen (drawn now).
   * For plugin screens that ask for the `notebook` input (docs/plugin-bridge.md); the same shape as Graphite's version.
   */
  fun pagesForBackground(max: Int, widthPx: Int): Pair<List<Int>, () -> Sequence<Bitmap>> {
    val layout = pageLayout ?: run {
      val picture = pagePicture(widthPx = widthPx)
      return listOf(1) to { sequenceOf(picture) }
    }
    val strokes = history.strokes.filter { !isHiddenLayer(it) }
    val itemsNow = items.toList()
    val pages = (0 until layout.count).filter { page ->
      val rect = layout.rect(page, RectF())
      layout.backgroundOf(page) >= 0 ||
        itemsNow.any { RectF.intersects(it.bounds(RectF()), rect) } ||
        strokes.any { RectF.intersects(it.bounds, rect) }
    }.take(max)
    val pdfNow = pdf
    val painter = ItemPainter(imagesDir)
    val draw: () -> Sequence<Bitmap> = {
      val renderer = CanvasStrokeRenderer.create()
      pages.asSequence().map { page ->
        val area = layout.rect(page, RectF())
        val scale = widthPx / area.width()
        val bitmap = Bitmap.createBitmap(widthPx, (area.height() * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(InkColors.pageOnScreen(false))
        val slide = layout.backgroundOf(page)
        if (slide >= 0) pdfNow?.renderNow(slide, widthPx)?.let {
          canvas.drawBitmap(it, null, RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
          it.recycle()
        }
        val matrix = Matrix().apply {
          setTranslate(-area.left, -area.top)
          postScale(scale, scale)
        }
        canvas.save()
        canvas.concat(matrix)
        for (item in itemsNow) if (RectF.intersects(item.bounds(RectF()), area)) painter.draw(canvas, item) { it }
        for (ink in strokes) if (RectF.intersects(ink.bounds, area)) renderer.draw(canvas, Stroke(ink.brush, ink.inputBatch()), matrix)
        canvas.restore()
        bitmap
      }
    }
    return pages.map { it + 1 } to draw
  }

  // ---- Projector view -----------------------------------------------------------------------

  /**
   * Called whenever the view is panned, zoomed, turned or its side space changes. Set by the
   * projector view only; it must do no work of its own (it is called for every touch step).
   */
  var onViewMoved: () -> Unit = {}

  /**
   * The projector's render thread, while Projector view is on (PROTOTYPE). The canvas publishes an
   * immutable [ProjectorSnapshot] to it at most once per main-thread frame ([requestProjectorPublish]);
   * the render thread draws from that and never touches this view. Set by ProjectorPresentation.
   */
  var projectorSink: dev.penlab.app.projector.ProjectorSink? = null
    set(value) {
      field = value
      if (value == null) liveMirror.endAll() else requestProjectorPublish()
    }

  private val projectorPublishPending = java.util.concurrent.atomic.AtomicBoolean(false)
  private val projectorPublishRunnable = Runnable {
    projectorPublishPending.set(false)
    publishProjector()
    // Keep publishing while a line is live or still waiting for its committed stroke.
    if (liveMirror.needsFrames) requestProjectorPublish()
  }

  /** Publishes a snapshot at the next animation frame (coalesced). Any thread. */
  fun requestProjectorPublish() {
    if (projectorSink != null && projectorPublishPending.compareAndSet(false, true)) postOnAnimation(projectorPublishRunnable)
  }

  /** Bumped when strokes are added/removed, so the visible-stroke list is rebuilt only then or when the view moves. */
  private var projectorHistoryVersion = 0
  private var projectorContentVersion = 0L
  private var projectorSceneVersion = 0L
  private var projectorBuiltVersion = -1
  private var projectorBuiltContentVersion = -1L
  private var projectorBuiltHidden: Set<Int>? = null
  private val projectorBuiltMatrix = FloatArray(9)
  private val projectorMatrixValues = FloatArray(9)
  private var projectorStrokes: List<dev.penlab.app.projector.ProjectorInk> = emptyList()
  private var projectorPages: List<ProjectorPage> = emptyList()
  private var projectorItems: List<ProjectorItem> = emptyList()
  private var projectorBuiltPdfBitmaps: List<Bitmap?> = emptyList()
  private val projectorPageRect = RectF()

  private fun invalidateProjectorContent() {
    projectorContentVersion++
    requestProjectorPublish()
  }

  private fun publishProjector() {
    val sink = projectorSink ?: return
    val widthPx = sink.targetWidth
    val heightPx = sink.targetHeight
    val layout = pageLayout
    val desk = if (layout == null) InkColors.pageOnScreen(darkPage) else InkColors.deskOnScreen(darkPage)
    if (width == 0 || height == 0 || widthPx <= 0 || heightPx <= 0) {
      sink.onSnapshot(ProjectorSnapshot(null, desk, 0, emptyList(), emptyList(), emptyList(), imagesDir, null, 0, 0, 1f))
      return
    }
    val free = width - leftInset - rightInset
    val shown = if (free > 0f) dev.penlab.app.projector.Box(leftInset, 0f, width - rightInset, height.toFloat())
    else dev.penlab.app.projector.Box(0f, 0f, width.toFloat(), height.toFloat())
    val fitted = dev.penlab.app.projector.fitToAspect(shown, widthPx.toFloat() / heightPx)
    projectorSource.set(fitted.left, fitted.top, fitted.right, fitted.bottom)
    projectorTarget.set(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
    pageToProjector.setRectToRect(projectorSource, projectorTarget, Matrix.ScaleToFit.FILL)
    pageToProjector.preConcat(pageToScreen)
    if (!pageToProjector.invert(projectorInverse)) return
    projectorVisible.set(projectorTarget)
    projectorInverse.mapRect(projectorVisible)
    pageToProjector.getValues(projectorMatrixValues)

    val zoom = pageToProjector.mapRadius(1f)
    val hidden = layers.hidden
    val pdfDoc = pdf
    val mayRenderPdf = SystemClock.uptimeMillis() - lastPanZoomTime >= PAN_ZOOM_SETTLE_MS
    val wantedPdfWidth = if (layout != null) (layout.pageWidth * zoom).toInt().coerceAtLeast(1) else 0
    var changed = projectorBuiltVersion != projectorHistoryVersion ||
      projectorBuiltContentVersion != projectorContentVersion ||
      projectorBuiltHidden != hidden ||
      !projectorMatrixValues.contentEquals(projectorBuiltMatrix)
    if (!changed && pdfDoc != null && layout != null) {
      val first = layout.pageAt(projectorVisible.top)
      val last = layout.pageAt(projectorVisible.bottom)
      var pdfIdx = 0
      for (i in first..last) {
        layout.rect(i, projectorPageRect)
        if (!RectF.intersects(projectorPageRect, projectorVisible)) continue
        val pdfPage = layout.backgroundOf(i)
        if (pdfPage >= 0) {
          val bmp = pdfDoc.bitmapFor(pdfPage, wantedPdfWidth, mayRenderPdf)
          if (pdfIdx >= projectorBuiltPdfBitmaps.size || projectorBuiltPdfBitmaps[pdfIdx] !== bmp) {
            changed = true
            break
          }
          pdfIdx++
        }
      }
      if (!changed && pdfIdx != projectorBuiltPdfBitmaps.size) changed = true
    }
    if (changed) {
      projectorBuiltVersion = projectorHistoryVersion
      projectorBuiltContentVersion = projectorContentVersion
      projectorBuiltHidden = hidden
      System.arraycopy(projectorMatrixValues, 0, projectorBuiltMatrix, 0, 9)
      val visible = chunkCache.strokesIn(projectorVisible).filter { !isHiddenLayer(it) }
      // Highlighters sit under the ink.
      projectorStrokes = (visible.filter { it.isHighlighter } + visible.filter { !it.isHighlighter }).map(::projectorInk)
      if (layout == null) {
        projectorPages = emptyList()
        projectorBuiltPdfBitmaps = emptyList()
      } else {
        val first = layout.pageAt(projectorVisible.top)
        val last = layout.pageAt(projectorVisible.bottom)
        val pages = ArrayList<ProjectorPage>()
        val bitmaps = ArrayList<Bitmap?>()
        for (i in first..last) {
          layout.rect(i, projectorPageRect)
          if (!RectF.intersects(projectorPageRect, projectorVisible)) continue
          val pdfPage = if (pdfDoc != null) layout.backgroundOf(i) else -1
          val isPdf = pdfPage >= 0
          val bmp = if (isPdf) pdfDoc?.bitmapFor(pdfPage, wantedPdfWidth, mayRenderPdf) else null
          if (isPdf) bitmaps += bmp
          pages += ProjectorPage(projectorPageRect.left, projectorPageRect.top, projectorPageRect.right, projectorPageRect.bottom, isPdf, bmp)
        }
        projectorPages = pages
        projectorBuiltPdfBitmaps = bitmaps
      }
      projectorItems = items.mapNotNull { item ->
        if (item.id in hiddenItems || !RectF.intersects(item.bounds(itemBox), projectorVisible)) return@mapNotNull null
        val light = layout?.let { isLightPage(it.pageAt(item.cy)) } ?: false
        ProjectorItem(item, onDarkPage = darkPage && !light)
      }
      projectorSceneVersion++
    }
    val live = liveMirror.active
    val livePts = if (live) liveMirror.copyPoints() else null
    // Lines waiting for their committed stroke go out with every snapshot until one built from
    // history at or after their commit has been published (that snapshot carries the real stroke).
    val pendingLines = liveMirror.dropCommitted(projectorBuiltVersion, SystemClock.uptimeMillis())
    val accent = pageEdgeAccent
    val edgePx = (if (accent != null) 1.5f * resources.displayMetrics.density else 1f) * zoom /
      pageToScreen.mapRadius(1f).coerceAtLeast(1e-6f)
    sink.onSnapshot(
      ProjectorSnapshot(
        matrix = projectorMatrixValues.copyOf(),
        deskColor = desk,
        pageColor = if (layout == null) 0 else InkColors.pageOnScreen(darkPage),
        pages = projectorPages,
        strokes = projectorStrokes,
        items = projectorItems,
        imagesDir = imagesDir,
        livePoints = livePts,
        liveCount = livePts?.size?.div(2) ?: 0,
        liveColor = liveMirror.colorArgb,
        liveSize = liveMirror.size,
        pendingLines = pendingLines,
        pageStyle = pageStyle,
        spacingMm = rulingMm,
        unitsPerDp = if (layout == null) resources.displayMetrics.density else PAPER_UNITS_PER_DP,
        darkPage = darkPage,
        pageWidth = layout?.pageWidth ?: 0f,
        pageHeight = layout?.pageHeight ?: 0f,
        visible = floatArrayOf(projectorVisible.left, projectorVisible.top, projectorVisible.right, projectorVisible.bottom),
        zoom = zoom,
        edgeColor = if (layout == null) 0 else (accent ?: InkColors.pageEdgeOnScreen(darkPage)),
        edgeWidthPx = edgePx.coerceAtLeast(1f),
        sceneVersion = projectorSceneVersion,
      ),
    )
  }

  private val pageToProjector = Matrix()
  private val projectorInverse = Matrix()
  private val projectorSource = RectF()
  private val projectorTarget = RectF()
  private val projectorVisible = RectF()

  /** Zooms so a page fills the width of the screen, with a margin, and shows the top of page 1. */
  /**
   * Notebook scrolling rules, like other note apps (only while the page is level):
   *  - page narrower than the screen: it stays centred, so scrolling is straight up and down;
   *  - page wider than the screen: it can move sideways, but its edges never come inside the
   *    screen edges;
   *  - the first page's top cannot be pulled further down than just below the top buttons.
   * The bottom is left free so pulling past the last page can add one.
   */
  /**
   * Screen space taken by the toolbar on the left and right (pixels). Fitting, centring and the
   * sideways limits use only the space between, so no part of the page hides under the toolbar.
   */
  fun setSideInsets(left: Float, right: Float) {
    if (left == leftInset && right == rightInset) return
    leftInset = left
    rightInset = right
    viewMoved()
  }

  /**
   * Room above the first page: the top buttons' clearance, but never less than the visible status
   * bar plus a small gap, so the page edge does not rest under the clock. Present mode: none.
   */
  private fun topClearance(): Float {
    val base = topClearancePx(presenting, density)
    if (presenting) return base
    val status = androidx.core.view.ViewCompat.getRootWindowInsets(this)
      ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top ?: 0
    return maxOf(base, status + STATUS_GAP_DP * density)
  }

  private var leftInset = 0f
  private var rightInset = 0f

  /** Screen space a bottom toolbar covers (px): the end of the last page rests above it, not under it. */
  private var bottomInset = 0f

  fun setBottomInset(px: Float) {
    if (px == bottomInset) return
    bottomInset = px
    viewMoved()
  }

  private fun keepPagesInView() {
    val layout = pageLayout ?: return
    if (width == 0) return
    pageToScreen.getValues(matrixValues)
    val delta = ViewSettle.clampPagesInView(
      matrixValues = matrixValues,
      pageWidth = layout.pageWidth,
      pageHeight = layout.pageHeight,
      activePageTop = layout.top(currentPage),
      totalHeight = layout.totalHeight,
      screenWidth = width.toFloat(),
      leftInset = leftInset,
      rightInset = rightInset,
      topLimit = topClearance(),
    )
    if (delta.dx != 0f || delta.dy != 0f) {
      matrixValues[Matrix.MTRANS_X] += delta.dx
      matrixValues[Matrix.MTRANS_Y] += delta.dy
      pageToScreen.setValues(matrixValues)
      if (delta.dx != 0f) flingVx = 0f // hit a side: stop gliding sideways
    }
  }

  /**
   * Makes the page exactly as wide as the space beside the toolbar (edge to edge), keeping the
   * part of the page in the middle of the screen where it is. The Fit button.
   */
  fun fitToWidth() {
    val layout = pageLayout ?: return
    if (width == 0) return
    stopFling()
    val middle = floatArrayOf(width / 2f, height / 2f)
    screenToPage.mapPoints(middle)
    val zoom = ((width - leftInset - rightInset) / layout.pageWidth).coerceIn(minZoom, maxZoom)
    pageToScreen.setScale(zoom, zoom)
    pageToScreen.postTranslate(leftInset, height / 2f - middle[1] * zoom)
    viewMoved()
  }

  /** Whether the (level) page is wider than the space beside the toolbar. */
  private fun pageOverflows(): Boolean {
    val layout = pageLayout ?: return false
    pageToScreen.getValues(matrixValues)
    if (abs(matrixValues[Matrix.MSKEW_Y]) > 1e-3f) return false // turned on purpose: leave it
    return layout.pageWidth * matrixValues[Matrix.MSCALE_X] > (width - leftInset - rightInset) * 1.02f
  }

  /**
   * Turning the tablet (landscape ↔ portrait): a page that was fitted to the old width, or no
   * longer fits, is fitted to the new width, keeping the part that was in the middle of the
   * screen in the middle.
   */
  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    val layout = pageLayout ?: return
    if (oldw <= 0 || (w == oldw && h == oldh)) return
    pageToScreen.getValues(matrixValues)
    if (abs(matrixValues[Matrix.MSKEW_Y]) > 1e-3f || abs(matrixValues[Matrix.MSKEW_X]) > 1e-3f) {
      // Turned on purpose: leave it. A slip of a degree or less is level, so straighten it first
      // (about the middle of the screen) and the notebook rules below apply as usual.
      if (!ViewSettle.isNearLevel(angleDegrees)) return
      straightenAbout(oldw / 2f, oldh / 2f)
      pageToScreen.getValues(matrixValues)
    }
    stopFling()
    val settled = ViewSettle.afterResize(
      ViewSettle.Level(matrixValues[Matrix.MSCALE_X], matrixValues[Matrix.MTRANS_X], matrixValues[Matrix.MTRANS_Y]),
      layout.pageWidth, layout.totalHeight + layout.gap,
      oldw.toFloat(), oldh.toFloat(), w.toFloat(), h.toFloat(),
      leftInset, rightInset, bottomInset, topClearance(), minZoom, maxZoom,
    )
    pageToScreen.setScale(settled.scale, settled.scale)
    pageToScreen.postTranslate(settled.tx, settled.ty)
    viewMoved()
  }

  /** Removes a small turn, keeping the page point under ([x], [y]) where it is. Zoom is kept. */
  private fun straightenAbout(x: Float, y: Float) {
    pageToScreen.invert(screenToPage)
    val p = floatArrayOf(x, y)
    screenToPage.mapPoints(p)
    val zoom = pageToScreen.mapRadius(1f)
    pageToScreen.setScale(zoom, zoom)
    pageToScreen.postTranslate(x - p[0] * zoom, y - p[1] * zoom)
  }

  /** After a pinch: if the page ended up nearly screen-wide, snap it to exactly screen-wide. */
  private fun snapToWidthIfClose() {
    val layout = pageLayout ?: return
    pageToScreen.getValues(matrixValues)
    if (abs(matrixValues[Matrix.MSKEW_Y]) > 1e-3f) return
    val pageWidth = layout.pageWidth * matrixValues[Matrix.MSCALE_X]
    val free = width - leftInset - rightInset
    if (abs(pageWidth - free) / free < FIT_SNAP_SHARE && pageWidth != free) fitToWidth()
  }

  fun fitPageWidth() {
    val layout = pageLayout ?: return
    if (width == 0) {
      post { fitPageWidth() } // not laid out yet
      return
    }
    val margin = FIT_MARGIN_DP * density
    val zoom = ((width - 2 * margin) / layout.pageWidth).coerceIn(minZoom, maxZoom)
    pageToScreen.setScale(zoom, zoom)
    // Start below the back / page / zoom buttons along the top.
    pageToScreen.postTranslate((width - layout.pageWidth * zoom) / 2f, topClearance())
    viewMoved()
  }

  // ---- Drawing ---------------------------------------------------------------------------

  /**
   * The brushes for a new stroke starting at page height [pageY]: the one it is stored with
   * (paper colour) and the one it is drawn with live (screen colour; real colour on a white PDF
   * page). Detail follows the zoom so curves look equally smooth at any zoom.
   */
  private fun strokeBrushes(pageY: Float): Pair<Brush, Brush> {
    val detail = PEN_DETAIL_PX / pageToScreen.mapRadius(1f)
    val paperBrush = paperBrush(tool, detail)
    val onLight = pageLayout?.let { isLightPage(it.pageAt(pageY)) } ?: false
    val screenBrush = paperBrush.copyWithColorIntArgb(InkColors.onScreen(tool.paperColor, darkPage && !onLight))
    return paperBrush to screenBrush
  }

  private fun startStroke(event: MotionEvent) {
    val penPoint = floatArrayOf(0f, 0f)
    event.findPointerIndex(penPointerId).takeIf { it >= 0 }?.let {
      penPoint[0] = event.getX(it)
      penPoint[1] = event.getY(it)
      screenToPage.mapPoints(penPoint)
    }
    val (paperBrush, screenBrush) = strokeBrushes(penPoint[1])

    // Ink converts the pen position to page coordinates with screenToPage, so the stroke it
    // hands back is already where it belongs on the page.
    val id = inProgressView.startStroke(event, penPointerId, screenBrush, screenToPage, identity)
    pendingStrokes[id] = PendingStroke(tool, paperBrush)
    activeStrokeId = id
    strokeDownUptime = event.downTime

    strokePoints.clear()
    pressureSum = 0f
    liveMirror.begin(id, screenBrush)
    requestProjectorPublish() // the publish runnable keeps itself going while the stroke is live
    val index = event.findPointerIndex(penPointerId)
    if (index >= 0) {
      rememberDown(event, index)
      recordPoint(event.getX(index), event.getY(index), event.getPressure(index), event.eventTime)
      holdAnchorX = event.getX(index)
      holdAnchorY = event.getY(index)
    }
    restartHoldTimer()
  }

  // ---- Hold to perfect a shape -------------------------------------------------------------

  private val pointBuffer = FloatArray(2)

  private fun recordPoint(screenX: Float, screenY: Float, pressure: Float, timeMs: Long) {
    pointBuffer[0] = screenX
    pointBuffer[1] = screenY
    screenToPage.mapPoints(pointBuffer)
    strokePoints += Point(pointBuffer[0], pointBuffer[1])
    pressureSum += pressure
    liveMirror.add(pressure, timeMs) // two array stores for the projector's live stroke
  }

  /** Keeps the stroke's points, and restarts the hold timer whenever the pen really moves. */
  private fun trackForShape(event: MotionEvent) {
    val index = event.findPointerIndex(penPointerId)
    if (index < 0) return
    for (h in 0 until event.historySize) {
      recordPoint(event.getHistoricalX(index, h), event.getHistoricalY(index, h), event.getHistoricalPressure(index, h), event.getHistoricalEventTime(h))
    }
    recordPoint(event.getX(index), event.getY(index), event.getPressure(index), event.eventTime)
    noteMovement(event, index)
    if (hypot(event.getX(index) - holdAnchorX, event.getY(index) - holdAnchorY) > holdSlopPx) {
      holdAnchorX = event.getX(index)
      holdAnchorY = event.getY(index)
      restartHoldTimer()
    }
  }

  private fun restartHoldTimer() {
    removeCallbacks(snapToShape)
    if (!tool.isEraser && !tool.isLasso) postDelayed(snapToShape, HOLD_MS)
  }

  /**
   * The pen has held still: if the stroke so far looks like a line or shape, swap it for the
   * clean version. One undo removes the shape. A gentle tick confirms the swap.
   */
  private fun replaceWithShape() {
    // Held still without ever moving: that is a press-and-hold, which opens the paste menu.
    if (!movedFromDown) {
      // Resting the pen while thinking must not pop up a menu: with a pen it takes a longer,
      // deliberate hold (about a second); with the lasso the usual half second.
      if (tool.isLasso) openPasteMenu() else postDelayed(lassoHold, PEN_PASTE_EXTRA_HOLD_MS)
      return
    }
    if (!shapeSnappingEnabled) return
    val strokeId = activeStrokeId ?: return
    val pending = pendingStrokes[strokeId] ?: return
    val zoom = pageToScreen.mapRadius(1f)
    if (strokePoints.size < 3) return
    val extent = strokePoints.zipWithNext().sumOf { (a, b) -> hypot(b.x - a.x, b.y - a.y).toDouble() } * zoom
    if (extent < minShapePx) return // a pause inside a letter, not a shape
    val shape = ShapeRecognizer.recognize(strokePoints) ?: return

    val pressure = pressureSum / strokePoints.size
    val clean = strokeAlong(shape.points, pending.paperBrush, pressure, spacing = SHAPE_POINT_SPACING_PX / zoom)
    // The shape replaces the hand-drawn line, so it carries the mark of when that line was begun.
    addToPage(InkStroke.from(clean, pending.tool.isHighlighter, layers.current, mediaMarkNow { SystemClock.uptimeMillis() - strokeDownUptime }), pending.tool)
    liveMirror.end(strokeId) // the clean shape is on the page: the projector shows it instead of the hand-drawn line
    pageChanged()
    performHapticFeedback(HapticFeedbackConstants.CONFIRM)

    // Remove the hand-drawn version on the next frame, together with the clean one appearing,
    // so there is no blank frame between them.
    pendingStrokes.remove(strokeId)
    activeStrokeId = null
    shapeSnapped = true
    postOnAnimation { inProgressView.cancelStroke(strokeId) }
  }

  private fun paperBrush(tool: InkTool, detail: Float): Brush =
    Brushes.get(PenBrushes.familyFor(tool, pressureSensitivity), tool.paperColor, tool.sizeDp * strokeUnitsPerDp, detail)

  /** Puts a stroke the pen just finished onto the page. */
  private fun store(drawn: Stroke, pending: PendingStroke?) {
    val highlighter = pending?.tool?.isHighlighter == true
    val stroke =
      if (darkPage && pending != null) {
        // Keep the drawn stroke as the screen version; store a paper-colour copy.
        val paper = drawn.copy(pending.paperBrush)
        screenVersions[paper] = drawn
        InkStroke.from(paper, highlighter, layers.current, mediaMarkFor(drawn))
      } else {
        InkStroke.from(drawn, highlighter, layers.current, mediaMarkFor(drawn))
      }
    addToPage(stroke, pending?.tool)
  }

  // ---- Media (lecture recording plugin) -----------------------------------------------------

  /**
   * While a media plugin records for this notebook: the mark for the stroke finishing now ([MediaMark]); the argument says how many
   * milliseconds it took to draw (asked only when a recording is live); [MediaMark.NONE] otherwise. Set by the screen that owns the recording session. Called
   * once per stroke at pen-up, never while the pen is down: the pen's path does no media work.
   */
  var mediaMarkNow: (strokeDurationMs: () -> Long) -> Long = { MediaMark.NONE }

  /**
   * A finger tap on ink with a mark, when a media plugin is enabled: called with the mark's recording number and position
   * (ms); returns true if the tap was taken (a media plugin will play from there). Null or false: marked ink is plain ink.
   */
  var onMediaSeek: ((recordingIndex: Int, positionMs: Long) -> Boolean)? = null

  /** The mark for a stroke Ink just finished: back-dated by how long it took to draw (its last input's elapsed time). */
  private fun mediaMarkFor(drawn: Stroke): Long {
    return mediaMarkNow {
      // Asked only while something records: the duration is the last input's elapsed time.
      val inputs = drawn.inputs
      if (inputs.size > 0) StrokeInput().also { inputs.populate(inputs.size - 1, it) }.elapsedTimeMillis else 0L
    }
  }

  /** The mark of the ink under a screen point (nearest stroke within a finger's reach), or NONE. */
  private fun mediaMarkAt(x: Float, y: Float): Long {
    val p = floatArrayOf(x, y)
    screenToPage.mapPoints(p)
    val reach = FINGER_REACH_DP * density / pageToScreen.mapRadius(1f)
    var best = MediaMark.NONE
    var bestDist = Float.MAX_VALUE
    for (stroke in chunkCache.strokesIn(RectF(p[0] - reach, p[1] - reach, p[0] + reach, p[1] + reach))) {
      if (stroke.mediaMark == MediaMark.NONE || isHiddenLayer(stroke)) continue
      for (i in 0 until stroke.pointCount) {
        val d = hypot(stroke.x(i) - p[0], stroke.y(i) - p[1])
        if (d < bestDist && d <= reach) {
          bestDist = d
          best = stroke.mediaMark
        }
      }
    }
    return best
  }

  /**
   * Eyedropper: when set, the next touch on the page (pen or finger) is not drawing; on lift it
   * reports the paper colour of the topmost ink under it (null if there is none) and clears itself.
   */
  var onColorSampled: ((Int?) -> Unit)? = null

  /** The paper colour of the topmost stroke at a screen point, or null. */
  private fun colorAt(screenX: Float, screenY: Float): Int? {
    val p = floatArrayOf(screenX, screenY).also(screenToPage::mapPoints)
    val reach = 6f * density / pageToScreen.mapRadius(1f)
    val area = ImmutableParallelogram.fromCenterAndDimensions(ImmutableVec(p[0], p[1]), 2 * reach, 2 * reach)
    for (i in history.strokes.indices.reversed()) {
      val stroke = history.strokes[i]
      if (!stroke.bounds.contains(p[0], p[1]) || isHiddenLayer(stroke)) continue
      if (stroke.stroke.shape.intersects(area, AffineTransform.IDENTITY)) return stroke.brush.colorIntArgb
    }
    return null
  }

  /** Puts a stroke (in paper colours) on the page: highlights under the ink, the rest on top. */
  private fun addToPage(stroke: InkStroke, tool: InkTool? = null) {
    val layout = pageLayout
    val last = (layout?.count ?: 0) - 1
    val y = stroke.bounds.centerY()
    // Writing in the bottom of the last page: a fresh page appears below, ready to go on. The
    // stroke and the new page are one undo step.
    val needsPage = layout != null && y >= layout.top(last) + layout.pageHeight * AUTO_PAGE_AT && y < layout.top(last) + layout.pitch
    if (!needsPage) return addToPageOnly(stroke, tool)
    history.grouped {
      addToPageOnly(stroke, tool)
      insertPage(layout!!.count)
    }
  }

  private fun addToPageOnly(stroke: InkStroke, tool: InkTool? = null) {
    val line = tool?.line ?: LineType.SOLID
    if (tool?.kind == InkTool.Kind.PEN && line != LineType.SOLID) {
      // Dashed and dotted lines: one stroke per dash, added as one undo step. The dash length
      // follows the line width but never gets shorter than for a 0.35 mm pen.
      val unit = maxOf(stroke.brush.size, MIN_DASH_UNIT_MM * itemMm)
      history.grouped { LinePattern.split(stroke, line, unit).forEach { history.add(it, insertIndexFor(it)) } }
      return
    }
    history.add(stroke, insertIndexFor(stroke))
  }

  /**
   * Where a stroke goes in the drawing order: strokes are kept in layer order (layer 0 at the
   * bottom); within its layer, a highlight goes under the ink (after the other highlights) and
   * ink goes on top.
   */
  private fun insertIndexFor(stroke: InkStroke): Int {
    val list = history.strokes
    val layer = stroke.layer
    var start = list.size
    var end = list.size
    for (i in list.indices) {
      val l = list[i].layer
      if (l >= layer && start == list.size) start = i
      if (l > layer) {
        end = i
        break
      }
    }
    if (start > end) start = end
    if (!stroke.isHighlighter) return end
    for (i in start until end) if (!list[i].isHighlighter) return i
    return end
  }

  // ---- Layers ------------------------------------------------------------------------------

  /** The layers' names (bottom first), which are hidden, and which one the pen draws on. */
  data class Layers(val names: List<String> = listOf("Layer 1"), val hidden: Set<Int> = emptySet(), val current: Int = 0)

  var layers: Layers = Layers()
    private set

  /** Called when layers change (added, hidden, renamed...), so the screen can update and save. */
  var onLayersChanged: () -> Unit = {}

  /** Puts saved layers in place when a notebook opens. */
  fun loadLayers(value: Layers) {
    layers = value.copy(current = value.current.coerceIn(0, value.names.lastIndex.coerceAtLeast(0)))
    layersVisibilityChanged()
  }

  fun selectLayer(index: Int) {
    if (index !in layers.names.indices) return
    layers = layers.copy(current = index, hidden = layers.hidden - index) // drawing on a hidden layer would surprise
    layersVisibilityChanged()
  }

  fun setLayerVisible(index: Int, visible: Boolean) {
    if (index !in layers.names.indices) return
    layers = layers.copy(hidden = if (visible) layers.hidden - index else layers.hidden + index)
    clearSelection()
    layersVisibilityChanged()
  }

  private fun Layers.toSaved(): SavedLayers = SavedLayers(names, hidden.sorted(), current)

  private fun SavedLayers.toLayers(): Layers = Layers(names, hidden.toSet(), current)

  /** Adds a layer on top and draws on it. One undo step. */
  fun addLayer() {
    val before = layers.toSaved()
    val after = addLayerSaved(before)
    history.recordState(HistoryStates.LAYERS, before, after)
    layers = after.toLayers()
    layersVisibilityChanged()
    onHistoryChanged()
  }

  fun renameLayer(index: Int, name: String) {
    val before = layers.toSaved()
    val after = renameLayerSaved(before, index, name) ?: return
    history.recordState(HistoryStates.LAYERS, before, after)
    layers = after.toLayers()
    onLayersChanged()
    onHistoryChanged()
  }

  /**
   * Deletes layer [index] and its ink; layers above move down one. One undo step brings it
   * all back. The last layer cannot be deleted.
   */
  fun deleteLayer(index: Int) {
    val before = layers.toSaved()
    val after = deleteLayerSaved(before, index) ?: return
    clearSelection()
    history.grouped {
      history.removeAll { it.layer == index }
      // Strokes on higher layers take the next number down.
      val renumbered = history.strokes.filter { it.layer > index }.associateWith { it.withLayer(it.layer - 1) }
      if (renumbered.isNotEmpty()) history.replace(renumbered)
      history.recordState(HistoryStates.LAYERS, before, after)
    }
    layers = after.toLayers()
    layersVisibilityChanged()
    pageChanged()
  }

  /** Undo/redo of a layer list change (add, rename, delete). */
  private fun applyLayers(state: SavedLayers) {
    layers = applySavedLayers(layers.toSaved(), state).toLayers()
    layersVisibilityChanged()
  }

  private fun isHiddenLayer(stroke: InkStroke) = stroke.layer in layers.hidden

  private fun layersVisibilityChanged() {
    chunkCache.hiddenLayers = layers.hidden
    rasters.invalidateAll()
    tiles.invalidateAll()
    clearThumbnails()
    finishedView.invalidate()
    onLayersChanged()
  }

  /** A committed stroke for the projector: screen colour and size, drawn there as a round polyline. */
  private fun projectorInk(ink: InkStroke): dev.penlab.app.projector.ProjectorInk {
    val color = ink.brush.colorIntArgb
    val shown = if (!darkPage || isOnLightPage(ink)) color else InkColors.onScreen(color, true)
    return dev.penlab.app.projector.ProjectorInk(ink, shown, ink.brush.size, ink.isHighlighter)
  }

  /** The stroke as it should look on screen: itself, or its lightness-inverted twin. */
  private fun screenVersion(ink: InkStroke): Stroke {
    val stroke = ink.stroke
    return if (!darkPage || isOnLightPage(ink)) stroke
    else
      synchronized(screenVersions) {
        screenVersions.getOrPut(stroke) {
          val brush = stroke.brush
          stroke.copy(brush.copyWithColorIntArgb(InkColors.onScreen(brush.colorIntArgb, true)))
        }
      }
  }

  private fun continueStroke(event: MotionEvent) {
    // Pen came down erasing and the button was released: start drawing from here.
    eraserCursor.hide()
    val strokeId = activeStrokeId ?: return startStroke(event)
    trackForShape(event)
    val predicted = predictor.predict()
    try {
      inProgressView.addToStroke(event, penPointerId, strokeId, predicted)
    } finally {
      predicted?.recycle()
    }
  }

  private fun cancelStroke(event: MotionEvent) {
    activeStrokeId?.let {
      inProgressView.cancelStroke(it, event)
      pendingStrokes.remove(it)
      liveMirror.end(it)
    }
    activeStrokeId = null
    removeCallbacks(snapToShape)
  }

  // ---- Ruler and protractor -------------------------------------------------------------

  /** Page units per millimetre: fixed on notebook paper, screen pixels per mm at 100% otherwise. */
  /** Page units per millimetre: 10 in notebooks; on an endless canvas, screen pixels at 100%. */
  val unitsPerMm: Float =
    if (pageLayout != null) PageLayout.UNITS_PER_MM else resources.displayMetrics.xdpi / 25.4f

  private var instrumentStyle = InstrumentStyle(darkPage)
  private var ruler: Ruler? = null
  private var protractor: Protractor? = null

  /** Vertical span (`top..bottom` root px) of any horizontal/floating toolbar strip, if present. */
  var horizontalToolbarSpanPx: Pair<Float, Float>? = null

  private val instruments: List<Instrument>
    get() = listOfNotNull(ruler, protractor)

  var rulerVisible: Boolean
    get() = ruler != null
    set(value) {
      ruler =
        if (value) {
          ruler ?: Ruler(unitsPerMm).also {
            val (rulerY, _) = computeStaggeredInstrumentPairY(
              viewHeightPx = height.toFloat(),
              topClearancePx = topObstruction,
              bottomClearancePx = bottomInset,
              toolbarTopPx = horizontalToolbarSpanPx?.first ?: Float.NaN,
              toolbarBottomPx = horizontalToolbarSpanPx?.second ?: Float.NaN,
            )
            placeLevelAtScreenY(it, rulerY)
          }
        } else {
          null
        }
      instrumentView.invalidate()
    }

  var protractorVisible: Boolean
    get() = protractor != null
    set(value) {
      protractor =
        if (value) {
          protractor ?: Protractor(unitsPerMm).also {
            val (_, protractorY) = computeStaggeredInstrumentPairY(
              viewHeightPx = height.toFloat(),
              topClearancePx = topObstruction,
              bottomClearancePx = bottomInset,
              toolbarTopPx = horizontalToolbarSpanPx?.first ?: Float.NaN,
              toolbarBottomPx = horizontalToolbarSpanPx?.second ?: Float.NaN,
            )
            placeLevelAtScreenY(it, protractorY)
          }
        } else {
          null
        }
      instrumentView.invalidate()
    }

  /** Puts a new instrument in the horizontal middle of the screen at [screenY], level on screen. */
  private fun placeLevelAtScreenY(instrument: Instrument, screenY: Float) {
    val point = floatArrayOf(width / 2f, screenY)
    screenToPage.mapPoints(point)
    instrument.cx = point[0]
    instrument.cy = point[1]
    instrument.angle = -angleDegrees // cancels the page's own turn
  }

  // The instrument edge the current pen stroke follows, if any.
  private var rulerInstrument: Instrument? = null
  private var rulerEdge = 0
  private var rulerDownTime = 0L
  private val rulerPoint = FloatArray(2)

  /** The instrument and edge within reach of the pen, or null. */
  private fun instrumentEdgeAt(event: MotionEvent, index: Int): Pair<Instrument, Int>? {
    if (instruments.isEmpty()) return null
    rulerPoint[0] = event.getX(index)
    rulerPoint[1] = event.getY(index)
    screenToPage.mapPoints(rulerPoint)
    val reach = RULER_SNAP_DP * density / pageToScreen.mapRadius(1f)
    for (instrument in instruments.asReversed()) {
      instrument.snapEdge(rulerPoint[0], rulerPoint[1], reach)?.let { return instrument to it }
    }
    return null
  }

  /** The pen point moved onto the edge, in page units. */
  private fun projectedPoint(instrument: Instrument, edge: Int, screenX: Float, screenY: Float): FloatArray {
    rulerPoint[0] = screenX
    rulerPoint[1] = screenY
    screenToPage.mapPoints(rulerPoint)
    instrument.project(edge, rulerPoint[0], rulerPoint[1], rulerPoint)
    return rulerPoint
  }

  private fun inputAt(instrument: Instrument, edge: Int, x: Float, y: Float, time: Long, pressure: Float): StrokeInput {
    val p = projectedPoint(instrument, edge, x, y)
    return StrokeInput.create(p[0], p[1], time, InputToolType.STYLUS, StrokeInput.NO_STROKE_UNIT_LENGTH, pressure.coerceIn(0f, 1f))
  }

  /**
   * A stroke drawn along an instrument's edge: every pen point is moved straight onto the edge
   * before Ink sees it, so the line is exactly as straight as the ruler. (Hold-to-snap shapes
   * do not apply; the line is already straight.)
   */
  private fun startRulerStroke(event: MotionEvent, index: Int, instrument: Instrument, edge: Int) {
    rulerInstrument = instrument
    rulerEdge = edge
    rulerDownTime = event.eventTime
    val input = inputAt(instrument, edge, event.getX(index), event.getY(index), 0L, event.getPressure(index))
    val (paperBrush, screenBrush) = strokeBrushes(input.y)
    val id = inProgressView.startStroke(input, screenBrush, pageToScreen)
    pendingStrokes[id] = PendingStroke(tool, paperBrush)
    activeStrokeId = id
    strokeDownUptime = event.downTime
  }

  private fun continueRulerStroke(event: MotionEvent, index: Int) {
    val instrument = rulerInstrument ?: return
    val id = activeStrokeId ?: return
    val batch = MutableStrokeInputBatch()
    for (h in 0 until event.historySize) {
      batch.add(inputAt(instrument, rulerEdge, event.getHistoricalX(index, h), event.getHistoricalY(index, h), event.getHistoricalEventTime(h) - rulerDownTime, event.getHistoricalPressure(index, h)))
    }
    batch.add(inputAt(instrument, rulerEdge, event.getX(index), event.getY(index), event.eventTime - rulerDownTime, event.getPressure(index)))
    inProgressView.addToStroke(batch, id)
  }

  private fun finishRulerStroke(event: MotionEvent, id: InProgressStrokeId) {
    val instrument = rulerInstrument ?: return
    val index = event.actionIndex
    inProgressView.finishStroke(inputAt(instrument, rulerEdge, event.getX(index), event.getY(index), event.eventTime - rulerDownTime, event.getPressure(index)), id)
  }

  // Fingers holding an instrument: one finger slides it, two slide and turn it.
  private var heldInstrument: Instrument? = null
  private val instrumentFingers = ArrayList<Int>(2)
  private var gripX = 0f
  private var gripY = 0f
  private var gripAngle = 0f
  private var gripSpread = 0f

  private fun handleInstrumentFingers(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        val index = event.actionIndex
        if (event.getToolType(index) != MotionEvent.TOOL_TYPE_FINGER || penNearby || instruments.isEmpty()) return false
        rulerPoint[0] = event.getX(index)
        rulerPoint[1] = event.getY(index)
        screenToPage.mapPoints(rulerPoint)
        val hit = instruments.asReversed().firstOrNull { it.contains(rulerPoint[0], rulerPoint[1]) } ?: return false
        heldInstrument = hit
        instrumentFingers.clear()
        instrumentFingers += event.getPointerId(index)
        updateGrip(event)
        return true
      }
      MotionEvent.ACTION_POINTER_DOWN -> {
        if (heldInstrument == null) return false
        if (instrumentFingers.size < 2 && event.getToolType(event.actionIndex) == MotionEvent.TOOL_TYPE_FINGER) {
          instrumentFingers += event.getPointerId(event.actionIndex)
          updateGrip(event)
        }
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        val instrument = heldInstrument ?: return false
        val oldX = gripX
        val oldY = gripY
        val oldAngle = gripAngle
        val oldSpread = gripSpread
        val twoFingers = updateGrip(event)
        instrument.cx += gripX - oldX
        instrument.cy += gripY - oldY
        if (twoFingers) {
          // Spreading or pinching the fingers makes it bigger or smaller.
          if (oldSpread > 1f && gripSpread > 1f) instrument.resize(gripSpread / oldSpread)
          val turn = ((gripAngle - oldAngle + 540f) % 360f) - 180f
          instrument.angle += turn
          // Turn about the middle of the fingers, not the instrument's centre.
          val r = Math.toRadians(turn.toDouble())
          val dx = instrument.cx - gripX
          val dy = instrument.cy - gripY
          instrument.cx = gripX + (dx * cos(r) - dy * sin(r)).toFloat()
          instrument.cy = gripY + (dx * sin(r) + dy * cos(r)).toFloat()
        }
        instrumentView.invalidate()
        return true
      }
      MotionEvent.ACTION_POINTER_UP -> {
        if (heldInstrument == null) return false
        instrumentFingers.remove(event.getPointerId(event.actionIndex))
        updateGrip(event, ignoreIndex = event.actionIndex)
        return true
      }
      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        if (heldInstrument == null) return false
        heldInstrument = null
        instrumentFingers.clear()
        return true
      }
    }
    return false
  }

  /**
   * Works out the grip: the middle of the holding fingers (page units) and, with two fingers,
   * the angle between them (degrees on the page). Returns true when two fingers hold it.
   */
  private fun updateGrip(event: MotionEvent, ignoreIndex: Int = -1): Boolean {
    val points = instrumentFingers.mapNotNull { id ->
      val i = event.findPointerIndex(id)
      if (i < 0 || i == ignoreIndex) null else floatArrayOf(event.getX(i), event.getY(i)).also(screenToPage::mapPoints)
    }
    if (points.isEmpty()) return false
    gripX = points.map { it[0] }.average().toFloat()
    gripY = points.map { it[1] }.average().toFloat()
    if (points.size >= 2) {
      gripAngle = Math.toDegrees(atan2((points[1][1] - points[0][1]).toDouble(), (points[1][0] - points[0][0]).toDouble())).toFloat()
      gripSpread = hypot(points[1][0] - points[0][0], points[1][1] - points[0][1])
      return true
    }
    gripSpread = 0f
    return false
  }

  /** Draws the ruler and protractor above the ink, through the page view. */
  private inner class InstrumentView(context: Context) : View(context) {
    private val visible = RectF()

    override fun onDraw(canvas: Canvas) {
      if (ruler == null && protractor == null) return
      canvas.save()
      canvas.concat(pageToScreen)
      val zoom = pageToScreen.mapRadius(1f)
      visible.set(0f, 0f, width.toFloat(), height.toFloat())
      screenToPage.mapRect(visible)
      protractor?.draw(canvas, zoom, visible, instrumentStyle)
      ruler?.draw(canvas, zoom, visible, instrumentStyle)
      canvas.restore()
    }
  }

  /**
   * Partial eraser: each touched stroke loses the points within the eraser's path (plus half
   * the line width) and is replaced by the pieces left either side, in its place in the page
   * order. Returns true if anything changed.
   */
  private fun cutStrokes(touched: List<InkStroke>, ax: Float, ay: Float, bx: Float, by: Float, radius: Float): Boolean {
    var changed = false
    for (stroke in touched) {
      val pieces = PartialEraser.cut(stroke, ax, ay, bx, by, radius + stroke.brush.size / 2f) ?: continue
      val index = history.strokes.indexOfFirst { it === stroke }
      if (index < 0) continue
      history.removeAll { it === stroke }
      history.addAll(pieces.mapIndexed { k, piece -> (index + k) to piece })
      changed = true
    }
    return changed
  }

  private fun endPenGesture(event: MotionEvent) {
    erasingGesture = false
    // The erase drag becomes an undo step only here, after the last report of the drag saw an empty
    // undo stack: report again, or the Undo button stays grey until the next action.
    if (history.endGroup()) onHistoryChanged()
    rulerInstrument = null
    // A finger stroke is no "pen nearby": the next finger must pan at once.
    if (penPointerId != MotionEvent.INVALID_POINTER_ID && !runningFingerPenCopy) {
      lastPenEventTime = event.eventTime
      lastPenUpTime = event.eventTime
    }
    penPointerId = MotionEvent.INVALID_POINTER_ID
    activeStrokeId = null
    eraserCursor.hide()
    removeCallbacks(snapToShape)
    removeCallbacks(lassoHold)
    shapeSnapped = false
  }

  // ---- Erasing (whole strokes) -----------------------------------------------------------

  /** Erasing when the eraser tool is picked, the side button is held, or the pen is flipped. */
  private fun isErasing(event: MotionEvent, index: Int): Boolean =
    tool.isEraser ||
      event.getToolType(index) == MotionEvent.TOOL_TYPE_ERASER ||
      event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0

  private val erasedArea = RectF()

  private val eraserPoint = FloatArray(2)

  /** Pen position on the page (not the screen), stored in [eraserPoint]. */
  private fun readPagePosition(event: MotionEvent, index: Int) {
    eraserPoint[0] = event.getX(index)
    eraserPoint[1] = event.getY(index)
    screenToPage.mapPoints(eraserPoint)
  }

  /**
   * True from the moment a pen gesture starts erasing until the pen lifts: letting go of the side
   * button mid-gesture keeps erasing instead of switching back to ink.
   */
  private var erasingGesture = false

  private fun startErasing(event: MotionEvent, index: Int) {
    erasingGesture = true
    readPagePosition(event, index)
    lastEraserX = eraserPoint[0]
    lastEraserY = eraserPoint[1]
    erasedDuringGesture = false
    history.beginGroup() // the whole drag is one undo step (ended in endPenGesture)
    eraseAlong(event, index) // a tap erases too, not only a drag
  }

  /**
   * Removes every stroke the eraser circle touched since the last event. The circle is swept
   * along the line between the two pen positions, so a fast swipe cannot jump over a stroke.
   * The circle keeps its size on screen: zoom in to erase more precisely.
   */
  private fun eraseAlong(event: MotionEvent, index: Int) {
    val eraserRadiusPx = eraserRadiusDp * resources.displayMetrics.density
    eraserCursor.show(event.getX(index), event.getY(index), eraserRadiusPx)
    readPagePosition(event, index)
    val x = eraserPoint[0]
    val y = eraserPoint[1]
    val radius = eraserRadiusPx / pageToScreen.mapRadius(1f) // screen pixels → page units
    val start = ImmutableVec(lastEraserX, lastEraserY)
    val end = ImmutableVec(x, y)
    val swept =
      if (hypot(x - lastEraserX, y - lastEraserY) < 0.01f) {
        ImmutableParallelogram.fromCenterAndDimensions(end, 2 * radius, 2 * radius)
      } else {
        ImmutableParallelogram.fromSegmentAndPadding(ImmutableSegment(start, end), radius)
      }
    erasedArea.set(minOf(lastEraserX, x), minOf(lastEraserY, y), maxOf(lastEraserX, x), maxOf(lastEraserY, y))
    erasedArea.inset(-radius, -radius)
    lastEraserX = x
    lastEraserY = y
    // The cheap bounding-box test rules out almost every stroke before the exact (slower)
    // shape test runs. The drag is one undo step (see startErasing).
    var touched = history.strokes.filter {
      RectF.intersects(it.bounds, erasedArea) && !isHiddenLayer(it) && it.stroke.shape.intersects(swept, AffineTransform.IDENTITY)
    }
    // Whole-stroke erasing takes a patterned line away as one: touching a dash removes every dash.
    if (!partialErase && touched.isNotEmpty()) touched = touched + withGroupMates(touched, history.strokes) { it.group }.filter { !isHiddenLayer(it) }
    val touchedSet: Set<InkStroke> = Collections.newSetFromMap<InkStroke>(IdentityHashMap()).apply { addAll(touched) }
    var erased =
      if (touched.isEmpty()) false
      else if (!partialErase) history.removeAll(mergeIntoPrevious = erasedDuringGesture) { it in touchedSet }
      else cutStrokes(touched, start.x, start.y, end.x, end.y, radius)
    if (eraseFills(x, y, radius)) erased = true
    if (erased) {
      erasedDuringGesture = true
      // Erased strokes leave the selection too (user request: with the side button, the box
      // stayed and dragging it brought the erased strokes back for a moment).
      if (selection.isNotEmpty() && selection.removeAll(touchedSet)) {
        if (selection.isEmpty() && selectedItems.isEmpty()) {
          clearSelection()
        } else {
          updateSelectionBounds()
          selectionView.invalidate()
          reportSelection()
        }
      }
      pageChanged()
    }
  }

  /**
   * Fills are pictures, not strokes, so the stroke test above never finds them (user request:
   * "the eraser can't erase fills"). The eraser takes away a whole fill when it touches a
   * coloured pixel of it, so erasing a line inside a filled shape keeps the fill unless the
   * eraser actually goes over the colour. Same undo step as the strokes of this drag.
   */
  private fun eraseFills(x: Float, y: Float, radius: Float): Boolean {
    val hit = items.filterIsInstance<ImageItem>().filter { it.isInk && it.fileName.startsWith("fill-") && touchesFill(it, x, y, radius) }
    if (hit.isEmpty()) return false
    val ids = hit.map { it.id }.toSet()
    changeItems { list -> list.removeAll { it.id in ids } }
    fillAlpha.keys.removeAll { it in hit.map { h -> h.fileName } }
    return true
  }

  // Each fill's coverage, small (at most 256 px a side), read once per picture.
  private val fillAlpha = HashMap<String, android.graphics.Bitmap?>()

  private fun touchesFill(item: ImageItem, x: Float, y: Float, radius: Float): Boolean {
    // Into the item's own frame (it may have been rotated or resized with the lasso).
    val pt = floatArrayOf(x, y)
    if (item.rotation != 0f) Matrix().apply { setRotate(-item.rotation, item.cx, item.cy) }.mapPoints(pt)
    val left = item.cx - item.width / 2f
    val top = item.cy - item.height / 2f
    if (pt[0] < left - radius || pt[0] > left + item.width + radius || pt[1] < top - radius || pt[1] > top + item.height + radius) return false
    val bmp = fillAlpha.getOrPut(item.fileName) {
      try {
        val file = java.io.File(imagesDir ?: return@getOrPut null, item.fileName)
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
        android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
      } catch (e: Exception) {
        null
      }
    } ?: return true // picture unreadable: the box is all we know
    // A few points across the eraser circle: centre and four sides.
    val sx = bmp.width / item.width
    val sy = bmp.height / item.height
    for ((dx, dy) in listOf(0f to 0f, radius to 0f, -radius to 0f, 0f to radius, 0f to -radius)) {
      val px = ((pt[0] + dx - left) * sx).toInt()
      val py = ((pt[1] + dy - top) * sy).toInt()
      if (px in 0 until bmp.width && py in 0 until bmp.height && android.graphics.Color.alpha(bmp.getPixel(px, py)) > 40) return true
    }
    return false
  }

  // ---- Pan and zoom ----------------------------------------------------------------------

  private var lastPanZoomTime = 0L

  /**
   * Present mode (projecting in class): the selection's box, handles and the search highlight are
   * not shown, and no new selection is made, so only the page and the ink are on screen.
   */
  var presenting = false
    set(value) {
      if (field == value) return
      field = value
      if (value) clearSelection()
      selectionView.visibility = if (value) INVISIBLE else VISIBLE
    }

  /** When true, two fingers no longer zoom (they still pan and turn, unless turning is locked). */
  var zoomLocked = false

  /** When true, two fingers no longer turn the page. */
  var rotationLocked = false

  /** Called when the zoom (in whole percent) or the angle (whole degrees) changes. */
  var onViewChanged: (zoomPercent: Int, angleDegrees: Int) -> Unit = { _, _ -> }

  private var reportedZoom = -1
  private var reportedAngle = Int.MIN_VALUE
  private val matrixValues = FloatArray(9)

  val zoomPercent: Int
    get() = Math.round(pageToScreen.mapRadius(1f) / trueSizeZoom * 100f)

  /** The page's angle on screen, -180..180 degrees. */
  val angleDegrees: Float
    get() {
      pageToScreen.getValues(matrixValues)
      return Math.toDegrees(atan2(matrixValues[Matrix.MSKEW_Y].toDouble(), matrixValues[Matrix.MSCALE_X].toDouble())).toFloat()
    }

  // ---- Momentum ---------------------------------------------------------------------------

  /** Recent pan steps (time, dx, dy) for working out how fast the fingers were moving at the end. */
  private val panTimes = LongArray(PAN_SAMPLES)
  private val panDx = FloatArray(PAN_SAMPLES)
  private val panDy = FloatArray(PAN_SAMPLES)
  private var panSamples = 0
  private var flingVx = 0f // screen px per second
  private var flingVy = 0f
  private var flingLastFrame = 0L
  private var flinging = false

  private fun recordPanStep(dx: Float, dy: Float) {
    val i = panSamples % PAN_SAMPLES
    panTimes[i] = SystemClock.uptimeMillis()
    panDx[i] = dx
    panDy[i] = dy
    panSamples++
  }

  /** On release: if the fingers were still moving fast, keep the page moving and let it slow down. */
  private fun startFlingIfFast() {
    val now = SystemClock.uptimeMillis()
    var sumX = 0f
    var sumY = 0f
    var oldest = now
    for (k in 0 until minOf(panSamples, PAN_SAMPLES)) {
      val t = panTimes[k]
      if (now - t <= FLING_WINDOW_MS) {
        sumX += panDx[k]
        sumY += panDy[k]
        oldest = minOf(oldest, t)
      }
    }
    panSamples = 0
    val span = maxOf(now - oldest, 16L) / 1000f
    flingVx = sumX / span
    flingVy = sumY / span
    if (hypot(flingVx, flingVy) < FLING_MIN_SPEED_DP * density) return
    flinging = true
    flingLastFrame = System.nanoTime()
    postOnAnimation(flingStep)
  }

  /** One frame of a glide: move by speed x time, then slow down (exponential friction). */
  private val flingStep: Runnable = object : Runnable {
    override fun run() {
      if (!flinging) return
      val now = System.nanoTime()
      val dt = ((now - flingLastFrame) / 1e9f).coerceIn(0f, 0.05f)
      flingLastFrame = now
      pageToScreen.postTranslate(flingVx * dt, flingVy * dt)
      val decay = Math.exp((-dt / FLING_TIME_CONSTANT_S).toDouble()).toFloat()
      flingVx *= decay
      flingVy *= decay
      viewMoved()
      if (pullDistance > 0f && flingVy < 0f) {
        // A glide never adds a page: it stops at the end and settles back.
        flinging = false
        springBackFromPull()
        return
      }
      if (hypot(flingVx, flingVy) < FLING_STOP_SPEED_DP * density) flinging = false else postOnAnimation(this)
    }
  }

  private fun stopFling() {
    flinging = false
    panSamples = 0
    pullBack?.cancel()
    pullBack = null
  }

  private var pullBack: android.animation.ValueAnimator? = null

  /** Let go before "release to add": the page slides back to the end of the last page. */
  private fun springBackFromPull() {
    val total = pullDistance
    if (total <= 0f) return
    var done = 0f
    pullBack?.cancel()
    pullBack = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
      duration = 260
      interpolator = android.view.animation.DecelerateInterpolator(1.6f)
      addUpdateListener {
        val target = total * (it.animatedValue as Float)
        pageToScreen.postTranslate(0f, target - done)
        done = target
        viewMoved()
      }
      start()
    }
  }

  /** True once fingers zoomed during the current touch (then release snaps instead of gliding). */
  private var pinchedThisGesture = false

  private fun panZoomBy(dx: Float, dy: Float, scaleFactor: Float, rotationDegrees: Float, focusX: Float, focusY: Float) {
    if (scaleFactor != 1f) pinchedThisGesture = true
    followingPlayback = false // the fingers move the page: stop following the playback
    recordPanStep(dx, dy)
    // Pulling past the last page gets harder the further it goes, like stretching something
    // (the page moves 1/2.5 as far as the finger at the "release to add" point). Heavier since
    // 13 Nov (user request: "so sensitive"): about 260 dp of finger travel to add a page.
    val pulledDy = if (pageLayout != null && dy < 0f && pullDistance > 0f) dy / (1f + PULL_RESISTANCE * pullDistance / (PULL_TO_ADD_DP * density)) else dy
    pageToScreen.postTranslate(dx, pulledDy)
    if (!rotationLocked && rotationDegrees != 0f) pageToScreen.postRotate(rotationDegrees, focusX, focusY)

    if (!zoomLocked) {
      // Keep the total zoom inside the allowed range.
      val zoom = pageToScreen.mapRadius(1f)
      val factor = (zoom * scaleFactor).coerceIn(minZoom, maxZoom) / zoom
      pageToScreen.postScale(factor, factor, focusX, focusY)
    }
    viewMoved()
  }

  /**
   * Sets the zoom and/or angle exactly (null keeps the current one), keeping whatever is in the
   * middle of the screen in the middle. Used by the view presets.
   */
  fun setView(zoom: Float? = null, angleDegrees: Float? = null) {
    val center = floatArrayOf(width / 2f, height / 2f)
    screenToPage.mapPoints(center)
    val newZoom = (zoom?.times(trueSizeZoom) ?: pageToScreen.mapRadius(1f)).coerceIn(minZoom, maxZoom)
    val newAngle = angleDegrees ?: this.angleDegrees
    pageToScreen.setTranslate(-center[0], -center[1])
    pageToScreen.postScale(newZoom, newZoom)
    pageToScreen.postRotate(newAngle)
    pageToScreen.postTranslate(width / 2f, height / 2f)
    viewMoved()
  }

  private fun viewMoved() {
    pageToScreen.invert(screenToPage)
    keepPagesInView()
    pageToScreen.invert(screenToPage)
    if (ruler != null || protractor != null) instrumentView.invalidate()
    updatePull()
    lastPanZoomTime = SystemClock.uptimeMillis()
    finishedView.invalidate()
    onViewMoved()
    // One more draw after the fingers stop, to refresh the cache for the new zoom.
    finishedView.postInvalidateDelayed(PAN_ZOOM_SETTLE_MS)
    if (hasAnySelection) {
      selectionView.invalidate()
      reportSelection()
    } else if (searchHit != null || playbackRecording >= 0) {
      selectionView.invalidate()
    }
    reportView()
  }

  private fun reportView() {
    if (pageLayout != null) {
      val page = currentPage
      if (page != reportedPage) {
        reportedPage = page
        onCurrentPageChanged(page)
      }
    }
    val zoom = zoomPercent
    val angle = Math.round(angleDegrees)
    if (zoom != reportedZoom || angle != reportedAngle) {
      reportedZoom = zoom
      reportedAngle = angle
      onViewChanged(zoom, angle)
    }
  }

  // ---- Bottom layer ----------------------------------------------------------------------

  /**
   * Draws the paper's ruling, then all finished strokes, through [pageToScreen]. The stroke
   * work is in [StrokeChunkCache].
   */
  private inner class FinishedStrokesView(context: Context) : View(context) {
    private val visiblePage = RectF()
    private val pageRect = RectF()
    private val ruled = RectF()
    private val paperPaint = Paint().apply { color = InkColors.pageOnScreen(darkPage) }
    private val whitePaint = Paint().apply { color = android.graphics.Color.WHITE }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val edgePaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = InkColors.pageEdgeOnScreen(darkPage)
      }
    private val pullPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = InkColors.pageEdgeOnScreen(darkPage)
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
      }
    private val pullBar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pullText =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = InkColors.onScreen(0xFF5F6670.toInt(), darkPage)
        textAlign = Paint.Align.CENTER
      }

    fun updateDarkPage() {
      paperPaint.color = InkColors.pageOnScreen(darkPage)
      edgePaint.color = InkColors.pageEdgeOnScreen(darkPage)
      pullPaint.color = InkColors.pageEdgeOnScreen(darkPage)
      pullText.color = InkColors.onScreen(0xFF5F6670.toInt(), darkPage)
    }

    /**
     * Each page that shows: paper, its ruling (measured from that page's own top edge, so every
     * page is ruled the same), and a thin edge. Below the last page, while pulled: the outline of
     * the next page and what letting go will do. Called in page units.
     */
    private fun drawPages(canvas: Canvas, layout: PageLayout) {
      val zoom = pageToScreen.mapRadius(1f)
      edgePaint.strokeWidth = 1f / zoom
      val first = layout.pageAt(visiblePage.top)
      val last = layout.pageAt(visiblePage.bottom)
      for (page in first..last) {
        layout.rect(page, pageRect)
        if (!RectF.intersects(pageRect, visiblePage)) continue
        val pdfPage = if (pdf != null) layout.backgroundOf(page) else -1
        if (pdfPage >= 0) {
          // A PDF page: white paper, the page's picture once it is drawn, no ruling.
          canvas.drawRect(pageRect, whitePaint)
          val wanted = (layout.pageWidth * zoom).toInt()
          val stillNow = SystemClock.uptimeMillis() - lastPanZoomTime >= PAN_ZOOM_SETTLE_MS
          pdf?.bitmapFor(pdfPage, wanted, mayRender = stillNow)?.let { canvas.drawBitmap(it, null, pageRect, bitmapPaint) }
          if (!stillNow) postInvalidateDelayed(PAN_ZOOM_SETTLE_MS) // ask for a sharper picture once still
        } else {
          canvas.drawRect(pageRect, paperPaint)
          canvas.save()
          canvas.clipRect(pageRect)
          canvas.translate(0f, pageRect.top)
          ruled.set(visiblePage)
          ruled.intersect(pageRect)
          ruled.offset(0f, -pageRect.top)
          pageBackground.draw(canvas, pageStyle, ruled, zoom)
          canvas.restore()
        }
        val accent = pageEdgeAccent
        if (accent != null) {
          edgePaint.color = accent
          edgePaint.strokeWidth = 1.5f * resources.displayMetrics.density / zoom
        }
        canvas.drawRect(pageRect, edgePaint)
      }
      // Once still, get the PDF pages just above and below ready, so scrolling finds them drawn.
      if (pdf != null && SystemClock.uptimeMillis() - lastPanZoomTime >= PAN_ZOOM_SETTLE_MS) {
        val wanted = (layout.pageWidth * zoom).toInt()
        for (page in intArrayOf(last + 1, first - 1, last + 2)) {
          if (page in 0 until layout.count) pdf.prefetch(layout.backgroundOf(page), wanted)
        }
      }
      if (pullIsLive()) {
        val top = layout.totalHeight + layout.gap
        val shown = (pullDistance / zoom).coerceAtMost(layout.pageHeight)
        pullPaint.strokeWidth = 1.5f * density / zoom
        canvas.drawRect(0f, top, layout.pageWidth, top + shown, pullPaint)
        pullText.textSize = 15f * density / zoom
        val message = if (pullReady) "Release to add a page" else if (pullPastSince != PullToAdd.NOT_PAST) "Keep holding" else "Pull to add a page"
        val textY = top + shown / 2f
        canvas.drawText(message, layout.pageWidth / 2f, textY, pullText)
        // How far there is to go: fills as the page is pulled, full = let go to add.
        val barWidth = 160f * density / zoom
        val barHeight = 6f * density / zoom
        val barTop = textY + 14f * density / zoom
        val left = layout.pageWidth / 2f - barWidth / 2f
        val progress = (pullDistance / (PULL_TO_ADD_DP * density)).coerceIn(0f, 1f)
        pullBar.color = InkColors.pageEdgeOnScreen(darkPage)
        canvas.drawRoundRect(left, barTop, left + barWidth, barTop + barHeight, barHeight / 2, barHeight / 2, pullBar)
        pullBar.color = if (pullReady) 0xFF4A90E2.toInt() else InkColors.onScreen(0xFF5F6670.toInt(), darkPage)
        canvas.drawRoundRect(left, barTop, left + barWidth * progress, barTop + barHeight, barHeight / 2, barHeight / 2, pullBar)
      }
    }

    /**
     * Zoomed out: each page's ink from its picture. A page whose picture is out of date shows
     * the old one plus its newest strokes drawn live until the new picture arrives. Pages just
     * above and below the screen are prepared too, so scrolling finds them ready.
     */
    private fun drawPagePictures(canvas: Canvas, layout: PageLayout, toView: Matrix = pageToScreen, visible: RectF = visiblePage, margin: Int = 1) {
      val zoom = pageToScreen.mapRadius(1f)
      val widthPx = (layout.pageWidth * zoom).toInt()
      val first = (layout.pageAt(visible.top) - margin).coerceAtLeast(0)
      val last = (layout.pageAt(visible.bottom) + margin).coerceAtMost(layout.count - 1)
      canvas.save()
      canvas.concat(toView)
      for (page in first..last) {
        layout.rect(page, pageRect)
        val (bitmap, current) = rasters.get(page, widthPx, pageRect.top, layout.pageWidth, layout.pageHeight, { strokesOnPage(page) }, ::buildForPicture)
        if (!RectF.intersects(pageRect, visible)) continue
        if (bitmap != null) canvas.drawBitmap(bitmap, null, pageRect, bitmapPaint)
        if (!current) {
          freshInk[page]?.forEach { stroke ->
            if (stroke !in hiddenFromRasters && !isHiddenLayer(stroke)) freshRenderer.draw(canvas, screenVersion(stroke), toView)
          }
        }
      }
      canvas.restore()
    }

    private val freshRenderer = CanvasStrokeRenderer.create()

    private var lastSlowLog = 0L

    override fun onDraw(canvas: Canvas) {
      val t0 = System.nanoTime()
      drawFrame(canvas, t0)
      requestProjectorPublish() // the projector follows every draw of this layer
      // Frames that take more than 12 ms of the main thread (a 120 Hz frame is 8.3 ms) are
      // logged with where the time went, at most twice a second: "adb logcat -s PenLabPerf".
      val total = (System.nanoTime() - t0) / 1_000_000
      val now = SystemClock.uptimeMillis()
      if (total > 12 && now - lastSlowLog > 500) {
        lastSlowLog = now
        android.util.Log.i("PenLabPerf", "slow frame: $total ms (pages+pdf ${pagesNs / 1_000_000} ms, photos/text ${itemsNs / 1_000_000} ms, ink the rest)")
      }
    }

    private var pagesNs = 0L
    private var itemsNs = 0L

    private fun drawFrame(canvas: Canvas, t0: Long) {
      visiblePage.set(0f, 0f, width.toFloat(), height.toFloat())
      screenToPage.mapRect(visiblePage)

      canvas.save()
      canvas.concat(pageToScreen)
      val layout = pageLayout
      if (layout == null) {
        pageBackground.draw(canvas, pageStyle, visiblePage, pageToScreen.mapRadius(1f))
      } else {
        drawPages(canvas, layout)
      }
      val t1 = System.nanoTime()
      pagesNs = t1 - t0
      drawItems(canvas, visiblePage) // photos and text, under the ink
      itemsNs = System.nanoTime() - t1
      canvas.restore()

      if (layout != null && layout.pageWidth * pageToScreen.mapRadius(1f) < PageRasters.RASTER_BELOW_PX && abs(angleDegrees) < 0.5f) {
        drawPagePictures(canvas, layout)
        return
      }
      if (layout == null) {
        // Many strokes on screen (zoomed out over a big drawing): squares instead of strokes.
        val onScreen = chunkCache.countIn(visiblePage)
        tileMode = if (tileMode) onScreen > TileRasters.EXIT_STROKES else onScreen > TileRasters.ENTER_STROKES
        if (tileMode) {
          canvas.save()
          canvas.concat(pageToScreen)
          val zoom = pageToScreen.mapRadius(1f)
          val current = tiles.draw(canvas, visiblePage, zoom, { area -> chunkCache.strokesIn(area, hiddenFromRasters).filter { !isHiddenLayer(it) } }, ::buildForPicture, bitmapPaint)
          if (current) freshTileInk.clear()
          else for (stroke in freshTileInk) if (stroke !in hiddenFromRasters && !isHiddenLayer(stroke)) freshRenderer.draw(canvas, screenVersion(stroke), pageToScreen)
          canvas.restore()
          return
        }
      }
      // While fingers are moving the page, do nothing but replay; tidy up once they stop.
      val pageIsStill = SystemClock.uptimeMillis() - lastPanZoomTime >= PAN_ZOOM_SETTLE_MS
      val moreToRefresh = chunkCache.draw(canvas, pageToScreen, visiblePage, refreshForZoom = pageIsStill)
      if (moreToRefresh) postInvalidateOnAnimation()
    }
  }

  /** The circle that shows how much the eraser covers. Sits above everything else. */
  // ---- Lasso: select, move, delete -------------------------------------------------------

  private enum class LassoGesture { NONE, DRAWING, MOVING, RESIZING, ROTATING, LINE_END }

  /** A gesture that changes the selection: its originals are hidden while it runs. */
  private val LassoGesture.changesSelection: Boolean
    get() = this == LassoGesture.MOVING || this == LassoGesture.RESIZING || this == LassoGesture.ROTATING || this == LassoGesture.LINE_END

  private var lassoGesture = LassoGesture.NONE
  private val lassoPath = ArrayList<Point>()

  private fun lassoBounds(): RectF {
    val r = RectF(lassoPath[0].x, lassoPath[0].y, lassoPath[0].x, lassoPath[0].y)
    for (p in lassoPath) r.union(p.x, p.y)
    return r
  }
  private val selection: MutableSet<InkStroke> = Collections.newSetFromMap(IdentityHashMap())
  private val selectionBounds = RectF() // page units
  private var moveStartX = 0f
  private var moveStartY = 0f
  private var moveDx = 0f
  private var moveDy = 0f
  private var activeHandle = ResizeHandle.BOTTOM_RIGHT
  private var resizeScaleX = 1f
  private var resizeScaleY = 1f
  private var resizeAnchorX = 0f
  private var resizeAnchorY = 0f
  private var resizeStartX = 0f
  private var resizeStartY = 0f
  private var rotateCenterX = 0f
  private var rotateCenterY = 0f
  private var rotateStartAngle = 0f
  private var rotateAngle = 0f // degrees

  /** Called when the clipboard fills or empties, so the screen can show or hide Paste. */
  var onClipboardChanged: (hasClipboard: Boolean) -> Unit = {}

  // Cut / copy / paste. Strokes never change, so the clipboard can hold them as they are;
  // pasting always makes new copies.
  // The clipboard is shared by all notebooks (copy in one, paste in another); see [SharedClipboard].
  private var clipboard: List<InkStroke>
    get() = SharedClipboard.strokes
    set(value) {
      SharedClipboard.strokes = value
    }
  private val clipboardBounds: RectF
    get() = SharedClipboard.bounds

  /**
   * Makes the shared clipboard fit this notebook before pasting: scaled if it was copied where a
   * millimetre is a different number of units (a notebook vs an endless canvas), and its photos
   * copied into this notebook's images folder.
   */
  private fun adoptClipboard() {
    val from = SharedClipboard.unitsPerMm
    if (from > 0f && from != itemMm) {
      val factor = itemMm / from
      val m = Matrix().apply { setScale(factor, factor, clipboardBounds.centerX(), clipboardBounds.centerY()) }
      SharedClipboard.strokes = SharedClipboard.strokes.map { it.mapped(m, factor) }
      SharedClipboard.items = SharedClipboard.items.map { it.mapped(m) }
      m.mapRect(SharedClipboard.bounds)
    }
    SharedClipboard.unitsPerMm = itemMm
    val source = SharedClipboard.imagesDir
    if (source != null && imagesDir != null && source != imagesDir) {
      for (item in SharedClipboard.items) {
        if (item !is ImageItem) continue
        val target = java.io.File(imagesDir, item.fileName)
        if (!target.exists()) runCatching { target.parentFile?.mkdirs(); java.io.File(source, item.fileName).copyTo(target) }
      }
    }
    SharedClipboard.imagesDir = imagesDir
  }

  val hasClipboard: Boolean
    get() = clipboard.isNotEmpty() || clipboardItems.isNotEmpty()

  val hasSelection: Boolean
    get() = hasAnySelection

  // ---- Fingers and the selection --------------------------------------------------------

  private var selectionFingerId = MotionEvent.INVALID_POINTER_ID
  private var tapDownTime = 0L
  private var tapDownX = 0f
  private var tapDownY = 0f
  private var tapCandidate = false

  /**
   * One finger landing on the selection (not the palm: the pen is away) drags or resizes it,
   * exactly like the pen would. Returns true while it owns the gesture.
   */
  private fun handleSelectionFinger(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        val index = event.actionIndex
        val finger = event.getToolType(index) == MotionEvent.TOOL_TYPE_FINGER
        if (finger && !penNearby && isOnSelection(event, index)) {
          selectionFingerId = event.getPointerId(index)
          startLasso(event, index) // starts a move or resize, since it is on the selection
          return true
        }
      }
      MotionEvent.ACTION_POINTER_DOWN -> {
        // A second finger joins: hand the gesture back to pan/zoom and drop the drag.
        if (selectionFingerId != MotionEvent.INVALID_POINTER_ID) {
          selectionFingerId = MotionEvent.INVALID_POINTER_ID
          cancelLasso()
        }
      }
      MotionEvent.ACTION_MOVE -> {
        if (selectionFingerId == MotionEvent.INVALID_POINTER_ID) return false
        val index = event.findPointerIndex(selectionFingerId)
        if (index >= 0) continueLasso(event, index)
        return true
      }
      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_POINTER_UP -> {
        if (selectionFingerId != MotionEvent.INVALID_POINTER_ID &&
          event.getPointerId(event.actionIndex) == selectionFingerId
        ) {
          selectionFingerId = MotionEvent.INVALID_POINTER_ID
          finishLasso()
          return true
        }
      }
      MotionEvent.ACTION_CANCEL -> {
        if (selectionFingerId != MotionEvent.INVALID_POINTER_ID) {
          selectionFingerId = MotionEvent.INVALID_POINTER_ID
          cancelLasso()
          return true
        }
      }
    }
    return false
  }

  private var lastTapUpTime = 0L
  private var lastTapX = 0f
  private var lastTapY = 0f
  private val doubleTapSlopPx = 40f * resources.displayMetrics.density

  /**
   * Fits the page to the screen: a notebook page fills the width beside the toolbar; an endless
   * canvas goes back to 100% and level, around the middle of the screen.
   */
  fun fitPage() {
    if (pageLayout != null) {
      fitToWidth()
    } else {
      setView(zoom = 1f, angleDegrees = 0f)
    }
  }

  /**
   * Endless canvas: back to where you were working, the newest stroke (or photo / text box),
   * at 100% and level. With nothing on the canvas, back to its starting point.
   */
  fun goHome() {
    if (width == 0) return
    stopFling()
    val target = history.strokes.lastOrNull()?.bounds ?: items.lastOrNull()?.bounds(RectF())
    val cx = target?.centerX() ?: 0f
    val cy = target?.centerY() ?: 0f
    val zoom = trueSizeZoom.coerceIn(minZoom, maxZoom)
    pageToScreen.setScale(zoom, zoom)
    pageToScreen.postTranslate(width / 2f - cx * zoom, height / 2f - cy * zoom)
    viewMoved()
  }

  // ---- Handwriting search -----------------------------------------------------------------

  /** The strokes handwriting search should read: visible layers, no highlighter. A copy. */
  fun searchableStrokes(): List<InkStroke> = history.strokes.filter { !it.isHighlighter && !isHiddenLayer(it) }

  /** A search result being shown (page units), highlighted until it fades or the pen writes. */
  private var searchHit: RectF? = null
  private var searchHitAlpha = 0f
  private val searchHitFade = android.animation.ValueAnimator.ofFloat(1f, 0f).apply {
    startDelay = SEARCH_HIT_MS
    duration = 600
    addUpdateListener {
      searchHitAlpha = it.animatedValue as Float
      selectionView.invalidate()
    }
  }

  /**
   * Moves the view so [rect] (a line found by search) is in the middle of the screen and
   * highlights it. Zooms in to 100% if the line would be too small to read, never out.
   */
  fun showSearchHit(rect: RectF) {
    if (width == 0) return
    stopFling()
    val zoom = pageToScreen.mapRadius(1f)
    val wanted = trueSizeZoom.coerceIn(minZoom, maxZoom)
    if (zoom < wanted * 0.6f) pageToScreen.postScale(wanted / zoom, wanted / zoom)
    val point = floatArrayOf(rect.centerX(), rect.centerY())
    pageToScreen.mapPoints(point)
    pageToScreen.postTranslate(width / 2f - point[0], height * 0.4f - point[1])
    viewMoved()
    searchHit = RectF(rect).apply { inset(-2f * unitsPerMm, -1.5f * unitsPerMm) }
    searchHitAlpha = 1f
    searchHitFade.cancel()
    searchHitFade.start()
    selectionView.invalidate()
  }

  private val searchHitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SEARCH_HIT_COLOR }

  // ---- Playback glow: what was being written at this moment -------------------------------------

  /** The recording being played (its index in the notebook's list) and the position (ms); -1 when none. */
  private var playbackRecording = -1
  private var playbackMs = 0L

  /**
   * While playing, the page scrolls to the ink being written at the current moment. Panning by hand turns it off (the
   * page must not fight the fingers); [followPlayback] turns it back on.
   */
  private var followingPlayback = true

  /** Ink of the played recording sorted by when it was written, rebuilt when the strokes change. */
  private var playbackIndex: List<InkStroke> = emptyList()
  private var playbackIndexKey = ""
  private val playbackGlow = RectF()
  private val playbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SEARCH_HIT_COLOR }

  /**
   * Called by the screen when the plugin's player reports progress (a few times a second): glows (and follows) the ink
   * written around [positionMs] of recording [recording]. Drawn by the selection layer, off the ink path.
   */
  fun showPlayback(recording: Int, positionMs: Long) {
    playbackRecording = recording
    playbackMs = positionMs
    val glow = playbackGlowRect() ?: run {
      selectionView.invalidate()
      return
    }
    if (followingPlayback && width > 0) {
      // Off screen (or nearly): bring it to the middle, keeping the zoom.
      val r = RectF(glow).also { pageToScreen.mapRect(it) }
      val margin = 48f * density
      if (r.top < margin + topClearance() || r.bottom > height - margin || r.right < margin || r.left > width - margin) {
        pageToScreen.postTranslate(width / 2f - r.centerX(), height * 0.45f - r.centerY())
        viewMoved()
      }
    }
    selectionView.invalidate()
  }

  fun hidePlayback() {
    if (playbackRecording < 0) return
    playbackRecording = -1
    selectionView.invalidate()
  }

  /** Follow the playback again (on Play, or after a tap on ink). */
  fun followPlayback() {
    followingPlayback = true
  }

  /** The area of the ink written from a little before [playbackMs] up to it, or null if none. */
  private fun playbackGlowRect(): RectF? {
    if (playbackRecording < 0) return null
    val strokes = history.strokes
    val key = "$playbackRecording:${strokes.size}:${strokes.firstOrNull()?.let(System::identityHashCode)}:${strokes.lastOrNull()?.let(System::identityHashCode)}"
    if (key != playbackIndexKey) {
      playbackIndexKey = key
      playbackIndex = strokes.filter { it.mediaMark != MediaMark.NONE && MediaMark.recording(it.mediaMark) == playbackRecording && !isHiddenLayer(it) }
        .sortedBy { MediaMark.positionMs(it.mediaMark) }
    }
    // First stroke written after (now - window): binary search, then walk up to now.
    val from = playbackMs - PLAYBACK_WINDOW_MS
    var lo = 0
    var hi = playbackIndex.size
    while (lo < hi) {
      val mid = (lo + hi) / 2
      if (MediaMark.positionMs(playbackIndex[mid].mediaMark) < from) lo = mid + 1 else hi = mid
    }
    playbackGlow.setEmpty()
    var i = lo
    while (i < playbackIndex.size && MediaMark.positionMs(playbackIndex[i].mediaMark) <= playbackMs + PLAYBACK_AHEAD_MS) {
      playbackGlow.union(playbackIndex[i].bounds)
      i++
    }
    return if (playbackGlow.isEmpty) null else playbackGlow
  }

  private fun drawPlayback(canvas: Canvas) {
    val glow = playbackGlowRect() ?: return
    canvas.save()
    canvas.concat(pageToScreen)
    playbackPaint.alpha = PLAYBACK_ALPHA
    val pad = 1.5f * unitsPerMm
    val radius = 2f * unitsPerMm
    canvas.drawRoundRect(glow.left - pad, glow.top - pad, glow.right + pad, glow.bottom + pad, radius, radius, playbackPaint)
    canvas.restore()
  }

  private fun drawSearchHit(canvas: Canvas) {
    val hit = searchHit ?: return
    if (searchHitAlpha <= 0f) {
      searchHit = null
      return
    }
    canvas.save()
    canvas.concat(pageToScreen)
    searchHitPaint.alpha = (SEARCH_HIT_ALPHA * searchHitAlpha).toInt()
    val radius = 2f * unitsPerMm
    canvas.drawRoundRect(hit, radius, radius, searchHitPaint)
    canvas.restore()
  }

  /** A quick one-finger tap on the page lets go of the selection and closes menus. */
  private fun trackFingerTap(event: MotionEvent) {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        val finger = event.getToolType(event.actionIndex) == MotionEvent.TOOL_TYPE_FINGER
        tapCandidate = finger && !penNearby
        tapDownTime = event.eventTime
        tapDownX = event.x
        tapDownY = event.y
      }
      MotionEvent.ACTION_POINTER_DOWN -> tapCandidate = false // two or more fingers: not this tap
      MotionEvent.ACTION_MOVE ->
        if (hypot(event.x - tapDownX, event.y - tapDownY) > holdSlopPx) tapCandidate = false
      MotionEvent.ACTION_UP -> {
        // A media plugin is on: a finger tap on ink that was written while recording plays the audio from there.
        val seek = onMediaSeek
        if (tapCandidate && seek != null && event.eventTime - tapDownTime < FINGER_TAP_MS) {
          val mark = mediaMarkAt(event.x, event.y)
          if (mark != MediaMark.NONE && seek(MediaMark.recording(mark), MediaMark.positionMs(mark))) {
            tapCandidate = false
            return
          }
        }
        if (tapCandidate && event.eventTime - tapDownTime < FINGER_TAP_MS) {
          clearSelection()
          onPageTouched()
          // A second quick tap close to the first: fit the page to the screen.
          if (event.eventTime - lastTapUpTime < DOUBLE_TAP_MS && hypot(event.x - lastTapX, event.y - lastTapY) < doubleTapSlopPx) {
            fitPage()
            lastTapUpTime = 0L
          } else {
            lastTapUpTime = event.eventTime
            lastTapX = event.x
            lastTapY = event.y
          }
        }
        tapCandidate = false
      }
      MotionEvent.ACTION_CANCEL -> tapCandidate = false
    }
  }

  /** True if the pen is on the selection's box or one of its handles. */
  private fun isOnSelection(event: MotionEvent, index: Int): Boolean {
    if (!hasAnySelection) return false
    val x = event.getX(index)
    val y = event.getY(index)
    if (lineSelection != null) return lineHandleAt(x, y) >= 0 || isNearLine(x, y)
    return handleAt(x, y) != null || isOnRotateHandle(x, y) || isInSelectionBox(x, y)
  }

  private fun startLasso(event: MotionEvent, index: Int) {
    readPagePosition(event, index)
    val x = eraserPoint[0]
    val y = eraserPoint[1]
    val line = lineSelection
    val lineHandle = if (line != null) lineHandleAt(event.getX(index), event.getY(index)) else -1
    val handle = if (hasAnySelection && line == null) handleAt(event.getX(index), event.getY(index)) else null
    if (line != null && lineHandle >= 0) {
      // Pen down on one end of a straight line: the end follows the pen, the other stays put.
      lassoGesture = LassoGesture.LINE_END
      lineEnd = lineHandle
      val endX = if (lineHandle == 0) line.ax else line.bx
      val endY = if (lineHandle == 0) line.ay else line.by
      lineGrabDx = endX - x // keep the offset between pen and handle, so the end does not jump
      lineGrabDy = endY - y
      linePreviewEnds = null
      linePreview = emptyList()
      hideSelection(true)
      finishedView.invalidate()
      onSelectionChanged(null)
    } else if (hasAnySelection && line == null && isOnRotateHandle(event.getX(index), event.getY(index))) {
      // Pen down on the rotate handle: turn the selection about its centre.
      lassoGesture = LassoGesture.ROTATING
      rotateCenterX = selectionBounds.centerX()
      rotateCenterY = selectionBounds.centerY()
      rotateStartAngle = atan2(y - rotateCenterY, x - rotateCenterX)
      rotateAngle = 0f
      hideSelection(true)
      finishedView.invalidate()
      onSelectionChanged(null)
    } else if (handle != null) {
      // Pen down on a handle: resize about the opposite corner or edge.
      lassoGesture = LassoGesture.RESIZING
      activeHandle = handle
      resizeScaleX = 1f
      resizeScaleY = 1f
      resizeAnchorX = selectionBounds.left + (1 - handle.fx) * selectionBounds.width()
      resizeAnchorY = selectionBounds.top + (1 - handle.fy) * selectionBounds.height()
      resizeStartX = selectionBounds.left + handle.fx * selectionBounds.width()
      resizeStartY = selectionBounds.top + handle.fy * selectionBounds.height()
      hideSelection(true)
      finishedView.invalidate()
      onSelectionChanged(null)
    } else if (hasAnySelection && (if (line != null) isNearLine(event.getX(index), event.getY(index)) else isInSelectionBox(event.getX(index), event.getY(index)))) {
      // Pen down on the selection: drag it. Hide the originals; the overlay draws them moving.
      lassoGesture = LassoGesture.MOVING
      moveStartX = x
      moveStartY = y
      moveDx = 0f
      moveDy = 0f
      hideSelection(true)
      finishedView.invalidate()
      onSelectionChanged(null)
    } else {
      clearSelection()
      lassoGesture = LassoGesture.DRAWING
      lassoPath.clear()
      lassoPath += Point(x, y)
      rememberDown(event, index)
      removeCallbacks(lassoHold)
      postDelayed(lassoHold, HOLD_MS)
    }
    selectionView.invalidate()
  }

  private fun continueLasso(event: MotionEvent, index: Int) {
    if (lassoGesture == LassoGesture.DRAWING) {
      noteMovement(event, index)
      for (h in 0 until event.historySize) {
        eraserPoint[0] = event.getHistoricalX(index, h)
        eraserPoint[1] = event.getHistoricalY(index, h)
        screenToPage.mapPoints(eraserPoint)
        lassoPath += Point(eraserPoint[0], eraserPoint[1])
      }
    }
    readPagePosition(event, index)
    when (lassoGesture) {
      LassoGesture.DRAWING -> lassoPath += Point(eraserPoint[0], eraserPoint[1])
      LassoGesture.MOVING -> {
        moveDx = eraserPoint[0] - moveStartX
        moveDy = eraserPoint[1] - moveStartY
      }
      LassoGesture.RESIZING -> {
        val x = eraserPoint[0]
        val y = eraserPoint[1]
        fun ratio(now: Float, start: Float, anchor: Float) =
          if (abs(start - anchor) < 0.5f) 1f else ((now - anchor) / (start - anchor)).coerceIn(MIN_SCALE, MAX_SCALE)
        if (activeHandle.isCorner) {
          val start = hypot(resizeStartX - resizeAnchorX, resizeStartY - resizeAnchorY).coerceAtLeast(1f)
          // Same scale both ways, but never past the anchor (no mirror images).
          val towards = (x - resizeAnchorX) * (resizeStartX - resizeAnchorX) + (y - resizeAnchorY) * (resizeStartY - resizeAnchorY)
          val s = if (towards <= 0f) MIN_SCALE else (hypot(x - resizeAnchorX, y - resizeAnchorY) / start).coerceIn(MIN_SCALE, MAX_SCALE)
          resizeScaleX = s
          resizeScaleY = s
        } else {
          resizeScaleX = if (activeHandle.stretchesX) ratio(x, resizeStartX, resizeAnchorX) else 1f
          resizeScaleY = if (activeHandle.stretchesY) ratio(y, resizeStartY, resizeAnchorY) else 1f
        }
      }
      LassoGesture.ROTATING -> {
        var degrees = Math.toDegrees((atan2(eraserPoint[1] - rotateCenterY, eraserPoint[0] - rotateCenterX) - rotateStartAngle).toDouble()).toFloat()
        rotateAngle = ((degrees + 540f) % 360f) - 180f // any angle, kept in -180..180 (user request: no snapping)
      }
      LassoGesture.LINE_END -> moveLineEnd(eraserPoint[0] + lineGrabDx, eraserPoint[1] + lineGrabDy)
      LassoGesture.NONE -> Unit
    }
    selectionView.invalidate()
  }

  private fun finishLasso() {
    removeCallbacks(lassoHold)
    when (lassoGesture) {
      LassoGesture.DRAWING -> selectInsideLasso()
      LassoGesture.MOVING -> dropSelection()
      LassoGesture.RESIZING -> dropResize()
      LassoGesture.ROTATING -> dropRotate()
      LassoGesture.LINE_END -> dropLineEnd()
      LassoGesture.NONE -> Unit
    }
    lassoGesture = LassoGesture.NONE
    lassoPath.clear()
    selectionView.invalidate()
    reportSelection()
  }

  private val handlePoint = FloatArray(2)
  private val boxCheck = FloatArray(2)

  private val density: Float
    get() = resources.displayMetrics.density

  /**
   * A point on the selection's box with its margin, as fractions across ([fx], [fy]), put on the
   * screen by [matrix]. The box lives on the page, so it turns and scales with the page view.
   */
  private fun boxPoint(fx: Float, fy: Float, matrix: Matrix, out: FloatArray) {
    val pad = SELECTION_PAD_DP * density / pageToScreen.mapRadius(1f)
    out[0] = selectionBounds.left - pad + fx * (selectionBounds.width() + 2 * pad)
    out[1] = selectionBounds.top - pad + fy * (selectionBounds.height() + 2 * pad)
    matrix.mapPoints(out)
  }

  /** Screen space covered by the top buttons (pixels); the rotate knob must stay clear of it. */
  private var topObstruction = 0f

  fun setTopObstruction(px: Float) {
    if (px == topObstruction) return
    topObstruction = px
    if (hasAnySelection) {
      selectionView.invalidate()
      reportSelection()
    }
  }

  /**
   * The rotate handle: a little above the middle of the box's top edge, or, when that spot would
   * be under the top buttons (a selection near the top of the screen), the same distance below
   * the bottom edge. Which side is decided from the unmoved view so it does not jump mid-gesture.
   */
  private fun rotateHandlePoint(matrix: Matrix, out: FloatArray) {
    val zoom = pageToScreen.mapRadius(1f)
    val offset = (SELECTION_PAD_DP + ROTATE_HANDLE_OFFSET_DP) * density / zoom
    out[0] = selectionBounds.centerX()
    out[1] = selectionBounds.top - offset
    pageToScreen.mapPoints(out)
    val hidden = rotateKnobHidden(out[1], HANDLE_RADIUS_DP * density, ROTATE_KNOB_MARGIN_DP * density, topObstruction)
    out[0] = selectionBounds.centerX()
    out[1] = if (hidden) selectionBounds.bottom + offset else selectionBounds.top - offset
    matrix.mapPoints(out)
  }

  /** The resize handle under the pen (screen pixels), nearest first, or null. */
  private fun handleAt(screenX: Float, screenY: Float): ResizeHandle? {
    var best: ResizeHandle? = null
    var bestDistance = HANDLE_GRAB_DP * density
    // Inside the drawn box a handle's reach shrinks with the box, so a small selection still moves
    // from its middle (see handleClaimsTouch).
    val inside = isInDrawnBox(screenX, screenY)
    val zoom = pageToScreen.mapRadius(1f)
    val shortSide = minOf(selectionBounds.width(), selectionBounds.height()) * zoom + 2 * SELECTION_PAD_DP * density
    for (handle in ResizeHandle.entries) {
      if (!visibleHandle(handle)) continue
      boxPoint(handle.fx, handle.fy, pageToScreen, handlePoint)
      val d = hypot(screenX - handlePoint[0], screenY - handlePoint[1])
      if (d < bestDistance && handleClaimsTouch(d, inside, shortSide, HANDLE_GRAB_DP * density)) {
        best = handle
        bestDistance = d
      }
    }
    return best
  }

  private fun isOnRotateHandle(screenX: Float, screenY: Float): Boolean {
    rotateHandlePoint(pageToScreen, handlePoint)
    return hypot(screenX - handlePoint[0], screenY - handlePoint[1]) < HANDLE_GRAB_DP * density
  }

  /** Inside the box as drawn (its margin included, without the extra grab margin of [isInSelectionBox]). */
  private fun isInDrawnBox(screenX: Float, screenY: Float): Boolean {
    boxCheck[0] = screenX
    boxCheck[1] = screenY
    screenToPage.mapPoints(boxCheck)
    val pad = SELECTION_PAD_DP * density / pageToScreen.mapRadius(1f)
    return boxCheck[0] in (selectionBounds.left - pad)..(selectionBounds.right + pad) &&
      boxCheck[1] in (selectionBounds.top - pad)..(selectionBounds.bottom + pad)
  }

  /** Inside the selection's box, margin included. */
  private fun isInSelectionBox(screenX: Float, screenY: Float): Boolean {
    boxCheck[0] = screenX
    boxCheck[1] = screenY
    screenToPage.mapPoints(boxCheck)
    val pad = (SELECTION_PAD_DP + SELECTION_GRAB_DP) * density / pageToScreen.mapRadius(1f)
    return boxCheck[0] in (selectionBounds.left - pad)..(selectionBounds.right + pad) &&
      boxCheck[1] in (selectionBounds.top - pad)..(selectionBounds.bottom + pad)
  }

  // ---- A straight line: two end handles instead of the box ------------------------------

  /**
   * Set when the selection is one straight line (one stroke, or all the dashes of one patterned
   * line, within [StraightLine]'s tolerance): the canvas then shows a round handle at each end
   * instead of the box. Null for every other selection, which keeps the box.
   */
  private var lineSelection: StraightLine.Ends? = null

  /** The first stroke of the selected line in page order: pen, colour, width, layer and pattern to rebuild with. */
  private var lineTemplate: InkStroke? = null
  private var lineEnd = 0 // which end the pen is dragging: 0 = A, 1 = B
  private var lineGrabDx = 0f
  private var lineGrabDy = 0f
  private var linePreviewEnds: StraightLine.Ends? = null
  private var linePreview: List<InkStroke> = emptyList() // what the drag would commit
  private var linePreviewDrawn: List<Stroke> = emptyList() // the same, ready to draw (not in the mesh cache)

  /** Looks at the selection and sets [lineSelection]. Called whenever the selection changes. */
  private fun detectLineSelection() {
    lineSelection = null
    lineTemplate = null
    if (selection.isEmpty() || selectedItems.isNotEmpty() || selection.size > MAX_LINE_PIECES) return
    val group = selection.first().group
    if (selection.size > 1 && (group == 0L || selection.any { it.group != group })) return
    // Page order, so the pattern keeps starting from the same end of the line.
    val strokes = if (selection.size == 1) listOf(selection.first()) else history.strokes.filter { it in selection }
    var count = 0
    for (stroke in strokes) count += stroke.pointCount
    val xy = FloatArray(count * 2)
    var k = 0
    for (stroke in strokes) {
      for (i in 0 until stroke.pointCount) {
        xy[k++] = stroke.x(i)
        xy[k++] = stroke.y(i)
      }
    }
    lineSelection = StraightLine.detect(xy, itemMm) ?: return
    lineTemplate = strokes.first()
  }

  /** Which end handle (0 or 1) is under a screen point, or -1. */
  private fun lineHandleAt(screenX: Float, screenY: Float): Int {
    val line = lineSelection ?: return -1
    val ends = floatArrayOf(line.ax, line.ay, line.bx, line.by)
    pageToScreen.mapPoints(ends)
    val da = hypot(screenX - ends[0], screenY - ends[1])
    val db = hypot(screenX - ends[2], screenY - ends[3])
    val reach = HANDLE_GRAB_DP * density
    return when {
      da > reach && db > reach -> -1
      da <= db -> 0
      else -> 1
    }
  }

  /** On the line itself (a thumb's width either side): dragging there moves the whole line. */
  private fun isNearLine(screenX: Float, screenY: Float): Boolean {
    val line = lineSelection ?: return false
    val ends = floatArrayOf(line.ax, line.ay, line.bx, line.by)
    pageToScreen.mapPoints(ends)
    val onScreen = StraightLine.Ends(ends[0], ends[1], ends[2], ends[3])
    return StraightLine.distanceToSegment(screenX, screenY, onScreen) <= (SELECTION_PAD_DP + SELECTION_GRAB_DP) * density
  }

  /** The pen moved the dragged end to (x, y) (page units): rebuilds the preview line. */
  private fun moveLineEnd(x: Float, y: Float) {
    val line = lineSelection ?: return
    val template = lineTemplate ?: return
    val ends = StraightLine.moveEnd(line, lineEnd, x, y, StraightLine.MIN_LENGTH_MM * itemMm) ?: return
    linePreviewEnds = ends
    linePreview = buildLine(template, ends)
    // Built straight away and kept out of the mesh cache: a drag makes hundreds of throwaway lines.
    linePreviewDrawn = linePreview.map { drawableOnScreen(it) }
  }

  /**
   * The strokes of a straight line between [ends], made like [template]: same pen, colour, width,
   * layer and pattern (one stroke if solid, else the dashes, all in the template's group).
   * Points are spaced like a snapped shape's, with the template's average pressure.
   */
  private fun buildLine(template: InkStroke, ends: StraightLine.Ends): List<InkStroke> {
    val xy = StraightLine.pointsAlong(ends, SHAPE_POINT_SPACING_PX / pageToScreen.mapRadius(1f))
    val n = xy.size / 2
    var pressure = 0f
    var counted = 0
    for (i in 0 until template.pointCount) {
      val p = template.points[i * InkStroke.FLOATS_PER_POINT + 2]
      if (p >= 0f) {
        pressure += p
        counted++
      }
    }
    val average = if (counted > 0) (pressure / counted).coerceIn(0.05f, 1f) else StrokeInput.NO_PRESSURE
    val f = InkStroke.FLOATS_PER_POINT
    val points = FloatArray(n * f)
    for (i in 0 until n) {
      points[i * f] = xy[i * 2]
      points[i * f + 1] = xy[i * 2 + 1]
      points[i * f + 2] = average
      points[i * f + 3] = StrokeInput.NO_TILT
      points[i * f + 4] = StrokeInput.NO_ORIENTATION
    }
    val whole =
      InkStroke(
        template.brush, points, LongArray(n) { it * 4L }, template.toolType, template.unitLengthCm,
        template.isHighlighter, template.layer, template.mediaMark, template.group, template.lineType,
      )
    if (template.lineType == LineType.SOLID) return listOf(whole)
    return LinePattern.split(whole, template.lineType, maxOf(template.brush.size, MIN_DASH_UNIT_MM * itemMm))
  }

  /** A stroke ready to draw with the page's colours on screen, built fresh (not kept in [StrokeMeshes]). */
  private fun drawableOnScreen(ink: InkStroke): Stroke {
    val stroke = ink.build()
    return if (!darkPage || isOnLightPage(ink)) stroke
    else stroke.copy(stroke.brush.copyWithColorIntArgb(InkColors.onScreen(stroke.brush.colorIntArgb, true)))
  }

  /**
   * Commits an end drag as one undo step: a lone stroke is swapped for the new line in its place;
   * a patterned line (a different number of dashes) is removed and added again.
   */
  private fun dropLineEnd() {
    hideSelection(false)
    val old = lineSelection
    val ends = linePreviewEnds
    val fresh = linePreview
    linePreviewEnds = null
    linePreview = emptyList()
    linePreviewDrawn = emptyList()
    val moved = old != null && ends != null && hypot(ends.ax - old.ax, ends.ay - old.ay) + hypot(ends.bx - old.bx, ends.by - old.by) > MIN_LINE_DRAG_DP * density / pageToScreen.mapRadius(1f)
    if (!moved || fresh.isEmpty()) {
      finishedView.invalidate()
      return
    }
    val replaced = selection.toList()
    history.grouped {
      if (replaced.size == 1 && fresh.size == 1) {
        history.replace(mapOf(replaced[0] to fresh[0]))
      } else {
        val doomed: Set<InkStroke> = Collections.newSetFromMap<InkStroke>(IdentityHashMap()).apply { addAll(replaced) }
        history.removeAll { it in doomed }
        for (stroke in fresh) history.add(stroke, insertIndexFor(stroke))
      }
    }
    selection.clear()
    selection += fresh
    updateSelectionBounds()
    pageChanged()
  }

  /** Edge handles are left out when the box is too small for them to be told from the corners. */
  private fun visibleHandle(handle: ResizeHandle): Boolean {
    if (handle.isCorner) return true
    val zoom = pageToScreen.mapRadius(1f)
    val room = 3 * HANDLE_GRAB_DP * density
    return if (handle.stretchesX) selectionBounds.height() * zoom >= room else selectionBounds.width() * zoom >= room
  }

  /** Commits a turn: the selection is replaced by rotated copies, in one undo step. */
  private fun dropRotate() {
    hideSelection(false)
    if (abs(rotateAngle) > 0.5f) {
      transformSelection(Matrix().apply { setRotate(rotateAngle, rotateCenterX, rotateCenterY) }, 1f)
    } else {
      finishedView.invalidate()
    }
    rotateAngle = 0f
  }

  /**
   * Replaces the selected strokes and items with copies moved by [matrix] (page units), as one
   * undo step; [widthScale] thickens or thins the lines to match a resize.
   */
  private fun transformSelection(matrix: Matrix, widthScale: Float) {
    history.grouped {
      if (selection.isNotEmpty()) {
        val copies = selection.associateWith { it.mapped(matrix, widthScale) }
        history.replace(copies)
        selection.clear()
        selection += copies.values
      }
      if (selectedItems.isNotEmpty()) {
        changeItems { list -> for (i in list.indices) if (list[i].id in selectedItems) list[i] = list[i].mapped(matrix) }
      }
    }
    updateSelectionBounds()
    pageChanged()
  }

  /** Hides the selection from the page while a drag shows it moving in the overlay (or shows it again). */
  private fun hideSelection(hide: Boolean) {
    chunkCache.setHidden(if (hide) selection else emptyList())
    val layout = pageLayout
    val pages = if (layout != null) (hiddenFromRasters + selection).map { layout.pageAt(it.bounds.centerY()) }.toSet() else emptySet()
    if (hiddenFromRasters.isNotEmpty() || hide) tiles.invalidateAll()
    hiddenFromRasters.clear()
    if (hide) hiddenFromRasters += selection
    pages.forEach(rasters::invalidate)
    val hadHiddenItems = hiddenItems.isNotEmpty()
    hiddenItems.clear()
    if (hide) hiddenItems += selectedItems
    if (hadHiddenItems || hiddenItems.isNotEmpty()) invalidateProjectorContent()
  }

  /** Commits a resize: the selection is replaced by scaled copies, in one undo step. */
  private fun dropResize() {
    hideSelection(false)
    if (abs(resizeScaleX - 1f) > 0.01f || abs(resizeScaleY - 1f) > 0.01f) {
      val matrix = Matrix().apply { setScale(resizeScaleX, resizeScaleY, resizeAnchorX, resizeAnchorY) }
      transformSelection(matrix, kotlin.math.sqrt(abs(resizeScaleX * resizeScaleY)))
    } else {
      finishedView.invalidate()
    }
    resizeScaleX = 1f
    resizeScaleY = 1f
  }

  /** Puts the selection on the clipboard. The selection stays. */
  fun copySelection() {
    if (!hasAnySelection) return
    // Keep page order, so pasted strokes overlap the same way.
    clipboard = history.strokes.filter { it in selection }
    clipboardItems = items.filter { it.id in selectedItems }
    clipboardBounds.set(selectionBounds)
    SharedClipboard.unitsPerMm = itemMm
    SharedClipboard.imagesDir = imagesDir
    onClipboardChanged(true)
  }

  /** Empties the clipboard (the Paste button goes away). */
  fun clearClipboard() {
    clipboard = emptyList()
    clipboardItems = emptyList()
    onClipboardChanged(false)
  }

  /** Copies the selection, then removes it from the page (one undo brings it back). */
  fun cutSelection() {
    copySelection()
    deleteSelection()
  }

  /**
   * Adds copies of the clipboard to the page, as one undo step, and selects them so they can
   * be dragged into place. They land just below and right of where they were copied from if
   * that is on screen, otherwise in the middle of the screen.
   */
  fun paste() {
    if (clipboard.isEmpty() && clipboardItems.isEmpty()) return
    adoptClipboard()
    val visible = RectF(0f, 0f, width.toFloat(), height.toFloat()).also { screenToPage.mapRect(it) }
    val (dx, dy) =
      if (RectF.intersects(visible, clipboardBounds)) {
        nudge() to nudge()
      } else {
        (visible.centerX() - clipboardBounds.centerX()) to (visible.centerY() - clipboardBounds.centerY())
      }
    placeCopies(clipboard, clipboardItems, dx, dy, toLayer = layers.current)
    // Pasting again steps further along from this copy, not from the original: before that fix
    // every paste landed on the same spot, piling copies up and the page lagged badly.
    clipboard = history.strokes.filter { it in selection }
    clipboardItems = items.filter { it.id in selectedItems }
    clipboardBounds.offset(dx, dy)
  }

  /** Pastes centred on the spot where the pen was held to open the paste menu. */
  fun pasteAtHeldPoint() {
    if (clipboard.isEmpty() && clipboardItems.isEmpty()) return
    adoptClipboard()
    placeCopies(clipboard, clipboardItems, heldX - clipboardBounds.centerX(), heldY - clipboardBounds.centerY(), toLayer = layers.current)
  }

  /** A copy of the selection just below and right of it, selected. The clipboard is untouched. */
  fun duplicateSelection() {
    if (!hasAnySelection) return
    val source = history.strokes.filter { it in selection }
    placeCopies(source, items.filter { it.id in selectedItems }, nudge(), nudge())
  }

  /** Mirrors the selection in place: [sideways] flips left and right, otherwise top and bottom. */
  fun mirrorSelection(sideways: Boolean) {
    if (!hasAnySelection) return
    val cx = selectionBounds.centerX()
    val cy = selectionBounds.centerY()
    val sx = if (sideways) -1f else 1f
    val sy = if (sideways) 1f else -1f
    transformSelection(Matrix().apply { setScale(sx, sy, cx, cy) }, 1f)
    selectionView.invalidate()
    reportSelection()
  }

  private fun nudge() = PASTE_OFFSET_DP * resources.displayMetrics.density / pageToScreen.mapRadius(1f)

  /** Adds moved copies of [source] in one undo step and selects them. Highlights stay under ink. */
  private fun placeCopies(source: List<InkStroke>, itemSource: List<PageItem>, dx: Float, dy: Float, toLayer: Int? = null) {
    clearSelection()
    history.beginGroup()
    if (itemSource.isNotEmpty()) {
      val move = Matrix().apply { setTranslate(dx, dy) }
      val copies = itemSource.map { it.mapped(move).withNewId() }
      changeItems { it += copies }
      selectedItems += copies.map { it.id }
    }
    val regroup = HashMap<Long, Long>() // a copied patterned line gets its own group id
    for (stroke in source) {
      var moved = stroke.translated(dx, dy)
      if (moved.group != 0L) moved = moved.withGroup(regroup.getOrPut(moved.group) { LinePattern.newGroupId() })
      val copy = if (toLayer != null && toLayer != moved.layer) moved.withLayer(toLayer) else moved
      history.add(copy, insertIndexFor(copy))
      selection += copy
    }
    history.endGroup()
    updateSelectionBounds()
    pageChanged()
    selectionView.invalidate()
    reportSelection()
  }

  // ---- Hold the pen still on the page: paste menu --------------------------------------

  /** Called with a screen point when the pen is held still and there is something to paste. */
  var onPasteMenu: (screenX: Float, screenY: Float) -> Unit = { _, _ -> }

  private var heldX = 0f // page units
  private var heldY = 0f
  private var downScreenX = 0f
  private var downScreenY = 0f
  private var movedFromDown = false
  private val lassoHold = Runnable { if (!movedFromDown) openPasteMenu() }

  /** Drops whatever the pen started (a dot or a lasso) and opens the paste menu at that spot. */
  private fun openPasteMenu() {
    if (clipboard.isEmpty() && clipboardItems.isEmpty()) return
    activeStrokeId?.let { id ->
      pendingStrokes.remove(id)
      liveMirror.end(id)
      postOnAnimation { inProgressView.cancelStroke(id) }
    }
    activeStrokeId = null
    if (lassoGesture == LassoGesture.DRAWING) {
      lassoGesture = LassoGesture.NONE
      lassoPath.clear()
      selectionView.invalidate()
    }
    shapeSnapped = true // ignore the rest of this pen gesture
    eraserPoint[0] = downScreenX
    eraserPoint[1] = downScreenY
    screenToPage.mapPoints(eraserPoint)
    heldX = eraserPoint[0]
    heldY = eraserPoint[1]
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    onPasteMenu(downScreenX, downScreenY)
  }

  private fun rememberDown(event: MotionEvent, index: Int) {
    downScreenX = event.getX(index)
    downScreenY = event.getY(index)
    movedFromDown = false
  }

  private fun noteMovement(event: MotionEvent, index: Int) {
    if (!movedFromDown && hypot(event.getX(index) - downScreenX, event.getY(index) - downScreenY) > holdSlopPx) {
      movedFromDown = true
      removeCallbacks(lassoHold)
    }
  }

  private fun cancelLasso() {
    if (lassoGesture.changesSelection) {
      hideSelection(false)
      finishedView.invalidate()
    }
    linePreviewEnds = null
    linePreview = emptyList()
    lassoGesture = LassoGesture.NONE
    lassoPath.clear()
    selectionView.invalidate()
    reportSelection()
  }

  private fun selectInsideLasso() {
    if (presenting || lassoPath.size < 2) return
    val loopBounds = lassoBounds()
    // About 10 dp on screen, in page units: how near a drawn line must pass to pick something up.
    val tolerance = 10f * resources.displayMetrics.density / pageToScreen.mapRadius(1f)
    if (isOpenLine(lassoPath, loopBounds)) {
      // A line, not a loop: everything it crosses.
      if (maxOf(loopBounds.width(), loopBounds.height()) < tolerance) return // a tap, not a line
      for (stroke in history.strokes) {
        if (!isHiddenLayer(stroke) && stroke.crossesLine(lassoPath, loopBounds, tolerance)) selection += stroke
      }
      for (item in items) if (lineTouchesRect(lassoPath, item.bounds(itemBox), tolerance)) selectedItems += item.id
    } else {
      if (lassoPath.size < 3) return
      for (stroke in history.strokes) {
        if (!isHiddenLayer(stroke) && stroke.isMostlyInside(lassoPath, loopBounds)) selection += stroke
      }
      // Photos and text boxes: picked up when the loop covers any part of them.
      for (item in items) if (loopTouchesRect(lassoPath, item.bounds(itemBox))) selectedItems += item.id
    }
    addGroupMates()
    updateSelectionBounds()
  }

  /** A patterned line is one object: picking up one dash picks up all of them. */
  private fun addGroupMates() {
    for (mate in withGroupMates(selection, history.strokes) { it.group }) if (!isHiddenLayer(mate)) selection += mate
  }

  /** Commits a drag: the selection is replaced by moved copies, in one undo step. */
  private fun dropSelection() {
    hideSelection(false)
    val moved = hypot(moveDx, moveDy) * pageToScreen.mapRadius(1f) > MIN_MOVE_PX
    if (moved) {
      transformSelection(Matrix().apply { setTranslate(moveDx, moveDy) }, 1f)
    } else {
      finishedView.invalidate()
    }
    moveDx = 0f
    moveDy = 0f
  }

  /** Removes the selected strokes from the page (one undo brings them back). */
  fun deleteSelection() {
    if (!hasAnySelection) return
    val doomed: Set<InkStroke> = Collections.newSetFromMap<InkStroke>(IdentityHashMap()).apply { addAll(selection) }
    val doomedItems = selectedItems.toSet()
    clearSelection()
    history.grouped {
      history.removeAll { it in doomed }
      changeItems { list -> list.removeAll { it.id in doomedItems } }
    }
    pageChanged()
  }

  fun clearSelection() {
    if (!hasAnySelection && lassoGesture == LassoGesture.NONE) return
    if (lassoGesture.changesSelection) hideSelection(false)
    selection.clear()
    selectedItems.clear()
    selectionBounds.setEmpty()
    lineSelection = null
    lineTemplate = null
    lassoGesture = LassoGesture.NONE
    lassoPath.clear()
    selectionView.invalidate()
    finishedView.invalidate()
    onSelectionChanged(null)
  }

  private fun updateSelectionBounds() {
    selectionBounds.setEmpty()
    for (stroke in selection) selectionBounds.union(chunkCache.boundsOf(stroke))
    for (item in items) if (item.id in selectedItems) selectionBounds.union(item.bounds(itemBox))
    detectLineSelection()
  }

  private val reportedRect = RectF()

  private fun reportSelection() {
    if (!hasAnySelection || lassoGesture != LassoGesture.NONE) {
      onSelectionChanged(null)
      return
    }
    // Everything the selection draws on screen (turned box and rotate handle), so the buttons
    // placed above it never cover a handle.
    lineSelection?.let { line ->
      // A straight line shows two end handles only (no box, no rotate handle).
      val ends = floatArrayOf(line.ax, line.ay, line.bx, line.by)
      pageToScreen.mapPoints(ends)
      reportedRect.set(minOf(ends[0], ends[2]), minOf(ends[1], ends[3]), maxOf(ends[0], ends[2]), maxOf(ends[1], ends[3]))
      reportedRect.inset(-HANDLE_RADIUS_DP * density, -HANDLE_RADIUS_DP * density)
      onSelectionChanged(RectF(reportedRect))
      return
    }
    // Start from the first corner and grow. (Testing reportedRect.isEmpty instead was wrong: a
    // single point has zero width, so every corner restarted the rect and only the last one plus
    // the knob survived, and the buttons were placed against that sliver. QA 26 Nov.)
    boxPoint(0f, 0f, pageToScreen, handlePoint)
    reportedRect.set(handlePoint[0], handlePoint[1], handlePoint[0], handlePoint[1])
    for ((fx, fy) in listOf(1f to 0f, 1f to 1f, 0f to 1f)) {
      boxPoint(fx, fy, pageToScreen, handlePoint)
      reportedRect.union(handlePoint[0], handlePoint[1])
    }
    rotateHandlePoint(pageToScreen, handlePoint)
    reportedRect.union(handlePoint[0], handlePoint[1])
    reportedRect.inset(-HANDLE_RADIUS_DP * density, -HANDLE_RADIUS_DP * density)
    onSelectionChanged(RectF(reportedRect))
  }

  /** Draws the lasso loop while it is drawn, and the selection's box (and moving strokes). */
  private inner class SelectionView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val renderer = CanvasStrokeRenderer.create()
    private val dashed =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = InkColors.onScreen(SELECTION_PAPER_COLOR, darkPage)
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 5f * density), 0f)
      }
    private val path = Path()
    private val box = RectF()
    private val movedMatrix = Matrix()
    private var lassoScreenPoints = FloatArray(64)

    override fun onDraw(canvas: Canvas) {
      drawSearchHit(canvas)
      drawPlayback(canvas)
      if (lassoGesture == LassoGesture.DRAWING && lassoPath.size > 1) {
        path.rewind()
        val count = lassoPath.size
        val needed = count * 2
        if (lassoScreenPoints.size < needed) lassoScreenPoints = FloatArray(needed)
        val points = lassoScreenPoints
        lassoPath.forEachIndexed { i, p ->
          points[i * 2] = p.x
          points[i * 2 + 1] = p.y
        }
        pageToScreen.mapPoints(points, 0, points, 0, count)
        path.moveTo(points[0], points[1])
        for (i in 1 until count) path.lineTo(points[i * 2], points[i * 2 + 1])
        // Show the loop closed while it will be read as a loop, so it is clear before the pen lifts.
        if (lassoPath.size > 2 && !isOpenLine(lassoPath, lassoBounds())) path.close()
        canvas.drawPath(path, dashed)
        return
      }
      if (!hasAnySelection) return

      // While dragged, resized or turned, the selection follows the pen here; the originals
      // are hidden from the page meanwhile.
      movedMatrix.set(pageToScreen)
      movedMatrix.preTranslate(moveDx, moveDy)
      movedMatrix.preScale(resizeScaleX, resizeScaleY, resizeAnchorX, resizeAnchorY)
      movedMatrix.preRotate(rotateAngle, rotateCenterX, rotateCenterY)
      if (lassoGesture == LassoGesture.LINE_END) {
        // The line as it will be if the pen lifts now (until the first move, the line as it is).
        if (linePreviewDrawn.isEmpty()) for (stroke in selection) renderer.draw(canvas, screenVersion(stroke), pageToScreen)
        else for (stroke in linePreviewDrawn) renderer.draw(canvas, stroke, pageToScreen)
      } else if (lassoGesture.changesSelection) {
        canvas.save()
        canvas.concat(movedMatrix)
        for (item in items) if (item.id in selectedItems) itemPainter.draw(canvas, item) { itemTextColor(item, it) }
        for (stroke in selection) renderer.draw(canvas, screenVersion(stroke), movedMatrix)
        canvas.restore()
      }

      // A straight line: a round handle at each end, nothing else.
      val line = lineSelection
      if (line != null) {
        val ends = linePreviewEnds ?: line
        val radius = HANDLE_RADIUS_DP * density
        for (end in 0..1) {
          point[0] = if (end == 0) ends.ax else ends.bx
          point[1] = if (end == 0) ends.ay else ends.by
          movedMatrix.mapPoints(point)
          canvas.drawCircle(point[0], point[1], radius, handleFill)
          canvas.drawCircle(point[0], point[1], radius, handleEdge)
        }
        return
      }

      // The box, drawn through its four corners so it turns with the page and the selection.
      path.rewind()
      for ((i, corner) in listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f).withIndex()) {
        boxPoint(corner.first, corner.second, movedMatrix, point)
        if (i == 0) path.moveTo(point[0], point[1]) else path.lineTo(point[0], point[1])
      }
      path.close()
      canvas.drawPath(path, dashed)

      // Rotate handle on a short stem from the nearer edge: the top normally, the bottom when the
      // knob was flipped below the box (near the top chrome). Drawing it from the top edge always
      // ran the stem through the whole selection.
      rotateHandlePoint(movedMatrix, point)
      val knobX = point[0]
      val knobY = point[1]
      boxPoint(0.5f, 0f, movedMatrix, point)
      var stemX = point[0]
      var stemY = point[1]
      boxPoint(0.5f, 1f, movedMatrix, point)
      if (hypot(point[0] - knobX, point[1] - knobY) < hypot(stemX - knobX, stemY - knobY)) {
        stemX = point[0]
        stemY = point[1]
      }
      point[0] = knobX
      point[1] = knobY
      canvas.drawLine(stemX, stemY, point[0], point[1], solid)
      canvas.drawCircle(point[0], point[1], HANDLE_RADIUS_DP * density, handleFill)
      canvas.drawCircle(point[0], point[1], HANDLE_RADIUS_DP * density, handleEdge)
      canvas.drawCircle(point[0], point[1], HANDLE_RADIUS_DP * 0.35f * density, handleDot)
      if (lassoGesture == LassoGesture.ROTATING) {
        canvas.drawText("${Math.round(rotateAngle)}°", point[0] + 14f * density, point[1] - 10f * density, label)
      }

      // Resize handles: corners round, edge midpoints a little smaller.
      for (handle in ResizeHandle.entries) {
        if (!visibleHandle(handle)) continue
        val radius = (if (handle.isCorner) HANDLE_RADIUS_DP else HANDLE_RADIUS_DP * 0.75f) * density
        boxPoint(handle.fx, handle.fy, movedMatrix, point)
        canvas.drawCircle(point[0], point[1], radius, handleFill)
        canvas.drawCircle(point[0], point[1], radius, handleEdge)
      }
    }

    private val point = FloatArray(2)
    private val solid =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.5f * density
        color = InkColors.onScreen(SELECTION_PAPER_COLOR, darkPage)
      }
    private val handleDot =
      Paint(Paint.ANTI_ALIAS_FLAG).apply { color = InkColors.onScreen(SELECTION_PAPER_COLOR, darkPage) }
    private val label =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * density
        color = InkColors.onScreen(0xFF000000.toInt(), darkPage)
      }

    private val handleFill =
      Paint(Paint.ANTI_ALIAS_FLAG).apply { color = InkColors.pageOnScreen(darkPage) }
    private val handleEdge =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = InkColors.onScreen(SELECTION_PAPER_COLOR, darkPage)
      }

    fun updateDarkPage() {
      val sel = InkColors.onScreen(SELECTION_PAPER_COLOR, darkPage)
      dashed.color = sel
      solid.color = sel
      handleDot.color = sel
      handleEdge.color = sel
      handleFill.color = InkColors.pageOnScreen(darkPage)
      label.color = InkColors.onScreen(0xFF000000.toInt(), darkPage)
    }
  }

  private inner class EraserCursorView(context: Context) : View(context) {
    private val paint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
        color = InkColors.onScreen(ERASER_CURSOR_PAPER_COLOR, darkPage)
      }

    fun updateDarkPage() {
      paint.color = InkColors.onScreen(ERASER_CURSOR_PAPER_COLOR, darkPage)
    }
    private var x = 0f
    private var y = 0f
    private var radius = 0f
    private var shown = false

    fun show(x: Float, y: Float, radius: Float) {
      this.x = x
      this.y = y
      this.radius = radius
      shown = true
      invalidate()
    }

    fun hide() {
      if (!shown) return
      shown = false
      invalidate()
    }

    override fun onDraw(canvas: Canvas) {
      if (shown) canvas.drawCircle(x, y, radius, paint)
    }
  }

  private companion object {
    /** Black at 60% on paper; shown inverted on the dark page. */
    const val ERASER_CURSOR_PAPER_COLOR = 0x99000000.toInt()

    /** Smallest stroke detail kept, in screen pixels. Smaller = smoother curves, more work. */
    const val PEN_DETAIL_PX = 0.1f

    /** Dashes are measured in line widths, but never in less than this (mm). */
    const val MIN_DASH_UNIT_MM = 0.35f

    /** A patterned line with more pieces than this is never offered line handles (the check stays cheap). */
    const val MAX_LINE_PIECES = 3000

    /** An end drag shorter than this (screen dp) is a tap, not an edit. */
    const val MIN_LINE_DRAG_DP = 2f

    const val MIN_ZOOM = BASE_MIN_ZOOM
    const val MAX_ZOOM = BASE_MAX_ZOOM

    /** Search results: highlighted this long (ms) before fading; a soft amber on either page. */
    /** Playback glow: ink written in the last 4 s before the play position (and 0.3 s after). */
    const val PLAYBACK_WINDOW_MS = 4000L
    const val PLAYBACK_AHEAD_MS = 300L
    const val PLAYBACK_ALPHA = 70

    /** How close (dp) a finger tap must land to ink to count as tapping it (media seek). */
    const val FINGER_REACH_DP = 24f

    const val SEARCH_HIT_MS = 2500L
    const val SEARCH_HIT_COLOR = 0xFFFFC34D.toInt()
    const val SEARCH_HIT_ALPHA = 90

    /** Finger travel, in multiples of Android's touch slop, that turns a touch into a pan. */
    const val FINGER_SLOP_FACTOR = 1.5f

    /** The page counts as still when no pan/zoom happened for this long. */
    const val PAN_ZOOM_SETTLE_MS = 120L

    /** After the pen leaves, fingers are still treated as its palm for this long. */
    const val PEN_NEARBY_GRACE_MS = 250L

    /** Hovering is forgotten if no hover event arrived for this long (see [penNearby]). */
    const val HOVER_STALE_MS = 600L

    /** Pen within this distance (dp) of the selection's box grabs it instead of starting a new loop. */
    const val SELECTION_GRAB_DP = 10f

    /** Selection box margin around the strokes, and its corner handle (dp). */
    const val SELECTION_PAD_DP = 6f
    const val HANDLE_RADIUS_DP = 7f
    const val HANDLE_GRAB_DP = 24f

    /** How far above the box the rotate handle sits (dp). */
    const val ROTATE_HANDLE_OFFSET_DP = 26f
    /** Air kept between the rotate knob's edge and the top buttons before it flips below the box. */
    const val ROTATE_KNOB_MARGIN_DP = 6f

    /** Resizing limits, relative to the size when the handle was grabbed. */
    const val MIN_SCALE = 0.1f
    const val MAX_SCALE = 10f

    /** How far a paste lands from the original (dp), so it does not hide exactly on top. */
    const val PASTE_OFFSET_DP = 24f

    /** A finger touch shorter than this, without moving, is a tap. */
    const val FINGER_TAP_MS = 300L

    /** Two taps closer together than this are a double tap. */
    const val DOUBLE_TAP_MS = 320L

    /** A drag shorter than this (screen px) is a tap, not a move. */
    const val MIN_MOVE_PX = 2f

    /** Dashed lasso and selection box: mid grey on paper, light grey on the dark page. */
    const val SELECTION_PAPER_COLOR = 0xFF5F6670.toInt()

    /** A new text box is this wide (mm); it grows downwards as you type. */
    const val TEXT_BOX_WIDTH_MM = 90f

    /** Ruling spacing on notebook paper: page units per dp (dp measured at 160 per inch). */
    const val PAPER_UNITS_PER_DP = PageLayout.UNITS_PER_MM * 25.4f / 160f

    /** The pen within this distance (dp) of a ruler or protractor edge draws along the edge. */
    const val RULER_SNAP_DP = 22f

    /** Momentum: speed is measured over the last [FLING_WINDOW_MS] of the pan. */
    const val PAN_SAMPLES = 16
    const val FLING_WINDOW_MS = 80L

    /** A release slower than this (dp per second) just stops. */
    const val FLING_MIN_SPEED_DP = 250f

    /** The glide ends below this speed (dp per second). */
    const val FLING_STOP_SPEED_DP = 12f

    /** How quickly a glide slows: speed falls to about a third every this many seconds. */
    const val FLING_TIME_CONSTANT_S = 0.33f

    /** Pull this far (dp) past the last page and let go to add a page. */
    const val PULL_TO_ADD_DP = 180f // was 150 (user request: heavier)

    /** How quickly the pull stiffens (0 = no resistance; 1 before 13 Nov, 1.5 before 15 Nov). Finger travel to add a page = PULL_TO_ADD_DP * (1 + PULL_RESISTANCE / 2): now about 400 dp, was 260. */
    const val PULL_RESISTANCE = 2.5f

    /** Undo steps kept when the notebook is saved (and so after it is closed). */
    const val MAX_SAVED_UNDO_STEPS = 100

    /** Writing below this fraction of the last page adds the next page. */
    const val AUTO_PAGE_AT = 0.8f

    /** Extra hold, after the shape hold, before a still pen opens the paste menu. */
    const val PEN_PASTE_EXTRA_HOLD_MS = 600L

    /** A pinch ending within this share of screen width snaps the page to exactly screen-wide. */
    const val FIT_SNAP_SHARE = 0.06f

    /** Margin round a page when fitting it to the screen width (dp). */
    const val FIT_MARGIN_DP = 24f
    /** Gap kept between the status bar and the top of the first page (dp). */
    const val STATUS_GAP_DP = 16f

    /** How long the pen must hold still at the end of a stroke to turn it into a shape. */
    const val HOLD_MS = 500L

    /** Pen movement smaller than this (dp) still counts as holding still. */
    const val HOLD_SLOP_DP = 6f

    /** What a drawing finger reports as pen pressure (a finger has no real pressure). */
    const val FINGER_PEN_PRESSURE = 0.5f

    /** Strokes shorter than this (dp on screen) are never turned into shapes: letters, ticks. */
    const val MIN_SHAPE_DP = 48f

    /** Spacing of the points laid along a clean shape, in screen pixels. */
    const val SHAPE_POINT_SPACING_PX = 3f

    fun MotionEvent.isPen(index: Int): Boolean =
      getToolType(index).let {
        it == MotionEvent.TOOL_TYPE_STYLUS || it == MotionEvent.TOOL_TYPE_ERASER
      }
  }
}

/**
 * Cut or copied strokes and items, shared by every open notebook in the app (it lives as long
 * as the app does), so something copied in one notebook can be pasted in another.
 */
object SharedClipboard {
  var strokes: List<InkStroke> = emptyList()
  var items: List<PageItem> = emptyList()
  val bounds = RectF()

  /** Page units per millimetre where it was copied (to rescale when pasted elsewhere). */
  var unitsPerMm = 0f

  /** The images folder its photos are in. */
  var imagesDir: java.io.File? = null
}
