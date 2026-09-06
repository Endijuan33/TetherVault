#!/usr/bin/env bash
# Downloads the pre-built hev-socks5-tunnel Android binaries and installs
# them as libhev-socks5-tunnel.so for both device ABIs.
#
# Note: upstream publishes one binary per ABI (not a single android.zip),
# e.g. https://github.com/heiher/hev-socks5-tunnel/releases/latest/download/
#      hev-socks5-tunnel-android-arm64-v8a
set -euo pipefail

cd "$(dirname "$0")"

BASE_URL="${HEV_SOCKS5_TUNNEL_BASE_URL:-https://github.com/heiher/hev-socks5-tunnel/releases/latest/download}"

install_abi() {
    local abi="$1"
    local dest_dir="app/src/main/jniLibs/$abi"
    local dest="$dest_dir/libhev-socks5-tunnel.so"
    local tmp
    tmp="$(mktemp)"

    echo "==> $abi"
    if ! curl -fsSL --retry 2 --connect-timeout 15 --max-time 300 \
        -o "$tmp" "$BASE_URL/hev-socks5-tunnel-android-$abi"; then
        echo "  -> download failed"
        rm -f "$tmp"
        return 1
    fi

    local magic
    magic="$(head -c 4 "$tmp" | od -An -tx1 | tr -d ' \n')"
    if [ "$magic" != "7f454c46" ]; then
        echo "  -> not an ELF binary (magic: $magic)"
        rm -f "$tmp"
        return 1
    fi

    mkdir -p "$dest_dir"
    mv "$tmp" "$dest"
    echo "  -> installed $dest"
}

FAILURES=0
install_abi arm64-v8a || FAILURES=$((FAILURES + 1))
install_abi armeabi-v7a || FAILURES=$((FAILURES + 1))

if [ "$FAILURES" -ge 2 ]; then
    echo "ERROR: no native library could be installed." >&2
    exit 1
fi

echo "Native libraries ready."
