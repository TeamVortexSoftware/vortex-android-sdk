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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import com.vortexsoftware.android.sdk.api.VortexClient
import com.vortexsoftware.android.sdk.api.dto.GroupDTO
import com.vortexsoftware.android.sdk.models.*
import kotlinx.coroutines.launch

// ============================================================================
// Find Friends View
// ============================================================================

/**
 * Displays a list of contacts with Connect buttons.
 * When user taps Connect, an invitation is created via the Vortex backend.
 * 
 * @param config Configuration with contacts and callbacks
 * @param client VortexClient for API calls
 * @param widgetId Widget configuration ID
 * @param groups Optional groups for scoping invitations
 * @param unfurlConfig Optional unfurl configuration for link metadata
 * @param onInvitationSent Callback when an invitation is successfully sent
 * @param block Optional ElementNode containing theme options and settings from widget config
 * @param modifier Modifier for the component
 */
@Composable
fun FindFriendsView(
    config: FindFriendsConfig,
    client: VortexClient,
    widgetId: String,
    groups: List<GroupDTO>?,
    unfurlConfig: UnfurlConfig? = null,
    onInvitationSent: (() -> Unit)? = null,
    outgoingInvitationUserIds: kotlinx.coroutines.flow.StateFlow<Set<String>>? = null,
    isOutgoingInvitationsLoaded: kotlinx.coroutines.flow.StateFlow<Boolean>? = null,
    block: ElementNode? = null,
    modifier: Modifier = Modifier
) {
    // Extract theme values from block or use defaults
    val titleColor = block?.getThemeOption("--vrtx-find-friends-title-color")?.let { parseColor(it) } ?: DefaultForeground
    val titleFontSize = block?.getThemeOption("--vrtx-find-friends-title-font-size")?.let { parseFontSize(it) } ?: 18f
    val titleFontWeight = block?.getThemeOption("--vrtx-find-friends-title-font-weight")?.let { parseFontWeight(it) } ?: FontWeight.SemiBold
    
    val nameColor = block?.getThemeOption("--vrtx-find-friends-contact-name-color")?.let { parseColor(it) } ?: DefaultForeground
    val subtitleColor = block?.getThemeOption("--vrtx-find-friends-contact-subtitle-color")?.let { parseColor(it) } ?: DefaultSecondaryForeground
    
    val avatarBackground = block?.getThemeOption("--vrtx-find-friends-avatar-background")?.let { parseColor(it) } ?: DefaultPrimaryBackground
    val avatarTextColor = block?.getThemeOption("--vrtx-find-friends-avatar-color")?.let { parseColor(it) } ?: DefaultPrimaryForeground
    
    val connectButtonBackgroundStyle = block?.getThemeOption("--vrtx-find-friends-connect-button-background")?.let { parseBackgroundStyle(it) }
    val connectButtonTextColor = block?.getThemeOption("--vrtx-find-friends-connect-button-color")?.let { parseColor(it) } ?: DefaultPrimaryForeground
    val connectButtonBorderRadius = block?.getThemeOption("--vrtx-find-friends-connect-button-borderRadius")?.let { parseBorderRadius(it) } ?: 8f
    
    // Extract customization text from block settings
    val title = block?.getTitle()
    val connectButtonText = block?.getCustomButtonLabel("connectButton") ?: "Connect"
    
    var contacts by remember { mutableStateOf(config.contacts) }
    var actionInProgress by remember { mutableStateOf<String?>(null) }
    var connectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    val scope = rememberCoroutineScope()
    
    // Observe outgoing invitation user IDs for filtering
    val outgoingIds = outgoingInvitationUserIds?.collectAsState()?.value ?: emptySet()
    val outgoingLoaded = isOutgoingInvitationsLoaded?.collectAsState()?.value ?: true
    
    // When outgoing invitations are refreshed (e.g. after revoke), remove contacts
    // from connectedIds if they are no longer in the outgoing set
    LaunchedEffect(outgoingIds) {
        if (connectedIds.isNotEmpty()) {
            connectedIds = connectedIds.filter { id -> outgoingIds.contains(id) }.toSet()
        }
    }
    
    // Filter out already connected contacts and contacts with outstanding outgoing invitations
    val visibleContacts = contacts.filter { 
        !connectedIds.contains(it.id) && !outgoingIds.contains(it.id)
    }
    
    // Don't render if no contacts (and outgoing invitations are loaded)
    if (visibleContacts.isEmpty() && outgoingLoaded) return
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        // Title
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                fontSize = titleFontSize.sp,
                fontWeight = titleFontWeight,
                color = titleColor,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        // Show shimmer while outgoing invitations are loading
        if (!outgoingLoaded) {
            ShimmerPlaceholderList(count = 3)
            return@Column
        }
        
        visibleContacts.forEach { contact ->
            FindFriendsRow(
                contact = contact,
                isLoading = actionInProgress == contact.id,
                avatarBackgroundColor = avatarBackground,
                avatarTextColor = avatarTextColor,
                nameColor = nameColor,
                subtitleColor = subtitleColor,
                connectButtonBackgroundStyle = connectButtonBackgroundStyle,
                connectButtonTextColor = connectButtonTextColor,
                connectButtonBorderRadius = connectButtonBorderRadius,
                connectButtonText = connectButtonText,
                onConnect = {
                    scope.launch {
                        actionInProgress = contact.id
                        // Merge contact metadata with unfurl metadata (contact metadata takes precedence)
                        val mergedMetadata = buildMap<String, Any> {
                            unfurlConfig?.toMetadata()?.let { putAll(it) }
                            contact.metadata?.let { putAll(it) }
                        }.takeIf { it.isNotEmpty() }
                        client.createInternalIdInvitation(
                            widgetId = widgetId,
                            internalId = contact.id,
                            contactName = contact.name,
                            contactAvatarUrl = contact.avatarUrl,
                            groups = groups,
                            metadata = mergedMetadata,
                            subtype = "find-friends"
                        ).onSuccess {
                            connectedIds = connectedIds + contact.id
                            config.onInvitationCreated?.invoke(contact)
                            onInvitationSent?.invoke()
                        }
                        actionInProgress = null
                    }
                }
            )
        }
    }
}

@Composable
private fun FindFriendsRow(
    contact: FindFriendsContact,
    isLoading: Boolean,
    avatarBackgroundColor: Color,
    avatarTextColor: Color,
    nameColor: Color,
    subtitleColor: Color,
    connectButtonBackgroundStyle: BackgroundStyle?,
    connectButtonTextColor: Color,
    connectButtonBorderRadius: Float,
    connectButtonText: String,
    onConnect: () -> Unit
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
        
        // Name and subtitle
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
            contact.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Connect button with gradient support
        val connectStyle = connectButtonBackgroundStyle ?: BackgroundStyle.Solid(DefaultPrimaryBackground.value.toLong())
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(connectButtonBorderRadius.dp))
                .styledBackground(connectStyle)
                .clickable(enabled = !isLoading, onClick = onConnect)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = connectButtonTextColor
                )
            } else {
                Text(
                    text = connectButtonText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = connectButtonTextColor
                )
            }
        }
    }
}
