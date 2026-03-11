package com.vortexsoftware.android.sdk.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.common.api.Scope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.vortexsoftware.android.sdk.api.VortexClient
import com.vortexsoftware.android.sdk.analytics.VortexAnalyticsClient
import com.vortexsoftware.android.sdk.analytics.VortexAnalyticsEvent
import com.vortexsoftware.android.sdk.api.dto.GroupDTO
import com.vortexsoftware.android.sdk.models.*
import com.vortexsoftware.android.sdk.ui.components.*
import com.vortexsoftware.android.sdk.ui.icons.VortexIcon
import com.vortexsoftware.android.sdk.ui.icons.VortexIconName
import com.vortexsoftware.android.sdk.ui.theme.VortexColors
import com.vortexsoftware.android.sdk.ui.theme.toComposeColor
import com.vortexsoftware.android.sdk.ui.theme.topRoundedCornerShape
import com.vortexsoftware.android.sdk.viewmodels.VortexInviteViewModel

/**
 * Main entry point for the Vortex Invite widget
 * 
 * @param componentId The widget component ID from Vortex dashboard
 * @param jwt Optional JWT token for authenticated requests
 * @param apiBaseUrl Base URL for the Vortex API (defaults to production)
 * @param analyticsBaseUrl Base URL for the Vortex Analytics collector (defaults to production)
 * @param group Optional group context for the invitation
 * @param segmentation Optional segmentation data for analytics filtering
 * @param googleClientId Optional Google OAuth client ID for Google Contacts integration
 * @param enableLogging Whether to enable debug logging for API requests
 * @param onDismiss Callback when the widget is dismissed
 * @param onEvent Callback for receiving analytics events
 * @param widgetConfiguration Optional prefetched widget configuration for instant rendering
 * @param deploymentId Optional deployment ID associated with prefetched configuration
 * @param locale Optional BCP 47 language code for internationalization (e.g., "pt-BR", "en-US")
 * @param findFriendsConfig Optional configuration for the Find Friends component
 * @param inviteContactsConfig Optional configuration for the Invite Contacts (SMS) component
 * @param invitationSuggestionsConfig Optional configuration for the Invitation Suggestions component
 * @param incomingInvitationsConfig Optional configuration for the Incoming Invitations component
 * @param outgoingInvitationsConfig Optional configuration for the Outgoing Invitations component
 * @param scope Optional scope identifier for scoping invitations (e.g., team ID, project ID).
 *              Used together with [scopeType] to create group context for API calls.
 *              This is a convenience alternative to [group] — if [group] is provided, it takes precedence.
 * @param scopeType Optional type of the scope (e.g., "team", "project").
 *                  Used together with [scope] to create group context for API calls.
 */
@Composable
fun VortexInviteView(
    componentId: String,
    jwt: String? = null,
    apiBaseUrl: String = VortexClient.DEFAULT_BASE_URL,
    analyticsBaseUrl: String? = null,
    group: GroupDTO? = null,
    segmentation: Map<String, Any>? = null,
    googleClientId: String? = null,
    enableLogging: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    onEvent: ((VortexAnalyticsEvent) -> Unit)? = null,
    widgetConfiguration: WidgetConfiguration? = null,
    deploymentId: String? = null,
    locale: String? = null,
    findFriendsConfig: FindFriendsConfig? = null,
    inviteContactsConfig: InviteContactsConfig? = null,
    invitationSuggestionsConfig: InvitationSuggestionsConfig? = null,
    incomingInvitationsConfig: IncomingInvitationsConfig? = null,
    outgoingInvitationsConfig: OutgoingInvitationsConfig? = null,
    searchBoxConfig: SearchBoxConfig? = null,
    unfurlConfig: UnfurlConfig? = null,
    scope: String? = null,
    scopeType: String? = null
) {
    // Convert scope/scopeType to GroupDTO if group is not provided
    val effectiveGroup = group ?: if (scope != null && scopeType != null) {
        GroupDTO.create(id = scope, name = scope, type = scopeType)
    } else {
        null
    }
    val viewModel: VortexInviteViewModel = viewModel(
        factory = VortexInviteViewModel.Factory(
            componentId = componentId,
            jwt = jwt,
            apiBaseUrl = apiBaseUrl,
            analyticsBaseUrl = analyticsBaseUrl,
            group = effectiveGroup,
            segmentation = segmentation,
            googleClientId = googleClientId,
            onDismiss = onDismiss,
            onEvent = onEvent,
            enableLogging = enableLogging,
            initialConfiguration = widgetConfiguration,
            initialDeploymentId = deploymentId,
            locale = locale,
            findFriendsConfig = findFriendsConfig,
            inviteContactsConfig = inviteContactsConfig,
            invitationSuggestionsConfig = invitationSuggestionsConfig,
            incomingInvitationsConfig = incomingInvitationsConfig,
            outgoingInvitationsConfig = outgoingInvitationsConfig,
            searchBoxConfig = searchBoxConfig,
            unfurlConfig = unfurlConfig
        )
    )
    
    // Load configuration on first composition
    LaunchedEffect(componentId) {
        viewModel.loadConfiguration()
    }
    
    VortexInviteContent(viewModel = viewModel)
}

