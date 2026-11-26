package dev.penlab.app.projector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectorLogicTest {
  // ---- fitToAspect ------------------------------------------------------------------------

  private val tablet = Box(0f, 0f, 1600f, 1000f) // 1.6

  @Test
  fun widerProjectorExtendsSidewaysAndKeepsTheHeight() {
    val fitted = fitToAspect(tablet, 16f / 9f)
    assertEquals(1000f, fitted.height, 0.01f)
    assertEquals(1000f * 16f / 9f, fitted.width, 0.01f)
    assertEquals(tablet.centerX, fitted.centerX, 0.01f)
  }

  @Test
  fun narrowerProjectorExtendsUpAndDown() {
    val fitted = fitToAspect(tablet, 1f)
    assertEquals(1600f, fitted.width, 0.01f)
    assertEquals(1600f, fitted.height, 0.01f)
    assertEquals(tablet.centerY, fitted.centerY, 0.01f)
  }

  @Test
  fun neverCropsTheTabletsView() {
    for (aspect in listOf(0.5f, 1f, 1.6f, 16f / 9f, 21f / 9f, 4f)) {
      val fitted = fitToAspect(tablet, aspect)
      assertTrue(fitted.left <= tablet.left && fitted.right >= tablet.right)
      assertTrue(fitted.top <= tablet.top && fitted.bottom >= tablet.bottom)
      assertEquals(aspect, fitted.width / fitted.height, 0.001f)
    }
  }

  @Test
  fun sameShapeChangesNothing() {
    val fitted = fitToAspect(tablet, 1.6f)
    assertEquals(tablet.width, fitted.width, 0.01f)
    assertEquals(tablet.height, fitted.height, 0.01f)
  }

  @Test
  fun offsetViewKeepsItsCentre() {
    val view = Box(200f, 0f, 1500f, 1000f) // with a toolbar on the left
    val fitted = fitToAspect(view, 2f)
    assertEquals(view.centerX, fitted.centerX, 0.01f)
    assertEquals(2000f, fitted.width, 0.01f)
  }

  @Test
  fun degenerateInputsAreReturnedAsTheyAre() {
    assertEquals(Box(0f, 0f, 0f, 10f), fitToAspect(Box(0f, 0f, 0f, 10f), 1.6f))
    assertEquals(tablet, fitToAspect(tablet, 0f))
    assertEquals(tablet, fitToAspect(tablet, Float.NaN))
  }

  // ---- the offer --------------------------------------------------------------------------

  @Test
  fun offerNeedsDisplayAndNotebook() {
    assertTrue(shouldOfferProjector(hasDisplay = true, notebookOpen = true, active = false, alreadyOffered = false))
    assertFalse(shouldOfferProjector(false, true, false, false))
    assertFalse(shouldOfferProjector(true, false, false, false))
  }

  @Test
  fun offerIsMadeOnlyOncePerConnectionAndNotWhileOn() {
    assertFalse(shouldOfferProjector(true, true, false, alreadyOffered = true))
    assertFalse(shouldOfferProjector(true, true, active = true, alreadyOffered = false))
  }

  // ---- static cache tracker ---------------------------------------------------------------

  @Test
  fun maxCacheDimensionOkAcceptsReasonableBoundsAndRejectsDegenerateOrHuge() {
    assertTrue(maxCacheDimensionOk(1, 1))
    assertTrue(maxCacheDimensionOk(1920, 1080))
    assertTrue(maxCacheDimensionOk(4096, 4096))
    assertFalse(maxCacheDimensionOk(0, 1080))
    assertFalse(maxCacheDimensionOk(1920, 0))
    assertFalse(maxCacheDimensionOk(-10, 1080))
    assertFalse(maxCacheDimensionOk(4097, 1080))
    assertFalse(maxCacheDimensionOk(1920, 4097))
  }

  @Test
  fun staticCacheTrackerTransitionsThroughDirectRebuildAndUseCache() {
    val tracker = StaticCacheTracker()
    val m1 = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    // Null or invalid dimensions always draw direct.
    assertEquals(ProjectorDrawMode.DIRECT, tracker.decide(1920, 1080, 1L, null, hasLiveOverlay = true))
    assertEquals(ProjectorDrawMode.DIRECT, tracker.decide(0, 1080, 1L, m1, hasLiveOverlay = true))
    assertEquals(ProjectorDrawMode.DIRECT, tracker.decide(1920, 0, 1L, m1, hasLiveOverlay = true))

    // Without a live overlay and no valid cache, pan/idle frames draw direct.
    assertEquals(ProjectorDrawMode.DIRECT, tracker.decide(1920, 1080, 1L, m1, hasLiveOverlay = false))

    // Once a live stroke starts with no valid cache, rebuild once.
    assertEquals(ProjectorDrawMode.REBUILD_CACHE, tracker.decide(1920, 1080, 1L, m1, hasLiveOverlay = true))
    tracker.markBuilt(1920, 1080, 1L, m1)

    // Subsequent frames with identical state hit the cache (both live and right after pen-up).
    assertEquals(ProjectorDrawMode.USE_CACHE, tracker.decide(1920, 1080, 1L, m1.copyOf(), hasLiveOverlay = true))
    assertEquals(ProjectorDrawMode.USE_CACHE, tracker.decide(1920, 1080, 1L, m1.copyOf(), hasLiveOverlay = false))

    // Caller mutating the passed matrix after markBuilt must not corrupt the cached snapshot matrix.
    val aliased = floatArrayOf(2f, 0f, 10f, 0f, 2f, 20f, 0f, 0f, 1f)
    tracker.markBuilt(1920, 1080, 2L, aliased)
    aliased[2] = 999f
    assertEquals(ProjectorDrawMode.REBUILD_CACHE, tracker.decide(1920, 1080, 2L, aliased, hasLiveOverlay = true))
  }

  @Test
  fun staticCacheTrackerInvalidatesOnSceneMatrixSizeAndExplicitInvalidate() {
    val tracker = StaticCacheTracker()
    val m1 = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    val m2 = floatArrayOf(1f, 0f, 5f, 0f, 1f, 0f, 0f, 0f, 1f)

    tracker.markBuilt(1920, 1080, 10L, m1)
    assertEquals(ProjectorDrawMode.USE_CACHE, tracker.decide(1920, 1080, 10L, m1, hasLiveOverlay = true))

    // Scene version bump forces rebuild when live, or direct+invalidate when idle.
    assertEquals(ProjectorDrawMode.REBUILD_CACHE, tracker.decide(1920, 1080, 11L, m1, hasLiveOverlay = true))
    assertEquals(ProjectorDrawMode.USE_CACHE, tracker.decide(1920, 1080, 10L, m1, hasLiveOverlay = true))

    // Pan/zoom (matrix change) with no live stroke invalidates and returns DIRECT so pan frames don't allocate.
    assertEquals(ProjectorDrawMode.DIRECT, tracker.decide(1920, 1080, 10L, m2, hasLiveOverlay = false))
    // Because pan invalidated the old cache, even returning to m1 now requires rebuilding when live.
    assertEquals(ProjectorDrawMode.REBUILD_CACHE, tracker.decide(1920, 1080, 10L, m1, hasLiveOverlay = true))

    // Size change invalidates.
    tracker.markBuilt(1920, 1080, 10L, m1)
    assertEquals(ProjectorDrawMode.REBUILD_CACHE, tracker.decide(1280, 720, 10L, m1, hasLiveOverlay = true))

    // Explicit invalidate forces rebuild.
    tracker.markBuilt(1920, 1080, 10L, m1)
    tracker.invalidate()
    assertEquals(ProjectorDrawMode.REBUILD_CACHE, tracker.decide(1920, 1080, 10L, m1, hasLiveOverlay = true))
  }
}
