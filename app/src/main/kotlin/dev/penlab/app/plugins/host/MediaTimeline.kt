package dev.penlab.app.plugins.host

import dev.penlab.app.ink.MediaMark

/**
 * Where in a recording a moment falls, with pauses left out: the recording's own clock (what the audio file's
 * position is) against the device clock ([android.os.SystemClock.elapsedRealtime], which is what the plugin reports).
 *
 * Immutable on purpose: [MediaSessions] publishes one snapshot through a `@Volatile` field, and the pen-up stamp
 * reads it with no lock and no binder call. Plain Kotlin, so it is unit-tested on the JVM.
 *
 * Pauses are `[from, until)` on the device clock; the last one is open ([until] null) while the recording is paused.
 */
class MediaTimeline private constructor(val startMs: Long, private val pauses: List<Pause>) {
  /** One pause: from [from] until [until], or until now when [until] is null. */
  class Pause(val from: Long, val until: Long?)

  constructor(startMs: Long) : this(startMs, emptyList())

  /** True between a pause and its resume. */
  val isPaused: Boolean get() = pauses.lastOrNull()?.until == null && pauses.isNotEmpty()

  /** The recording paused at [at] (ignored if it already is; clamped so it cannot start before the recording did). */
  fun paused(at: Long): MediaTimeline = if (isPaused) this else MediaTimeline(startMs, pauses + Pause(maxOf(at, startMs, pauses.lastOrNull()?.until ?: startMs), null))

  /** The recording went on at [at] (ignored if it is not paused; never before the pause began). */
  fun resumed(at: Long): MediaTimeline {
    val open = pauses.lastOrNull()?.takeIf { it.until == null } ?: return this
    return MediaTimeline(startMs, pauses.dropLast(1) + Pause(open.from, maxOf(at, open.from)))
  }

  /**
   * Milliseconds into the recording at device time [t], not counting pauses. A moment inside a pause has the position
   * the recording had when it paused (the audio did not move). A moment before the start is 0.
   */
  fun positionAt(t: Long): Long {
    if (t <= startMs) return 0L
    var position = t - startMs
    for (p in pauses) {
      val from = p.from
      val until = p.until ?: Long.MAX_VALUE
      val overlap = minOf(until, t) - from
      if (overlap > 0) position -= overlap
    }
    return position.coerceAtLeast(0L)
  }

  /** True if device time [t] lies inside a pause (a pause that is still open counts to the end of time). */
  fun isPausedAt(t: Long): Boolean = pauses.any { t >= it.from && t < (it.until ?: Long.MAX_VALUE) }
}

/** The recording a stroke is stamped into: its place in the notebook's list and the timeline of its audio. */
class MediaSession(
  /** The `id` of the plugin's manifest (what the plugin passes to every host call). */
  val pluginId: String,
  /** The notebook it records for: only that notebook's ink is stamped. */
  val notebookId: String,
  /** The notebook's index for this recording (what strokes carry in their [MediaMark]). */
  val recordingIndex: Int,
  /** The plugin's own reference for the recording (stored in the notebook's list). */
  val ref: String,
  val timeline: MediaTimeline,
) {
  val paused: Boolean get() = timeline.isPaused

  fun withTimeline(t: MediaTimeline) = MediaSession(pluginId, notebookId, recordingIndex, ref, t)

  companion object {
    /**
     * The mark for a stroke of [notebookId] that is being committed at device time [nowMs] and took [strokeDurationMs]
     * to draw; [MediaMark.NONE] when there is nothing to stamp. The stamp is where the stroke BEGAN, so it points at
     * what was being said when the writing started. A stroke that began during a pause has no mark (nothing was being
     * recorded then); one that began just before a pause keeps its mark even if the pen came up inside the pause.
     */
    fun markFor(session: MediaSession?, notebookId: String, nowMs: Long, strokeDurationMs: Long): Long {
      if (session == null || session.notebookId != notebookId) return MediaMark.NONE
      val began = nowMs - strokeDurationMs.coerceAtLeast(0L)
      if (session.timeline.isPausedAt(began)) return MediaMark.NONE
      return MediaMark.of(session.recordingIndex, session.timeline.positionAt(began))
    }
  }
}
