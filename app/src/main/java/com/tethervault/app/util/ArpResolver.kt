package com.tethervault.app.util

import android.util.Log
import java.io.BufferedReader
import java.io.FileReader

object ArpResolver {

    private const val TAG = "TetherVaultArp"
    private const val ARP_TABLE_PATH = "/proc/net/arp"

    /**
     * Resolves the hardware (MAC) address of a peer IP from the kernel ARP
     * table. Returns null when the entry is missing, incomplete, or the
     * table cannot be read (restricted on many Android 10+ builds).
     */
    fun getMacFromIp(ipAddress: String): String? {
        if (ipAddress.isBlank()) return null
        return try {
            BufferedReader(FileReader(ARP_TABLE_PATH)).use { reader ->
                // First line is the header: "IP address  HW type  Flags  HW address..."
                reader.readLine()
                reader.lineSequence().forEach { line ->
                    val columns = line.trim().split(Regex("\\s+"))
                    if (columns.size > MAC_COLUMN_INDEX &&
                        columns[IP_COLUMN_INDEX] == ipAddress
                    ) {
                        val mac = columns[MAC_COLUMN_INDEX]
                        if (mac.isNotBlank() && !mac.equals(INCOMPLETE_ENTRY, ignoreCase = true)) {
                            return mac
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read ARP table: ${e.message}")
            null
        }
    }

    private const val IP_COLUMN_INDEX = 0
    private const val MAC_COLUMN_INDEX = 3
    private const val INCOMPLETE_ENTRY = "00:00:00:00:00:00"
}
