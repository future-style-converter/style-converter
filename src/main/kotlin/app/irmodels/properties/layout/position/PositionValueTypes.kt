package app.irmodels.properties.layout.position

import app.irmodels.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

/**
 * Value type for CSS inset properties (top, right, bottom, left, inset-*).
 * Supports auto, length, and percentage values.
 */
@Serializable(with = InsetValueSerializer::class)
sealed interface InsetValue {
    @Serializable data class Auto(val unit: Unit = Unit) : InsetValue
    @Serializable data class LengthValue(val length: IRLength) : InsetValue
    @Serializable data class PercentageValue(val percentage: IRPercentage) : InsetValue
    @Serializable data class Expression(val expr: String) : InsetValue
    @Serializable data class Anchor(
        val anchorName: String?, // null for implicit anchor (e.g., anchor(bottom))
        val side: String,        // bottom, top, left, right, center, start, end, etc.
        val fallback: IRLength? = null
    ) : InsetValue
}

object InsetValueSerializer : KSerializer<InsetValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("InsetValue")

    override fun serialize(encoder: Encoder, value: InsetValue) {
        require(encoder is JsonEncoder)
        val json = encoder.json
        encoder.encodeJsonElement(when (value) {
            is InsetValue.LengthValue -> json.encodeToJsonElement(IRLength.serializer(), value.length)
            is InsetValue.PercentageValue -> json.encodeToJsonElement(IRPercentage.serializer(), value.percentage)
            is InsetValue.Auto -> JsonPrimitive("auto")
            is InsetValue.Expression -> buildJsonObject { put("expr", value.expr) }
            is InsetValue.Anchor -> buildJsonObject {
                put("anchor", value.side)
                value.anchorName?.let { put("name", it) }
                value.fallback?.let { put("fallback", json.encodeToJsonElement(IRLength.serializer(), it)) }
            }
        })
    }

    override fun deserialize(decoder: Decoder): InsetValue {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.content == "auto" -> InsetValue.Auto()
            element is JsonObject && element.containsKey("expr") ->
                InsetValue.Expression(element["expr"]!!.jsonPrimitive.content)
            element is JsonObject && element.containsKey("anchor") ->
                InsetValue.Anchor(
                    anchorName = element["name"]?.jsonPrimitive?.content,
                    side = element["anchor"]!!.jsonPrimitive.content,
                    fallback = element["fallback"]?.let { decoder.json.decodeFromJsonElement(IRLength.serializer(), it) }
                )
            element is JsonObject && element.containsKey("unit") ->
                InsetValue.LengthValue(decoder.json.decodeFromJsonElement(IRLength.serializer(), element))
            else -> InsetValue.PercentageValue(decoder.json.decodeFromJsonElement(IRPercentage.serializer(), element))
        }
    }
}
