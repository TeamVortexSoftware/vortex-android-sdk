# Vortex Android SDK

An **invitations-as-a-service** Android SDK that renders dynamic invitation forms configured via the Vortex backend API. Built with Jetpack Compose and Kotlin.

## Features

- 🎨 **Dynamic Form Rendering** - UI driven by backend configuration
- 📧 **Multiple Invitation Methods** - Email, SMS, shareable links, QR codes
- 🔗 **Social Sharing** - WhatsApp, Telegram, LINE, Twitter/X, Instagram, Discord, Facebook Messenger
- 📱 **Contact Import** - Device contacts and Google Contacts integration
- 👥 **Find Friends** - Invite users by internal ID with Connect/Invite buttons
- 💬 **SMS Invitations** - Invite contacts via SMS with in-app composer
- 💡 **Invitation Suggestions** - Display suggested contacts with invite/dismiss actions
- 📥 **Incoming Invitations** - Accept or delete received invitations
- 📤 **Outgoing Invitations** - View and cancel sent invitations
- 🔍 **Search Box** - Search and invite users with a configurable search component
- 🔗 **Deferred Deep Links** - Fingerprint matching for post-install attribution
- 🌍 **Internationalization** - Locale support for multi-language widgets
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
    implementation("com.vortexsoftware.android:vortex-sdk:1.0.8")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.vortexsoftware.android:vortex-sdk:1.0.8'
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

### With Scope Context

Use `scope` and `scopeType` convenience parameters to scope invitations to a team, project, or other entity:

```kotlin
VortexInviteView(
    componentId = "your-widget-id",
    jwt = "your-jwt-token",
    scope = "team-123",
    scopeType = "team",
    onDismiss = { /* handle dismiss */ }
)
```

Alternatively, you can pass a `GroupDTO` directly for more control:

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

### Invite Contacts

The Invite Contacts component displays a list of contacts that can be invited via SMS. Unlike the Contact Import feature (which fetches contacts from the device), Invite Contacts receives a pre-populated list of contacts from your app.

**Basic Usage:**

```kotlin
import com.vortexsoftware.android.sdk.models.InviteContactsConfig
import com.vortexsoftware.android.sdk.models.InviteContactsContact

VortexInviteView(
    componentId = "your-component-id",
    jwt = jwt,
    inviteContactsConfig = InviteContactsConfig(
        contacts = listOf(
            InviteContactsContact(id = "1", name = "Alice Johnson", phoneNumber = "+1 (555) 123-4567"),
            InviteContactsContact(id = "2", name = "Bob Smith", phoneNumber = "+1 (555) 234-5678"),
            InviteContactsContact(id = "3", name = "Carol Davis", phoneNumber = "+1 (555) 345-6789")
        )
    ),
    onDismiss = { /* ... */ }
)
```

**How It Works:**

1. The component shows an "Invite your contacts" entry with a contact count
2. Tapping it navigates to a searchable list of contacts
3. Each contact has an "Invite" button that:
   - Creates an SMS invitation via the Vortex API
   - Opens the in-app SMS composer (on supported devices)
   - Pre-fills the message with the invitation link

**InviteContactsContact Properties:**

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `id` | `String` | Yes | Unique identifier for the contact |
| `name` | `String` | Yes | Display name |
| `phoneNumber` | `String` | Yes | Phone number for SMS |
| `avatarUrl` | `String?` | No | Avatar image URL |

**InviteContactsConfig Properties:**

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `contacts` | `List<InviteContactsContact>` | Yes | List of contacts to display |
| `onInvite` | `suspend (InviteContactsContact, String?) -> Unit` | No | Called after SMS invitation is created. Receives the contact and the short link. |

### Find Friends

The Find Friends component displays a list of contacts provided by your app. Each contact has a "Connect" button that creates an invitation via the Vortex backend.

**Basic Usage:**

```kotlin
import com.vortexsoftware.android.sdk.models.FindFriendsConfig
import com.vortexsoftware.android.sdk.models.FindFriendsContact

VortexInviteView(
    componentId = "your-component-id",
    jwt = jwt,
    findFriendsConfig = FindFriendsConfig(
        contacts = listOf(
            FindFriendsContact(internalId = "user-123", name = "Alice Johnson", subtitle = "@alice"),
            FindFriendsContact(internalId = "user-456", name = "Bob Smith", subtitle = "@bob")
        ),
        onInvitationCreated = { contact ->
            // Called after an invitation is successfully created
            // Use this to trigger in-app notifications
            println("Invitation created for ${contact.name}")
        }
    ),
    onDismiss = { /* ... */ }
)
```

