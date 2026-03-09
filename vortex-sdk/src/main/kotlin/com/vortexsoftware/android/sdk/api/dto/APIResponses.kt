package com.vortexsoftware.android.sdk.api.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    val attributes: kotlinx.serialization.json.JsonObject? = null,
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
 * Handles string, boolean, and array values (matching iOS SDK's AttributeValue enum)
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
        android.util.Log.d("VortexSDK", "AttributeValueSerializer.deserialize called")
        val jsonDecoder = decoder as? kotlinx.serialization.json.JsonDecoder
            ?: throw kotlinx.serialization.SerializationException("Expected JsonDecoder")
        
        val element = jsonDecoder.decodeJsonElement()
        android.util.Log.d("VortexSDK", "AttributeValueSerializer - element: $element, type: ${element::class.simpleName}")
        
        return when (element) {
            is kotlinx.serialization.json.JsonPrimitive -> {
                val result = when {
                    element.isString -> AttributeValueDTO.StringValue(element.content)
                    element.content == "true" || element.content == "false" -> 
                        AttributeValueDTO.BoolValue(element.content.toBoolean())
                    else -> AttributeValueDTO.StringValue(element.content)
                }
                android.util.Log.d("VortexSDK", "AttributeValueSerializer - parsed primitive: $result")
                result
            }
            is kotlinx.serialization.json.JsonArray -> {
                // Handle string arrays by joining them (or take first element)
                val strings = element.mapNotNull { item ->
                    (item as? kotlinx.serialization.json.JsonPrimitive)?.content
                }
                AttributeValueDTO.StringValue(strings.joinToString(", "))
            }
            is kotlinx.serialization.json.JsonObject -> {
                // Handle objects by converting to string representation
                AttributeValueDTO.StringValue(element.toString())
            }
            else -> {
                // Fallback for any other type
                AttributeValueDTO.StringValue(element.toString())
            }
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
    val status: String? = null,
    val shortLink: String? = null
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
    val templateVariables: Map<String, String>? = null,
    @SerialName("metadata")
    val metadata: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    @SerialName("subtype")
    val subtype: String? = null
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
    val templateVariables: Map<String, String>? = null,
    @SerialName("metadata")
    val metadata: Map<String, kotlinx.serialization.json.JsonElement>? = null
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

// ============================================================================
// Outgoing Invitations
// ============================================================================

/**
 * Response from GET /api/v1/invitations/sent
 */
@Serializable
data class OutgoingInvitationsResponse(
    val data: OutgoingInvitationsData
)

@Serializable
data class OutgoingInvitationsData(
    val invitations: List<OutgoingInvitation>
)

/**
 * Target of an invitation (e.g., email or SMS recipient).
 *
 * Handles both field-name formats returned by the API:
 * - Full detail endpoint: `type`, `value`, `name`, `avatarUrl`
 * - List endpoints: `targetType`, `targetValue`, `targetName`, `targetAvatarUrl`
 */
@Serializable(with = InvitationTargetSerializer::class)
data class InvitationTarget(
    val targetType: String,
    val targetValue: String,
    val targetName: String? = null,
    val targetAvatarUrl: String? = null
)

/**
 * Custom serializer for [InvitationTarget] that handles both field-name formats
 * returned by the Vortex API.
 */
object InvitationTargetSerializer : KSerializer<InvitationTarget> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("InvitationTarget") {
        element<String>("targetType")
        element<String>("targetValue")
        element<String>("targetName", isOptional = true)
        element<String>("targetAvatarUrl", isOptional = true)
    }

    override fun deserialize(decoder: Decoder): InvitationTarget {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject

        val targetType = obj["targetType"]?.jsonPrimitive?.content
            ?: obj["type"]?.jsonPrimitive?.content
            ?: ""
        val targetValue = obj["targetValue"]?.jsonPrimitive?.content
            ?: obj["value"]?.jsonPrimitive?.content
            ?: ""
        val targetName = (obj["targetName"] ?: obj["name"])
            ?.takeIf { it !is kotlinx.serialization.json.JsonNull }
            ?.jsonPrimitive?.content
        val targetAvatarUrl = (obj["targetAvatarUrl"] ?: obj["avatarUrl"])
            ?.takeIf { it !is kotlinx.serialization.json.JsonNull }
            ?.jsonPrimitive?.content

        return InvitationTarget(targetType, targetValue, targetName, targetAvatarUrl)
    }

    override fun serialize(encoder: Encoder, value: InvitationTarget) {
        val jsonEncoder = encoder as JsonEncoder
        val obj = buildMap {
            put("targetType", JsonPrimitive(value.targetType))
            put("targetValue", JsonPrimitive(value.targetValue))
            value.targetName?.let { put("targetName", JsonPrimitive(it)) }
            value.targetAvatarUrl?.let { put("targetAvatarUrl", JsonPrimitive(it)) }
        }
        jsonEncoder.encodeJsonElement(JsonObject(obj))
    }
}

/**
 * Individual outgoing invitation from the API
 */
@Serializable
data class OutgoingInvitation(
    val id: String,
    val targets: List<InvitationTarget>? = null,
    val senderIdentifier: String? = null,
    val senderIdentifierType: String? = null,
    val avatarUrl: String? = null,
    val status: String? = null,
    val createdAt: String? = null,
    val metadata: Map<String, JsonElement>? = null
)

// ============================================================================
// Incoming Invitations
// ============================================================================

/**
 * Response from GET /api/v1/invitations (open/incoming invitations)
 */
@Serializable
data class IncomingInvitationsResponse(
    val data: IncomingInvitationsData
)

@Serializable
data class IncomingInvitationsData(
    val invitations: List<IncomingInvitation>,
    val nextCursor: String? = null,
    val hasMore: Boolean? = null,
    val count: Int? = null
)

/**
 * Individual incoming invitation from the API
 */
@Serializable
data class IncomingInvitation(
    val id: String,
    val targets: List<InvitationTarget>? = null,
    val senderIdentifier: String? = null,
    val senderIdentifierType: String? = null,
    val avatarUrl: String? = null,
    val status: String? = null,
    val createdAt: String? = null,
    val source: String? = null,
    val deliveryType: String? = null,
    val metadata: Map<String, JsonElement>? = null,
    val creatorName: String? = null,
    val creatorAvatarUrl: String? = null,
    val creatorId: String? = null
)

/**
 * Request body for accepting an invitation
 */
@Serializable
data class AcceptInvitationRequest(
    val invitationId: String
)

// ============================================================================
// Invitation (full detail)
// ============================================================================

/**
 * Group associated with an invitation.
 */
@Serializable
data class InvitationGroup(
    val id: String,
    val groupId: String,
    val type: String,
    val name: String? = null
)

/**
 * Acceptance record for an invitation.
 */
@Serializable
data class InvitationAcceptance(
    val id: String,
    val accountId: String,
    val projectId: String,
    val acceptedAt: String,
    val targetType: String,
    val targetValue: String,
    val identifiers: Map<String, JsonElement>? = null
)

/**
 * A full invitation as returned by the Vortex API (`GET /api/v1/invitations/:id`).
 */
@Serializable
data class Invitation(
    val id: String,
    val accountId: String? = null,
    val projectId: String? = null,
    val deploymentId: String? = null,
    val widgetConfigurationId: String? = null,
    val status: String? = null,
    val invitationType: String? = null,
    val deliveryTypes: List<String>? = null,
    val source: String? = null,
    val subtype: String? = null,
    val foreignCreatorId: String? = null,
    val creatorName: String? = null,
    val creatorAvatarUrl: String? = null,
    val createdAt: String? = null,
    val modifiedAt: String? = null,
    val deactivated: Boolean? = null,
    val deliveryCount: Int? = null,
    val views: Int? = null,
    val clickThroughs: Int? = null,
    val configurationAttributes: Map<String, JsonElement>? = null,
    val attributes: Map<String, JsonElement>? = null,
    val metadata: Map<String, JsonElement>? = null,
    val passThrough: String? = null,
    val target: List<InvitationTarget>? = null,
    val groups: List<InvitationGroup>? = null,
    val accepts: List<InvitationAcceptance>? = null,
    val scope: String? = null,
    val scopeType: String? = null,
    val expired: Boolean? = null,
    val expires: String? = null
)

// ============================================================================
// Deferred Deep Links (Fingerprint Matching)
// ============================================================================

/**
 * Device fingerprint data for deferred deep link matching
 */
@Serializable
data class DeviceFingerprint(
    val platform: String? = null,
    val osVersion: String? = null,
    val deviceModel: String? = null,
    val deviceBrand: String? = null,
    val timezone: String? = null,
    val language: String? = null,
    val screenWidth: Int? = null,
    val screenHeight: Int? = null,
    val carrierName: String? = null,
    val totalMemory: Long? = null
)

/**
 * Request body for fingerprint matching endpoint
 */
@Serializable
data class MatchFingerprintRequest(
    val fingerprint: DeviceFingerprint
)

/**
 * Invitation context returned when a fingerprint match is found
 */
@Serializable
data class DeferredLinkContext(
    val invitationId: String,
    val inviterId: String? = null,
    val groupId: String? = null,
    val groupType: String? = null,
    val metadata: Map<String, kotlinx.serialization.json.JsonElement>? = null
) {
    /** Scope identifier from the invitation (e.g., team ID, project ID) */
    val scope: String? get() = groupId
    
    /** Type of the scope (e.g., "team", "project") */
    val scopeType: String? get() = groupType
}

/**
 * Response from fingerprint matching endpoint
 */
@Serializable
data class MatchFingerprintResponse(
    val matched: Boolean,
    val confidence: Double? = null,
    val context: DeferredLinkContext? = null,
    val error: String? = null
)

// ============================================================================
// SMS Invitations
// ============================================================================

/**
 * Target for SMS invitation
 */
@Serializable
data class SmsInvitationTarget(
    val targetType: String = "sms",
    val targetValue: String,
    val targetName: String? = null
)

/**
 * Request body for creating an SMS invitation
 */
@Serializable
data class CreateSmsInvitationRequest(
    @SerialName("widgetConfigurationId")
    val widgetConfigurationId: String,
    @SerialName("source")
    val source: String = "sms",
    @SerialName("targets")
    val targets: List<SmsInvitationTarget>,
    @SerialName("groups")
    val groups: List<GroupDTO>? = null,
    @SerialName("templateVariables")
    val templateVariables: Map<String, String>? = null
)

/**
 * Request body for creating an internal ID invitation (Find Friends)
 */
@Serializable
data class CreateInternalIdInvitationRequest(
    @SerialName("widgetConfigurationId")
    val widgetConfigurationId: String,
    @SerialName("source")
    val source: String = "internalId",
    @SerialName("targets")
    val targets: List<InternalIdTarget>,
    @SerialName("groups")
    val groups: List<GroupDTO>? = null,
    @SerialName("templateVariables")
    val templateVariables: Map<String, String>? = null
)

/**
 * Target for internal ID invitation
 */
@Serializable
data class InternalIdTarget(
    val targetType: String = "internalId",
    val targetValue: String,
    val targetName: String? = null
)
