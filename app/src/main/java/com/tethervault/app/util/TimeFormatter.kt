package com.tethervault.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeFormatter {

    private val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
        .withZone(ZoneId.systemDefault())

    fun format(timestampMillis: Long): String =
        formatter.format(Instant.ofEpochMilli(timestampMillis))
}
