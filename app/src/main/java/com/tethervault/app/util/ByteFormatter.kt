package com.tethervault.app.util

import java.util.Locale

object ByteFormatter {

    private const val UNIT = 1024L
    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

    fun format(bytes: Long): String {
        if (bytes < UNIT) return "$bytes B"
        var value = bytes
        var unitIndex = 0
        while (value >= UNIT && unitIndex < UNITS.lastIndex) {
            value /= UNIT
            unitIndex++
        }
        return String.format(Locale.US, "%d %s", value, UNITS[unitIndex])
    }
}
