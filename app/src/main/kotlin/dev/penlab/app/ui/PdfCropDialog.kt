package dev.penlab.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Matrix as AndroidMatrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.penlab.app.ui.components.PenLabChoiceChip
import dev.penlab.app.ui.components.PenLabIconButton
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.PenLabLayout
import dev.penlab.app.ui.theme.PenLabShapes
import dev.penlab.app.ui.theme.PenLabSpacing
import dev.penlab.app.ui.theme.PenLabTextStyles
import dev.penlab.app.ui.theme.softShadow
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val THUMB_CACHE_BYTES = 16 * 1024 * 1024
private const val PREVIEW_CACHE_BYTES = 24 * 1024 * 1024
private const val THUMB_WIDTH_PX = 180
private const val PREVIEW_WIDTH_PX = 1280

/**
 * Two-pane tablet modal for browsing pages of an external PDF and cropping a region onto the open
 * notebook canvas as a lossless PNG image item.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfCropDialog(
  uri: Uri,
  imagesDir: File,
  onPlaced: (fileName: String, adaptToDarkPaper: Boolean) -> Unit,
  onDismiss: () -> Unit,
  darkPage: Boolean = false,
) {
  val context = LocalContext.current
  val scheme = MaterialTheme.colorScheme
  val mainHandler = remember { Handler(Looper.getMainLooper()) }

  var title by remember(uri) { mutableStateOf("Add from PDF") }
  var loading by remember(uri) { mutableStateOf(true) }
  var loadError by remember(uri) { mutableStateOf<String?>(null) }
  var previewError by remember(uri) { mutableStateOf<String?>(null) }
  var previewRetryNonce by remember(uri) { mutableIntStateOf(0) }
  var pageCount by remember(uri) { mutableIntStateOf(0) }
  var currentPage by remember(uri) { mutableIntStateOf(0) }
  var cropRect by remember(uri) { mutableStateOf(NormalizedCropRect.DEFAULT) }
  var adaptToDarkPaper by remember(uri, darkPage) { mutableStateOf(defaultDarkPaperCropAdapt(darkPage)) }
  var thumbTick by remember(uri) { mutableIntStateOf(0) }
  var previewTick by remember(uri) { mutableIntStateOf(0) }
  var placing by remember(uri) { mutableStateOf(false) }
  var placeError by remember(uri) { mutableStateOf<String?>(null) }

  val session = remember(uri) { PdfCropSession(context.applicationContext, uri, mainHandler) }

  DisposableEffect(session) {
    session.open(
      onReady = { resolvedTitle, count ->
        title = resolvedTitle
        pageCount = count
        loading = false
      },
      onError = { msg ->
        loadError = msg
        loading = false
      },
    )
    onDispose { session.close() }
  }

  LaunchedEffect(currentPage, pageCount, previewRetryNonce) {
    if (pageCount > 0 && currentPage in 0 until pageCount) {
      previewError = null
      session.requestPreview(
        pageIndex = currentPage,
        onRendered = {
          previewError = null
          previewTick++
        },
        onError = { msg ->
          previewError = msg
          previewTick++
        },
      )
    }
  }

  fun selectPage(page: Int) {
    if (placing || page !in 0 until pageCount || page == currentPage) return
    currentPage = page
    cropRect = NormalizedCropRect.DEFAULT
    placeError = null
    previewError = null
  }

  val listState = rememberLazyListState()
  LaunchedEffect(currentPage, pageCount) {
    if (pageCount > 0 && currentPage in 0 until pageCount) {
      listState.animateScrollToItem(currentPage)
    }
  }

  Dialog(
    onDismissRequest = { if (!placing) onDismiss() },
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    Surface(
      modifier =
        Modifier
          .fillMaxWidth(0.94f)
          .fillMaxHeight(0.90f)
          .widthIn(max = 1040.dp)
          .softShadow(PenLabShapes.extraLarge, elevation = PenLabLayout.dialogElevation),
      shape = PenLabShapes.extraLarge,
      color = PenLabComponentTokens.dialogBg,
      contentColor = scheme.onSurface,
      tonalElevation = 0.dp,
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // ---- Header ----
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(start = PenLabSpacing.xl, end = PenLabSpacing.md, top = PenLabSpacing.lg, bottom = PenLabSpacing.md),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = title,
              color = scheme.onSurface,
              style = MaterialTheme.typography.headlineSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "Scroll to a page and drag the box to crop what you want on the canvas",
              color = scheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Spacer(Modifier.width(PenLabSpacing.sm))
          PenLabIconButton(
            icon = PenLabIcons.Close,
            contentDescription = "Close",
            enabled = !placing,
            onClick = onDismiss,
          )
        }

        HorizontalDivider(color = scheme.outlineVariant, thickness = 1.dp)

        // ---- Body ----
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          when {
            loading -> {
              Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PenLabSpacing.md),
              ) {
                CircularProgressIndicator()
                Text(
                  text = "Opening PDF…",
                  color = scheme.onSurfaceVariant,
                  style = MaterialTheme.typography.bodyMedium,
                )
              }
            }
            loadError != null -> {
              Column(
                modifier = Modifier.align(Alignment.Center).padding(PenLabSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PenLabSpacing.md),
              ) {
                Text(
                  text = loadError ?: "Could not open this PDF.",
                  color = scheme.onSurface,
                  style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(
                  onClick = onDismiss,
                  modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
                ) {
                  Text("Close")
                }
              }
            }
            else -> {
              Row(modifier = Modifier.fillMaxSize()) {
                // Left scrollable page thumbnail strip
                LazyColumn(
                  state = listState,
                  modifier = Modifier.width(148.dp).fillMaxHeight(),
                  contentPadding = PaddingValues(PenLabSpacing.sm),
                  verticalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
                ) {
                  items(pageCount) { index ->
                    val selected = index == currentPage
                    @Suppress("UNUSED_EXPRESSION")
                    thumbTick
                    val thumb = session.cachedThumb(index)
                    LaunchedEffect(index) {
                      if (thumb == null) session.requestThumb(index) { thumbTick++ }
                    }
                    Column(
                      modifier =
                        Modifier
                          .fillMaxWidth()
                          .clip(PenLabShapes.small)
                          .background(if (selected) PenLabComponentTokens.chipSelectedBg else PenLabComponentTokens.cardBg)
                          .border(
                            width = if (selected) PenLabLayout.ringSelected else PenLabLayout.hairline,
                            color = if (selected) scheme.primary else PenLabComponentTokens.cardBorder,
                            shape = PenLabShapes.small,
                          )
                          .clickable(enabled = !placing, role = Role.Button) { selectPage(index) }
                          .padding(PenLabSpacing.xs),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.spacedBy(PenLabSpacing.xs),
                    ) {
                      val thumbAspect =
                        thumb?.let { it.width.toFloat() / it.height.toFloat() }
                          ?: PenLabLayout.a4PortraitAspect
                      Box(
                        modifier =
                          Modifier
                            .fillMaxWidth()
                            .aspectRatio(thumbAspect)
                            .clip(PenLabShapes.extraSmall)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                      ) {
                        if (thumb != null && !thumb.isRecycled) {
                          Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = "Page ${index + 1}",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                          )
                        }
                      }
                      Text(
                        text = "${index + 1}",
                        color = if (selected) PenLabComponentTokens.chipSelectedContent else scheme.onSurfaceVariant,
                        style = PenLabTextStyles.tabularMedium,
                      )
                    }
                  }
                }

                VerticalDivider(color = scheme.outlineVariant, thickness = 1.dp)

                // Right interactive crop stage
                @Suppress("UNUSED_EXPRESSION")
                previewTick
                val preview = session.cachedPreview(currentPage)
                Box(
                  modifier =
                    Modifier
                      .weight(1f)
                      .fillMaxHeight()
                      .background(scheme.surfaceVariant.copy(alpha = 0.35f))
                      .padding(PenLabSpacing.lg),
                  contentAlignment = Alignment.Center,
                ) {
                  BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                  ) {
                    val pageAspect =
                      preview?.let { it.width.toFloat() / it.height.toFloat() }
                        ?: PenLabLayout.a4PortraitAspect
                    val containerAspect =
                      if (maxHeight > 0.dp) maxWidth / maxHeight else pageAspect
                    val pageBoxModifier =
                      if (pageAspect > containerAspect) {
                        Modifier.fillMaxWidth().aspectRatio(pageAspect)
                      } else {
                        Modifier.fillMaxHeight().aspectRatio(pageAspect)
                      }
                    Box(
                      modifier =
                        pageBoxModifier
                          .clip(PenLabShapes.small)
                          .background(Color.White)
                          .border(PenLabLayout.hairline, PenLabComponentTokens.cardBorder, PenLabShapes.small),
                      contentAlignment = Alignment.Center,
                    ) {
                      if (preview != null && !preview.isRecycled) {
                        Image(
                          bitmap = preview.asImageBitmap(),
                          contentDescription = "Page ${currentPage + 1} preview",
                          contentScale = ContentScale.FillBounds,
                          modifier = Modifier.fillMaxSize(),
                        )
                        CropOverlay(
                          cropRect = cropRect,
                          enabled = !placing,
                          onCropChange = { cropRect = it },
                          primaryColor = scheme.primary,
                          onPrimaryColor = scheme.onPrimary,
                          pageKey = currentPage,
                          modifier = Modifier.fillMaxSize(),
                        )
                      } else if (previewError != null) {
                        Column(
                          modifier = Modifier.padding(PenLabSpacing.lg),
                          horizontalAlignment = Alignment.CenterHorizontally,
                          verticalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
                        ) {
                          Text(
                            text = previewError ?: "Could not render this page preview.",
                            color = scheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                          )
                          OutlinedButton(
                            onClick = {
                              previewError = null
                              previewRetryNonce++
                            },
                            modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
                          ) {
                            Text("Retry")
                          }
                        }
                      } else {
                        CircularProgressIndicator()
                      }
                    }
                  }
                }
              }
            }
          }
        }

        HorizontalDivider(color = scheme.outlineVariant, thickness = 1.dp)

        // ---- Footer ----
        @Suppress("UNUSED_EXPRESSION")
        previewTick
        val activePreviewReady = pageCount > 0 && session.cachedPreview(currentPage) != null
        FlowRow(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(horizontal = PenLabSpacing.xl, vertical = PenLabSpacing.md),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
        ) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(PenLabSpacing.xs),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.xs),
            ) {
              PenLabIconButton(
                icon = PenLabIcons.Back,
                contentDescription = "Previous page",
                enabled = !placing && currentPage > 0,
                onClick = { selectPage(currentPage - 1) },
              )
              if (pageCount > 0) {
                Text(
                  text = "Page ${currentPage + 1} of $pageCount",
                  color = scheme.onSurfaceVariant,
                  style = PenLabTextStyles.tabularLabel,
                )
              }
              PenLabIconButton(
                icon = PenLabIcons.ChevronRight,
                contentDescription = "Next page",
                enabled = !placing && currentPage < pageCount - 1,
                onClick = { selectPage(currentPage + 1) },
              )
            }
            OutlinedButton(
              onClick = { cropRect = NormalizedCropRect.FULL },
              enabled = !placing && pageCount > 0,
              modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
            ) {
              Text("Full page")
            }
            TextButton(
              onClick = { cropRect = NormalizedCropRect.DEFAULT },
              enabled = !placing && pageCount > 0,
              modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
            ) {
              Text("Reset")
            }
            if (darkPage) {
              PenLabChoiceChip(
                label = "Adapt to dark paper",
                selected = adaptToDarkPaper,
                onClick = { adaptToDarkPaper = !adaptToDarkPaper },
              )
            }
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
          ) {
            if (placeError != null) {
              Text(
                text = placeError!!,
                color = scheme.error,
                style = MaterialTheme.typography.bodySmall,
              )
            }
            TextButton(
              onClick = onDismiss,
              enabled = !placing,
              modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
            ) {
              Text("Cancel")
            }
            Button(
              onClick = {
                if (placing) return@Button
                placing = true
                placeError = null
                session.renderCroppedPng(
                  pageIndex = currentPage,
                  crop = cropRect,
                  imagesDir = imagesDir,
                  onSuccess = { fileName ->
                    placing = false
                    onPlaced(fileName, adaptToDarkPaper)
                  },
                  onError = { msg ->
                    placing = false
                    placeError = msg
                  },
                )
              },
              enabled = !placing && activePreviewReady,
              shape = PenLabShapes.pill,
              modifier = Modifier.heightIn(min = PenLabSpacing.touchTarget),
            ) {
              Text(if (placing) "Placing…" else "Place on canvas")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CropOverlay(
  cropRect: NormalizedCropRect,
  enabled: Boolean,
  onCropChange: (NormalizedCropRect) -> Unit,
  primaryColor: Color,
  onPrimaryColor: Color,
  pageKey: Int,
  modifier: Modifier = Modifier,
) {
  val currentCrop by rememberUpdatedState(cropRect)
  val currentOnChange by rememberUpdatedState(onCropChange)

  Canvas(
    modifier =
      modifier.pointerInput(pageKey, enabled) {
        if (!enabled) return@pointerInput
        var activeHandle: CropHandle? = null
        var dragStart = currentCrop
        var anchorX = 0f
        var anchorY = 0f
        var accumDx = 0f
        var accumDy = 0f

        detectDragGestures(
          onDragStart = { offset ->
            val w = size.width.toFloat().coerceAtLeast(1f)
            val h = size.height.toFloat().coerceAtLeast(1f)
            val nx = (offset.x / w).coerceIn(0f, 1f)
            val ny = (offset.y / h).coerceIn(0f, 1f)
            val hitPx = 28.dp.toPx()
            val hitRx = (hitPx / w).coerceIn(0.02f, 0.25f)
            val hitRy = (hitPx / h).coerceIn(0.02f, 0.25f)
            val handle = hitTestCropHandle(nx, ny, currentCrop, hitRx, hitRy)
            activeHandle = handle
            dragStart = currentCrop
            anchorX = nx
            anchorY = ny
            accumDx = 0f
            accumDy = 0f
            if (handle == CropHandle.NEW_DRAG) {
              currentOnChange(NormalizedCropRect.fromDrag(nx, ny, nx, ny))
            }
          },
          onDrag = { change, dragAmount ->
            val handle = activeHandle ?: return@detectDragGestures
            change.consume()
            val w = size.width.toFloat().coerceAtLeast(1f)
            val h = size.height.toFloat().coerceAtLeast(1f)
            accumDx += dragAmount.x / w
            accumDy += dragAmount.y / h
            val next =
              if (handle == CropHandle.NEW_DRAG) {
                NormalizedCropRect.fromDrag(anchorX, anchorY, anchorX + accumDx, anchorY + accumDy)
              } else {
                applyCropDrag(dragStart, handle, accumDx, accumDy)
              }
            currentOnChange(next)
          },
          onDragEnd = { activeHandle = null },
          onDragCancel = { activeHandle = null },
        )
      },
  ) {
    val w = size.width
    val h = size.height
    val cl = cropRect.left * w
    val ct = cropRect.top * h
    val cr = cropRect.right * w
    val cb = cropRect.bottom * h
    val cw = (cr - cl).coerceAtLeast(1f)
    val ch = (cb - ct).coerceAtLeast(1f)

    // Outer dark scrim outside the crop box
    val scrim = Color.Black.copy(alpha = 0.48f)
    if (ct > 0f) drawRect(scrim, Offset.Zero, Size(w, ct))
    if (cb < h) drawRect(scrim, Offset(0f, cb), Size(w, h - cb))
    if (cl > 0f) drawRect(scrim, Offset(0f, ct), Size(cl, ch))
    if (cr < w) drawRect(scrim, Offset(cr, ct), Size(w - cr, ch))

    // Interior thirds guide grid
    val gridColor = Color.White.copy(alpha = 0.35f)
    val gridStroke = 1.dp.toPx()
    for (i in 1..2) {
      val gx = cl + cw * (i / 3f)
      drawLine(gridColor, Offset(gx, ct), Offset(gx, cb), strokeWidth = gridStroke)
      val gy = ct + ch * (i / 3f)
      drawLine(gridColor, Offset(cl, gy), Offset(cr, gy), strokeWidth = gridStroke)
    }

    // Primary crop border
    val borderStroke = 2.dp.toPx()
    drawRect(primaryColor, Offset(cl, ct), Size(cw, ch), style = Stroke(width = borderStroke))

    // Translucent circular corner handles so page text underneath remains legible
    val cornerRadius = 8.dp.toPx()
    val cornerFill = primaryColor.copy(alpha = 0.36f)
    val pillFill = primaryColor.copy(alpha = 0.45f)
    val handleStroke = Stroke(width = 2.dp.toPx())
    val ringColor = onPrimaryColor.copy(alpha = 0.92f)
    for ((cx, cy) in listOf(cl to ct, cr to ct, cl to cb, cr to cb)) {
      drawCircle(cornerFill, radius = cornerRadius, center = Offset(cx, cy))
      drawCircle(ringColor, radius = cornerRadius, center = Offset(cx, cy), style = handleStroke)
    }

    // Mid-edge pills
    val pillLong = 22.dp.toPx()
    val pillShort = 5.dp.toPx()
    val pillCorner = CornerRadius(pillShort / 2f, pillShort / 2f)
    val edgeStroke = Stroke(width = 1.5.dp.toPx())
    val midX = (cl + cr) / 2f
    val midY = (ct + cb) / 2f
    for (topY in listOf(ct - pillShort / 2f, cb - pillShort / 2f)) {
      drawRoundRect(pillFill, Offset(midX - pillLong / 2f, topY), Size(pillLong, pillShort), pillCorner)
      drawRoundRect(ringColor, Offset(midX - pillLong / 2f, topY), Size(pillLong, pillShort), pillCorner, style = edgeStroke)
    }
    for (leftX in listOf(cl - pillShort / 2f, cr - pillShort / 2f)) {
      drawRoundRect(pillFill, Offset(leftX, midY - pillLong / 2f), Size(pillShort, pillLong), pillCorner)
      drawRoundRect(ringColor, Offset(leftX, midY - pillLong / 2f), Size(pillShort, pillLong), pillCorner, style = edgeStroke)
    }
  }
}

/**
 * Owns the single background thread, temp PDF file, and [PdfRenderer] for one [PdfCropDialog].
 * Every [PdfRenderer.openPage] call is strictly serialized on [executor].
 */
