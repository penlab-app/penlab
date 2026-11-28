package dev.penlab.app.ink

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** The page count, bookmarks and PDF pages, as one undoable state (pages added or deleted). */
data class SavedPages(val count: Int, val bookmarks: List<Int>, val backgrounds: List<Int>)

/** The layers' names, hidden indices, and active layer index, as one undoable state. */
data class SavedLayers(
  val names: List<String>,
  val hidden: List<Int> = emptyList(),
  val current: Int = 0,
)

/**
 * One undo step as plain numbers, mirroring [StrokeHistory.Edit]: strokes added, removed,
 * swapped or inserted (with their positions), groups, and states (photos and text boxes, pages, layers).
 */
sealed class SavedEdit {
  class Add(val index: Int, val stroke: SavedStroke) : SavedEdit()

  class Remove(val steps: List<List<Pair<Int, SavedStroke>>>) : SavedEdit()

  class Replace(val swaps: List<Triple<Int, SavedStroke, SavedStroke>>) : SavedEdit()

  class Insert(val steps: List<Pair<Int, SavedStroke>>) : SavedEdit()

  class Group(val edits: List<SavedEdit>) : SavedEdit()

  class Items(val before: List<SavedItem>, val after: List<SavedItem>) : SavedEdit()

  class Pages(val before: SavedPages, val after: SavedPages) : SavedEdit()

  class Layers(val before: SavedLayers, val after: SavedLayers) : SavedEdit()
}

/**
 * The undo and redo steps of a notebook, saved beside its strokes so undo still works after the
 * notebook is closed and opened again. [strokeCount] is how many strokes the page
 * had when this was written: if the strokes file does not match (a save of one finished but not
 * the other), the history is ignored rather than trusted.
 */
class SavedHistory(val strokeCount: Int, val undo: List<SavedEdit>, val redo: List<SavedEdit>)

/**
 * `notebook.history`: gzip, then `int magic "GRHS"`, `int version`, `int strokeCount`, then the
 * undo steps and the redo steps (each: `int count`, then each step as a tag byte and its data).
 */
object HistoryFile {
  private const val MAGIC = 0x47524853 // "GRHS"
  private const val VERSION = 5 // 2: strokes carry their layer; 3: and their audio mark; 4: and their line group; 5: layer edits carry hidden & current
  internal const val LEGACY_LAYER_CURRENT = -1

  /** Whether the file being read stores layers (set per read; reads run one at a time). */
  @Volatile private var hasLayer = true
  @Volatile private var hasAudio = true
  @Volatile private var hasGroup = true
  @Volatile private var hasLayerDetails = true

  private const val ADD = 1
  private const val REMOVE = 2
  private const val REPLACE = 3
  private const val INSERT = 4
  private const val GROUP = 5
  private const val ITEMS = 6
  private const val PAGES = 7
  private const val LAYERS = 8

  fun write(history: SavedHistory, output: OutputStream) {
    DataOutputStream(BufferedOutputStream(GZIPOutputStream(output, 1 shl 16), 1 shl 16)).use { out ->
      out.writeInt(MAGIC)
      out.writeInt(VERSION)
      out.writeInt(history.strokeCount)
      writeList(out, history.undo)
      writeList(out, history.redo)
    }
  }

  fun read(input: InputStream): SavedHistory =
    DataInputStream(BufferedInputStream(GZIPInputStream(input, 1 shl 16), 1 shl 16)).use { data ->
      if (data.readInt() != MAGIC) throw IOException("not a PenLab history file")
      val version = data.readInt()
      if (version !in 1..VERSION) throw IOException("history from another version")
      hasLayer = version >= 2
      hasAudio = version >= 3
      hasGroup = version >= 4
      hasLayerDetails = version >= 5
      SavedHistory(data.readInt(), readList(data), readList(data))
    }

  private fun writeList(out: DataOutputStream, edits: List<SavedEdit>) {
    out.writeInt(edits.size)
    edits.forEach { writeEdit(out, it) }
  }

  private fun readList(data: DataInputStream): List<SavedEdit> {
    val n = data.readInt()
    if (n < 0 || n > 100_000) throw IOException("bad step count")
    return List(n) { readEdit(data) }
  }

  private fun writeIndexed(out: DataOutputStream, list: List<Pair<Int, SavedStroke>>) {
    out.writeInt(list.size)
    for ((index, stroke) in list) {
      out.writeInt(index)
      PageFile.writeStroke(out, stroke)
    }
  }