**How It Works:**

1. Your app provides a list of contacts with user IDs (users already in your platform)
2. The component displays them with a "Connect" button (text configurable via widget config)
3. When the user taps "Connect", the SDK creates an invitation via the Vortex API
4. The `onInvitationCreated` callback is called after a successful invitation
5. The section is hidden when there are no contacts to display
6. Contacts that already have an outstanding outgoing invitation are automatically filtered out (matched by `userId`)

**FindFriendsContact Properties:**

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `internalId` | `String` | Yes | User ID in your platform |
| `name` | `String` | Yes | Display name |
| `subtitle` | `String?` | No | Secondary text (e.g., username) |
| `avatarUrl` | `String?` | No | Avatar image URL |
| `metadata` | `Map<String, Any>?` | No | Custom metadata (included in invitation payload) |

**FindFriendsConfig Properties:**

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `contacts` | `List<FindFriendsContact>` | Yes | List of contacts to display |
| `onInvitationCreated` | `suspend (FindFriendsContact) -> Unit` | No | Called after successful invitation |

### Invitation Suggestions

The Invitation Suggestions component displays a list of suggested contacts provided by your app. Each contact has an "Invite" button and a dismiss (X) button. When the user taps "Invite", an invitation is created via the Vortex backend. The dismiss button removes the suggestion from the list.

**Basic Usage:**

```kotlin
import com.vortexsoftware.android.sdk.models.InvitationSuggestionsConfig
import com.vortexsoftware.android.sdk.models.InvitationSuggestionContact

VortexInviteView(
    componentId = "your-component-id",
    jwt = jwt,
    invitationSuggestionsConfig = InvitationSuggestionsConfig(
        suggestions = listOf(
            InvitationSuggestionContact(
                id = "1",
                name = "Henry",
                email = "henry@example.com",
                reason = "Works in your department"
            )
        ),
        onInvite = { suggestion ->
            // Called after invitation succeeds
            println("Invitation created for ${suggestion.name}")
        },
        onDismiss = { suggestion ->
            // Handle dismissal in your backend
            println("Dismissed suggestion for ${suggestion.name}")
        }
    ),
    onDismiss = { /* ... */ }
)
```

**How It Works:**

1. Your app provides a list of suggested contacts with user IDs
2. The component displays them with an "Invite" button and a dismiss (X) button
3. When the user taps "Invite", the SDK creates an invitation via the Vortex API
4. The `onInvite` callback is called after a successful invitation
5. When the user taps the X button, the `onDismiss` callback is called and the contact is removed from the list
6. Contacts that already have an outstanding outgoing invitation are automatically filtered out (matched by `userId`)

**InvitationSuggestionContact Properties:**

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `id` | `String` | Yes | Unique identifier |
| `name` | `String` | Yes | Display name |
| `email` | `String` | Yes | Email address for the invitation |
| `avatarUrl` | `String?` | No | Avatar image URL |
| `reason` | `String?` | No | Reason why this contact is suggested |
| `metadata` | `Map<String, Any>?` | No | Custom metadata |

**InvitationSuggestionsConfig Properties:**

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `suggestions` | `List<InvitationSuggestionContact>` | Yes | List of suggested contacts |
| `onInvite` | `suspend (InvitationSuggestionContact) -> Unit` | No | Called after successful invitation |
| `onDismiss` | `suspend (InvitationSuggestionContact) -> Unit` | No | Called when user dismisses a suggestion |

### Search Box

The Search Box component displays a search input with a search button. When the user taps the search button, your app's `onSearch` callback is invoked to return matching contacts. The results are rendered below the search box with "Connect" buttons, identical to the Find Friends component.

**Basic Usage:**

```kotlin
import com.vortexsoftware.android.sdk.models.SearchBoxConfig
import com.vortexsoftware.android.sdk.models.SearchBoxContact

VortexInviteView(
    componentId = "your-component-id",
    jwt = jwt,
    searchBoxConfig = SearchBoxConfig(
        onSearch = { query ->
            // Return matching contacts from your backend or local data
            val results = myAPI.searchUsers(query)
            results.map { user ->
                SearchBoxContact(
                    userId = user.id,
                    name = user.displayName,
                    subtitle = "@${user.username}",
                    avatarUrl = user.avatarUrl
                )
            }
        },
        onInvitationCreated = { contact ->
            println("Invitation created for ${contact.name}")
        }
    ),
    onDismiss = { /* ... */ }
)
```

