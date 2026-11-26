package dev.penlab.app.plugins.external

import dev.penlab.app.plugins.PluginRegistry
import dev.penlab.sdk.PageInfo
import dev.penlab.sdk.PenLabBridge
import dev.penlab.sdk.PluginRequest
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A media plugin as PenLab hosts it: found by the registry, and the command / seek wire format it is sent. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExternalMediaTest {
  @get:Rule val tmp = TemporaryFolder()

  private val manifest =
    (ManifestParser.parse(
      """{"id":"acme.rec","name":"Acme recorder","apiVersion":2,"capabilities":[
        {"kind":"media","id":"record","label":"Record lecture","placement":"topBar"},
        {"kind":"pageAction","id":"count","label":"Count"}
      ]}""",
    ) as ManifestParse.Ok).manifest

  private class Seen(val capability: String, val json: String, val timeoutMs: Long)

  private fun registry(messages: MutableList<String> = ArrayList(), transport: PluginTransport): PluginRegistry {
    val r = PluginRegistry(emptyList()) { messages += it }
    r.replaceExternal(listOf(ExternalPlugin(manifest, "acme.pkg", transport, ExternalEnv(File(tmp.root, "io")))))
    return r
  }

  private val none = """{"type":"none"}"""

  @Test
  fun theRegistryOffersTheMediaCapabilityWithItsPackage() {
    val r = registry { _, _, _, _ -> TransportOutcome.Ok(none, ResultFiles.EMPTY) }
    val entry = r.mediaPlugins().single()
    assertEquals("acme.rec", entry.plugin.id)
    assertEquals("acme.pkg", entry.capability.packageName) // what MediaHostService checks the caller against
    assertEquals(MediaPlacement.TOP_BAR, entry.capability.placement)
    assertTrue(entry.external)
    // A media capability is not a page action: it is in no menu of plugin actions.
    assertTrue(r.pageActions(true).none { it.capability.id == "record" } && r.pageActions(false).none { it.capability.id == "record" })
    assertEquals(listOf("count"), r.pageActions(true).map { it.capability.id }.filter { it == "count" })
  }

  @Test
  fun aSwitchedOffPluginIsNoLongerOffered() = runTest {
    val r = registry { _, _, _, _ -> TransportOutcome.Failed(FailureKind.DIED, "gone") }
    val entry = r.mediaPlugins().single()
    r.guarded(entry.plugin) { entry.capability.command(PenLabBridge.MEDIA_STOP) }
    assertTrue(r.mediaPlugins().isEmpty()) // so the host service's caller list is empty for it too
  }

  @Test
  fun aCommandIsSentAsMediaCommandWithTheContractKey() = runTest {
    var seen: Seen? = null
    val r = registry { cap, json, files, timeout ->
      seen = Seen(cap, json, timeout)
      assertTrue(files.keySet().isNullOrEmpty())
      TransportOutcome.Ok(none, ResultFiles.EMPTY)
    }
    val entry = r.mediaPlugins().single()
    for (command in listOf("start", "pause", "resume", "stop")) {
      assertNull(entry.capability.command(command))
      val s = seen!!
      assertEquals("media:command", s.capability)
      assertEquals(PluginTimeouts.MEDIA_MS, s.timeoutMs)
      val o = JSONObject(s.json)
      assertEquals(command, o.getString("command")) // {"command": "start"|...} as the contract says
      assertEquals("media:command", o.getString("capabilityId"))
      assertEquals(command, PluginRequest.fromJson(s.json).media!!.command)
    }
  }

  @Test
  fun aCommandCarriesTheOpenNotebookInPage() = runTest {
    var json: String? = null
    val r = registry { _, j, _, _ ->
      json = j
      TransportOutcome.Ok(none, ResultFiles.EMPTY)
    }
    val entry = r.mediaPlugins().single()
    entry.capability.command("start", PageInfo("nb-42", 1, 210f, 297f, darkPage = true))
    val page = JSONObject(json!!).getJSONObject("page")
    assertEquals("nb-42", page.getString("notebookId"))
    assertEquals("nb-42", PluginRequest.fromJson(json!!).page!!.notebookId)
    entry.capability.command("stop") // no page given: none is sent
    assertFalse(JSONObject(json!!).has("page"))
  }

  @Test
  fun aSeekCarriesTheRecordingIndex() = runTest {
    var json: String? = null
    val r = registry { _, j, _, _ ->
      json = j
      TransportOutcome.Ok(none, ResultFiles.EMPTY)
    }
    r.mediaPlugins().single().capability.seek("Recordings/Graphite/x.m4a", 4_000, recordingIndex = 3)
    val o = JSONObject(json!!)
    assertEquals(3, o.getInt("recordingIndex"))
    assertEquals("Recordings/Graphite/x.m4a", o.getString("ref"))
    assertEquals(4_000L, o.getLong("positionMs"))
  }

  @Test
  fun onlyMediaCallsAreBoundWithCapabilities() {
    val include = android.content.Context.BIND_INCLUDE_CAPABILITIES
    assertTrue(bindFlagsFor("media:command") and include != 0)
    assertTrue(bindFlagsFor("media:seek") and include != 0)
    assertTrue(bindFlagsFor("media:command") and android.content.Context.BIND_AUTO_CREATE != 0)
    assertEquals(0, bindFlagsFor("count") and include)
    assertEquals(0, bindFlagsFor("record") and include) // a capability id merely resembling media is not one
    assertTrue(bindFlagsFor("count") and android.content.Context.BIND_AUTO_CREATE != 0)
  }

  @Test
  fun aSeekCarriesTheRefAndPosition() = runTest {
    var seen: Seen? = null
    val r = registry { cap, json, _, _ ->
      seen = Seen(cap, json, 0)
      TransportOutcome.Ok(none, ResultFiles.EMPTY)
    }
    val entry = r.mediaPlugins().single()
    assertNull(entry.capability.seek("content://rec/9", 61_500))
    val s = seen!!
    assertEquals("media:seek", s.capability)
    val o = JSONObject(s.json)
    assertEquals("content://rec/9", o.getString("ref"))
    assertEquals(61_500L, o.getLong("positionMs"))
  }

  @Test
  fun aMessageOrErrorFromThePluginComesBackForTheUserAndKeepsItOn() = runTest {
    val r = registry { _, _, _, _ -> TransportOutcome.Ok("""{"type":"error","message":"Microphone is busy"}""", ResultFiles.EMPTY) }
    val entry = r.mediaPlugins().single()
    val call = r.guarded(entry.plugin) { entry.capability.command("start") }
    assertEquals("Microphone is busy", (call as PluginRegistry.Call.Ok).value)
    assertTrue(r.isEnabled("acme.rec"))
  }

  @Test
  fun aBrokenAnswerOrADeadPluginSwitchesItOffWithOneMessage() = runTest {
    val messages = ArrayList<String>()
    val r = registry(messages) { _, _, _, _ -> TransportOutcome.Ok("""{"type":"banana"}""", ResultFiles.EMPTY) }
    val entry = r.mediaPlugins().single()
    val call = r.guarded(entry.plugin) { entry.capability.command("pause") }
    assertTrue(call is PluginRegistry.Call.Failed && call.pluginDisabled)
    assertFalse(r.isEnabled("acme.rec"))
    assertEquals(1, messages.size)
  }
}
