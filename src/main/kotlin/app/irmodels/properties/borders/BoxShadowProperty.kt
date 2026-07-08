package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable(with = BoxShadowPropertySerializer::class)
data class BoxShadowProperty(
    val value: BoxShadowValue
) : IRProperty {
    override val propertyName = "box-shadow"

    // Convenience constructor for list of shadows
    constructor(shadows: List<Shadow>) : this(BoxShadowValue.Shadows(shadows))

    sealed interface BoxShadowValue {
        @Serializable data class Shadows(val list: List<Shadow>) : BoxShadowValue
        @Serializable data class Expression(val expr: String) : BoxShadowValue
        @Serializable data class Keyword(val keyword: String) : BoxShadowValue
    }

    @Serializable(with = BoxShadowSerializer::class)
    data class Shadow(val offsetX: IRLength, val offsetY: IRLength, val blurRadius: IRLength?, val spreadRadius: IRLength?, val color: IRColor?, val inset: Boolean = false)
}

object BoxShadowPropertySerializer : KSerializer<BoxShadowProperty> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BoxShadowProperty")
    override fun serialize(encoder: Encoder, value: BoxShadowProperty) {
        require(encoder is JsonEncoder)
        val json = encoder.json
        when (val v = value.value) {
            is BoxShadowProperty.BoxShadowValue.Shadows -> {
                encoder.encodeJsonElement(json.encodeToJsonElement(ListSerializer(BoxShadowSerializer), v.list))
            }
            is BoxShadowProperty.BoxShadowValue.Expression -> {
                encoder.encodeJsonElement(buildJsonObject { put("expr", v.expr) })
            }
            is BoxShadowProperty.BoxShadowValue.Keyword -> {
                encoder.encodeJsonElement(JsonPrimitive(v.keyword))
            }
        }
    }
    override fun deserialize(decoder: Decoder): BoxShadowProperty {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonArray -> BoxShadowProperty(decoder.json.decodeFromJsonElement(ListSerializer(BoxShadowSerializer), element))
            element is JsonObject && element.containsKey("expr") ->
                BoxShadowProperty(BoxShadowProperty.BoxShadowValue.Expression(element["expr"]!!.jsonPrimitive.content))
            element is JsonPrimitive -> BoxShadowProperty(BoxShadowProperty.BoxShadowValue.Keyword(element.content))
            else -> BoxShadowProperty(emptyList())
        }
    }
}

object BoxShadowSerializer : KSerializer<BoxShadowProperty.Shadow> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BoxShadow")
    override fun serialize(encoder: Encoder, value: BoxShadowProperty.Shadow) {
        require(encoder is JsonEncoder)
        val json = encoder.json
        encoder.encodeJsonElement(buildJsonObject {
            put("x", json.encodeToJsonElement(IRLength.serializer(), value.offsetX))
            put("y", json.encodeToJsonElement(IRLength.serializer(), value.offsetY))
            value.blurRadius?.let { put("blur", json.encodeToJsonElement(IRLength.serializer(), it)) }
            value.spreadRadius?.let { put("spread", json.encodeToJsonElement(IRLength.serializer(), it)) }
            value.color?.let { put("c", json.encodeToJsonElement(IRColor.serializer(), it)) }
            if (value.inset) put("inset", true)
        })
    }
    override fun deserialize(decoder: Decoder): BoxShadowProperty.Shadow {
        require(decoder is JsonDecoder)
        val obj = decoder.decodeJsonElement().jsonObject
        val json = decoder.json
        return BoxShadowProperty.Shadow(
            offsetX = json.decodeFromJsonElement(IRLength.serializer(), obj["x"]!!),
            offsetY = json.decodeFromJsonElement(IRLength.serializer(), obj["y"]!!),
            blurRadius = obj["blur"]?.let { json.decodeFromJsonElement(IRLength.serializer(), it) },
            spreadRadius = obj["spread"]?.let { json.decodeFromJsonElement(IRLength.serializer(), it) },
            color = obj["c"]?.let { json.decodeFromJsonElement(IRColor.serializer(), it) },
            inset = obj["inset"]?.jsonPrimitive?.boolean ?: false
        )
    }
}
