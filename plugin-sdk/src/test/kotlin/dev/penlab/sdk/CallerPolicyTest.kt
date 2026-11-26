package dev.penlab.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerPolicyTest {
  @Test
  fun onlyThePenLabPackageIsAllowed() {
    assertTrue(CallerPolicy.isAllowed(listOf("dev.penlab.app"), false, { false }))
    assertTrue(CallerPolicy.isAllowed(listOf("other", "dev.penlab.app"), false, { false }))
    assertFalse(CallerPolicy.isAllowed(listOf("dev.evil.app"), false, { true }))
    assertFalse(CallerPolicy.isAllowed(emptyList(), false, { true })) // package not visible / unknown uid
    assertFalse(CallerPolicy.isAllowed(listOf("dev.penlab.app.fake"), false, { true }))
  }

  @Test
  fun signatureIsOnlyAskedForInStrictMode() {
    var asked = 0
    assertTrue(CallerPolicy.isAllowed(listOf("dev.penlab.app"), false, { asked++; false }))
    assertEquals(0, asked)
    assertFalse(CallerPolicy.isAllowed(listOf("dev.penlab.app"), true, { asked++; false }))
    assertEquals(1, asked)
    assertTrue(CallerPolicy.isAllowed(listOf("dev.penlab.app"), true, { true }))
  }

  @Test
  fun signatureIsNotAskedForTheWrongPackage() {
    var asked = 0
    assertFalse(CallerPolicy.isAllowed(listOf("x"), true, { asked++; true }))
    assertEquals(0, asked)
  }
}