**How It Works:**

1. The component renders a search text field with a configurable placeholder and a search button
2. An optional title can be displayed above the search box (configured in the widget editor)
3. When the user taps the search button, your `onSearch` callback is called with the query string
4. Your callback returns a list of `SearchBoxContact` objects
5. The matching contacts are rendered below the search box with a "Connect" button next to each
6. If the search returns no results, a configurable "no results" message is displayed
7. When the user taps "Connect", the SDK creates an invitation via the Vortex API and the contact is removed from the list (identical to Find Friends behavior)
8. Contacts that already have an outstanding outgoing invitation are automatically filtered out (matched by `userId`)

**SearchBoxContact Properties:**

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `userId` | `String` | Yes | User ID in your platform |
| `name` | `String` | Yes | Display name |
| `subtitle` | `String?` | No | Secondary text (e.g., username) |
| `avatarUrl` | `String?` | No | Avatar image URL |
| `metadata` | `Map<String, Any>?` | No | Custom metadata |

**SearchBoxConfig Properties:**

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `onSearch` | `suspend (String) -> List<SearchBoxContact>` | Yes | Search callback returning matching contacts |
| `onInvitationCreated` | `(SearchBoxContact) -> Unit` | No | Called after successful invitation |

> **Note:** The placeholder text, connect button text, no-results message, and title are all configurable from the widget editor.

### Incoming Invitations

The Incoming Invitations component displays invitations the user has received, with Accept and Delete actions.

**Basic Usage:**

```kotlin
import com.vortexsoftware.android.sdk.models.IncomingInvitationsConfig

VortexInviteView(
    componentId = "your-component-id",
    jwt = jwt,
    incomingInvitationsConfig = IncomingInvitationsConfig(
        onAccept = { invitation ->
            // Handle acceptance (return true to proceed with API call)
            true
        },
        onDelete = { invitation ->
            // Handle deletion (return true to proceed with API call)
            true
        }
    ),
    onDismiss = { /* ... */ }
)
```

**With Internal Invitations:**

You can merge your app's invitations with Vortex API invitations. The SDK deduplicates by `userId` — if an API invitation and an internal invitation share the same `userId`, the API one is kept (since it supports server-side accept/delete).

```kotlin
import com.vortexsoftware.android.sdk.models.IncomingInvitationsConfig
import com.vortexsoftware.android.sdk.models.IncomingInvitationItem

IncomingInvitationsConfig(
    internalInvitations = listOf(
        IncomingInvitationItem(
            id = "internal-1",
            name = "Alice Johnson",
            avatarUrl = "https://example.com/avatar.jpg",
            isVortexInvitation = false,
            metadata = mapOf("inviter_handle" to "@alice")
        )
    ),
    onAccept = { invitation ->
        if (invitation.isVortexInvitation) {
            // Vortex invitation: return true to let SDK call the Vortex API
            true
        } else {
            // Internal/app invitation: handle it yourself
            myAPI.acceptInvitation(invitation.id)
            true  // Return true to remove from list
        }
    },
    onDelete = { invitation ->
        if (invitation.isVortexInvitation) {
            true  // Let SDK handle the API call
        } else {
            myAPI.deleteInvitation(invitation.id)
            true  // Return true to remove from list
        }
    },
    getSubtitle = { invitation ->
        // Return a custom subtitle derived from metadata
        invitation.metadata?.get("inviter_handle") as? String
    }
)
```

**Identifying Invitation Source:**

Use the `isVortexInvitation` property to determine where an invitation came from:
- `true`: Fetched from the Vortex API — the SDK will handle accept/delete API calls
- `false`: Provided by your app via `internalInvitations` — your app must handle the action

**IncomingInvitationItem Properties:**

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `id` | `String` | Yes | Unique identifier |
| `name` | `String` | Yes | Display name of the sender |
| `subtitle` | `String?` | No | Secondary text |
| `avatarUrl` | `String?` | No | Avatar image URL |
| `isVortexInvitation` | `Boolean` | No | Source: `true` = Vortex API, `false` = your app. Default: `false` |
| `invitation` | `IncomingInvitation?` | No | Raw API invitation data (null for internal invitations) |
| `metadata` | `Map<String, Any>?` | No | Custom metadata |

