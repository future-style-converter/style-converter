package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class WidthProperty(
    val width: WidthValue
) : IRProperty {
    override val propertyName = "width"

    @Serializable(with = WidthValueSerializer::class)
    sealed interface WidthValue {
        @Serializable data class Auto(val unit: Unit = Unit) : WidthValue
        @Serializable data class LengthValue(val length: IRLength) : WidthValue
        @Serializable data class PercentageValue(val percentage: IRPercentage) : WidthValue
        @Serializable data class MinContent(val unit: Unit = Unit) : WidthValue
        @Serializable data class MaxContent(val unit: Unit = Unit) : WidthValue
        @Serializable data class FitContent(val maxSize: IRLength?) : WidthValue
        @Serializable data class Expression(val expression: String) : WidthValue
        @Serializable data class AnchorSize(
            val anchorName: String?, // null for implicit anchor
            val dimension: String    // width, height, block, inline, self-block, self-inline
        ) : WidthValue
        // css-values-5 §11 calc-size(<basis>, <calc-sum>) with a KEYWORD
        // basis and an affine expression over `size` — the runtime-resolvable
        // typed form CalcSizeParser produces (wave 42 lane W3). Pure-length
        // bases and size-free expressions are evaluated at parse time and
        // never reach this variant (they emit LengthValue instead).
        @Serializable data class CalcSize(
            val basis: String,    // auto|min-content|max-content|fit-content|stretch|content
            val factor: Double,   // multiplier on the resolved basis size
            val offsetPx: Double, // absolute px addend (may be negative)
            val original: String, // verbatim declaration for web browser replay
        ) : WidthValue
    }
}

object WidthValueSerializer : KSerializer<WidthProperty.WidthValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("WidthValue")
    override fun serialize(encoder: Encoder, value: WidthProperty.WidthValue) {
        require(encoder is JsonEncoder)
        val json = encoder.json
        encoder.encodeJsonElement(when (value) {
            is WidthProperty.WidthValue.LengthValue -> buildJsonObject {
                put("type", "length"); put("length", json.encodeToJsonElement(IRLength.serializer(), value.length))
            }
            is WidthProperty.WidthValue.PercentageValue -> buildJsonObject {
                put("type", "percentage"); put("value", value.percentage.value)
            }
            is WidthProperty.WidthValue.Auto -> JsonPrimitive("auto")
            is WidthProperty.WidthValue.MinContent -> JsonPrimitive("min-content")
            is WidthProperty.WidthValue.MaxContent -> JsonPrimitive("max-content")
            is WidthProperty.WidthValue.FitContent -> if (value.maxSize != null)
                buildJsonObject { put("fit-content", json.encodeToJsonElement(IRLength.serializer(), value.maxSize)) }
            else JsonPrimitive("fit-content")
            is WidthProperty.WidthValue.Expression -> buildJsonObject {
                put("type", "expression"); put("expr", value.expression)
            }
            is WidthProperty.WidthValue.AnchorSize -> buildJsonObject {
                put("type", "anchor-size")
                put("dimension", value.dimension)
                value.anchorName?.let { put("name", it) }
            }
            // calc-size wire shape (wave 42 lane W3) — flat, discriminated by
            // type like every other tagged WidthValue. Deliberately the SAME
            // field set the kotlinx-polymorphic MinMaxValue/MaxValue twins
            // emit, so all three runtimes decode ONE shape for all six
            // physical sizing longhands (schema leaves are permissive;
            // additive shape per schema/spec/02-values.md).
            is WidthProperty.WidthValue.CalcSize -> buildJsonObject {
                put("type", "calc-size")
                put("basis", value.basis)       // keyword the runtime resolves
                put("factor", value.factor)     // multiplier on `size`
                put("offsetPx", value.offsetPx) // absolute px addend
                put("original", value.original) // verbatim CSS for web replay
            }
        })
    }
    override fun deserialize(decoder: Decoder): WidthProperty.WidthValue {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonObject && element["type"]?.jsonPrimitive?.content == "length" ->
                WidthProperty.WidthValue.LengthValue(decoder.json.decodeFromJsonElement(IRLength.serializer(), element["length"]!!))
            element is JsonObject && element["type"]?.jsonPrimitive?.content == "percentage" ->
                WidthProperty.WidthValue.PercentageValue(IRPercentage(element["value"]?.jsonPrimitive?.double ?: 0.0))
            element is JsonObject && element["type"]?.jsonPrimitive?.content == "expression" ->
                WidthProperty.WidthValue.Expression(element["expr"]?.jsonPrimitive?.content ?: "")
            element is JsonObject && element["type"]?.jsonPrimitive?.content == "anchor-size" ->
                WidthProperty.WidthValue.AnchorSize(
                    anchorName = element["name"]?.jsonPrimitive?.content,
                    dimension = element["dimension"]?.jsonPrimitive?.content ?: "width"
                )
            // calc-size round-trip (wave 42 lane W3) — symmetric with the
            // encode branch above; defaults mirror the identity expression
            // `calc-size(auto, size)` so a partially-formed object degrades
            // to the basis itself rather than throwing.
            element is JsonObject && element["type"]?.jsonPrimitive?.content == "calc-size" ->
                WidthProperty.WidthValue.CalcSize(
                    basis = element["basis"]?.jsonPrimitive?.content ?: "auto",
                    factor = element["factor"]?.jsonPrimitive?.double ?: 1.0,
                    offsetPx = element["offsetPx"]?.jsonPrimitive?.double ?: 0.0,
                    original = element["original"]?.jsonPrimitive?.content ?: ""
                )
            element is JsonObject && element.containsKey("fit-content") ->
                WidthProperty.WidthValue.FitContent(decoder.json.decodeFromJsonElement(IRLength.serializer(), element["fit-content"]!!))
            element is JsonPrimitive -> when (element.content) {
                "auto" -> WidthProperty.WidthValue.Auto()
                "min-content" -> WidthProperty.WidthValue.MinContent()
                "max-content" -> WidthProperty.WidthValue.MaxContent()
                "fit-content" -> WidthProperty.WidthValue.FitContent(null)
                else -> WidthProperty.WidthValue.Auto()
            }
            else -> WidthProperty.WidthValue.Auto()
        }
    }
}
