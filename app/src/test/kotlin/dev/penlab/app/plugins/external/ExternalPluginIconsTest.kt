package dev.penlab.app.plugins.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalPluginIconsTest {
  @Test
  fun parseIconNameAcceptsDrawablePrefixAndBareValidNames() {
    assertEquals("ic_wordcount", parseIconName("@drawable/ic_wordcount"))
    assertEquals("ic_a_1", parseIconName("  ic_a_1  "))
    assertEquals("a", parseIconName("a"))
  }

  @Test
  fun parseIconNameRejectsBlankWrongPrefixPathsAndUppercase() {
    assertNull(parseIconName(null))
    assertNull(parseIconName(""))
    assertNull(parseIconName("   "))
    assertNull(parseIconName("@drawable/"))
    assertNull(parseIconName("@layout/ic_foo"))
    assertNull(parseIconName("../ic_foo"))
    assertNull(parseIconName("ic_Foo"))
    assertNull(parseIconName("1_ic"))
    assertNull(parseIconName("a".repeat(129)))
  }

  @Test
  fun boundedSizeHandlesDegenerateSmallerAndOversizedDimensions() {
    assertEquals(96 to 96, ExternalPluginIcons.boundedSize(-1, 48))
    assertEquals(96 to 96, ExternalPluginIcons.boundedSize(48, 0))
    assertEquals(48 to 48, ExternalPluginIcons.boundedSize(48, 48))
    assertEquals(96 to 48, ExternalPluginIcons.boundedSize(200, 100))
    assertEquals(48 to 96, ExternalPluginIcons.boundedSize(100, 200))
    assertEquals(96 to 1, ExternalPluginIcons.boundedSize(1000, 1))
  }
}
