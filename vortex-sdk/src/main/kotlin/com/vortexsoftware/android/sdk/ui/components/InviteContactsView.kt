package com.vortexsoftware.android.sdk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vortexsoftware.android.sdk.api.VortexClient
import com.vortexsoftware.android.sdk.api.dto.GroupDTO
import com.vortexsoftware.android.sdk.models.*

// ============================================================================
// Invite Contacts (SMS) View
// ============================================================================

/**
 * Displays contacts for SMS invitations.
 * 
 * Shows a two-screen pattern matching the iOS SDK:
 * 1. Entry view: Title with a right chevron (›) - tapping navigates to contacts list
 * 2. Contacts list: Back button, search box, and contact rows with Invite buttons
 * 
 * @param config Configuration with contacts
 * @param client VortexClient for API calls
 * @param widgetId Widget configuration ID
 * @param groups Optional groups for scoping invitations
 * @param shareMessage Message template for SMS
 * @param block Optional ElementNode containing theme options and settings from widget config
 * @param onOpenSms Callback to open SMS app with phone number and message
 * @param modifier Modifier for the component
 */
@Composable
fun InviteContactsView(
    config: InviteContactsConfig,
    client: VortexClient,
    widgetId: String,
    groups: List<GroupDTO>?,
    shareMessage: String,
    block: ElementNode? = null,
    onOpenSms: (phoneNumber: String, message: String) -> Unit,
    onInvitationSent: (() -> Unit)? = null,
    onNavigateToContacts: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Extract theme values from block or use defaults
    val titleColor = block?.getThemeOption("--vrtx-invite-contacts-title-color")?.let { parseColor(it) } ?: DefaultForeground
    val titleFontSize = block?.getThemeOption("--vrtx-invite-contacts-title-font-size")?.let { parseFontSize(it) } ?: 16f
    val titleFontWeight = block?.getThemeOption("--vrtx-invite-contacts-title-font-weight")?.let { parseFontWeight(it) } ?: FontWeight.Medium
    
    // Extract customization text from block settings
    val inviteYourContactsText = block?.getCustomButtonLabel("inviteYourContactsText") ?: block?.getTitle() ?: "Invite your contacts"
    
    val contacts = remember { config.contacts.sortedBy { it.name.lowercase() } }
    
    // Don't render if no contacts
    if (contacts.isEmpty()) return
    
    // Primary screen: title with right chevron (navigates to secondary page)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onNavigateToContacts() }
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = inviteYourContactsText,
            fontSize = titleFontSize.sp,
            fontWeight = titleFontWeight,
            color = titleColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "›",
            fontSize = titleFontSize.sp,
            fontWeight = titleFontWeight,
            color = titleColor.copy(alpha = 0.75f)
        )
    }
}

@Composable
private fun InviteContactsRow(
    contact: InviteContactsContact,
    isLoading: Boolean,
    isInvited: Boolean,
    avatarBackgroundColor: Color,
    avatarTextColor: Color,
    nameColor: Color,
    subtitleColor: Color,
    inviteButtonBackgroundStyle: BackgroundStyle?,
    inviteButtonTextColor: Color,
    inviteButtonBorderRadius: Float,
    inviteButtonText: String,
    onInvite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        AvatarView(
            name = contact.name,
            avatarUrl = contact.avatarUrl,
            backgroundColor = avatarBackgroundColor,
            textColor = avatarTextColor
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Name and phone
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = contact.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
            Text(
                text = contact.phoneNumber,
                fontSize = 13.sp,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Invite button with gradient support
        val inviteStyle = if (isInvited) {
            BackgroundStyle.Solid(Color.Gray.value.toLong())
        } else {
            inviteButtonBackgroundStyle ?: BackgroundStyle.Solid(DefaultPrimaryBackground.value.toLong())
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(inviteButtonBorderRadius.dp))
                .styledBackground(inviteStyle)
                .clickable(enabled = !isLoading && !isInvited, onClick = onInvite)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = inviteButtonTextColor
                )
            } else {
                Text(
                    text = if (isInvited) "✓" else inviteButtonText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = inviteButtonTextColor
                )
            }
        }
    }
}
