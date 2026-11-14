package io.github.cataylor5.ibs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/** Original supplied PNG bytes, packaged as text because IDE asset import accepts text buffers. */
internal object BrandAssets {
    @Volatile var wordmark: Bitmap? = null
        private set
    @Volatile var overview: Bitmap? = null
        private set

    @Volatile var overviewDay: Bitmap? = null
        private set

    fun loadWordmark(context: Context): Bitmap? = wordmark ?: decode(context, R.raw.ibs_wordmark)
        ?.let(::trimTransparentPadding).also { wordmark = it }
    fun loadOverview(context: Context, night: Boolean = true): Bitmap? = if (night)
        overview ?: decode(context, R.raw.ibs_overview).also { overview = it }
    else overviewDay ?: decode(context, R.raw.ibs_overview_day).also { overviewDay = it }

    internal data class HeaderPin(val bitmap: Bitmap, val width: Float, val height: Float)

    fun headerPin(original: Bitmap): HeaderPin {
        val cropped = trimTransparentPadding(original)
        // Keep exactly the scale used by the former 76 x 96 dp Fit image, removing empty canvas only.
        val scale = minOf(76f / original.width, 96f / original.height)
        return HeaderPin(cropped, cropped.width * scale, cropped.height * scale)
    }

    /** Fit the lettering itself without the supplied PNG's transparent outer canvas. */
    private fun trimTransparentPadding(bitmap: Bitmap): Bitmap {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (pixels[y * bitmap.width + x] ushr 24 != 0) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        if (right < left || bottom < top) return bitmap
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }

    private fun decode(context: Context, resource: Int): Bitmap? = try {
        val encoded = context.resources.openRawResource(resource).bufferedReader().use { it.readText() }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 2 })
    } catch (_: Exception) { null }
}
