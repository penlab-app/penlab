package dev.penlab.app.plugins.external

import dev.penlab.sdk.NotebookPage
import dev.penlab.sdk.NotebookResult
import dev.penlab.sdk.PenLabBridge
import dev.penlab.sdk.PluginResult as Wire
import dev.penlab.sdk.ResultItem
import dev.penlab.sdk.SdkStroke
import dev.penlab.sdk.StrokeFamily
import dev.penlab.sdk.StrokesBinary
import dev.penlab.sdk.WireFormatException
import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import dev.penlab.plugin.Result as V1

class ResultMapperTest {
  @get:Rule val tmp = TemporaryFolder()

  private class Files(val map: Map<String, ByteArray> = emptyMap()) : ResultFiles {
    override fun readBytes(name: String, maxBytes: Long): ByteArray? = map[name]?.also { if (it.size > maxBytes) throw IOException("too big") }
    override fun copyTo(name: String, target: File): Boolean = map[name]?.let { target.writeBytes(it); true } ?: false
    override fun close() {}
  }

  private fun items(wire: Wire.Items, files: Files = Files()) = (ResultMapper.toResult(wire, files, "P") as V1.Items).items

  @Test
  fun textMessageNoneMapDirectly() {
    assertEquals("t", (ResultMapper.toResult(Wire.Text("t"), Files(), "P") as V1.Text).text)
    assertEquals("m", (ResultMapper.toResult(Wire.Message("m"), Files(), "P") as V1.Message).message)
    assertTrue(ResultMapper.toResult(Wire.None, Files(), "P") === V1.None)
  }

  @Test
  fun aPluginReportedErrorIsAMessageNotACrash() {
    val result = ResultMapper.toResult(Wire.Error("no network"), Files(), "LaTeX") as V1.Message
    assertEquals("“LaTeX”: no network", result.message)
  }

  @Test
  fun imageItemsKeepTheirBaseTypeAndBytes() {
    val png = byteArrayOf(9, 8, 7)
    val out = items(Wire.Items(listOf(ResultItem(PenLabBridge.TYPE_INK_IMAGE, floatArrayOf(0f, 0f, 10f, 5f), file = "item-0"), ResultItem(PenLabBridge.TYPE_IMAGE, floatArrayOf(0f, 0f, 1f, 1f), file = "item-1"))), Files(mapOf("item-0" to png, "item-1" to png)))
    assertEquals(listOf("penlab.ink-image", "penlab.image"), out.map { it.typeId })
    assertArrayEquals(png, out[0].payload)
  }

  @Test
  fun textItemCarriesUtf8AndOwnTypesPassThrough() {
    val out = items(Wire.Items(listOf(ResultItem.text("héllo", 0f, 0f, 5f, 5f), ResultItem("acme.thing", floatArrayOf(0f, 0f, 1f, 1f)))))
    assertEquals("penlab.text", out[0].typeId)
    assertEquals("héllo", out[0].payload.toString(Charsets.UTF_8))
    assertEquals("acme.thing", out[1].typeId)
    assertEquals(0, out[1].payload.size)
  }

  @Test
  fun anEmptyItemListIsEmpty() {
    assertEquals(0, items(Wire.Items(emptyList())).size)
  }

  @Test
  fun missingOrOversizedOrEmptyPicturesAreWireErrors() {
    val pic = ResultItem(PenLabBridge.TYPE_IMAGE, floatArrayOf(0f, 0f, 1f, 1f), file = "item-0")
    for (files in listOf(Files(), Files(mapOf("item-0" to ByteArray(0))), Files(mapOf("item-0" to ByteArray((ResultMapper.MAX_PICTURE_BYTES + 1).toInt()))))) {
      try {
        items(Wire.Items(listOf(pic)), files)
        fail()
      } catch (e: WireFormatException) {
        // expected
      }
    }
    try {
      items(Wire.Items(listOf(ResultItem(PenLabBridge.TYPE_IMAGE, floatArrayOf(0f, 0f, 1f, 1f))))) // a picture with no file at all
      fail()
    } catch (e: WireFormatException) {
      // expected
    }
  }

  @Test
  fun anActionAnsweringWithANotebookIsAWireError() {
    try {
      ResultMapper.toResult(Wire.Notebook(NotebookResult("n", 1f, 1f, emptyList())), Files(), "P")
      fail()
    } catch (e: WireFormatException) {
      // expected
    }
  }

  @Test
  fun notebookMapsPagesAndCopiesThePdf() {
    val wire = Wire.Notebook(
      NotebookResult(
        "Notes", 210f, 297f,
        listOf(NotebookPage(0), NotebookPage(1, listOf(ResultItem.text("x", 0f, 0f, 5f, 5f)))),
        backgroundPdfName = "pdf",
      )
    )
    val work = tmp.newFolder()
    val nb = ResultMapper.toNotebook(wire, Files(mapOf("pdf" to byteArrayOf(1, 2))), work)!!
    assertEquals("Notes", nb.title)
    assertEquals(210f, nb.pageWidthMm, 0f)
    assertEquals(listOf(0, 1), nb.pages.map { it.backgroundPdfPage })
    assertEquals(1, nb.pages[1].items.size)
    assertNotNull(nb.backgroundPdf)
    assertEquals(work, nb.backgroundPdf!!.parentFile)
    assertArrayEquals(byteArrayOf(1, 2), nb.backgroundPdf!!.readBytes())
  }

