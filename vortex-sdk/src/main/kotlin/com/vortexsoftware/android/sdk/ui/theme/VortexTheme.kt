package com.vortexsoftware.android.sdk.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vortexsoftware.android.sdk.models.BackgroundStyle
import com.vortexsoftware.android.sdk.models.GradientColorStop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vortex SDK color constants
 */
object VortexColors {
    val Gray66 = Color(0xFF666666)
    val Gray33 = Color(0xFF333333)
    val GrayE0 = Color(0xFFE0E0E0)
    val GrayF5 = Color(0xFFF5F5F5)
    val White = Color.White
    val Black = Color.Black
    val Transparent = Color.Transparent
    val Red = Color(0xFFE53935)
    val RedLight = Color(0x1AE53935)
}

/**
 * Convert a Long color value (ARGB) to Compose Color
 */
fun Long.toComposeColor(): Color = Color(this.toInt())

/**
 * Convert CSS angle (in degrees) to Compose gradient start/end offsets
 * CSS: 0deg = to top, 90deg = to right, 180deg = to bottom, 270deg = to left
 */
fun angleToGradientOffsets(degrees: Float): Pair<Offset, Offset> {
    val radians = (degrees - 90) * PI.toFloat() / 180f
    
    val x = cos(radians)
    val y = sin(radians)
    
    // Normalize to 0-1 range
    val startX = 0.5f - x * 0.5f
    val startY = 0.5f + y * 0.5f
    val endX = 0.5f + x * 0.5f
    val endY = 0.5f - y * 0.5f
    
    return Pair(
        Offset(startX, startY),
        Offset(endX, endY)
    )
}

/**
 * Create a Compose Brush from a list of gradient color stops
 */
fun createGradientBrush(angle: Float, colorStops: List<GradientColorStop>): Brush {
    val (start, end) = angleToGradientOffsets(angle)
    
    return Brush.linearGradient(
        colorStops = colorStops.map { stop ->
            stop.location to stop.color.toComposeColor()
        }.toTypedArray(),
        start = Offset(start.x * 1000f, start.y * 1000f),
        end = Offset(end.x * 1000f, end.y * 1000f)
    )
}

/**
 * Extension to apply BackgroundStyle to a Modifier
 */
@Composable
fun Modifier.backgroundStyle(
    style: BackgroundStyle?,
    shape: RoundedCornerShape = RoundedCornerShape(10.dp)
): Modifier {
    return when (style) {
        is BackgroundStyle.Solid -> {
            this
                .clip(shape)
                .background(style.color.toComposeColor(), shape)
        }
        is BackgroundStyle.Gradient -> {
            val brush = createGradientBrush(style.angle, style.colorStops)
            this
                .clip(shape)
                .background(brush, shape)
        }
        null -> {
            this
                .clip(shape)
                .background(VortexColors.GrayF5, shape)
        }
    }
}

/**
 * Default button background modifier
 */
@Composable
fun Modifier.vortexButtonBackground(
    backgroundStyle: BackgroundStyle? = null,
    cornerRadius: Int = 10
): Modifier {
    val shape = RoundedCornerShape(cornerRadius.dp)
    return this.backgroundStyle(backgroundStyle, shape)
}

/**
 * Rounded corner shape for specific corners
 */
fun topRoundedCornerShape(radius: Int = 20): RoundedCornerShape {
    return RoundedCornerShape(
        topStart = radius.dp,
        topEnd = radius.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )
}
