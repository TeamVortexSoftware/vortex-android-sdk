package com.vortexsoftware.android.sdk.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.vortexsoftware.android.sdk.VortexSDK
import com.vortexsoftware.android.sdk.api.dto.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Retrofit API interface for Vortex backend
 */
interface VortexApi {
    
    /**
     * Fetch widget configuration
     */
    @GET("api/v1/widgets/{componentId}")
    suspend fun getWidgetConfiguration(
        @Path("componentId") componentId: String,
        @Query("locale") locale: String? = null,
        @Query("templateVariables") templateVariables: String? = null
    ): Response<WidgetConfigurationResponse>
    
    /**
     * Create an invitation
     */
    @POST("api/v1/invitations")
    suspend fun createInvitation(
        @Body request: CreateInvitationRequest
    ): Response<CreateInvitationResponse>
    
    /**
     * Create an SMS invitation
     */
    @POST("api/v1/invitations")
    suspend fun createSmsInvitation(
        @Body request: CreateSmsInvitationRequest
    ): Response<CreateInvitationResponse>
    
    /**
     * Create an internal ID invitation (Find Friends)
     */
    @POST("api/v1/invitations")
    suspend fun createInternalIdInvitation(
        @Body request: CreateInternalIdInvitationRequest
    ): Response<CreateInvitationResponse>
    
    /**
     * Generate a shareable link
     */
    @POST("api/v1/invitations/generate-shareable-link-invite")
    suspend fun generateShareableLink(
        @Body request: GenerateShareableLinkRequest
    ): Response<ShareableLinkResponse>
    
    /**
     * Get outgoing (sent) invitations
     */
    @GET("api/v1/invitations/sent")
    suspend fun getOutgoingInvitations(): Response<OutgoingInvitationsResponse>
    
    /**
     * Get incoming (received) invitations
     */
    @GET("api/v1/invitations")
    suspend fun getIncomingInvitations(): Response<IncomingInvitationsResponse>
    
    /**
     * Revoke (cancel) an invitation
     */
    @DELETE("api/v1/invitations/{invitationId}")
    suspend fun revokeInvitation(
        @Path("invitationId") invitationId: String
    ): Response<Unit>
    
    /**
     * Accept an incoming invitation
     */
    @POST("api/v1/invitations/accept")
    suspend fun acceptInvitation(
        @Body request: AcceptInvitationRequest
    ): Response<Unit>
    
    /**
     * Get a single invitation by ID
     */
    @GET("api/v1/invitations/{invitationId}")
    suspend fun getInvitation(
        @Path("invitationId") invitationId: String
    ): Response<Invitation>

    /**
     * Delete (reject) an incoming invitation
     */
    @DELETE("api/v1/invitations/{invitationId}")
    suspend fun deleteInvitation(
        @Path("invitationId") invitationId: String
    ): Response<Unit>
    
    /**
     * Match device fingerprint for deferred deep linking
     */
    @POST("api/v1/deferred-links/match")
    suspend fun matchFingerprint(
        @Body request: MatchFingerprintRequest
    ): Response<MatchFingerprintResponse>
}

/**
 * Main API client for Vortex backend communication
 */
class VortexClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val jwt: String? = null,
    private val enableLogging: Boolean = false,
    private val clientName: String = "VortexSDK-Android",
    private val clientVersion: String = VortexSDK.VERSION
) {
    
    companion object {
        const val DEFAULT_BASE_URL = "https://client-api.vortexsoftware.com"
        private const val TIMEOUT_SECONDS = 30L
    }

    private val sessionId = UUID.randomUUID().toString()
    private var sessionAttestation: String? = null
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val requestBuilder = request.newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("x-session-id", sessionId)
                    .addHeader("x-vortex-client-version", clientVersion)
                    .addHeader("x-vortex-client-name", clientName)
                
                if (!jwt.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $jwt")
                }

                sessionAttestation?.let {
                    requestBuilder.addHeader("x-session-attestation", it)
                }
                
                val finalRequest = requestBuilder.build()
                
                if (enableLogging) {
                    val buffer = okio.Buffer()
                    finalRequest.body?.writeTo(buffer)
                    val bodyString = buffer.readUtf8()
                    android.util.Log.d("VortexClient", "Sending ${finalRequest.method} request to ${finalRequest.url}")
                    android.util.Log.d("VortexClient", "Request Body: $bodyString")
                }
                
                chain.proceed(finalRequest)
            }
            .apply {
                if (enableLogging) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                } else {
                    // Always log errors even if logging is disabled
                    addInterceptor(HttpLoggingInterceptor { message ->
                        if (message.contains("HTTP/1.1 4") || message.contains("HTTP/1.1 5") ||
                            message.contains("HTTP/2 4") || message.contains("HTTP/2 5")) {
                            android.util.Log.e("VortexClient", message)
                        }
                    }.apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .build()
    }
    
    private val retrofit: Retrofit by lazy {
        val contentType = "application/json".toMediaType()
        Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
    
    private val api: VortexApi by lazy {
        retrofit.create(VortexApi::class.java)
    }
    
    /**
     * Fetch widget configuration for the given component ID
     * @param componentId The widget/component ID
     * @param locale Optional BCP 47 language code for internationalization (e.g., "pt-BR", "en-US")
     */
    suspend fun getWidgetConfiguration(
        componentId: String,
        locale: String? = null,
        templateVariables: Map<String, String>? = null
    ): Result<WidgetConfigurationResponse> {
        // Serialize templateVariables to JSON string for query parameter
        val templateVariablesJson = templateVariables?.let {
            val jsonObj = JsonObject(it.mapValues { (_, v) -> JsonPrimitive(v) })
            jsonObj.toString()
        }
        val result = executeRequest {
            api.getWidgetConfiguration(componentId, locale, templateVariablesJson)
        }
        
        result.onSuccess { response ->
            response.data.sessionAttestation?.let {
                this.sessionAttestation = it
            }
        }
        
        return result
    }
    
    /**
     * Create an invitation for the given email
     */
    suspend fun createInvitation(
        widgetId: String,
        inviteeEmail: String,
        groups: List<GroupDTO>? = null,
        formData: Map<String, String>? = null,
        passThrough: String? = null,
        configurationAttributes: Map<String, JsonElement>? = null,
        templateVariables: Map<String, String>? = null,
        metadata: Map<String, Any>? = null,
        subtype: String? = null,
        locale: String? = null
    ): Result<CreateInvitationResponse> {
        val payload = mutableMapOf<String, InvitationPayloadValue>()
        payload["email"] = InvitationPayloadValue(value = JsonPrimitive(inviteeEmail), type = "email")
        
        formData?.forEach { (key, value) ->
            if (key != "email") {
                payload[key] = InvitationPayloadValue(value = JsonPrimitive(value), type = "text")
            }
        }
        
        return executeRequest {
            api.createInvitation(
                CreateInvitationRequest(
                    widgetConfigurationId = widgetId,
                    payload = payload,
                    groups = groups,
                    passThrough = passThrough,
                    configurationAttributes = configurationAttributes,
                    templateVariables = templateVariables,
                    metadata = metadata?.toJsonElementMap(),
                    subtype = subtype,
                    locale = locale
                )
            )
        }
    }

    /**
     * Create multiple invitations at once
     */
    suspend fun createInvitations(
        widgetId: String,
        inviteeEmails: List<String>,
        groups: List<GroupDTO>? = null,
        formData: Map<String, String>? = null,
        passThrough: String? = null,
        configurationAttributes: Map<String, JsonElement>? = null,
        templateVariables: Map<String, String>? = null,
        metadata: Map<String, Any>? = null,
        subtype: String? = null,
        locale: String? = null
    ): Result<CreateInvitationResponse> {
        val payload = mutableMapOf<String, InvitationPayloadValue>()
        
        // Use a single "email" key with a JsonArray of emails
        payload["email"] = InvitationPayloadValue(
            value = JsonArray(inviteeEmails.map { JsonPrimitive(it) }),
            type = "email"
        )
        
        formData?.forEach { (key, value) ->
            if (key != "email") {
                payload[key] = InvitationPayloadValue(value = JsonPrimitive(value), type = "text")
            }
        }
        
        return executeRequest {
            api.createInvitation(
                CreateInvitationRequest(
                    widgetConfigurationId = widgetId,
                    payload = payload,
                    groups = groups,
                    passThrough = passThrough,
                    configurationAttributes = configurationAttributes,
                    templateVariables = templateVariables,
                    metadata = metadata?.toJsonElementMap(),
                    subtype = subtype,
                    locale = locale
                )
            )
        }
    }

    /**
     * Generate a shareable link for the widget
     */
    suspend fun generateShareableLink(
        widgetId: String,
        groups: List<GroupDTO>? = null,
        templateVariables: Map<String, String>? = null,
        metadata: Map<String, Any>? = null
    ): Result<ShareableLinkResponse> {
        return executeRequest {
            api.generateShareableLink(
                GenerateShareableLinkRequest(
                    widgetConfigurationId = widgetId,
                    groups = groups,
                    templateVariables = templateVariables,
                    metadata = metadata?.toJsonElementMap()
                )
            )
        }
    }
    
    /**
     * Create an SMS invitation
     * @param widgetId The widget configuration ID
     * @param phoneNumber The recipient's phone number
     * @param contactName Optional name of the contact
     * @param groups Optional groups for scoping
     * @param templateVariables Optional template variables
     * @return The short link for the invitation
     */
    suspend fun createSmsInvitation(
        widgetId: String,
        phoneNumber: String,
        contactName: String? = null,
        groups: List<GroupDTO>? = null,
        templateVariables: Map<String, String>? = null,
        metadata: Map<String, Any>? = null,
        locale: String? = null
    ): Result<String?> {
        // Build payload matching iOS/RN SDK format:
        // { smsTarget: { type: "phone", value: [{ value: phoneNumber, name?: contactName }] } }
        val valueObject = mutableMapOf<String, JsonPrimitive>(
            "value" to JsonPrimitive(phoneNumber)
        )
        contactName?.let {
            valueObject["name"] = JsonPrimitive(it)
        }

        val payload = mapOf(
            "smsTarget" to InvitationPayloadValue(
                value = kotlinx.serialization.json.JsonArray(listOf(JsonObject(valueObject))),
                type = "phone"
            )
        )

        return executeRequest {
            api.createInvitation(
                CreateInvitationRequest(
                    widgetConfigurationId = widgetId,
                    payload = payload,
                    source = "phone",
                    groups = groups,
                    templateVariables = templateVariables,
                    metadata = metadata?.toJsonElementMap(),
                    locale = locale
                )
            )
        }.map { response ->
            response.data.invitationEntries?.firstOrNull()?.shortLink
        }
    }
    
    /**
     * Create an internal ID invitation (for Find Friends feature)
     * @param widgetId The widget configuration ID
     * @param internalId The internal user ID
     * @param contactName Optional name of the contact
     * @param groups Optional groups for scoping
     * @param templateVariables Optional template variables
     */
    suspend fun createInternalIdInvitation(
        widgetId: String,
        internalId: String,
        contactName: String? = null,
        contactAvatarUrl: String? = null,
        contactEmail: String? = null,
        groups: List<GroupDTO>? = null,
        templateVariables: Map<String, String>? = null,
        metadata: Map<String, Any>? = null,
        subtype: String? = null,
        locale: String? = null
    ): Result<CreateInvitationResponse> {
        // Build the target value object: { value: internalId, name: contactName, avatarUrl?: string }
        val targetValueMap = mutableMapOf<String, JsonPrimitive>(
            "value" to JsonPrimitive(internalId),
            "name" to JsonPrimitive(contactName ?: "")
        )
        contactAvatarUrl?.let {
            targetValueMap["avatarUrl"] = JsonPrimitive(it)
        }

        // Build payload matching iOS/RN SDK format:
        // { internalId: { type: "internal", value: { value, name, avatarUrl? }, email?: "x@y.com" } }
        // DEV-2043: Email goes INSIDE the internalId field for multi-target support
        val payload = mapOf(
            "internalId" to InvitationPayloadValue(
                value = JsonObject(targetValueMap),
                type = "internal",
                email = contactEmail
            )
        )

        return executeRequest {
            api.createInvitation(
                CreateInvitationRequest(
                    widgetConfigurationId = widgetId,
                    payload = payload,
                    source = "internal",
                    groups = groups,
                    templateVariables = templateVariables,
                    metadata = metadata?.toJsonElementMap(),
                    subtype = subtype,
                    locale = locale
                )
            )
        }
    }
    
    /**
     * Retrieves details of a specific invitation by its ID.
     *
     * Use this method to fetch the full details of any invitation, including its targets,
     * groups, acceptance records, and metadata.
     *
     * @param invitationId The ID of the invitation to retrieve
     * @return The full invitation details
     * @throws VortexError if the request fails or the invitation is not found
     */
    suspend fun getInvitation(invitationId: String): Result<Invitation> {
        return executeRequest {
            api.getInvitation(invitationId)
        }
    }

    /**
     * Retrieves outgoing (sent) invitations for the current user.
     *
     * @return List of outgoing invitations
     */
    suspend fun getOutgoingInvitations(): Result<List<OutgoingInvitation>> {
        return executeRequest {
            api.getOutgoingInvitations()
        }.map { response ->
            response.data.invitations
        }
    }
    
    /**
     * Revokes an outgoing invitation that the user has sent.
     *
     * This permanently cancels the invitation. The invitation will no longer be
     * accessible to the recipient.
     *
     * @param invitationId The ID of the invitation to revoke
     * @throws VortexError if the request fails
     */
    suspend fun revokeInvitation(invitationId: String): Result<Unit> {
        return executeRequest {
            api.revokeInvitation(invitationId)
        }
    }
    
    /**
     * Fetches incoming (open) invitations for the current user.
     *
     * Returns only pending invitations that have not yet been accepted.
     *
     * @return List of incoming invitations
     */
    suspend fun getIncomingInvitations(): Result<List<IncomingInvitation>> {
        return executeRequest {
            api.getIncomingInvitations()
        }.map { response ->
            response.data.invitations
        }
    }
    
    /**
     * Accepts an incoming invitation that the user has received.
     *
     * Once accepted, the invitation status will be updated and the acceptance
     * will be recorded.
     *
     * @param invitationId The ID of the invitation to accept
     * @param isExisting Whether the accepting user was already a registered user of your service.
     *                   Set to `true` for existing users, `false` for new signups.
     *                   If `null` (default), the value is not specified.
     * @throws VortexError if the request fails
     */
    suspend fun acceptInvitation(invitationId: String, isExisting: Boolean? = null): Result<Unit> {
        return executeRequest {
            api.acceptInvitation(AcceptInvitationRequest(invitationId, isExisting))
        }
    }
    
    /**
     * Deletes (rejects/declines) an incoming invitation.
     *
     * This removes the invitation from the user's incoming list.
     *
     * @param invitationId The ID of the invitation to delete
     * @throws VortexError if the request fails
     */
    suspend fun deleteIncomingInvitation(invitationId: String): Result<Unit> {
        return executeRequest {
            api.deleteInvitation(invitationId)
        }
    }
    
    /**
     * Match device fingerprint for deferred deep linking
     * @param fingerprint Device fingerprint data
     * @return Match result containing invitation context if found
     */
    suspend fun matchFingerprint(fingerprint: DeviceFingerprint): Result<MatchFingerprintResponse> {
        return executeRequest {
            api.matchFingerprint(MatchFingerprintRequest(fingerprint))
        }
    }
    
    /**
     * Execute a request and handle errors
     */
    private suspend fun <T> executeRequest(
        request: suspend () -> Response<T>
    ): Result<T> {
        return try {
            val response = request()
            
            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it)
                } ?: Result.failure(VortexError.DecodingError("Empty response body"))
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("VortexClient", "Request failed: ${response.code()} - $errorBody")
                Result.failure(VortexError.fromHttpStatus(response.code(), errorBody))
            }
        } catch (e: Exception) {
            Result.failure(VortexError.fromException(e))
        }
    }
}

/**
 * Convert a Map<String, Any> to Map<String, JsonElement> for serialization.
 * Supports String, Number, Boolean, nested Map, and List values.
 */
@Suppress("UNCHECKED_CAST")
fun Map<String, Any>.toJsonElementMap(): Map<String, JsonElement> {
    return mapValues { (_, value) -> value.toJsonElement() }
}

private fun Any.toJsonElement(): JsonElement = when (this) {
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject((this as Map<String, Any>).toJsonElementMap())
    is List<*> -> JsonArray(this.mapNotNull { it?.toJsonElement() })
    else -> JsonPrimitive(toString())
}
