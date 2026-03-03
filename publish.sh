#!/bin/bash
#
# Publish the Vortex Android SDK to Maven Central.
#
# This script automatically loads all required credentials at runtime:
#   - mavenCentralUsername / mavenCentralPassword from `vortex secrets`
#   - GPG private key imported temporarily from `vortex secrets`, then removed after use
#   - signing.gnupg.passphrase from `vortex secrets`
#
# Prerequisites:
#   - `vortex` CLI installed and authenticated
#   - Java 17+
#   - gpg installed
#
# Usage:
#   ./publish.sh                    # Build, sign, and publish
#   ./publish.sh --dry-run          # Build and sign only (no upload)
#

set -euo pipefail

# Change to the directory where this script lives (project root)
cd "$(dirname "$0")"

SECRETS_KEY="providers/maven/service-account/vortexsoftwareops"

# --- Load credentials ---

echo "🔑 Loading Maven Central credentials..."
MAVEN_CENTRAL_USERNAME="$(vortex secrets read -k "$SECRETS_KEY" -p userTokenUsername)"
MAVEN_CENTRAL_PASSWORD="$(vortex secrets read -k "$SECRETS_KEY" -p userTokenPassword)"

echo "🔑 Loading GPG passphrase..."
SIGNING_PASSWORD="$(vortex secrets read -k "$SECRETS_KEY" -p gpgpassphrase)"

# --- Import GPG key temporarily ---

echo "🔑 Importing GPG key from secrets vault..."
GPG_KEY_FILE="$(mktemp)"
vortex secrets read -k "$SECRETS_KEY" -p privateKey > "$GPG_KEY_FILE"

# Fix BEGIN/END markers to be on their own lines (in case they're not)
sed -i.bak 's/\(-----BEGIN PGP PRIVATE KEY BLOCK-----\)/\n\1\n/g; s/\(-----END PGP PRIVATE KEY BLOCK-----\)/\n\1\n/g' "$GPG_KEY_FILE"
rm -f "${GPG_KEY_FILE}.bak"

gpg --batch --import "$GPG_KEY_FILE" 2>/dev/null || true
rm -f "$GPG_KEY_FILE"

# Detect the imported key ID
SIGNING_KEY_ID="$(gpg --list-secret-keys --keyid-format short 2>/dev/null \
    | grep -E "^sec" \
    | head -1 \
    | sed -E 's|.*/([A-F0-9]+).*|\1|')"

if [ -z "$SIGNING_KEY_ID" ]; then
    echo "❌ Failed to import GPG key from secrets vault."
    exit 1
fi

# Cleanup function to remove the GPG key after use
cleanup() {
    echo "🧹 Removing temporary GPG key ($SIGNING_KEY_ID) from keyring..."
    gpg --batch --yes --delete-secret-and-public-key "$(gpg --list-secret-keys --with-colons 2>/dev/null | grep -E "^fpr" | head -1 | cut -d: -f10)" 2>/dev/null || true
}
trap cleanup EXIT

echo "  Maven Central Username: ${MAVEN_CENTRAL_USERNAME:0:4}..."
echo "  GPG Key ID: $SIGNING_KEY_ID"

# --- Determine Gradle task ---

if [ "${1:-}" = "--dry-run" ]; then
    echo ""
    echo "🏗️  Dry run: building and signing only (no upload)..."
    GRADLE_TASK=":vortex-sdk:signMavenPublication"
else
    echo ""
    echo "🚀 Publishing to Maven Central..."
    GRADLE_TASK=":vortex-sdk:publishAndReleaseToMavenCentral"
fi

# --- Run Gradle ---

./gradlew "$GRADLE_TASK" \
    -PmavenCentralUsername="$MAVEN_CENTRAL_USERNAME" \
    -PmavenCentralPassword="$MAVEN_CENTRAL_PASSWORD" \
    -Psigning.gnupg.keyName="$SIGNING_KEY_ID" \
    -Psigning.gnupg.passphrase="$SIGNING_PASSWORD"

echo ""
echo "✅ Done!"
