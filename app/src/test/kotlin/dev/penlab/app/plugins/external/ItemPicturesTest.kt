package dev.penlab.app.plugins.external

import org.junit.Assert.assertEquals
import org.junit.Test

class ItemPicturesTest {
  private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

  @Test
  fun mimeTypeIsSniffedFromTheBytes() {
    assertEquals("image/png", ItemPictures.sniffMime(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
    assertEquals("image/jpeg", ItemPictures.sniffMime(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10)))
    assertEquals("image/gif", ItemPictures.sniffMime("GIF89a".toByteArray()))
    assertEquals("image/webp", ItemPictures.sniffMime("RIFF....WEBPVP8 ".toByteArray()))
    assertEquals("image/bmp", ItemPictures.sniffMime("BM......".toByteArray()))
    assertEquals("application/octet-stream", ItemPictures.sniffMime(bytes(1, 2, 3)))
    assertEquals("application/octet-stream", ItemPictures.sniffMime(ByteArray(0)))
  }

  @Test
  fun aPictureWithinTheCapIsNotScaled() {
    assertEquals(2048 to 1536, ItemPictures.scaledSize(2048, 1536))
    assertEquals(800 to 600, ItemPictures.scaledSize(800, 600))
  }

  @Test
  fun aBiggerPictureIsScaledToTheCapOnItsLongSide() {
    assertEquals(2048 to 1536, ItemPictures.scaledSize(4096, 3072)) // a 12 MP photo
    assertEquals(1536 to 2048, ItemPictures.scaledSize(3000, 4000))
    assertEquals(2048 to 1, ItemPictures.scaledSize(100_000, 5)) // never below one pixel
  }
}
