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
import kotlinx.coroutines.launch

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
    modifier: Modifier = Modifier
) {
    // Extract theme values from block or use defaults
    val titleColor = block?.getThemeOption("--vrtx-invite-contacts-title-color")?.let { parseColor(it) } ?: DefaultForeground
    val titleFontSize = block?.getThemeOption("--vrtx-invite-contacts-title-font-size")?.let { parseFontSize(it) } ?: 16f
    val titleFontWeight = block?.getThemeOption("--vrtx-invite-contacts-title-font-weight")?.let { parseFontWeight(it) } ?: FontWeight.Medium
    
    val nameColor = block?.getThemeOption("--vrtx-invite-contacts-name-color")?.let { parseColor(it) } ?: DefaultForeground
    val subtitleColor = block?.getThemeOption("--vrtx-invite-contacts-subtitle-color")?.let { parseColor(it) } ?: DefaultSecondaryForeground
    
    val avatarBackground = block?.getThemeOption("--vrtx-invite-contacts-avatar-background")?.let { parseColor(it) } ?: DefaultPrimaryBackground
    val avatarTextColor = block?.getThemeOption("--vrtx-invite-contacts-avatar-color")?.let { parseColor(it) } ?: DefaultPrimaryForeground
    
    val inviteButtonBackgroundStyle = block?.getThemeOption("--vrtx-invite-contacts-invite-button-background")?.let { parseBackgroundStyle(it) }
    val inviteButtonTextColor = block?.getThemeOption("--vrtx-invite-contacts-invite-button-color")?.let { parseColor(it) } ?: DefaultPrimaryForeground
    val inviteButtonBorderRadius = block?.getThemeOption("--vrtx-invite-contacts-invite-button-border-radius")?.let { parseBorderRadius(it) } ?: 8f
    
    // Extract customization text from block settings
    val inviteYourContactsText = block?.getCustomButtonLabel("inviteYourContactsText") ?: block?.getTitle() ?: "Invite your contacts"
    val inviteButtonText = block?.getCustomButtonLabel("inviteButton") ?: "Invite"
    val backButtonText = block?.getCustomButtonLabel("backButton") ?: "Back"
    val emptyStateMessage = block?.getCustomButtonLabel("emptyStateMessage") ?: "No contacts to invite"
    val emptySearchState = block?.getCustomButtonLabel("emptySearchState") ?: "No contacts match your search"
    val searchPlaceholderText = block?.getCustomButtonLabel("searchPlaceholder") ?: "Search contacts..."
    
    var contacts by remember { mutableStateOf(config.contacts.sortedBy { it.name.lowercase() }) }
    var actionInProgress by remember { mutableStateOf<String?>(null) }
    var invitedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showContactsList by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    
    // Don't render if no contacts
    if (contacts.isEmpty()) return
    
    // Filter contacts based on search query
    val filteredContacts = if (searchQuery.isBlank()) {
        contacts
    } else {
        val query = searchQuery.lowercase().trim()
        contacts.filter { contact ->
            contact.name.lowercase().contains(query) ||
            contact.phoneNumber.contains(query)
        }
    }
    
    if (showContactsList) {
        // Secondary screen: contacts list with back button and search
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            // Back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showContactsList = false
                        searchQuery = ""
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "‹",
                    fontSize = 20.sp,
                    color = DefaultSecondaryForeground
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = backButtonText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = DefaultForeground
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Search box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(searchPlaceholderText, fontSize = 16.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            
            // Contact rows or empty state
            if (filteredContacts.isEmpty()) {
                Text(
                    text = if (searchQuery.isBlank()) emptyStateMessage else emptySearchState,
                    fontSize = 14.sp,
                    color = DefaultSecondaryForeground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            } else {
                filteredContacts.forEach { contact ->
                    InviteContactsRow(
                        contact = contact,
                        isLoading = actionInProgress == contact.id,
                        isInvited = invitedIds.contains(contact.id),
                        avatarBackgroundColor = avatarBackground,
                        avatarTextColor = avatarTextColor,
                        nameColor = nameColor,
                        subtitleColor = subtitleColor,
                        inviteButtonBackgroundStyle = inviteButtonBackgroundStyle,
                        inviteButtonTextColor = inviteButtonTextColor,
                        inviteButtonBorderRadius = inviteButtonBorderRadius,
                        inviteButtonText = inviteButtonText,
                        onInvite = {
                            scope.launch {
                                actionInProgress = contact.id
                                client.createSmsInvitation(
                                    widgetId = widgetId,
                                    phoneNumber = contact.phoneNumber,
                                    contactName = contact.name,
                                    groups = groups
                                ).onSuccess { shortLink ->
                                    invitedIds = invitedIds + contact.id
                                    config.onInvite?.invoke(contact, shortLink)
                                    onInvitationSent?.invoke()
                                    // Open SMS app with the invitation link
                                    shortLink?.let { link ->
                                        val fullMessage = shareMessage.replace("{{link}}", link)
                                            .replace("{{vortex_share_link}}", link)
                                        onOpenSms(contact.phoneNumber, fullMessage)
                                    }
                                }
                                actionInProgress = null
                            }
                        }
                    )
                }
            }
        }
    } else {
        // Primary screen: title with right chevron
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable { showContactsList = true }
                .padding(horizontal = 16.dp, vertical = 16.dp),
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
        val backgroundBrush = if (isInvited) {
            Brush.linearGradient(listOf(Color.Gray, Color.Gray))
        } else {
            (inviteButtonBackgroundStyle ?: BackgroundStyle.Solid(DefaultPrimaryBackground.value.toLong())).toBrush()
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(inviteButtonBorderRadius.dp))
                .background(backgroundBrush)
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
