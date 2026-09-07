# TetherVault

**TetherVault** is an Android application that turns your device into a voucher-based internet sharing hotspot. It combines a Wi-Fi Direct Group Owner (hotspot), a local VPN tunnel (TUN interface) with `hev-socks5-tunnel` routing, a captive portal with voucher authentication, real bidirectional TCP forwarding for authenticated clients, and a reactive management UI — all built with Kotlin and Jetpack Compose.

## Features

### Implemented
- **Configurable Hotspot** — the SSID and security mode (Open or WPA2/WPA3-PSK) are configurable in the Settings tab and persisted with DataStore. In Open mode the network effectively joins without a secret, making the voucher portal the only gate to the internet (Wi-Fi Direct mandates WPA2 on the radio, so a public passphrase is applied transparently). Changes apply on the next hotspot start.
- **Persistent Foreground Service** — the hotspot runs inside a stable foreground service (`connectedDevice` type) with a persistent notification and a **Stop Hotspot** action button.
- **VPN / TUN Interface** — an `android.net.VpnService` captures device traffic into a TUN interface (`10.0.0.2/24`, default route, DNS `8.8.8.8`, MTU 1500).
- **Native Routing (hev-socks5-tunnel)** — the library is compiled from source with the Android NDK by CI; the Kotlin bridge (`TProxyStartService`/`TProxyStopService`) starts the tunnel via a generated YAML config and routes TUN traffic to the local proxy as SOCKS5.
- **Captive Portal** — a pure `java.io` server answering on two ports: SOCKS5 on 1080 for tunneled traffic and a plain-HTTP login page on 8080 reachable directly at the group owner's address (e.g. `http://192.168.49.1:8080`), which starts together with the hotspot and validates voucher codes against the Room database.
- **Real TCP Forwarding** — authenticated clients get a genuine bidirectional pipe: SOCKS5 CONNECT requests are parsed (IPv4 / domain / IPv6 destinations) and both streams are forwarded with coroutines.
- **Voucher Management** — generate with a duration picker (1 hour / 24 hours / 7 days / custom hours; 8-character secure random codes), list with AVAILABLE / USED / EXPIRED status, copy-to-clipboard, and delete with confirmation dialogs.
- **Device Management** — live list of connected clients with authentication status, up/down data usage, MAC addresses resolved from the kernel ARP table, and access revocation with confirmation.
- **Access Log** — every voucher login and device revocation is recorded in Room and shown in a live history list (green LOGIN / red REVOKED).
- **Reactive UI** — all lists are backed by Room `Flow` → `StateFlow`, so every change (generate, login via portal, revoke) updates the UI instantly.
- **CI/CD** — CircleCI builds the native library from source and produces debug + release APKs as artifacts on every push.

### Planned
- UDP forwarding (SOCKS5 `UDP ASSOCIATE`) — currently only TCP `CONNECT` is supported.
- End-to-end validation on physical devices.

## Architecture

Lightweight Clean Architecture with MVVM and unidirectional data flow:

```
┌─────────────────────────────────────────────────────────┐
│ presentation (Compose UI, ViewModels, Navigation, Theme)│
└────────────────────────┬────────────────────────────────┘
                         │ StateFlow / events
┌────────────────────────▼────────────────────────────────┐
│ domain (models, repository & manager interfaces,        │
│         use cases: AuthenticateDevice, GenerateVoucher) │
└────────────────────────┬────────────────────────────────┘
                         │ implemented by
┌────────────────────────▼────────────────────────────────┐
│ data (Room DB, repositories, Wi-Fi Direct hotspot,      │
│       VPN tunnel, tun2socks adapter + YAML config,      │
│       SOCKS5 captive portal / TCP forwarder)            │
└─────────────────────────────────────────────────────────┘

hev.socks5.tunnel.Tun2Socks (JNI bridge, mirrors the native
RegisterNatives table of hev-socks5-tunnel)
```

Cross-cutting concerns are wired with **Hilt** dependency injection. The hotspot and the VPN expose their status as `StateFlow` (`HotspotState`, `VpnState`) observed by the UI, so services, managers, and screens stay decoupled.

## Tech Stack

| Area | Technology |
|---|---|
| Language | Kotlin 2.1.21 |
| UI | Jetpack Compose (BOM 2025.06.01), Material 3 with dynamic color |
| Navigation | Navigation Compose 2.9.0 |
| DI | Hilt 2.56.2 |
| Database | Room 2.7.1 (KSP), DataStore (settings) |
| Async | Kotlin Coroutines + Flow |
| Networking | `WifiP2pManager`, `VpnService`, raw `java.io` sockets, hev-socks5-tunnel (NDK) |
| Build | AGP 8.10.1, Gradle 8.14.2, Version Catalog, R8/ProGuard |
| CI/CD | CircleCI + GitHub Actions (NDK r27.2, tun2socks built from source) |

