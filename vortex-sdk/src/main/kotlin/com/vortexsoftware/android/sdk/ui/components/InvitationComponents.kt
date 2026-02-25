package com.vortexsoftware.android.sdk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.vortexsoftware.android.sdk.api.VortexClient
import com.vortexsoftware.android.sdk.api.dto.GroupDTO
import com.vortexsoftware.android.sdk.api.dto.IncomingInvitation
import com.vortexsoftware.android.sdk.api.dto.OutgoingInvitation
import com.vortexsoftware.android.sdk.models.*
import com.vortexsoftware.android.sdk.ui.theme.toComposeColor
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

// Default colors matching iOS SDK
private val DefaultPrimaryBackground = Color(0xFF6291d5)
private val DefaultPrimaryForeground = Color.White
private val DefaultSecondaryBackground = Color.White
private val DefaultSecondaryForeground = Color(0xFF353e5c)
private val DefaultForeground = Color(0xFF334153)

/**
 * Convert a BackgroundStyle to a Compose Brush
 */
private fun BackgroundStyle.toBrush(): Brush {
    return when (this) {
        is BackgroundStyle.Solid -> Brush.linearGradient(listOf(color.toComposeColor(), color.toComposeColor()))
        is BackgroundStyle.Gradient -> {
            // Convert CSS angle to Compose gradient direction
            // CSS: 0deg = bottom to top, 90deg = left to right
            // We need to calculate start and end points based on angle
            val angleRad = Math.toRadians(angle.toDouble())
            val colors = colorStops.sortedBy { it.location }.map { it.color.toComposeColor() }
            Brush.linearGradient(colors)
        }
    }
}

/**
 * Parse a background style string (solid color or gradient)
 */
private fun parseBackgroundStyle(value: String?): BackgroundStyle? {
    if (value.isNullOrBlank()) return null
    return BackgroundStyle.parse(value)
}

/**
 * Extension to convert Long color to Compose Color
 */
private fun Long.toComposeColor(): Color = Color(this)

/**
 * Helper to parse hex color string to Color
 */
