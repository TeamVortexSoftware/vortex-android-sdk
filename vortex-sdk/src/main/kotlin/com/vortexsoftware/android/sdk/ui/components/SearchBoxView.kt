package com.vortexsoftware.android.sdk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vortexsoftware.android.sdk.api.VortexClient
import com.vortexsoftware.android.sdk.api.dto.GroupDTO
import com.vortexsoftware.android.sdk.models.*
import kotlinx.coroutines.launch

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
    outgoingInvitationUserIds: kotlinx.coroutines.flow.StateFlow<Set<String>>? = null,
    block: ElementNode? = null,
    locale: String? = null,
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
    
    val inputColor = block?.getThemeOption("--vrtx-search-box-input-color")?.let { parseColor(it) } ?: DefaultForeground
    val inputFontSize = block?.getThemeOption("--vrtx-search-box-input-font-size")?.let { parseFontSize(it) } ?: 16f
    val inputFontFamily = block?.getThemeOption("--vrtx-search-box-input-font-family")?.let { parseFontFamily(it) }
    val inputFontWeight = block?.getThemeOption("--vrtx-search-box-input-font-weight")?.let { parseFontWeight(it) }
    
    val placeholderColor = block?.getThemeOption("--vrtx-search-box-placeholder-color")?.let { parseColor(it) } ?: Color(0xFF999999)
    
    // Extract customization text from block settings
    val title = block?.getTitle()
    val connectButtonText = block?.getCustomButtonLabel("connectButton") ?: "Connect"
    val placeholder = block?.getCustomButtonLabel("searchPlaceholder") ?: "Search..."
    val noResultsMessage = block?.getCustomButtonLabel("noResultsMessage") ?: "No results found"
    
    // Observe outgoing invitation user IDs for filtering
    val outgoingIds = outgoingInvitationUserIds?.collectAsState()?.value ?: emptySet()
    
    // Local state
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchBoxContact>?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var actionInProgress by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
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
                        color = placeholderColor
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false
                ),
                modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                shape = RoundedCornerShape(8.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = inputColor,
                    fontSize = inputFontSize.sp,
                    fontFamily = inputFontFamily,
                    fontWeight = inputFontWeight
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = borderColor,
                    focusedBorderColor = DefaultPrimaryBackground
                )
            )
            
            // Search button
            val searchButtonStyle = searchButtonBackgroundStyle ?: BackgroundStyle.Solid(DefaultPrimaryBackground.value.toLong())
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
                        .styledBackground(searchButtonStyle),
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
        
        // Results (filtered by outgoing invitation user IDs)
        searchResults?.let { allResults ->
            val results = allResults.filter { !outgoingIds.contains(it.userId) }
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
                                    contactEmail = contact.email,
                                    groups = groups,
                                    metadata = mergedMetadata,
                                    subtype = "search-box",
                                    locale = locale
                                ).onSuccess {
                                    // Remove connected contact from results; clear entirely if none remain
                                    val remaining = searchResults?.filter { it.id != contact.id }
                                    searchResults = remaining?.takeIf { it.isNotEmpty() }
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
