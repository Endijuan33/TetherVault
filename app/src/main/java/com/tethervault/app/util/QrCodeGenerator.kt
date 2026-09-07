package com.tethervault.app.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeGenerator {

    fun generate(content: String, size: Int = 512): Bitmap? = runCatching {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size) { index ->
            if (matrix[index % size, index / size]) Color.BLACK else Color.WHITE
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}
