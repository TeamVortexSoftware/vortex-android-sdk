package com.vortexsoftware.android.sdk.analytics

import android.os.Build
import android.util.Base64
import com.vortexsoftware.android.sdk.VortexSDK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// MARK: - Analytics Event

/**
 * Represents a telemetry event that can be tracked by the Vortex SDK.
 *
 * Events are emitted during user interactions with the invitation widget and can be
 * consumed via the `onEvent` callback. This enables integration with external analytics
 * services and custom event handling.
 *
 * Example usage:
 * ```kotlin
 * VortexInviteView(
 *     componentId = "your-component-id",
 *     jwt = userJWT,
 *     onEvent = { event ->
 *         println("Event: ${event.name}")
 *         // Forward to your analytics service
 *     }
 * )
 * ```
 */
data class VortexAnalyticsEvent(
    /** The name of the event (e.g., "widget_render", "email_invitations_submitted") */
    val name: String,
    
    /** The widget configuration ID associated with this event */
    val widgetConfigurationId: String? = null,
    
    /** The deployment ID for this event */
    val deploymentId: String? = null,
    
    /** The environment ID for this event */
    val environmentId: String? = null,
    
    /** The platform on which the event occurred (always "android" for this SDK) */
    val platform: String = "android",
    
    /** The timestamp when the event occurred (Unix epoch in seconds) */
    val timestamp: Long = System.currentTimeMillis() / 1000,
    
    /** The session ID for this widget session */
    val sessionId: String? = null,
    
    /** The user agent string (Android device info) */
    val useragent: String? = null,
    
    /** The foreign user ID (user ID in your system, extracted from JWT) */
    val foreignUserId: String? = null,
    
    /** Optional segmentation data for analytics filtering */
    val segmentation: Map<String, Any>? = null,
    
    /** Optional event-specific payload containing additional context */
    val payload: Map<String, Any>? = null,
    
    /** Optional groups associated with this event */
    val groups: List<GroupInfo>? = null
) {
    /**
     * Converts the event to a JSON object for API transmission.
     * Uses camelCase keys as expected by the analytics API.
     */
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            put("name", name)
            widgetConfigurationId?.let { put("widgetConfigurationId", it) }
            deploymentId?.let { put("deploymentId", it) }
            environmentId?.let { put("environmentId", it) }
            put("platform", platform)
            put("timestamp", timestamp)
            sessionId?.let { put("sessionId", it) }
            useragent?.let { put("useragent", it) }
            foreignUserId?.let { put("foreignUserId", it) }
            segmentation?.let { put("segmentation", it.toJsonElement()) }
            payload?.let { put("payload", it.toJsonElement()) }
            groups?.let { groupList ->
                put("groups", buildJsonArray {
                    groupList.forEach { group ->
                        add(buildJsonObject {
                            put("type", group.type)
                            put("id", group.id)
                            put("name", group.name)
                        })
                    }
                })
            }
        }
    }
}

// MARK: - Group Info

/**
 * Group information for analytics events.
 *
 * Groups allow you to associate events with specific organizational units
 * in your system (e.g., teams, organizations, workspaces).
 */
data class GroupInfo(
    /** The type of group (e.g., "team", "organization") */
    val type: String,
    
    /** The unique identifier for the group */
    val id: String,
    
    /** The display name of the group */
    val name: String
)

// MARK: - Event Names

/**
 * Standard event names emitted by the Vortex SDK.
 *
 * These events are available via the `onEvent` callback and can be used
 * to track user interactions with the invitation widget.
 */
enum class VortexEventName(val eventName: String) {
    /** Emitted when the widget is rendered successfully. */
    WIDGET_RENDER("widget_render"),
    
    /** Emitted when there's an error rendering the widget. */
    WIDGET_ERROR("widget_error"),
    
    /** Emitted when email invitations are submitted. */
    EMAIL_INVITATIONS_SUBMITTED("email_invitations_submitted"),
    
    /** Emitted when the email field receives focus. */
    WIDGET_EMAIL_FIELD_FOCUS("widget_email_field_focus"),
    
    /** Emitted when the email field loses focus. */
    WIDGET_EMAIL_FIELD_BLUR("widget_email_field_blur"),
    
    /** Emitted when email validation occurs. */
    WIDGET_EMAIL_VALIDATION("widget_email_validation"),
    
    /** Emitted when a share link is clicked. */
    WIDGET_SHARE_LINK_CLICK("widget_share_link_click"),
    
    /** Emitted when there's a validation error on form submission. */
    WIDGET_EMAIL_VALIDATION_ERROR("widget_email_validation_error"),
    
    /** Emitted when there's an error submitting the email form. */
    WIDGET_EMAIL_SUBMIT_ERROR("widget_email_submit_error"),

    /** Emitted when the user clicks the link to view the contacts list. */
    CONTACTS_LINK_CLICKED("contacts_link_clicked"),

    /** Emitted when the user clicks the button to invite a specific contact. */
    CONTACTS_INVITE_BUTTON_CLICKED("contactsInvite_button_clicked"),

    /** Emitted when a list of found friends is displayed. Payload includes the number of friends. */
    FIND_FRIENDS_LIST_DISPLAYED("findFriends_list_displayed")
}

// MARK: - Analytics Client

/**
 * Client for sending analytics events to the Vortex backend.
 *
 * This client handles the network communication for tracking events.
 * Events are sent asynchronously and failures are handled silently
 * to ensure analytics never impact the user experience.
 */
