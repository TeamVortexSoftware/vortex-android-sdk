package com.vortexsoftware.android.sdk.ui.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vortexsoftware.android.sdk.models.ElementNode
import com.vortexsoftware.android.sdk.models.Theme
import com.vortexsoftware.android.sdk.models.parseHexColor
import com.vortexsoftware.android.sdk.ui.icons.VortexIcon
import com.vortexsoftware.android.sdk.ui.icons.VortexIconName
import com.vortexsoftware.android.sdk.ui.theme.VortexColors
import com.vortexsoftware.android.sdk.ui.theme.backgroundStyle
import com.vortexsoftware.android.sdk.ui.theme.toComposeColor
import com.vortexsoftware.android.sdk.viewmodels.VortexInviteViewModel

/**
 * Share options view that renders share buttons based on configuration
 */
@Composable
fun ShareOptionsView(
    block: ElementNode,
    viewModel: VortexInviteViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val copySuccess by viewModel.copySuccess.collectAsState()
    val loadingCopy by viewModel.loadingCopy.collectAsState()
    val shareSuccess by viewModel.shareSuccess.collectAsState()
    val loadingShare by viewModel.loadingShare.collectAsState()
    val config by viewModel.configuration.collectAsState()
    
    val theme = block.theme ?: config?.theme
    val labelColor = theme?.getOption("--color-foreground")?.let { parseHexColor(it)?.toComposeColor() } ?: VortexColors.Gray66
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section label from block attributes
        block.getString("label")?.let { label ->
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = labelColor
            )
        }
        
        // Render buttons in configuration order
        viewModel.shareOptions.forEach { option ->
            ShareButtonForOption(
                option = option,
                block = block,
                viewModel = viewModel,
                context = context,
                copySuccess = copySuccess,
                loadingCopy = loadingCopy,
                shareSuccess = shareSuccess,
                loadingShare = loadingShare
            )
        }
    }
}

@Composable
private fun ShareButtonForOption(
    option: String,
    block: ElementNode,
    viewModel: VortexInviteViewModel,
    context: Context,
    copySuccess: Boolean,
    loadingCopy: Boolean,
    shareSuccess: Boolean,
    loadingShare: Boolean
) {
    when (option) {
        "copyLink" -> ShareButton(
            icon = VortexIconName.LINK,
            title = if (copySuccess) "✓ Copied!" else "Copy Link",
            isLoading = loadingCopy,
            theme = block.theme,
            onClick = { viewModel.copyLink(context) }
        )
        "nativeShareSheet" -> ShareButton(
            icon = VortexIconName.SHARE,
            title = if (shareSuccess) "✓ Shared!" else "Share Invitation",
            isLoading = loadingShare,
            theme = block.theme,
            onClick = { viewModel.shareInvitation(context) }
        )
        "sms" -> ShareButton(
            icon = VortexIconName.SMS,
            title = "Share via SMS",
            theme = block.theme,
            onClick = { viewModel.shareViaSms(context) }
        )
        "qrCode" -> ShareButton(
            icon = VortexIconName.QR_CODE,
            title = "Show QR Code",
            theme = block.theme,
            onClick = { viewModel.showQrCode() }
        )
        "line" -> ShareButton(
            icon = VortexIconName.LINE,
            title = "Share via LINE",
            theme = block.theme,
            onClick = { viewModel.shareViaLine(context) }
        )
        "email" -> ShareButton(
            icon = VortexIconName.EMAIL,
            title = "Share via Email",
            theme = block.theme,
            onClick = { viewModel.shareViaEmail(context) }
        )
        "twitterDms" -> ShareButton(
            icon = VortexIconName.X_TWITTER,
            title = "Share via X",
            theme = block.theme,
            onClick = { viewModel.shareViaTwitter(context) }
        )
        "instagramDms" -> ShareButton(
            icon = VortexIconName.INSTAGRAM,
            title = "Share via Instagram",
            theme = block.theme,
            onClick = { viewModel.shareViaInstagram(context) }
        )
        "whatsApp" -> ShareButton(
            icon = VortexIconName.WHATSAPP,
            title = "Share via WhatsApp",
            theme = block.theme,
            onClick = { viewModel.shareViaWhatsApp(context) }
        )
        "facebookMessenger" -> ShareButton(
            icon = VortexIconName.FACEBOOK_MESSENGER,
            title = "Share via Messenger",
            theme = block.theme,
            onClick = { viewModel.shareViaFacebookMessenger(context) }
        )
        "telegram" -> ShareButton(
            icon = VortexIconName.TELEGRAM,
            title = "Share via Telegram",
            theme = block.theme,
            onClick = { viewModel.shareViaTelegram(context) }
        )
        "discord" -> ShareButton(
            icon = VortexIconName.DISCORD,
            title = "Share via Discord",
            theme = block.theme,
            onClick = { viewModel.shareViaDiscord(context) }
        )
    }
}

/**
 * Individual share button component
 */
@Composable
fun ShareButton(
    icon: VortexIconName,
    title: String,
    isLoading: Boolean = false,
    theme: Theme? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundStyle = theme?.buttonBackgroundStyle
    val foregroundColor = theme?.buttonTextColor?.let { Color(it.toInt()) } ?: VortexColors.Gray33
    
    Button(
        onClick = onClick,
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = foregroundColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = foregroundColor.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier
            .fillMaxWidth()
            .backgroundStyle(backgroundStyle)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = foregroundColor,
                    strokeWidth = 2.dp
                )
            } else {
                Box(
                    modifier = Modifier.size(width = 24.dp, height = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    VortexIcon(
                        name = icon,
                        size = 18,
                        color = foregroundColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Email pill view for displaying added emails
 */
@Composable
fun EmailPillView(
    email: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = VortexColors.GrayF5,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = email,
                fontSize = 14.sp,
                color = VortexColors.Gray33
            )
            
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                VortexIcon(
                    name = VortexIconName.CLOSE,
                    size = 14,
                    color = VortexColors.Gray66
                )
            }
        }
    }
}