**Min SDK 26 · Target/Compile SDK 36**

## Project Structure

```
app/src/main/java/
├── hev/socks5/tunnel/Tun2Socks.kt       # JNI bridge (TProxyStartService/Stop/IsRunning/GetStats)
└── com/tethervault/app/
    ├── TetherVaultApp.kt                # @HiltAndroidApp
    ├── di/                              # Hilt modules (DB, repos, hotspot, VPN)
    ├── data/
    │   ├── hotspot/                     # WifiP2pManager Group Owner logic
    │   ├── vpn/                         # VpnManagerImpl, Tun2SocksAdapter (YAML config),
    │   │                                # LocalProxyServerImpl (SOCKS5 + portal + forwarding)
    │   ├── local/                       # Room database, entities, DAOs
    │   └── repository/                  # Repository implementations
    ├── domain/
    │   ├── model/                       # Voucher, ConnectedDevice, AccessLog,
    │   │                                # HotspotState, VpnState, AccessLogAction
    │   ├── hotspot/                     # HotspotManager interface
    │   ├── vpn/                         # VpnManager, LocalProxyServer, SocketProtector
    │   ├── repository/                  # Repository interfaces
    │   └── usecase/                     # AuthenticateDevice (ARP + logging), GenerateVoucher
    ├── presentation/
    │   ├── main/                        # MainActivity, Home (hotspot + VPN controls, permissions)
    │   ├── devices/                     # Device list, revoke + confirmation, byte usage
    │   ├── vouchers/                    # Voucher list, duration dialog, copy, delete
    │   ├── settings/                    # Hotspot configuration + Access Log history
    │   ├── components/                  # Shared composables (EmptyState)
    │   ├── navigation/                  # Bottom navigation destinations
    │   └── theme/                       # Material 3 theme
    ├── service/
    │   ├── TetherVaultForegroundService.kt # Hotspot foreground service
    │   └── TetherVaultVpnService.kt        # VPN (TUN) service, SocketProtector
    └── util/                            # Constants, ByteFormatter, TimeFormatter, ArpResolver
```

Root-level helpers: `setup-native.sh` (local native library install), `generate-keystore.sh` (release signing), `.circleci/config.yml` (CI pipeline).

## Getting Started

### Prerequisites
- Android Studio (Ladybug or newer recommended)
- JDK 17
- Android SDK with platform 36

### Build

```bash
# Debug APK (native library must be present, see below)
./gradlew :app:assembleDebug

# Release APK (R8 minified + resource shrinking, signed if keystore exists)
./gradlew :app:assembleRelease
```

Outputs land in `app/build/outputs/apk/`.

### Native Library (hev-socks5-tunnel)

The recommended path is the **CI artifact**: CircleCI clones `heiher/hev-socks5-tunnel` with submodules, compiles `libhev-socks5-tunnel.so` for `arm64-v8a` and `armeabi-v7a` with `ndk-build`, and packages it into the APKs. The build passes `-DPKGNAME=hev/socks5/tunnel -DCLSNAME=Tun2Socks` so the library's `JNI_OnLoad` binds its native methods (`TProxyStartService(configPath, fd)`, `TProxyStopService`, `TProxyIsRunning`, `TProxyGetStats`) to the Kotlin bridge class.

For local development, `./setup-native.sh` downloads the upstream prebuilt Android binaries into `jniLibs` — note that these release binaries are standalone executables rather than JNI shared libraries, so the app runs without routing until a CI-built (or self-compiled) `.so` replaces them. The `Tun2SocksAdapter` guards every `LinkageError`, so a missing or incompatible library never crashes the app.

### Release Signing

`app/build.gradle.kts` reads credentials from `app/keystore.properties` when present and signs the release build automatically; without it the release APK is simply unsigned. Generate a local keystore plus properties file with:

```bash
./generate-keystore.sh
```

Keystores and `keystore.properties` are gitignored.

## CI/CD

Two GitHub Actions workflows automate the full build (in addition to the existing CircleCI pipeline):

