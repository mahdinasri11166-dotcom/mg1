package com.ghostmago.app.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrHelper {

    fun generate(content: String, size: Int = 768): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    fun decode(bitmap: Bitmap): String? {
        val candidates = mutableListOf(bitmap)
        // Scaled versions help camera/gallery soft images
        try {
            if (bitmap.width > 900) {
                val w = 800
                val h = (bitmap.height * 800f / bitmap.width).toInt().coerceAtLeast(200)
                candidates += Bitmap.createScaledBitmap(bitmap, w, h, true)
            }
            if (bitmap.width < 400) {
                val w = 600
                val h = (bitmap.height * 600f / bitmap.width).toInt().coerceAtLeast(200)
                candidates += Bitmap.createScaledBitmap(bitmap, w, h, true)
            }
        } catch (_: Exception) {
        }

        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        val reader = MultiFormatReader().apply { setHints(hints) }

        for (bmp in candidates) {
            val w = bmp.width
            val h = bmp.height
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            val attempts = listOf(pixels, invert(pixels.copyOf()))
            for (pix in attempts) {
                val source = RGBLuminanceSource(w, h, pix)
                for (bin in listOf(HybridBinarizer(source), GlobalHistogramBinarizer(source))) {
                    try {
                        reader.reset()
                        val text = reader.decode(BinaryBitmap(bin), hints).text
                        if (!text.isNullOrBlank()) return text.trim()
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return null
    }

    private fun invert(pixels: IntArray): IntArray {
        for (i in pixels.indices) {
            val c = pixels[i]
            pixels[i] = (c and 0xFF000000.toInt()) or (0x00FFFFFF - (c and 0x00FFFFFF))
        }
        return pixels
    }
}
