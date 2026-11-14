package io.github.cataylor5.ibs

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cataylor5.ibs.ui.theme.IbsBrand
import io.github.cataylor5.ibs.ui.theme.IbsAppearance
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reuses the actual pin and wordmark embedded in the bundled splash, without replaying it. */
internal object SplashArtwork {
    @Volatile var cached: Pair<Bitmap, Bitmap>? = null
    fun load(context: Context): Pair<Bitmap, Bitmap>? {
        cached?.let { return it }
        val retriever = MediaMetadataRetriever()
        return try {
            context.resources.openRawResourceFd(R.raw.ibs_splash).use {
                retriever.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: return null
            val frame = retriever.getFrameAtTime((duration * 900), MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
            // The artwork is cobalt on aqua. Preserve its original pixels and the white toilet.
            val width = frame.width
            val height = frame.height
            val pixels = IntArray(width * height)
            frame.getPixels(pixels, 0, width, 0, 0, width, height)
            fun blue(pixel: Int): Boolean {
                val r = pixel shr 16 and 255
                val g = pixel shr 8 and 255
                val b = pixel and 255
                return b >= 120 && b > g * 1.35 && b > r * 1.8
            }
            var left = width; var right = -1; var top = height; var bottom = -1
            for (y in height / 5 until height * 7 / 10) for (x in width / 5 until width * 4 / 5) {
                if (blue(pixels[y * width + x])) {
                    left = minOf(left, x); right = maxOf(right, x)
                    top = minOf(top, y); bottom = maxOf(bottom, y)
                }
            }
            if (right <= left || bottom <= top) { frame.recycle(); return null }
            // Find the blank horizontal gap between the pin and the IBS wordmark.
            var gapStart = -1
            var bestStart = -1
            var bestLength = 0
            val searchStart = top + (bottom - top) / 2
            val searchEnd = top + (bottom - top) * 4 / 5
            for (y in searchStart..searchEnd) {
                val empty = (left..right).none { blue(pixels[y * width + it]) }
                if (empty && gapStart < 0) gapStart = y
                if (gapStart >= 0 && (!empty || y == searchEnd)) {
                    val length = y - gapStart
                    if (length > bestLength) { bestLength = length; bestStart = gapStart }
                    gapStart = -1
                }
            }
            if (bestLength == 0) { frame.recycle(); return null }
            val split = bestStart + bestLength / 2
            fun cut(yStart: Int, yEnd: Int, keepWhite: Boolean): Bitmap {
                val w = right - left + 1
                val h = yEnd - yStart + 1
                val output = IntArray(w * h)
                for (y in yStart..yEnd) {
                    val rowBlue = (left..right).filter { blue(pixels[y * width + it]) }
                    val rowLeft = rowBlue.firstOrNull() ?: width
                    val rowRight = rowBlue.lastOrNull() ?: -1
                    for (x in left..right) {
                        val p = pixels[y * width + x]
                        val whiteInsidePin = keepWhite && x in rowLeft..rowRight &&
                            (p shr 16 and 255) > 210 && (p shr 8 and 255) > 210 && (p and 255) > 210
                        if (blue(p) || whiteInsidePin) output[(y - yStart) * w + x - left] = p
                    }
                }
                return Bitmap.createBitmap(output, w, h, Bitmap.Config.ARGB_8888)
            }
            (cut(top, split, true) to cut(split + 1, bottom, false)).also { cached = it; frame.recycle() }
        } catch (_: Exception) { null }
        finally { retriever.release() }
    }
}

@Composable
internal fun IbsBrandHeader() {
    val context = LocalContext.current.applicationContext
    val night = IbsAppearance.state.night == true
    val palette = IbsAppearance.palette
    val wordmark by produceState(BrandAssets.wordmark) {
        value = withContext(Dispatchers.IO) { BrandAssets.loadWordmark(context) }
    }
    val pin by produceState<BrandAssets.HeaderPin?>(null) {
        value = withContext(Dispatchers.IO) { SplashArtwork.load(context)?.first?.let(BrandAssets::headerPin) }
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(Brush.horizontalGradient(if (night) listOf(IbsBrand.Navy, Color(palette.surface))
            else listOf(IbsBrand.PaleAqua, Color(0xFFDCEBFC))))
        .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        pin?.let { artwork ->
            Box(Modifier.width(76.dp).height(artwork.height.dp), contentAlignment = Alignment.Center) {
                Image(artwork.bitmap.asImageBitmap(), "IBS restroom pin", Modifier.size(artwork.width.dp, artwork.height.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            }
        }
        wordmark?.let {
            Image(it.asImageBitmap(), "I Better Stop!", Modifier.weight(1f).height(64.dp),
                alignment = Alignment.CenterStart, contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        } ?: Text("I Better Stop!", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = 24.sp, color = if (night) IbsBrand.Aqua else IbsBrand.Cobalt)
    }
}
