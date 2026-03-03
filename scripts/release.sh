#!/bin/bash
#
# Release the Vortex Android SDK.
#
# This script:
#   1. Bumps the version on develop
#   2. Merges develop → main (with temporary branch protection toggle)
#   3. Creates a git tag and GitHub release
#   4. Publishes to Maven Central (via publish.sh)
#   5. Bumps develop to the next development version
#
# Usage:
#   ./scripts/release.sh <version> <next-dev-version>
#   ./scripts/release.sh 1.0.0 1.0.1-SNAPSHOT
#
# Prerequisites:
#   - `gh` CLI installed and authenticated
#   - `vortex` CLI installed and authenticated
#   - gpg installed
#   - Java 17+
#
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=$1
NEXT_VERSION=$2

if [ -z "$VERSION" ] || [ -z "$NEXT_VERSION" ]; then
    echo "Usage: ./scripts/release.sh <version> <next-dev-version>"
    echo "Example: ./scripts/release.sh 1.0.0 1.0.1-SNAPSHOT"
    exit 1
fi

REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)

echo "📦 Releasing Vortex Android SDK v$VERSION"
echo "   Next development version: $NEXT_VERSION"
echo "   Repository: $REPO"
echo ""

# --- 1. Work on develop — bump to release version ---

echo "📥 Pulling latest develop..."
git checkout develop
git pull origin develop

echo "📝 Bumping version to $VERSION..."

# gradle.properties — single source of truth for version
sed -i '' "s/^VERSION_NAME=.*/VERSION_NAME=$VERSION/" gradle.properties

# README.md — installation examples
sed -i '' "s/implementation(\"com.vortexsoftware.android:vortex-sdk:.*\")/implementation(\"com.vortexsoftware.android:vortex-sdk:$VERSION\")/" \
    README.md
sed -i '' "s/implementation 'com.vortexsoftware.android:vortex-sdk:.*'/implementation 'com.vortexsoftware.android:vortex-sdk:$VERSION'/" \
    README.md

git add -A
git commit -m "Release v$VERSION"
git push origin develop

# --- 2. Temporarily disable branch protection on main ---

echo "⏳ Disabling branch protection on main..."
gh api -X DELETE "repos/$REPO/branches/main/protection" 2>/dev/null || true

# --- 3. FF-only merge develop → main ---

git checkout main
git pull origin main
git merge --ff-only develop
git push origin main

# --- 4. Re-enable branch protection ---

echo "🔒 Re-enabling branch protection on main..."
gh api -X PUT "repos/$REPO/branches/main/protection" \
  --input - <<'EOF'
{
  "required_status_checks": null,
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1
  },
  "restrictions": null
}
EOF

# --- 5. Tag and create GitHub release ---

echo "🏷️  Creating tag v$VERSION..."
git tag -a "$VERSION" -m "Release $VERSION"
git push origin "$VERSION"

echo "📋 Creating GitHub release..."
gh release create "$VERSION" \
    --title "v$VERSION" \
    --generate-notes

# --- 6. Publish to Maven Central ---

echo "🚀 Publishing to Maven Central..."
./publish.sh

# --- 7. Bump develop to next development version ---

echo "📝 Bumping to next development version ($NEXT_VERSION)..."
git checkout develop

sed -i '' "s/^VERSION_NAME=.*/VERSION_NAME=$NEXT_VERSION/" gradle.properties

git add -A
git commit -m "Bump version to $NEXT_VERSION for development"
git push origin develop

echo ""
echo "✅ Released v$VERSION"
echo "   - main and develop are in sync at release commit"
echo "   - Tag $VERSION created"
echo "   - GitHub Release created"
echo "   - Published to Maven Central"
echo "   - Branch protection re-enabled"
echo "   - develop bumped to $NEXT_VERSION"
