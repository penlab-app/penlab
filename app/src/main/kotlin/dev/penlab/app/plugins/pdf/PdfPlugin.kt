package dev.penlab.app.plugins.pdf

import dev.penlab.app.ink.PdfBackground
import dev.penlab.app.notes.PdfExporter
import dev.penlab.app.plugins.AppExportSource
import dev.penlab.plugin.API_VERSION
import dev.penlab.plugin.Capability
import dev.penlab.plugin.ExportSource
import dev.penlab.plugin.Exporter
import dev.penlab.plugin.ImportSource
import dev.penlab.plugin.ImportedNotebook
import dev.penlab.plugin.ImportedPage
import dev.penlab.plugin.Importer
import dev.penlab.plugin.Plugin
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * PDF, written against the plugin API inside the app to prove the API: the library's imports
 * and the notebook's "Save / Share as PDF" go through the registry to this plugin. It adds no
 * code of its own for the work: reading uses [PdfBackground] (Android's PdfRenderer) and writing
 * uses [PdfExporter].
 */
class PdfPlugin : Plugin {
  override val id = ID
  override val name = "PDF"
  override val apiVersion = API_VERSION

  override fun capabilities(): List<Capability> = listOf(PdfImporter, PdfWriter)

  /** A PDF becomes a notebook with one page per PDF page, at the PDF's own page size, the PDF under the ink. */
  object PdfImporter : Importer {
    override val label = "PDF"
    override val mimeTypes = listOf(MIME)

    override suspend fun import(source: ImportSource): ImportedNotebook? {
      val pdf = File(source.workDir, "${UUID.randomUUID()}.pdf")
      try {
        source.open().use { input -> pdf.outputStream().use { input.copyTo(it) } }
      } catch (e: IOException) {
        return null // unreadable file: a failed import, not a broken plugin
      } catch (e: SecurityException) {
        return null
      }
      val (pages, widthMm, heightMm) = PdfBackground.inspect(pdf) ?: return null
      return ImportedNotebook(source.suggestedTitle, widthMm, heightMm, List(pages) { ImportedPage(backgroundPdfPage = it) }, pdf)
    }
  }

  /** Writes the notebook as a PDF with real page sizes, vector ink and imported PDF pages under it. */
  object PdfWriter : Exporter {
    override val label = "PDF"
    override val mimeType = MIME
    override val fileExtension = "pdf"

    override suspend fun export(source: ExportSource, out: OutputStream) {
      // The PDF writer needs the app's own strokes (exact brushes), not the plain copies.
      val app = source as? AppExportSource ?: throw IllegalArgumentException("PDF export needs the app's own notebook source")
      PdfExporter.export(app.info, app.inkStrokes, app.pageStyle, app.pdfFile, out, app.pageItems, app.imagesDir, app.dark)
    }
  }

  companion object {
    const val ID = "dev.penlab.pdf"
    const val MIME = "application/pdf"
  }
}
