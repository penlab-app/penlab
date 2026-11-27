package dev.penlab.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfCropGeometryTest {
  @Test
  fun fromDragHandlesAllFourDirectionsAndClampsToUnitSquare() {
    val tlToBr = NormalizedCropRect.fromDrag(0.1f, 0.2f, 0.7f, 0.8f)
    val brToTl = NormalizedCropRect.fromDrag(0.7f, 0.8f, 0.1f, 0.2f)
    val trToBl = NormalizedCropRect.fromDrag(0.7f, 0.2f, 0.1f, 0.8f)
    val blToTr = NormalizedCropRect.fromDrag(0.1f, 0.8f, 0.7f, 0.2f)

    assertEquals(NormalizedCropRect(0.1f, 0.2f, 0.7f, 0.8f), tlToBr)
    assertEquals(tlToBr, brToTl)
    assertEquals(tlToBr, trToBl)
    assertEquals(tlToBr, blToTr)

    val clamped = NormalizedCropRect.fromDrag(-0.4f, -0.2f, 1.5f, 1.3f)
    assertEquals(NormalizedCropRect.FULL, clamped)
  }

  @Test
  fun fromDragEnforcesMinimumSpanForTinyOrZeroDrags() {
    val zeroCenter = NormalizedCropRect.fromDrag(0.4f, 0.5f, 0.4f, 0.5f)
    assertEquals(NormalizedCropRect.MIN_SPAN, zeroCenter.width, 1e-5f)
    assertEquals(NormalizedCropRect.MIN_SPAN, zeroCenter.height, 1e-5f)
    assertEquals(0.4f, zeroCenter.left, 1e-5f)
    assertEquals(0.5f, zeroCenter.top, 1e-5f)

    val nearFarCorner = NormalizedCropRect.fromDrag(0.99f, 0.98f, 1.2f, 1.0f)
    assertEquals(1f, nearFarCorner.right, 1e-5f)
    assertEquals(1f, nearFarCorner.bottom, 1e-5f)
    assertTrue(nearFarCorner.left >= 0f && nearFarCorner.top >= 0f)
    assertEquals(NormalizedCropRect.MIN_SPAN, nearFarCorner.width, 1e-5f)
    assertEquals(NormalizedCropRect.MIN_SPAN, nearFarCorner.height, 1e-5f)
  }

  @Test
  fun hitTestDetectsCornersEdgesInteriorAndOutsideNewDrag() {
    val rect = NormalizedCropRect(0.2f, 0.2f, 0.8f, 0.6f)
    val rx = 0.04f
    val ry = 0.04f

    assertEquals(CropHandle.TOP_LEFT, hitTestCropHandle(0.21f, 0.19f, rect, rx, ry))
    assertEquals(CropHandle.TOP_RIGHT, hitTestCropHandle(0.79f, 0.21f, rect, rx, ry))
    assertEquals(CropHandle.BOTTOM_LEFT, hitTestCropHandle(0.20f, 0.60f, rect, rx, ry))
    assertEquals(CropHandle.BOTTOM_RIGHT, hitTestCropHandle(0.82f, 0.61f, rect, rx, ry))

    assertEquals(CropHandle.TOP, hitTestCropHandle(0.50f, 0.22f, rect, rx, ry))
    assertEquals(CropHandle.BOTTOM, hitTestCropHandle(0.50f, 0.58f, rect, rx, ry))
    assertEquals(CropHandle.LEFT, hitTestCropHandle(0.18f, 0.40f, rect, rx, ry))
    assertEquals(CropHandle.RIGHT, hitTestCropHandle(0.81f, 0.40f, rect, rx, ry))

    assertEquals(CropHandle.MOVE, hitTestCropHandle(0.50f, 0.40f, rect, rx, ry))
    assertEquals(CropHandle.NEW_DRAG, hitTestCropHandle(0.05f, 0.05f, rect, rx, ry))
    assertEquals(CropHandle.NEW_DRAG, hitTestCropHandle(0.90f, 0.90f, rect, rx, ry))
  }

  @Test
  fun applyCropDragMovePreservesSizeAndStaysInsideBounds() {
    val start = NormalizedCropRect(0.2f, 0.3f, 0.6f, 0.7f)
    val moved = applyCropDrag(start, CropHandle.MOVE, 0.1f, -0.1f)
    assertEquals(0.3f, moved.left, 1e-5f)
    assertEquals(0.2f, moved.top, 1e-5f)
    assertEquals(start.width, moved.width, 1e-5f)
    assertEquals(start.height, moved.height, 1e-5f)

    val clampedMax = applyCropDrag(start, CropHandle.MOVE, 2f, 2f)
    assertEquals(1f, clampedMax.right, 1e-5f)
    assertEquals(1f, clampedMax.bottom, 1e-5f)
    assertEquals(start.width, clampedMax.width, 1e-5f)
    assertEquals(start.height, clampedMax.height, 1e-5f)

    val clampedMin = applyCropDrag(start, CropHandle.MOVE, -2f, -2f)
    assertEquals(0f, clampedMin.left, 1e-5f)
    assertEquals(0f, clampedMin.top, 1e-5f)
    assertEquals(start.width, clampedMin.width, 1e-5f)
    assertEquals(start.height, clampedMin.height, 1e-5f)
  }

  @Test
  fun applyCropDragCornerAndEdgeHandlesRespectMinimumSpan() {
    val start = NormalizedCropRect(0.2f, 0.2f, 0.8f, 0.8f)

    val collapsedLeft = applyCropDrag(start, CropHandle.LEFT, 0.9f, 0f)
    assertEquals(start.right, collapsedLeft.right, 1e-5f)
    assertEquals(NormalizedCropRect.MIN_SPAN, collapsedLeft.width, 1e-5f)

    val collapsedRight = applyCropDrag(start, CropHandle.RIGHT, -0.9f, 0f)
    assertEquals(start.left, collapsedRight.left, 1e-5f)
    assertEquals(NormalizedCropRect.MIN_SPAN, collapsedRight.width, 1e-5f)

    val collapsedTopLeft = applyCropDrag(start, CropHandle.TOP_LEFT, 0.9f, 0.9f)
    assertEquals(NormalizedCropRect.MIN_SPAN, collapsedTopLeft.width, 1e-5f)
    assertEquals(NormalizedCropRect.MIN_SPAN, collapsedTopLeft.height, 1e-5f)
    assertEquals(start.right, collapsedTopLeft.right, 1e-5f)
    assertEquals(start.bottom, collapsedTopLeft.bottom, 1e-5f)

    val clampedBottomRight = applyCropDrag(start, CropHandle.BOTTOM_RIGHT, 0.5f, 0.5f)
    assertEquals(1f, clampedBottomRight.right, 1e-5f)
    assertEquals(1f, clampedBottomRight.bottom, 1e-5f)
    assertEquals(start.left, clampedBottomRight.left, 1e-5f)
    assertEquals(start.top, clampedBottomRight.top, 1e-5f)
  }

  @Test
  fun computeHighResCropBoundsPreservesAspectAndCapsMemory() {
    // A4 page (595 x 842 pt), full-page crop: targets 2000px long side
    val full = computeHighResCropBounds(595, 842, NormalizedCropRect.FULL)
    assertEquals(2000, maxOf(full.cropWidth, full.cropHeight))
    assertEquals(full.renderWidth, full.cropWidth)
    assertEquals(full.renderHeight, full.cropHeight)
    val expectedAspect = 595f / 842f
    val actualAspect = full.cropWidth.toFloat() / full.cropHeight.toFloat()
    assertEquals(expectedAspect, actualAspect, 0.01f)

    // Small crop (10% x 10%): capped so full render doesn't exceed maxRenderDimPx (3600)
    val small = computeHighResCropBounds(595, 842, NormalizedCropRect(0.1f, 0.2f, 0.2f, 0.3f))
    assertTrue(small.renderWidth <= 3600 && small.renderHeight <= 3600)
    assertEquals(3600, maxOf(small.renderWidth, small.renderHeight))
    assertTrue(small.cropLeft in 0 until small.renderWidth)
    assertTrue(small.cropTop in 0 until small.renderHeight)
    assertTrue(small.cropWidth > 0 && small.cropHeight > 0)
    assertTrue(small.cropLeft + small.cropWidth <= small.renderWidth)
    assertTrue(small.cropTop + small.cropHeight <= small.renderHeight)
  }

  @Test
  fun defaultCropRectAndDarkPaperAdaptDefaults() {
    val def = NormalizedCropRect.DEFAULT
    assertTrue(def.left in 0.05f..0.15f)
    assertTrue(def.top >= 0.20f)
    assertTrue(def.right >= 0.85f && def.bottom > def.top)
    assertTrue(defaultDarkPaperCropAdapt(darkPage = true))
    assertFalse(defaultDarkPaperCropAdapt(darkPage = false))
  }
}
