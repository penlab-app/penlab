package dev.penlab.app.notes

import androidx.core.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Moves the notebooks from the private library into Documents/PenLab (see [LibraryLocation]),
 * once, without ever risking a notebook:
 *
 *  1. each notebook is copied into a hidden `.migrating-<id>` folder in the target,
 *  2. the copy is checked against the original (same files, same sizes),
 *  3. only then is it renamed into place and the private original removed.
 *
 * A notebook with the same id already in the target is never overwritten: an identical one is
 * taken to be an earlier, interrupted move (the private copy is simply removed); a different one
 * is kept and the incoming notebook gets a new id ("<id>-moved"). Any failure stops the move and
 * leaves what is left in the private library untouched. Plain java.io, so it is unit-tested on
 * the JVM.
 */
object LibraryMigration {
  private const val STAGING = ".migrating-"
  private const val REMOVING = ".migrated-"
  private const val TRASH = ".trash"
  private const val FOLDERS = "folders.txt"
  private const val COLORS = "folder-colors.txt"

  /** One notebook to copy: [sourceDir] becomes `<target>/<targetId>`. */
  data class Item(val sourceDir: File, val targetId: String) {
    val renamed: Boolean get() = sourceDir.name != targetId
  }

  /** [copy]: notebooks to move. [alreadyThere]: private notebooks identical to one in the target. */
  data class Plan(val copy: List<Item>, val alreadyThere: List<File>) {
    val isEmpty: Boolean get() = copy.isEmpty() && alreadyThere.isEmpty()
  }

  /** [moved] counts notebooks now safely in the target (renamed ones included); [trashedMoved] counts trashed ones; [error] is null on success. */
  data class Result(val moved: Int, val renamed: Int, val error: String?, val trashedMoved: Int = 0)

  private data class MoveBatch(val moved: Int, val renamed: Int, val error: String?)

