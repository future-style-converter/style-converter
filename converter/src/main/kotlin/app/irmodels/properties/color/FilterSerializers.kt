package app.irmodels.properties.color

import app.irmodels.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

object FilterFunctionSerializer : KSerializer<FilterFunction> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FilterFunction")

    override fun serialize(encoder: Encoder, value: FilterFunction) {
        require(encoder is JsonEncoder)
        val json = encoder.json
        encoder.encodeJsonElement(when (value) {
            is FilterFunction.Blur -> buildJsonObject {
                put("fn", "blur")
                put("r", json.encodeToJsonElement(IRLength.serializer(), value.radius))
            }
            is FilterFunction.Brightness -> buildJsonObject {
                put("fn", "brightness")
                put("v", json.encodeToJsonElement(IRPercentage.serializer(), value.amount))
            }
            is FilterFunction.Contrast -> buildJsonObject {
                put("fn", "contrast")
                put("v", json.encodeToJsonElement(IRPercentage.serializer(), value.amount))
            }
            is FilterFunction.Grayscale -> buildJsonObject {
                put("fn", "grayscale")
                put("v", json.encodeToJsonElement(IRPercentage.serializer(), value.amount))
            }
            is FilterFunction.HueRotate -> buildJsonObject {
                put("fn", "hue-rotate")
                put("a", json.encodeToJsonElement(IRAngle.serializer(), value.angle))
            }
            is FilterFunction.Invert -> buildJsonObject {
                put("fn", "invert")
                put("v", json.encodeToJsonElement(IRPercentage.serializer(), value.amount))
            }
            is FilterFunction.Saturate -> buildJsonObject {
                put("fn", "saturate")
                put("v", json.encodeToJsonElement(IRPercentage.serializer(), value.amount))
            }
            is FilterFunction.Sepia -> buildJsonObject {
                put("fn", "sepia")
                put("v", json.encodeToJsonElement(IRPercentage.serializer(), value.amount))
            }
            is FilterFunction.Opacity -> buildJsonObject {
                put("fn", "opacity")
                put("v", json.encodeToJsonElement(IRPercentage.serializer(), value.amount))
            }
            is FilterFunction.DropShadow -> buildJsonObject {
                put("fn", "drop-shadow")
                put("x", json.encodeToJsonElement(IRLength.serializer(), value.offsetX))
                put("y", json.encodeToJsonElement(IRLength.serializer(), value.offsetY))
                value.blurRadius?.let { put("r", json.encodeToJsonElement(IRLength.serializer(), it)) }
                value.color?.let { put("c", json.encodeToJsonElement(IRColor.serializer(), it)) }
            }
        })
    }

    override fun deserialize(decoder: Decoder): FilterFunction {
        require(decoder is JsonDecoder)
        val obj = decoder.decodeJsonElement().jsonObject
        val json = decoder.json
        return when (obj["fn"]?.jsonPrimitive?.content) {
            "blur" -> FilterFunction.Blur(json.decodeFromJsonElement(IRLength.serializer(), obj["r"]!!))
            "brightness" -> FilterFunction.Brightness(json.decodeFromJsonElement(IRPercentage.serializer(), obj["v"]!!))
            "contrast" -> FilterFunction.Contrast(json.decodeFromJsonElement(IRPercentage.serializer(), obj["v"]!!))
            "grayscale" -> FilterFunction.Grayscale(json.decodeFromJsonElement(IRPercentage.serializer(), obj["v"]!!))
            "hue-rotate" -> FilterFunction.HueRotate(json.decodeFromJsonElement(IRAngle.serializer(), obj["a"]!!))
            "invert" -> FilterFunction.Invert(json.decodeFromJsonElement(IRPercentage.serializer(), obj["v"]!!))
            "saturate" -> FilterFunction.Saturate(json.decodeFromJsonElement(IRPercentage.serializer(), obj["v"]!!))
            "sepia" -> FilterFunction.Sepia(json.decodeFromJsonElement(IRPercentage.serializer(), obj["v"]!!))
            "opacity" -> FilterFunction.Opacity(json.decodeFromJsonElement(IRPercentage.serializer(), obj["v"]!!))
            "drop-shadow" -> FilterFunction.DropShadow(
                json.decodeFromJsonElement(IRLength.serializer(), obj["x"]!!),
                json.decodeFromJsonElement(IRLength.serializer(), obj["y"]!!),
                obj["r"]?.let { json.decodeFromJsonElement(IRLength.serializer(), it) },
                obj["c"]?.let { json.decodeFromJsonElement(IRColor.serializer(), it) }
            )
            else -> FilterFunction.Blur(IRLength.fromPx(0.0))
        }
    }
}

object FilterValueSerializer : KSerializer<FilterProperty.FilterValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FilterValue")

    override fun serialize(encoder: Encoder, value: FilterProperty.FilterValue) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is FilterProperty.FilterValue.None -> JsonPrimitive("none")
            is FilterProperty.FilterValue.FilterList ->
                encoder.json.encodeToJsonElement(ListSerializer(FilterFunctionSerializer), value.functions)
            is FilterProperty.FilterValue.UrlReference -> buildJsonObject { put("url", JsonPrimitive(value.url)) }
            is FilterProperty.FilterValue.Keyword -> JsonPrimitive(value.keyword)
            is FilterProperty.FilterValue.Raw -> buildJsonObject { put("raw", JsonPrimitive(value.value)) }
        })
    }

    override fun deserialize(decoder: Decoder): FilterProperty.FilterValue {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.content == "none" -> FilterProperty.FilterValue.None()
            element is JsonPrimitive -> FilterProperty.FilterValue.Keyword(element.content)
            element is JsonObject && element.containsKey("url") -> FilterProperty.FilterValue.UrlReference((element["url"] as JsonPrimitive).content)
            element is JsonObject && element.containsKey("raw") -> FilterProperty.FilterValue.Raw((element["raw"] as JsonPrimitive).content)
            element is JsonArray ->
                FilterProperty.FilterValue.FilterList(decoder.json.decodeFromJsonElement(ListSerializer(FilterFunctionSerializer), element))
            else -> FilterProperty.FilterValue.None()
        }
    }
}