private class PdfCropSession(
  private val context: Context,
  private val uri: Uri,
  private val mainHandler: Handler,
) {
  private val executor =
    Executors.newSingleThreadExecutor { r -> Thread(r, "penlab-pdf-crop").apply { isDaemon = true } }
  private val closed = AtomicBoolean(false)

  private var tempFile: File? = null
  private var descriptor: ParcelFileDescriptor? = null
  private var renderer: PdfRenderer? = null

  private val thumbCache =
    object : LruCache<Int, Bitmap>(THUMB_CACHE_BYTES) {
      override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }
  private val previewCache =
    object : LruCache<Int, Bitmap>(PREVIEW_CACHE_BYTES) {
      override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }
  private val pendingThumbs = HashSet<Int>()

  fun cachedThumb(page: Int): Bitmap? = thumbCache.get(page)

  fun cachedPreview(page: Int): Bitmap? = previewCache.get(page)

  fun open(onReady: (title: String, pageCount: Int) -> Unit, onError: (String) -> Unit) {
    executor.execute {
      if (closed.get()) return@execute
      try {
        val name = resolvePdfTitle(context, uri)
        val tmp = File(context.cacheDir, "pdf-crop-${UUID.randomUUID()}.pdf")
        tempFile = tmp
        val stream = context.contentResolver.openInputStream(uri)
          ?: throw IllegalStateException("Cannot read PDF")
        stream.use { input ->
          tmp.outputStream().use { out -> input.copyTo(out) }
        }
        val fd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
        descriptor = fd
        val pdf = PdfRenderer(fd)
        renderer = pdf
        val count = pdf.pageCount
        if (count <= 0) throw IllegalStateException("PDF has no pages")
        mainHandler.post { if (!closed.get()) onReady(name, count) }
      } catch (e: SecurityException) {
        mainHandler.post { if (!closed.get()) onError("Permission denied to read this PDF.") }
      } catch (_: Throwable) {
        mainHandler.post { if (!closed.get()) onError("Could not open this PDF.") }
      }
    }
  }

  fun requestThumb(pageIndex: Int, onRendered: () -> Unit) {
    if (closed.get()) return
    executor.execute {
      if (closed.get()) return@execute
      if (thumbCache.get(pageIndex) != null || !pendingThumbs.add(pageIndex)) return@execute
      try {
        val pdf = renderer ?: return@execute
        if (pageIndex !in 0 until pdf.pageCount) return@execute
        val bmp =
          pdf.openPage(pageIndex).use { page ->
            val h = (THUMB_WIDTH_PX.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
            Bitmap.createBitmap(THUMB_WIDTH_PX, h, Bitmap.Config.ARGB_8888).apply {
              eraseColor(AndroidColor.WHITE)
              page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
              prepareToDraw()
            }
          }
        thumbCache.put(pageIndex, bmp)
        mainHandler.post { if (!closed.get()) onRendered() }
      } catch (_: Throwable) {
      } finally {
        pendingThumbs.remove(pageIndex)
      }
    }
  }

  fun requestPreview(pageIndex: Int, onRendered: () -> Unit, onError: (String) -> Unit) {
    if (closed.get()) return
    executor.execute {
      if (closed.get()) return@execute
      if (previewCache.get(pageIndex) != null) {
        mainHandler.post { if (!closed.get()) onRendered() }
        return@execute
      }
      try {
        val pdf = renderer ?: throw IllegalStateException("PDF closed")
        if (pageIndex !in 0 until pdf.pageCount) throw IndexOutOfBoundsException("Invalid page")
        val bmp =
          pdf.openPage(pageIndex).use { page ->
            val h = (PREVIEW_WIDTH_PX.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
            Bitmap.createBitmap(PREVIEW_WIDTH_PX, h, Bitmap.Config.ARGB_8888).apply {
              eraseColor(AndroidColor.WHITE)
              page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
              prepareToDraw()
            }
          }
        previewCache.put(pageIndex, bmp)
        mainHandler.post { if (!closed.get()) onRendered() }
      } catch (_: Throwable) {
        mainHandler.post { if (!closed.get()) onError("Could not render this page preview.") }
      }
    }
  }

  fun renderCroppedPng(
    pageIndex: Int,
    crop: NormalizedCropRect,
    imagesDir: File,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    if (closed.get()) return
    executor.execute {
      if (closed.get()) return@execute
      try {
        val pdf = renderer ?: throw IllegalStateException("PDF closed")
        val fileName =
          pdf.openPage(pageIndex).use { page ->
            val bounds = computeHighResCropBounds(page.width, page.height, crop)
            val bitmap = Bitmap.createBitmap(bounds.cropWidth, bounds.cropHeight, Bitmap.Config.ARGB_8888)
            try {
              bitmap.eraseColor(AndroidColor.WHITE)
              val scale = bounds.renderWidth.toFloat() / page.width.coerceAtLeast(1).toFloat()
              val matrix =
                AndroidMatrix().apply {
                  setScale(scale, scale)
                  postTranslate(-bounds.cropLeft.toFloat(), -bounds.cropTop.toFloat())
                }
              page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
              imagesDir.mkdirs()
              val outFile = File(imagesDir, "pdf-crop-${UUID.randomUUID()}.png")
              outFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
              outFile.name
            } finally {
              bitmap.recycle()
            }
          }
        mainHandler.post { if (!closed.get()) onSuccess(fileName) }
      } catch (_: Throwable) {
        mainHandler.post { if (!closed.get()) onError("Could not crop this PDF page.") }
      }
    }
  }

  fun close() {
    if (!closed.compareAndSet(false, true)) return
    executor.execute {
      thumbCache.evictAll()
      previewCache.evictAll()
      pendingThumbs.clear()
      runCatching { renderer?.close() }
      runCatching { descriptor?.close() }
      runCatching { tempFile?.delete() }
      renderer = null
      descriptor = null
      tempFile = null
    }
    executor.shutdown()
  }
}

private fun resolvePdfTitle(context: Context, uri: Uri): String {
  try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) {
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0) c.getString(idx)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
      }
    }
  } catch (_: Exception) {
  }
  return "Add from PDF"
}
