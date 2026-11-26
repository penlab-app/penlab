package dev.penlab.sdk;

import android.os.Bundle;

/**
 * The interface PenLab binds to on a plugin's service (action dev.penlab.PLUGIN).
 *
 * Plugin authors do not implement this by hand: extend PenLabPluginService, which also checks who is
 * calling. Files travel as ParcelFileDescriptors inside the Bundles (no content URIs between the apps).
 */
interface IPenLabPlugin {
  /** The bridge version the plugin speaks (2). PenLab refuses a plugin that answers anything else. */
  int apiVersion();

  /**
   * Runs one capability. [requestJson] is a PluginRequest; [files] maps file names (see the request)
   * to ParcelFileDescriptors PenLab opened for the plugin. The returned Bundle holds the PluginResult
   * JSON under "result" and the result's files, by name, as ParcelFileDescriptors.
   */
  Bundle run(String capabilityId, String requestJson, in Bundle files);

  /** PenLab gave up waiting (timeout): stop working on [capabilityId] if you can. */
  void cancel(String capabilityId);
}
