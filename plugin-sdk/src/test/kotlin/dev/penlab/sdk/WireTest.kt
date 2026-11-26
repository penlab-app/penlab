package dev.penlab.sdk

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WireTest {
  @Test
  fun requestRoundTrips() {
    val request = PluginRequest(
      "wordcount",
      PageInfo("nb-1", 2, 210f, 297f, darkPage = true),
      SelectionInfo(floatArrayOf(10f, 20f, 30.5f, 40f), 3, 1, PenLabBridge.FILE_SELECTION_IMAGE, PenLabBridge.FILE_STROKES),
      SourceInfo("a.sdocx", "application/octet-stream", "a", PenLabBridge.FILE_SOURCE),
    )
    val back = PluginRequest.fromJson(request.toJson())
    assertEquals("wordcount", back.capabilityId)
    assertEquals(2, back.page!!.pageIndex)
    assertTrue(back.page!!.darkPage)
    assertArrayEquals(floatArrayOf(10f, 20f, 30.5f, 40f), back.selection!!.boundsMm, 0f)
    assertEquals(3, back.selection!!.strokeCount)
    assertEquals(PenLabBridge.FILE_STROKES, back.selection!!.strokesFile)
    assertEquals("a", back.source!!.suggestedTitle)
  }

  @Test
  fun requestWithoutOptionalPartsReadsAsNulls() {
    val back = PluginRequest.fromJson(PluginRequest("x").toJson())
    assertNull(back.page)
    assertNull(back.selection)
    assertNull(back.source)
  }

  @Test
  fun malformedRequestIsAWireFormatException() {
    try {
      PluginRequest.fromJson("{\"nothing\":1}")
      fail()
    } catch (e: WireFormatException) {
      // expected
    }
  }

  @Test
  fun strokesRoundTrip() {
    val s = SdkStroke(floatArrayOf(1f, 2.5f), floatArrayOf(3f, Float.NaN), floatArrayOf(0.5f, -1f), longArrayOf(0, 16), "PEN", 0xFF112233.toInt(), 0.4f)
    val back = StrokesJson.decode(StrokesJson.encode(listOf(s, s)))
    assertEquals(2, back.size)
    assertEquals(2, back[0].pointCount)
    assertArrayEquals(floatArrayOf(1f, 2.5f), back[0].xs, 0f)
    assertArrayEquals(floatArrayOf(3f, 0f), back[0].ys, 0f) // NaN is sent as 0: JSON has no NaN
    assertArrayEquals(longArrayOf(0, 16), back[0].times)
    assertEquals(0xFF112233.toInt(), back[0].argb)
    assertEquals(0, StrokesJson.decode(StrokesJson.encode(emptyList())).size)
  }

  @Test
  fun itemsResultCarriesDataAsNamedFiles() {
    val png = byteArrayOf(1, 2, 3)
    val encoded = ResultCodec.encode(PluginResult.Items(listOf(ResultItem.image(png, 0f, 0f, 20f, 10f, ink = true), ResultItem.text("hi", 1f, 2f, 3f, 4f))))
    assertEquals(1, encoded.files.size) // only the picture is a file; text stays in the JSON
    val decoded = ResultCodec.decode(encoded.json) as PluginResult.Items
    assertEquals(PenLabBridge.TYPE_INK_IMAGE, decoded.items[0].typeId)
    assertEquals(encoded.files.keys.single(), decoded.items[0].file)
    assertArrayEquals(png, (encoded.files.values.single() as ResultFile.Bytes).bytes)
    assertEquals("hi", decoded.items[1].text)
    assertNull(decoded.items[1].file)
    assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f), decoded.items[1].boundsMm, 0f)
  }

  @Test
  fun simpleResultsRoundTrip() {
    assertEquals("t", (ResultCodec.decode(ResultCodec.encode(PluginResult.Text("t")).json) as PluginResult.Text).text)
    assertEquals("m", (ResultCodec.decode(ResultCodec.encode(PluginResult.Message("m")).json) as PluginResult.Message).message)
    assertEquals("e", (ResultCodec.decode(ResultCodec.encode(PluginResult.Error("e")).json) as PluginResult.Error).message)
    assertTrue(ResultCodec.decode(ResultCodec.encode(PluginResult.None).json) === PluginResult.None)
  }

  @Test
  fun notebookResultRoundTrips() {
    val nb = NotebookResult(
      "Notes", 210f, 297f,
      listOf(NotebookPage(0, emptyList()), NotebookPage(-1, listOf(ResultItem.text("x", 0f, 0f, 5f, 5f)))),
      backgroundPdf = java.io.File("/tmp/x.pdf"),
    )
    val encoded = ResultCodec.encode(PluginResult.Notebook(nb))
    assertTrue(PenLabBridge.FILE_PDF in encoded.files)
    val back = (ResultCodec.decode(encoded.json) as PluginResult.Notebook).notebook
    assertEquals("Notes", back.title)
    assertEquals(PenLabBridge.FILE_PDF, back.backgroundPdfName)
    assertEquals(2, back.pages.size)
    assertEquals(0, back.pages[0].backgroundPdfPage)
    assertEquals("x", back.pages[1].items.single().text)
  }

  @Test
  fun unknownResultTypeAndBadBoundsAreRejected() {
    for (json in listOf("{\"type\":\"banana\"}", "{\"type\":\"items\",\"items\":[{\"typeId\":\"a\",\"boundsMm\":[1,2,3]}]}", "not json")) {
      try {
        ResultCodec.decode(json)
        fail(json)
      } catch (e: WireFormatException) {
        // expected
      }
    }
  }
}
