package br.com.pontopj.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

data class SavedFile(val uri: Uri, val name: String)

/** Salva um arquivo na pasta Downloads do aparelho (não precisa de permissão no Android 10+). */
fun saveToDownloads(context: Context, name: String, mime: String, bytes: ByteArray): SavedFile? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
    return try {
        val stream = resolver.openOutputStream(uri) ?: throw IllegalStateException("sem saída")
        stream.use { it.write(bytes) }
        val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        resolver.update(uri, done, null, null)
        SavedFile(uri, name)
    } catch (e: Exception) {
        runCatching { resolver.delete(uri, null, null) }
        null
    }
}
