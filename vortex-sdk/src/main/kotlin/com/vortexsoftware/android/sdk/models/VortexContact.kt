package com.vortexsoftware.android.sdk.models

/**
 * Represents a contact that can be invited
 * Used for both device contacts and Google contacts
 */
data class VortexContact(
    val id: String,
    val name: String,
    val email: String
) {
    companion object {
        /**
         * Create a VortexContact from name and email
         * Generates a unique ID based on the email
         */
        fun create(name: String, email: String): VortexContact {
            return VortexContact(
                id = email.hashCode().toString(),
                name = name.ifBlank { email },
                email = email
            )
        }
    }
    
    /**
     * Display name - returns name if available, otherwise email
     */
    val displayName: String
        get() = name.ifBlank { email }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VortexContact) return false
        return email.equals(other.email, ignoreCase = true)
    }
    
    override fun hashCode(): Int {
        return email.lowercase().hashCode()
    }
}

/**
 * State for tracking invitation status of a contact
 */
data class ContactInviteState(
    val contact: VortexContact,
    val isInvited: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
