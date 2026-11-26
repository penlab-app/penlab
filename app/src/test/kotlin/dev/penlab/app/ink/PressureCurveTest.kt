package dev.penlab.app.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PressureCurveTest {
  @Test
  fun ballpointHasNoPressureCurve() {
    for (sensitivity in PressureSensitivity.entries) {
      assertNull(pressureCurveFor(PenStyle.BALLPOINT, sensitivity))
    }
  }

  @Test
  fun pressureAndFountainCurvesExistForAllPresets() {
    for (style in listOf(PenStyle.PRESSURE, PenStyle.FOUNTAIN)) {
      for (sensitivity in PressureSensitivity.entries) {
        assertNotNull("$style / $sensitivity", pressureCurveFor(style, sensitivity))
      }
    }
  }

  @Test
  fun pressureCurveDynamicRangeAndBoundsSafety() {
    for (sensitivity in PressureSensitivity.entries) {
      val pressure = pressureCurveFor(PenStyle.PRESSURE, sensitivity)!!
      assertTrue("$sensitivity minMultiplier", pressure.minMultiplier <= 0.40f)
      assertTrue("$sensitivity maxMultiplier min", pressure.maxMultiplier >= 1.55f)
      assertTrue("$sensitivity maxMultiplier max", pressure.maxMultiplier <= 1.90f)
      assertTrue("$sensitivity sourceStart > 0", pressure.sourceStart > 0f)
      assertTrue("$sensitivity sourceStart < sourceEnd", pressure.sourceStart < pressure.sourceEnd)
      assertTrue("$sensitivity sourceEnd < 1", pressure.sourceEnd < 1f)

      val fountain = pressureCurveFor(PenStyle.FOUNTAIN, sensitivity)!!
      assertTrue("fountain $sensitivity maxMultiplier max", fountain.maxMultiplier <= 1.90f)
      assertTrue("fountain $sensitivity sourceStart > 0", fountain.sourceStart > 0f)
      assertTrue("fountain $sensitivity sourceStart < sourceEnd", fountain.sourceStart < fountain.sourceEnd)
      assertTrue("fountain $sensitivity sourceEnd < 1", fountain.sourceEnd < 1f)
    }
  }

  @Test
  fun evaluateMultiplierIsMonotonicAndClamped() {
    for (style in listOf(PenStyle.PRESSURE, PenStyle.FOUNTAIN)) {
      for (sensitivity in PressureSensitivity.entries) {
        val spec = pressureCurveFor(style, sensitivity)!!
        assertEquals(spec.minMultiplier, spec.evaluateMultiplier(0f), 1e-5f)
        assertEquals(spec.maxMultiplier, spec.evaluateMultiplier(1f), 1e-5f)
        assertEquals(1f, spec.evaluateMultiplier(-1f), 1e-5f)
        assertEquals(1f, spec.evaluateMultiplier(Float.NaN), 1e-5f)

        val steps = 10
        var prev = spec.evaluateMultiplier(spec.sourceStart)
        for (i in 1..steps) {
          val p = spec.sourceStart + (spec.sourceEnd - spec.sourceStart) * (i.toFloat() / steps)
          val curr = spec.evaluateMultiplier(p)
          assertTrue("$style $sensitivity step $i: $curr > $prev", curr > prev)
          prev = curr
        }
      }
    }
  }

  @Test
  fun sensitivityPresetsOrderResponsivenessAcrossTypicalSPenPressures() {
    for (style in listOf(PenStyle.PRESSURE, PenStyle.FOUNTAIN)) {
      val light = pressureCurveFor(style, PressureSensitivity.LIGHT)!!
      val balanced = pressureCurveFor(style, PressureSensitivity.BALANCED)!!
      val firm = pressureCurveFor(style, PressureSensitivity.FIRM)!!
      for (p in listOf(0.25f, 0.40f)) {
        val ml = light.evaluateMultiplier(p)
        val mb = balanced.evaluateMultiplier(p)
        val mf = firm.evaluateMultiplier(p)
        assertTrue("$style at $p: light($ml) > balanced($mb)", ml > mb)
        assertTrue("$style at $p: balanced($mb) > firm($mf)", mb > mf)
      }
    }
  }

  @Test
  fun balancedPressureHitsNominalWidthNearNormalWritingForce() {
    val balanced = pressureCurveFor(PenStyle.PRESSURE, PressureSensitivity.BALANCED)!!
    val atNominal = balanced.evaluateMultiplier(0.34f)
    assertTrue("expected ~1.0 at 0.34f, got $atNominal", atNominal in 0.95f..1.05f)
  }
}
