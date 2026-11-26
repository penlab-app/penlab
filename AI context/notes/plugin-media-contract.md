# PenLab plugin bridge — "media" capability (audio plugin) — contract (Master, 26 Nov 2026)

Goal: Graphite's lecture recording as a plugin app, with the same feel: record while writing, every stroke remembers
when it was written, a finger tap on ink plays from that moment, a recording pill on screen, recordings + transcripts.

## Data model (PenLab side, unchanged on disk)
- Strokes already carry `mediaMark` (Graphite's AudioMark, same packing): `(recordingIndex shl 32) or positionMs`,
  `-1` = none. Graphite notebooks therefore keep working.
- The notebook keeps its recordings list in notebook.properties `recordings` (already passed through, unit-separator
  joined). PenLab now OWNS this list: entry = an opaque string the plugin gives (e.g. "content://…/rec/123" or a
  Recordings/… path); the stroke's recordingIndex is the index into this list.

## Manifest
`{ "kind": "media", "id": "record", "label": "Record lecture", "icon": "...", "placement": "topBar" }`
Optional `"screen": "<activity>"` id of a `screen` capability that shows recordings/player/transcripts.

## Host service (new, in PenLab): `dev.penlab.app.plugins.host.MediaHostService`
Exported bound service, permission-gated: callers must be an ENABLED plugin package (trusted by same certificate or
consented), checked via `Binder.getCallingUid()` → package → registry. AIDL `IPenLabHost` (in :plugin-sdk):
```aidl
interface IPenLabHost {
  int apiVersion();
  // A recording started for the notebook currently open in PenLab. Returns the recordingIndex to stamp, or -1 if no
  // notebook is open. `ref` is the plugin's opaque reference for this recording (stored in the notebook's list).
  int mediaStarted(String pluginId, String ref, long startElapsedRealtimeMs);
  void mediaPaused(String pluginId, long atElapsedRealtimeMs);
  void mediaResumed(String pluginId, long atElapsedRealtimeMs);
  void mediaStopped(String pluginId);
  // Playback progress (for the glow that follows the ink). recordingIndex as returned by mediaStarted or from a seek.
  void playbackPosition(String pluginId, int recordingIndex, long positionMs, boolean playing);
  // Level 0..1 for the pill's meter, at most ~10/s. Optional.
  void level(String pluginId, float level);
}
```
- While a session is active (started, not paused/stopped) PenLab stamps every COMMITTED stroke (pen-up, never during
  the stroke) with `mediaMark = of(recordingIndex, positionMs)` where positionMs is computed from the stroke's start
  time vs the session's start/pause timeline (pauses excluded), exactly like Graphite's audioMarkFor.
- The pen hot path gains nothing: the stamp is computed once at commit from cached session state.

## Plugin service additions (plugin side, in :plugin-sdk `PenLabPluginService`)
- `Bundle run("media:command", {"command": "start"|"pause"|"resume"|"stop"})` — PenLab's pill/menu controls.
- `run("media:seek", {"ref": "<ref>", "positionMs": n})` — finger tap on marked ink → plugin plays from there.
- The plugin binds `IPenLabHost` (explicit component `dev.penlab.app/.plugins.host.MediaHostService`) to report.

## PenLab UI (host-drawn, generic, uses existing components/tokens)
- Toolbar More (or top bar per placement) entry "Record lecture" → sends `start`.
- While a session exists: a recording pill in the top chrome (red dot, elapsed time, level meter, pause/resume, stop),
  palm-ignoring, tap-swallow rules; hidden in Present (recording continues).
- Finger tap on ink that carries a mediaMark (when a media plugin is enabled): `media:seek` with the notebook's
  `recordings[index]` ref + position; a soft glow follows `playbackPosition` over the strokes written at that time
  (port Graphite's playback glow, drawn off the ink path).
- If no media plugin is installed, marked strokes behave as plain ink (no seek).

## The audio plugin app (penlab-extras `:audio`, dev.akram.penlab.audio, shared key)
- Port Graphite's audio/: Recorder (crash-safe ADTS → m4a), RecordingService (foreground, microphone type,
  notification with pause/stop), Player, QuietWatch (too-quiet warning), Srt/Transcripts.
- Recordings saved where Graphite saved them (Recordings/Graphite … rename to Recordings/PenLab for new ones; read old
  Graphite ones so migrated notebooks seek correctly).
- `screen` capability "Recordings": list for this notebook, player with seek bar, transcript (srt) view synced to
  playback, rename/delete-with-undo. Themed via PenLabScreenContract.theme().
- Permissions declared: microphone, notifications.

## Tests
- JVM: mark packing/unpacking, positionMs with pauses, host caller policy, command/seek wire format, ref list
  persistence. Device: record 20 s while writing → strokes marked → stop → tap ink → plays from that point → glow;
  uninstall plugin → ink still fine.
