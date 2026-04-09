package com.vortexsoftware.android.sdk.viewmodels

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.vortexsoftware.android.sdk.analytics.*
import com.vortexsoftware.android.sdk.api.VortexClient
import com.vortexsoftware.android.sdk.api.dto.GroupDTO
import com.vortexsoftware.android.sdk.api.dto.OutgoingInvitation
import com.vortexsoftware.android.sdk.cache.VortexConfigurationCache
import com.vortexsoftware.android.sdk.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID

/**
 * Main ViewModel for the Vortex Invite widget
 * Handles all business logic for the invitation flow
 */
class VortexInviteViewModel(
    private val componentId: String,
    private val jwt: String?,
    private val apiBaseUrl: String,
    private val analyticsBaseUrl: String?,
    private val group: GroupDTO?,
    private val segmentation: Map<String, Any>?,
    private val googleClientId: String?,
    private val onDismiss: (() -> Unit)?,
    private val onEvent: ((VortexAnalyticsEvent) -> Unit)?,
    private val enableLogging: Boolean = false,
    private val initialConfiguration: WidgetConfiguration? = null,
    private val initialDeploymentId: String? = null,
    private val locale: String? = null,
    val findFriendsConfig: FindFriendsConfig? = null,
    val inviteContactsConfig: InviteContactsConfig? = null,
    val invitationSuggestionsConfig: InvitationSuggestionsConfig? = null,
    val incomingInvitationsConfig: IncomingInvitationsConfig? = null,
    val outgoingInvitationsConfig: OutgoingInvitationsConfig? = null,
    val searchBoxConfig: SearchBoxConfig? = null,
    val unfurlConfig: UnfurlConfig? = null,
    private val templateVariables: Map<String, String>? = null
) : ViewModel() {
    
    val googleClientIdValue: String? get() = googleClientId
    val localeValue: String? get() = locale
    val templateVariablesValue: Map<String, String>? get() = templateVariables
    
    // Expose widget configuration ID and group for use by child components
    // Use configuration ID when available, fallback to componentId
    val widgetId: String get() = _configuration.value?.id ?: componentId
    val groupList: List<GroupDTO>? get() = group?.let { listOf(it) }
    
    // Analytics
    private val sessionId: String = UUID.randomUUID().toString()
    private var deploymentId: String? = null
    private var widgetRenderTracked = false
    private var formRenderTime: Long? = null
    
    private val analyticsClient: VortexAnalyticsClient by lazy {
        VortexAnalyticsClient(
            baseURL = analyticsBaseUrl ?: VortexAnalyticsClient.DEFAULT_ANALYTICS_URL,
            sessionId = sessionId,
            jwt = jwt
        )
    }
    
    // Expose client for use by child components
    val vortexClient = VortexClient(
        baseUrl = apiBaseUrl,
        jwt = jwt,
        enableLogging = enableLogging
    )
    
    // Keep private reference for internal use
    private val client get() = vortexClient
    
    // Configuration state - initialize with cached config if available (synchronous check)
    private val _configuration = MutableStateFlow<WidgetConfiguration?>(
        initialConfiguration ?: VortexConfigurationCache.get(componentId)?.configuration
    )
    val configuration: StateFlow<WidgetConfiguration?> = _configuration.asStateFlow()
    
    // Loading state - starts true only if no cached config available
    private val _isLoading = MutableStateFlow(
        initialConfiguration == null && VortexConfigurationCache.get(componentId) == null
    )
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Current view state
    private val _currentView = MutableStateFlow(InviteViewState.MAIN)
    val currentView: StateFlow<InviteViewState> = _currentView.asStateFlow()
    
    // Form data
    private val _formData = MutableStateFlow<MutableMap<String, String>>(mutableMapOf())
    val formData: StateFlow<Map<String, String>> = _formData.asStateFlow()
    
    // Email entry
    private val _emailInput = MutableStateFlow("")
    val emailInput: StateFlow<String> = _emailInput.asStateFlow()
    
    private val _addedEmails = MutableStateFlow<List<String>>(emptyList())
    val addedEmails: StateFlow<List<String>> = _addedEmails.asStateFlow()
    
    // Contacts
    private val _selectedContacts = MutableStateFlow<List<VortexContact>>(emptyList())
    val selectedContacts: StateFlow<List<VortexContact>> = _selectedContacts.asStateFlow()
    
    private val _contactInviteStates = MutableStateFlow<Map<String, ContactInviteState>>(emptyMap())
    val contactInviteStates: StateFlow<Map<String, ContactInviteState>> = _contactInviteStates.asStateFlow()
    
    private val _contactSearchQuery = MutableStateFlow("")
    val contactSearchQuery: StateFlow<String> = _contactSearchQuery.asStateFlow()
    
    // Invitation sent event - observed by OutgoingInvitationsView to trigger refresh
    private val _invitationSentEvent = MutableStateFlow<InvitationSentEvent?>(null)
    val invitationSentEvent: StateFlow<InvitationSentEvent?> = _invitationSentEvent.asStateFlow()
    
    // Centralized outgoing invitations state
    private val _fetchedOutgoingInvitations = MutableStateFlow<List<OutgoingInvitation>>(emptyList())
    val fetchedOutgoingInvitations: StateFlow<List<OutgoingInvitation>> = _fetchedOutgoingInvitations.asStateFlow()
    
    private val _outgoingInvitationUserIds = MutableStateFlow<Set<String>>(emptySet())
    val outgoingInvitationUserIds: StateFlow<Set<String>> = _outgoingInvitationUserIds.asStateFlow()
    
    private val _isOutgoingInvitationsLoaded = MutableStateFlow(false)
    val isOutgoingInvitationsLoaded: StateFlow<Boolean> = _isOutgoingInvitationsLoaded.asStateFlow()
    
    /**
     * Fire an invitation sent event that other subcomponents can observe.
     * Call this when an invitation is successfully sent from any subcomponent.
     */
    fun fireInvitationSentEvent(source: InvitationSentEvent.InvitationSource, shortLink: String = "") {
        _invitationSentEvent.value = InvitationSentEvent(source = source, shortLink = shortLink)
    }
    
    // Share states
    private val _shareableLink = MutableStateFlow<String?>(null)
    val shareableLink: StateFlow<String?> = _shareableLink.asStateFlow()

    private val _qrCodeLink = MutableStateFlow<String?>(null)
    val qrCodeLink: StateFlow<String?> = _qrCodeLink.asStateFlow()
    
    private val _loadingCopy = MutableStateFlow(false)
    val loadingCopy: StateFlow<Boolean> = _loadingCopy.asStateFlow()
    
    private val _copySuccess = MutableStateFlow(false)
    val copySuccess: StateFlow<Boolean> = _copySuccess.asStateFlow()
    
    private val _loadingShare = MutableStateFlow(false)
    val loadingShare: StateFlow<Boolean> = _loadingShare.asStateFlow()
    
    private val _shareSuccess = MutableStateFlow(false)
    val shareSuccess: StateFlow<Boolean> = _shareSuccess.asStateFlow()

    // Form title from widget configuration (matching iOS SDK's configFormTitle)
    val configFormTitle: String?
        get() {
            val rootElement = _configuration.value?.elements?.firstOrNull() ?: return null
            return rootElement.getCustomButtonLabel("mobile.formTitle")
                ?: rootElement.getCustomButtonLabel("formTitle")
        }
    
    val formTitleColor: Long?
        get() {
            val rootElement = _configuration.value?.elements?.firstOrNull() ?: return null
            val hex = rootElement.getThemeOption("--vrtx-form-title-color") ?: return null
            return parseHexColor(hex)
        }
    
    val formTitleFontSize: Float?
        get() {
            val rootElement = _configuration.value?.elements?.firstOrNull() ?: return null
            val str = rootElement.getThemeOption("--vrtx-form-title-font-size") ?: return null
            return str.replace("px", "").toFloatOrNull()
        }
    
    val formTitleFontWeight: androidx.compose.ui.text.font.FontWeight?
        get() {
            val rootElement = _configuration.value?.elements?.firstOrNull() ?: return null
            val str = rootElement.getThemeOption("--vrtx-form-title-font-weight") ?: return null
            return when (str.lowercase()) {
                "bold", "700" -> androidx.compose.ui.text.font.FontWeight.Bold
                "600", "semibold" -> androidx.compose.ui.text.font.FontWeight.SemiBold
                "500", "medium" -> androidx.compose.ui.text.font.FontWeight.Medium
                "400", "normal", "regular" -> androidx.compose.ui.text.font.FontWeight.Normal
                "300", "light" -> androidx.compose.ui.text.font.FontWeight.Light
                else -> null
            }
        }

    // Share Message configuration
    val shareTitle: String
        get() {
            val config = _configuration.value ?: return "You're Invited!"
            return config.props["vortex.components.share.template.subject"]?.value?.let { (it as? JsonPrimitive)?.content }?.takeIf { it.isNotBlank() }
                ?: config.props["vortex.components.share.title"]?.value?.let { (it as? JsonPrimitive)?.content }
                ?: findShareOptionsBlock()?.getString("shareTitle")
                ?: "You're Invited!"
        }

    /**
     * Share message template from configuration.
     * May contain {{vortex_share_link}} placeholder for the link.
     */
    val shareMessageTemplate: String
        get() {
            val config = _configuration.value ?: return "{{vortex_share_link}}"
            return config.props["vortex.components.share.template.body"]?.value?.let { (it as? JsonPrimitive)?.content }?.takeIf { it.isNotBlank() }
                ?: config.props["vortex.components.share.message"]?.value?.let { (it as? JsonPrimitive)?.content }
                ?: findShareOptionsBlock()?.getString("shareMessage")
                ?: "{{vortex_share_link}}"
        }

    val shareMessage: String
        get() = shareMessageTemplate

    /**
     * Build the full share text by replacing the {{vortex_share_link}} placeholder with the actual link.
     * If no placeholder is found, append the link to the template.
     */
    private fun buildShareText(link: String): String {
        val template = shareMessageTemplate
        return if (template.contains("{{vortex_share_link}}")) {
            template.replace("{{vortex_share_link}}", link)
        } else {
            if (template.endsWith(" ")) "$template$link" else "$template $link"
        }
    }

    // Google Contacts state
    private val _googleContacts = MutableStateFlow<List<VortexContact>>(emptyList())
    val googleContacts: StateFlow<List<VortexContact>> = _googleContacts.asStateFlow()

    private val _isGoogleLoading = MutableStateFlow(false)
    val isGoogleLoading: StateFlow<Boolean> = _isGoogleLoading.asStateFlow()

    private val _isGoogleAuthenticated = MutableStateFlow(false)
    val isGoogleAuthenticated: StateFlow<Boolean> = _isGoogleAuthenticated.asStateFlow()

    private val _googleAuthenticatedEmail = MutableStateFlow<String?>(null)
    val googleAuthenticatedEmail: StateFlow<String?> = _googleAuthenticatedEmail.asStateFlow()

    private val _workspaceWarning = MutableStateFlow<String?>(null)
    val workspaceWarning: StateFlow<String?> = _workspaceWarning.asStateFlow()
    
    // Feature flags derived from configuration
    val shareOptions: List<String>
        get() {
            val config = _configuration.value ?: return emptyList()
            
            // Try to get from vortex.components.share.options prop
            val shareOptionsProp = config.props["vortex.components.share.options"]?.value
            if (shareOptionsProp is JsonArray) {
                return shareOptionsProp.mapNotNull { (it as? JsonPrimitive)?.content }
            }
            
            // Fallback to searching the block tree
            return findShareOptionsBlock()?.children
                ?.filter { it.type == "block" && it.subtype == "share-button" }
                ?.mapNotNull { it.getString("shareType") }
                ?: emptyList()
        }
    
    val isCopyLinkEnabled: Boolean
        get() = shareOptions.contains("copyLink")
    
    val isSmsEnabled: Boolean
        get() = shareOptions.contains("sms")
    
    val isQrCodeEnabled: Boolean
        get() = shareOptions.contains("qrCode")
    
    val isNativeContactsEnabled: Boolean
        get() {
            val config = _configuration.value ?: return false
            
            // Check in general components list
            val components = config.props["vortex.components"]?.value
            if (components is JsonArray) {
                val componentList = components.mapNotNull { (it as? JsonPrimitive)?.content }
                if (componentList.contains("vortex.components.importcontacts.providers.importcontacts") ||
                    componentList.contains("vortex.components.importcontacts.providers.native") ||
                    componentList.contains("importcontacts")) {
                    return true
                }
            }
            
            // Check in vortex.components.importcontacts.providers prop
            val providersProp = config.props["vortex.components.importcontacts.providers"]?.value
            if (providersProp is JsonArray) {
                val providerList = providersProp.mapNotNull { (it as? JsonPrimitive)?.content }
                if (providerList.contains("native") || providerList.contains("importcontacts")) return true
            }
            
            // Fallback to searching the block tree
            return findContactsImportBlock()?.children
                ?.any { it.getString("importType") == "native" || it.subtype == "vrtx-contacts-import" } == true
        }
    
    val isGoogleContactsEnabled: Boolean
        get() {
            // Check if googleClientId is provided
            if (googleClientId.isNullOrBlank()) {
                if (enableLogging) android.util.Log.d("Vortex", "isGoogleContactsEnabled: googleClientId is null or blank")
                return false
            }
            
            val config = _configuration.value ?: return false
            
            // Check in general components list
            val components = config.props["vortex.components"]?.value
            if (components is JsonArray) {
                val componentList = components.mapNotNull { (it as? JsonPrimitive)?.content }
                if (componentList.contains("vortex.components.importcontacts.providers.google") ||
                    componentList.contains("google")) {
                    return true
                }
            }

            // Check in vortex.components.importcontacts.providers prop
            val providersProp = config.props["vortex.components.importcontacts.providers"]?.value
            if (providersProp is JsonArray) {
                val providerList = providersProp.mapNotNull { (it as? JsonPrimitive)?.content }
                if (providerList.contains("google")) return true
            }
            
            // Fallback to searching the block tree
            return findContactsImportBlock()?.children
                ?.any { it.getString("importType") == "google" } == true
        }

    val isGoogleWorkspaceEnabled: Boolean
        get() {
            val config = _configuration.value ?: return false
            val components = config.props["vortex.components"]?.value
            if (components is JsonArray) {
                val componentList = components.mapNotNull { (it as? JsonPrimitive)?.content }
                return componentList.contains("vortex.components.importcontacts.providers.google.workspace.directory")
            }
            return false
        }

    val isGoogleCalendarEnabled: Boolean
        get() {
            val config = _configuration.value ?: return false
            val components = config.props["vortex.components"]?.value
            if (components is JsonArray) {
                val componentList = components.mapNotNull { (it as? JsonPrimitive)?.content }
                return componentList.contains("vortex.components.importcontacts.providers.google.calendar.guests")
            }
            return false
        }
    
    // Guards against concurrent and rapid-fire configuration fetches.
    private var isLoadingConfiguration = false
    private var lastFetchTime: Long = 0L
    private val minFetchIntervalMs: Long = 30_000

    /**
     * Load widget configuration with stale-while-revalidate pattern.
     * If cached/prefetched configuration exists, it's used immediately (no loading spinner).
     * Fresh configuration is always fetched in the background to ensure up-to-date data.
     */
    fun loadConfiguration() {
        if (isLoadingConfiguration) return
        isLoadingConfiguration = true
        viewModelScope.launch {
            _error.value = null
            
            // Early cache check: populate outgoing invitations from cache if available
            if (jwt != null) {
                VortexConfigurationCache.getOutgoingInvitations(jwt)?.let { cached ->
                    _fetchedOutgoingInvitations.value = cached
                    updateOutgoingInvitationUserIds()
                    _isOutgoingInvitationsLoaded.value = true
                    if (enableLogging) {
                        android.util.Log.d("VortexInviteViewModel", "Using cached outgoing invitations")
                    }
                }
            }
            
            // Step 1: Check for initial configuration (passed via init) or cached configuration
            var hasCachedConfig = false
            
            if (initialConfiguration != null) {
                // Use configuration passed via init (from prefetcher or parent view)
                _configuration.value = initialConfiguration
                deploymentId = initialDeploymentId
                hasCachedConfig = true
                if (enableLogging) {
                    android.util.Log.d("VortexInviteViewModel", "Using initial configuration")
                }
            } else {
                // Check shared cache
                VortexConfigurationCache.get(componentId)?.let { cached ->
                    _configuration.value = cached.configuration
                    deploymentId = cached.deploymentId
                    hasCachedConfig = true
                    if (enableLogging) {
                        android.util.Log.d("VortexInviteViewModel", "Using cached configuration")
                    }
                }
            }
            
            // Step 2: Only show loading if we don't have any configuration yet
            if (!hasCachedConfig) {
                _isLoading.value = true
            }
            
            // Step 3: Fetch fresh configuration (stale-while-revalidate) unless recently fetched
            val now = System.currentTimeMillis()
            if (lastFetchTime > 0 && (now - lastFetchTime) < minFetchIntervalMs && hasCachedConfig) {
                _isLoading.value = false
                isLoadingConfiguration = false
                fetchOutgoingInvitations()
                return@launch
            }

            client.getWidgetConfiguration(componentId, locale, templateVariables)
                .onSuccess { response ->
                    val freshConfig = WidgetConfiguration.fromDTO(response.data)
                    _configuration.value = freshConfig
                    // Extract deploymentId from API response (CRITICAL for analytics)
                    deploymentId = response.data.deploymentId
                    
                    // Update shared cache for future use
                    VortexConfigurationCache.set(
                        componentId = componentId,
                        configuration = freshConfig,
                        deploymentId = response.data.deploymentId
                    )
                    
                    lastFetchTime = System.currentTimeMillis()
                    _isLoading.value = false
                    isLoadingConfiguration = false
                    // Track widget render on successful load
                    trackWidgetRender()
                    
                    // Fetch outgoing invitations in background (SWR revalidation)
                    fetchOutgoingInvitations()
                    
                    if (enableLogging) {
                        android.util.Log.d("VortexInviteViewModel", "Fresh configuration loaded and cached")
                    }
                }
                .onFailure { e ->
                    android.util.Log.e("VortexInviteViewModel", "Failed to load configuration: ${e.message}")
                    // Only set error if we don't have cached config to show
                    if (!hasCachedConfig) {
                        val errorMessage = e.message ?: "Failed to load configuration"
                        _error.value = errorMessage
                        // Track widget error
                        trackWidgetError(errorMessage)
                    }
                    _isLoading.value = false
                    isLoadingConfiguration = false
                }
        }
    }
    
    /**
     * Fetch outgoing invitations from API and update the centralized cache.
     * Called after config loads and when invitations are sent (SWR pattern).
     */
    fun fetchOutgoingInvitations() {
        viewModelScope.launch {
            client.getOutgoingInvitations()
                .onSuccess { fetched ->
                    _fetchedOutgoingInvitations.value = fetched
                    updateOutgoingInvitationUserIds()
                    _isOutgoingInvitationsLoaded.value = true
                    // Update cache
                    jwt?.let { VortexConfigurationCache.setOutgoingInvitations(it, fetched) }
                    if (enableLogging) {
                        android.util.Log.d("VortexInviteViewModel", "Fetched ${fetched.size} outgoing invitations")
                    }
                }
                .onFailure { e ->
                    // Still mark as loaded even on failure so shimmer goes away
                    _isOutgoingInvitationsLoaded.value = true
                    if (enableLogging) {
                        android.util.Log.e("VortexInviteViewModel", "Failed to fetch outgoing invitations: ${e.message}")
                    }
                }
        }
    }
    
    /**
     * Update the set of outgoing invitation user IDs from both internal and API-fetched invitations.
     */
    private fun updateOutgoingInvitationUserIds() {
        val ids = mutableSetOf<String>()
        // From internal (app-provided) outgoing invitations
        outgoingInvitationsConfig?.internalInvitations?.forEach { item ->
            ids.add(item.id)
        }
        // From API-fetched outgoing invitations
        _fetchedOutgoingInvitations.value.forEach { invitation ->
            invitation.targets?.firstOrNull()?.targetValue?.let { ids.add(it) }
        }
        _outgoingInvitationUserIds.value = ids
    }
    
    /**
     * Dismiss the widget
     */
    fun dismiss() {
        onDismiss?.invoke()
    }
    
    /**
     * Navigate to a specific view
     */
    fun navigateTo(view: InviteViewState) {
        _currentView.value = view
    }
    
    /**
     * Navigate back to main view
     */
    fun navigateBack() {
        _currentView.value = InviteViewState.MAIN
    }
    
    // MARK: - Form handling
    
    /**
     * Update a form field value
     */
    fun updateFormField(key: String, value: String) {
        _formData.value = _formData.value.toMutableMap().apply {
            put(key, value)
        }
    }
    
    /**
     * Get current form data as immutable map
     */
    fun getFormData(): Map<String, String> = _formData.value.toMap()
    
    // MARK: - Email handling
    
    /**
     * Update email input field
     */
    fun updateEmailInput(email: String) {
        _emailInput.value = email
    }
    
    /**
     * Add an email to the list
     */
    fun addEmail(email: String) {
        val trimmed = email.trim()
        if (trimmed.isNotEmpty() && isValidEmail(trimmed) && !_addedEmails.value.contains(trimmed)) {
            _addedEmails.value = _addedEmails.value + trimmed
            _emailInput.value = ""
        }
    }
    
    /**
     * Remove an email from the list
     */
    fun removeEmail(email: String) {
        _addedEmails.value = _addedEmails.value.filter { it != email }
    }
    
    /**
     * Validate email format
     */
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    
    // MARK: - Contact handling
    
    /**
     * Navigate to contacts picker
     */
    fun selectFromContacts() {
        _currentView.value = InviteViewState.CONTACTS_PICKER
    }
    
    /**
     * Navigate to Google contacts picker
     */
    fun selectFromGoogleContacts() {
        _currentView.value = InviteViewState.GOOGLE_CONTACTS_PICKER
    }
    
    /**
     * Update contact search query
     */
    fun updateContactSearchQuery(query: String) {
        _contactSearchQuery.value = query
    }

    /**
     * Add contacts to the selected list
     */
    fun addContacts(contacts: List<VortexContact>) {
        val currentContacts = _selectedContacts.value.toMutableList()
        contacts.forEach { contact ->
            if (!currentContacts.any { it.email.equals(contact.email, ignoreCase = true) }) {
                currentContacts.add(contact)
                _contactInviteStates.value = _contactInviteStates.value + (contact.id to ContactInviteState(contact))
            }
        }
        _selectedContacts.value = currentContacts
        _currentView.value = InviteViewState.MAIN
    }
    
    /**
     * Invite a specific contact
     */
    fun inviteContact(contact: VortexContact) {
        viewModelScope.launch {
            val widgetConfigId = _configuration.value?.id ?: componentId

            // Don't invite if already invited or loading
            val currentState = _contactInviteStates.value[contact.id]
            if (currentState?.isInvited == true || currentState?.isLoading == true) return@launch

            // Update loading state
            _contactInviteStates.value = _contactInviteStates.value + (contact.id to ContactInviteState(
                contact = contact,
                isLoading = true
            ))
            
            client.createInvitation(
                widgetId = widgetConfigId,
                inviteeEmail = contact.email,
                groups = group?.let { listOf(it) },
                formData = getFormData().takeIf { it.isNotEmpty() },
                templateVariables = templateVariables,
                metadata = unfurlConfig?.toMetadata(),
                locale = locale
            ).onSuccess {
                fireInvitationSentEvent(InvitationSentEvent.InvitationSource.INVITE_CONTACTS)
                _contactInviteStates.value = _contactInviteStates.value + (contact.id to ContactInviteState(
                    contact = contact,
                    isInvited = true
                ))
            }.onFailure { e ->
                _contactInviteStates.value = _contactInviteStates.value + (contact.id to ContactInviteState(
                    contact = contact,
                    errorMessage = e.message ?: "Failed to send invitation"
                ))
            }
        }
    }

    // MARK: - Google Contacts handling

    /**
     * Handle successful Google Sign-In
     */
    fun onGoogleSignInSuccess(accessToken: String) {
        _isGoogleAuthenticated.value = true
        fetchGoogleContacts(accessToken)
    }

    /**
     * Switch Google account - signs out and re-triggers sign-in flow
     */
    fun switchGoogleAccount(context: Context) {
        viewModelScope.launch {
            try {
                GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
            } catch (_: Exception) {}
            _isGoogleAuthenticated.value = false
            _googleContacts.value = emptyList()
            _googleAuthenticatedEmail.value = null
            _workspaceWarning.value = null
            // Re-trigger sign-in flow (UI will show sign-in button again)
        }
    }

    /**
     * Fetch contacts from all enabled Google sources in parallel
     */
    private fun fetchGoogleContacts(accessToken: String) {
        viewModelScope.launch {
            _isGoogleLoading.value = true
            _error.value = null
            _workspaceWarning.value = null

            try {
                // 1. Fetch user email for self-exclusion
                val userEmail = fetchUserEmail(accessToken)
                _googleAuthenticatedEmail.value = userEmail

                // 2. Fetch all enabled sources in parallel
                val personalDeferred = async { fetchPersonalContacts(accessToken) }
                val workspaceDeferred = if (isGoogleWorkspaceEnabled) async { fetchWorkspaceDirectory(accessToken) } else null
                val calendarDeferred = if (isGoogleCalendarEnabled) async { fetchCalendarGuests(accessToken) } else null

                val personal = personalDeferred.await()
                val workspace = workspaceDeferred?.await() ?: emptyList()
                val calendar = calendarDeferred?.await() ?: emptyList()

                // 3. Handle workspace warning
                if (isGoogleWorkspaceEnabled && workspace.isEmpty()) {
                    _workspaceWarning.value = "Results below do not include your Google Workspace directory. Check with your admin to make sure Contact Sharing is enabled."
                }

                // 4. Merge, dedup, exclude self
                val allContacts = mergeAndDedup(personal, workspace, calendar, userEmail)
                _googleContacts.value = allContacts
            } catch (e: Exception) {
                _error.value = "Failed to fetch Google contacts: ${e.message}"
                if (enableLogging) android.util.Log.e("Vortex", "Error fetching contacts", e)
            } finally {
                _isGoogleLoading.value = false
            }
        }
    }

    private suspend fun fetchUserEmail(accessToken: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://www.googleapis.com/oauth2/v3/userinfo")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val json = Json { ignoreUnknownKeys = true }
                    val data = json.parseToJsonElement(body).jsonObject
                    data["email"]?.jsonPrimitive?.content
                } else null
            } catch (_: Exception) { null }
        }
    }

    private suspend fun fetchPersonalContacts(accessToken: String): List<VortexContact> {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://people.googleapis.com/v1/people/me/connections?personFields=names,emailAddresses,photos&pageSize=1000")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext emptyList()

                val body = response.body?.string() ?: return@withContext emptyList()
                val json = Json { ignoreUnknownKeys = true }
                val data = json.parseToJsonElement(body).jsonObject
                val contacts = mutableListOf<VortexContact>()
                val connections = data["connections"]?.jsonArray ?: return@withContext emptyList()

                for (person in connections) {
                    val personObj = person.jsonObject
                    val emailAddresses = personObj["emailAddresses"]?.jsonArray ?: continue
                    val resourceName = personObj["resourceName"]?.jsonPrimitive?.contentOrNull ?: java.util.UUID.randomUUID().toString()
                    val displayName = personObj["names"]?.jsonArray?.getOrNull(0)?.jsonObject?.get("displayName")?.jsonPrimitive?.contentOrNull
                    // Extract photo URL (skip default/placeholder photos)
                    val photos = personObj["photos"]?.jsonArray
                    val photoUrl = photos?.getOrNull(0)?.jsonObject?.let { photo ->
                        val url = photo["url"]?.jsonPrimitive?.contentOrNull
                        val isDefault = photo["default"]?.jsonPrimitive?.booleanOrNull ?: false
                        if (url != null && !isDefault) url else null
                    }
                    for (emailObj in emailAddresses) {
                        val email = emailObj.jsonObject["value"]?.jsonPrimitive?.content ?: continue
                        val key = "$resourceName-$email"
                        val name = displayName ?: inferNameFromEmail(email)
                        contacts.add(VortexContact(id = key, name = name, email = email, source = ContactSource.CONTACTS, imageUrl = photoUrl))
                    }
                }
                contacts.distinctBy { it.email.lowercase() }
            } catch (_: Exception) { emptyList() }
        }
    }

    /**
     * Fetch workspace directory contacts.
     * Tries DOMAIN_PROFILE → DOMAIN_CONTACT → searchDirectoryPeople (matching RN/iOS SDK)
     */
    private suspend fun fetchWorkspaceDirectory(accessToken: String): List<VortexContact> {
        return withContext(Dispatchers.IO) {
            val sources = listOf(
                "https://people.googleapis.com/v1/people:listDirectoryPeople?sources=DIRECTORY_SOURCE_TYPE_DOMAIN_PROFILE&readMask=names,emailAddresses,photos&pageSize=1000",
                "https://people.googleapis.com/v1/people:listDirectoryPeople?sources=DIRECTORY_SOURCE_TYPE_DOMAIN_CONTACT&readMask=names,emailAddresses,photos&pageSize=1000",
                "https://people.googleapis.com/v1/people:searchDirectoryPeople?sources=DIRECTORY_SOURCE_TYPE_DOMAIN_PROFILE&readMask=names,emailAddresses,photos&pageSize=500&query="
            )
            for (urlString in sources) {
                try {
                    val httpClient = OkHttpClient()
                    val request = Request.Builder()
                        .url(urlString)
                        .addHeader("Authorization", "Bearer $accessToken")
                        .build()
                    val response = httpClient.newCall(request).execute()
                    if (!response.isSuccessful) continue

                    val body = response.body?.string() ?: continue
                    val json = Json { ignoreUnknownKeys = true }
                    val data = json.parseToJsonElement(body).jsonObject
                    val people = data["people"]?.jsonArray ?: continue
                    if (people.isEmpty()) continue

                    val contacts = mutableListOf<VortexContact>()
                    for (person in people) {
                        val personObj = person.jsonObject
                        val emailAddresses = personObj["emailAddresses"]?.jsonArray ?: continue
                        val resourceName = personObj["resourceName"]?.jsonPrimitive?.contentOrNull ?: java.util.UUID.randomUUID().toString()
                        val displayName = personObj["names"]?.jsonArray?.getOrNull(0)?.jsonObject?.get("displayName")?.jsonPrimitive?.contentOrNull
                        // Extract photo URL (skip default/placeholder photos)
                        val photos = personObj["photos"]?.jsonArray
                        val imageUrl = photos?.getOrNull(0)?.jsonObject?.let { photo ->
                            val url = photo["url"]?.jsonPrimitive?.contentOrNull
                            val isDefault = photo["default"]?.jsonPrimitive?.booleanOrNull ?: false
                            if (url != null && !isDefault) url else null
                        }
                        for (emailObj in emailAddresses) {
                            val email = emailObj.jsonObject["value"]?.jsonPrimitive?.content ?: continue
                            val key = "ws-$resourceName-$email"
                            val name = displayName ?: inferNameFromEmail(email)
                            contacts.add(VortexContact(id = key, name = name, email = email, source = ContactSource.WORKSPACE, imageUrl = imageUrl))
                        }
                    }
                    return@withContext contacts
                } catch (_: Exception) { continue }
            }
            emptyList()
        }
    }

    private suspend fun fetchCalendarGuests(accessToken: String): List<VortexContact> {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val now = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                val timeMax = dateFormat.format(now.time)
                now.add(java.util.Calendar.DAY_OF_YEAR, -30)
                val timeMin = dateFormat.format(now.time)
                val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events?timeMin=$timeMin&timeMax=$timeMax&maxResults=500&singleEvents=true&orderBy=startTime"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext emptyList()

                val body = response.body?.string() ?: return@withContext emptyList()
                val json = Json { ignoreUnknownKeys = true }
                val data = json.parseToJsonElement(body).jsonObject
                val items = data["items"]?.jsonArray ?: return@withContext emptyList()

                // Count frequency per attendee email
                val userEmailLower = _googleAuthenticatedEmail.value?.lowercase() ?: ""
                val attendeeCounts = mutableMapOf<String, Pair<String, Int>>() // email -> (name, count)
                for (event in items) {
                    val attendees = event.jsonObject["attendees"]?.jsonArray ?: continue
                    for (attendee in attendees) {
                        val obj = attendee.jsonObject
                        val email = obj["email"]?.jsonPrimitive?.content ?: continue
                        val emailLower = email.lowercase().trim()
                        if (emailLower.isBlank()) continue
                        // Skip self, resource calendars, and group calendars (matching RN/iOS)
                        if (emailLower == userEmailLower) continue
                        if (emailLower.contains("resource.calendar.google.com")) continue
                        if (emailLower.startsWith("group.calendar.google.com")) continue
                        val displayName = obj["displayName"]?.jsonPrimitive?.contentOrNull
                        val name = if (!displayName.isNullOrBlank() && displayName.lowercase() != emailLower) {
                            displayName
                        } else {
                            inferNameFromEmail(email)
                        }
                        val existing = attendeeCounts[emailLower]
                        attendeeCounts[emailLower] = Pair(existing?.first ?: name, (existing?.second ?: 0) + 1)
                    }
                }

                val attendeesWithCount = attendeeCounts.map { (email, pair) ->
                    Pair(VortexContact(id = "cal-$email", name = pair.first, email = email, source = ContactSource.CALENDAR), pair.second)
                }

                selectFrequentlyContacted(attendeesWithCount)
            } catch (_: Exception) { emptyList() }
        }
    }

    private fun selectFrequentlyContacted(
        attendees: List<Pair<VortexContact, Int>>
    ): List<VortexContact> {
        if (attendees.isEmpty()) return emptyList()

        // 1. Require minimum 2 shared events
        val qualified = attendees.filter { it.second >= 2 }
        if (qualified.isEmpty()) return emptyList()

        // 2. Sort by frequency descending
        val sorted = qualified.sortedByDescending { it.second }

        // 3. Top 20%, capped at 10, floor of 3
        val twentyPercent = kotlin.math.ceil(sorted.size * 0.2).toInt()
        val limit = minOf(maxOf(twentyPercent, 3), 10)

        // 4. Take top N, then sort alphabetically
        return sorted.take(limit)
            .map { it.first }
            .sortedBy { it.name.lowercase() }
    }

    private fun mergeAndDedup(
        personal: List<VortexContact>,
        workspace: List<VortexContact>,
        calendar: List<VortexContact>,
        userEmail: String?
    ): List<VortexContact> {
        val calendarEmails = calendar.map { it.email.lowercase() }.toSet()

        val mainMap = mutableMapOf<String, VortexContact>()
        (personal + workspace).forEach { contact ->
            val key = contact.email.lowercase()
            if (key != userEmail?.lowercase() && key !in calendarEmails) {
                mainMap.putIfAbsent(key, contact)
            }
        }
        val mainContacts = mainMap.values.sortedBy { it.name.lowercase() }

        val calendarContacts = calendar.filter {
            it.email.lowercase() != userEmail?.lowercase()
        }

        return calendarContacts + mainContacts
    }
    
    /**
     * Send invitations to all added emails
     */
    fun sendEmailInvitations() {
        viewModelScope.launch {
            val widgetConfigId = _configuration.value?.id ?: componentId

            val emailsToSend = if (_addedEmails.value.isNotEmpty()) {
                _addedEmails.value
            } else if (_emailInput.value.isNotBlank()) {
                // If it's a single input field with multiple emails (comma/space separated)
                _emailInput.value.split(Regex("[,\\s]+")).filter { it.isNotBlank() && isValidEmail(it) }
            } else {
                emptyList()
            }

            if (emailsToSend.isNotEmpty()) {
                client.createInvitations(
                    widgetId = widgetConfigId,
                    inviteeEmails = emailsToSend,
                    groups = group?.let { listOf(it) },
                    formData = getFormData().takeIf { it.isNotEmpty() },
                    templateVariables = templateVariables,
                    metadata = unfurlConfig?.toMetadata(),
                    locale = locale
                ).onSuccess {
                    fireInvitationSentEvent(InvitationSentEvent.InvitationSource.EMAIL_INVITATIONS)
                }
            }
            
            _addedEmails.value = emptyList()
            _emailInput.value = ""
            _currentView.value = InviteViewState.MAIN
            
            // Show success? or just dismiss?
            // For now, let's keep it simple
        }
    }
    
    // MARK: - Share source tracking

    companion object {
        private val shareSourceCodes = mapOf(
            "sms" to "m",
            "email" to "e",
            "native" to "n",
            "qrcode" to "q",
            "whatsapp" to "w",
            "twitter" to "t",
            "facebook" to "f",
            "instagram" to "i",
            "slack" to "s",
            "linkedin" to "l",
            "telegram" to "g",
            "discord" to "d",
            "line" to "y",
            "msteams" to "p"
        )

        /**
         * Inject a share source code into a shareable link URL.
         * Transforms /i/shortcode → /i/{code}/shortcode so analytics can track
         * which platform drove the click.
         */
        fun injectShareSource(baseUrl: String, source: String): String {
            val code = shareSourceCodes[source] ?: return baseUrl
            val uri = Uri.parse(baseUrl) ?: return baseUrl
            val pathSegments = uri.pathSegments.toMutableList()
            val inviteIndex = pathSegments.indexOf("i")
            if (inviteIndex >= 0 && inviteIndex < pathSegments.size - 1) {
                pathSegments.add(inviteIndex + 1, code)
                val newPath = "/" + pathSegments.joinToString("/")
                val newUri = uri.buildUpon().path(newPath).build()
                return newUri.toString()
            }
            return baseUrl
        }
    }

    // MARK: - Share handling
    
    /**
     * Get or generate shareable link
     */
    private suspend fun getShareableLink(): String? {
        _shareableLink.value?.let { return it }
        
        val widgetConfigId = _configuration.value?.id ?: componentId

        return client.generateShareableLink(
            widgetId = widgetConfigId,
            groups = group?.let { listOf(it) },
            templateVariables = templateVariables,
            metadata = unfurlConfig?.toMetadata()
        ).getOrNull()?.data?.invitation?.shortLink?.also {
            _shareableLink.value = it
        }
    }
    
    /**
     * Copy shareable link to clipboard
     */
    fun copyLink(context: Context) {
        trackShareLinkClick("copyLink")
        viewModelScope.launch {
            _loadingCopy.value = true
            _copySuccess.value = false
            
            val link = getShareableLink()
            if (link != null) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Invitation Link", injectShareSource(link, "native"))
                clipboard.setPrimaryClip(clip)
                _copySuccess.value = true
                fireInvitationSentEvent(InvitationSentEvent.InvitationSource.SHARE_LINK, link)
                
                // Reset success state after delay
                kotlinx.coroutines.delay(2000)
                _copySuccess.value = false
            } else {
                android.util.Log.e("VortexInviteViewModel", "Failed to get shareable link for copying")
            }
            
            _loadingCopy.value = false
        }
    }
    
    /**
     * Share invitation via native share sheet
     */
    fun shareInvitation(context: Context) {
        trackShareLinkClick("shareViaNativeShare")
        viewModelScope.launch {
            _loadingShare.value = true
            _shareSuccess.value = false
            
            val link = getShareableLink()
            if (link != null) {
                val sourcedLink = injectShareSource(link, "native")
                val fullMessage = buildShareText(sourcedLink)
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                    putExtra(Intent.EXTRA_TEXT, fullMessage)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, "Share Invitation")
                shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(shareIntent)
                _shareSuccess.value = true
                fireInvitationSentEvent(InvitationSentEvent.InvitationSource.SHARE_LINK, link)
                
                // Reset success state after delay
                kotlinx.coroutines.delay(2000)
                _shareSuccess.value = false
            } else {
                android.util.Log.e("VortexInviteViewModel", "Failed to get shareable link for sharing")
            }
            
            _loadingShare.value = false
        }
    }
    
    /**
     * Share via SMS - Opens the native SMS app with pre-filled message
     */
    fun shareViaSms(context: Context) {
        trackShareLinkClick("shareViaSMS")
        viewModelScope.launch {
            getShareableLink()?.let { baseLink ->
                val link = injectShareSource(baseLink, "sms")
                val fullMessage = buildShareText(link)
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:")
                    putExtra("sms_body", fullMessage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                fireInvitationSentEvent(InvitationSentEvent.InvitationSource.SHARE_LINK, link)
            }
        }
    }
    
    /**
     * Show QR code view
     */
    fun showQrCode() {
        trackShareLinkClick("shareViaQrCode")
        viewModelScope.launch {
            getShareableLink()?.let { link ->
                _qrCodeLink.value = injectShareSource(link, "qrcode")
                _currentView.value = InviteViewState.QR_CODE
            }
        }
    }
    
    /**
     * Share via WhatsApp
     */
    fun shareViaWhatsApp(context: Context) {
        trackShareLinkClick("shareViaWhatsApp")
        viewModelScope.launch {
            getShareableLink()?.let { baseLink ->
                val link = injectShareSource(baseLink, "whatsapp")
                val fullMessage = buildShareText(link)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://wa.me/?text=${Uri.encode(fullMessage)}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
    
    /**
     * Share via Telegram
     */
    fun shareViaTelegram(context: Context) {
        trackShareLinkClick("shareViaTelegram")
        viewModelScope.launch {
            getShareableLink()?.let { baseLink ->
                val link = injectShareSource(baseLink, "telegram")
                val fullMessage = buildShareText(link)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://t.me/share/url?url=${Uri.encode(link)}&text=${Uri.encode(fullMessage)}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
    
    /**
     * Share via LINE
     */
    fun shareViaLine(context: Context) {
        trackShareLinkClick("shareViaLine")
        viewModelScope.launch {
            getShareableLink()?.let { baseLink ->
                val link = injectShareSource(baseLink, "line")
                val fullMessage = buildShareText(link)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://line.me/R/msg/text/?${Uri.encode(fullMessage)}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
    
    /**
     * Share via Email
     */
    fun shareViaEmail(context: Context) {
        trackShareLinkClick("shareViaEmail")
        viewModelScope.launch {
            getShareableLink()?.let { baseLink ->
                val link = injectShareSource(baseLink, "email")
                val fullMessage = buildShareText(link)
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                    putExtra(Intent.EXTRA_TEXT, fullMessage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
    
    /**
     * Share via Twitter/X
     */
    fun shareViaTwitter(context: Context) {
        trackShareLinkClick("shareViaTwitter")
        viewModelScope.launch {
            getShareableLink()?.let { baseLink ->
                val link = injectShareSource(baseLink, "twitter")
                val fullMessage = buildShareText(link)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://twitter.com/intent/tweet?text=${Uri.encode(fullMessage)}&url=${Uri.encode(link)}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
    
    /**
     * Share via Instagram (opens app)
     */
    fun shareViaInstagram(context: Context) {
        trackShareLinkClick("shareViaInstagram")
        viewModelScope.launch {
            getShareableLink()?.let { baseLink ->
                val link = injectShareSource(baseLink, "instagram")
                // Instagram doesn't have a direct share URL, copy to clipboard and open app
                val fullMessage = buildShareText(link)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Invitation", fullMessage)
                clipboard.setPrimaryClip(clip)
                
                val intent = context.packageManager.getLaunchIntentForPackage("com.instagram.android")
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent?.let { context.startActivity(it) }
            }
        }
    }
    
    /**
     * Share via Facebook Messenger
     */
    fun shareViaFacebookMessenger(context: Context) {
        trackShareLinkClick("shareViaFacebookMessenger")
        viewModelScope.launch {
            getShareableLink()?.let { baseLink ->
                val link = injectShareSource(baseLink, "facebook")
                // Messenger share link works best with just the URL
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("fb-messenger://share/?link=${Uri.encode(link)}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to web
                    val webIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://www.facebook.com/dialog/send?link=${Uri.encode(link)}&app_id=123456789") // Note: App ID might be needed for web dialog
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(webIntent)
                }
            }
        }
    }
    
    /**
     * Share via Discord
     */
    fun shareViaDiscord(context: Context) {
        trackShareLinkClick("shareViaDiscord")
        viewModelScope.launch {
            getShareableLink()?.let { baseLink ->
                val link = injectShareSource(baseLink, "discord")
                // Discord doesn't have a direct share URL, copy to clipboard and open app
                val fullMessage = buildShareText(link)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Invitation", fullMessage)
                clipboard.setPrimaryClip(clip)
                
                val intent = context.packageManager.getLaunchIntentForPackage("com.discord")
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent?.let { context.startActivity(it) }
            }
        }
    }
    
    // MARK: - Helper methods
    
    /**
     * Find the share options block in the configuration
     */
    private fun findShareOptionsBlock(): ElementNode? {
        val elements = _configuration.value?.elements ?: return null
        return findBlockBySubtype(elements, "share-options") 
            ?: findBlockBySubtype(elements, "vrtx-share-options")
    }
    
    /**
     * Find the invite-contacts (SMS) block in the configuration
     */
    fun findInviteContactsBlock(): ElementNode? {
        val elements = _configuration.value?.elements ?: return null
        return findBlockBySubtype(elements, "invite-contacts")
            ?: findBlockBySubtype(elements, "vrtx-invite-contacts")
            ?: findBlockBySubtype(elements, "sms-invitations")
            ?: findBlockBySubtype(elements, "vrtx-sms-invitations")
    }
    
    /**
     * Read a customization label from any element block, with nested key lookup fallback.
     * Tries flat dotted key first (e.g. "google.frequentlyContactedTitle"),
     * then nested path (e.g. google -> frequentlyContactedTitle -> textContent).
     */
    fun customLabel(block: ElementNode?, key: String, default: String): String {
        // Try flat dotted key first
        block?.getCustomButtonLabel(key)?.let { return it }
        // Try nested path lookup through raw JSON
        val keyParts = key.split(".")
        if (keyParts.size >= 2) {
            try {
                val customizations = block?.settings?.get("customizations") as? JsonObject ?: return default
                var current: JsonElement? = customizations
                for (part in keyParts) {
                    current = (current as? JsonObject)?.get(part)
                    if (current == null) break
                }
                val textContent = (current as? JsonObject)?.get("textContent")?.let { it as? JsonPrimitive }?.contentOrNull
                if (textContent != null) return textContent
            } catch (_: Exception) {}
        }
        return default
    }

    /**
     * Infer a display name from an email address (e.g. "john.doe@example.com" -> "John Doe")
     */
    private fun inferNameFromEmail(email: String): String {
        val localPart = email.split("@").firstOrNull() ?: return email
        val name = localPart
            .replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return name.ifBlank { email }
    }

    /**
     * Filtered frequently contacted (calendar) Google contacts based on search query
     */
    val filteredFrequentlyContacted: List<VortexContact>
        get() {
            val calendar = _googleContacts.value.filter { it.source == ContactSource.CALENDAR }
            val query = _contactSearchQuery.value
            if (query.isBlank()) return calendar
            val q = query.lowercase()
            return calendar.filter { it.name.lowercase().contains(q) || it.email.lowercase().contains(q) }
        }

    /**
     * Filtered main (non-calendar) Google contacts based on search query
     */
    val filteredMainContacts: List<VortexContact>
        get() {
            val main = _googleContacts.value.filter { it.source != ContactSource.CALENDAR }
            val query = _contactSearchQuery.value
            if (query.isBlank()) return main
            val q = query.lowercase()
            return main.filter { it.name.lowercase().contains(q) || it.email.lowercase().contains(q) }
        }

    /**
     * Grouped main contacts by first letter for alphabetic section headers
     */
    val groupedMainContacts: List<Pair<String, List<VortexContact>>>
        get() {
            val contacts = filteredMainContacts
            val groups = mutableMapOf<String, MutableList<VortexContact>>()
            for (contact in contacts) {
                val firstChar = contact.name.firstOrNull()?.uppercase() ?: "#"
                val letter = if (firstChar.first().isLetter()) firstChar else "#"
                groups.getOrPut(letter) { mutableListOf() }.add(contact)
            }
            return groups.toSortedMap().map { (k, v) -> Pair(k, v) }
        }

    /**
     * Find the contacts import block in the configuration
     */
    internal fun findContactsImportBlock(): ElementNode? {
        val elements = _configuration.value?.elements ?: return null
        return findBlockBySubtype(elements, "contacts-import")
            ?: findBlockBySubtype(elements, "vrtx-contacts-import")
    }
    
    /**
     * Recursively find a block by subtype
     */
    private fun findBlockBySubtype(elements: List<ElementNode>, subtype: String): ElementNode? {
        for (element in elements) {
            if (element.subtype == subtype) {
                return element
            }
            element.children.let { children ->
                findBlockBySubtype(children, subtype)?.let { return it }
            }
        }
        return null
    }
    
    // MARK: - Analytics tracking
    
    /**
     * Track an analytics event
     */
    private fun trackEvent(eventName: VortexEventName, payload: Map<String, Any>? = null) {
        val event = VortexAnalyticsEvent(
            name = eventName.eventName,
            widgetConfigurationId = _configuration.value?.id ?: componentId,
            deploymentId = deploymentId,
            sessionId = sessionId,
            useragent = VortexDeviceInfo.useragent,
            foreignUserId = VortexJWTParser.extractForeignUserId(jwt),
            segmentation = segmentation,
            payload = payload,
            groups = group?.let { 
                listOf(GroupInfo(
                    type = it.type,
                    id = it.groupId ?: it.id ?: "",
                    name = it.name
                ))
            }
        )
        
        // Call user's callback
        onEvent?.invoke(event)
        
        // Send to analytics backend (fire-and-forget)
        analyticsClient.track(event)
    }
    
    /**
     * Track widget render event (once per session)
     */
    fun trackWidgetRender() {
        if (widgetRenderTracked) return
        widgetRenderTracked = true
        formRenderTime = System.currentTimeMillis()
        trackEvent(VortexEventName.WIDGET_RENDER)
    }
    
    /**
     * Track widget error event
     */
    fun trackWidgetError(error: String) {
        trackEvent(VortexEventName.WIDGET_ERROR, mapOf("error" to error))
    }
    
    /**
     * Track share link click event
     */
    fun trackShareLinkClick(clickName: String) {
        trackEvent(VortexEventName.WIDGET_SHARE_LINK_CLICK, mapOf("clickName" to clickName))
    }
    
    /**
     * Track email field focus event
     */
    fun trackEmailFieldFocus() {
        val timestamp = formRenderTime?.let { System.currentTimeMillis() - it } ?: 0L
        trackEvent(VortexEventName.WIDGET_EMAIL_FIELD_FOCUS, mapOf("timestamp" to timestamp))
    }
    
    /**
     * Track email field blur event
     */
    fun trackEmailFieldBlur() {
        val timestamp = formRenderTime?.let { System.currentTimeMillis() - it } ?: 0L
        trackEvent(VortexEventName.WIDGET_EMAIL_FIELD_BLUR, mapOf("timestamp" to timestamp))
    }
    
    /**
     * Track email validation event
     */
    fun trackEmailValidation(email: String, isValid: Boolean) {
        trackEvent(VortexEventName.WIDGET_EMAIL_VALIDATION, mapOf(
            "email" to email,
            "isValid" to isValid
        ))
    }
    
    /**
     * Track email invitations submitted event
     */
    fun trackEmailInvitationsSubmitted(formData: Map<String, Any>) {
        trackEvent(VortexEventName.EMAIL_INVITATIONS_SUBMITTED, mapOf("formData" to formData))
    }
    
    /**
     * Track email validation error event
     */
    fun trackEmailValidationError(formData: Map<String, Any>) {
        trackEvent(VortexEventName.WIDGET_EMAIL_VALIDATION_ERROR, mapOf("formData" to formData))
    }
    
    /**
     * Track email submit error event
     */
    fun trackEmailSubmitError(error: String) {
        trackEvent(VortexEventName.WIDGET_EMAIL_SUBMIT_ERROR, mapOf("error" to error))
    }
    
    /**
     * Track outbound invitation delete event
     */
    fun trackOutboundInvitationDelete(invitationId: String, inviteeName: String) {
        trackEvent(VortexEventName.WIDGET_SHARE_LINK_CLICK, mapOf(
            "clickName" to "cancelOutboundInvitation",
            "invitationId" to invitationId,
            "inviteeName" to inviteeName
        ))
    }
    
    /**
     * Track find friends list displayed event (when the list of found friends is rendered)
     */
    fun trackFindFriendsListDisplayed(count: Int) {
        trackEvent(VortexEventName.FIND_FRIENDS_LIST_DISPLAYED, mapOf("count" to count))
    }
    
    /**
     * Track contacts link click event (when user clicks to view contacts list)
     */
    fun trackContactsLinkClicked() {
        trackEvent(VortexEventName.CONTACTS_LINK_CLICKED)
    }
    
    /**
     * Track contacts invite button click event (when user clicks to invite a specific contact)
     */
    fun trackContactsInviteButtonClicked() {
        trackEvent(VortexEventName.CONTACTS_INVITE_BUTTON_CLICKED)
    }
    
    /**
     * Factory for creating VortexInviteViewModel with parameters
     */
    class Factory(
        private val componentId: String,
        private val jwt: String?,
        private val apiBaseUrl: String,
        private val analyticsBaseUrl: String?,
        private val group: GroupDTO?,
        private val segmentation: Map<String, Any>?,
        private val googleClientId: String?,
        private val onDismiss: (() -> Unit)?,
        private val onEvent: ((VortexAnalyticsEvent) -> Unit)?,
        private val enableLogging: Boolean = false,
        private val initialConfiguration: WidgetConfiguration? = null,
        private val initialDeploymentId: String? = null,
        private val locale: String? = null,
        private val findFriendsConfig: FindFriendsConfig? = null,
        private val inviteContactsConfig: InviteContactsConfig? = null,
        private val invitationSuggestionsConfig: InvitationSuggestionsConfig? = null,
        private val incomingInvitationsConfig: IncomingInvitationsConfig? = null,
        private val outgoingInvitationsConfig: OutgoingInvitationsConfig? = null,
        private val searchBoxConfig: SearchBoxConfig? = null,
        private val unfurlConfig: UnfurlConfig? = null,
        private val templateVariables: Map<String, String>? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VortexInviteViewModel::class.java)) {
                return VortexInviteViewModel(
                    componentId = componentId,
                    jwt = jwt,
                    apiBaseUrl = apiBaseUrl,
                    analyticsBaseUrl = analyticsBaseUrl,
                    group = group,
                    segmentation = segmentation,
                    googleClientId = googleClientId,
                    onDismiss = onDismiss,
                    onEvent = onEvent,
                    enableLogging = enableLogging,
                    initialConfiguration = initialConfiguration,
                    initialDeploymentId = initialDeploymentId,
                    locale = locale,
                    findFriendsConfig = findFriendsConfig,
                    inviteContactsConfig = inviteContactsConfig,
                    invitationSuggestionsConfig = invitationSuggestionsConfig,
                    incomingInvitationsConfig = incomingInvitationsConfig,
                    outgoingInvitationsConfig = outgoingInvitationsConfig,
                    searchBoxConfig = searchBoxConfig,
                    unfurlConfig = unfurlConfig,
                    templateVariables = templateVariables
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
