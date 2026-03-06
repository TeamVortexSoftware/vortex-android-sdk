package com.vortexsoftware.android.sdk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vortexsoftware.android.sdk.models.ElementNode
import com.vortexsoftware.android.sdk.models.Theme
import com.vortexsoftware.android.sdk.models.parseHexColor
import com.vortexsoftware.android.sdk.ui.theme.VortexColors
import com.vortexsoftware.android.sdk.ui.theme.backgroundStyle
import com.vortexsoftware.android.sdk.ui.theme.toComposeColor
import com.vortexsoftware.android.sdk.viewmodels.VortexInviteViewModel

/**
 * Heading element (h1-h6)
 */
@Composable
fun HeadingView(
    block: ElementNode,
    modifier: Modifier = Modifier
) {
    val text = block.getString("text") ?: block.textContent ?: return
    val overrideTagName = block.settings?.get("overrideTagName")?.let {
        (it as? kotlinx.serialization.json.JsonPrimitive)?.content
    }
    val level = overrideTagName?.removePrefix("h")?.toIntOrNull()
        ?: block.subtype?.removePrefix("h")?.toIntOrNull()
        ?: 2
    val theme = block.theme
    val color = theme?.getOption("--color-foreground")?.let { parseHexColor(it)?.toComposeColor() } ?: VortexColors.Gray33
    
    val fontSize = when (level) {
        1 -> 28.sp
        2 -> 24.sp
        3 -> 20.sp
        4 -> 18.sp
        5 -> 16.sp
        else -> 14.sp
    }
    
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

/**
 * Text/paragraph element
 */
@Composable
fun TextView(
    block: ElementNode,
    modifier: Modifier = Modifier
) {
    val text = block.getString("text") ?: block.textContent ?: return
    val theme = block.theme
    val color = theme?.getOption("--color-foreground")?.let { parseHexColor(it)?.toComposeColor() } ?: VortexColors.Gray66
    
    Text(
        text = text,
        fontSize = 14.sp,
        color = color,
        modifier = modifier.padding(vertical = 4.dp)
    )
}

/**
 * Form label element
 */
@Composable
fun FormLabelView(
    block: ElementNode,
    modifier: Modifier = Modifier
) {
    val text = block.getString("text") ?: block.textContent ?: return
    val theme = block.theme
    val color = theme?.getOption("--color-foreground")?.let { parseHexColor(it)?.toComposeColor() } ?: VortexColors.Gray66
    
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = modifier.padding(bottom = 4.dp)
    )
}

/**
 * Image element
 */
@Composable
fun ImageView(
    block: ElementNode,
    modifier: Modifier = Modifier
) {
    val src = block.getString("src") ?: return
    val alt = block.getString("alt") ?: ""
    
    AsyncImage(
        model = src,
        contentDescription = alt,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}

/**
 * Link element
 */
@Composable
fun LinkView(
    block: ElementNode,
    modifier: Modifier = Modifier
) {
    val text = block.getString("text") ?: block.textContent ?: return
    val href = block.getString("href") ?: return
    val uriHandler = LocalUriHandler.current
    
    Text(
        text = text,
        fontSize = 14.sp,
        color = Color(0xFF2196F3),
        textDecoration = TextDecoration.Underline,
        modifier = modifier
            .clickable { uriHandler.openUri(href) }
            .padding(vertical = 4.dp)
    )
}

/**
 * Button element
 */
@Composable
fun ButtonView(
    block: ElementNode,
    theme: Theme?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val text = block.getString("text") ?: block.textContent ?: "Button"
    val backgroundStyle = theme?.buttonBackgroundStyle
    val textColor = theme?.buttonTextColor?.let { Color(it.toInt()) } ?: VortexColors.Gray33
    
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = textColor
        ),
        modifier = modifier
            .fillMaxWidth()
            .backgroundStyle(backgroundStyle)
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Divider element
 */
@Composable
fun DividerView(
    modifier: Modifier = Modifier
) {
    HorizontalDivider(
        modifier = modifier.padding(vertical = 12.dp),
        color = VortexColors.GrayE0
    )
}

/**
 * Text input field
 */
@Composable
fun TextboxView(
    block: ElementNode,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val placeholder = block.getString("placeholder") ?: ""
    val label = block.getString("label")
    val name = block.getString("name") ?: ""
    val inputType = block.getString("inputType") ?: "text"
    
    val keyboardType = when (inputType) {
        "email" -> KeyboardType.Email
        "number" -> KeyboardType.Number
        "phone" -> KeyboardType.Phone
        else -> KeyboardType.Text
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        label?.let {
            Text(
                text = it,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = VortexColors.Gray66,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = VortexColors.Gray66) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VortexColors.Gray66,
                unfocusedBorderColor = VortexColors.GrayE0
            )
        )
    }
}

