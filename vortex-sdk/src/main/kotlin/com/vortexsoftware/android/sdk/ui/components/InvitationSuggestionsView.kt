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
import com.vortexsoftware.android.sdk.api.dto.GroupDTO
import com.vortexsoftware.android.sdk.models.*
import kotlinx.coroutines.launch

// ============================================================================
// Invitation Suggestions View
// ============================================================================

/**
 * Displays suggested contacts with Invite and Dismiss buttons.
 * 
 * @param config Configuration with suggestions and callbacks
 * @param client VortexClient for API calls
 * @param widgetId Widget configuration ID
 * @param groups Optional groups for scoping invitations
 * @param block Optional ElementNode containing theme options and settings from widget config
 * @param modifier Modifier for the component
 */
@Composable
fun InvitationSuggestionsView(
    config: InvitationSuggestionsConfig,
    client: VortexClient,
    widgetId: String,
    groups: List<GroupDTO>?,
    unfurlConfig: UnfurlConfig? = null,
    onInvitationSent: (() -> Unit)? = null,
    outgoingInvitationUserIds: kotlinx.coroutines.flow.StateFlow<Set<String>>? = null,
    isOutgoingInvitationsLoaded: kotlinx.coroutines.flow.StateFlow<Boolean>? = null,
    block: ElementNode? = null,
    locale: String? = null,
    modifier: Modifier = Modifier
) {
    // Extract theme values from block or use defaults
    val titleColor = block?.getThemeOption("--vrtx-invitation-suggestions-title-color")?.let { parseColor(it) } ?: DefaultForeground
    val titleFontSize = block?.getThemeOption("--vrtx-invitation-suggestions-title-font-size")?.let { parseFontSize(it) } ?: 18f
    val titleFontWeight = block?.getThemeOption("--vrtx-invitation-suggestions-title-font-weight")?.let { parseFontWeight(it) } ?: FontWeight.SemiBold
    
    val nameColor = block?.getThemeOption("--vrtx-invitation-suggestions-name-color")?.let { parseColor(it) } ?: DefaultForeground
    val subtitleColor = block?.getThemeOption("--vrtx-invitation-suggestions-subtitle-color")?.let { parseColor(it) } ?: DefaultSecondaryForeground
    
    val avatarBackground = block?.getThemeOption("--vrtx-invitation-suggestions-avatar-background")?.let { parseColor(it) } ?: DefaultPrimaryBackground
    val avatarTextColor = block?.getThemeOption("--vrtx-invitation-suggestions-avatar-color")?.let { parseColor(it) } ?: DefaultPrimaryForeground
    
    val inviteButtonBackgroundStyle = block?.getThemeOption("--vrtx-invitation-suggestions-invite-button-background")?.let { parseBackgroundStyle(it) }
    val inviteButtonTextColor = block?.getThemeOption("--vrtx-invitation-suggestions-invite-button-color")?.let { parseColor(it) } ?: DefaultPrimaryForeground
    val inviteButtonBorderRadius = block?.getThemeOption("--vrtx-invitation-suggestions-invite-button-border-radius")?.let { parseBorderRadius(it) } ?: 8f
    
    val dismissButtonColor = block?.getThemeOption("--vrtx-invitation-suggestions-dismiss-button-color")?.let { parseColor(it) } ?: DefaultSecondaryForeground
    
    // Extract customization text from block settings
    val title = block?.getTitle()
    val inviteButtonText = block?.getCustomButtonLabel("inviteButton") ?: "Invite"
    
    var suggestions by remember { mutableStateOf(config.suggestions) }
    var actionInProgress by remember { mutableStateOf<String?>(null) }
    var invitedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dismissedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // Track which suggestion IDs have been selected for display (for maxDisplayCount)
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    val scope = rememberCoroutineScope()
    
    // Observe outgoing invitation user IDs for filtering
    val outgoingIds = outgoingInvitationUserIds?.collectAsState()?.value ?: emptySet()
    val outgoingLoaded = isOutgoingInvitationsLoaded?.collectAsState()?.value ?: true

    // When outgoing invitations are refreshed (e.g. after revoke), remove contacts
    // from invitedIds if they are no longer in the outgoing set
    LaunchedEffect(outgoingIds) {
        if (invitedIds.isNotEmpty()) {
            invitedIds = invitedIds.filter { id -> outgoingIds.contains(id) }.toSet()
        }
    }

    // IDs that should be excluded from display
    val excludedIds = dismissedIds + invitedIds + outgoingIds
    
    // Filter out dismissed, invited, and contacts with outstanding outgoing invitations
    val availableSuggestions = suggestions.filter { !excludedIds.contains(it.id) }
    
    // Apply maxDisplayCount logic: select a random subset and backfill as suggestions are removed
    val visibleSuggestions = if (config.maxDisplayCount != null && config.maxDisplayCount > 0) {
        val maxN = config.maxDisplayCount
        // Clean selectedIds: remove any that are no longer available
        val validSelected = selectedIds.filter { id -> availableSuggestions.any { it.id == id } }.toSet()
        // If we need more suggestions, backfill from the pool
        val needed = maxN - validSelected.size
        val newSelected = if (needed > 0) {
            val pool = availableSuggestions.filter { !validSelected.contains(it.id) }
            val backfill = pool.shuffled().take(needed).map { it.id }.toSet()
            validSelected + backfill
        } else {
            validSelected
        }
        // Update selectedIds state if changed
        if (newSelected != selectedIds) {
            selectedIds = newSelected
        }
        availableSuggestions.filter { newSelected.contains(it.id) }.sortedBy { it.name.lowercase() }
    } else {
        availableSuggestions
    }
    
    // Don't render if no suggestions (and outgoing invitations are loaded)
    if (visibleSuggestions.isEmpty() && outgoingLoaded) return
    
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
        
        visibleSuggestions.forEach { suggestion ->
            InvitationSuggestionRow(
                suggestion = suggestion,
                isLoading = actionInProgress == suggestion.id,
                avatarBackgroundColor = avatarBackground,
                avatarTextColor = avatarTextColor,
                nameColor = nameColor,
                subtitleColor = subtitleColor,
                inviteButtonBackgroundStyle = inviteButtonBackgroundStyle,
                inviteButtonTextColor = inviteButtonTextColor,
                inviteButtonBorderRadius = inviteButtonBorderRadius,
                inviteButtonText = inviteButtonText,
                dismissButtonColor = dismissButtonColor,
                onInvite = {
                    scope.launch {
                        actionInProgress = suggestion.id
                        // Merge contact metadata with unfurl metadata (contact metadata takes precedence)
                        val mergedMetadata = buildMap<String, Any> {
                            unfurlConfig?.toMetadata()?.let { putAll(it) }
                            suggestion.metadata?.let { putAll(it) }
                        }.takeIf { it.isNotEmpty() }
                        client.createInternalIdInvitation(
                            widgetId = widgetId,
                            internalId = suggestion.id,
                            contactName = suggestion.name,
                            contactAvatarUrl = suggestion.avatarUrl,
                            contactEmail = suggestion.email,
                            groups = groups,
                            metadata = mergedMetadata,
                            subtype = "suggestions",
                            locale = locale
                        ).onSuccess {
                            invitedIds = invitedIds + suggestion.id
                            config.onInvite?.invoke(suggestion)
                            onInvitationSent?.invoke()
                        }.onFailure {
                            // Silently fail - suggestion stays visible
                        }
                        actionInProgress = null
                    }
                },
                onDismiss = {
                    scope.launch {
                        dismissedIds = dismissedIds + suggestion.id
                        config.onDismiss?.invoke(suggestion)
                    }
                }
            )
        }
    }
}

