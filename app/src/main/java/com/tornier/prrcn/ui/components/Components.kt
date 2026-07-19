package com.tornier.prrcn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MotionPhotosOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tornier.prrcn.data.model.MediaType
import com.tornier.prrcn.data.model.Vault
import java.io.File
import java.util.Locale

fun iconFor(type: MediaType): ImageVector = when (type) {
    MediaType.IMAGE -> Icons.Rounded.Image
    MediaType.GIF -> Icons.Rounded.Gif
    MediaType.VIDEO -> Icons.Rounded.PlayCircle
    MediaType.AUDIO -> Icons.Rounded.MusicNote
    MediaType.LIVE_PHOTO -> Icons.Rounded.MotionPhotosOn
    MediaType.OTHER -> Icons.Rounded.InsertDriveFile
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024; i++
    }
    return String.format(Locale.US, "%.1f %s", value, units[i])
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}

/** Thumbnail for a device/vault media item. Falls back to a typed icon for non-visual media. */
@Composable
fun MediaThumbnail(
    model: Any?,
    type: MediaType,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (type.isVisual && model != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContextCompat()).data(model).crossfade(true).build(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = iconFor(type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).size(40.dp)
            )
        }
    }
}

@Composable
private fun LocalContextCompat() = androidx.compose.ui.platform.LocalContext.current

/** Extract (once) and remember a vault's cover file so it can be shown in a grid. */
@Composable
fun rememberVaultCover(
    vault: Vault,
    extractor: suspend (Vault, String) -> File?
): File? {
    val state = produceState<File?>(initialValue = null, key1 = vault.file.absolutePath) {
        value = vault.coverEntry?.let { extractor(vault, it) }
    }
    return state.value
}

@Composable
fun ScrimBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
        }
        androidx.compose.material3.Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
