package com.vortexsoftware.android.sdk.models

/**
 * Represents the current view state of the invite widget
 */
enum class InviteViewState {
    /**
     * Main view showing the form and share options
     */
    MAIN,
    
    /**
     * Email entry view for manually adding email addresses
     */
    EMAIL_ENTRY,
    
    /**
     * Native device contacts picker
     */
    CONTACTS_PICKER,
    
    /**
     * Google contacts picker
     */
    GOOGLE_CONTACTS_PICKER,
    
    /**
     * QR code display view
     */
    QR_CODE,
    
    /**
     * Invite contacts (SMS) list view
     */
    INVITE_CONTACTS
}
