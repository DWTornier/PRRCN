package com.tornier.prrcn.data.model

import android.net.Uri
import java.io.File

/** Broad classification of a media item, used for icons, decoding and grouping. */
enum class MediaType {
    IMAGE, GIF, VIDEO, AUDIO, LIVE_PHOTO, OTHER;

    val isVisual: Boolean get() = this == IMAGE || this == GIF || this == VIDEO || this == LIVE_PHOTO

    companion object {
        fun fromMime(mime: String?, name: String? = null): MediaType {
            val m = mime?.lowercase().orEmpty()
            val n = name?.lowercase().orEmpty()
            return when {
                m == "image/gif" || n.endsWith(".gif") -> GIF
                m.startsWith("video/") || n.endsWith(".mp4") || n.endsWith(".mov") || n.endsWith(".mkv") -> VIDEO
                m.startsWith("audio/") || n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".flac") || n.endsWith(".wav") -> AUDIO
                m.startsWith("image/") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".heic") -> IMAGE
                else -> OTHER
            }
        }
    }
}

/** A media item that lives in the device's shared storage (queried from MediaStore). */
data class DeviceMedia(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val type: MediaType,
    val size: Long,
    val dateAddedSec: Long,
    val durationMs: Long = 0L
)

/** A packed archive ("vault") stored on disk as an obfuscated zip. */
data class Vault(
    val file: File,
    val displayName: String,
    val itemCount: Int,
    val sizeBytes: Long,
    val lastModified: Long,
    /** entry name used to render the cover thumbnail, if any visual entry exists */
    val coverEntry: String?
)

/** A single entry stored inside a [Vault]. */
data class VaultEntry(
    val name: String,
    val type: MediaType,
    val size: Long
)
