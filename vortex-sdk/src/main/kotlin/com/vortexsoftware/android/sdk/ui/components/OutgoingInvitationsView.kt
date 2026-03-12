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
import com.vortexsoftware.android.sdk.api.VortexClient
import com.vortexsoftware.android.sdk.api.VortexError
import com.vortexsoftware.android.sdk.api.dto.OutgoingInvitation
import com.vortexsoftware.android.sdk.models.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ============================================================================
// Outgoing Invitations View
// ============================================================================

/**
 * Displays outgoing (sent) invitations with cancel functionality.
 * Uses centralized outgoing invitations from the ViewModel instead of making direct API calls.
 * Shows shimmer placeholders while loading, and supports SWR (stale-while-revalidate) pattern.
 * 
 * @param client VortexClient for API calls (used for revoking invitations)
 * @param config Configuration with callbacks
 * @param invitationSentEvent StateFlow that fires when an invitation is sent from another component
 * @param fetchedOutgoingInvitations StateFlow of API-fetched outgoing invitations from ViewModel
 * @param isOutgoingInvitationsLoaded StateFlow indicating whether outgoing invitations have been loaded
 * @param onRefreshOutgoingInvitations Callback to trigger a refresh of outgoing invitations (SWR)
 * @param block Optional ElementNode containing theme options and settings from widget config
 * @param modifier Modifier for the component
 */
