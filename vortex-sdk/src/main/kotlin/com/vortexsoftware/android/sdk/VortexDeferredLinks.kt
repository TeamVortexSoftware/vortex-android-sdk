package com.vortexsoftware.android.sdk

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.WindowManager
import com.vortexsoftware.android.sdk.api.VortexClient
import com.vortexsoftware.android.sdk.api.dto.DeviceFingerprint
import com.vortexsoftware.android.sdk.api.dto.MatchFingerprintResponse
import java.util.Locale
import java.util.TimeZone

/**
 * Provides functionality for deferred deep linking through fingerprint matching.
 *
 * Deferred deep linking allows the app to retrieve invitation context even when the user
 * installs the app after clicking an invitation link. The server stores a fingerprint of
 * the user's device when they click the link, and this class provides methods to match
 * that fingerprint after app installation.
 *
 * ## Usage
 *
 * Call [retrieveDeferredDeepLink] when the user signs in or when the app session is restored
 * to check if there's a pending invitation from before the app was installed.
 *
 * ```kotlin
 * val result = VortexDeferredLinks.retrieveDeferredDeepLink(context, jwt)
 * result.onSuccess { response ->
 *     if (response.matched && response.context != null) {
 *         println("Found invitation: ${response.context.invitationId}")
 *         // Handle the deferred deep link
 *     }
 * }
 * ```
 *
 * The account ID is derived from the JWT token, so account-wide matching is performed
 * across all components/widgets for the account.
 */
object VortexDeferredLinks {
    
    private const val DEFAULT_BASE_URL = "https://client-api.vortexsoftware.com"
    
    /**
     * Retrieves deferred deep link context by matching the device fingerprint.
     *
     * Call this method when the user signs in or when the app session is restored
     * to check if there's a pending invitation from before the app was installed.
     *
     * @param context Android context for collecting device information
     * @param jwt JWT authentication token for the current user
     * @param baseUrl Base URL of the Vortex API (defaults to production). Only override for development/staging.
     * @return Result containing match response with invitation context if found
     */
    suspend fun retrieveDeferredDeepLink(
        context: Context,
        jwt: String,
        baseUrl: String = DEFAULT_BASE_URL
    ): Result<MatchFingerprintResponse> {
        val fingerprint = collectDeviceFingerprint(context)
        
        val client = VortexClient(
            baseUrl = baseUrl,
            jwt = jwt,
            clientName = VortexSDK.NAME,
            clientVersion = VortexSDK.VERSION
        )
        
        return client.matchFingerprint(fingerprint)
    }
    
    /**
     * Collects device fingerprint data from the current device.
     *
     * This method gathers various device characteristics that can be used
     * to match against fingerprints stored on the server.
     *
     * @param context Android context for accessing device information
     * @return DeviceFingerprint containing device characteristics
     */
    fun collectDeviceFingerprint(context: Context): DeviceFingerprint {
        // Get OS version
        val osVersion = Build.VERSION.RELEASE
        
        // Get device model (e.g., "Pixel 6", "SM-G998B")
        val deviceModel = Build.MODEL
        
        // Get device brand/manufacturer (e.g., "Google", "Samsung")
        val deviceBrand = Build.MANUFACTURER
        
        // Get timezone
        val timezone = TimeZone.getDefault().id
        
        // Get language
        val language = Locale.getDefault().toLanguageTag()
        
        // Get screen dimensions
        val (screenWidth, screenHeight) = getScreenDimensions(context)
        
        // Get carrier name (if available)
        val carrierName = getCarrierName(context)
        
        // Get total memory
        val totalMemory = Runtime.getRuntime().maxMemory()
        
        return DeviceFingerprint(
            platform = "android",
            osVersion = osVersion,
            deviceModel = deviceModel,
            deviceBrand = deviceBrand,
            timezone = timezone,
            language = language,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            carrierName = carrierName,
            totalMemory = totalMemory
        )
    }
    
    /**
     * Gets the screen dimensions in pixels
     */
    private fun getScreenDimensions(context: Context): Pair<Int, Int> {
        return try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = windowManager.currentWindowMetrics.bounds
                Pair(bounds.width(), bounds.height())
            } else {
                @Suppress("DEPRECATION")
                val displayMetrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(displayMetrics)
                Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
            }
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }
    
    /**
     * Gets the carrier name if available
     */
    private fun getCarrierName(context: Context): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            telephonyManager?.networkOperatorName?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}
