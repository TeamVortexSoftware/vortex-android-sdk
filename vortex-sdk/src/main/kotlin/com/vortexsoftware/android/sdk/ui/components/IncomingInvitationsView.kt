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
import com.vortexsoftware.android.sdk.models.*
import kotlinx.coroutines.launch

// ============================================================================
// Incoming Invitations View
// ============================================================================

/**
 * Displays incoming (received) invitations with accept/delete functionality.
 * 
 * @param client VortexClient for API calls
 * @param config Configuration with callbacks and internal invitations
 * @param block Optional ElementNode containing theme options and settings from widget config
 * @param modifier Modifier for the component
 */
@Composable
fun IncomingInvitationsView(
    client: VortexClient,
    config: IncomingInvitationsConfig?,
    block: ElementNode? = null,
    modifier: Modifier = Modifier
) {
    // Extract theme values from block or use defaults
    val titleColor = block?.getThemeOption("--vrtx-incoming-invitations-title-color")?.let { parseColor(it) } ?: DefaultForeground
    val titleFontSize = block?.getThemeOption("--vrtx-incoming-invitations-title-font-size")?.let { parseFontSize(it) } ?: 16f
    val titleFontWeight = block?.getThemeOption("--vrtx-incoming-invitations-title-font-weight")?.let { parseFontWeight(it) } ?: FontWeight.SemiBold
    
    val nameColor = block?.getThemeOption("--vrtx-incoming-invitations-name-color")?.let { parseColor(it) } ?: DefaultForeground
    val nameFontSize = block?.getThemeOption("--vrtx-incoming-invitations-name-font-size")?.let { parseFontSize(it) } ?: 16f
    val nameFontWeight = block?.getThemeOption("--vrtx-incoming-invitations-name-font-weight")?.let { parseFontWeight(it) } ?: FontWeight.Medium
    
    val subtitleColor = block?.getThemeOption("--vrtx-incoming-invitations-subtitle-color")?.let { parseColor(it) } ?: DefaultSecondaryForeground
    val subtitleFontSize = block?.getThemeOption("--vrtx-incoming-invitations-subtitle-font-size")?.let { parseFontSize(it) } ?: 13f
    
    val avatarBackground = block?.getThemeOption("--vrtx-incoming-invitations-avatar-background")?.let { parseColor(it) } ?: DefaultPrimaryBackground
    val avatarTextColor = block?.getThemeOption("--vrtx-incoming-invitations-avatar-color")?.let { parseColor(it) } ?: DefaultPrimaryForeground
    
    // Accept button supports gradient backgrounds
    val acceptButtonBackgroundStyle = block?.getThemeOption("--vrtx-incoming-invitations-accept-button-background")?.let { parseBackgroundStyle(it) } 
        ?: BackgroundStyle.Solid(0xFF6291d5)
    val acceptButtonTextColor = block?.getThemeOption("--vrtx-incoming-invitations-accept-button-color")?.let { parseColor(it) } ?: DefaultPrimaryForeground
    val acceptButtonBorderRadius = block?.getThemeOption("--vrtx-incoming-invitations-accept-button-border-radius")?.let { parseBorderRadius(it) } ?: 8f
    
    val deleteButtonBackground = block?.getThemeOption("--vrtx-incoming-invitations-delete-button-background")?.let { parseColor(it) } ?: DefaultSecondaryBackground
    val deleteButtonTextColor = block?.getThemeOption("--vrtx-incoming-invitations-delete-button-color")?.let { parseColor(it) } ?: DefaultSecondaryForeground
    val deleteButtonBorderRadius = block?.getThemeOption("--vrtx-incoming-invitations-delete-button-border-radius")?.let { parseBorderRadius(it) } ?: 8f
    
    // Extract customization text from block settings
    val title = block?.getTitle()
    
    // Debug logging for title extraction
    android.util.Log.d("VortexSDK", "IncomingInvitationsView - block: ${block != null}, subtype: ${block?.subtype}")
    android.util.Log.d("VortexSDK", "IncomingInvitationsView - attributes keys: ${block?.attributes?.keys}")
    android.util.Log.d("VortexSDK", "IncomingInvitationsView - title attribute: ${block?.attributes?.get("title")}")
    android.util.Log.d("VortexSDK", "IncomingInvitationsView - getTitle() result: $title")
    
    val acceptButtonText = block?.getCustomButtonLabel("acceptButton") ?: "Accept"
    val deleteButtonText = block?.getCustomButtonLabel("deleteButton") ?: "Delete"
    val emptyStateMessage = block?.getCustomButtonLabel("emptyStateMessage") ?: "No incoming invitations"
    
    var invitations by remember { mutableStateOf<List<IncomingInvitationItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionInProgress by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    // Load invitations on first composition
    LaunchedEffect(Unit) {
        isLoading = true
        error = null
        
        val allInvitations = mutableListOf<IncomingInvitationItem>()
        
        // Add internal invitations from config
        config?.internalInvitations?.forEach { item ->
            allInvitations.add(item)
        }
        
        // Fetch from API
        client.getIncomingInvitations()
            .onSuccess { fetched ->
                // Filter out already accepted invitations
                val pendingInvitations = fetched.filter { inv ->
                    val status = inv.status?.lowercase() ?: ""
                    status != "accepted" && status != "accepted_elsewhere"
                }
                // Map to display items with isVortexInvitation = true
                val apiInvitations = pendingInvitations.map { inv ->
                    IncomingInvitationItem(
                        id = inv.id,
                        name = inv.senderIdentifier ?: "Unknown",
                        subtitle = inv.targets?.firstOrNull()?.targetValue,
                        avatarUrl = inv.avatarUrl,
                        isVortexInvitation = true,
                        invitation = inv
                    )
                }
                allInvitations.addAll(apiInvitations)
            }
            .onFailure { e ->
                // Silently fail - just show internal invitations
            }
        
        invitations = allInvitations.sortedBy { it.name.lowercase() }
        isLoading = false
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // Title
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
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Text(
                    text = error!!,
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            }
            invitations.isEmpty() -> {
                Text(
                    text = emptyStateMessage,
                    color = Color.Gray,
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
                    IncomingInvitationRow(
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
                        acceptButtonBackgroundStyle = acceptButtonBackgroundStyle,
                        acceptButtonTextColor = acceptButtonTextColor,
                        acceptButtonBorderRadius = acceptButtonBorderRadius,
                        acceptButtonText = acceptButtonText,
                        deleteButtonBackground = deleteButtonBackground,
                        deleteButtonTextColor = deleteButtonTextColor,
                        deleteButtonBorderRadius = deleteButtonBorderRadius,
                        deleteButtonText = deleteButtonText,
                        onAccept = {
                            scope.launch {
                                actionInProgress = item.id
                                // Call callback first
                                val shouldProceed = config?.onAccept?.invoke(item) ?: true
                                
                                if (shouldProceed && item.isVortexInvitation) {
                                    // Only call API for Vortex invitations
                                    val result = client.acceptInvitation(item.id)
                                    val shouldRemove = result.isSuccess || result.exceptionOrNull().let {
                                        it is VortexError.NotFound || it is VortexError.Conflict
                                    }
                                    if (shouldRemove) {
                                        invitations = invitations.filter { it.id != item.id }
                                    }
                                } else if (shouldProceed) {
                                    // For internal invitations, just remove from list
                                    invitations = invitations.filter { it.id != item.id }
                                }
                                actionInProgress = null
                            }
                        },
                        onDelete = {
                            scope.launch {
                                actionInProgress = item.id
                                
                                // Call callback first
                                val shouldProceed = config?.onDelete?.invoke(item) ?: true
                                
                                if (shouldProceed && item.isVortexInvitation) {
                                    // Only call API for Vortex invitations
                                    val result = client.deleteIncomingInvitation(item.id)
                                    val shouldRemove = result.isSuccess || result.exceptionOrNull().let {
                                        it is VortexError.NotFound || it is VortexError.Conflict
                                    }
                                    if (shouldRemove) {
                                        invitations = invitations.filter { it.id != item.id }
                                    }
                                } else if (shouldProceed) {
                                    // For internal invitations, just remove from list
                                    invitations = invitations.filter { it.id != item.id }
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
private fun IncomingInvitationRow(
    item: IncomingInvitationItem,
    displaySubtitle: String? = null,
    isLoading: Boolean,
    avatarBackgroundColor: Color,
    avatarTextColor: Color,
    nameColor: Color,
    nameFontSize: Float,
    nameFontWeight: FontWeight,
    subtitleColor: Color,
    subtitleFontSize: Float,
    acceptButtonBackgroundStyle: BackgroundStyle,
    acceptButtonTextColor: Color,
    acceptButtonBorderRadius: Float,
    acceptButtonText: String,
    deleteButtonBackground: Color,
    deleteButtonTextColor: Color,
    deleteButtonBorderRadius: Float,
    deleteButtonText: String,
    onAccept: () -> Unit,
    onDelete: () -> Unit
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
        
        // Delete button (shown first like iOS)
        Button(
            onClick = onDelete,
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = deleteButtonBackground,
                contentColor = deleteButtonTextColor
            ),
            shape = RoundedCornerShape(deleteButtonBorderRadius.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(deleteButtonText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Accept button with gradient support
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(acceptButtonBorderRadius.dp))
                .background(acceptButtonBackgroundStyle.toBrush())
                .clickable(enabled = !isLoading, onClick = onAccept)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = acceptButtonTextColor
                )
            } else {
                Text(
                    text = acceptButtonText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = acceptButtonTextColor
                )
            }
        }
    }
}
