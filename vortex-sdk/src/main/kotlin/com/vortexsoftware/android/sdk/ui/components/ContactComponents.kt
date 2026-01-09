package com.vortexsoftware.android.sdk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vortexsoftware.android.sdk.models.*
import com.vortexsoftware.android.sdk.ui.icons.VortexIcon
import com.vortexsoftware.android.sdk.ui.icons.VortexIconName
import com.vortexsoftware.android.sdk.ui.theme.VortexColors
import com.vortexsoftware.android.sdk.ui.theme.toComposeColor
import com.vortexsoftware.android.sdk.viewmodels.VortexInviteViewModel

/**
 * Contacts import view with buttons for native/Google contacts and email
 */
@Composable
fun ContactsImportView(
    block: ElementNode,
    viewModel: VortexInviteViewModel,
    modifier: Modifier = Modifier
) {
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
        
        // Import from Contacts button
        if (viewModel.isNativeContactsEnabled) {
            ShareButton(
                icon = VortexIconName.IMPORT_CONTACTS,
                title = block.getCustomButtonLabel("importContacts") ?: "Add from Contacts",
                theme = block.theme,
                onClick = { viewModel.selectFromContacts() }
            )
        }
        
        // Import from Google Contacts button
        if (viewModel.isGoogleContactsEnabled) {
            ShareButton(
                icon = VortexIconName.GOOGLE,
                title = block.getCustomButtonLabel("googleContacts") ?: "Add from Google Contacts",
                theme = block.theme,
                onClick = { viewModel.selectFromGoogleContacts() }
            )
        }
        
        // Add by Email button (navigates to email entry view)
        ShareButton(
            icon = VortexIconName.EMAIL,
            title = block.getCustomButtonLabel("addByEmail") ?: "Add by Email",
            theme = block.theme,
            onClick = { viewModel.navigateTo(InviteViewState.EMAIL_ENTRY) }
        )
    }
}

/**
 * Contact row view displaying a contact with an Invite button
 */
@Composable
fun ContactRowView(
    contact: VortexContact,
    inviteState: ContactInviteState?,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isInvited = inviteState?.isInvited == true
    val isLoading = inviteState?.isLoading == true
    val errorMessage = inviteState?.errorMessage
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Contact info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = contact.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = VortexColors.Gray33,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = contact.email,
                fontSize = 13.sp,
                color = VortexColors.Gray66,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Show error message if present
            errorMessage?.let { error ->
                Text(
                    text = error,
                    fontSize = 11.sp,
                    color = VortexColors.Red,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Invite button, Invited status, or Error with Retry
        when {
            isInvited -> {
                Text(
                    text = "✓ Invited!",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VortexColors.Gray66
                )
            }
            errorMessage != null -> {
                // Show Retry button on error
                Button(
                    onClick = onInvite,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VortexColors.RedLight,
                        contentColor = VortexColors.Red
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 80.dp)
                        .border(1.dp, VortexColors.Red.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = VortexColors.Red,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Retry",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            else -> {
                Button(
                    onClick = onInvite,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VortexColors.GrayF5,
                        contentColor = VortexColors.Gray33
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 80.dp)
                        .border(1.dp, VortexColors.GrayE0, RoundedCornerShape(6.dp))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = VortexColors.Gray33,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Invite",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Contact list view showing all selected contacts
 */
@Composable
fun ContactListView(
    contacts: List<VortexContact>,
    inviteStates: Map<String, ContactInviteState>,
    onInvite: (VortexContact) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        contacts.forEach { contact ->
            ContactRowView(
                contact = contact,
                inviteState = inviteStates[contact.id],
                onInvite = { onInvite(contact) }
            )
            
            if (contact != contacts.last()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = VortexColors.GrayE0
                )
            }
        }
    }
}

/**
 * Email entry view for manually adding email addresses
 */
@Composable
fun EmailEntryView(
    emailInput: String,
    addedEmails: List<String>,
    onEmailInputChange: (String) -> Unit,
    onAddEmail: () -> Unit,
    onRemoveEmail: (String) -> Unit,
    onSendInvitations: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Email input field
        OutlinedTextField(
            value = emailInput,
            onValueChange = onEmailInputChange,
            placeholder = { Text("Enter email address", color = VortexColors.Gray66) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VortexColors.Gray66,
                unfocusedBorderColor = VortexColors.GrayE0
            ),
            trailingIcon = {
                if (emailInput.isNotBlank()) {
                    TextButton(onClick = onAddEmail) {
                        Text("Add", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        )
        
        // Added emails as pills
        if (addedEmails.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Recipients:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = VortexColors.Gray66
                )
                
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    addedEmails.forEach { email ->
                        EmailPillView(
                            email = email,
                            onRemove = { onRemoveEmail(email) }
                        )
                    }
                }
            }
        }
        
        // Send invitations button
        if (addedEmails.isNotEmpty()) {
            Button(
                onClick = onSendInvitations,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Send ${addedEmails.size} Invitation${if (addedEmails.size > 1) "s" else ""}",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Flow row layout for wrapping items
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    // Using Compose's built-in FlowRow from foundation
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}
