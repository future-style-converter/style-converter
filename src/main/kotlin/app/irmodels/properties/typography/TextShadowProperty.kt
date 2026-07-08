package app.irmodels.properties.typography

import app.irmodels.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class TextShadowProperty(
    val shadows: List<Shadow>
) : IRProperty {
    override val propertyName = "text-shadow"

    @Serializable(with = TextShadowSerializer::class)
    data class Shadow(val offsetX: IRLength, val offsetY: IRLength, val blurRadius: IRLength?, val color: IRColor?)
}

object TextShadowSerializer : KSerializer<TextShadowProperty.Shadow> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TextShadow")
    override fun serialize(encoder: Encoder, value: TextShadowProperty.Shadow) {
        require(encoder is JsonEncoder)
        val json = encoder.json
        encoder.encodeJsonElement(buildJsonObject {
            put("x", json.encodeToJsonElement(IRLength.serializer(), value.offsetX))
            put("y", json.encodeToJsonElement(IRLength.serializer(), value.offsetY))
            value.blurRadius?.let { put("blur", json.encodeToJsonElement(IRLength.serializer(), it)) }
            value.color?.let { put("c", json.encodeToJsonElement(IRColor.serializer(), it)) }
        })
    }
    override fun deserialize(decoder: Decoder): TextShadowProperty.Shadow {
        require(decoder is JsonDecoder)
        val obj = decoder.decodeJsonElement().jsonObject
        val json = decoder.json
        return TextShadowProperty.Shadow(
            offsetX = json.decodeFromJsonElement(IRLength.serializer(), obj["x"]!!),
            offsetY = json.decodeFromJsonElement(IRLength.serializer(), obj["y"]!!),
            blurRadius = obj["blur"]?.let { json.decodeFromJsonElement(IRLength.serializer(), it) },
            color = obj["c"]?.let { json.decodeFromJsonElement(IRColor.serializer(), it) }
        )
    }
}
