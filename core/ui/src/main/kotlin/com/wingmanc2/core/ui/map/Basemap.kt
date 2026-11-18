package com.wingmanc2.core.ui.map

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.wingmanc2.core.ui.R

internal object Basemap {
    private const val SOURCE_PX = 1024
    private const val MAX_SAMPLE_SHIFT = 3
    private val decoded = arrayOfNulls<ImageBitmap>(MAX_SAMPLE_SHIFT + 1)

    fun sampleShiftFor(displayedPx: Float): Int {
        var shift = 0
        while (shift < MAX_SAMPLE_SHIFT && (SOURCE_PX shr (shift + 1)) >= displayedPx) shift++
        return shift
    }

    fun cached(sampleShift: Int): ImageBitmap? = synchronized(this) { decoded[sampleShift] }

    fun load(resources: Resources, sampleShift: Int): ImageBitmap = synchronized(this) {
        decoded[sampleShift] ?: decode(resources, 1 shl sampleShift).also { decoded[sampleShift] = it }
    }

    private fun decode(resources: Resources, sampleSize: Int): ImageBitmap {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeResource(resources, R.drawable.basemap, options).asImageBitmap()
    }
}
