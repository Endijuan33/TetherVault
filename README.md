# TetherVault

**TetherVault** is an Android application that turns your device into a voucher-based internet sharing hotspot. It combines a Wi-Fi Direct Group Owner (hotspot), a local VPN tunnel (TUN interface), a captive portal with voucher authentication, and a reactive management UI — all built with Kotlin and Jetpack Compose.

## Features

### Implemented
- **Wi-Fi Direct Hotspot** — creates an autonomous Wi-Fi Direct group (Group Owner mode) and surfaces the system-generated SSID & passphrase in the UI.
- **Persistent Foreground Service** — the hotspot runs inside a stable foreground service (`connectedDevice` type) with a persistent notification and a **Stop Hotspot** action button.
- **VPN / TUN Interface** — an `android.net.VpnService` captures device traffic into a TUN interface (`10.0.0.2/24`, default route, DNS `8.8.8.8`, MTU 1500).
- **Native Bridge (tun2socks)** — a crash-safe JNI wrapper ready to route TUN traffic to a local proxy. The app runs normally even before `libtun2socks.so` is dropped in.
- **Captive Portal** — a pure `java.io` HTTP server (port 1080) that intercepts unauthenticated clients, serves a login form, and validates voucher codes against the Room database.
- **Voucher Management** — generate (8-character secure random codes), list (AVAILABLE / USED / EXPIRED status), copy-to-clipboard, and delete vouchers with confirmation dialogs.
- **Device Management** — live list of connected clients with authentication status, data usage display, and access revocation.
- **Reactive UI** — all lists are backed by Room `Flow` → `StateFlow`, so every change (generate, login via portal, revoke) updates the UI instantly.

