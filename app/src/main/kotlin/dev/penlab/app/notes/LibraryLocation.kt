package dev.penlab.app.notes

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Where the notebooks live: the ONE place that decides, so nothing else builds a path to the
 * library by hand (NotebookStore asks [root]; every other class asks NotebookStore).
 *
 * With "All files access" granted the library is `Documents/PenLab` on shared storage, which
 * survives uninstalling the app. Without it (or if that folder cannot be made) the library is
 * the app's private `files/library`, so the app still works; notebooks made there are moved to
 * Documents/PenLab later by [LibraryMigration] once the permission is given.
 *
 * [root] reads a cached answer: call [refresh] when the permission may have changed (on
 * start and on resume), not per file.
 */
class LibraryLocation(
  /** The app's private files directory; also where the pre-notebook single page was saved. */
  val privateDir: File,
  /** `Documents/PenLab` on shared storage (not created until the permission is granted). */
  val sharedLibrary: File,
  private val hasAllFilesAccess: () -> Boolean,
) {
  /** The old, private library; the fallback and the source of a migration. */
  val privateLibrary = File(privateDir, "library")

  @Volatile private var useShared = false

  init {
    refresh()
  }

  /** Re-reads the permission. Returns true if the library moved to the other place. */
  fun refresh(): Boolean {
    val before = useShared
    useShared = hasAllFilesAccess() && (sharedLibrary.isDirectory || sharedLibrary.mkdirs())
    return before != useShared
  }

  /** "All files access" is granted and Documents/PenLab is usable, so notes are being kept there. */
  val isShared: Boolean get() = useShared

  /** The folder with one subfolder per notebook. Always exists. */
  val root: File
    get() = (if (useShared) sharedLibrary else privateLibrary).also { it.mkdirs() }

  companion object {
    /** The real thing: Documents/PenLab, enabled by `Environment.isExternalStorageManager()`. */
    fun forApp(context: Context) = LibraryLocation(
      context.filesDir,
      File(File(Environment.getExternalStorageDirectory(), "Documents"), "PenLab"),
    ) { Environment.isExternalStorageManager() }
  }
}