**IncomingInvitationsConfig Properties:**

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `internalInvitations` | `List<IncomingInvitationItem>?` | No | App-provided invitations (`isVortexInvitation = false`) |
| `onAccept` | `suspend (IncomingInvitationItem) -> Boolean` | No | Called when user accepts |
| `onDelete` | `suspend (IncomingInvitationItem) -> Boolean` | No | Called when user deletes |
| `getSubtitle` | `(IncomingInvitationItem) -> String?` | No | Compute subtitle from metadata |

**Callback Return Values:**

| Invitation Source | Return `true` | Return `false` |
|-------------------|---------------|----------------|
| Vortex (`isVortexInvitation == true`) | SDK calls Vortex API, removes from list | Cancels the action |
| Internal (`isVortexInvitation == false`) | Removes from list (no API call) | Keeps in list |

### Outgoing Invitations

The Outgoing Invitations component displays invitations the user has sent, with a Cancel action.

**Basic Usage:**

```kotlin
import com.vortexsoftware.android.sdk.models.OutgoingInvitationsConfig

VortexInviteView(
    componentId = "your-component-id",
    jwt = jwt,
    outgoingInvitationsConfig = OutgoingInvitationsConfig(
        onCancel = { invitation ->
            // Handle cancellation
            println("Cancelled invitation to ${invitation.name}")
        }
    ),
    onDismiss = { /* ... */ }
)
```

**With Internal Invitations:**

You can merge your app's outgoing invitations with Vortex API invitations. The SDK deduplicates by `userId` — if an API invitation and an internal invitation share the same `userId`, the API one is kept (since it supports server-side revocation).

```kotlin
import com.vortexsoftware.android.sdk.models.OutgoingInvitationsConfig
import com.vortexsoftware.android.sdk.models.OutgoingInvitationItem

OutgoingInvitationsConfig(
    internalInvitations = listOf(
        OutgoingInvitationItem(
            id = "internal-1",
            name = "Bob Smith",
            avatarUrl = "https://example.com/avatar.jpg",
            isVortexInvitation = false,
            metadata = mapOf("invitee_handle" to "@bob")
        )
    ),
    onCancel = { invitation ->
        if (invitation.isVortexInvitation) {
            // Vortex invitation: SDK handles the API call automatically
            println("Vortex invitation cancelled")
        } else {
            // Internal/app invitation: handle it yourself
            myAPI.cancelInvitation(invitation.id)
        }
    },
    getSubtitle = { invitation ->
        // Return a custom subtitle derived from metadata
        invitation.metadata?.get("invitee_handle") as? String
    }
)
```

**Identifying Invitation Source:**

Use the `isVortexInvitation` property to determine where an invitation came from:
- `true`: Fetched from the Vortex API — the SDK will handle cancel API calls
- `false`: Provided by your app via `internalInvitations` — your app must handle the action

**OutgoingInvitationItem Properties:**

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `id` | `String` | Yes | Unique identifier |
| `name` | `String` | Yes | Display name of the invitee |
| `subtitle` | `String?` | No | Secondary text |
| `avatarUrl` | `String?` | No | Avatar image URL |
| `isVortexInvitation` | `Boolean` | No | Source: `true` = Vortex API, `false` = your app. Default: `true` |
| `invitation` | `OutgoingInvitation?` | No | Raw API invitation data (null for internal invitations) |
| `metadata` | `Map<String, Any>?` | No | Custom metadata |

**OutgoingInvitationsConfig Properties:**

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `internalInvitations` | `List<OutgoingInvitationItem>?` | No | App-provided invitations (`isVortexInvitation = false`) |
| `onCancel` | `suspend (OutgoingInvitationItem) -> Unit` | No | Called when user cancels |
| `getSubtitle` | `(OutgoingInvitationItem) -> String?` | No | Compute subtitle from metadata |

## Authentication

The SDK requires a JWT token for authentication. You should obtain this token from your backend server:

```kotlin
// Example: Fetch JWT from your backend
suspend fun fetchVortexToken(): String {
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("https://your-api.com/vortex/token")
        .build()
    
    val response = client.newCall(request).execute()
    val json = JSONObject(response.body?.string() ?: "")
    return json.getString("jwt")
}

// Usage
lifecycleScope.launch {
    val jwt = fetchVortexToken()
    // Pass jwt to VortexInviteView
}
```

## API Reference

