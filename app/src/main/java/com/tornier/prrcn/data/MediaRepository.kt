package com.tornier.prrcn.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.tornier.prrcn.data.model.DeviceMedia
import com.tornier.prrcn.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the media the user already has on the device so it can be picked and packed.
 * Queries the unified [MediaStore.Files] table for images / video / audio in one pass.
 */
class MediaRepository(private val context: Context) {

    suspend fun queryDeviceMedia(): List<DeviceMedia> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DURATION
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString()
        )
        val sort = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        val result = ArrayList<DeviceMedia>()
        context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val durCol = c.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol) ?: "unknown"
                val mime = c.getString(mimeCol)
                val size = c.getLong(sizeCol)
                val date = c.getLong(dateCol)
                val dur = if (durCol >= 0) c.getLong(durCol) else 0L
                val uri = ContentUris.withAppendedId(collection, id)
                result += DeviceMedia(
                    id = id,
                    uri = uri,
                    displayName = name,
                    mimeType = mime,
                    type = MediaType.fromMime(mime, name),
                    size = size,
                    dateAddedSec = date,
                    durationMs = dur
                )
            }
        }
        result
    }
}