- **Android Build** (`.github/workflows/android-build.yml`) — runs on every push to `main` and on pull requests: compiles `libhev-socks5-tunnel.so` from source with the NDK, assembles debug and release APKs, verifies the native libraries are packaged, uploads the APKs as run artifacts, regenerates `CHANGELOG.md` from the commit history, and (on pushes) publishes a rolling **Nightly Build** prerelease with the APKs attached.
- **Android Release** (`.github/workflows/android-release.yml`) — runs when a tag like `v1.0.0` is pushed: performs the same build, regenerates the changelog (the tag becomes its own version section), moves the tag onto the changelog commit, extracts that version's section as the release notes, and creates a full GitHub **Release** with both APKs.

The changelog (`scripts/generate-changelog.sh`) is fully regenerated on every run — grouped into Features / Fixes / Changes by conventional-commit type — so it can never drift from the commit history. Changelog commits use `[skip ci]` to avoid build loops.

Download the APKs from the repo's **Releases** tab (Nightly prerelease for the latest `main`, tagged releases for versions); each workflow run also keeps per-run artifacts for 30 (build) / 90 (release) days.

### How It Works

1. **Home → Start Hotspot** — requests runtime permissions (nearby devices / notifications on Android 13+, location below), starts the foreground service, and creates the Wi-Fi Direct group using the SSID/passphrase/security configured in Settings (custom credentials require Android 10+; older devices fall back to system-generated values). SSID and password (or an "Open network" badge) appear on screen.
2. **Home → Start VPN** — triggers the system VPN consent dialog (`VpnService.prepare`), then establishes the TUN interface, starts the local proxy server, writes `tun2socks_config.yaml`, and launches the native tunnel.
3. **Client connects** — the login page is reachable directly at `http://<group-owner-address>:8080` (the address is also shown on the Home screen while the hotspot runs). Tunneled traffic arrives at the local proxy as SOCKS5 on port 1080; unauthenticated SOCKS5 clients get the portal via a success reply plus redirect. Note: Android deliberately routes tethered/hotspot client traffic around VpnService (there is no public API to include it — verified against API 36), so the OS-native "Login to network" auto-popup and voucher-gated internet forwarding both require the traffic to be forced through the tunnel, which is only possible with root.
4. **Voucher login** — the client submits a voucher code; it is validated against Room (exists, unused, unexpired). On success the voucher is marked used, the client's MAC is resolved from `/proc/net/arp` (fallback `unknown:<ip>`), and the device becomes authenticated — recorded as a LOGIN access log entry.
5. **Authenticated traffic** — subsequent SOCKS5 `CONNECT` requests are parsed (IPv4 / domain / IPv6 destination) and piped bidirectionally to the real internet.
6. **Manage** — generate duration-picked vouchers (FAB on the Vouchers tab), copy codes, revoke devices from the Devices tab (recorded as REVOKED), and review history on the Access Log tab. All lists update reactively.

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

- **Hotspot client traffic cannot be intercepted on non-rooted devices.** Android routes tethered/Wi-Fi Direct client traffic around `VpnService` (no public API changes this), forbids apps from binding privileged ports (53/80/443), and requires root to enable kernel IP forwarding. Consequences: the OS "Sign in to network" popup does not auto-trigger, and authenticated clients do not receive internet through the tunnel. The portal, voucher login, and device/Access-Log management work fully via the direct `http://<group-owner>:8080` page. Closing this gap (forcing client traffic into the TUN, e.g. with iptables `REDIRECT`, like the VPN Hotspot app) requires a rooted device.
- Reading `/proc/net/arp` is restricted on many Android 10+ builds; when unavailable, devices fall back to the `unknown:<ip>` placeholder MAC.
- Only TCP is forwarded (SOCKS5 `CONNECT`); UDP `ASSOCIATE` (used by DNS-over-UDP, QUIC, gaming) is refused with `command not supported`.
- Revocation changes the authentication flag in the database; an already-authenticated client is blocked on its next request rather than being force-disconnected.
- The app is feature-complete but not yet validated end-to-end on physical devices (Wi-Fi Direct and VPN cannot run on emulators). Prefer APKs produced by CI, which include the properly compiled native library.

## Roadmap

- [x] `hev-socks5-tunnel` native routing built from source in CI
- [x] Real TCP forwarding for authenticated clients
- [x] MAC resolution & device fingerprints (ARP)
- [x] Access log UI
- [x] Voucher duration customization in the UI
- [x] Release signing helper + credential-safe gitignore
- [ ] UDP forwarding (SOCKS5 `UDP ASSOCIATE`)
- [ ] On-device end-to-end validation
- [ ] Play Store compliance review