@Composable
private fun InvitationSuggestionRow(
    suggestion: InvitationSuggestionContact,
    isLoading: Boolean,
    avatarBackgroundColor: Color,
    avatarTextColor: Color,
    nameColor: Color,
    subtitleColor: Color,
    inviteButtonBackgroundStyle: BackgroundStyle?,
    inviteButtonTextColor: Color,
    inviteButtonBorderRadius: Float,
    inviteButtonText: String,
    dismissButtonColor: Color,
    onInvite: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        AvatarView(
            name = suggestion.name,
            avatarUrl = suggestion.avatarUrl,
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
                text = suggestion.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
            // Use reason if available, otherwise show email as subtitle
            val subtitleText = suggestion.reason ?: suggestion.email
            if (subtitleText != null) {
                Text(
                    text = subtitleText,
                    fontSize = 13.sp,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Invite button with gradient support
        val inviteStyle = inviteButtonBackgroundStyle ?: BackgroundStyle.Solid(DefaultPrimaryBackground.value.toLong())
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(inviteButtonBorderRadius.dp))
                .styledBackground(inviteStyle)
                .clickable(enabled = !isLoading, onClick = onInvite)
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
                    text = inviteButtonText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = inviteButtonTextColor
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Dismiss button (X)
        IconButton(
            onClick = onDismiss,
            enabled = !isLoading
        ) {
            Text(
                text = "✕",
                fontSize = 18.sp,
                color = dismissButtonColor
            )
        }
    }
}
