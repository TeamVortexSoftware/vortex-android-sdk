package com.vortexsoftware.android.sdk.models

import com.vortexsoftware.android.sdk.api.dto.IncomingInvitation
import com.vortexsoftware.android.sdk.api.dto.OutgoingInvitation

// ============================================================================
// Find Friends Configuration
// ============================================================================

/**
 * Contact data for the Find Friends feature.
 * Represents a user who can be invited via internal ID.
 *
 * @property internalId Internal ID of the contact in the customer's platform
 * @property name Display name of the contact
 * @property subtitle Optional subtitle (e.g., username, email, or app-specific info)
 * @property avatarUrl Optional URL for the contact's avatar image
 * @property metadata Optional metadata for app-specific data
 */
data class FindFriendsContact(
    val internalId: String,
    val name: String,
    val subtitle: String? = null,
    val avatarUrl: String? = null,
    val metadata: Map<String, Any>? = null
) {
    // Convenience property for ID access
    val id: String get() = internalId
}

/**
 * Configuration for the Find Friends component.
 *
 * The Find Friends feature displays a list of contacts that can be invited
 * using their internal user ID. When the user taps "Connect", an invitation
 * with targetType = internalId is created via the Vortex API.
 *
 * @property contacts List of contacts to display
 * @property onInvitationCreated Optional callback called after an invitation is successfully created.
 *                               Use this to trigger in-app notifications or update your UI.
 */
data class FindFriendsConfig(
    val contacts: List<FindFriendsContact>,
    val onInvitationCreated: (suspend (FindFriendsContact) -> Unit)? = null
)

// ============================================================================
// Invite Contacts (SMS) Configuration
// ============================================================================

/**
 * Contact data for the Invite Contacts (SMS) feature.
 * Represents a contact that can be invited via SMS.
 *
 * @property id Unique identifier for the contact
 * @property name Display name of the contact
 * @property phoneNumber Phone number for SMS invitation
 * @property avatarUrl Optional URL for the contact's avatar image
 */
data class InviteContactsContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val avatarUrl: String? = null
)

/**
 * Configuration for the Invite Contacts (SMS) component.
 *
 * The Invite Contacts feature displays a list of contacts that can be
 * invited via SMS. When the user taps "Invite", an SMS invitation is
 * created and the device's SMS app is opened with a pre-filled message.
 *
 * @property contacts List of contacts to display
 * @property onInvite Callback when user taps "Invite" on a contact.
 *                    Called after the SMS invitation is created.
 *                    The callback receives the contact and the invitation short link.
 */
data class InviteContactsConfig(
    val contacts: List<InviteContactsContact>,
    val onInvite: (suspend (InviteContactsContact, String?) -> Unit)? = null
)

// ============================================================================
// Invitation Suggestions Configuration
// ============================================================================

/**
 * Contact data for the Invitation Suggestions feature.
 * Represents a suggested contact that can be invited or dismissed.
 *
 * @property id Unique identifier for the suggestion
 * @property name Display name of the contact
 * @property email Email address for the invitation
 * @property avatarUrl Optional URL for the contact's avatar image
 * @property reason Optional reason why this contact is suggested
 * @property metadata Optional metadata for app-specific data
 */
data class InvitationSuggestionContact(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String? = null,
    val reason: String? = null,
    val metadata: Map<String, Any>? = null
)

/**
 * Configuration for the Invitation Suggestions component.
 *
 * The Invitation Suggestions feature displays a list of suggested contacts
 * that the user might want to invite. Each suggestion has an "Invite" button
 * and a dismiss (X) button.
 *
 * @property suggestions List of suggested contacts to display
 * @property onInvite Callback when user taps "Invite" on a suggestion.
 *                    Called after the API invitation succeeds.
 * @property onDismiss Callback when user taps the dismiss (X) button.
 *                     The host app should handle removing the suggestion from their backend.
 */
data class InvitationSuggestionsConfig(
    val suggestions: List<InvitationSuggestionContact>,
    val onInvite: (suspend (InvitationSuggestionContact) -> Unit)? = null,
    val onDismiss: (suspend (InvitationSuggestionContact) -> Unit)? = null
)

// ============================================================================
// Incoming Invitations Configuration
// ============================================================================

/**
 * Display model for an incoming invitation.
 *
 * Use the `isVortexInvitation` property to determine the source of the invitation:
 * - `true`: The invitation was fetched from the Vortex API
 * - `false`: The invitation was provided by your app via `internalInvitations`
 *
 * @property id Invitation ID
 * @property name Display name (sender name or identifier)
 * @property subtitle Secondary text (e.g., email or phone number)
 * @property avatarUrl Optional URL for the sender's avatar
 * @property isVortexInvitation Whether this invitation came from the Vortex API
 * @property invitation The raw invitation data from the API (null for internal invitations)
 * @property metadata Optional metadata for app-specific data
 */
data class IncomingInvitationItem(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val avatarUrl: String? = null,
    val isVortexInvitation: Boolean = false,
    val invitation: IncomingInvitation? = null,
    val metadata: Map<String, Any>? = null
)