### VortexClient

`VortexClient` provides standalone API methods for managing invitations programmatically, outside of the `VortexInviteView` UI component.

**Initialization:**

```kotlin
import com.vortexsoftware.android.sdk.api.VortexClient

val client = VortexClient(
    baseUrl = "https://client-api.vortexsoftware.com",
    jwt = "your-jwt-token",
    clientName = "your-app",
    clientVersion = "1.0.0"
)
```

**Methods:**

```kotlin
// Get full details of an invitation
val invitation = client.getInvitation(invitationId = "invitation-id")

// Accept an incoming invitation
client.acceptInvitation(invitationId = "invitation-id")

// Revoke (deactivate) an invitation
client.revokeInvitation(invitationId = "invitation-id")

// Delete an incoming invitation
client.deleteIncomingInvitation(invitationId = "invitation-id")

// Get outgoing invitations
val outgoing = client.getOutgoingInvitations()

// Get incoming invitations
val incoming = client.getIncomingInvitations()
```

| Method | Description |
|--------|-------------|
| `getInvitation(invitationId)` | Retrieves full details of a specific invitation including targets, groups, acceptance records, and metadata |
| `acceptInvitation(invitationId)` | Accepts an incoming invitation that the user has received |
| `revokeInvitation(invitationId)` | Revokes (deactivates) an invitation where the user is either the creator or a target |
| `deleteIncomingInvitation(invitationId)` | Deletes an incoming invitation |
| `getOutgoingInvitations()` | Retrieves all outgoing invitations for the authenticated user |
| `getIncomingInvitations()` | Retrieves all incoming invitations for the authenticated user |
| `matchFingerprint(fingerprint)` | Matches a device fingerprint for deferred deep linking |

**Invitation Model:**

The `Invitation` data class returned by `getInvitation` includes:

```kotlin
data class Invitation(
    val id: String,
    val status: String?,
    val invitationType: String?,
    val deliveryTypes: List<String>?,
    val source: String?,
    val foreignCreatorId: String?,
    val creatorName: String?,
    val createdAt: String?,
    val deactivated: Boolean?,
    val target: List<InvitationTarget>?,
    val groups: List<InvitationGroup>?,
    val metadata: Map<String, JsonElement>?,
    // ... and more fields
)
```

### VortexInviteView

The main Composable for rendering invitation forms.

**Signature:**

