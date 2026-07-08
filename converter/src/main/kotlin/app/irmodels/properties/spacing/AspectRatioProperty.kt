package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class AspectRatioProperty(
    val ratio: AspectRatio,
    /** Normalized ratio as width/height Double for cross-platform use. Null if auto/expression/keyword. */
    val normalizedRatio: Double? = null
) : IRProperty {
    override val propertyName = "aspect-ratio"

    companion object {
        fun fromRatio(width: Double, height: Double) = AspectRatioProperty(
            ratio = AspectRatio.Ratio(IRNumber(width), IRNumber(height)),
            normalizedRatio = if (height != 0.0) width / height else null
        )
        fun fromAutoRatio(width: Double, height: Double) = AspectRatioProperty(
            ratio = AspectRatio.AutoRatio(IRNumber(width), IRNumber(height)),
            normalizedRatio = if (height != 0.0) width / height else null
        )
        fun fromSingleValue(value: Double) = AspectRatioProperty(
            ratio = AspectRatio.SingleValue(IRNumber(value)),
            normalizedRatio = value
        )
        fun auto() = AspectRatioProperty(ratio = AspectRatio.Auto(), normalizedRatio = null)
        fun fromExpression(expr: String) = AspectRatioProperty(
            ratio = AspectRatio.Expression(expr),
            normalizedRatio = null
        )
        fun fromKeyword(keyword: String) = AspectRatioProperty(
            ratio = AspectRatio.Keyword(keyword),
            normalizedRatio = null
        )
    }

    @Serializable(with = AspectRatioSerializer::class)
    sealed interface AspectRatio {
        @Serializable data class Auto(val unit: Unit = Unit) : AspectRatio
        @Serializable data class Ratio(val width: IRNumber, val height: IRNumber) : AspectRatio
        @Serializable data class AutoRatio(val width: IRNumber, val height: IRNumber) : AspectRatio  // "auto 16/9"
        @Serializable data class SingleValue(val value: IRNumber) : AspectRatio
        @Serializable data class Expression(val expr: String) : AspectRatio
        @Serializable data class Keyword(val keyword: String) : AspectRatio
    }
}

object AspectRatioSerializer : KSerializer<AspectRatioProperty.AspectRatio> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AspectRatio")
    override fun serialize(encoder: Encoder, value: AspectRatioProperty.AspectRatio) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is AspectRatioProperty.AspectRatio.Auto -> JsonPrimitive("auto")
            is AspectRatioProperty.AspectRatio.Ratio -> buildJsonObject {
                put("w", encoder.json.encodeToJsonElement(IRNumber.serializer(), value.width))
                put("h", encoder.json.encodeToJsonElement(IRNumber.serializer(), value.height))
            }
            is AspectRatioProperty.AspectRatio.AutoRatio -> buildJsonObject {
                put("auto", JsonPrimitive(true))
                put("w", encoder.json.encodeToJsonElement(IRNumber.serializer(), value.width))
                put("h", encoder.json.encodeToJsonElement(IRNumber.serializer(), value.height))
            }
            is AspectRatioProperty.AspectRatio.SingleValue -> buildJsonObject {
                put("value", encoder.json.encodeToJsonElement(IRNumber.serializer(), value.value))
            }
            is AspectRatioProperty.AspectRatio.Expression -> buildJsonObject {
                put("expr", JsonPrimitive(value.expr))
            }
            is AspectRatioProperty.AspectRatio.Keyword -> JsonPrimitive(value.keyword)
        })
    }
    override fun deserialize(decoder: Decoder): AspectRatioProperty.AspectRatio {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.content == "auto" -> AspectRatioProperty.AspectRatio.Auto()
            element is JsonPrimitive -> AspectRatioProperty.AspectRatio.Keyword(element.content)
            element is JsonObject && element.containsKey("auto") -> AspectRatioProperty.AspectRatio.AutoRatio(
                decoder.json.decodeFromJsonElement(IRNumber.serializer(), element["w"]!!),
                decoder.json.decodeFromJsonElement(IRNumber.serializer(), element["h"]!!)
            )
            element is JsonObject && element.containsKey("w") -> AspectRatioProperty.AspectRatio.Ratio(
                decoder.json.decodeFromJsonElement(IRNumber.serializer(), element["w"]!!),
                decoder.json.decodeFromJsonElement(IRNumber.serializer(), element["h"]!!)
            )
            element is JsonObject && element.containsKey("value") -> AspectRatioProperty.AspectRatio.SingleValue(
                decoder.json.decodeFromJsonElement(IRNumber.serializer(), element["value"]!!)
            )
            element is JsonObject && element.containsKey("expr") -> AspectRatioProperty.AspectRatio.Expression(
                (element["expr"] as JsonPrimitive).content
            )
            else -> AspectRatioProperty.AspectRatio.Auto()
        }
    }
}