  /** Notebook folders of a library: every directory not starting with a dot (.trash, staging). */
  fun notebookDirs(library: File): List<File> =
    library.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }.orEmpty().sortedBy { it.name }

  /** Trashed notebook folders in `<library>/.trash/`. */
  fun trashDirs(library: File): List<File> = notebookDirs(File(library, TRASH))

  /** True if there is something in [source] to move. Cheap; safe to call often. */
  fun pending(source: File): Boolean = notebookDirs(source).isNotEmpty() || trashDirs(source).isNotEmpty()

  /** Decides what to copy and what to call it; touches nothing. */
  fun plan(source: File, target: File): Plan {
    val taken = target.list().orEmpty().toMutableSet()
    val copy = mutableListOf<Item>()
    val already = mutableListOf<File>()
    for (dir in notebookDirs(source)) {
      val existing = File(target, dir.name)
      when {
        !existing.exists() && dir.name !in taken -> copy += Item(dir, dir.name).also { taken += dir.name }
        existing.isDirectory && sameContents(dir, existing) -> already += dir
        else -> {
          var n = 1
          fun candidate() = if (n == 1) "${dir.name}-moved" else "${dir.name}-moved-$n"
          while (candidate() in taken) n++
          copy += Item(dir, candidate()).also { taken += it.targetId }
        }
      }
    }
    return Plan(copy, already)
  }

  /** Every file's path (relative, "/"-separated) with its size, and every directory with -1. */
  fun fingerprint(dir: File): Map<String, Long> =
    dir.walkTopDown().filter { it != dir }.associate { f ->
      f.relativeTo(dir).invariantSeparatorsPath to (if (f.isDirectory) -1L else f.length())
    }

  /** Same files, same sizes, same folders. */
  fun sameContents(a: File, b: File): Boolean = fingerprint(a) == fingerprint(b)

  /** Union of two folders.txt lists, cleaned and sorted. */
  fun mergeFolderLines(source: List<String>, target: List<String>): List<String> =
    (target + source).map(::cleanFolder).filter { it.isNotEmpty() }.distinct().sorted()

  /** Union of two folder-colors.txt lists ("path<TAB>colour"); the target's colour wins on a clash. */
  fun mergeColorLines(source: List<String>, target: List<String>): List<String> {
    fun key(line: String) = line.substringBeforeLast('\t', "")
    val merged = linkedMapOf<String, String>()
    for (line in source + target) if (key(line).isNotEmpty()) merged[key(line)] = line
    return merged.entries.sortedBy { it.key }.map { it.value }
  }

  private fun migrateDir(fromRoot: File, toRoot: File): MoveBatch {
    // Leftovers of an interrupted run: unfinished copies, and originals half-deleted after a copy was verified.
    toRoot.listFiles { f -> f.name.startsWith(STAGING) }?.forEach { it.deleteRecursively() }
    fromRoot.listFiles { f -> f.name.startsWith(REMOVING) }?.forEach { it.deleteRecursively() }

    var moved = 0
    var renamed = 0
    var error: String? = null
    try {
      val plan = plan(fromRoot, toRoot)
      for (item in plan.copy) {
        val staging = File(toRoot, STAGING + item.targetId)
        copyTree(item.sourceDir, staging)
        if (!sameContents(item.sourceDir, staging)) throw IOException("the copy of ${item.sourceDir.name} does not match the original")
        if (!staging.renameTo(File(toRoot, item.targetId))) throw IOException("could not put ${item.targetId} in place")
        removeOriginal(item.sourceDir)
        moved++
        if (item.renamed) renamed++
      }
      for (dir in plan.alreadyThere) {
        removeOriginal(dir)
        moved++
      }
    } catch (e: Exception) {
      error = e.message ?: e.javaClass.simpleName
    }
    return MoveBatch(moved, renamed, error)
  }

  /**
   * Runs the move. [source] is the private library, [target] Documents/PenLab (it must exist).
   * Blocking file work: call off the main thread.
   */
  fun run(source: File, target: File): Result {
    if (source.canonicalFile == target.canonicalFile) return Result(0, 0, null)
    val main = migrateDir(source, target)
    var renamed = main.renamed
    var error = main.error
    var trashedMoved = 0

    if (error == null && trashDirs(source).isNotEmpty()) {
      val sourceTrash = File(source, TRASH)
      val targetTrash = File(target, TRASH)
      if (!targetTrash.isDirectory && !targetTrash.mkdirs()) {
        error = "could not create $TRASH"
      } else {
        val trash = migrateDir(sourceTrash, targetTrash)
        trashedMoved = trash.moved
        renamed += trash.renamed
        error = trash.error
        if (error == null && sourceTrash.list().isNullOrEmpty()) sourceTrash.delete()
      }
    }

    // The folder list and colours are merged either way (a union loses nothing); the private
    // files are removed only after a clean run.
    try {
      mergeFile(File(source, FOLDERS), File(target, FOLDERS), deleteSource = error == null, ::mergeFolderLines)
      mergeFile(File(source, COLORS), File(target, COLORS), deleteSource = error == null, ::mergeColorLines)
    } catch (e: Exception) {
      if (error == null) error = e.message ?: e.javaClass.simpleName
    }
    return Result(main.moved, renamed, error, trashedMoved)
  }

  /** Renaming first makes the removal all-or-nothing as far as the library listing is concerned. */
  private fun removeOriginal(dir: File) {
    val gone = File(dir.parentFile, REMOVING + dir.name)
    gone.deleteRecursively()
    if (!dir.renameTo(gone)) throw IOException("could not remove the private copy of ${dir.name}")
    gone.deleteRecursively()
  }

  private fun mergeFile(from: File, into: File, deleteSource: Boolean, merge: (List<String>, List<String>) -> List<String>) {
    if (!from.exists()) return
    val lines = merge(from.readLines(), if (into.exists()) into.readLines() else emptyList())
    val file = AtomicFile(into)
    val out = file.startWrite()
    try {
      out.write(lines.joinToString("\n").toByteArray())
      file.finishWrite(out)
    } catch (e: Exception) {
      file.failWrite(out)
      throw e
    }
    if (deleteSource) from.delete()
  }

  /** Copies a directory tree, flushing each file to disk, keeping modification times. */
  private fun copyTree(from: File, to: File) {
    to.deleteRecursively()
    if (!to.mkdirs()) throw IOException("could not create ${to.name}")
    for (f in from.walkTopDown().filter { it != from }) {
      val dest = File(to, f.relativeTo(from).path)
      if (f.isDirectory) {
        if (!dest.isDirectory && !dest.mkdirs()) throw IOException("could not create ${dest.name}")
      } else {
        f.inputStream().use { input ->
          FileOutputStream(dest).use { out ->
            input.copyTo(out)
            out.fd.sync()
          }
        }
        dest.setLastModified(f.lastModified())
      }
    }
  }
}