  private fun readIndexed(data: DataInputStream): List<Pair<Int, SavedStroke>> =
    List(count(data)) { data.readInt() to PageFile.readStroke(data, hasLayer, hasAudio, hasGroup) }

  private fun count(data: DataInputStream): Int = data.readInt().also { if (it < 0 || it > 10_000_000) throw IOException("bad count") }

  private fun writePages(out: DataOutputStream, p: SavedPages) {
    out.writeInt(p.count)
    out.writeInt(p.bookmarks.size)
    p.bookmarks.forEach(out::writeInt)
    out.writeInt(p.backgrounds.size)
    p.backgrounds.forEach(out::writeInt)
  }

  private fun readPages(data: DataInputStream) =
    SavedPages(data.readInt(), List(count(data)) { data.readInt() }, List(count(data)) { data.readInt() })

  private fun writeLayers(out: DataOutputStream, l: SavedLayers) {
    out.writeInt(l.names.size)
    l.names.forEach(out::writeUTF)
    out.writeInt(l.hidden.size)
    l.hidden.forEach(out::writeInt)
    out.writeInt(l.current)
  }

  private fun readLayers(data: DataInputStream): SavedLayers {
    val names = List(count(data)) { data.readUTF() }
    if (!hasLayerDetails) return SavedLayers(names, emptyList(), LEGACY_LAYER_CURRENT)
    val hidden = List(count(data)) { data.readInt() }
    val current = data.readInt()
    return SavedLayers(names, hidden, current)
  }

  private fun writeEdit(out: DataOutputStream, edit: SavedEdit) {
    when (edit) {
      is SavedEdit.Add -> {
        out.writeByte(ADD)
        out.writeInt(edit.index)
        PageFile.writeStroke(out, edit.stroke)
      }
      is SavedEdit.Remove -> {
        out.writeByte(REMOVE)
        out.writeInt(edit.steps.size)
        edit.steps.forEach { writeIndexed(out, it) }
      }
      is SavedEdit.Replace -> {
        out.writeByte(REPLACE)
        out.writeInt(edit.swaps.size)
        for ((index, old, new) in edit.swaps) {
          out.writeInt(index)
          PageFile.writeStroke(out, old)
          PageFile.writeStroke(out, new)
        }
      }
      is SavedEdit.Insert -> {
        out.writeByte(INSERT)
        writeIndexed(out, edit.steps)
      }
      is SavedEdit.Group -> {
        out.writeByte(GROUP)
        writeList(out, edit.edits)
      }
      is SavedEdit.Items -> {
        out.writeByte(ITEMS)
        out.writeInt(edit.before.size)
        edit.before.forEach { PageFile.writeItem(out, it) }
        out.writeInt(edit.after.size)
        edit.after.forEach { PageFile.writeItem(out, it) }
      }
      is SavedEdit.Pages -> {
        out.writeByte(PAGES)
        writePages(out, edit.before)
        writePages(out, edit.after)
      }
      is SavedEdit.Layers -> {
        out.writeByte(LAYERS)
        writeLayers(out, edit.before)
        writeLayers(out, edit.after)
      }
    }
  }

  private fun readEdit(data: DataInputStream): SavedEdit =
    when (val tag = data.readByte().toInt()) {
      ADD -> SavedEdit.Add(data.readInt(), PageFile.readStroke(data, hasLayer, hasAudio, hasGroup))
      REMOVE -> SavedEdit.Remove(List(count(data)) { readIndexed(data) })
      REPLACE -> SavedEdit.Replace(List(count(data)) { Triple(data.readInt(), PageFile.readStroke(data, hasLayer, hasAudio, hasGroup), PageFile.readStroke(data, hasLayer, hasAudio, hasGroup)) })
      INSERT -> SavedEdit.Insert(readIndexed(data))
      GROUP -> SavedEdit.Group(readList(data))
      ITEMS -> SavedEdit.Items(List(count(data)) { PageFile.readItem(data) }, List(count(data)) { PageFile.readItem(data) })
      PAGES -> SavedEdit.Pages(readPages(data), readPages(data))
      LAYERS -> SavedEdit.Layers(readLayers(data), readLayers(data))
      else -> throw IOException("unknown step $tag")
    }
}
