package com.vortexsoftware.android.sdk.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.vortexsoftware.android.sdk.R

/**
 * Icon names used in the Vortex SDK (matching iOS/React Native SDK)
 */
enum class VortexIconName {
    CLOSE,
    ARROW_BACK,
    LINK,
    SHARE,
    IMPORT_CONTACTS,
    EMAIL,
    GOOGLE,
    X_TWITTER,
    INSTAGRAM,
    SMS,
    WHATSAPP,
    QR_CODE,
    LINE,
    TELEGRAM,
    DISCORD,
    FACEBOOK_MESSENGER
}

/**
 * FontAwesome icon style
 */
enum class FontAwesomeStyle {
    SOLID,
    REGULAR,
    BRANDS
}

/**
 * FontAwesome 6 icon unicode values
 */
object FontAwesome6Icons {
    // UI Icons (Solid)
    const val XMARK = "\uf00d"
    const val ARROW_LEFT = "\uf060"
    const val LINK = "\uf0c1"
    const val SHARE_NODES = "\uf1e0"
    const val ADDRESS_BOOK = "\uf2b9"
    const val ENVELOPE = "\uf0e0"
    const val COMMENT = "\uf075"
    const val QRCODE = "\uf029"
    
    // Brand Icons
    const val GOOGLE = "\uf1a0"
    const val X_TWITTER = "\ue61b"
    const val INSTAGRAM = "\uf16d"
    const val WHATSAPP = "\uf232"
    const val LINE = "\uf3c0"
    const val TELEGRAM = "\uf2c6"
    const val DISCORD = "\uf392"
    const val FACEBOOK_MESSENGER = "\uf39f"
}

/**
 * Get FontAwesome icon info (unicode and style) for a VortexIconName
 */
fun VortexIconName.toFontAwesome(): Pair<String, FontAwesomeStyle> {
    return when (this) {
        VortexIconName.CLOSE -> FontAwesome6Icons.XMARK to FontAwesomeStyle.SOLID
        VortexIconName.ARROW_BACK -> FontAwesome6Icons.ARROW_LEFT to FontAwesomeStyle.SOLID
        VortexIconName.LINK -> FontAwesome6Icons.LINK to FontAwesomeStyle.SOLID
        VortexIconName.SHARE -> FontAwesome6Icons.SHARE_NODES to FontAwesomeStyle.SOLID
        VortexIconName.IMPORT_CONTACTS -> FontAwesome6Icons.ADDRESS_BOOK to FontAwesomeStyle.SOLID
        VortexIconName.EMAIL -> FontAwesome6Icons.ENVELOPE to FontAwesomeStyle.SOLID
        VortexIconName.GOOGLE -> FontAwesome6Icons.GOOGLE to FontAwesomeStyle.BRANDS
        VortexIconName.X_TWITTER -> FontAwesome6Icons.X_TWITTER to FontAwesomeStyle.BRANDS
        VortexIconName.INSTAGRAM -> FontAwesome6Icons.INSTAGRAM to FontAwesomeStyle.BRANDS
        VortexIconName.SMS -> FontAwesome6Icons.COMMENT to FontAwesomeStyle.SOLID
        VortexIconName.WHATSAPP -> FontAwesome6Icons.WHATSAPP to FontAwesomeStyle.BRANDS
        VortexIconName.QR_CODE -> FontAwesome6Icons.QRCODE to FontAwesomeStyle.SOLID
        VortexIconName.LINE -> FontAwesome6Icons.LINE to FontAwesomeStyle.BRANDS
        VortexIconName.TELEGRAM -> FontAwesome6Icons.TELEGRAM to FontAwesomeStyle.BRANDS
        VortexIconName.DISCORD -> FontAwesome6Icons.DISCORD to FontAwesomeStyle.BRANDS
        VortexIconName.FACEBOOK_MESSENGER -> FontAwesome6Icons.FACEBOOK_MESSENGER to FontAwesomeStyle.BRANDS
    }
}

/**
 * Get Material Icon fallback for a VortexIconName
 */
fun VortexIconName.toMaterialIcon(): ImageVector {
    return when (this) {
        VortexIconName.CLOSE -> Icons.Default.Close
        VortexIconName.ARROW_BACK -> Icons.AutoMirrored.Filled.ArrowBack
        VortexIconName.LINK -> Icons.Default.Link
        VortexIconName.SHARE -> Icons.Default.Share
        VortexIconName.IMPORT_CONTACTS -> Icons.Default.PersonAdd
        VortexIconName.EMAIL -> Icons.Default.Email
        VortexIconName.GOOGLE -> Icons.Default.AccountCircle
        VortexIconName.X_TWITTER -> Icons.Default.AlternateEmail
        VortexIconName.INSTAGRAM -> Icons.Default.CameraAlt
        VortexIconName.SMS -> Icons.AutoMirrored.Filled.Message
        VortexIconName.WHATSAPP -> Icons.Default.Phone
        VortexIconName.QR_CODE -> Icons.Default.QrCode
        VortexIconName.LINE -> Icons.Default.Chat
        VortexIconName.TELEGRAM -> Icons.AutoMirrored.Filled.Send
        VortexIconName.DISCORD -> Icons.Default.SportsEsports
        VortexIconName.FACEBOOK_MESSENGER -> Icons.Default.Message
    }
}

/**
 * FontAwesome font families
 */
object FontAwesomeFonts {
    val Solid: FontFamily by lazy {
        try {
            FontFamily(Font(R.font.fa_solid_900))
        } catch (e: Exception) {
            FontFamily.Default
        }
    }
    
    val Regular: FontFamily by lazy {
        try {
            FontFamily(Font(R.font.fa_regular_400))
        } catch (e: Exception) {
            FontFamily.Default
        }
    }
    
    val Brands: FontFamily by lazy {
        try {
            FontFamily(Font(R.font.fa_brands_400))
        } catch (e: Exception) {
            FontFamily.Default
        }
    }
    
    fun forStyle(style: FontAwesomeStyle): FontFamily {
        return when (style) {
            FontAwesomeStyle.SOLID -> Solid
            FontAwesomeStyle.REGULAR -> Regular
            FontAwesomeStyle.BRANDS -> Brands
        }
    }
}

/**
 * A composable that renders FontAwesome 6 icons for the Vortex SDK
 * Falls back to Material Icons if FontAwesome fonts are not available
 */
@Composable
fun VortexIcon(
    name: VortexIconName,
    modifier: Modifier = Modifier,
    size: Int = 18,
    color: Color = Color.Unspecified
) {
    val (unicode, style) = name.toFontAwesome()
    val fontFamily = FontAwesomeFonts.forStyle(style)
    
    // Try to use FontAwesome font
    if (fontFamily != FontFamily.Default) {
        Text(
            text = unicode,
            modifier = modifier,
            fontFamily = fontFamily,
            fontSize = size.sp,
            color = color
        )
    } else {
        // Fallback to Material Icons
        Icon(
            imageVector = name.toMaterialIcon(),
            contentDescription = name.name,
            modifier = modifier,
            tint = color
        )
    }
}
