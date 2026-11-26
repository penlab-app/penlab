package dev.penlab.app.ink

/**
 * The strokes on a page, plus the undo and redo stacks.
 *
 * Every change to the page goes through here so it can be undone. It is generic ([T] is a stroke
 * in the app) so the logic can be unit-tested on the laptop without the tablet.
 *
 * Draw order is list order: later items are drawn on top.
 */
class StrokeHistory<T> {

  /**
   * Told about every single stroke that enters or leaves the page, with its position in
   * [strokes]. Lets the drawing cache update only what changed instead of rebuilding.
   */
  interface Listener<T> {
    fun onInserted(index: Int, stroke: T)

    fun onRemoved(index: Int, stroke: T)
  }

  var listener: Listener<T>? = null

  private val items = mutableListOf<T>()
  private val undoStack = ArrayDeque<Edit<T>>()
  private val redoStack = ArrayDeque<Edit<T>>()

  /** What is on the page right now, bottom to top. */
  val strokes: List<T>
    get() = items

  val canUndo: Boolean
    get() = undoStack.isNotEmpty()

  val canRedo: Boolean
    get() = redoStack.isNotEmpty()

  /**
   * Puts a new stroke on the page: on top by default, or at [index] to slip it underneath
   * (the highlighter goes below the ink).
   */
  fun add(stroke: T, index: Int = items.size) {
    insert(index, stroke)
    record(Edit.Add(index, stroke))
  }

  /**
   * Removes every stroke matching [shouldRemove]. Returns true if anything was removed.
   *
   * With [mergeIntoPrevious], the removal joins the previous erase step, so one eraser drag that
   * removes strokes over many touch events is a single undo.
   */
  fun removeAll(mergeIntoPrevious: Boolean = false, shouldRemove: (T) -> Boolean): Boolean {
    val removed = items.withIndex().filter { shouldRemove(it.value) }
    if (removed.isEmpty()) return false
    removeAt(removed)

    val previous = undoStack.lastOrNull()
    if (mergeIntoPrevious && previous is Edit.Remove) {
      previous.steps += removed
      redoStack.clear()
    } else {
      record(Edit.Remove(mutableListOf(removed)))
    }
    return true
  }

  /**
   * Fills an empty page with saved strokes. Not an undoable step: undo starts from the page as
   * it was opened.
   */
  fun load(strokes: List<T>) {
    for (stroke in strokes) insert(items.size, stroke)
    undoStack.clear()
    redoStack.clear()
  }

  /**
   * Adds several strokes as one undo step (a paste). Each pair is (position, stroke), applied in
   * order, so each position counts the strokes added before it.
   */
  fun addAll(placed: List<Pair<Int, T>>) {
    if (placed.isEmpty()) return
    val steps = placed.map { (index, stroke) -> IndexedValue(index.coerceIn(0, items.size), stroke).also { insert(it.index, stroke) } }
    record(Edit.Insert(steps))
  }

  /**
   * Swaps strokes for new versions in the same places (moving a selection makes moved copies).
   * Strokes not on the page are ignored. Returns true if anything was swapped.
   */
  fun replace(changes: Map<T, T>): Boolean {
    val swaps = items.withIndex().filter { it.value in changes }.map { Swap(it.index, it.value, changes.getValue(it.value)) }
    if (swaps.isEmpty()) return false
    swaps.forEach { swapAt(it.index, it.new) }
    record(Edit.Replace(swaps))
    return true
  }

  /** Empties the page. Undoable like any other erase. */
  fun clear(): Boolean = removeAll { true }

  /** Reverts the latest change. Returns false if there is nothing to undo. */
  fun undo(): Boolean {
    val edit = undoStack.removeLastOrNull() ?: return false
    undoEdit(edit)
    redoStack += edit
    return true
  }

  private fun undoEdit(edit: Edit<T>) {
    when (edit) {
      is Edit.Add -> removeAt(listOf(IndexedValue(edit.index, edit.stroke)))
      // Put strokes back at their old positions, newest step first, lowest index first.
      is Edit.Remove -> edit.steps.asReversed().forEach { step ->
        step.forEach { insert(it.index, it.value) }
      }
      is Edit.Replace -> edit.swaps.forEach { swapAt(it.index, it.old) }
      is Edit.Insert -> edit.steps.asReversed().forEach { removeAt(listOf(it)) }
      is Edit.Group -> edit.edits.asReversed().forEach(::undoEdit)
      is Edit.Action -> edit.undo()
      is Edit.State -> stateHandler?.invoke(edit.kind, edit.before)
    }
  }

  /** Re-applies the latest undone change. Returns false if there is nothing to redo. */
  fun redo(): Boolean {
    val edit = redoStack.removeLastOrNull() ?: return false
    redoEdit(edit)
    undoStack += edit
    return true
  }

  private fun redoEdit(edit: Edit<T>) {
    when (edit) {
      is Edit.Add -> insert(edit.index, edit.stroke)
      is Edit.Remove -> edit.steps.forEach(::removeAt)
      is Edit.Replace -> edit.swaps.forEach { swapAt(it.index, it.new) }
      is Edit.Insert -> edit.steps.forEach { insert(it.index, it.value) }
      is Edit.Group -> edit.edits.forEach(::redoEdit)
      is Edit.State -> stateHandler?.invoke(edit.kind, edit.after)
      is Edit.Action -> edit.redo()
    }
  }

