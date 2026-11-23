package dev.penlab.app.plugins

import dev.penlab.app.plugins.pdf.PdfPlugin
import dev.penlab.plugin.Plugin

/**
 * The plugins built into this app, listed by hand: add a line here to add a plugin. No
 * reflection, no scanning, so what runs is what you can read.
 */
object BuiltInPlugins {
  fun all(): List<Plugin> = listOf(
    PdfPlugin(),
  )
}
