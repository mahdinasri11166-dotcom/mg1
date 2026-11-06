package com.ghostmago.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

object ImageCompress {
    /** Compress to JPEG under maxBytes (default ~28KB for UDP mesh). */
    fun toJpeg(data: ByteArray, maxBytes: Int = 28000, maxDim: Int = 640): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        var sample = 1
        val w = bounds.outWidth
        val h = bounds.outHeight
        while (w / sample > maxDim || h / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(data, 0, data.size, opts) ?: return data.copyOf(minOf(data.size, maxBytes))
        var quality = 85
        var out: ByteArray
        do {
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            out = baos.toByteArray()
            quality -= 10
        } while (out.size > maxBytes && quality >= 30)
        if (!bmp.isRecycled) bmp.recycle()
        return if (out.size > maxBytes) out.copyOf(maxBytes) else out
    }
}