  /**
   * Runs [block] and makes every change it records into one undo step (inserting a page moves
   * every later stroke and adds the page: one undo puts it all back).
   */
  fun <R> grouped(block: () -> R): R {
    if (group != null) return block()
    beginGroup()
    try {
      return block()
    } finally {
      endGroup()
    }
  }

  /**
   * Starts collecting changes into one undo step that lasts until [endGroup] (one eraser drag
   * spans many touch events). Nested calls are ignored.
   */
  fun beginGroup() {
    if (group == null) group = mutableListOf()
  }

  /**
   * Ends the group. Returns true if it recorded something: only now does [canUndo] become true,
   * so the caller must tell the toolbar (a change reported while the group was still open saw an
   * empty undo stack: the Undo button stayed grey after an eraser drag).
   */
  fun endGroup(): Boolean {
    val edits = group ?: return false
    group = null
    if (edits.isEmpty()) return false
    push(Edit.Group(edits))
    return true
  }

  /**
   * Records a change that is not about strokes (the page count, bookmarks): [undo] and [redo]
   * are run when it is undone or redone. Call it after making the change.
   */
  fun recordAction(undo: () -> Unit, redo: () -> Unit) = record(Edit.Action(undo, redo))

  /**
   * Records a change that is not about strokes as two plain states (before and after) of some
   * [kind] of thing (the photos and text boxes, the page count...). Undo and redo hand the
   * right state to [stateHandler]. Unlike [recordAction], these can be saved to a file, so undo
   * survives closing the notebook. The states must never change afterwards (pass copies).
   */
  fun recordState(kind: Int, before: Any, after: Any) = record(Edit.State(kind, before, after))

  /** Puts a recorded state back (see [recordState]). */
  var stateHandler: ((kind: Int, state: Any) -> Unit)? = null

  /**
   * The newest [max] undo steps and redo steps, oldest first, for saving. Stops at a step that
   * cannot be saved (a [recordAction] closure): everything older than it is left out.
   */
  fun saveableSteps(max: Int): Pair<List<Edit<T>>, List<Edit<T>>> {
    fun take(stack: ArrayDeque<Edit<T>>): List<Edit<T>> {
      val out = ArrayList<Edit<T>>()
      for (edit in stack.asReversed()) {
        if (out.size >= max || !edit.isSaveable()) break
        out += edit.frozen()
      }
      return out.asReversed()
    }
    return take(undoStack) to take(redoStack)
  }

  /** Brings back saved undo and redo steps, after [load] put the saved strokes on the page. */
  fun restoreSteps(undo: List<Edit<T>>, redo: List<Edit<T>>) {
    undoStack.clear()
    undoStack.addAll(undo)
    redoStack.clear()
    redoStack.addAll(redo)
  }

  private fun Edit<T>.isSaveable(): Boolean =
    when (this) {
      is Edit.Action -> false
      is Edit.Group -> edits.all { it.isSaveable() }
      else -> true
    }

  /** A copy that later merges (one eraser drag joining a step) cannot change. */
  private fun Edit<T>.frozen(): Edit<T> =
    when (this) {
      is Edit.Remove -> Edit.Remove(steps.toMutableList())
      is Edit.Group -> Edit.Group(edits.map { it.frozen() })
      else -> this
    }

  private var group: MutableList<Edit<T>>? = null

  private fun record(edit: Edit<T>) {
    val collecting = group
    if (collecting != null) {
      collecting += edit
      redoStack.clear()
    } else {
      push(edit)
    }
  }

  private fun push(edit: Edit<T>) {
    undoStack += edit
    redoStack.clear() // a new change makes the old "future" meaningless
    if (undoStack.size > MAX_UNDO_STEPS) undoStack.removeFirst()
  }

  /** Removes from the highest index down, so earlier removals do not shift later ones. */
  private fun removeAt(indexed: List<IndexedValue<T>>) {
    indexed.asReversed().forEach {
      items.removeAt(it.index)
      listener?.onRemoved(it.index, it.value)
    }
  }

  private fun swapAt(index: Int, stroke: T) {
    val old = items[index]
    items[index] = stroke
    listener?.onRemoved(index, old)
    listener?.onInserted(index, stroke)
  }

  class Swap<T>(val index: Int, val old: T, val new: T)

  private fun insert(index: Int, stroke: T) {
    items.add(index, stroke)
    listener?.onInserted(index, stroke)
  }

  /** One undo step. Public so the steps can be saved and read back (see `HistoryFile`). */
  sealed interface Edit<T> {
    /** Undo and redo run in strict reverse order, so [index] is still right when they need it. */
    class Add<T>(val index: Int, val stroke: T) : Edit<T>

    /** Each step is the strokes one removal took out, with the index each one had. */
    class Remove<T>(val steps: MutableList<List<IndexedValue<T>>>) : Edit<T>

    class Replace<T>(val swaps: List<Swap<T>>) : Edit<T>

    class Insert<T>(val steps: List<IndexedValue<T>>) : Edit<T>

    class Group<T>(val edits: List<Edit<T>>) : Edit<T>

    class Action<T>(val undo: () -> Unit, val redo: () -> Unit) : Edit<T>

    class State<T>(val kind: Int, val before: Any, val after: Any) : Edit<T>
  }

  private companion object {
    const val MAX_UNDO_STEPS = 500
  }
}
