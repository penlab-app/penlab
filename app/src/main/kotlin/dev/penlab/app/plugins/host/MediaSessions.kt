package dev.penlab.app.plugins.host

import dev.penlab.app.ink.MediaMark
import kotlin.math.max

/**
 * The state behind the "media" capability: which recording is live, which one is playing, how loud the microphone is.
 *
 * Calls arrive on BINDER threads (from [MediaHostService]) and are read on the main thread (the pill, the playback glow)
 * and at pen-up (the stamp). So writes take [lock], and everything the readers use is an immutable snapshot in a
 * `@Volatile` field: the pen-up path reads [session] once and does arithmetic, with no lock and no binder call.
 *
 * Plain Kotlin (the clock and the callbacks are injected), unit-tested on the JVM. At most one recording is live at a time.
 */
class MediaSessions(
  /** The device clock the plugins report in ([android.os.SystemClock.elapsedRealtime]). */
  private val clock: () -> Long,
  /** Something visible changed (a session started or ended, paused, playback moved). Called on whichever thread caused it. */
  onChange: () -> Unit = {},
) {
  /** Replaceable: the screen sets it while it is on. */
  @Volatile var listener: () -> Unit = onChange
  /** What is playing: the recording's index, the position, and when PenLab was told (device clock). */
  class Playback(val recordingIndex: Int, val positionMs: Long, val playing: Boolean, val receivedAt: Long)

  /** The open notebook as far as recordings go: its id and its list, which only grows (see [RecordingRefs]). */
  class NotebookBinding(val id: String, recordings: List<String>, private val onRecordingsChanged: (List<String>) -> Unit) {
    private var list = recordings

    /** The recordings now. */
    @Synchronized fun recordings(): List<String> = list

    /** The index of [ref] (added if new, and the new list reported), or -1 if the reference is not storable. */
    fun add(ref: String): Int {
      val changed: List<String>
      val index: Int
      synchronized(this) {
        val (next, i) = RecordingRefs.add(list, ref) ?: return -1
        index = i
        changed = next
        if (next === list) return index
        list = next
      }
      onRecordingsChanged(changed)
      return index
    }

    /**
     * Takes [ref] off the end of the list, if it is still the last entry (never from the middle: a stroke's mark holds an index, so
     * entries before the end must not move). True if it was removed, and the new list reported.
     */
    fun removeLast(ref: String): Boolean {
      val changed: List<String>
      synchronized(this) {
        if (list.lastOrNull() != ref) return false
        changed = list.dropLast(1)
        list = changed
      }
      onRecordingsChanged(changed)
      return true
    }
  }

  private val lock = Any()

  /** The live recording, or null. */
  @Volatile var session: MediaSession? = null
    private set

  /** The player's last report, or null. */
  @Volatile var playback: Playback? = null
    private set

  /** The microphone level 0..1 (0 when no recording is live). */
  @Volatile var level: Float = 0f
    private set

  @Volatile private var notebook: NotebookBinding? = null

  // What the live session needs to tell whether it was a ghost (see [stopped]); reset by every [started].
  private var sessionBinding: NotebookBinding? = null // under [lock]
  private var sessionAppended = false // under [lock]: this start added the ref (it did not reuse an old entry)
  private var sessionStartedAt = 0L // under [lock]
  @Volatile private var sessionReported = false // a level or playback report arrived
  @Volatile private var sessionMarked = false // a stroke carries this recording's index
  @Volatile private var seekedIndex = -1
  private var lastLevelAt: Long? = null

  /** A notebook was opened: recordings that start now belong to it. */
  fun bindNotebook(binding: NotebookBinding?) {
    notebook = binding
  }

  /** A notebook closed. Its recording (if any) goes on, but nothing is stamped into other notebooks. */
  fun unbindNotebook(id: String) {
    synchronized(lock) { if (notebook?.id == id) notebook = null }
  }

  /**
   * [pluginId] started recording ([ref], [startMs] on the device clock). Returns the recording's index in the open
   * notebook, or -1 when no notebook is open, the reference is refused, or another plugin already records.
   * A plugin that starts again without stopping replaces its own live recording.
   */
  fun started(pluginId: String, ref: String, startMs: Long): Int {
    val now = clock()
    val index: Int
    synchronized(lock) {
      val nb = notebook ?: return -1
      val live = session
      if (live != null && live.pluginId != pluginId) return -1
      val before = nb.recordings().size
      index = nb.add(ref)
      if (index < 0) return -1
      sessionBinding = nb
      sessionAppended = nb.recordings().size > before
      sessionStartedAt = now
      sessionReported = false
      sessionMarked = false
      // A start in the future, or long ago, is a wrong clock: use the moment PenLab heard of it.
      val start = if (startMs > now || startMs < now - MAX_BACKDATE_MS) now else startMs
      session = MediaSession(pluginId, nb.id, index, ref, MediaTimeline(start))
      level = 0f
    }
    listener()
    return index
  }

  fun paused(pluginId: String, at: Long) = update(pluginId) { it.withTimeline(it.timeline.paused(at.coerceAtMost(clock()))) }

  fun resumed(pluginId: String, at: Long) = update(pluginId) { it.withTimeline(it.timeline.resumed(at.coerceAtMost(clock()))) }

  /**
   * The recording of [pluginId] ended. A recording that never really started (the plugin said "started", then failed to open the
   * microphone or its service) must not leave a reference in the notebook's list, so the reference is taken off again when ALL of
   * these hold: this start appended it, it is still the last entry, the plugin sent no level and no playback report, no stroke was
   * marked with it, and it ended within [GHOST_WINDOW_MS] of starting. Otherwise it stays (an entry in the middle never moves).
   */
  fun stopped(pluginId: String) {
    var ghost: Pair<NotebookBinding, String>? = null
    synchronized(lock) {
      val s = session ?: return
      if (s.pluginId != pluginId) return
      val binding = sessionBinding
      if (binding != null && sessionAppended && !sessionReported && !sessionMarked && clock() - sessionStartedAt <= GHOST_WINDOW_MS) ghost = binding to s.ref
      session = null
      level = 0f
    }
    ghost?.let { (binding, ref) -> binding.removeLast(ref) }
    listener()
  }

  /** Forgets the live recording without the plugin's say-so (PenLab could not reach it to stop it, or the plugin was switched off). */
  fun endLocally() {
    synchronized(lock) {
      if (session == null) return
      session = null
      level = 0f
    }
    listener()
  }

  fun playbackPosition(pluginId: String, recordingIndex: Int, positionMs: Long, playing: Boolean) {
    // Anyone enabled may report: a player may belong to another media plugin than the recorder. Garbage is dropped:
    // the index must be one PenLab knows (the live recording, the one a tap just asked for, or one in the open notebook's list).
    if (positionMs < 0 || !isKnownRecording(recordingIndex)) return
    sessionReported = true
    playback = Playback(recordingIndex, positionMs, playing, clock())
    listener()
  }

  /**
   * A tap on ink asked the plugin to play recording [recordingIndex] (what the seek request carried): reports for it are
   * accepted from now on even if the notebook is no longer bound. This is what makes the glow work for a recording whose
   * reference the plugin does not recognise (a notebook migrated from Graphite): it reports the index it was sent.
   */
  fun noteSeek(recordingIndex: Int) {
    if (recordingIndex >= 0) seekedIndex = recordingIndex
  }

  private fun isKnownRecording(index: Int): Boolean =
    index >= 0 && (index == session?.recordingIndex || index == seekedIndex || index < (notebook?.recordings()?.size ?: 0))

  /** The player was closed or went stale: no glow. */
  fun clearPlayback() {
    if (playback == null) return
    playback = null
    listener()
  }

  /** The microphone level; values arriving faster than [MIN_LEVEL_GAP_MS] apart are dropped (the contract says ~10 a second). */
  fun level(pluginId: String, value: Float) {
    val now = clock()
    synchronized(lock) {
      if (session?.pluginId != pluginId) return
      sessionReported = true
      if (lastLevelAt?.let { now - it < MIN_LEVEL_GAP_MS } == true) return
      lastLevelAt = now
      level = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
    }
  }

  /**
   * The mark for a stroke of [notebookId] committed now ([MediaSession.markFor]); [strokeDurationMs] says how long it took to
   * draw and is asked only while a recording of this notebook is live. Called at pen-up on the main thread: one volatile
   * read and a little arithmetic.
   */
  fun markFor(notebookId: String, strokeDurationMs: () -> Long): Long {
    val live = session ?: return MediaMark.NONE
    if (live.notebookId != notebookId) return MediaMark.NONE
    val mark = MediaSession.markFor(live, notebookId, clock(), strokeDurationMs())
    if (mark != MediaMark.NONE) sessionMarked = true
    return mark
  }

  /** Milliseconds into the live recording now (pauses not counted), or 0. */
  fun elapsedMs(): Long {
    val s = session ?: return 0L
    return s.timeline.positionAt(clock())
  }

  private inline fun update(pluginId: String, change: (MediaSession) -> MediaSession) {
    synchronized(lock) {
      val s = session ?: return
      if (s.pluginId != pluginId) return
      val next = change(s)
      if (next.timeline === s.timeline) return // already in that state
      session = next
    }
    listener()
  }

  companion object {
    /** A start more than this long ago is not believed. */
    const val MAX_BACKDATE_MS = 60_000L

    /** A recording that ends this soon after it started, having reported nothing and marked nothing, was a failed start (see [stopped]). */
    const val GHOST_WINDOW_MS = 10_000L

    /** Level reports closer than this are dropped. */
    const val MIN_LEVEL_GAP_MS = 50L

    /** A playback report older than this is stale: the glow goes (the player was closed or died). */
    const val PLAYBACK_STALE_MS = 15_000L

    /** A tap on ink starts the audio this much BEFORE the stroke was written, so the sentence it belongs to is heard from its start (Graphite's lead). */
    const val SEEK_LEAD_MS = 2_000L

    /** Where a tap on ink with [markPositionMs] starts the audio. */
    fun seekPosition(markPositionMs: Long): Long = max(0L, markPositionMs - SEEK_LEAD_MS)
  }
}
