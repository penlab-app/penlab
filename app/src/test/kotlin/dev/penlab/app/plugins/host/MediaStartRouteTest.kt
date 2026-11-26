package dev.penlab.app.plugins.host

import dev.penlab.sdk.PageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Record lecture": the plugin's start activity when it declares one, else the binder. */
class MediaStartRouteTest {
  private val page = PageInfo("nb", 0, 210f, 297f, false)

  @Test
  fun aPluginWithAStartActivityIsOpenedInFront() {
    val route = MediaStartRoute.choose("a.b.StartRecordingActivity", page)
    assertEquals("a.b.StartRecordingActivity", (route as MediaStartRoute.Activity).className)
  }

  @Test
  fun aPluginWithoutOneStartsOverTheBinderEvenWithNoNotebook() {
    assertSame(MediaStartRoute.Binder, MediaStartRoute.choose(null, page))
    assertSame(MediaStartRoute.Binder, MediaStartRoute.choose(null, null))
  }

  @Test
  fun aStartActivityNeedsAnOpenNotebook() {
    assertSame(MediaStartRoute.NoNotebook, MediaStartRoute.choose("a.b.Start", null))
    assertTrue(MediaStartRoute.choose("a.b.Start", page) is MediaStartRoute.Activity)
  }
}
