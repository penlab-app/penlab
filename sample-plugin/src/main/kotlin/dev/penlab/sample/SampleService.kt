package dev.penlab.sample

import dev.penlab.sdk.PenLabPluginService
import dev.penlab.sdk.PluginCall
import dev.penlab.sdk.PluginResult

/**
 * The plugin's service: PenLab binds to it and calls [onRun] for the page actions listed in
 * res/raw/penlab_plugin.json. Screens (see [HelloActivity]) do not come through here.
 */
class SampleService : PenLabPluginService() {
  override fun onRun(call: PluginCall): PluginResult =
    when (call.capabilityId) {
      "wordcount" -> {
        // "selection": "required" in the manifest means PenLab only offers this with ink selected.
        val strokes = call.strokes()
        val points = strokes.sumOf { it.pointCount }
        PluginResult.Text(resources.getQuantityString(R.plurals.stroke_count, strokes.size, strokes.size, points))
      }
      else -> PluginResult.Error("Unknown capability ${call.capabilityId}")
    }
}
