package dev.penlab.sdk;

/**
 * What PenLab offers a plugin app (the "media" capability: lecture recording). The plugin binds PenLab's
 * exported service dev.penlab.app/.plugins.host.MediaHostService, usually through PenLabHostClient, and reports
 * what its recorder and player are doing. PenLab answers only to plugin apps that are switched on and declare a
 * "media" capability; anyone else gets a SecurityException.
 */
interface IPenLabHost {
  /** The host interface version (1). */
  int apiVersion();

  /**
   * A recording started for the notebook currently open in PenLab. Returns the recordingIndex to stamp, or -1 if no
   * notebook is open (or PenLab refused the reference). `ref` is the plugin's opaque reference for this recording
   * (stored in the notebook's list).
   */
  int mediaStarted(String pluginId, String ref, long startElapsedRealtimeMs);
  void mediaPaused(String pluginId, long atElapsedRealtimeMs);
  void mediaResumed(String pluginId, long atElapsedRealtimeMs);
  void mediaStopped(String pluginId);

  /** Playback progress (for the glow that follows the ink). recordingIndex as returned by mediaStarted or from a seek. */
  void playbackPosition(String pluginId, int recordingIndex, long positionMs, boolean playing);

  /** Level 0..1 for the pill's meter, at most ~10/s. Optional. */
  void level(String pluginId, float level);
}
