package dev.penlab.app.plugins.host

import dev.penlab.sdk.PageInfo
import dev.penlab.sdk.PenLabBridge

/** Which notebook a `media:command` request names (the `page` of the request, as page actions have). */
object MediaPages {
  /**
   * The `page` to send with [command]. `start` always names the notebook open now ([open]): that is where the recording is filed.
   * The other controls act on the live recording, so they name its notebook ([sessionNotebookId]) and carry a page only when that is
   * the notebook open now (a page needs the open notebook's size); from another notebook, or with nothing live, they carry none.
   */
  fun forCommand(command: String, open: PageInfo?, sessionNotebookId: String?): PageInfo? =
    when {
      open == null -> null
      command == PenLabBridge.MEDIA_START -> open
      sessionNotebookId != null && open.notebookId == sessionNotebookId -> open
      else -> null
    }
}
