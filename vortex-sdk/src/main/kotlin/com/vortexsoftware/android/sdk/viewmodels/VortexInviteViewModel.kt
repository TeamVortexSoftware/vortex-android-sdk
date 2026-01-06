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
import com.vortexsoftware.android.sdk.analytics.*
import com.vortexsoftware.android.sdk.api.VortexClient
import com.vortexsoftware.android.sdk.api.dto.GroupDTO
import com.vortexsoftware.android.sdk.cache.VortexConfigurationCache
import com.vortexsoftware.android.sdk.models.*
import kotlinx.coroutines.Dispatchers
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
    private val initialDeploymentId: String? = null
) : ViewModel() {
    
    val googleClientIdValue: String? get() = googleClientId
    
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
    
    private val client = VortexClient(
        baseUrl = apiBaseUrl,
        jwt = jwt,
        enableLogging = enableLogging
    )
    
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
    
    // Share states
    private val _shareableLink = MutableStateFlow<String?>(null)
    val shareableLink: StateFlow<String?> = _shareableLink.asStateFlow()
    
    private val _loadingCopy = MutableStateFlow(false)
    val loadingCopy: StateFlow<Boolean> = _loadingCopy.asStateFlow()
    
    private val _copySuccess = MutableStateFlow(false)
    val copySuccess: StateFlow<Boolean> = _copySuccess.asStateFlow()
    
    private val _loadingShare = MutableStateFlow(false)
    val loadingShare: StateFlow<Boolean> = _loadingShare.asStateFlow()
    
    private val _shareSuccess = MutableStateFlow(false)
    val shareSuccess: StateFlow<Boolean> = _shareSuccess.asStateFlow()

    // Share Message configuration
    val shareTitle: String
        get() {
            val config = _configuration.value ?: return "You're Invited!"
            return config.props["vortex.components.share.title"]?.value?.let { (it as? JsonPrimitive)?.content }
                ?: findShareOptionsBlock()?.getString("shareTitle")
                ?: "You're Invited!"
        }

    val shareMessage: String
        get() {
            val config = _configuration.value ?: return "Join me on this project!"
            return config.props["vortex.components.share.message"]?.value?.let { (it as? JsonPrimitive)?.content }
                ?: findShareOptionsBlock()?.getString("shareMessage")
                ?: "Join me on this project!"
        }

    // Google Contacts state
    private val _googleContacts = MutableStateFlow<List<VortexContact>>(emptyList())
    val googleContacts: StateFlow<List<VortexContact>> = _googleContacts.asStateFlow()

    private val _isGoogleLoading = MutableStateFlow(false)
    val isGoogleLoading: StateFlow<Boolean> = _isGoogleLoading.asStateFlow()

    private val _isGoogleAuthenticated = MutableStateFlow(false)
    val isGoogleAuthenticated: StateFlow<Boolean> = _isGoogleAuthenticated.asStateFlow()
    
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
    
    /**
     * Load widget configuration with stale-while-revalidate pattern.
     * If cached/prefetched configuration exists, it's used immediately (no loading spinner).
     * Fresh configuration is always fetched in the background to ensure up-to-date data.
     */
    fun loadConfiguration() {
        viewModelScope.launch {
            _error.value = null
            
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
            
            // Step 3: Always fetch fresh configuration (stale-while-revalidate)
            client.getWidgetConfiguration(componentId)
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
                    
                    _isLoading.value = false
                    // Track widget render on successful load
                    trackWidgetRender()
                    
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
                }
        }
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
                formData = getFormData().takeIf { it.isNotEmpty() }
            ).onSuccess {
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
    fun onGoogleSignInSuccess(accountEmail: String) {
        _isGoogleAuthenticated.value = true
        fetchGoogleContacts(accountEmail)
    }

    /**
     * Fetch contacts from Google People API
     */
    private fun fetchGoogleContacts(accessToken: String) {
        viewModelScope.launch {
            _isGoogleLoading.value = true
            _error.value = null
            
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://people.googleapis.com/v1/people/me/connections?personFields=names,emailAddresses&pageSize=1000")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
                
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                if (!response.isSuccessful) {
                    throw Exception("Google API error: ${response.code}")
                }

                val body = response.body?.string() ?: throw Exception("Empty response body")
                val json = Json { ignoreUnknownKeys = true }
                val data = json.parseToJsonElement(body).jsonObject
                
                val contacts = mutableListOf<VortexContact>()
                val connections = data["connections"]?.jsonArray ?: emptyList<JsonElement>()
                
                for (person in connections) {
                    val personObj = person.jsonObject
                    val emailAddresses = personObj["emailAddresses"]?.jsonArray ?: continue
                    
                    for (emailObj in emailAddresses) {
                        val email = emailObj.jsonObject["value"]?.jsonPrimitive?.content ?: continue
                        val name = personObj["names"]?.jsonArray?.getOrNull(0)?.jsonObject?.get("displayName")?.jsonPrimitive?.content ?: email
                        
                        contacts.add(VortexContact.create(name, email))
                    }
                }
                
                _googleContacts.value = contacts.distinctBy { it.email.lowercase() }
            } catch (e: Exception) {
                _error.value = "Failed to fetch Google contacts: ${e.message}"
                android.util.Log.e("Vortex", "Error fetching contacts", e)
            } finally {
                _isGoogleLoading.value = false
            }
        }
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
                    formData = getFormData().takeIf { it.isNotEmpty() }
                )
            }
            
            _addedEmails.value = emptyList()
            _emailInput.value = ""
            _currentView.value = InviteViewState.MAIN
            
            // Show success? or just dismiss?
            // For now, let's keep it simple
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
            groups = group?.let { listOf(it) }
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
                val clip = ClipData.newPlainText("Invitation Link", link)
                clipboard.setPrimaryClip(clip)
                _copySuccess.value = true
                
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
        trackShareLinkClick("share")
        viewModelScope.launch {
            _loadingShare.value = true
            _shareSuccess.value = false
            
            val link = getShareableLink()
            if (link != null) {
                val fullMessage = "$shareMessage $link"
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
     * Share via SMS
     */
    fun shareViaSms(context: Context) {
        trackShareLinkClick("sms")
        viewModelScope.launch {
            getShareableLink()?.let { link ->
                val fullMessage = "$shareMessage $link"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("sms:")
                    putExtra("sms_body", fullMessage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
    
    /**
     * Show QR code view
     */
    fun showQrCode() {
        trackShareLinkClick("qrCode")
        viewModelScope.launch {
            getShareableLink()?.let {
                _currentView.value = InviteViewState.QR_CODE
            }
        }
    }
    
    /**
     * Share via WhatsApp
     */
    fun shareViaWhatsApp(context: Context) {
        trackShareLinkClick("whatsapp")
        viewModelScope.launch {
            getShareableLink()?.let { link ->
                val fullMessage = "$shareMessage $link"
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
        trackShareLinkClick("telegram")
        viewModelScope.launch {
            getShareableLink()?.let { link ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://t.me/share/url?url=${Uri.encode(link)}&text=${Uri.encode(shareMessage)}")
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
        trackShareLinkClick("line")
        viewModelScope.launch {
            getShareableLink()?.let { link ->
                val fullMessage = "$shareMessage $link"
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
        trackShareLinkClick("email")
        viewModelScope.launch {
            getShareableLink()?.let { link ->
                val fullMessage = "$shareMessage $link"
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
        trackShareLinkClick("twitter")
        viewModelScope.launch {
            getShareableLink()?.let { link ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://twitter.com/intent/tweet?text=${Uri.encode(shareMessage)}&url=${Uri.encode(link)}")
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
        trackShareLinkClick("instagram")
        viewModelScope.launch {
            getShareableLink()?.let { link ->
                // Instagram doesn't have a direct share URL, copy to clipboard and open app
                val fullMessage = "$shareMessage $link"
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
        trackShareLinkClick("messenger")
        viewModelScope.launch {
            getShareableLink()?.let { link ->
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
        trackShareLinkClick("discord")
        viewModelScope.launch {
            getShareableLink()?.let { link ->
                // Discord doesn't have a direct share URL, copy to clipboard and open app
                val fullMessage = "$shareMessage $link"
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
     * Find the contacts import block in the configuration
     */
    private fun findContactsImportBlock(): ElementNode? {
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
        private val initialDeploymentId: String? = null
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
                    initialDeploymentId = initialDeploymentId
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
