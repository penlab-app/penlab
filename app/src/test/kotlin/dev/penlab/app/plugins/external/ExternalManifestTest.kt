package dev.penlab.app.plugins.external

import dev.penlab.app.plugins.ScreenPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalManifestTest {
  private fun ok(json: String) = (ManifestParser.parse(json) as ManifestParse.Ok).manifest

  private fun rejected(json: String) = (ManifestParser.parse(json) as ManifestParse.Rejected).reason

  @Test
  fun validManifestWithEveryKind() {
    val m = ok(
      """
      { "id": "dev.akram.penlab.samsung", "name": "Samsung Notes import", "apiVersion": 2,
        "capabilities": [
          { "kind": "importer", "id": "sdocx", "label": "Samsung Notes (.sdocx)", "mimeTypes": ["application/octet-stream"], "extensions": [".SDocX", "sdocx"] },
          { "kind": "pageAction", "id": "latex", "label": "To LaTeX", "icon": "@drawable/x", "selection": "required" },
          { "kind": "pageAction", "id": "clean", "label": "Clean", "selection": "none" },
          { "kind": "pageAction", "id": "any", "label": "Any" },
          { "kind": "screen", "id": "chat", "label": "Chat", "activity": "com.x.ChatActivity", "placement": "selectionMenu" },
          { "kind": "recogniser", "id": "r", "label": "Recogniser" },
          { "kind": "media", "id": "m", "label": "Recording" }
        ],
        "permissions": ["network", "microphone"] }
      """,
    )
    assertEquals("dev.akram.penlab.samsung", m.id)
    assertEquals("Samsung Notes import", m.name)
    assertEquals(listOf("network", "microphone"), m.permissions)
    assertEquals(7, m.capabilities.size)
    val importer = m.capabilities[0] as CapabilityDecl.ImporterDecl
    assertEquals(listOf("sdocx", "sdocx"), importer.extensions) // dot stripped, lower-cased
    assertEquals(SelectionRule.REQUIRED, (m.capabilities[1] as CapabilityDecl.PageActionDecl).selection)
    assertEquals(SelectionRule.NONE, (m.capabilities[2] as CapabilityDecl.PageActionDecl).selection)
    assertEquals(SelectionRule.ANY, (m.capabilities[3] as CapabilityDecl.PageActionDecl).selection)
    val screen = m.capabilities[4] as CapabilityDecl.ScreenDecl
    assertEquals("com.x.ChatActivity", screen.activity)
    assertEquals(ScreenPlacement.SELECTION_MENU, screen.placement)
    assertTrue(m.capabilities[5] is CapabilityDecl.RecogniserDecl)
    assertTrue(m.capabilities[6] is CapabilityDecl.MediaDecl)
  }

  @Test
  fun screenInputsKeepOnlyKnownNames() {
    val m = ok(
      """{"id":"a.b","name":"P","apiVersion":2,"capabilities":[
        {"kind":"screen","id":"s","label":"S","activity":".A","inputs":["page","notebook","items","hologram"]},
        {"kind":"screen","id":"t","label":"T","activity":".A"}]}""",
    )
    assertEquals(setOf("page", "notebook", "items"), (m.capabilities[0] as CapabilityDecl.ScreenDecl).inputs)
    assertTrue((m.capabilities[1] as CapabilityDecl.ScreenDecl).inputs.isEmpty())
  }

  @Test
  fun unknownKindsAreIgnoredAndTheRestStays() {
    val m = ok("""{"id":"a.b","name":"P","apiVersion":2,"capabilities":[{"kind":"hologram","id":"h","label":"H"},{"kind":"pageAction","id":"p","label":"P"}]}""")
    assertEquals(listOf("p"), m.capabilities.map { it.id })
  }

  @Test
  fun brokenCapabilitiesAreSkippedNotFatal() {
    val m = ok(
      """{"id":"a.b","name":"P","apiVersion":2,"capabilities":[
        {"kind":"pageAction","id":"no label"},
        {"kind":"pageAction","id":"bad selection","label":"X","selection":"sometimes"},
        {"kind":"screen","id":"s","label":"S"},
        {"kind":"screen","id":"s2","label":"S","activity":".A","placement":"nowhere"},
        {"kind":"pageAction","id":"dup","label":"One"},
        {"kind":"pageAction","id":"dup","label":"Two"},
        "not an object"
      ]}""",
    )
    assertEquals(listOf("dup"), m.capabilities.map { it.id })
    assertEquals("One", m.capabilities.single().label)
  }

  @Test
  fun screenPlacementDefaultsToTheMoreMenu() {
    val m = ok("""{"id":"a.b","name":"P","apiVersion":2,"capabilities":[{"kind":"screen","id":"s","label":"S","activity":".A"}]}""")
    assertEquals(ScreenPlacement.MORE_MENU, (m.capabilities.single() as CapabilityDecl.ScreenDecl).placement)
  }

  @Test
  fun wrongOrMissingApiVersionIsRejected() {
    assertTrue("needs bridge API 3" in rejected("""{"id":"a.b","name":"P","apiVersion":3,"capabilities":[]}"""))
    assertTrue("needs bridge API 1" in rejected("""{"id":"a.b","name":"P","apiVersion":1}"""))
    assertEquals("no apiVersion", rejected("""{"id":"a.b","name":"P"}"""))
  }

  @Test
  fun badIdentityOrJsonIsRejected() {
    rejected("""{"id":"../etc","name":"P","apiVersion":2}""")
    rejected("""{"id":"","name":"P","apiVersion":2}""")
    rejected("""{"id":"a.b","name":" ","apiVersion":2}""")
    rejected("not json")
  }

  @Test
  fun aPluginWithNoCapabilitiesIsStillValid() {
    assertEquals(0, ok("""{"id":"a.b","name":"P","apiVersion":2}""").capabilities.size)
  }

  @Test
  fun mediaCapabilityReadsItsPlacementAndScreen() {
    val m = ok(
      """{"id":"a.b","name":"P","apiVersion":2,"capabilities":[
        {"kind":"media","id":"record","label":"Record lecture","icon":"x","placement":"topBar","screen":"recordings"},
        {"kind":"media","id":"two","label":"Two","placement":"fileMenu"},
        {"kind":"media","id":"three","label":"Three"},
        {"kind":"media","id":"four","label":"Four","placement":"somewhereNew","screen":"not a plain id!"}
      ]}""",
    )
    val media = m.capabilities.map { it as CapabilityDecl.MediaDecl }
    assertEquals(listOf(MediaPlacement.TOP_BAR, MediaPlacement.FILE_MENU, MediaPlacement.MORE_MENU, MediaPlacement.MORE_MENU), media.map { it.placement })
    assertEquals("recordings", media[0].screenId)
    assertNull(media[1].screenId)
    assertNull(media[3].screenId) // not a plain id: dropped, the capability stays
    assertEquals("Record lecture", media[0].label)
  }

  @Test
  fun `screen placement topBar is read`() {
    val m = ok(
      """{"id":"a.b","name":"P","apiVersion":2,"capabilities":[
        {"kind":"screen","id":"hub","label":"Claude AI","activity":".A","placement":"topBar"}
      ]}""",
    )
    assertEquals(dev.penlab.app.plugins.ScreenPlacement.TOP_BAR, (m.capabilities.single() as CapabilityDecl.ScreenDecl).placement)
  }

  @Test
  fun mediaCapabilityReadsItsStartActivity() {
    val m = ok(
      """{"id":"a.b","name":"P","apiVersion":2,"capabilities":[
        {"kind":"media","id":"one","label":"One","startActivity":"com.x.StartRecordingActivity"},
        {"kind":"media","id":"two","label":"Two","startActivity":".StartRecordingActivity"},
        {"kind":"media","id":"three","label":"Three"},
        {"kind":"media","id":"four","label":"Four","startActivity":"   "},
        {"kind":"media","id":"five","label":"Five","startActivity":"not a class!"}
      ]}""",
    )
    val media = m.capabilities.map { it as CapabilityDecl.MediaDecl }
    assertEquals("com.x.StartRecordingActivity", media[0].startActivity)
    assertEquals(".StartRecordingActivity", media[1].startActivity)
    assertNull(media[2].startActivity)
    assertNull(media[3].startActivity)
    assertNull(media[4].startActivity) // dropped, the capability stays
    assertEquals(5, media.size)
  }

  @Test
  fun capabilityIconParsesValidDrawableNamesAndRejectsInvalidOnes() {
    val m = ok(
      """{"id":"a.b","name":"P","apiVersion":2,"capabilities":[
        {"kind":"pageAction","id":"p1","label":"P1","icon":"@drawable/ic_wordcount"},
        {"kind":"screen","id":"s1","label":"S1","activity":".A","icon":"ic_chat_2"},
        {"kind":"media","id":"m1","label":"M1","icon":"@layout/bad"},
        {"kind":"pageAction","id":"p2","label":"P2","icon":"../escape"},
        {"kind":"pageAction","id":"p3","label":"P3","icon":"IcUpper"},
        {"kind":"pageAction","id":"p4","label":"P4","icon":"   "}
      ]}""",
    )
    assertEquals("ic_wordcount", m.capabilities[0].icon)
    assertEquals("ic_chat_2", m.capabilities[1].icon)
    assertNull(m.capabilities[2].icon)
    assertNull(m.capabilities[3].icon)
    assertNull(m.capabilities[4].icon)
    assertNull(m.capabilities[5].icon)
    assertEquals(6, m.capabilities.size) // invalid icons are dropped without dropping the capability
  }
}
