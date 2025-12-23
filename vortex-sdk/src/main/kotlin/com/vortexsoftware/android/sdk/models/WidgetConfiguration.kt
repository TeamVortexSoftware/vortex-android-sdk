package com.vortexsoftware.android.sdk.models

import com.vortexsoftware.android.sdk.api.dto.*
import kotlinx.serialization.json.*
import kotlinx.serialization.builtins.ListSerializer

/**
 * Represents the complete widget configuration fetched from the API
 */
data class WidgetConfiguration(
    val id: String,
    val name: String,
    val slug: String?,
    val version: String?,
    val type: String,
    val theme: Theme?,
    val elements: List<ElementNode>,
    val props: Map<String, ConfigurationPropertyDTO> = emptyMap(),
    val deploymentId: String? = null,
    val widgetType: String? = null,
    val environmentRole: String? = null,
    val environmentName: String? = null
) {
    companion object {
        /**
         * Create from API response DTO
         */
        fun fromDTO(dto: WidgetConfigurationData): WidgetConfiguration {
            val configDto = dto.widgetConfiguration
            val props = configDto.configuration.props
            
            android.util.Log.d("VortexSDK", "Mapping WidgetConfiguration from DTO. Props: ${props.keys}")
            
            val json = Json { 
                ignoreUnknownKeys = true 
                isLenient = true
            }

            // Extract theme
            val theme = props["vortex.theme"]?.value?.let { themeJson ->
                android.util.Log.d("VortexSDK", "Found vortex.theme: $themeJson")
                try {
                    // It can be an object with "options" or a list of options
                    val themeDto = if (themeJson is JsonObject && "options" in themeJson) {
                        json.decodeFromJsonElement(ThemeDTO.serializer(), themeJson)
                    } else if (themeJson is JsonArray) {
                        val options = json.decodeFromJsonElement(ListSerializer(ThemeOptionDTO.serializer()), themeJson)
                        ThemeDTO(options = options)
                    } else {
                        null
                    }
                    themeDto?.let { Theme.fromDTO(it) }
                } catch (e: Exception) {
                    android.util.Log.e("VortexSDK", "Failed to parse theme", e)
                    null
                }
            }

            // Extract elements from vortex.components.form
            val elements = props["vortex.components.form"]?.value?.let { formJson ->
                android.util.Log.d("VortexSDK", "Found vortex.components.form: $formJson")
                try {
                    // Form can have a "root" or be a list
                    val nodes = if (formJson is JsonObject && "root" in formJson) {
                        val root = json.decodeFromJsonElement(ElementNodeDTO.serializer(), formJson["root"]!!)
                        listOf(root)
                    } else if (formJson is JsonArray) {
                        json.decodeFromJsonElement(ListSerializer(ElementNodeDTO.serializer()), formJson)
                    } else {
                        android.util.Log.w("VortexSDK", "vortex.components.form is neither object with root nor array")
                        emptyList()
                    }
                    nodes.map { ElementNode.fromDTO(it) }
                } catch (e: Exception) {
                    android.util.Log.e("VortexSDK", "Failed to parse form elements", e)
                    emptyList()
                }
            } ?: run {
                android.util.Log.w("VortexSDK", "vortex.components.form NOT FOUND in props")
                emptyList()
            }

            android.util.Log.d("VortexSDK", "Mapped ${elements.size} root elements")

            return WidgetConfiguration(
                id = configDto.id,
                name = configDto.name ?: "Untitled Widget",
                slug = configDto.slug,
                version = configDto.version,
                type = configDto.type ?: "vortex-widget",
                theme = theme,
                elements = elements,
                props = props,
                deploymentId = dto.deploymentId,
                widgetType = dto.widgetType,
                environmentRole = dto.environmentRole,
                environmentName = dto.environmentName
            )
        }
    }
}

/**
 * Theme configuration with CSS-like options
 */
data class Theme(
    val name: String?,
    val options: List<ThemeOption>
) {
    companion object {
        fun fromDTO(dto: ThemeDTO): Theme {
            return Theme(
                name = dto.name,
                options = dto.options?.map { ThemeOption(it.key, it.value) } ?: emptyList()
            )
        }
    }
    
    /**
     * Get a theme option value by key
     */
    fun getOption(key: String): String? {
        return options.find { it.key == key }?.value
    }
    
    /**
     * Get button background style from theme options
     */
    val buttonBackgroundStyle: BackgroundStyle?
        get() = getOption("--vrtx-icon-button-background")?.let { BackgroundStyle.parse(it) }
    
    /**
     * Get button text color from theme options
     */
    val buttonTextColor: Long?
        get() = getOption("--vrtx-icon-button-color")?.let { parseHexColor(it) } ?:
                getOption("--color-primary-foreground")?.let { parseHexColor(it) }

    /**
     * Get surface background color
     */
    val surfaceBackgroundColor: Long?
        get() = getOption("--color-surface-background")?.let { parseHexColor(it) }

    /**
     * Get primary background color
     */
    val primaryBackgroundColor: Long?
        get() = getOption("--color-primary-background")?.let { parseHexColor(it) }
}

/**
 * A single theme option (key-value pair)
 */
data class ThemeOption(
    val key: String,
    val value: String
)

/**
 * Represents a UI element node in the widget tree
 */