@Composable
private fun VortexInviteContent(viewModel: VortexInviteViewModel) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val sheetHeight = screenHeight * 0.8f
    
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val widgetConfig by viewModel.configuration.collectAsState()
    val currentView by viewModel.currentView.collectAsState()
    
    val theme = widgetConfig?.theme
    // Priority: 1) Root element's inline style, 2) Theme's --color-surface-background, 3) Default
    val rootElement = widgetConfig?.elements?.firstOrNull()
    val rootBackgroundValue = rootElement?.style?.get("background")
    val themeSurfaceColor = theme?.surfaceBackgroundColor
    val surfaceColor = when {
        rootBackgroundValue != null && rootBackgroundValue != "transparent" -> {
            parseHexColor(rootBackgroundValue)?.toComposeColor() ?: MaterialTheme.colorScheme.surface
        }
        themeSurfaceColor != null -> {
            themeSurfaceColor.toComposeColor()
        }
        else -> MaterialTheme.colorScheme.surface
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(onClick = { viewModel.dismiss() }),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Main content sheet
        Column(
            modifier = Modifier
                .height(sheetHeight)
                .fillMaxWidth()
                .clip(topRoundedCornerShape(20))
                .background(surfaceColor)
                .clickable(enabled = false) {} // Prevent click-through
        ) {
            // Header with close/back button and optional form title
            HeaderView(
                currentView = currentView,
                formTitle = viewModel.configFormTitle,
                formTitleColor = viewModel.formTitleColor?.toComposeColor(),
                formTitleFontSize = viewModel.formTitleFontSize,
                formTitleFontWeight = viewModel.formTitleFontWeight,
                onBack = { viewModel.navigateBack() },
                onClose = { viewModel.dismiss() }
            )
            
            // Content based on loading state
            when {
                isLoading -> LoadingView()
                error != null -> ErrorView(error = error!!, onRetry = { viewModel.loadConfiguration() })
                widgetConfig != null -> {
                    if (widgetConfig?.elements?.isEmpty() == true) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No content to display", color = VortexColors.Gray66)
                        }
                    } else {
                        when (currentView) {
                            InviteViewState.MAIN -> MainFormView(viewModel = viewModel)
                            InviteViewState.EMAIL_ENTRY -> EmailEntryContentView(viewModel = viewModel)
                            InviteViewState.CONTACTS_PICKER -> ContactsPickerView(viewModel = viewModel)
                            InviteViewState.GOOGLE_CONTACTS_PICKER -> GoogleContactsPickerView(viewModel = viewModel)
                            InviteViewState.QR_CODE -> QrCodeView(viewModel = viewModel)
                            InviteViewState.INVITE_CONTACTS -> InviteContactsSecondaryView(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderView(
    currentView: InviteViewState,
    formTitle: String? = null,
    formTitleColor: Color? = null,
    formTitleFontSize: Float? = null,
    formTitleFontWeight: FontWeight? = null,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val defaultTitleColor = Color(0xFF1A1A1A)
    val iconColor = (formTitleColor ?: defaultTitleColor).copy(alpha = 0.75f)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = if (currentView == InviteViewState.MAIN) onClose else onBack
        ) {
            VortexIcon(
                name = if (currentView == InviteViewState.MAIN) VortexIconName.CLOSE else VortexIconName.ARROW_BACK,
                size = 24,
                color = iconColor
            )
        }
        if (formTitle != null) {
            Text(
                text = formTitle,
                fontSize = (formTitleFontSize ?: 17f).sp,
                fontWeight = formTitleFontWeight ?: FontWeight.SemiBold,
                color = formTitleColor ?: defaultTitleColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            // Balancing spacer to keep title centered
            Spacer(modifier = Modifier.size(48.dp))
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = VortexColors.Gray66)
    }
}

@Composable
private fun ErrorView(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = VortexColors.Red
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            fontSize = 14.sp,
            color = VortexColors.Gray66
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun MainFormView(viewModel: VortexInviteViewModel) {
    val config by viewModel.configuration.collectAsState()
    val formData by viewModel.formData.collectAsState()
    val selectedContacts by viewModel.selectedContacts.collectAsState()
    val contactInviteStates by viewModel.contactInviteStates.collectAsState()
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Render configuration elements
        config?.elements?.forEach { element ->
            RenderElement(
                element = element,
                viewModel = viewModel,
                formData = formData
            )
        }
        
        // Show selected contacts if any
        if (selectedContacts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Selected Contacts",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = VortexColors.Gray66,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            ContactListView(
                contacts = selectedContacts,
                inviteStates = contactInviteStates,
                onInvite = { contact -> viewModel.inviteContact(contact) }
            )
        }
        
        // Note: Components like FindFriendsView, InviteContactsView, InvitationSuggestionsView,
        // IncomingInvitationsView, and OutgoingInvitationsView are rendered based on widget
        // configuration blocks (via RenderBlock). They are NOT rendered here as standalone
        // components to avoid duplication and to respect the widget configuration order.
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun RenderElement(
    element: ElementNode,
    viewModel: VortexInviteViewModel,
    formData: Map<String, String>
) {
    // Skip hidden elements
    if (element.getBoolean("hidden") == true) return

    when {
        element.type == "row" || element.tagName == "vrtx-row" || element.tagName == "row_layout" -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                element.children.forEach { child ->
                    Box(modifier = Modifier.weight(1f)) {
                        RenderElement(child, viewModel, formData)
                    }
                }
            }
        }
        element.type == "column" || element.tagName == "vrtx-column" || element.type == "root" || element.tagName == "vrtx-root" || element.tagName == "div" -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (element.type == "root" || element.tagName == "vrtx-root") {
                            Modifier.padding(horizontal = 16.dp)
                        } else {
                            Modifier
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                element.children.forEach { child ->
                    RenderElement(child, viewModel, formData)
                }
            }
        }
        element.type == "block" -> RenderBlock(element, viewModel, formData)
        else -> {
            // Render children for unknown container types
            element.children.forEach { child ->
                RenderElement(child, viewModel, formData)
            }
        }
    }
}

@Composable
private fun RenderBlock(
    block: ElementNode,
    viewModel: VortexInviteViewModel,
    formData: Map<String, String>
) {
    val config by viewModel.configuration.collectAsState()
    val theme = block.theme ?: config?.theme
    
    when (block.subtype) {
        // Headings
        "h1", "h2", "h3", "h4", "h5", "h6", "vrtx-heading" -> {
            HeadingView(
                block = block
            )
        }
        
        // Text elements
        "text", "paragraph", "span", "p", "vrtx-text" -> {
            TextView(
                block = block
            )
        }
        
        // Form label
        "label", "vrtx-form-label" -> {
            FormLabelView(
                block = block
            )
        }
        
        // Image
        "image", "vrtx-image" -> {
            ImageView(
                block = block
            )
        }
        
        // Link
        "link", "vrtx-link" -> {
            LinkView(
                block = block
            )
        }
        
        // Divider
        "divider", "vrtx-divider" -> {
            DividerView()
        }
        
        // Form inputs
        "textbox", "vrtx-textbox" -> {
            val name = block.getString("name") ?: ""
            TextboxView(
                block = block,
                value = formData[name] ?: "",
                onValueChange = { viewModel.updateFormField(name, it) }
            )
        }
        
        "textarea", "vrtx-textarea" -> {
            val name = block.getString("name") ?: ""
            TextareaView(
                block = block,
                value = formData[name] ?: "",
                onValueChange = { viewModel.updateFormField(name, it) }
            )
        }
        
        "select", "vrtx-select" -> {
            val name = block.getString("name") ?: ""
            SelectView(
                block = block,
                value = formData[name] ?: "",
                onValueChange = { viewModel.updateFormField(name, it) }
            )
        }
        
        "radio", "vrtx-radio" -> {
            val name = block.getString("name") ?: ""
            RadioView(
                block = block,
                value = formData[name] ?: "",
                onValueChange = { viewModel.updateFormField(name, it) }
            )
        }
        
        "checkbox", "vrtx-checkbox" -> {
            val name = block.getString("name") ?: ""
            CheckboxView(
                block = block,
                checked = formData[name]?.toBoolean() ?: false,
                onCheckedChange = { viewModel.updateFormField(name, it.toString()) }
            )
        }
        
        // Share options
        "share-options", "vrtx-share-options" -> {
            ShareOptionsView(
                block = block,
                viewModel = viewModel
            )
        }
        
        // Contacts import
        "contacts-import", "vrtx-contacts-import" -> {
            ContactsImportView(
                block = block,
                viewModel = viewModel
            )
        }
        
        // Email invitations
        "email-invitations", "vrtx-email-invitations" -> {
            val role = block.vortex?.role
            if (role != "email") {
                ContactsImportView(
                    block = block,
                    viewModel = viewModel
                )
            }
        }
        
        // Submit button - intentionally not rendered to match RN SDK behavior
        // The RN SDK doesn't render standalone submit buttons from the widget configuration
        "submit", "vrtx-submit" -> {
            // Skip rendering - the submit functionality is handled by other components
            // (e.g., EmailEntryView has its own send button)
        }
        
        // Button
        "button", "vrtx-button" -> {
            ButtonView(
                block = block,
                theme = theme,
                onClick = { /* Handle button click */ }
            )
        }
        
        // Find Friends component
        "find-friends", "vrtx-find-friends" -> {
            viewModel.findFriendsConfig?.let { findFriendsConfig ->
                FindFriendsView(
                    config = findFriendsConfig,
                    client = viewModel.vortexClient,
                    widgetId = viewModel.widgetId,
                    groups = viewModel.groupList,
                    unfurlConfig = viewModel.unfurlConfig,
                    onInvitationSent = {
                        viewModel.fireInvitationSentEvent(InvitationSentEvent.InvitationSource.FIND_FRIENDS)
                    },
                    outgoingInvitationUserIds = viewModel.outgoingInvitationUserIds,
                    isOutgoingInvitationsLoaded = viewModel.isOutgoingInvitationsLoaded,
                    block = block
                )
            }
        }
        
        // Invite Contacts (SMS) component
        "invite-contacts", "vrtx-invite-contacts", "sms-invitations", "vrtx-sms-invitations" -> {
            viewModel.inviteContactsConfig?.let { inviteContactsConfig ->
                val context = LocalContext.current
                InviteContactsView(
                    config = inviteContactsConfig,
                    client = viewModel.vortexClient,
                    widgetId = viewModel.widgetId,
                    groups = viewModel.groupList,
                    shareMessage = viewModel.shareMessage,
                    block = block,
                    onOpenSms = { phoneNumber, message ->
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("smsto:$phoneNumber")
                            putExtra("sms_body", message)
                        }
                        context.startActivity(intent)
                    },
                    onInvitationSent = {
                        viewModel.fireInvitationSentEvent(InvitationSentEvent.InvitationSource.INVITE_CONTACTS)
                    },
                    onNavigateToContacts = {
                        viewModel.trackContactsLinkClicked()
                        viewModel.navigateTo(InviteViewState.INVITE_CONTACTS)
                    }
                )
            }
        }
        
        // Invitation Suggestions component
        "invitation-suggestions", "vrtx-invitation-suggestions" -> {
            viewModel.invitationSuggestionsConfig?.let { suggestionsConfig ->
                InvitationSuggestionsView(
                    config = suggestionsConfig,
                    client = viewModel.vortexClient,
                    widgetId = viewModel.widgetId,
                    groups = viewModel.groupList,
                    unfurlConfig = viewModel.unfurlConfig,
                    onInvitationSent = {
                        viewModel.fireInvitationSentEvent(InvitationSentEvent.InvitationSource.INVITATION_SUGGESTIONS)
                    },
                    outgoingInvitationUserIds = viewModel.outgoingInvitationUserIds,
                    isOutgoingInvitationsLoaded = viewModel.isOutgoingInvitationsLoaded,
                    block = block
                )
            }
        }
        
        // Incoming Invitations component
        "incoming-invitations", "vrtx-incoming-invitations" -> {
            IncomingInvitationsView(
                client = viewModel.vortexClient,
                config = viewModel.incomingInvitationsConfig,
                block = block
            )
        }
        
        // Outgoing Invitations component
        "outgoing-invitations", "vrtx-outgoing-invitations" -> {
            OutgoingInvitationsView(
                client = viewModel.vortexClient,
                config = viewModel.outgoingInvitationsConfig,
                invitationSentEvent = viewModel.invitationSentEvent,
                fetchedOutgoingInvitations = viewModel.fetchedOutgoingInvitations,
                isOutgoingInvitationsLoaded = viewModel.isOutgoingInvitationsLoaded,
                onRefreshOutgoingInvitations = { viewModel.fetchOutgoingInvitations() },
                block = block
            )
        }
        
        // Search Box component
        "search-box", "vrtx-search-box" -> {
            viewModel.searchBoxConfig?.let { searchBoxConfig ->
                SearchBoxView(
                    config = searchBoxConfig,
                    client = viewModel.vortexClient,
                    widgetId = viewModel.widgetId,
                    groups = viewModel.groupList,
                    unfurlConfig = viewModel.unfurlConfig,
                    onInvitationSent = {
                        viewModel.fireInvitationSentEvent(InvitationSentEvent.InvitationSource.SEARCH_BOX)
                    },
                    outgoingInvitationUserIds = viewModel.outgoingInvitationUserIds,
                    block = block
                )
            }
        }
        
        else -> {
            // Render children for unknown block types
            block.children.forEach { child ->
                RenderElement(child, viewModel, formData)
            }
        }
    }
}

@Composable
private fun EmailEntryContentView(viewModel: VortexInviteViewModel) {
    val emailInput by viewModel.emailInput.collectAsState()
    val addedEmails by viewModel.addedEmails.collectAsState()
    
    EmailEntryView(
        emailInput = emailInput,
        addedEmails = addedEmails,
        onEmailInputChange = { viewModel.updateEmailInput(it) },
        onAddEmail = { viewModel.addEmail(emailInput) },
        onRemoveEmail = { viewModel.removeEmail(it) },
        onSendInvitations = { viewModel.sendEmailInvitations() }
    )
}

@Composable
private fun ContactsPickerView(viewModel: VortexInviteViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val searchQuery by viewModel.contactSearchQuery.collectAsState()
    val inviteStates by viewModel.contactInviteStates.collectAsState()
    
    var allContacts by remember { mutableStateOf<List<VortexContact>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasPermission by remember { 
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, 
                android.Manifest.permission.READ_CONTACTS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            allContacts = fetchDeviceContacts(context)
            isLoading = false
        }
    }

    val filteredContacts = remember(allContacts, searchQuery) {
        if (searchQuery.isBlank()) {
            allContacts
        } else {
            allContacts.filter { 
                it.name.contains(searchQuery, ignoreCase = true) || 
                it.email.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { 
                viewModel.updateContactSearchQuery("")
                viewModel.navigateBack() 
            }) {
                VortexIcon(name = VortexIconName.ARROW_BACK, color = VortexColors.Gray33)
            }
            Text(
                text = "Select Contacts",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = VortexColors.Gray33,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        if (!hasPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                VortexIcon(
                    name = VortexIconName.IMPORT_CONTACTS,
                    size = 48,
                    color = VortexColors.GrayE0
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Contact Access Required",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = VortexColors.Gray33
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please grant permission to access your contacts so you can invite them.",
                    fontSize = 14.sp,
                    color = VortexColors.Gray66,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { launcher.launch(android.Manifest.permission.READ_CONTACTS) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Grant Permission")
                }
            }
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VortexColors.Gray66)
            }
        } else {
            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateContactSearchQuery(it) },
                placeholder = { Text("Search contacts", color = VortexColors.Gray66) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VortexColors.Gray66,
                    unfocusedBorderColor = VortexColors.GrayE0
                ),
                shape = RoundedCornerShape(8.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = VortexColors.Gray66
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateContactSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = VortexColors.Gray66
                            )
                        }
                    }
                }
            )

            if (filteredContacts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (searchQuery.isEmpty()) "No Contacts Found" else "No matching contacts",
                        fontSize = 16.sp,
                        color = VortexColors.Gray66
                    )
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredContacts.size) { index ->
                        val contact = filteredContacts[index]
                        ContactPickerRow(
                            contact = contact,
                            inviteState = inviteStates[contact.id],
                            onInvite = {
                                viewModel.inviteContact(contact)
                            }
                        )
                        if (index < filteredContacts.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = VortexColors.GrayF5
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactPickerRow(
    contact: VortexContact,
    inviteState: ContactInviteState?,
    onInvite: () -> Unit
) {
    val isInvited = inviteState?.isInvited == true
    val isLoading = inviteState?.isLoading == true
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar placeholder
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(VortexColors.GrayF5, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.name.take(1).uppercase(),
                fontWeight = FontWeight.Bold,
                color = VortexColors.Gray66
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = VortexColors.Gray33,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = contact.email,
                fontSize = 13.sp,
                color = VortexColors.Gray66,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Invite button, Invited status, or Loading
        when {
            isInvited -> {
                Text(
                    text = "Invited!",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VortexColors.Gray66
                )
            }
            else -> {
                Button(
                    onClick = onInvite,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VortexColors.GrayF5,
                        contentColor = VortexColors.Gray33
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 70.dp, minHeight = 32.dp)
                        .border(1.dp, VortexColors.GrayE0, RoundedCornerShape(6.dp))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = VortexColors.Gray33,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Invite",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun fetchDeviceContacts(context: android.content.Context): List<VortexContact> {
    val contacts = mutableListOf<VortexContact>()
    val contentResolver = context.contentResolver
    val cursor = contentResolver.query(
        android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_URI,
        arrayOf(
            android.provider.ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            android.provider.ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
            android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS
        ),
        null,
        null,
        android.provider.ContactsContract.CommonDataKinds.Email.DISPLAY_NAME + " ASC"
    )

    cursor?.use {
        val nameColumn = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Email.DISPLAY_NAME)
        val emailColumn = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS)
        
        while (it.moveToNext()) {
            val name = it.getString(nameColumn) ?: ""
            val email = it.getString(emailColumn) ?: ""
            if (email.isNotBlank()) {
                contacts.add(VortexContact.create(name, email))
            }
        }
    }
    
    // Group by email to avoid duplicates
    return contacts.distinctBy { it.email.lowercase() }
}

@Composable
private fun GoogleContactsPickerView(viewModel: VortexInviteViewModel) {
    val context = LocalContext.current
    val googleClientId = viewModel.googleClientIdValue
    val isGoogleAuthenticated by viewModel.isGoogleAuthenticated.collectAsState()
    val isGoogleLoading by viewModel.isGoogleLoading.collectAsState()
    val googleContacts by viewModel.googleContacts.collectAsState()
    val contactInviteStates by viewModel.contactInviteStates.collectAsState()
    val googleAuthEmail by viewModel.googleAuthenticatedEmail.collectAsState()
    val workspaceWarning by viewModel.workspaceWarning.collectAsState()

    // Configurable strings (with nested key lookup fallback)
    val block = viewModel.findContactsImportBlock()
    val titleText = viewModel.customLabel(block, "google.title", "Add from Google Contacts")
    val searchPlaceholder = viewModel.customLabel(block, "google.searchPlaceholder", "Search contacts...")
    val loadingText = viewModel.customLabel(block, "google.loadingText", "Loading Google contacts...")
    val emptyStateText = viewModel.customLabel(block, "google.emptyState", "No Google contacts with email addresses found")
    val frequentlyContactedTitle = viewModel.customLabel(block, "google.frequentlyContactedTitle", "★ Frequently contacted")
    val switchAccountLabel = viewModel.customLabel(block, "google.switchAccountLabel", "Switch Google Account")
    val inviteLabel = viewModel.customLabel(block, "google.inviteButton", "Invite")
    val invitedLabel = viewModel.customLabel(block, "google.invitedStatus", "✓ Invited!")
    val retryLabel = viewModel.customLabel(block, "google.retryButton", "Retry")

    // Dynamic scopes
    val scopes = remember(viewModel.isGoogleWorkspaceEnabled, viewModel.isGoogleCalendarEnabled) {
        buildList {
            add(Scope("https://www.googleapis.com/auth/contacts.readonly"))
            add(Scope("https://www.googleapis.com/auth/userinfo.email"))
            if (viewModel.isGoogleWorkspaceEnabled) {
                add(Scope("https://www.googleapis.com/auth/directory.readonly"))
            }
            if (viewModel.isGoogleCalendarEnabled) {
                add(Scope("https://www.googleapis.com/auth/calendar.readonly"))
            }
        }
    }

    val gso = remember(googleClientId, scopes) {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .apply {
                scopes.forEach { requestScopes(it) }
                if (googleClientId != null) requestIdToken(googleClientId)
            }
            .build()
    }

    val googleSignInClient = remember(context, gso) {
        GoogleSignIn.getClient(context, gso)
    }

    // Build dynamic scope string for token request
    fun buildScopeStr(): String = buildString {
        append("oauth2:https://www.googleapis.com/auth/contacts.readonly")
        append(" https://www.googleapis.com/auth/userinfo.email")
        if (viewModel.isGoogleWorkspaceEnabled) append(" https://www.googleapis.com/auth/directory.readonly")
        if (viewModel.isGoogleCalendarEnabled) append(" https://www.googleapis.com/auth/calendar.readonly")
        append(" email profile")
    }

    val coroutineScope = rememberCoroutineScope()

    // Auto-restore credentials on picker open
    LaunchedEffect(Unit) {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (lastAccount != null && !isGoogleAuthenticated) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val token = GoogleAuthUtil.getToken(context, lastAccount.email!!, buildScopeStr())
                    viewModel.onGoogleSignInSuccess(token)
                } catch (_: Exception) {
                    // Token expired, need re-auth — do nothing, show sign-in button
                }
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account.email
            if (email != null) {
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val token = GoogleAuthUtil.getToken(context, email, buildScopeStr())
                        viewModel.onGoogleSignInSuccess(token)
                    } catch (e: Exception) {
                        android.util.Log.e("Vortex", "Failed to get access token", e)
                    }
                }
            }
        } catch (e: ApiException) {
            android.util.Log.e("Vortex", "Google Sign-In failed: ${e.message}")
        }
    }

    if (!isGoogleAuthenticated) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            VortexIcon(
                name = VortexIconName.GOOGLE,
                size = 48,
                color = VortexColors.Gray33
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = titleText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = VortexColors.Gray33
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sign in to import your contacts and invite them to join.",
                fontSize = 14.sp,
                color = VortexColors.Gray66,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { launcher.launch(googleSignInClient.signInIntent) },
                colors = ButtonDefaults.buttonColors(containerColor = VortexColors.Gray33),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign in with Google")
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = { viewModel.navigateBack() }) {
                Text("Cancel", color = VortexColors.Gray66)
            }
        }
    } else {
        var searchQuery by remember { mutableStateOf("") }

        // Use ViewModel's computed filtered/grouped properties
        val frequentlyContacted = viewModel.filteredFrequentlyContacted
        val grouped = viewModel.groupedMainContacts
        val allFiltered = frequentlyContacted + grouped.flatMap { it.second }

        Column(modifier = Modifier.fillMaxSize()) {
            // Title header (matching iOS)
            Text(
                text = titleText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = VortexColors.Gray33,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)
            )

            // Switch account header (above search, matching RN/iOS)
            googleAuthEmail?.let { email ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = email,
                        fontSize = 13.sp,
                        color = VortexColors.Gray66,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { viewModel.switchGoogleAccount(context) },
                        modifier = Modifier.heightIn(min = 48.dp).padding(start = 12.dp)
                    ) {
                        Text(
                            text = switchAccountLabel,
                            fontSize = 13.sp,
                            color = Color(0xFF1A73E8)
                        )
                    }
                }
            }

            // Search Bar
            androidx.compose.material3.TextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.updateContactSearchQuery(it)
                },
                placeholder = { Text(searchPlaceholder, color = VortexColors.Gray66) },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = VortexColors.Gray66,
                        modifier = Modifier.size(20.dp)
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = VortexColors.GrayF5,
                    unfocusedContainerColor = VortexColors.GrayF5,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
            )

            if (isGoogleLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = VortexColors.Gray33)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = loadingText, fontSize = 13.sp, color = VortexColors.Gray66)
                    }
                }
            } else if (allFiltered.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(text = emptyStateText, fontSize = 14.sp, color = VortexColors.Gray66)
                }
            } else {
                // Sectioned list with sections
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f)) {
                    // Workspace warning
                    workspaceWarning?.let { warning ->
                        item {
                            Text(
                                text = warning,
                                color = Color(0xFFB8860B),
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .background(Color(0xFFFFF8E1), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            )
                        }
                    }

                    // Frequently contacted section
                    if (frequentlyContacted.isNotEmpty()) {
                        item { GoogleContactsSectionDivider(text = frequentlyContactedTitle) }
                        items(frequentlyContacted.size) { index ->
                            val contact = frequentlyContacted[index]
                            GoogleContactRow(
                                contact = contact,
                                inviteState = contactInviteStates[contact.id],
                                onInvite = { viewModel.inviteContact(contact) },
                                inviteLabel = inviteLabel,
                                invitedLabel = invitedLabel,
                                retryLabel = retryLabel
                            )
                        }
                    }

                    // Alphabetic sections for main contacts
                    grouped.forEach { (letter, contacts) ->
                        item { GoogleContactsSectionDivider(text = letter) }
                        items(contacts.size) { index ->
                            val contact = contacts[index]
                            GoogleContactRow(
                                contact = contact,
                                inviteState = contactInviteStates[contact.id],
                                onInvite = { viewModel.inviteContact(contact) },
                                inviteLabel = inviteLabel,
                                invitedLabel = invitedLabel,
                                retryLabel = retryLabel
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Section divider matching RN/iOS SDK style (bottom border, not pinned)
 */
@Composable
private fun GoogleContactsSectionDivider(text: String) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .padding(top = 4.dp)
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color(0xFF888888)
            )
        }
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = Color(0xFFE0E0E0)
        )
    }
}

