package com.tethervault.app.domain.model

data class AppSettings(
    val hotspotSsid: String,
    val hotspotSecurity: HotspotSecurity,
    val hotspotPassphrase: String
)

enum class HotspotSecurity {
    // Wi-Fi Direct groups are always WPA2-PSK per the P2P specification, so
    // OPEN mode uses a well-known public passphrase: joining requires no
    // secret and the voucher portal remains the only gate to the internet.
    OPEN,

    WPA2_PSK
}
