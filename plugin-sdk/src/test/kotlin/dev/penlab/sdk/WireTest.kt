package dev.penlab.sdk

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

  private fun ink(layer: Int = -1) = SdkStroke(floatArrayOf(1f, 2f), floatArrayOf(3f, 4f), floatArrayOf(0.5f, 0.6f), longArrayOf(0, 8), "PEN", 0xFF000000.toInt(), 0.4f, StrokeFamily.BALLPOINT, layer)

  @Test
  fun itemsResultCarriesStrokesAsOneFile() {
    val encoded = ResultCodec.encode(PluginResult.Items(listOf(ResultItem.text("t", 0f, 0f, 1f, 1f)), listOf(ink(), ink(1))))
    assertTrue(PenLabBridge.FILE_RESULT_STROKES in encoded.files)
    val decoded = ResultCodec.decode(encoded.json) as PluginResult.Items
    assertEquals(PenLabBridge.FILE_RESULT_STROKES, decoded.strokesName)
    val strokes = StrokesBinary.decode((encoded.files.getValue(decoded.strokesName!!) as ResultFile.Bytes).bytes)
    assertEquals(listOf(StrokeFamily.BALLPOINT, StrokeFamily.BALLPOINT), strokes.map { it.family })
    assertEquals(listOf(-1, 1), strokes.map { it.layer })
  }

  @Test
  fun strokesOnlyResultHasNoOtherFiles() {
    val encoded = ResultCodec.encode(PluginResult.Items(emptyList(), listOf(ink())))
    assertEquals(setOf(PenLabBridge.FILE_RESULT_STROKES), encoded.files.keys)
    assertNull((ResultCodec.decode(ResultCodec.encode(PluginResult.Items(listOf(ResultItem.text("t", 0f, 0f, 1f, 1f)))).json) as PluginResult.Items).strokesName)
  }

  @Test
  fun notebookStrokesTravelAsPerPageSectionsInOneFile() {
    val nb = NotebookResult("N", 210f, 297f, listOf(NotebookPage(-1, emptyList(), listOf(ink(), ink())), NotebookPage(-1), NotebookPage(-1, emptyList(), listOf(ink(3)))))
    val encoded = ResultCodec.encode(PluginResult.Notebook(nb))
    assertEquals(1, encoded.files.size)
    val back = (ResultCodec.decode(encoded.json) as PluginResult.Notebook).notebook
    assertEquals(PenLabBridge.FILE_RESULT_STROKES, back.strokesName)
    val sections = StrokesBinary.decodeSections((encoded.files.getValue(back.strokesName!!) as ResultFile.Bytes).bytes)
    assertEquals(listOf(2, 0, 1), sections.map { it.size })
    assertEquals(3, sections[2][0].layer)
  }

  @Test
  fun requestAdvertisesTheStrokesFormat() {
    assertEquals(StrokesBinary.VERSION, PluginRequest.fromJson(PluginRequest("x", strokesFormat = StrokesBinary.VERSION).toJson()).strokesFormat)
    assertEquals(0, PluginRequest.fromJson("{\"capabilityId\":\"x\"}").strokesFormat) // an older PenLab says nothing
  }

  @Test
  fun selectionStrokesJsonKeepsFamilyAndLayer() {
    val back = StrokesJson.decode(StrokesJson.encode(listOf(ink(2))))[0]
    assertEquals(StrokeFamily.BALLPOINT, back.family)
    assertEquals(2, back.layer)
    // an older PenLab's strokes.json has neither: the family comes from the tool
    val old = StrokesJson.decode("{\"strokes\":[{\"tool\":\"HIGHLIGHTER\",\"argb\":1,\"widthMm\":2,\"xs\":[0],\"ys\":[0],\"pressures\":[0],\"times\":[0]}]}")[0]
    assertEquals(StrokeFamily.HIGHLIGHTER, old.family)
    assertEquals(-1, old.layer)
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

  private val photo = SelectionItem("9b1c", PenLabBridge.TYPE_IMAGE, floatArrayOf(10f, 20f, 110f, 80f), 20f, "item-0", "image/jpeg")
  private val note = SelectionItem("4e07", PenLabBridge.TYPE_TEXT, floatArrayOf(0f, 0f, 5f, 5f), text = "héllo")

  @Test
  fun requestWithItemsRoundTrips() {
    val selection = SelectionInfo(floatArrayOf(0f, 0f, 120f, 90f), 0, 2, PenLabBridge.FILE_SELECTION_IMAGE, PenLabBridge.FILE_STROKES, listOf(photo, note))
    val back = PluginRequest.fromJson(PluginRequest("cutout", selection = selection, itemsFormat = PenLabBridge.ITEMS_FORMAT).toJson())
    assertEquals(1, back.itemsFormat)
    assertTrue(back.canReplaceItems)
    val items = back.selectionItems
    assertEquals(listOf("9b1c", "4e07"), items.map { it.id })
    assertEquals(20f, items[0].rotationDeg, 0f)
    assertArrayEquals(floatArrayOf(10f, 20f, 110f, 80f), items[0].boundsMm, 0f)
    assertEquals("item-0", items[0].file)
    assertEquals("image/jpeg", items[0].mimeType)
    assertTrue(items[0].isPicture)
    assertNull(items[1].file)
    assertEquals("héllo", items[1].text)
    assertFalse(items[1].isPicture)
  }

  @Test
  fun anOlderPenLabSendsNoItems() {
    val back = PluginRequest.fromJson("""{"apiVersion":2,"capabilityId":"x","selection":{"boundsMm":[0,0,1,1],"strokeCount":0,"itemCount":1}}""")
    assertEquals(0, back.itemsFormat)
    assertFalse(back.canReplaceItems)
    assertTrue(back.selectionItems.isEmpty())
    // and a request without items does not write an empty array
    assertFalse(PluginRequest("x", selection = SelectionInfo(floatArrayOf(0f, 0f, 1f, 1f), 0, 0)).toJson().contains("\"items\""))
  }

  @Test
  fun selectionItemsThisSdkCannotReadAreSkipped() {
    val back = PluginRequest.fromJson(
      """{"capabilityId":"x","itemsFormat":1,"selection":{"boundsMm":[0,0,1,1],"strokeCount":0,"itemCount":4,"items":[
        {"id":"a"},{"id":"b","typeId":"penlab.image","boundsMm":[1,2,3]},5,{"id":"c","typeId":"penlab.image","boundsMm":[0,0,4,4]}]}}""",
    )
    assertEquals(listOf("c"), back.selectionItems.map { it.id })
    assertEquals(0f, back.selectionItems[0].rotationDeg, 0f)
  }

  @Test
  fun resultItemReplacesAndRotationRoundTrip() {
    val encoded = ResultCodec.encode(PluginResult.Items(listOf(ResultItem.image(byteArrayOf(1, 2), 1f, 2f, 3f, 4f, rotationDeg = 20f, replaces = "9b1c"), ResultItem.text("t", 0f, 0f, 1f, 1f, replaces = "4e07"))))
    val items = (ResultCodec.decode(encoded.json) as PluginResult.Items).items
    assertEquals("9b1c", items[0].replaces)
    assertEquals(20f, items[0].rotationDeg, 0f)
    assertEquals("4e07", items[1].replaces)
    assertEquals(0f, items[1].rotationDeg, 0f)
    assertEquals("item-0", items[0].file)
  }

  @Test
  fun resultItemWithoutTheNewFieldsReadsAsDefaultsAndWritesNothingExtra() {
    val plain = ResultCodec.encode(PluginResult.Items(listOf(ResultItem.text("t", 0f, 0f, 1f, 1f)))).json
    assertFalse(plain.contains("replaces"))
    assertFalse(plain.contains("rotationDeg"))
    val item = (ResultCodec.decode("""{"type":"items","items":[{"typeId":"penlab.text","boundsMm":[0,0,1,1],"text":"t"}]}""") as PluginResult.Items).items.single()
    assertNull(item.replaces)
    assertEquals(0f, item.rotationDeg, 0f)
  }

  @Test
  fun aNonFiniteTurnIsWrittenAndReadAsNone() {
    val json = ResultCodec.encode(PluginResult.Items(listOf(ResultItem.text("t", 0f, 0f, 1f, 1f, rotationDeg = Float.NaN)))).json
    assertEquals(0f, (ResultCodec.decode(json) as PluginResult.Items).items.single().rotationDeg, 0f)
  }
}