/**
 * Reusable Google contact row with avatar (photo or initials)
 */
@Composable
private fun GoogleContactRow(
    contact: VortexContact,
    inviteState: ContactInviteState?,
    onInvite: () -> Unit,
    inviteLabel: String = "Invite",
    invitedLabel: String = "✓ Invited!",
    retryLabel: String = "Retry"
) {
    ContactRowView(
        contact = contact,
        inviteState = inviteState,
        onInvite = onInvite,
        inviteLabel = inviteLabel,
        invitedLabel = invitedLabel,
        retryLabel = retryLabel
    )
}

@Composable
private fun InviteContactsSecondaryView(viewModel: VortexInviteViewModel) {
    val config = viewModel.inviteContactsConfig ?: return
    val block = viewModel.findInviteContactsBlock()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Theme values from block
    val nameColor = block?.getThemeOption("--vrtx-invite-contacts-name-color")?.let { parseHexColor(it)?.toComposeColor() } ?: VortexColors.Gray33
    val subtitleColor = block?.getThemeOption("--vrtx-invite-contacts-subtitle-color")?.let { parseHexColor(it)?.toComposeColor() } ?: VortexColors.Gray66
    val avatarBackground = block?.getThemeOption("--vrtx-invite-contacts-avatar-background")?.let { parseHexColor(it)?.toComposeColor() } ?: Color(0xFFE8F0FE)
    val avatarTextColor = block?.getThemeOption("--vrtx-invite-contacts-avatar-color")?.let { parseHexColor(it)?.toComposeColor() } ?: Color(0xFF1A73E8)
    val inviteButtonBackgroundStyle = block?.getThemeOption("--vrtx-invite-contacts-invite-button-background")?.let { parseBackgroundStyle(it) }
    val inviteButtonTextColor = block?.getThemeOption("--vrtx-invite-contacts-invite-button-color")?.let { parseHexColor(it)?.toComposeColor() } ?: Color(0xFF1A73E8)
    val inviteButtonBorderRadius = block?.getThemeOption("--vrtx-invite-contacts-invite-button-border-radius")?.let { parseBorderRadius(it) } ?: 8f
    
    val inputColor = block?.getThemeOption("--vrtx-invite-contacts-input-color")?.let { parseHexColor(it)?.toComposeColor() } ?: VortexColors.Gray33
    val inputFontSize = block?.getThemeOption("--vrtx-invite-contacts-input-font-size")?.let { parseFontSize(it) } ?: 16f
    val inputFontFamily = block?.getThemeOption("--vrtx-invite-contacts-input-font-family")?.let { parseFontFamily(it) }
    val inputFontWeight = block?.getThemeOption("--vrtx-invite-contacts-input-font-weight")?.let { parseFontWeight(it) }
    
    val placeholderColor = block?.getThemeOption("--vrtx-invite-contacts-placeholder-color")?.let { parseHexColor(it)?.toComposeColor() } ?: VortexColors.Gray66
    
    // Customization text
    val inviteButtonText = block?.getCustomButtonLabel("inviteButton") ?: "Invite"
    val emptyStateMessage = block?.getCustomButtonLabel("emptyStateMessage") ?: "No contacts to invite"
    val emptySearchState = block?.getCustomButtonLabel("emptySearchState") ?: "No contacts match your search"
    val searchPlaceholderText = block?.getCustomButtonLabel("searchPlaceholder") ?: "Search contacts..."
    
    val contacts = remember { config.contacts.sortedBy { it.name.lowercase() } }
    var searchQuery by remember { mutableStateOf("") }
    var actionInProgress by remember { mutableStateOf<String?>(null) }
    var invitedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    val filteredContacts = if (searchQuery.isBlank()) {
        contacts
    } else {
        val query = searchQuery.lowercase().trim()
        contacts.filter { contact ->
            contact.name.lowercase().contains(query) ||
            contact.phoneNumber.contains(query)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Search box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(searchPlaceholderText, color = placeholderColor) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                color = inputColor,
                fontSize = inputFontSize.sp,
                fontFamily = inputFontFamily,
                fontWeight = inputFontWeight
            )
        )
        
        // Contact rows or empty state
        if (filteredContacts.isEmpty()) {
            Text(
                text = if (searchQuery.isBlank()) emptyStateMessage else emptySearchState,
                fontSize = 14.sp,
                color = VortexColors.Gray66,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
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
                            viewModel.trackContactsInviteButtonClicked()
                            scope.launch {
                                actionInProgress = contact.id
                                viewModel.vortexClient.createSmsInvitation(
                                    widgetId = viewModel.widgetId,
                                    phoneNumber = contact.phoneNumber,
                                    contactName = contact.name,
                                    groups = viewModel.groupList
                                ).onSuccess { shortLink ->
                                    invitedIds = invitedIds + contact.id
                                    config.onInvite?.invoke(contact, shortLink)
                                    viewModel.fireInvitationSentEvent(InvitationSentEvent.InvitationSource.INVITE_CONTACTS)
                                    viewModel.fetchOutgoingInvitations()
                                    shortLink?.let { rawLink ->
                                        val link = VortexInviteViewModel.injectShareSource(rawLink, "sms")
                                        val fullMessage = viewModel.shareMessage.replace("{{link}}", link)
                                            .replace("{{vortex_share_link}}", link)
                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("smsto:${contact.phoneNumber}")
                                            putExtra("sms_body", fullMessage)
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: android.content.ActivityNotFoundException) {
                                            // No SMS app available (e.g. emulator) - invitation was
                                            // already created server-side, so just continue silently.
                                        }
                                    }
                                }.onFailure {
                                    // API call failed - reset loading state
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
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
            Text(
                text = contact.phoneNumber,
                fontSize = 13.sp,
                color = subtitleColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                lineHeight = 13.sp
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Invite button
        val inviteStyle = if (isInvited) {
            BackgroundStyle.Solid(Color.Gray.value.toLong())
        } else {
            inviteButtonBackgroundStyle ?: BackgroundStyle.Solid(avatarBackgroundColor.value.toLong())
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

@Composable
private fun QrCodeView(viewModel: VortexInviteViewModel) {
    val qrCodeLink by viewModel.qrCodeLink.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        qrCodeLink?.let { link ->
            val qrBitmap = remember(link) { generateQrCode(link) }
            qrBitmap?.let { bitmap ->
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier
                        .size(250.dp)
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .padding(20.dp)
                )
            }
        } ?: run {
            CircularProgressIndicator(color = VortexColors.Gray66)
        }
    }
}

/**
 * Generate a QR code bitmap from a string
 */
private fun generateQrCode(content: String, size: Int = 512): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        
        bitmap
    } catch (e: Exception) {
        null
    }
}
