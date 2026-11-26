package dev.penlab.sdk

import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Helpers for the files that travel next to the JSON: reading the request's files, and turning a
 * result's files into the ParcelFileDescriptors `IPenLabPlugin.run` returns. [PenLabPluginService]
 * uses these for you; they are public for plugins that talk to the bridge in their own way.
 */
object PluginFiles {
  /** Largest file read into memory by [readBytes]. */
  const val MAX_BYTES = 64L * 1024 * 1024

  /** The request file called [name] as an open descriptor (the caller closes it), or null if there is none. */
  @Suppress("DEPRECATION")
  fun open(files: Bundle, name: String): ParcelFileDescriptor? = files.getParcelable(name) as? ParcelFileDescriptor

  /** The request file called [name] as a stream (closing it closes the descriptor), or null. */
  fun inputStream(files: Bundle, name: String): InputStream? = open(files, name)?.let { ParcelFileDescriptor.AutoCloseInputStream(it) }

  /** The whole request file called [name], or null if there is none. Throws [IOException] beyond [maxBytes]. */
  fun readBytes(files: Bundle, name: String, maxBytes: Long = MAX_BYTES): ByteArray? = inputStream(files, name)?.use { readLimited(it, maxBytes) }

  /**
   * Opens [files] for return: bytes go through a temporary file in [tempDir] that is deleted as soon
   * as its descriptor is open (the descriptor keeps the data alive); on-disk files are opened as they are.
   */
  fun toBundle(tempDir: File, files: Map<String, ResultFile>): Bundle {
    val bundle = Bundle()
    tempDir.mkdirs()
    for ((name, file) in files) {
      val pfd =
        when (file) {
          is ResultFile.OnDisk -> ParcelFileDescriptor.open(file.file, ParcelFileDescriptor.MODE_READ_ONLY)
          is ResultFile.Bytes -> {
            val temp = File(tempDir, "${UUID.randomUUID()}.tmp")
            temp.writeBytes(file.bytes)
            try {
              ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
            } finally {
              temp.delete()
            }
          }
        }
      bundle.putParcelable(name, pfd)
    }
    return bundle
  }

  /** Reads [input] to the end, failing with [IOException] if it is longer than [maxBytes]. */
  fun readLimited(input: InputStream, maxBytes: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
      val n = input.read(buffer)
      if (n < 0) break
      total += n
      if (total > maxBytes) throw IOException("file larger than $maxBytes bytes")
      out.write(buffer, 0, n)
    }
    return out.toByteArray()
  }
}
