package dev.penlab.app.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingPdfTest {
  private fun pick(action: String?, type: String? = "application/pdf", data: String? = "content://d", stream: String? = "content://s", handled: Boolean = false, history: Boolean = false) =
    IncomingPdf.pick(action, type, data, stream, handled, history)

  @Test
  fun viewUsesTheData() = assertEquals("content://d", pick(IncomingPdf.ACTION_VIEW))

  @Test
  fun sendUsesTheStream() = assertEquals("content://s", pick(IncomingPdf.ACTION_SEND))

  @Test
  fun mimeTypeIsMatchedIgnoringCase() = assertEquals("content://d", pick(IncomingPdf.ACTION_VIEW, type = "Application/PDF"))

  @Test
  fun otherTypesAreRefused() {
    assertNull(pick(IncomingPdf.ACTION_VIEW, type = "image/png"))
    assertNull(pick(IncomingPdf.ACTION_SEND, type = null))
  }

  @Test
  fun otherActionsAreRefused() {
    assertNull(pick("android.intent.action.MAIN"))
    assertNull(pick(null))
  }

  @Test
  fun missingFileGivesNull() {
    assertNull(pick(IncomingPdf.ACTION_VIEW, data = null))
    assertNull(pick(IncomingPdf.ACTION_SEND, stream = null))
  }

  @Test
  fun anAlreadyHandledIntentIsNeverImportedTwice() = assertNull(pick(IncomingPdf.ACTION_VIEW, handled = true))

  @Test
  fun reopeningFromRecentsImportsNothing() = assertNull(pick(IncomingPdf.ACTION_VIEW, history = true))

  @Test
  fun fileNamesKeepOrGetAPdfExtension() {
    assertEquals("Lecture 1.pdf", IncomingPdf.fileName("Lecture 1.pdf"))
    assertEquals("Notes.PDF", IncomingPdf.fileName("Notes.PDF"))
    assertEquals("scan.pdf", IncomingPdf.fileName("scan"))
    assertEquals("Imported.pdf", IncomingPdf.fileName(null))
    assertEquals("Imported.pdf", IncomingPdf.fileName("  "))
  }
}
