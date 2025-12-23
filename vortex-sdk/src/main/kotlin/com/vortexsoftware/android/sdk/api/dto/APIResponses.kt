package com.vortexsoftware.android.sdk.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from GET /api/v1/widgets/{componentId}
 */
@Serializable
data class WidgetConfigurationResponse(
    val data: WidgetConfigurationData
)

@Serializable
data class WidgetConfigurationData(
    @SerialName("widgetConfiguration")
    val widgetConfiguration: WidgetConfigurationDTO,
    val deploymentId: String? = null,
    val widgetType: String? = null,
    val environmentRole: String? = null,
    val environmentName: String? = null,
    val renderer: RendererInfoDTO? = null,
    val sessionAttestation: String? = null
)

@Serializable
data class RendererInfoDTO(
    val url: String? = null
)

@Serializable
data class WidgetConfigurationDTO(
    val id: String,
    val name: String? = null,
    val slug: String? = null,
    val version: String? = null,
    val type: String? = null,
    val configuration: WidgetConfigurationConfigurationDTO
)

@Serializable
data class WidgetConfigurationConfigurationDTO(
    val meta: WidgetConfigurationMetaDTO? = null,
    val props: Map<String, ConfigurationPropertyDTO>
)

@Serializable
data class WidgetConfigurationMetaDTO(
    val configuration: ConfigurationInfoDTO
)

@Serializable
data class ConfigurationInfoDTO(
    val version: String,
    val componentType: String? = null,
    val businessType: String? = null
)

@Serializable
data class ConfigurationPropertyDTO(
    val value: kotlinx.serialization.json.JsonElement? = null,
    val valueType: String
)

@Serializable
data class WidgetConfig(
    val theme: ThemeDTO? = null,
    val elements: List<ElementNodeDTO>? = null
)

@Serializable
data class ThemeDTO(
    val name: String? = null,
    val options: List<ThemeOptionDTO>? = null
)

@Serializable
data class ThemeOptionDTO(
    val key: String,
    val value: String
)

@Serializable
data class ElementNodeDTO(
    val id: String,
    val type: String,
    val subtype: String? = null,
    val tagName: String? = null,
    val schemaVersion: Int? = null,
    val attributes: Map<String, AttributeValueDTO>? = null,
    val style: Map<String, String>? = null,
    val textContent: String? = null,
    val theme: ThemeDTO? = null,
    val children: List<ElementNodeDTO>? = null,
    val settings: kotlinx.serialization.json.JsonObject? = null,
    val meta: kotlinx.serialization.json.JsonObject? = null,
    val hidden: Boolean? = null,
    val vortex: VortexMetadataDTO? = null
)

@Serializable
data class VortexMetadataDTO(
    val role: String? = null
)

/**
 * Attribute value that can be either a string or boolean
 */
@Serializable(with = AttributeValueSerializer::class)
sealed class AttributeValueDTO {
    @Serializable
    @SerialName("string")
    data class StringValue(val value: String) : AttributeValueDTO()
    
    @Serializable
    @SerialName("boolean")
    data class BoolValue(val value: Boolean) : AttributeValueDTO()
    
    companion object {
        fun fromAny(value: Any?): AttributeValueDTO? {
            return when (value) {
                is String -> StringValue(value)
                is Boolean -> BoolValue(value)
                else -> null
            }
        }
    }
}

/**
 * Custom serializer for flexible attribute value parsing
 */
object AttributeValueSerializer : kotlinx.serialization.KSerializer<AttributeValueDTO> {
    override val descriptor = kotlinx.serialization.descriptors.buildClassSerialDescriptor("AttributeValue")
    
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: AttributeValueDTO) {
        when (value) {
            is AttributeValueDTO.StringValue -> encoder.encodeString(value.value)
            is AttributeValueDTO.BoolValue -> encoder.encodeBoolean(value.value)
        }
    }
    
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): AttributeValueDTO {
        val jsonDecoder = decoder as? kotlinx.serialization.json.JsonDecoder
            ?: throw kotlinx.serialization.SerializationException("Expected JsonDecoder")
        
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is kotlinx.serialization.json.JsonPrimitive -> {
                when {
                    element.isString -> AttributeValueDTO.StringValue(element.content)
                    element.content == "true" || element.content == "false" -> 
                        AttributeValueDTO.BoolValue(element.content.toBoolean())
                    else -> AttributeValueDTO.StringValue(element.content)
                }
            }
            else -> throw kotlinx.serialization.SerializationException("Unexpected JSON element type")
        }
    }
}

/**
 * Response from POST /api/v1/invitations
 */
@Serializable
data class CreateInvitationResponse(
    val data: CreateInvitationData
)

@Serializable
data class CreateInvitationData(
    val invitationEntries: List<InvitationEntry>? = null
)

@Serializable
data class InvitationEntry(
    val id: String? = null,
    val status: String? = null
)

/**
 * Response from POST /api/v1/invitations/generate-shareable-link-invite
 */
@Serializable
data class ShareableLinkResponse(
    val data: ShareableLinkData
)

@Serializable
data class ShareableLinkData(
    val invitation: ShareableLinkInvitation
)

@Serializable
data class ShareableLinkInvitation(
    val id: String,
    val shortLink: String,
    val source: String? = null,
    val attributes: Map<String, String>? = null
)

/**
 * Request body for creating an invitation
 */
@Serializable
data class CreateInvitationRequest(
    @SerialName("widgetConfigurationId")
    val widgetConfigurationId: String,
    @SerialName("payload")
    val payload: Map<String, InvitationPayloadValue>? = null,
    @SerialName("source")
    val source: String = "email",
    @SerialName("groups")
    val groups: List<GroupDTO>? = null,
    @SerialName("passThrough")
    val passThrough: String? = null,
    @SerialName("configurationAttributes")
    val configurationAttributes: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    @SerialName("templateVariables")
    val templateVariables: Map<String, String>? = null
)

/**
 * Value for a payload field
 */
@Serializable
data class InvitationPayloadValue(
    @SerialName("value")
    val value: kotlinx.serialization.json.JsonElement,
    @SerialName("type")
    val type: String,
    @SerialName("role")
    val role: String? = null
)

/**
 * Request body for generating a shareable link
 */
@Serializable
data class GenerateShareableLinkRequest(
    @SerialName("widgetConfigurationId")
    val widgetConfigurationId: String,
    @SerialName("groups")
    val groups: List<GroupDTO>? = null,
    @SerialName("templateVariables")
    val templateVariables: Map<String, String>? = null
)

/**
 * Group DTO for passing group context
 */
@Serializable
data class GroupDTO(
    val id: String? = null,
    val groupId: String? = null,
    val type: String,
    val name: String
) {
    /**
     * Helper to create a GroupDTO that aligns with iOS/backend expectations
     * where both id and groupId are present and usually the same.
     */
    companion object {
        fun create(id: String, type: String, name: String) = GroupDTO(
            id = id,
            groupId = id,
            type = type,
            name = name
        )
    }
}
