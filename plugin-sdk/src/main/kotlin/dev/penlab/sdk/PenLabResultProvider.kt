package dev.penlab.sdk

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves the result files of a screen activity ([PenLabScreenContract.resultIntent]) to PenLab,
 * read-only, by one-time grant. It is declared (not exported) by this SDK's manifest, so a plugin
 * does nothing to enable it. Files live in `cache/penlab-results`.
 */
class PenLabResultProvider : ContentProvider() {
  override fun onCreate(): Boolean = true

  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
    if (mode != "r") throw FileNotFoundException("read-only")
    val dir = resultsDir(context ?: throw FileNotFoundException())
    val name = uri.lastPathSegment ?: throw FileNotFoundException()
    val file = File(dir, name)
    // The name came from a URI: never follow it out of the folder.
    if (file.canonicalFile.parentFile != dir.canonicalFile || !file.isFile) throw FileNotFoundException()
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
  }

  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

  override fun getType(uri: Uri): String? = null

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

  internal companion object {
    fun resultsDir(context: android.content.Context) = File(context.cacheDir, "penlab-results")

    fun authority(context: android.content.Context) = "${context.packageName}.penlab-results"
  }
}
