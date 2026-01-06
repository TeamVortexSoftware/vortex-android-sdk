# Vortex Android SDK

An **invitations-as-a-service** Android SDK that renders dynamic invitation forms configured via the Vortex backend API. Built with Jetpack Compose and Kotlin.

## Features

- 🎨 **Dynamic Form Rendering** - UI driven by backend configuration
- 📧 **Multiple Invitation Methods** - Email, SMS, shareable links, QR codes
- 🔗 **Social Sharing** - WhatsApp, Telegram, LINE, Twitter/X, Instagram, Discord, Facebook Messenger
- 📱 **Contact Import** - Device contacts and Google Contacts integration
- 🎭 **Theming Support** - Solid colors and gradient backgrounds
- 🔒 **JWT Authentication** - Secure API communication

## Requirements

- Android API 24+ (Android 7.0)
- Kotlin 2.0+
- Jetpack Compose

## Installation

### Gradle (Kotlin DSL)

Add the dependency to your app's `build.gradle.kts`:

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

## Quick Start

### Basic Usage

```kotlin
import com.vortexsoftware.android.sdk.ui.VortexInviteView

@Composable
fun MyScreen() {
    var showInviteWidget by remember { mutableStateOf(false) }
    
    Button(onClick = { showInviteWidget = true }) {
        Text("Invite Friends")
    }
    
    if (showInviteWidget) {
        VortexInviteView(
            componentId = "your-widget-id",
            onDismiss = { showInviteWidget = false }
        )
    }
}
```

### With Authentication

```kotlin
VortexInviteView(
    componentId = "your-widget-id",
    jwt = "your-jwt-token",
    onDismiss = { /* handle dismiss */ }
)
```

### With Group Context

```kotlin
import com.vortexsoftware.android.sdk.api.dto.GroupDTO

VortexInviteView(
    componentId = "your-widget-id",
    jwt = "your-jwt-token",
    group = GroupDTO.create(id = "group-123", name = "My Team", type = "team"),
    onDismiss = { /* handle dismiss */ }
)
```

### With Google Contacts Integration

```kotlin
VortexInviteView(
    componentId = "your-widget-id",
    jwt = "your-jwt-token",
    googleClientId = "your-google-oauth-client-id",
    onDismiss = { /* handle dismiss */ }
)
```

### Prefetch for Instant Rendering

The SDK supports prefetching widget configurations to eliminate loading delays when users open the invite form. The cache is **global and transparent** - you just need to prefetch, and `VortexInviteView` will automatically use the cached configuration.

#### Automatic Caching (Zero Code Changes)

After the first load, configurations are automatically cached. Subsequent opens will render instantly from cache while refreshing in the background (stale-while-revalidate pattern):

```kotlin
// First open: shows loading spinner, fetches config, caches it
// Second open: renders instantly from cache, refreshes in background
VortexInviteView(
    componentId = "your-component-id",
    jwt = jwt,
    onDismiss = { /* ... */ }
)
```

#### Manual Prefetch (For Optimal UX)

For the best user experience, prefetch the configuration when the JWT becomes available. The prefetcher populates a global cache that `VortexInviteView` automatically uses:

```kotlin
import com.vortexsoftware.android.sdk.prefetch.VortexConfigurationPrefetcher

// Create prefetcher (e.g., in your ViewModel or Composable)
val prefetcher = VortexConfigurationPrefetcher(
    componentId = "your-component-id"
)

// When JWT becomes available, start prefetching
lifecycleScope.launch {
    prefetcher.prefetch(jwt)
}

// Later, VortexInviteView automatically uses the cached configuration
// No need to pass any configuration parameters!
VortexInviteView(
    componentId = "your-component-id",
    jwt = jwt,
    onDismiss = { /* ... */ }
)
```

#### Prefetcher State Observation

The prefetcher exposes StateFlows for observing prefetch state:

```kotlin
val prefetcher = VortexConfigurationPrefetcher(componentId = "your-component-id")

// Observe loading state
prefetcher.isLoading.collect { isLoading -> /* ... */ }

// Observe errors
prefetcher.error.collect { error -> /* ... */ }

// Check if prefetched
prefetcher.isPrefetched.collect { isPrefetched -> /* ... */ }
```

## Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `componentId` | `String` | Yes | - | Widget ID from Vortex dashboard |
| `jwt` | `String?` | No | `null` | JWT token for authenticated requests |
| `group` | `GroupDTO?` | No | `null` | Group context for invitations |
| `googleClientId` | `String?` | No | `null` | Google OAuth client ID for Google Contacts |
| `onDismiss` | `(() -> Unit)?` | No | `null` | Callback when widget is dismissed |

## Permissions

The SDK requires the following permissions (declared in the SDK's manifest):

```xml
<!-- Required for API calls -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Optional: For importing device contacts -->
<uses-permission android:name="android.permission.READ_CONTACTS" />
```

The `READ_CONTACTS` permission is requested at runtime when the user attempts to import contacts.

## Google Sign-In Setup (Optional)

To enable Google Contacts integration:

1. Create OAuth 2.0 credentials in the [Google Cloud Console](https://console.cloud.google.com/)
2. Enable the People API
3. Add your OAuth client ID to the `VortexInviteView`:

```kotlin
VortexInviteView(
    componentId = "your-widget-id",
    googleClientId = "your-client-id.apps.googleusercontent.com",
    onDismiss = { /* handle dismiss */ }
)
```

## Architecture

The SDK follows a clean architecture pattern:

```
com.vortexsoftware.android.sdk/
├── api/
│   ├── dto/           # Data Transfer Objects
│   ├── VortexClient   # API client (Retrofit)
│   └── VortexError    # Error types
├── models/
│   ├── WidgetConfiguration  # Configuration models
│   ├── VortexContact        # Contact model
│   └── InviteViewState      # View state enum
├── viewmodels/
│   └── VortexInviteViewModel  # Main ViewModel
└── ui/
    ├── components/    # Reusable UI components
    ├── icons/         # Icon handling
    ├── theme/         # Theming utilities
    └── VortexInviteView  # Main entry point
```

## API Endpoints

The SDK communicates with the following Vortex API endpoints:

- `GET /api/v1/widgets/{componentId}` - Fetch widget configuration
- `POST /api/v1/invitations` - Create invitation
- `POST /api/v1/invitations/generate-shareable-link-invite` - Generate shareable link

## Supported Share Methods

| Method | Description |
|--------|-------------|
| `copyLink` | Copy invitation link to clipboard |
| `nativeShareSheet` | Android native share sheet |
| `sms` | Share via SMS |
| `qrCode` | Display QR code |
| `email` | Share via email app |
| `whatsApp` | Share via WhatsApp |
| `telegram` | Share via Telegram |
| `line` | Share via LINE |
| `twitterDms` | Share via Twitter/X |
| `instagramDms` | Share via Instagram |
| `facebookMessenger` | Share via Facebook Messenger |
| `discord` | Share via Discord |

## Theming

The SDK supports theming through the backend configuration. Theme options include:

- Button background colors (solid or gradient)
- Button text colors
- Custom CSS-like variables

Example gradient support:
```
linear-gradient(90deg, #6291d5 0%, #bf8ae0 100%)
```

## Dependencies

- **Jetpack Compose** - UI framework
- **Retrofit** - HTTP client
- **OkHttp** - HTTP client
- **Kotlinx Serialization** - JSON parsing
- **Coil** - Image loading
- **Google Play Services Auth** - Google Sign-In
- **ZXing** - QR code generation

## ProGuard / R8

The SDK includes ProGuard rules automatically. No additional configuration is needed.

## Related Projects

| Project | Description |
|---------|-------------|
| [vortex-ios-sdk](../vortex-ios-sdk) | iOS SDK (Swift/SwiftUI) |
| [vortex-react-native](../vortex-suite/packages/vortex-react-native) | React Native SDK |

## License

Apache License 2.0 - see [LICENSE](LICENSE) file for details.

## Support

For support, please contact [support@vortexsoftware.com](mailto:support@vortexsoftware.com) or visit our [documentation](https://docs.vortexsoftware.com).
