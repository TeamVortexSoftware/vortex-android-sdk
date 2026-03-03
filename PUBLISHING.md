# Publishing the Vortex Android SDK to Maven Central

This guide walks you through publishing the Vortex Android SDK to Maven Central so users can install it with Gradle.

## Overview

Maven Central is the primary repository for Java/Kotlin artifacts. Publishing requires:
- Gradle build configuration with required metadata (already configured)
- GPG signing of artifacts
- Sonatype account and domain verification
- Sources and Javadoc JARs (handled automatically by the vanniktech plugin + Dokka)

## First Time Setup (Already Done)

The `com.vortexsoftware` namespace has already been verified on Sonatype for the Java SDK. The Android SDK publishes under `com.vortexsoftware.android` which is covered by the same domain verification. **You do not need to re-verify the domain.**

### 1. Sonatype Account

The Sonatype account and domain verification for `com.vortexsoftware` are already set up. No action needed.

### 2. GPG Key for Signing (Already Exists)

The GPG key is shared with the Java SDK and stored in the secrets vault. **You do not need to import it manually** — the `publish.sh` script imports it temporarily at runtime and removes it from your keyring after use.

## Prerequisites

### 1. Credentials

All credentials are loaded **automatically at runtime** by the `publish.sh` script (see [Publishing Process](#publishing-process)). You do **not** need to configure `~/.gradle/gradle.properties` manually.

The script fetches:
- **`mavenCentralUsername`** / **`mavenCentralPassword`** — from `vortex secrets read` (`userTokenUsername` / `userTokenPassword`)
- **GPG private key** — imported temporarily from `vortex secrets read` (`privateKey`), then **removed from the keyring** after use
- **`signing.gnupg.passphrase`** (GPG passphrase) — from `vortex secrets read` (`gpgpassphrase`)
- **`signing.gnupg.keyName`** — auto-detected from the temporarily imported GPG key

<details>
<summary>Manual setup (alternative — only if not using publish.sh)</summary>

Create or update `~/.gradle/gradle.properties`:

```properties
mavenCentralUsername=YOUR_USER_TOKEN_USERNAME
mavenCentralPassword=YOUR_USER_TOKEN_PASSWORD
signing.keyId=GPG_KEY_ID
signing.password=GPG_PASSPHRASE
signing.secretKeyRingFile=/Users/YOUR_USER/.gnupg/secring.gpg
```

Obtain the values:

```bash
# mavenCentralUsername:
vortex secrets read -k providers/maven/service-account/vortexsoftwareops -p userTokenUsername

# mavenCentralPassword:
vortex secrets read -k providers/maven/service-account/vortexsoftwareops -p userTokenPassword

# signing.password (GPG passphrase):
vortex secrets read -k providers/maven/service-account/vortexsoftwareops -p gpgpassphrase

# signing.keyId:
gpg --list-keys --keyid-format short
# Look for the short ID after "pub rsa.../XXXXXXXX"
```

**Security Note**: Never commit credentials to Git.

</details>

### 3. Install Java 17+

```bash
# macOS
brew install openjdk@17

# Verify
java --version
```

## Build Configuration

The publishing configuration is already set up in the project:

- **`gradle/libs.versions.toml`**: Declares Dokka and vanniktech maven-publish plugin versions
- **`build.gradle.kts`** (root): Applies plugins with `apply false`
- **`vortex-sdk/build.gradle.kts`**: Contains the full publishing configuration:
  - ✅ Dokka plugin for KDoc → Javadoc generation
  - ✅ vanniktech maven-publish plugin for Maven Central publishing
  - ✅ GPG signing of all publications
  - ✅ POM metadata (license, developer info, SCM URLs)
  - ✅ Coordinates: `com.vortexsoftware.android:vortex-sdk:1.0.0`

## Publishing Process

### Step 1: Update Version

Edit `vortex-sdk/build.gradle.kts` and update the version in the `coordinates()` call:

```kotlin
coordinates("com.vortexsoftware.android", "vortex-sdk", "1.0.0")
```

### Step 2: Build and Test

```bash
# Clean previous builds
./gradlew clean

# Build the SDK
./gradlew :vortex-sdk:build

# Generate KDoc documentation (optional, to verify)
./gradlew :vortex-sdk:dokkaHtml
# Output: vortex-sdk/build/dokka/html/index.html
```

### Step 3: Deploy to Maven Central

Use the `publish.sh` script, which automatically loads all credentials at runtime:

```bash
# Build, sign, and publish to Maven Central
./publish.sh

# Or dry-run (build and sign only, no upload)
./publish.sh --dry-run
```

The script:
1. Fetches `mavenCentralUsername` / `mavenCentralPassword` via `vortex secrets read`
2. Fetches the GPG passphrase via `vortex secrets read`
3. Auto-detects the GPG key ID from your local keyring
4. Runs `./gradlew :vortex-sdk:publishAndReleaseToMavenCentral` with all credentials passed as Gradle properties

This will build the AAR, generate sources + Javadoc JARs (via Dokka), sign all artifacts with GPG, upload to Sonatype Central Portal, and automatically release.

### Step 4: Verify Publication

After 15-30 minutes, verify your artifact is available:

- [Maven Central Search](https://search.maven.org/)
- Direct URL: `https://central.sonatype.com/artifact/com.vortexsoftware.android/vortex-sdk`

## Installation for Users

Once published, users can add the dependency:

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.vortexsoftware.android:vortex-sdk:1.0.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.vortexsoftware.android:vortex-sdk:1.0.0'
}
```

## Automated Publishing with GitHub Actions

Create `.github/workflows/publish.yml`:

```yaml
name: Publish Android SDK

on:
  release:
    types: [published]
  workflow_dispatch:
    inputs:
      version:
        description: 'Version to publish'
        required: true

jobs:
  publish:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'zulu'

      - name: Publish to Maven Central
        run: ./gradlew :vortex-sdk:publishAndReleaseToMavenCentral
        env:
          ORG_GRADLE_PROJECT_mavenCentralUsername: ${{ secrets.MAVEN_CENTRAL_USERNAME }}
          ORG_GRADLE_PROJECT_mavenCentralPassword: ${{ secrets.MAVEN_CENTRAL_PASSWORD }}
          ORG_GRADLE_PROJECT_signingInMemoryKey: ${{ secrets.GPG_SIGNING_KEY }}
          ORG_GRADLE_PROJECT_signingInMemoryKeyPassword: ${{ secrets.GPG_SIGNING_PASSWORD }}
```

### GitHub Secrets Setup

Add these secrets to your repository (Settings → Secrets and variables → Actions):

1. `MAVEN_CENTRAL_USERNAME` - Sonatype user token username
2. `MAVEN_CENTRAL_PASSWORD` - Sonatype user token password
3. `GPG_SIGNING_KEY` - GPG private key in ASCII armor format (`gpg --export-secret-keys --armor KEY_ID`)
4. `GPG_SIGNING_PASSWORD` - GPG passphrase

You can obtain these from the Vortex secrets manager:

```bash
# Sonatype credentials
vortex secrets read -k providers/maven/service-account/vortexsoftwareops -p userTokenSettingsXML

# GPG key
vortex secrets read -k providers/maven/service-account/vortexsoftwareops -p privateKey

# GPG passphrase
vortex secrets read -k providers/maven/service-account/vortexsoftwareops -p gpgpassphrase
```

## Version Management

Follow [Semantic Versioning](https://semver.org/):

- **MAJOR** (1.0.0 → 2.0.0): Breaking API changes
- **MINOR** (1.0.0 → 1.1.0): New features, backward compatible
- **PATCH** (1.0.0 → 1.0.1): Bug fixes, backward compatible

## Generating API Documentation

The SDK uses [Dokka](https://github.com/Kotlin/dokka) to generate API documentation from KDoc comments:

```bash
# Generate HTML documentation
./gradlew :vortex-sdk:dokkaHtml
# Output: vortex-sdk/build/dokka/html/index.html

# Generate Javadoc-style documentation
./gradlew :vortex-sdk:dokkaJavadoc
# Output: vortex-sdk/build/dokka/javadoc/index.html
```

The vanniktech plugin automatically uses Dokka to generate the `-javadoc.jar` required by Maven Central.

## Differences from the Java SDK

| Aspect | Java SDK | Android SDK |
|--------|----------|-------------|
| Build tool | Maven (`pom.xml`) | Gradle (`build.gradle.kts`) |
| Publishing plugin | `central-publishing-maven-plugin` | `com.vanniktech.maven.publish` |
| Doc generation | `maven-javadoc-plugin` | Dokka |
| Deploy command | `mvn clean deploy -P gpg` | `./gradlew :vortex-sdk:publishAndReleaseToMavenCentral` |
| Manual Nexus steps | Yes (close → release) | No (automatic) |
| Bundle script | `create-maven-central-bundle.sh` | Not needed |
| Group ID | `com.vortexsoftware` | `com.vortexsoftware.android` |
| Sonatype account | Shared | Shared |
| GPG key | Shared | Shared |

## Release Checklist

- [ ] Update version in `vortex-sdk/build.gradle.kts` (`coordinates()`)
- [ ] Update version in `README.md` (installation section)
- [ ] Update `CHANGELOG.md`
- [ ] Run all tests: `./gradlew :vortex-sdk:test`
- [ ] Build locally: `./gradlew :vortex-sdk:build`
- [ ] Generate docs: `./gradlew :vortex-sdk:dokkaHtml`
- [ ] Commit version changes
- [ ] Create Git tag: `git tag v1.0.0`
- [ ] Push changes and tag
- [ ] Publish: `./gradlew :vortex-sdk:publishAndReleaseToMavenCentral`
- [ ] Verify on Maven Central (wait 15-30 min)
- [ ] Create GitHub release
- [ ] Announce release

## Troubleshooting

### GPG Signing Issues

```bash
# Test GPG signing
echo "test" | gpg --clearsign

# If "no secret key" error
gpg --list-secret-keys

# Verify key is on keyserver
gpg --keyserver keyserver.ubuntu.com --recv-keys YOUR_KEY_ID
```

### Publishing Failures

1. **401 Unauthorized**: Check Sonatype credentials in `~/.gradle/gradle.properties`
2. **Missing metadata**: Verify POM configuration in `build.gradle.kts`
3. **GPG errors**: Ensure key is imported and passphrase is correct
4. **Validation errors**: Run `./gradlew :vortex-sdk:publishToMavenCentral` (without release) to debug

### Build Issues

```bash
# Clean build
./gradlew clean :vortex-sdk:build

# Verbose output
./gradlew :vortex-sdk:publishAndReleaseToMavenCentral --info
```

## Resources

- [vanniktech/gradle-maven-publish-plugin](https://github.com/vanniktech/gradle-maven-publish-plugin)
- [Dokka Documentation](https://kotlin.github.io/dokka/)
- [Maven Central Publishing Guide](https://central.sonatype.org/publish/)
- [Central Portal](https://central.sonatype.com/)

## Support

For publishing issues:
- Check [Sonatype Status](https://status.maven.org/)
- Ask on [Sonatype Community](https://community.sonatype.com/)

For SDK issues:
- Create an issue on GitHub
- Contact support@vortexsoftware.com