### Planned / Manual Steps
- Drop `libtun2socks.so` into `app/src/main/jniLibs/<abi>/` (see [Native Library](#native-library-libtun2socks)).
- Real TCP/UDP proxy forwarding upstream.
- MAC address resolution for connected clients.

## Architecture

Lightweight Clean Architecture with MVVM and unidirectional data flow:

```
┌─────────────────────────────────────────────────────────┐
│ presentation (Compose UI, ViewModels, Navigation, Theme)│
└────────────────────────┬────────────────────────────────┘
                         │ StateFlow / events
┌────────────────────────▼────────────────────────────────┐
│ domain (models, repository & manager interfaces,        │
│         use cases: Authenticate, GenerateVoucher)       │
└────────────────────────┬────────────────────────────────┘
                         │ implemented by
┌────────────────────────▼────────────────────────────────┐
│ data (Room DB, repositories, Wi-Fi Direct hotspot,      │
│       VPN tunnel, tun2socks JNI, captive portal server) │
└─────────────────────────────────────────────────────────┘
```

Cross-cutting concerns are wired with **Hilt** dependency injection. Both the hotspot and the VPN expose their status as `StateFlow` (`HotspotState`, `VpnState`) observed by the UI, so services, managers, and screens stay decoupled.

## Tech Stack

| Area | Technology |
|---|---|
| Language | Kotlin 2.1.21 |
| UI | Jetpack Compose (BOM 2025.06.01), Material 3 with dynamic color |
| Navigation | Navigation Compose 2.9.0 |
| DI | Hilt 2.56.2 |
| Database | Room 2.7.1 (KSP) |
| Async | Kotlin Coroutines + Flow |
| Networking | `WifiP2pManager`, `VpnService`, raw `java.io` sockets |
| Build | AGP 8.10.1, Gradle 8.14.2, Version Catalog, R8/ProGuard |

**Min SDK 26 · Target/Compile SDK 36**

## Project Structure

```
app/src/main/java/com/tethervault/app/
├── TetherVaultApp.kt                  # @HiltAndroidApp
├── di/                                # Hilt modules (DB, repos, hotspot, VPN)
├── data/
│   ├── hotspot/                       # WifiP2pManager Group Owner logic
│   ├── vpn/                           # VpnManagerImpl, Tun2SocksAdapter
│   │   └── jni/                       # Tun2SocksJniWrapper (external funs)
│   ├── local/                         # Room database, entities, DAOs
│   └── repository/                    # Repository implementations
├── domain/
│   ├── model/                         # Voucher, ConnectedDevice, AccessLog,
│   │                                  # HotspotState, VpnState
│   ├── hotspot/                       # HotspotManager interface
│   ├── vpn/                           # VpnManager, LocalProxyServer, SocketProtector
│   ├── repository/                    # Repository interfaces
│   └── usecase/                       # AuthenticateDevice, GenerateVoucher
├── presentation/
│   ├── main/                          # MainActivity, Home (hotspot + VPN controls)
│   ├── devices/                       # Device list & revocation
│   ├── vouchers/                      # Voucher list & generation
│   ├── settings/                      # Settings placeholder
│   ├── components/                    # Shared composables (EmptyState)
│   ├── navigation/                    # Bottom navigation destinations
│   └── theme/                         # Material 3 theme
├── service/
│   ├── TetherVaultForegroundService.kt # Hotspot foreground service
│   └── TetherVaultVpnService.kt        # VPN (TUN) service
└── util/                              # Constants, ByteFormatter, TimeFormatter
```

## Getting Started

### Prerequisites
- Android Studio (Ladybug or newer recommended)
- JDK 17
- Android SDK with platform 36

### Build

```bash
# Debug APK
./gradlew :app:assembleDebug

# Release APK (R8 minified + resource shrinking)
./gradlew :app:assembleRelease
```

Outputs land in `app/build/outputs/apk/`. The release build is unsigned — configure a signing config before distribution.

### How It Works

1. **Home → Start Hotspot** — requests runtime permissions (nearby devices / notifications on Android 13+, location below), starts the foreground service, and creates the Wi-Fi Direct group. SSID & password appear on screen.
2. **Home → Start VPN** — triggers the system VPN consent dialog (`VpnService.prepare`), then establishes the TUN interface and starts the captive portal server.
3. **Client connects** — any HTTP request from an unauthenticated client is intercepted by the captive portal and answered with the TetherVault login form.
4. **Voucher login** — the client submits a voucher code; it is validated against Room (exists, unused, unexpired). On success the voucher is marked used and the device becomes authenticated.
5. **Manage** — generate vouchers (FAB on the Vouchers tab), copy codes, revoke devices from the Devices tab. All lists update reactively.

## Native Library (libtun2socks)

The Kotlin side is complete and stable; routing TUN traffic to the local proxy requires a native library with these exact JNI symbols:

```text
Java_com_tethervault_app_data_vpn_jni_Tun2SocksJniWrapper_start
Java_com_tethervault_app_data_vpn_jni_Tun2SocksJniWrapper_stop
```

Drop the compiled `.so` files into `app/src/main/jniLibs/<abi>/` (e.g. `arm64-v8a/`). Until then the app runs normally — the adapter catches `UnsatisfiedLinkError` and logs a warning instead of crashing. The ProGuard keep rule for the wrapper is already in place.

## Permissions

| Permission | Purpose |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Network access and connectivity checks |
| `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` | Wi-Fi Direct group management |
| `NEARBY_WIFI_DEVICES` (runtime, API 33+) | Required for Wi-Fi Direct on Android 13+ |
| `ACCESS_FINE_LOCATION` (runtime, API ≤ 32) | Required for Wi-Fi Direct on older Android |
| `POST_NOTIFICATIONS` (runtime, API 33+) | Foreground service notification |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Hotspot foreground service |
| `BIND_VPN_SERVICE` | System-binds the VPN service |

## Known Limitations

- Without `libtun2socks.so`, VPN traffic is captured into the TUN interface but not forwarded (the device loses upstream internet while the VPN is active).
- Client MAC addresses are stored as `unknown:<ip>` placeholders until MAC resolution is implemented.
- Revocation changes the authentication flag in the database; an already-authenticated client is blocked on its next request rather than being force-disconnected.
- Requires testing on physical devices (Wi-Fi Direct and VPN cannot run on emulators).

## Roadmap

- [ ] Ship `libtun2socks.so` + real traffic forwarding
- [ ] MAC resolution & device fingerprints
- [ ] Access log UI (Room entity already in place)
- [ ] Voucher duration customization in the UI
- [ ] Release signing & Play Store compliance review
