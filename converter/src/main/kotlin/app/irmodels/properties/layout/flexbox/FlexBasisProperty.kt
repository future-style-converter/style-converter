package app.irmodels.properties.layout.flexbox

import app.irmodels.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class FlexBasisProperty(
    val value: FlexBasis,
    /** Normalized pixels for length values (px assumed). Null for auto/content/expression/percentage. */
    val normalizedPixels: Double? = null
) : IRProperty {
    override val propertyName = "flex-basis"

    @Serializable(with = FlexBasisSerializer::class)
    sealed interface FlexBasis {
        @Serializable data class Auto(val unit: Unit = Unit) : FlexBasis
        @Serializable data class Content(val unit: Unit = Unit) : FlexBasis
        @Serializable data class MaxContent(val unit: Unit = Unit) : FlexBasis
        @Serializable data class MinContent(val unit: Unit = Unit) : FlexBasis
        @Serializable data class FitContent(val unit: Unit = Unit) : FlexBasis
        @Serializable data class LengthValue(val length: IRLength) : FlexBasis
        @Serializable data class PercentageValue(val percentage: IRPercentage) : FlexBasis
        @Serializable data class Expression(val expr: String) : FlexBasis
        @Serializable data class Keyword(val keyword: String) : FlexBasis
    }

    companion object {
        fun auto() = FlexBasisProperty(FlexBasis.Auto(), null)
        fun content() = FlexBasisProperty(FlexBasis.Content(), null)
        fun maxContent() = FlexBasisProperty(FlexBasis.MaxContent(), null)
        fun minContent() = FlexBasisProperty(FlexBasis.MinContent(), null)
        fun fitContent() = FlexBasisProperty(FlexBasis.FitContent(), null)
        fun fromLength(length: IRLength) = FlexBasisProperty(
            FlexBasis.LengthValue(length),
            if (length.unit == IRLength.LengthUnit.PX) length.value else null
        )
        fun fromPercentage(pct: IRPercentage) = FlexBasisProperty(FlexBasis.PercentageValue(pct), null)
        fun fromExpression(expr: String) = FlexBasisProperty(FlexBasis.Expression(expr), null)
        fun fromKeyword(keyword: String) = FlexBasisProperty(FlexBasis.Keyword(keyword), null)
    }
}

object FlexBasisSerializer : KSerializer<FlexBasisProperty.FlexBasis> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FlexBasis")
    override fun serialize(encoder: Encoder, value: FlexBasisProperty.FlexBasis) {
        require(encoder is JsonEncoder)
        val json = encoder.json
        encoder.encodeJsonElement(when (value) {
            is FlexBasisProperty.FlexBasis.LengthValue -> json.encodeToJsonElement(IRLength.serializer(), value.length)
            is FlexBasisProperty.FlexBasis.PercentageValue -> json.encodeToJsonElement(IRPercentage.serializer(), value.percentage)
            is FlexBasisProperty.FlexBasis.Auto -> JsonPrimitive("auto")
            is FlexBasisProperty.FlexBasis.Content -> JsonPrimitive("content")
            is FlexBasisProperty.FlexBasis.MaxContent -> JsonPrimitive("max-content")
            is FlexBasisProperty.FlexBasis.MinContent -> JsonPrimitive("min-content")
            is FlexBasisProperty.FlexBasis.FitContent -> JsonPrimitive("fit-content")
            is FlexBasisProperty.FlexBasis.Expression -> buildJsonObject { put("expr", JsonPrimitive(value.expr)) }
            is FlexBasisProperty.FlexBasis.Keyword -> JsonPrimitive(value.keyword)
        })
    }
    override fun deserialize(decoder: Decoder): FlexBasisProperty.FlexBasis {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.content == "auto" -> FlexBasisProperty.FlexBasis.Auto()
            element is JsonPrimitive && element.content == "content" -> FlexBasisProperty.FlexBasis.Content()
            element is JsonPrimitive && element.content == "max-content" -> FlexBasisProperty.FlexBasis.MaxContent()
            element is JsonPrimitive && element.content == "min-content" -> FlexBasisProperty.FlexBasis.MinContent()
            element is JsonPrimitive && element.content == "fit-content" -> FlexBasisProperty.FlexBasis.FitContent()
            element is JsonPrimitive -> FlexBasisProperty.FlexBasis.Keyword(element.content)
            element is JsonObject && element.containsKey("unit") ->
                FlexBasisProperty.FlexBasis.LengthValue(decoder.json.decodeFromJsonElement(IRLength.serializer(), element))
            element is JsonObject && element.containsKey("expr") ->
                FlexBasisProperty.FlexBasis.Expression((element["expr"] as JsonPrimitive).content)
            else -> FlexBasisProperty.FlexBasis.PercentageValue(decoder.json.decodeFromJsonElement(IRPercentage.serializer(), element))
        }
    }
}