data class ElementNode(
    val id: String,
    val type: String,
    val subtype: String?,
    val tagName: String?,
    val schemaVersion: Int?,
    val attributes: Map<String, AttributeValue>,
    val style: Map<String, String>,
    val textContent: String?,
    val theme: Theme?,
    val children: List<ElementNode>,
    val settings: JsonObject?,
    val meta: JsonObject?,
    val hidden: Boolean?,
    val vortex: VortexMetadata?
) {
    companion object {
        fun fromDTO(dto: ElementNodeDTO): ElementNode {
            return ElementNode(
                id = dto.id,
                type = dto.type,
                subtype = dto.subtype,
                tagName = dto.tagName,
                schemaVersion = dto.schemaVersion,
                attributes = dto.attributes?.mapValues { (_, v) -> AttributeValue.fromDTO(v) } ?: emptyMap(),
                style = dto.style ?: emptyMap(),
                textContent = dto.textContent,
                theme = dto.theme?.let { Theme.fromDTO(it) },
                children = dto.children?.map { fromDTO(it) } ?: emptyList(),
                settings = dto.settings,
                meta = dto.meta,
                hidden = dto.hidden,
                vortex = dto.vortex?.let { VortexMetadata(it.role) }
            )
        }
    }
    
    /**
     * Get a string attribute value
     */
    fun getString(key: String): String? = attributes[key]?.stringValue
    
    /**
     * Get a boolean attribute value
     */
    fun getBoolean(key: String): Boolean? = attributes[key]?.boolValue ?: hidden
}

/**
 * Vortex-specific metadata for special components
 */
data class VortexMetadata(
    val role: String?
)

/**
 * Attribute value that can be either a string or boolean
 */
sealed class AttributeValue {
    data class StringValue(val value: String) : AttributeValue()
    data class BoolValue(val value: Boolean) : AttributeValue()
    
    val stringValue: String?
        get() = when (this) {
            is StringValue -> value
            is BoolValue -> value.toString()
        }
    
    val boolValue: Boolean?
        get() = when (this) {
            is BoolValue -> value
            is StringValue -> value.toBooleanStrictOrNull()
        }
    
    companion object {
        fun fromDTO(dto: AttributeValueDTO): AttributeValue {
            return when (dto) {
                is AttributeValueDTO.StringValue -> StringValue(dto.value)
                is AttributeValueDTO.BoolValue -> BoolValue(dto.value)
            }
        }
    }
}

/**
 * Represents either a solid color or a gradient background
 */
sealed class BackgroundStyle {
    data class Solid(val color: Long) : BackgroundStyle()
    data class Gradient(
        val angle: Float,
        val colorStops: List<GradientColorStop>
    ) : BackgroundStyle()
    
    companion object {
        /**
         * Parse a CSS background value (either solid color or gradient)
         */
        fun parse(value: String): BackgroundStyle? {
            val trimmed = value.trim()
            
            // Check if it's a gradient
            if (trimmed.startsWith("linear-gradient(")) {
                return parseLinearGradient(trimmed)
            }
            
            // Try to parse as solid color
            return parseHexColor(trimmed)?.let { Solid(it) }
        }
        
        /**
         * Parse a CSS linear-gradient string
         * Example: "linear-gradient(90deg, #6291d5 0%, #bf8ae0 100%)"
         */
        private fun parseLinearGradient(cssGradient: String): Gradient? {
            if (!cssGradient.startsWith("linear-gradient(") || !cssGradient.endsWith(")")) {
                return null
            }
            
            // Extract content inside parentheses
            val content = cssGradient.substring(16, cssGradient.length - 1)
            val parts = content.split(",").map { it.trim() }
            
            if (parts.size < 2) return null
            
            // Parse angle (first part should be like "90deg")
            var angle = 180f // Default: top to bottom
            var colorStartIndex = 0
            
            val firstPart = parts.firstOrNull()
            if (firstPart != null && firstPart.endsWith("deg")) {
                val degString = firstPart.removeSuffix("deg")
                degString.toFloatOrNull()?.let {
                    angle = it
                    colorStartIndex = 1
                }
            }
            
            // Parse color stops
            val colorStops = mutableListOf<GradientColorStop>()
            
            for (i in colorStartIndex until parts.size) {
                val part = parts[i]
                val components = part.split(" ").filter { it.isNotEmpty() }
                
                if (components.isEmpty()) continue
                
                val colorString = components[0]
                var location = (i - colorStartIndex).toFloat() / maxOf(1, parts.size - colorStartIndex - 1)
                
                // Parse percentage if present
                if (components.size >= 2) {
                    val percentString = components[1].removeSuffix("%")
                    percentString.toFloatOrNull()?.let {
                        location = it / 100f
                    }
                }
                
                // Parse color
                parseHexColor(colorString)?.let { color ->
                    colorStops.add(GradientColorStop(color, location))
                }
            }
            
            if (colorStops.size < 2) return null
            
            return Gradient(angle, colorStops)
        }
    }
}

/**
 * A color stop in a gradient
 */
data class GradientColorStop(
    val color: Long,
    val location: Float
)

/**
 * Parse a hex color string to a Long color value (ARGB format)
 * Supports formats: "#RGB", "#RRGGBB", "#RRGGBBAA"
 */
fun parseHexColor(hex: String): Long? {
    val sanitized = hex.trim().removePrefix("#")
    
    return try {
        when (sanitized.length) {
            3 -> {
                // #RGB -> #RRGGBB
                val r = sanitized[0].toString().repeat(2)
                val g = sanitized[1].toString().repeat(2)
                val b = sanitized[2].toString().repeat(2)
                (0xFF000000 or "$r$g$b".toLong(16))
            }
            6 -> {
                // #RRGGBB
                (0xFF000000 or sanitized.toLong(16))
            }
            8 -> {
                // #RRGGBBAA -> AARRGGBB
                val rgb = sanitized.substring(0, 6).toLong(16)
                val alpha = sanitized.substring(6, 8).toLong(16)
                (alpha shl 24) or rgb
            }
            else -> null
        }
    } catch (e: NumberFormatException) {
        null
    }
}