```kotlin
@Composable
fun VortexInviteView(
    componentId: String,
    jwt: String? = null,
    apiBaseUrl: String = VortexClient.DEFAULT_BASE_URL,
    group: GroupDTO? = null,
    googleClientId: String? = null,
    enableLogging: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    onEvent: ((VortexAnalyticsEvent) -> Unit)? = null,
    locale: String? = null,
    findFriendsConfig: FindFriendsConfig? = null,
    inviteContactsConfig: InviteContactsConfig? = null,
    invitationSuggestionsConfig: InvitationSuggestionsConfig? = null,
    incomingInvitationsConfig: IncomingInvitationsConfig? = null,
    outgoingInvitationsConfig: OutgoingInvitationsConfig? = null,
    searchBoxConfig: SearchBoxConfig? = null,
    unfurlConfig: UnfurlConfig? = null,
    scope: String? = null,
    scopeType: String? = null
)
```

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `componentId` | `String` | Yes | - | Widget ID from Vortex dashboard |
| `jwt` | `String?` | No | `null` | JWT token for authenticated requests |
| `apiBaseUrl` | `String` | No | Production URL | Base URL of the Vortex API |
| `group` | `GroupDTO?` | No | `null` | Group context for invitations |
| `googleClientId` | `String?` | No | `null` | Google OAuth client ID for Google Contacts |
| `enableLogging` | `Boolean` | No | `false` | Enable debug logging |
| `onDismiss` | `(() -> Unit)?` | No | `null` | Callback when widget is dismissed |
| `onEvent` | `((VortexAnalyticsEvent) -> Unit)?` | No | `null` | Callback for analytics events |
| `locale` | `String?` | No | `null` | BCP 47 language code for internationalization |
| `findFriendsConfig` | `FindFriendsConfig?` | No | `null` | Configuration for [Find Friends](#find-friends) |
| `inviteContactsConfig` | `InviteContactsConfig?` | No | `null` | Configuration for [Invite Contacts](#invite-contacts) |
| `invitationSuggestionsConfig` | `InvitationSuggestionsConfig?` | No | `null` | Configuration for [Invitation Suggestions](#invitation-suggestions) |
| `incomingInvitationsConfig` | `IncomingInvitationsConfig?` | No | `null` | Configuration for [Incoming Invitations](#incoming-invitations) |
| `outgoingInvitationsConfig` | `OutgoingInvitationsConfig?` | No | `null` | Configuration for [Outgoing Invitations](#outgoing-invitations) |
| `searchBoxConfig` | `SearchBoxConfig?` | No | `null` | Configuration for [Search Box](#search-box) |
| `unfurlConfig` | `UnfurlConfig?` | No | `null` | Configuration for [Unfurl Configuration](#unfurl-configuration) |
| `scope` | `String?` | No | `null` | Scope identifier (e.g., team ID). Convenience alternative to `group` |
| `scopeType` | `String?` | No | `null` | Scope type (e.g., "team", "project"). Used with `scope` |

## Deferred Deep Linking

Deferred deep linking allows your app to retrieve invitation context even when a user installs the app after clicking an invitation link. When a user clicks an invitation link but doesn't have the app installed, they're redirected to the Play Store. After installation, the SDK can match the device fingerprint to retrieve the original invitation context.

### Basic Usage

Call `VortexDeferredLinks.retrieveDeferredDeepLink` when the user signs in or when the app session is restored:

```kotlin
import com.vortexsoftware.android.sdk.VortexDeferredLinks

// Call when user signs in or session is restored
lifecycleScope.launch {
    val result = VortexDeferredLinks.retrieveDeferredDeepLink(context, jwt)
    result.onSuccess { response ->
        if (response.matched && response.context != null) {
            println("Found pending invitation!")
            println("Invitation ID: ${response.context.invitationId}")
            println("Inviter ID: ${response.context.inviterId ?: "N/A"}")
            println("Scope: ${response.context.scope ?: "N/A"}")
            // Handle the invitation (e.g., show UI, auto-join group, etc.)
        }
    }.onFailure { error ->
        println("Deferred link check failed: $error")
    }
}
```

### Response Types

**MatchFingerprintResponse:**

```kotlin
data class MatchFingerprintResponse(
    val matched: Boolean,        // Whether a matching invitation was found
    val confidence: Double?,     // Match confidence score (0.0 - 1.0)
    val context: DeferredLinkContext?,  // Invitation context if matched
    val error: String?           // Error message if any
)
```

**DeferredLinkContext:**

```kotlin
data class DeferredLinkContext(
    val invitationId: String,    // The original invitation ID
    val inviterId: String?,      // ID of the user who sent the invitation
    val groupId: String?,        // Group ID from the invitation
    val groupType: String?,      // Type of the group
    val metadata: Map<String, JsonElement>?  // Additional metadata
) {
    val scope: String?           // Alias for groupId (e.g., team ID, project ID)
    val scopeType: String?       // Alias for groupType (e.g., "team", "project")
}
```

### Best Practices

1. **Call on authentication**: Check for deferred deep links immediately after user sign-in or session restore
2. **Use Vortex JWT**: The endpoint requires a Vortex JWT token (not your app's auth token)
3. **Handle once**: Clear or mark the invitation as handled after processing to avoid showing it repeatedly
4. **Graceful degradation**: The check may fail (network issues, no match found) — handle errors gracefully

### Example Integration

```kotlin
import com.vortexsoftware.android.sdk.VortexDeferredLinks

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val _pendingInvitation = MutableStateFlow<DeferredLinkContext?>(null)
    val pendingInvitation: StateFlow<DeferredLinkContext?> = _pendingInvitation

    fun checkForPendingInvitations(vortexJwt: String) {
        viewModelScope.launch {
            val result = VortexDeferredLinks.retrieveDeferredDeepLink(
                getApplication(), vortexJwt
            )
            result.onSuccess { response ->
                if (response.matched) {
                    _pendingInvitation.value = response.context
                }
            }
            // Log error but don't block the user
            result.onFailure { error ->
                println("Deferred deep link check failed: $error")
            }
        }
    }

    fun dismissPendingInvitation() {
        _pendingInvitation.value = null
    }
}
```

## Unfurl Configuration

When invitation links are shared on social platforms (WhatsApp, Facebook, Twitter, etc.), the `UnfurlConfig` allows you to customize the Open Graph metadata that appears in the link preview card.

### Usage

```kotlin
VortexInviteView(
    componentId = "your-component-id",
    jwt = jwt,
    onDismiss = { /* ... */ },
    unfurlConfig = UnfurlConfig(
        title = "Join our team!",
        description = "You've been invited to collaborate",
        image = "https://example.com/preview.png",
        siteName = "My App",
        type = "website"
    )
)
```

### UnfurlConfig Properties

| Property | Type | Description |
|----------|------|-------------|
| `title` | `String?` | The title shown in the link preview (`og:title`) |
| `description` | `String?` | The description shown in the link preview (`og:description`) |
| `image` | `String?` | URL to the image shown in the link preview (`og:image`). Must be a valid URL. |
| `siteName` | `String?` | The site name shown in the link preview (`og:site_name`) |
| `type` | `String?` | The Open Graph type (`og:type`). Defaults to "website" if not provided. |

### Priority Chain

The backend uses this priority for unfurl values:
1. **Invitation metadata** (values from `UnfurlConfig`) — highest priority
2. **Domain profile unfurlData** — fallback from account settings in Vortex dashboard
3. **Hardcoded defaults** — last resort

## Internationalization

Pass a locale to fetch localized widget content:

```kotlin
VortexInviteView(
    componentId = "your-widget-id",
    jwt = jwt,
    locale = "pt-BR", // BCP 47 language code
    onDismiss = { /* handle dismiss */ }
)
```

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

## Error Handling

The SDK provides structured error types via the `VortexError` sealed class:

```kotlin
import com.vortexsoftware.android.sdk.api.VortexError

sealed class VortexError : Exception() {
    data class NetworkError(message: String, cause: Throwable?)    // No connection, timeout
    data class ServerError(statusCode: Int, message: String)       // Server returned error
    data class DecodingError(message: String, cause: Throwable?)   // Failed to parse response
    data class InvalidRequest(message: String)                     // Invalid request parameters
    data class Unauthorized(message: String)                       // Auth error or expired token
    data class NotFound(message: String)                           // Resource not found
    data class Conflict(message: String)                           // Resource conflict
    data class Unknown(message: String, cause: Throwable?)         // Unexpected error
}
```

Handle errors when using `VortexClient` directly:

```kotlin
lifecycleScope.launch {
    val result = client.getInvitation(invitationId = "invitation-id")
    result.onSuccess { invitation ->
        // Use invitation...
    }.onFailure { error ->
        when (error) {
            is VortexError.Unauthorized -> {
                // Token expired, refresh and retry
            }
            is VortexError.NotFound -> {
                // Invitation doesn't exist
            }
            is VortexError.NetworkError -> {
                // No internet connection
            }
            else -> {
                println("Error: ${error.message}")
            }
        }
    }
}
```

## Features

**Invitation Methods:**
- Email invitations with validation
- Copy shareable link to clipboard
- Android native share sheet integration
- SMS sharing
- QR code generation
- LINE messaging integration

**Contact Import:**
- Android device contacts integration
- Google Contacts integration (requires Google Sign-In)

**Invite Contacts:**
- Display a list of contacts for SMS invitation
- In-app SMS composer on supported devices
- Callback when SMS invitation is created

**Core Capabilities:**
- Dynamic form rendering from server configuration
- JWT authentication
- Group/team context support
- Real-time loading states and error handling
- Customizable UI based on widget configuration
- Deferred deep linking via fingerprint matching
- Invitation metadata support (custom data attached to invitations)
- Deduplication of internal and API invitations by `userId`
- Custom subtitle rendering via `getSubtitle` callback

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

## Configuration

The SDK automatically fetches and caches widget configuration from the Vortex platform. The configuration determines:
- Available invitation methods
- Form fields and validation
- UI theme and styling
- Enabled features

## API Endpoints

The SDK communicates with the following Vortex API endpoints:

- `GET /api/v1/widgets/{componentId}` - Fetch widget configuration
- `POST /api/v1/invitations` - Create invitation (email, SMS, internal ID)
- `POST /api/v1/invitations/generate-shareable-link-invite` - Generate shareable link
- `GET /api/v1/invitations/sent` - Get outgoing invitations
- `GET /api/v1/invitations` - Get incoming invitations
- `POST /api/v1/invitations/accept` - Accept an invitation
- `DELETE /api/v1/invitations/{id}` - Delete/revoke an invitation
- `POST /api/v1/deferred-links/match` - Match device fingerprint for deferred deep links

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
