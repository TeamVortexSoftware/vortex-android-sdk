package com.vortexsoftware.android.sdk.ui

import android.graphics.Bitmap
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
    onEvent: ((VortexAnalyticsEvent) -> Unit)? = null
) {
    val viewModel: VortexInviteViewModel = viewModel(
        factory = VortexInviteViewModel.Factory(
            componentId = componentId,
            jwt = jwt,
            apiBaseUrl = apiBaseUrl,
            analyticsBaseUrl = analyticsBaseUrl,
            group = group,
            segmentation = segmentation,
            googleClientId = googleClientId,
            onDismiss = onDismiss,
            onEvent = onEvent,
            enableLogging = enableLogging
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
    val surfaceColor = theme?.surfaceBackgroundColor?.toComposeColor() ?: MaterialTheme.colorScheme.surface
    
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
            // Header with close/back button
            HeaderView(
                currentView = currentView,
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
    onBack: () -> Unit,
    onClose: () -> Unit
) {
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
                color = VortexColors.Gray66
            )
        }
        Spacer(modifier = Modifier.weight(1f))
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
        "h1", "h2", "h3", "h4", "h5", "h6" -> {
            HeadingView(
                block = block
            )
        }
        
        // Text elements
        "text", "paragraph", "span", "p" -> {
            TextView(
                block = block
            )
        }
        
        // Form label
        "label" -> {
            FormLabelView(
                block = block
            )
        }
        
        // Image
        "image" -> {
            ImageView(
                block = block
            )
        }
        
        // Link
        "link" -> {
            LinkView(
                block = block
            )
        }
        
        // Divider
        "divider" -> {
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
        
        // Submit button
        "submit", "vrtx-submit" -> {
            val role = block.vortex?.role
            SubmitButtonView(
                block = block,
                theme = theme,
                isLoading = false,
                onClick = { 
                    if (role == "submit") {
                        viewModel.sendEmailInvitations()
                    }
                }
            )
        }
        
        // Button
        "button", "vrtx-button" -> {
            ButtonView(
                block = block,
                theme = theme,
                onClick = { /* Handle button click */ }
            )
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

    val gso = remember(googleClientId) {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope("https://www.googleapis.com/auth/contacts.readonly"))
            .apply {
                if (googleClientId != null) {
                    requestIdToken(googleClientId)
                }
            }
            .build()
    }

    val googleSignInClient = remember(context, gso) {
        GoogleSignIn.getClient(context, gso)
    }

    val coroutineScope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account.email
            if (email != null) {
                // We must get the access token on a background thread
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val scope = "oauth2:https://www.googleapis.com/auth/contacts.readonly email profile"
                        val token = GoogleAuthUtil.getToken(context, email, scope)
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
                text = "Connect Google Contacts",
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
        Column(modifier = Modifier.fillMaxSize()) {
            // Search Bar
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .background(VortexColors.GrayF5, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = VortexColors.Gray66,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Search Google contacts",
                        color = VortexColors.Gray66,
                        fontSize = 14.sp
                    )
                }
            }

            if (isGoogleLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VortexColors.Gray33)
                }
            } else {
                ContactListView(
                    contacts = googleContacts,
                    inviteStates = contactInviteStates,
                    onInvite = { contact -> viewModel.inviteContact(contact) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QrCodeView(viewModel: VortexInviteViewModel) {
    val shareableLink by viewModel.shareableLink.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Scan to Join",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = VortexColors.Gray33
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        shareableLink?.let { link ->
            val qrBitmap = remember(link) { generateQrCode(link) }
            qrBitmap?.let { bitmap ->
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier
                        .size(200.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                )
            }
        } ?: run {
            CircularProgressIndicator(color = VortexColors.Gray66)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = { viewModel.navigateBack() }) {
            Text("Done")
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
