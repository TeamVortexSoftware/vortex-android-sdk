package com.vortexsoftware.android.sdk

/**
 * Vortex Android SDK
 * 
 * An invitations-as-a-service SDK that renders dynamic invitation forms
 * configured via the Vortex backend API.
 * 
 * ## Quick Start
 * 
 * ```kotlin
 * // In your Composable
 * VortexInviteView(
 *     componentId = "your-widget-id",
 *     jwt = "optional-jwt-token",
 *     onDismiss = { /* handle dismiss */ }
 * )
 * ```
 * 
 * ## Features
 * - Dynamic form rendering from backend configuration
 * - Multiple invitation methods (email, SMS, shareable links, QR codes)
 * - Social sharing (WhatsApp, Telegram, LINE, Twitter/X, etc.)
 * - Contact import (device contacts, Google Contacts)
 * - Theming support with gradients
 * 
 * @see com.vortexsoftware.android.sdk.ui.VortexInviteView
 */
object VortexSDK {
    /**
     * SDK version
     */
    const val VERSION = "1.0.0"
    
    /**
     * SDK name
     */
    const val NAME = "VortexSDK"
}