@Composable
fun OutgoingInvitationsView(
    client: VortexClient,
    config: OutgoingInvitationsConfig?,
    invitationSentEvent: StateFlow<InvitationSentEvent?>? = null,
    fetchedOutgoingInvitations: StateFlow<List<OutgoingInvitation>>? = null,
    isOutgoingInvitationsLoaded: StateFlow<Boolean>? = null,
    onRefreshOutgoingInvitations: (() -> Unit)? = null,
    block: ElementNode? = null,
    modifier: Modifier = Modifier
) {
    // Extract theme values from block or use defaults
    val titleColor = block?.getThemeOption("--vrtx-outgoing-invitations-title-color")?.let { parseColor(it) } ?: DefaultForeground
    val titleFontSize = block?.getThemeOption("--vrtx-outgoing-invitations-title-font-size")?.let { parseFontSize(it) } ?: 16f
    val titleFontWeight = block?.getThemeOption("--vrtx-outgoing-invitations-title-font-weight")?.let { parseFontWeight(it) } ?: FontWeight.SemiBold
    
    val nameColor = block?.getThemeOption("--vrtx-outgoing-invitations-name-color")?.let { parseColor(it) } ?: DefaultForeground
    val nameFontSize = block?.getThemeOption("--vrtx-outgoing-invitations-name-font-size")?.let { parseFontSize(it) } ?: 16f
    val nameFontWeight = block?.getThemeOption("--vrtx-outgoing-invitations-name-font-weight")?.let { parseFontWeight(it) } ?: FontWeight.Medium
    
    val subtitleColor = block?.getThemeOption("--vrtx-outgoing-invitations-subtitle-color")?.let { parseColor(it) } ?: DefaultSecondaryForeground
    val subtitleFontSize = block?.getThemeOption("--vrtx-outgoing-invitations-subtitle-font-size")?.let { parseFontSize(it) } ?: 13f
    
    val avatarBackground = block?.getThemeOption("--vrtx-outgoing-invitations-avatar-background")?.let { parseColor(it) } ?: DefaultPrimaryBackground
    val avatarTextColor = block?.getThemeOption("--vrtx-outgoing-invitations-avatar-color")?.let { parseColor(it) } ?: DefaultPrimaryForeground
    
    val cancelButtonBackground = block?.getThemeOption("--vrtx-outgoing-invitations-cancel-button-background")?.let { parseColor(it) } ?: DefaultSecondaryBackground
    val cancelButtonTextColor = block?.getThemeOption("--vrtx-outgoing-invitations-cancel-button-color")?.let { parseColor(it) } ?: DefaultSecondaryForeground
    val cancelButtonBorderRadius = block?.getThemeOption("--vrtx-outgoing-invitations-cancel-button-border-radius")?.let { parseBorderRadius(it) } ?: 8f
    
    // Extract customization text from block settings
    val title = block?.getTitle()
    val cancelButtonText = block?.getCustomButtonLabel("cancelButton") ?: "Cancel"
    val emptyStateMessage = block?.getCustomButtonLabel("emptyStateMessage") ?: "No outgoing invitations"
    
    var invitations by remember { mutableStateOf<List<OutgoingInvitationItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionInProgress by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    // Observe centralized outgoing invitations from ViewModel
    val apiInvitations = fetchedOutgoingInvitations?.collectAsState()?.value ?: emptyList()
    val isLoaded = isOutgoingInvitationsLoaded?.collectAsState()?.value ?: true
    
    // Observe invitation sent events for SWR refresh
    val sentEvent = invitationSentEvent?.collectAsState()?.value
    
    // Refresh when an invitation is sent (SWR - no shimmer, just refresh in background)
    LaunchedEffect(sentEvent) {
        if (sentEvent != null) {
            onRefreshOutgoingInvitations?.invoke()
        }
    }
    
    // Rebuild invitations list when API data changes
    LaunchedEffect(apiInvitations, isLoaded) {
        if (isLoaded) {
            try {
                // Filter out shareable link invitations
                val filtered = apiInvitations.filter { invitation ->
                    invitation.targets?.firstOrNull()?.targetType != "share"
                }
                val apiItems = filtered.map { it.toDisplayItem() }
                // Merge with internal invitations from config
                val internalItems = config?.internalInvitations ?: emptyList()
                // Deduplicate: if an API invitation has the same id as an internal one, keep the API one
                val apiIds = apiItems.map { it.id }.toSet()
                val uniqueInternal = internalItems.filter { it.id !in apiIds }
                invitations = (apiItems + uniqueInternal).sortedBy { it.name.lowercase() }
            } catch (e: Exception) {
                if (invitations.isEmpty()) {
                    error = "Failed to load invitations"
                }
            }
        }
    }
    
    // Compute loading state: show shimmer only when not loaded AND we don't have stale data
    val showShimmer = !isLoaded && invitations.isEmpty()
    
    // Hide entire section when loaded and empty (align with iOS behavior)
    if (isLoaded && error == null && invitations.isEmpty()) {
        return
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // Title - always visible (even during shimmer)
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                fontSize = titleFontSize.sp,
                fontWeight = titleFontWeight,
                color = titleColor,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        
        when {
            showShimmer -> {
                ShimmerPlaceholderList(count = 3)
            }
            error != null && invitations.isEmpty() -> {
                Text(
                    text = error!!,
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            }
            else -> {
                invitations.forEach { item ->
                    // Use getSubtitle callback if provided, otherwise fall back to item.subtitle
                    val displaySubtitle = config?.getSubtitle?.invoke(item) ?: item.subtitle
                    OutgoingInvitationRow(
                        item = item,
                        displaySubtitle = displaySubtitle,
                        isLoading = actionInProgress == item.id,
                        avatarBackgroundColor = avatarBackground,
                        avatarTextColor = avatarTextColor,
                        nameColor = nameColor,
                        nameFontSize = nameFontSize,
                        nameFontWeight = nameFontWeight,
                        subtitleColor = subtitleColor,
                        subtitleFontSize = subtitleFontSize,
                        cancelButtonBackground = cancelButtonBackground,
                        cancelButtonTextColor = cancelButtonTextColor,
                        cancelButtonBorderRadius = cancelButtonBorderRadius,
                        cancelButtonText = cancelButtonText,
                        onCancel = {
                            scope.launch {
                                actionInProgress = item.id
                                val result = client.revokeInvitation(item.id)
                                val shouldRemove = result.isSuccess || result.exceptionOrNull().let {
                                    it is VortexError.NotFound || it is VortexError.Conflict
                                }
                                if (shouldRemove) {
                                    config?.onCancel?.invoke(item)
                                    invitations = invitations.filter { it.id != item.id }
                                    // Refresh outgoing invitations so filtered components (search, suggestions, find friends) update
                                    onRefreshOutgoingInvitations?.invoke()
                                }
                                actionInProgress = null
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OutgoingInvitationRow(
    item: OutgoingInvitationItem,
    displaySubtitle: String? = null,
    isLoading: Boolean,
    avatarBackgroundColor: Color,
    avatarTextColor: Color,
    nameColor: Color,
    nameFontSize: Float,
    nameFontWeight: FontWeight,
    subtitleColor: Color,
    subtitleFontSize: Float,
    cancelButtonBackground: Color,
    cancelButtonTextColor: Color,
    cancelButtonBorderRadius: Float,
    cancelButtonText: String,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        AvatarView(
            name = item.name,
            avatarUrl = item.avatarUrl,
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
                text = item.name,
                fontSize = nameFontSize.sp,
                fontWeight = nameFontWeight,
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = nameFontSize.sp
            )
            displaySubtitle?.let { subtitle ->
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = subtitleFontSize.sp,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = subtitleFontSize.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Cancel button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(cancelButtonBorderRadius.dp))
                .background(cancelButtonBackground)
                .clickable(enabled = !isLoading, onClick = onCancel)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = cancelButtonTextColor
                )
            } else {
                Text(
                    text = cancelButtonText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cancelButtonTextColor
                )
            }
        }
    }
}
