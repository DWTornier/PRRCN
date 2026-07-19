package com.tornier.prrcn.ui.color

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.request.videoFrameMillis
import coil.size.Scale
import com.tornier.prrcn.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of analysing a media item: its most prominent colour and a small frame for blurring. */
data class MediaSeed(val color: Color, val thumbnail: Bitmap?)

/** Brand seed derived from the golden box logo, used before any media has been viewed. */
val BrandSeed = Color(0xFFF0A814)

/**
 * Decode a small frame of [model] (image / gif / video / vault file), pick the most
 * eye-catching colour via [Palette] and return both the colour and a downscaled bitmap
 * that the acrylic background can blur.
 */
suspend fun loadMediaSeed(
    context: Context,
    model: Any?,
    type: MediaType? = null
): MediaSeed? = withContext(Dispatchers.IO) {
    if (model == null) return@withContext null
    val builder = ImageRequest.Builder(context)
        .data(model)
        .size(160)
        .scale(Scale.FILL)
        .allowHardware(false) // Palette needs to read the pixels back
    if (type == MediaType.VIDEO) {
        builder.videoFrameMillis(0)
    }
    val result = runCatching { context.imageLoader.execute(builder.build()) }.getOrNull()
    val drawable = (result as? SuccessResult)?.drawable
    val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        ?: return@withContext null

    val safe = if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
    } else bitmap

    val color = dominantColorOf(safe)?.let { Color(it) } ?: BrandSeed
    MediaSeed(color, safe)
}

/** Choose the most prominent swatch, preferring vivid colours then falling back gracefully. */
fun dominantColorOf(bitmap: Bitmap): Int? = runCatching {
    val palette = Palette.from(bitmap).clearFilters().maximumColorCount(24).generate()
    (palette.vibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.dominantSwatch
        ?: palette.mutedSwatch)?.rgb
}.getOrNull()

/**
 * A lightweight, dependency-free box blur (three passes approximate a gaussian).
 * Operates on a downscaled bitmap so it is cheap on every API level.
 */
fun blurBitmap(source: Bitmap, radius: Int = 6, downscaleTo: Int = 96): Bitmap {
    val ratio = source.height.toFloat() / source.width.toFloat().coerceAtLeast(1f)
    val w = downscaleTo.coerceAtMost(source.width)
    val h = (w * ratio).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(source, w, h, true)
    val out = scaled.copy(Bitmap.Config.ARGB_8888, true)
    val pixels = IntArray(w * h)
    out.getPixels(pixels, 0, w, 0, 0, w, h)
    repeat(3) {
        boxBlurHorizontal(pixels, w, h, radius)
        boxBlurVertical(pixels, w, h, radius)
    }
    out.setPixels(pixels, 0, w, 0, 0, w, h)
    return out
}

private fun boxBlurHorizontal(pixels: IntArray, w: Int, h: Int, r: Int) {
    val tmp = IntArray(w)
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            var a = 0; var rr = 0; var g = 0; var b = 0; var count = 0
            for (k in -r..r) {
                val xx = (x + k).coerceIn(0, w - 1)
                val p = pixels[row + xx]
                a += (p ushr 24) and 0xFF
                rr += (p ushr 16) and 0xFF
                g += (p ushr 8) and 0xFF
                b += p and 0xFF
                count++
            }
            tmp[x] = ((a / count) shl 24) or ((rr / count) shl 16) or ((g / count) shl 8) or (b / count)
        }
        System.arraycopy(tmp, 0, pixels, row, w)
    }
}

private fun boxBlurVertical(pixels: IntArray, w: Int, h: Int, r: Int) {
    val tmp = IntArray(h)
    for (x in 0 until w) {
        for (y in 0 until h) {
            var a = 0; var rr = 0; var g = 0; var b = 0; var count = 0
            for (k in -r..r) {
                val yy = (y + k).coerceIn(0, h - 1)
                val p = pixels[yy * w + x]
                a += (p ushr 24) and 0xFF
                rr += (p ushr 16) and 0xFF
                g += (p ushr 8) and 0xFF
                b += p and 0xFF
                count++
            }
            tmp[y] = ((a / count) shl 24) or ((rr / count) shl 16) or ((g / count) shl 8) or (b / count)
        }
        for (y in 0 until h) pixels[y * w + x] = tmp[y]
    }
}
