# Vortex Android SDK - Development Guidelines

## What is Vortex?

Vortex is an **invitations-as-a-service** platform. We provide SDKs that render dynamic invitation forms configured via a backend API. The forms support multiple invitation methods (email, SMS, shareable links, QR codes, social sharing) and can import contacts from device or Google.

The SDK fetches a `WidgetConfiguration` from the Vortex API, which contains a tree of `ElementNode` objects that define the UI structure. The SDK renders this configuration dynamically using Jetpack Compose.

## Related Projects

| Project | Path                                                            | Description |
|---------|-----------------------------------------------------------------|-------------|
| **Android SDK** | `.` (this repo)                                                 | This repository - Kotlin/Jetpack Compose SDK |
| **Android Demo App** | (separate repo: `../vortex-android-demo`)                       | Demo app called "Acme Tasks" |
| **iOS SDK** | (separate repo: `../vortex-ios-sdk`)                            | iOS SDK (Swift/SwiftUI) |
| **iOS Demo App** | (separate repo: `../vortex-ios-demo`)                           | iOS demo app |
| **React Native SDK** | (separate repo: `../vortex-suite/packages/vortex-react-native`) | RN SDK (reference implementation) |
| **React Native Demo** | (separate repo: `../vortex-suite/standalone/demo-react-native`) | RN demo app |
| **Shared UI Code** | (separate repo: `../vortex-suite/packages/vortex-shared-ui`)    | Shared UI components used by RN SDK |

**Important:** The React Native SDK is the reference implementation. When implementing features or fixing bugs, check how the RN SDK handles it for consistency. The iOS SDK can also be used as a reference for platform-specific patterns.

## Android SDK Code Organization

```
vortex-sdk/src/main/java/com.vortexsoftware.android/sdk/
├── api/
│   ├── dto/
│   │   └── APIResponses.kt         # Response DTOs (WidgetConfigurationResponse, CreateInvitationResponse, etc.)
│   ├── VortexClient.kt             # Main API client for backend communication
│   └── VortexError.kt              # Error types (VortexError sealed class)
├── models/
│   ├── InviteViewState.kt          # View state enum (Main, EmailEntry, ContactsPicker, etc.)
│   ├── VortexContact.kt            # Contact model for device/Google contacts
│   └── WidgetConfiguration.kt      # Configuration models (ElementNode, Theme, etc.)
├── viewmodels/
│   └── VortexInviteViewModel.kt    # Main ViewModel - handles all business logic
├── ui/
│   ├── components/
│   │   ├── ContactComponents.kt    # ContactsImportView, ContactsPickerView, GoogleContactsPickerView
│   │   ├── FormComponents.kt       # Form elements (Textbox, Select, Radio, Checkbox, etc.)
│   │   └── ShareComponents.kt      # ShareOptionsView, ShareButton, EmailPillView
│   ├── VortexIcon.kt               # Icon component using Material Icons
│   ├── VortexTheme.kt              # Theme and styling utilities
│   └── VortexInviteView.kt         # Main entry point composable
└── VortexSDK.kt                    # SDK version info and namespace
```

## Key Architectural Patterns

### Dynamic Form Rendering
The SDK uses a recursive rendering approach with Jetpack Compose:
- `VortexInviteView` → `FormView()` → `RenderRow()` → `RenderColumn()` → `RenderBlock()`
- `RenderBlock()` uses a `when` expression on `block.subtype` to render the appropriate composable
- Uses `@Composable` functions for type-safe UI composition

### State Management
- `VortexInviteViewModel` is the single source of truth
- Uses `StateFlow` and `MutableStateFlow` for reactive UI updates
- Coroutines with `viewModelScope` for async operations
- State hoisting pattern for composables

### Configuration-Driven UI
- All UI is driven by `WidgetConfiguration` fetched from API
- `ElementNode` tree structure mirrors the RN SDK's approach
- Feature flags (e.g., `isCopyLinkEnabled`, `isSmsEnabled`) are derived from configuration

## Dependencies

- **Jetpack Compose**: UI toolkit (BOM 2024.02.00+)
- **Kotlin Coroutines**: Async operations
- **Kotlinx Serialization**: JSON parsing
- **OkHttp**: HTTP client
- **Google Play Services Auth**: For Google Sign-In (Google Contacts integration)
- **Material Icons Extended**: For icons

## Important Notes for Future Sessions

1. **No debug logs in production**: Use `Log.d()` only in debug builds. Consider using Timber for better logging.

2. **Test file is placeholder**: Unit tests should be added for ViewModel and API client.

3. **Compose performance**: Avoid unnecessary recompositions by using `remember`, `derivedStateOf`, and stable types.

4. **Google Sign-In setup**: Requires `googleClientId` parameter and proper OAuth configuration in Google Cloud Console.

5. **Minimum Android version**: API 24 (Android 7.0) - set in build.gradle.kts

6. **Build command**: Use Android Studio or `./gradlew :vortex-sdk:assembleRelease`

## API Endpoints Used

- `GET /api/v1/widgets/{componentId}` - Fetch widget configuration
- `POST /api/v1/invitations` - Create invitation
- `POST /api/v1/invitations/generate-shareable-link-invite` - Get shareable link

## Common Tasks

### Adding a new element type
1. Add case to `RenderBlock()` in `VortexInviteView.kt`
2. Create composable in appropriate file under `ui/components/`
3. Check RN SDK and iOS SDK for reference implementation

### Adding a new share method
1. Add feature flag computed property in `VortexInviteViewModel`
2. Add action method in ViewModel
3. Add button in `ShareOptionsView`
4. Add icon to `VortexIconName` enum if needed

### Building the SDK
```bash
# Debug build
./gradlew :vortex-sdk:assembleDebug

# Release build
./gradlew :vortex-sdk:assembleRelease

# Publish to local Maven
./gradlew :vortex-sdk:publishToMavenLocal
```

### Using the SDK in a project
Add to your app's `build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.vortexsoftware.android:vortex-sdk:1.0.0")
}
```

Or use composite build for local development (see demo app for example).