/**
 * Textarea input field
 */
@Composable
fun TextareaView(
    block: ElementNode,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val placeholder = block.getString("placeholder") ?: ""
    val label = block.getString("label")
    val rows = block.getString("rows")?.toIntOrNull() ?: 4
    
    Column(modifier = modifier.fillMaxWidth()) {
        label?.let {
            Text(
                text = it,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = VortexColors.Gray66,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = VortexColors.Gray66) },
            minLines = rows,
            maxLines = rows,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VortexColors.Gray66,
                unfocusedBorderColor = VortexColors.GrayE0
            )
        )
    }
}

/**
 * Select/dropdown element
 */
@Composable
fun SelectView(
    block: ElementNode,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = block.getString("label")
    val placeholder = block.getString("placeholder") ?: "Select..."
    val options = block.children.mapNotNull { child ->
        val optionValue = child.getString("value")
        val optionLabel = child.getString("label") ?: optionValue
        if (optionValue != null && optionLabel != null) {
            optionValue to optionLabel
        } else null
    }
    
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == value }?.second ?: placeholder
    
    Column(modifier = modifier.fillMaxWidth()) {
        label?.let {
            Text(
                text = it,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = VortexColors.Gray66,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        
        @OptIn(ExperimentalMaterial3Api::class)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VortexColors.Gray66,
                    unfocusedBorderColor = VortexColors.GrayE0
                )
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (optionValue, optionLabel) ->
                    DropdownMenuItem(
                        text = { Text(optionLabel) },
                        onClick = {
                            onValueChange(optionValue)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Radio button group
 */
@Composable
fun RadioView(
    block: ElementNode,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = block.getString("label")
    val options = block.children.mapNotNull { child ->
        val optionValue = child.getString("value")
        val optionLabel = child.getString("label") ?: optionValue
        if (optionValue != null && optionLabel != null) {
            optionValue to optionLabel
        } else null
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        label?.let {
            Text(
                text = it,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = VortexColors.Gray66,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        Column(Modifier.selectableGroup()) {
            options.forEach { (optionValue, optionLabel) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = value == optionValue,
                            onClick = { onValueChange(optionValue) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = value == optionValue,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = optionLabel,
                        fontSize = 14.sp,
                        color = VortexColors.Gray33
                    )
                }
            }
        }
    }
}

/**
 * Checkbox element
 */
@Composable
fun CheckboxView(
    block: ElementNode,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = block.getString("label") ?: ""
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = VortexColors.Gray33
        )
    }
}

/**
 * Submit button element
 */
@Composable
fun SubmitButtonView(
    block: ElementNode,
    theme: Theme?,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val text = block.getString("text") ?: block.textContent ?: "Send Invitation"
    val backgroundStyle = theme?.buttonBackgroundStyle
    val textColor = theme?.buttonTextColor?.let { Color(it.toInt()) } ?: Color.White
    
    Button(
        onClick = onClick,
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (backgroundStyle == null) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = textColor
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(
                if (backgroundStyle != null) {
                    Modifier.backgroundStyle(backgroundStyle)
                } else {
                    Modifier
                }
            )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = textColor,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Row layout container
 */
@Composable
fun RowLayoutView(
    block: ElementNode,
    viewModel: VortexInviteViewModel,
    renderBlock: @Composable (ElementNode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        block.children.forEach { child ->
            Box(modifier = Modifier.weight(1f)) {
                renderBlock(child)
            }
        }
    }
}

/**
 * Column layout container
 */
@Composable
fun ColumnLayoutView(
    block: ElementNode,
    viewModel: VortexInviteViewModel,
    renderBlock: @Composable (ElementNode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        block.children.forEach { child ->
            renderBlock(child)
        }
    }
}
