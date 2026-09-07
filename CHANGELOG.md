# Changelog

_Generated automatically from commit messages. Do not edit by hand._

## Unreleased

### Changes
- Realign to a one-button hotspot flow with QR portal access
- Make the Open-mode passphrase a configurable shared public password
- Serve the captive portal on port 8080 directly from the hotspot
- Trigger the OS captive portal flow with voucher login redirects
- Make hotspot SSID and security configurable with voucher-only gating
- Update README to match the current implementation
- Turn the local proxy into a real bidirectional TCP forwarder
- Align tun2socks JNI bridge with the real upstream API
- Build tun2socks native library from source with NDK in CI
- Integrate hev-socks5-tunnel native library and SOCKS5 portal flow
- Add release signing config with keystore helper script
- Resolve client MAC addresses from the kernel ARP table
- Add voucher duration options and access log UI
- Add CircleCI pipeline with dynamic tun2socks setup
- Add TetherVault Android app: voucher-based hotspot with captive portal
- Initial commit