/**
 * Configuration for the Incoming Invitations component.
 *
 * The Incoming Invitations feature displays invitations the user has received
 * with "Accept" and "Delete" actions.
 *
 * @property internalInvitations App-provided invitations to merge with API-fetched ones.
 *                               These will have `isVortexInvitation = false`.
 * @property onAccept Callback when user accepts an invitation.
 *                    For Vortex invitations: return true to let SDK call API, false to cancel.
 *                    For internal invitations: handle accept logic, return true to remove from list.
 * @property onDelete Callback when user deletes/rejects an invitation.
 *                    For Vortex invitations: return true to let SDK call API, false to cancel.
 *                    For internal invitations: handle delete logic, return true to remove from list.
 * @property getSubtitle Optional callback to compute a custom subtitle for each invitation.
 *                       When provided, the SDK calls this function for each invitation to determine
 *                       the displayed subtitle. If not provided, no subtitle is rendered.
 */
data class IncomingInvitationsConfig(
    val internalInvitations: List<IncomingInvitationItem>? = null,
    val onAccept: (suspend (IncomingInvitationItem) -> Boolean)? = null,
    val onDelete: (suspend (IncomingInvitationItem) -> Boolean)? = null,
    val getSubtitle: ((IncomingInvitationItem) -> String?)? = null
)

// ============================================================================
// Outgoing Invitations Configuration
// ============================================================================

/**
 * Display model for an outgoing invitation.
 *
 * @property id Invitation ID
 * @property name Display name (recipient name or identifier)
 * @property subtitle Secondary text (e.g., email or phone number)
 * @property avatarUrl Optional URL for the recipient's avatar
 * @property isVortexInvitation Whether this invitation came from the Vortex API
 * @property invitation The raw invitation data from the API (null for internal invitations)
 * @property metadata Optional metadata for app-specific data
 */
data class OutgoingInvitationItem(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val avatarUrl: String? = null,
    val isVortexInvitation: Boolean = true,
    val invitation: OutgoingInvitation? = null,
    val metadata: Map<String, Any>? = null
)

/**
 * Configuration for the Outgoing Invitations component.
 *
 * The Outgoing Invitations feature displays invitations the user has sent
 * with a "Cancel" action to revoke pending invitations.
 *
 * @property internalInvitations App-provided invitations to merge with API-fetched ones.
 *                               These will have `isVortexInvitation = false`.
 * @property onCancel Callback when user cancels/revokes an invitation.
 *                    Called after the API revocation succeeds.
 * @property getSubtitle Optional callback to compute a custom subtitle for each invitation.
 *                       When provided, the SDK calls this function for each invitation to determine
 *                       the displayed subtitle. If not provided, no subtitle is rendered.
 */
data class OutgoingInvitationsConfig(
    val internalInvitations: List<OutgoingInvitationItem>? = null,
    val onCancel: (suspend (OutgoingInvitationItem) -> Unit)? = null,
    val getSubtitle: ((OutgoingInvitationItem) -> String?)? = null
)

// ============================================================================
// Search Box Configuration
// ============================================================================

/**
 * Contact data for the Search Box feature.
 * Represents a contact returned from the search callback.
 *
 * @property userId The user ID that identifies this contact in the customer's platform
 * @property name Display name of the contact
 * @property subtitle Optional subtitle (e.g., username, email, or app-specific info)
 * @property avatarUrl Optional URL for the contact's avatar image
 * @property metadata Optional metadata for app-specific data
 */
data class SearchBoxContact(
    val userId: String,
    val name: String,
    val subtitle: String? = null,
    val avatarUrl: String? = null,
    val metadata: Map<String, Any>? = null
) {
    val id: String get() = userId
}

/**
 * Configuration for the Search Box feature.
 *
 * The customer provides an `onSearch` callback that returns matching contacts.
 * When the user taps "Connect", the SDK creates an invitation via the Vortex backend,
 * identical to the Find Friends component behavior.
 *
 * @property onSearch Called when the user taps the search button. Returns matching contacts.
 * @property onInvitationCreated Optional callback called after an invitation is successfully created.
 */
data class SearchBoxConfig(
    val onSearch: suspend (String) -> List<SearchBoxContact>,
    val onInvitationCreated: ((SearchBoxContact) -> Unit)? = null
)

// ============================================================================
// Unfurl Configuration
// ============================================================================

/**
 * Configuration for Open Graph unfurl metadata when sharing invitation links.
 *
 * When invitation links are shared on social platforms, these values customize
 * the preview card that appears. The backend extracts these from the invitation's
 * metadata and uses them to generate Open Graph meta tags.
 *
 * @property title The title shown in the link preview (og:title)
 * @property description The description shown in the link preview (og:description)
 * @property image URL to the image shown in the link preview (og:image)
 * @property siteName The site name shown in the link preview (og:site_name)
 * @property type The Open Graph type (og:type), defaults to "website"
 */
data class UnfurlConfig(
    val title: String? = null,
    val description: String? = null,
    val image: String? = null,
    val siteName: String? = null,
    val type: String? = null
) {
    /**
     * Convert the config to metadata dictionary with nested unfurlConfig object.
     * Used internally when creating invitations.
     */
    fun toMetadata(): Map<String, Any> {
        val unfurlConfig = mutableMapOf<String, String>()
        title?.let { unfurlConfig["title"] = it }
        description?.let { unfurlConfig["description"] = it }
        image?.let { unfurlConfig["image"] = it }
        siteName?.let { unfurlConfig["siteName"] = it }
        type?.let { unfurlConfig["type"] = it }

        if (unfurlConfig.isEmpty()) return emptyMap()
        return mapOf("unfurlConfig" to unfurlConfig)
    }
}
