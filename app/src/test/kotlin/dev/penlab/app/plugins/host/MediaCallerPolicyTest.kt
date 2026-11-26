package dev.penlab.app.plugins.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Who may call the exported media service: only a hosted media plugin, and only as itself. */
class MediaCallerPolicyTest {
  private val audio = MediaPluginRef("dev.akram.penlab.audio", "dev.akram.penlab.audio")
  private val other = MediaPluginRef("com.other.rec", "com.other.pkg")
  private val hosted = listOf(audio, other)

  @Test
  fun aHostedPluginMayCallAsItself() {
    assertEquals(audio, MediaCallerPolicy.resolve(listOf("dev.akram.penlab.audio"), "dev.akram.penlab.audio", hosted))
    // A UID can hold several packages (shared UID): any of them may be the plugin's.
    assertEquals(other, MediaCallerPolicy.resolve(listOf("x.y", "com.other.pkg"), "com.other.rec", hosted))
  }

  @Test
  fun anEnabledPluginCannotSpeakAsAnother() {
    assertNull(MediaCallerPolicy.resolve(listOf("com.other.pkg"), "dev.akram.penlab.audio", hosted))
  }

  @Test
  fun anAppThatIsNotAPluginIsRefused() {
    assertNull(MediaCallerPolicy.resolve(listOf("dev.evil.app"), "dev.akram.penlab.audio", hosted))
    assertNull(MediaCallerPolicy.resolve(listOf("dev.evil.app"), "dev.evil.app", hosted))
  }

  @Test
  fun aPluginThatIsOffOrCrashedIsNotInTheListAndIsRefused() {
    assertNull(MediaCallerPolicy.resolve(listOf("dev.akram.penlab.audio"), "dev.akram.penlab.audio", emptyList()))
    assertNull(MediaCallerPolicy.resolve(listOf("dev.akram.penlab.audio"), "dev.akram.penlab.audio", listOf(other)))
  }

  @Test
  fun anUnknownCallerWithNoVisiblePackageIsRefused() {
    assertNull(MediaCallerPolicy.resolve(emptyList(), "dev.akram.penlab.audio", hosted))
  }

  @Test
  fun aPluginIdThatIsAPrefixOrCaseVariantDoesNotMatch() {
    assertNull(MediaCallerPolicy.resolve(listOf("dev.akram.penlab.audio"), "dev.akram.penlab", hosted))
    assertNull(MediaCallerPolicy.resolve(listOf("dev.akram.penlab.audio"), "DEV.AKRAM.PENLAB.AUDIO", hosted))
  }
}
