package com.skintclub.odyssey.club

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/*
 * Thumbnails picked for secret episodes. A photo picker URI only stays readable while the app
 * that got it is running, so the picked image is copied into the app's own files and the
 * episode keeps that copy's file:// URI - which Coil (lists, player, notification artwork via
 * CoilBitmapLoader) loads like any other image URL.
 */
object SecretEpisodeThumbnails {
    private const val DIR = "secret_episode_thumbnails"

    private fun dir(context: Context) = File(context.filesDir, DIR)

    /** Copies the image at [source] into app storage and returns the copy's URI string, or null
     * if it couldn't be read. */
    suspend fun import(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val extension = when (context.contentResolver.getType(source)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> "jpg"
            }
            val target = File(dir(context).apply { mkdirs() }, "${UUID.randomUUID()}.$extension")
            val input = context.contentResolver.openInputStream(source) ?: return@runCatching null
            input.use { inp -> target.outputStream().use { out -> inp.copyTo(out) } }
            Uri.fromFile(target).toString()
        }.getOrNull()
    }

    /** Deletes [thumbnail] if it's one of the copies [import] made (a URL is left alone). */
    suspend fun delete(context: Context, thumbnail: String?) = withContext(Dispatchers.IO) {
        val file = ownedFile(context, thumbnail) ?: return@withContext
        file.delete()
    }

    private fun ownedFile(context: Context, thumbnail: String?): File? {
        val uri = thumbnail?.let(Uri::parse) ?: return null
        if (uri.scheme != "file") return null
        val file = File(uri.path ?: return null)
        return file.takeIf { it.parentFile?.canonicalPath == dir(context).canonicalPath }
    }
}
