package com.vortexsoftware.android.sdk.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.vortexsoftware.android.sdk.VortexSDK
import com.vortexsoftware.android.sdk.api.dto.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
        @Path("componentId") componentId: String
    ): Response<WidgetConfigurationResponse>
    
    /**
     * Create an invitation
     */
    @POST("api/v1/invitations")
    suspend fun createInvitation(
        @Body request: CreateInvitationRequest
    ): Response<CreateInvitationResponse>
    
    /**
     * Generate a shareable link
     */
    @POST("api/v1/invitations/generate-shareable-link-invite")
    suspend fun generateShareableLink(
        @Body request: GenerateShareableLinkRequest
    ): Response<ShareableLinkResponse>
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
     */
    suspend fun getWidgetConfiguration(componentId: String): Result<WidgetConfigurationResponse> {
        val result = executeRequest {
            api.getWidgetConfiguration(componentId)
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
        configurationAttributes: Map<String, kotlinx.serialization.json.JsonElement>? = null,
        templateVariables: Map<String, String>? = null
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
                    templateVariables = templateVariables
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
        configurationAttributes: Map<String, kotlinx.serialization.json.JsonElement>? = null,
        templateVariables: Map<String, String>? = null
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
                    templateVariables = templateVariables
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
        templateVariables: Map<String, String>? = null
    ): Result<ShareableLinkResponse> {
        return executeRequest {
            api.generateShareableLink(
                GenerateShareableLinkRequest(
                    widgetConfigurationId = widgetId,
                    groups = groups,
                    templateVariables = templateVariables
                )
            )
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
