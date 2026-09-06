#!/usr/bin/env bash
# Generates a local release keystore plus app/keystore.properties so that
# ./gradlew :app:assembleRelease produces a signed APK.
# Usage: ./generate-keystore.sh
set -euo pipefail

cd "$(dirname "$0")"

KEYSTORE_FILE="release.keystore"
PROPERTIES_FILE="app/keystore.properties"
ALIAS="tethervault"
STORE_PASSWORD="${KEYSTORE_PASSWORD:-tethervault123}"
KEY_PASSWORD="${KEY_PASSWORD:-tethervault123}"

if ! command -v keytool >/dev/null 2>&1; then
    echo "ERROR: keytool not found. Install a JDK first." >&2
    exit 1
fi

if [ -f "$KEYSTORE_FILE" ]; then
    echo "ERROR: $KEYSTORE_FILE already exists. Delete it first to regenerate." >&2
    exit 1
fi

keytool -genkeypair -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass "$STORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "CN=TetherVault, OU=Mobile, O=TetherVault, L=Jakarta, S=Jakarta, C=ID"

cat > "$PROPERTIES_FILE" <<EOF
storeFile=../$KEYSTORE_FILE
storePassword=$STORE_PASSWORD
keyAlias=$ALIAS
keyPassword=$KEY_PASSWORD
EOF

echo "Keystore and properties generated:"
ls -la "$KEYSTORE_FILE" "$PROPERTIES_FILE"
echo "Next: ./gradlew :app:assembleRelease"
