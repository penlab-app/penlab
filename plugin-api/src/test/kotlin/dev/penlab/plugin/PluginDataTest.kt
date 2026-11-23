package dev.penlab.plugin

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginDataTest {
  @Test fun theApiVersionIsOne() = assertEquals(1, API_VERSION)

  @Test fun emptySelectionIsEmptyAndHasNoPicture() {
    val s = Selection.empty()
    assertTrue(s.isEmpty)
    assertEquals(null, s.bitmap)
  }

  @Test fun aSelectionWithAnItemIsNotEmpty() {
    val s = Selection(emptyList(), listOf(PluginItem("a", RectF(), ByteArray(0))), RectF(), null)
    assertFalse(s.isEmpty)
  }

  @Test fun aStrokeReportsItsPointCount() {
    val s = PluginStroke(floatArrayOf(0f, 1f), floatArrayOf(0f, 1f), floatArrayOf(0.5f, -1f), longArrayOf(0, 8), Tool.PEN, 0xFF000000.toInt(), 0.5f)
    assertEquals(2, s.pointCount)
  }

  @Test fun anItemNeedsASingleLineTypeId() {
    for (bad in listOf("", "a\nb")) {
      var refused = false
      try { PluginItem(bad, RectF(), ByteArray(0)) } catch (e: IllegalArgumentException) { refused = true }
      assertTrue(refused)
    }
    val payload = byteArrayOf(1, 2)
    assertSame(payload, PluginItem("ok", RectF(), payload).payload)
  }

  @Test fun resultKindsCarryTheirData() {
    assertEquals("t", (Result.Text("t") as Result.Text).text)
    assertEquals("m", Result.Message("m").message)
    assertTrue(Result.Items(emptyList()).items.isEmpty())
    assertTrue(Result.None === Result.None)
  }

  @Test fun pageActionDefaultsAreAlwaysEnabledWithTheHostIcon() {
    val action = object : PageAction {
      override val id = "a"
      override val label = "A"
      override suspend fun run(selection: Selection, page: PageRef) = Result.None
    }
    assertEquals(EnabledWhen.ALWAYS, action.enabledWhen)
    assertEquals(0, action.iconRes)
  }
}