  @Test
  fun withoutAPdfPagesShowPlainPaper() {
    val nb = ResultMapper.toNotebook(Wire.Notebook(NotebookResult("n", 100f, 100f, listOf(NotebookPage(3)))), Files(), tmp.newFolder())!!
    assertNull(nb.backgroundPdf)
    assertEquals(-1, nb.pages.single().backgroundPdfPage)
  }

  @Test
  fun cannotReadMeIsNullNotACrash() {
    assertNull(ResultMapper.toNotebook(Wire.Error("not a Samsung note"), Files(), tmp.newFolder()))
    assertNull(ResultMapper.toNotebook(Wire.None, Files(), tmp.newFolder()))
  }

  @Test
  fun anImporterAnsweringNonsenseOrWithAMissingPdfIsAWireError() {
    for (wire in listOf(Wire.Text("hi"), Wire.Notebook(NotebookResult("n", 1f, 1f, emptyList(), backgroundPdfName = "pdf")))) {
      try {
        ResultMapper.toNotebook(wire, Files(), tmp.newFolder())
        fail()
      } catch (e: WireFormatException) {
        // expected
      }
    }
  }

  private fun ink(family: StrokeFamily, layer: Int = -1) =
    SdkStroke(floatArrayOf(1f, 2f), floatArrayOf(3f, 4f), floatArrayOf(0.5f, 0.6f), longArrayOf(0, 8), if (family == StrokeFamily.HIGHLIGHTER) "HIGHLIGHTER" else "PEN", 0xFF102030.toInt(), 0.4f, family, layer)

  @Test
  fun anItemsResultCarriesItsStrokes() {
    val file = StrokesBinary.encode(listOf(ink(StrokeFamily.FOUNTAIN, 1), ink(StrokeFamily.HIGHLIGHTER)))
    val result = ResultMapper.toResult(Wire.Items(emptyList(), strokesName = "strokes.bin"), Files(mapOf("strokes.bin" to file)), "P") as V1.Items
    assertEquals(listOf("fountain", "highlighter"), result.strokes.map { it.family })
    assertEquals(listOf(dev.penlab.plugin.Tool.PEN, dev.penlab.plugin.Tool.HIGHLIGHTER), result.strokes.map { it.tool })
    assertEquals(listOf(1, -1), result.strokes.map { it.layer })
    assertEquals(0xFF102030.toInt(), result.strokes[0].argb)
    assertEquals(0.4f, result.strokes[0].widthMm, 0f)
  }

  @Test
  fun notebookStrokesLandOnTheirPages() {
    val file = StrokesBinary.encodeSections(listOf(listOf(ink(StrokeFamily.PRESSURE)), emptyList(), listOf(ink(StrokeFamily.BALLPOINT), ink(StrokeFamily.BALLPOINT))))
    val wire = Wire.Notebook(NotebookResult("n", 210f, 297f, listOf(NotebookPage(), NotebookPage(), NotebookPage()), strokesName = "strokes.bin"))
    val nb = ResultMapper.toNotebook(wire, Files(mapOf("strokes.bin" to file)), tmp.newFolder())!!
    assertEquals(listOf(1, 0, 2), nb.pages.map { it.strokes.size })
    assertEquals("ballpoint", nb.pages[2].strokes[0].family)
  }

  @Test
  fun fewerStrokeSectionsThanPagesIsFine() {
    val file = StrokesBinary.encodeSections(listOf(listOf(ink(StrokeFamily.PRESSURE))))
    val nb = ResultMapper.toNotebook(Wire.Notebook(NotebookResult("n", 1f, 1f, listOf(NotebookPage(), NotebookPage()), strokesName = "s")), Files(mapOf("s" to file)), tmp.newFolder())!!
    assertEquals(listOf(1, 0), nb.pages.map { it.strokes.size })
  }

  @Test
  fun missingMalformedOrTooManyStrokeSectionsAreWireErrors() {
    val twoSections = StrokesBinary.encodeSections(listOf(emptyList(), emptyList()))
    val cases =
      listOf<() -> Any?>(
        { ResultMapper.toResult(Wire.Items(emptyList(), strokesName = "s"), Files(), "P") }, // named, not sent
        { ResultMapper.toResult(Wire.Items(emptyList(), strokesName = "s"), Files(mapOf("s" to byteArrayOf(1, 2, 3))), "P") }, // not a strokes file
        { ResultMapper.toNotebook(Wire.Notebook(NotebookResult("n", 1f, 1f, listOf(NotebookPage()), strokesName = "s")), Files(mapOf("s" to twoSections)), tmp.newFolder()) }, // 2 pages of ink for 1 page
      )
    for (case in cases) {
      try {
        case()
        fail()
      } catch (e: WireFormatException) {
        // expected
      }
    }
  }

  @Test
  fun anOversizedStrokesFileIsAWireError() {
    val big = object : ResultFiles {
      override fun readBytes(name: String, maxBytes: Long): ByteArray? = throw IOException("too big")
      override fun copyTo(name: String, target: File) = false
      override fun close() {}
    }
    try {
      ResultMapper.toResult(Wire.Items(emptyList(), strokesName = "s"), big, "P")
      fail()
    } catch (e: WireFormatException) {
      // expected
    }
  }
}
