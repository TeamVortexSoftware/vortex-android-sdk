package com.vortexsoftware.android.sdk.models

/**
 * Event fired when an invitation is sent from any subcomponent.
 * Other subcomponents can observe this via the ViewModel's `invitationSentEvent` property.
 */
data class InvitationSentEvent(
    /** The source component that sent the invitation */
    val source: InvitationSource,
    /** The short link that was created for the invitation */
    val shortLink: String,
    /** Timestamp when the event was fired */
    val timestamp: Long = System.currentTimeMillis()
) {
    /** Source component types that can fire invitation events */
    enum class InvitationSource(val value: String) {
        INVITE_CONTACTS("invite_contacts"),
        FIND_FRIENDS("find_friends"),
        INVITATION_SUGGESTIONS("invitation_suggestions"),
        EMAIL_INVITATIONS("email_invitations"),
        SHARE_LINK("share_link"),
        SEARCH_BOX("search_box")
    }
}