private fun parseColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try {
        val cleanHex = hex.removePrefix("#")
        when (cleanHex.length) {
            6 -> Color(android.graphics.Color.parseColor("#$cleanHex"))
            8 -> Color(android.graphics.Color.parseColor("#$cleanHex"))
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Helper to parse font weight string to FontWeight
 */
private fun parseFontWeight(weight: String?): FontWeight? {
    return when (weight?.lowercase()) {
        "100", "thin" -> FontWeight.Thin
        "200", "extralight" -> FontWeight.ExtraLight
        "300", "light" -> FontWeight.Light
        "400", "normal", "regular" -> FontWeight.Normal
        "500", "medium" -> FontWeight.Medium
        "600", "semibold" -> FontWeight.SemiBold
        "700", "bold" -> FontWeight.Bold
        "800", "extrabold" -> FontWeight.ExtraBold
        "900", "black" -> FontWeight.Black
        else -> null
    }
}

/**
 * Helper to parse font size string (e.g., "16px") to sp value
 */
private fun parseFontSize(size: String?): Float? {
    if (size.isNullOrBlank()) return null
    return size.replace("px", "").toFloatOrNull()
}

/**
 * Helper to parse border radius string (e.g., "8px") to dp value
 */
private fun parseBorderRadius(radius: String?): Float? {
    if (radius.isNullOrBlank()) return null
    return radius.replace("px", "").toFloatOrNull()
}

// ============================================================================
// Outgoing Invitations View
// ============================================================================

/**
 * Displays outgoing (sent) invitations with cancel functionality.
 * 
 * @param client VortexClient for API calls
 * @param config Configuration with callbacks
 * @param block Optional ElementNode containing theme options and settings from widget config
 * @param modifier Modifier for the component
 */
@Composable
fun OutgoingInvitationsView(
    client: VortexClient,
    config: OutgoingInvitationsConfig?,
    invitationSentEvent: kotlinx.coroutines.flow.StateFlow<InvitationSentEvent?>? = null,
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
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionInProgress by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    // Refresh when an invitation is sent from another component
    val sentEvent = invitationSentEvent?.collectAsState()?.value
    
    // Load invitations on first composition and when sentEvent changes
    LaunchedEffect(Unit, sentEvent) {
        isLoading = true
        error = null
        client.getOutgoingInvitations()
            .onSuccess { fetched ->
                // Filter out shareable link invitations
                val filtered = fetched.filter { invitation ->
                    invitation.targets?.firstOrNull()?.targetType != "share"
                }
                val apiItems = filtered.map { it.toDisplayItem() }
                // Merge with internal invitations from config
                val internalItems = config?.internalInvitations ?: emptyList()
                // Deduplicate: if an API invitation has the same id as an internal one, keep the API one
                val internalIds = apiItems.map { it.id }.toSet()
                val uniqueInternal = internalItems.filter { it.id !in internalIds }
                invitations = (apiItems + uniqueInternal).sortedBy { it.name.lowercase() }
            }
            .onFailure { e ->
                error = "Failed to load invitations"
            }
        isLoading = false
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
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
                                client.revokeInvitation(item.id)
                                    .onSuccess {
                                        config?.onCancel?.invoke(item)
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
        Button(
            onClick = onCancel,
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = cancelButtonBackground,
                contentColor = cancelButtonTextColor
            ),
            shape = RoundedCornerShape(cancelButtonBorderRadius.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = cancelButtonTextColor
                )
            } else {
                Text(cancelButtonText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

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
            .padding(horizontal = 16.dp)
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
                                    client.acceptInvitation(item.id)
                                        .onSuccess {
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
                                    client.deleteIncomingInvitation(item.id)
                                        .onSuccess {
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

// ============================================================================
// Find Friends View
// ============================================================================

/**
 * Displays contacts for the Find Friends feature with Connect/Invite buttons.
 * 
 * @param config Configuration with contacts and callbacks
 * @param client VortexClient for API calls
 * @param widgetId Widget configuration ID
 * @param groups Optional groups for scoping invitations
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
    
    // Filter out already connected contacts
    val visibleContacts = contacts.filter { !connectedIds.contains(it.id) }
    
    // Don't render if no contacts
    if (visibleContacts.isEmpty()) return
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
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
                            metadata = mergedMetadata
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
        val backgroundBrush = (connectButtonBackgroundStyle ?: BackgroundStyle.Solid(DefaultPrimaryBackground.value.toLong())).toBrush()
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(connectButtonBorderRadius.dp))
                .background(backgroundBrush)
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
    block: ElementNode? = null,
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
    
    val scope = rememberCoroutineScope()
    
    // Filter out dismissed and invited suggestions
    val visibleSuggestions = suggestions.filter { 
        !dismissedIds.contains(it.id) && !invitedIds.contains(it.id) 
    }
    
    // Don't render if no suggestions
    if (visibleSuggestions.isEmpty()) return
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
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
                            groups = groups,
                            metadata = mergedMetadata
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
            Text(
                text = subtitleText,
                fontSize = 13.sp,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Invite button with gradient support
        val backgroundBrush = (inviteButtonBackgroundStyle ?: BackgroundStyle.Solid(DefaultPrimaryBackground.value.toLong())).toBrush()
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(inviteButtonBorderRadius.dp))
                .background(backgroundBrush)
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

// ============================================================================
// Shared Components
// ============================================================================

/**
 * Avatar view with image loading and initials fallback
 * Uses Coil for async image loading, matching iOS SDK's AsyncImage behavior
 */
@Composable
private fun AvatarView(
    name: String,
    avatarUrl: String?,
    backgroundColor: Color,
    textColor: Color,
    size: Int = 44
) {
    val initials = name
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
    
    if (!avatarUrl.isNullOrBlank()) {
        // Load image from URL with initials as fallback
        SubcomposeAsyncImage(
            model = avatarUrl,
            contentDescription = "$name avatar",
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Success -> {
                    SubcomposeAsyncImageContent()
                }
                is AsyncImagePainter.State.Loading -> {
                    // Show initials while loading
                    InitialsAvatar(initials, backgroundColor, textColor, size)
                }
                else -> {
                    // Show initials on error or empty
                    InitialsAvatar(initials, backgroundColor, textColor, size)
                }
            }
        }
    } else {
        // No URL provided, show initials
        InitialsAvatar(initials, backgroundColor, textColor, size)
    }
}

/**
 * Initials-based avatar fallback
 */
@Composable
private fun InitialsAvatar(
    initials: String,
    backgroundColor: Color,
    textColor: Color,
    size: Int
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.ifEmpty { "?" },
            fontSize = (size / 2.75).sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

// ============================================================================
// Search Box Component
// ============================================================================

/**
 * Search Box component that displays a search input with a search button,
 * and renders matching contacts below with Connect buttons.
 * When user taps Connect, an invitation is created via the Vortex backend.
 *
 * @param config Configuration with search callback and invitation created callback
 * @param client VortexClient for API calls
 * @param widgetId Widget configuration ID
 * @param groups Optional groups for scoping invitations
 * @param unfurlConfig Optional unfurl configuration for link metadata
 * @param onInvitationSent Callback when an invitation is successfully sent
 * @param block Optional ElementNode containing theme options and settings from widget config
 * @param modifier Modifier for the component
 */
@Composable
fun SearchBoxView(
    config: SearchBoxConfig,
    client: VortexClient,
    widgetId: String,
    groups: List<GroupDTO>?,
    unfurlConfig: UnfurlConfig? = null,
    onInvitationSent: (() -> Unit)? = null,
    block: ElementNode? = null,
    modifier: Modifier = Modifier
) {
    // Extract theme values from block or use defaults
    val titleColor = block?.getThemeOption("--vrtx-search-box-title-color")?.let { parseColor(it) } ?: DefaultForeground
    val titleFontSize = block?.getThemeOption("--vrtx-search-box-title-font-size")?.let { parseFontSize(it) } ?: 18f
    val titleFontWeight = block?.getThemeOption("--vrtx-search-box-title-font-weight")?.let { parseFontWeight(it) } ?: FontWeight.SemiBold
    
    val nameColor = block?.getThemeOption("--vrtx-search-box-contact-name-color")?.let { parseColor(it) } ?: DefaultForeground
    val subtitleColor = block?.getThemeOption("--vrtx-search-box-contact-subtitle-color")?.let { parseColor(it) } ?: DefaultSecondaryForeground
    
    val avatarBackground = block?.getThemeOption("--vrtx-search-box-avatar-background")?.let { parseColor(it) } ?: DefaultPrimaryBackground
    val avatarTextColor = block?.getThemeOption("--vrtx-search-box-avatar-color")?.let { parseColor(it) } ?: DefaultPrimaryForeground
    
    val connectButtonBackgroundStyle = block?.getThemeOption("--vrtx-search-box-connect-button-background")?.let { parseBackgroundStyle(it) }
    val connectButtonTextColor = block?.getThemeOption("--vrtx-search-box-connect-button-color")?.let { parseColor(it) } ?: DefaultPrimaryForeground
    val connectButtonBorderRadius = block?.getThemeOption("--vrtx-search-box-connect-button-border-radius")?.let { parseBorderRadius(it) } ?: 8f
    
    val searchButtonBackgroundStyle = block?.getThemeOption("--vrtx-search-box-search-button-background")?.let { parseBackgroundStyle(it) }
    val searchButtonTextColor = block?.getThemeOption("--vrtx-search-box-search-button-color")?.let { parseColor(it) } ?: DefaultPrimaryForeground
    
    val borderColor = block?.getThemeOption("--vrtx-search-box-border-color")?.let { parseColor(it) } ?: Color(0xFFCCCCCC)
    
    // Extract customization text from block settings
    val title = block?.getTitle()
    val connectButtonText = block?.getCustomButtonLabel("connectButton") ?: "Connect"
    val placeholder = block?.getCustomButtonLabel("searchPlaceholder") ?: "Search..."
    val noResultsMessage = block?.getCustomButtonLabel("noResultsMessage") ?: "No results found"
    
    // Local state
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchBoxContact>?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var actionInProgress by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
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
        
        // Search input row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search text field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = placeholder,
                        fontSize = 16.sp,
                        color = Color(0xFF999999)
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = borderColor,
                    focusedBorderColor = DefaultPrimaryBackground
                )
            )
            
            // Search button
            val searchButtonBrush = (searchButtonBackgroundStyle ?: BackgroundStyle.Solid(DefaultPrimaryBackground.value.toLong())).toBrush()
            Button(
                onClick = {
                    if (searchQuery.trim().isNotEmpty() && !isSearching) {
                        scope.launch {
                            isSearching = true
                            try {
                                val results = config.onSearch(searchQuery.trim())
                                searchResults = results
                            } catch (_: Exception) {
                                searchResults = emptyList()
                            }
                            isSearching = false
                        }
                    }
                },
                enabled = searchQuery.trim().isNotEmpty() && !isSearching,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(searchButtonBrush, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = searchButtonTextColor,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = searchButtonTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Results
        searchResults?.let { results ->
            if (results.isEmpty() && !isSearching) {
                // No results message
                Text(
                    text = noResultsMessage,
                    fontSize = 14.sp,
                    color = DefaultSecondaryForeground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                results.forEach { contact ->
                    SearchBoxContactRow(
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
                                    internalId = contact.userId,
                                    contactName = contact.name,
                                    contactAvatarUrl = contact.avatarUrl,
                                    groups = groups,
                                    metadata = mergedMetadata
                                ).onSuccess {
                                    // Remove connected contact from results
                                    searchResults = searchResults?.filter { it.id != contact.id }
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
    }
}

/**
 * A single contact row in the Search Box results list
 */
@Composable
private fun SearchBoxContactRow(
    contact: SearchBoxContact,
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
        val backgroundBrush = (connectButtonBackgroundStyle ?: BackgroundStyle.Solid(DefaultPrimaryBackground.value.toLong())).toBrush()
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(connectButtonBorderRadius.dp))
                .background(backgroundBrush)
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

// ============================================================================
// Extension Functions
// ============================================================================

private fun OutgoingInvitation.toDisplayItem(): OutgoingInvitationItem {
    val target = targets?.firstOrNull()
    val targetName = target?.targetName
    val targetValue = target?.targetValue
    
    val displayName = targetName ?: targetValue ?: senderIdentifier ?: "Unknown"
    val subtitle = targetValue ?: senderIdentifier
    val metadataMap = metadata?.mapValues { (_, v) -> v.jsonPrimitiveOrNull()?.content ?: v.toString() }
    
    return OutgoingInvitationItem(
        id = id,
        name = displayName,
        subtitle = subtitle,
        avatarUrl = target?.targetAvatarUrl ?: avatarUrl,
        isVortexInvitation = true,
        invitation = this,
        metadata = metadataMap
    )
}

private fun IncomingInvitation.toDisplayItem(): IncomingInvitationItem {
    val target = targets?.firstOrNull()
    val targetName = target?.targetName
    val targetValue = target?.targetValue
    
    val displayName = targetName ?: senderIdentifier ?: "Unknown"
    val subtitle = targetValue ?: senderIdentifier
    val metadataMap = metadata?.mapValues { (_, v) -> v.jsonPrimitiveOrNull()?.content ?: v.toString() }
    
    return IncomingInvitationItem(
        id = id,
        name = displayName,
        subtitle = subtitle,
        avatarUrl = avatarUrl,
        isVortexInvitation = true,
        invitation = this,
        metadata = metadataMap
    )
}

private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveOrNull(): kotlinx.serialization.json.JsonPrimitive? {
    return this as? kotlinx.serialization.json.JsonPrimitive
}