class VortexAnalyticsClient(
    private val baseURL: String,
    private val sessionId: String,
    private var jwt: String?
) {
    companion object {
        /** Default production analytics collector URL */
        const val DEFAULT_ANALYTICS_URL = "https://collector.vortexsoftware.com"
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Updates the JWT token used for authentication.
     * @param jwt The new JWT token
     */
    fun updateJWT(jwt: String?) {
        this.jwt = jwt
    }
    
    /**
     * Tracks an analytics event by sending it to the backend.
     *
     * This method is fire-and-forget; it does not wait for a response
     * and silently handles any errors to avoid impacting the user experience.
     *
     * @param event The event to track
     */
    fun track(event: VortexAnalyticsEvent) {
        val currentJwt = jwt
        if (currentJwt == null) {
            if (android.util.Log.isLoggable("VortexSDK", android.util.Log.DEBUG)) {
                android.util.Log.d("VortexSDK", "Analytics: Skipping event '${event.name}' - no JWT token")
            }
            return
        }
        
        scope.launch {
            try {
                val url = "${baseURL.trimEnd('/')}/api/v1/events"
                val jsonBody = Json.encodeToString(event.toJsonObject())
                
                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", "Bearer $currentJwt")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("x-session-id", sessionId)
                    .addHeader("x-vortex-client-name", "VortexSDK-Android")
                    .build()
                
                if (android.util.Log.isLoggable("VortexSDK", android.util.Log.DEBUG)) {
                    android.util.Log.d("VortexSDK", "Analytics: Tracking event '${event.name}'")
                }
                
                // Fire and forget
                httpClient.newCall(request).execute().close()
            } catch (e: Exception) {
                // Silently handle errors - analytics should never impact user experience
                if (android.util.Log.isLoggable("VortexSDK", android.util.Log.DEBUG)) {
                    android.util.Log.d("VortexSDK", "Analytics: Failed to track event '${event.name}': ${e.message}")
                }
            }
        }
    }
}

// MARK: - Device Info

/**
 * Helper for generating device information for analytics.
 */
object VortexDeviceInfo {
    
    /**
     * Generates a user agent string for the current Android device.
     *
     * Format: `VortexSDK-Android/1.0.0 (Android <version>; <model>)`
     * Example: `VortexSDK-Android/1.0.0 (Android 14; Pixel 8)`
     */
    val useragent: String
        get() {
            val osVersion = Build.VERSION.RELEASE
            val model = Build.MODEL
            val sdkVersion = VortexSDK.VERSION
            return "VortexSDK-Android/$sdkVersion (Android $osVersion; $model)"
        }
}

// MARK: - JWT Parsing

/**
 * Helper for extracting information from JWT tokens.
 */
object VortexJWTParser {
    
    /**
     * Extracts the foreign user ID from a JWT token.
     *
     * The function looks for common user ID claims in the following order:
     * 1. `userId` - Vortex's standard claim
     * 2. `sub` - Standard JWT subject claim
     * 3. `user_id` - Alternative format
     *
     * @param jwt The JWT token string
     * @return The user ID if found, null otherwise
     */
    fun extractForeignUserId(jwt: String?): String? {
        if (jwt == null) return null
        
        try {
            // Handle raw-data format (insecure JWT for development)
            if (jwt.startsWith("raw-data:")) {
                val base64Part = jwt.removePrefix("raw-data:")
                val decodedBytes = Base64.decode(base64Part, Base64.DEFAULT)
                val jsonString = String(decodedBytes, Charsets.UTF_8)
                val json = Json.parseToJsonElement(jsonString).jsonObject
                
                return json["userId"]?.jsonPrimitive?.contentOrNull
                    ?: json["user_id"]?.jsonPrimitive?.contentOrNull
            }
            
            // Standard JWT format: header.payload.signature
            val parts = jwt.split(".")
            if (parts.size < 2) return null
            
            // Decode the payload (second part) using URL-safe Base64
            val payload = parts[1]
            val decodedBytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val jsonString = String(decodedBytes, Charsets.UTF_8)
            val json = Json.parseToJsonElement(jsonString).jsonObject
            
            // Try common user ID claims
            return json["userId"]?.jsonPrimitive?.contentOrNull
                ?: json["sub"]?.jsonPrimitive?.contentOrNull
                ?: json["user_id"]?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            if (android.util.Log.isLoggable("VortexSDK", android.util.Log.DEBUG)) {
                android.util.Log.d("VortexSDK", "JWT parsing failed: ${e.message}")
            }
            return null
        }
    }
}

// MARK: - Helper Extensions

/**
 * Converts a Map<String, Any> to a JsonElement for serialization.
 */
private fun Map<String, Any>.toJsonElement(): JsonElement {
    return buildJsonObject {
        forEach { (key, value) ->
            put(key, value.toJsonElement())
        }
    }
}

/**
 * Converts any value to a JsonElement.
 */
private fun Any?.toJsonElement(): JsonElement {
    return when (this) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Map<*, *> -> buildJsonObject {
            @Suppress("UNCHECKED_CAST")
            (this@toJsonElement as Map<String, Any?>).forEach { (key, value) ->
                put(key, value.toJsonElement())
            }
        }
        is List<*> -> buildJsonArray {
            this@toJsonElement.forEach { add(it.toJsonElement()) }
        }
        is Array<*> -> buildJsonArray {
            this@toJsonElement.forEach { add(it.toJsonElement()) }
        }
        else -> JsonPrimitive(this.toString())
    }
}
