package com.vortexsoftware.android.sdk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.vortexsoftware.android.sdk.models.*
import com.vortexsoftware.android.sdk.ui.theme.toComposeColor

// Default colors matching iOS SDK
internal val DefaultPrimaryBackground = Color(0xFF6291d5)
internal val DefaultPrimaryForeground = Color.White
internal val DefaultSecondaryBackground = Color.White
internal val DefaultSecondaryForeground = Color(0xFF353e5c)
internal val DefaultForeground = Color(0xFF334153)

/**
 * Convert a BackgroundStyle to a Compose Brush
 */
internal fun BackgroundStyle.toBrush(): Brush {
    return when (this) {
        is BackgroundStyle.Solid -> Brush.linearGradient(listOf(color.toComposeColor(), color.toComposeColor()))
        is BackgroundStyle.Gradient -> {
            // Convert CSS angle to Compose gradient direction
            // CSS: 0deg = bottom to top, 90deg = left to right
            // We need to calculate start and end points based on angle
            val angleRad = Math.toRadians(angle.toDouble())
            val colors = colorStops.sortedBy { it.location }.map { it.color.toComposeColor() }
            Brush.linearGradient(colors)
        }
    }
}

/**
 * Parse a background style string (solid color or gradient)
 */
internal fun parseBackgroundStyle(value: String?): BackgroundStyle? {
    if (value.isNullOrBlank()) return null
    return BackgroundStyle.parse(value)
}

/**
 * Extension to convert Long color to Compose Color
 */
internal fun Long.toComposeColor(): Color = Color(this)

/**
 * Helper to parse hex color string to Color
 */
internal fun parseColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try {
        val cleanHex = hex.removePrefix("#")
        when (cleanHex.length) {
            6 -> Color(android.graphics.Color.parseColor("#$cleanHex"))
            8 -> Color(android.graphics.Color.parseColor("#$cleanHex"))
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Helper to parse font weight string to FontWeight
 */
internal fun parseFontWeight(weight: String?): FontWeight? {
    return when (weight?.lowercase()) {
        "100", "thin" -> FontWeight.Thin
        "200", "extralight" -> FontWeight.ExtraLight
        "300", "light" -> FontWeight.Light
        "400", "normal", "regular" -> FontWeight.Normal
        "500", "medium" -> FontWeight.Medium
        "600", "semibold" -> FontWeight.SemiBold
        "700", "bold" -> FontWeight.Bold
        "800", "extrabold" -> FontWeight.ExtraBold
        "900", "black" -> FontWeight.Black
        else -> null
    }
}

/**
 * Helper to parse font size string (e.g., "16px") to sp value
 */
internal fun parseFontSize(size: String?): Float? {
    if (size.isNullOrBlank()) return null
    return size.replace("px", "").replace("sp", "").trim().toFloatOrNull()
}

/**
 * Helper to parse border radius string (e.g., "8px") to dp value
 */
internal fun parseBorderRadius(radius: String?): Float? {
    if (radius.isNullOrBlank()) return null
    return radius.replace("px", "").replace("dp", "").trim().toFloatOrNull()
}

// ============================================================================
// Shared Components
// ============================================================================

/**
 * Avatar view with image loading and initials fallback
 * Uses Coil for async image loading, matching iOS SDK's AsyncImage behavior
 */
@Composable
internal fun AvatarView(
    name: String,
    avatarUrl: String?,
    backgroundColor: Color,
    textColor: Color,
    size: Int = 44
) {
    val initials = name
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
    
    if (!avatarUrl.isNullOrBlank()) {
        // Load image from URL with initials as fallback
        SubcomposeAsyncImage(
            model = avatarUrl,
            contentDescription = "$name avatar",
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Success -> {
                    SubcomposeAsyncImageContent()
                }
                is AsyncImagePainter.State.Loading -> {
                    // Show initials while loading
                    InitialsAvatar(initials, backgroundColor, textColor, size)
                }
                else -> {
                    // Show initials on error or empty
                    InitialsAvatar(initials, backgroundColor, textColor, size)
                }
            }
        }
    } else {
        // No URL provided, show initials
        InitialsAvatar(initials, backgroundColor, textColor, size)
    }
}

/**
 * Initials-based avatar fallback
 */
@Composable
internal fun InitialsAvatar(
    initials: String,
    backgroundColor: Color,
    textColor: Color,
    size: Int
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.ifEmpty { "?" },
            fontSize = (size / 2.75).sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

// ============================================================================
// Extension Functions
// ============================================================================

internal fun com.vortexsoftware.android.sdk.api.dto.OutgoingInvitation.toDisplayItem(): OutgoingInvitationItem {
    val target = targets?.firstOrNull()
    val targetName = target?.targetName
    val targetValue = target?.targetValue
    
    val displayName = targetName ?: targetValue ?: senderIdentifier ?: "Unknown"
    val subtitle = targetValue ?: senderIdentifier
    val metadataMap = metadata?.mapValues { (_, v) -> v.jsonPrimitiveOrNull()?.content ?: v.toString() }
    
    return OutgoingInvitationItem(
        id = id,
        name = displayName,
        subtitle = subtitle,
        avatarUrl = target?.targetAvatarUrl ?: avatarUrl,
        isVortexInvitation = true,
        invitation = this,
        metadata = metadataMap
    )
}

internal fun com.vortexsoftware.android.sdk.api.dto.IncomingInvitation.toDisplayItem(): IncomingInvitationItem {
    val target = targets?.firstOrNull()
    val targetName = target?.targetName
    val targetValue = target?.targetValue
    
    val displayName = targetName ?: senderIdentifier ?: "Unknown"
    val subtitle = targetValue ?: senderIdentifier
    val metadataMap = metadata?.mapValues { (_, v) -> v.jsonPrimitiveOrNull()?.content ?: v.toString() }
    
    return IncomingInvitationItem(
        id = id,
        name = displayName,
        subtitle = subtitle,
        avatarUrl = avatarUrl,
        isVortexInvitation = true,
        invitation = this,
        metadata = metadataMap
    )
}

internal fun kotlinx.serialization.json.JsonElement.jsonPrimitiveOrNull(): kotlinx.serialization.json.JsonPrimitive? {
    return this as? kotlinx.serialization.json.JsonPrimitive
}
