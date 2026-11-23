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

  // ---- the scheduler ----------------------------------------------------------------------

  /** A fake clock and timer queue, and a render that finishes only when the test says so. */
  private class Rig(debounce: Long = 100L) {
    var time = 0L
    private val timers = ArrayList<Pair<Long, () -> Unit>>() // fire time, action
    var renders = 0
    private var finish: (() -> Unit)? = null
    val scheduler = RenderScheduler(debounce, { time }, { delay, action -> timers += (time + delay) to action }, { done -> renders++; finish = done })

    /** Moves time forward, firing due timers in order. */
    fun advance(ms: Long) {
      val end = time + ms
      while (true) {
        val next = timers.filter { it.first <= end }.minByOrNull { it.first } ?: break
        timers.remove(next)
        time = maxOf(time, next.first)
        next.second()
      }
      time = end
    }

    fun finishRender() {
      val f = finish ?: error("no render running")
      finish = null
      f()
    }

    val renderRunning get() = finish != null
  }

  @Test
  fun rendersOnceAfterTheDebounce() {
    val rig = Rig()
    rig.scheduler.markDirty()
    rig.advance(99)
    assertEquals(0, rig.renders)
    rig.advance(1)
    assertEquals(1, rig.renders)
  }

  @Test
  fun aBurstOfChangesRendersOnceAfterTheLastOne() {
    val rig = Rig()
    repeat(10) {
      rig.scheduler.markDirty()
      rig.advance(30) // faster than the debounce
    }
    assertEquals(0, rig.renders)
    rig.advance(70)
    assertEquals(1, rig.renders)
  }

  @Test
  fun changesDuringARenderAreCoalescedIntoOneMore() {
    val rig = Rig()
    rig.scheduler.markDirty()
    rig.advance(100)
    assertEquals(1, rig.renders)
    repeat(5) { rig.scheduler.markDirty(); rig.advance(10) }
    rig.advance(500)
    assertEquals("only one render in flight", 1, rig.renders)
    rig.finishRender()
    rig.advance(100)
    assertEquals(2, rig.renders)
    rig.finishRender()
    rig.advance(1000)
    assertEquals("nothing more is pending", 2, rig.renders)
  }

  @Test
  fun nothingRendersWithoutChanges() {
    val rig = Rig()
    rig.advance(1000)
    assertEquals(0, rig.renders)
  }

  @Test
  fun cancelDropsPendingWorkAndALateRenderResult() {
    val rig = Rig()
    rig.scheduler.markDirty()
    rig.scheduler.cancel()
    rig.advance(1000)
    assertEquals(0, rig.renders)

    rig.scheduler.markDirty()
    rig.advance(100)
    assertEquals(1, rig.renders)
    rig.scheduler.cancel()
    rig.finishRender() // finishing after cancel must not confuse the next run
    rig.scheduler.markDirty()
    rig.advance(100)
    assertEquals(2, rig.renders)
    assertTrue(rig.scheduler.isRendering)
  }
}
