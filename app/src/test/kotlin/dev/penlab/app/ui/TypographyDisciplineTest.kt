package dev.penlab.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-scanning guard for README invariant 4.8 (token and craft discipline): font sizes are
 * written only in `ui/theme` (Type.kt and friends). A composable that needs a new text size adds
 * a style to `PenLabTextStyles` instead of writing `fontSize = 12.sp` inline.
 *
 * Plain JVM test: Gradle runs unit tests with the module directory (`app/`) as working directory,
 * so the source tree is read straight from disk.
 */
class TypographyDisciplineTest {
  private val literalSp = Regex("""\.sp\b""")

  @Test
  fun noLiteralSpOutsideTheTheme() {
    val root = File("src/main/kotlin")
    assertTrue("Run from the app/ module directory: ${root.absolutePath} not found", root.isDirectory)
    val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    assertTrue("Expected to scan the app sources", files.size > 20)

    val offenders =
      files
        .filterNot { it.invariantSeparatorsPath.contains("/ui/theme/") }
        .flatMap { file ->
          file.readLines().mapIndexedNotNull { index, line -> if (hasLiteralSp(line)) "${file.path}:${index + 1}: ${line.trim()}" else null }
        }

    assertEquals("Literal .sp outside ui/theme (add a PenLabTextStyles entry instead):\n" + offenders.joinToString("\n"), 0, offenders.size)
  }

  @Test
  fun scannerRecognisesLiteralSp() {
    assertTrue(hasLiteralSp("    fontSize = 12.sp,"))
    assertTrue(hasLiteralSp("    letterSpacing = 0.4.sp"))
    assertTrue(hasLiteralSp("style.copy(fontSize = (x * 2f).sp)"))
    assertFalse(hasLiteralSp("import androidx.compose.ui.unit.sp"))
    assertFalse(hasLiteralSp("  // never write 12.sp here"))
    assertFalse(hasLiteralSp("  * documented as 10.sp in the spec"))
    assertFalse(hasLiteralSp("    style = PenLabTextStyles.badge,"))
  }

  /** True for code lines containing `.sp`; imports and comment lines are ignored. */
  private fun hasLiteralSp(line: String): Boolean {
    val t = line.trim()
    if (t.startsWith("import ") || t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) return false
    return literalSp.containsMatchIn(t)
  }
}
