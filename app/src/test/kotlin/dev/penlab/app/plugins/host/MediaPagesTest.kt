package dev.penlab.app.plugins.host

import dev.penlab.sdk.PageInfo
import dev.penlab.sdk.PenLabBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which notebook a recording control names, so the plugin files recordings under the right one. */
class MediaPagesTest {
  private val open = PageInfo("nb-open", 2, 210f, 297f, darkPage = false)

  @Test
  fun startNamesTheOpenNotebook() {
    assertEquals("nb-open", MediaPages.forCommand(PenLabBridge.MEDIA_START, open, null)!!.notebookId)
    assertEquals("nb-open", MediaPages.forCommand(PenLabBridge.MEDIA_START, open, "another")!!.notebookId)
  }

  @Test
  fun otherControlsNameTheLiveRecordingsNotebookWhenItIsOpen() {
    for (command in listOf(PenLabBridge.MEDIA_PAUSE, PenLabBridge.MEDIA_RESUME, PenLabBridge.MEDIA_STOP)) {
      assertEquals("nb-open", MediaPages.forCommand(command, open, "nb-open")!!.notebookId)
      assertNull(MediaPages.forCommand(command, open, "another")) // never claim the open notebook for another notebook's recording
      assertNull(MediaPages.forCommand(command, open, null))
    }
  }

  @Test
  fun noNotebookOpenMeansNoPage() {
    assertNull(MediaPages.forCommand(PenLabBridge.MEDIA_START, null, null))
    assertNull(MediaPages.forCommand(PenLabBridge.MEDIA_STOP, null, "nb"))
  }
}
