package app.irmodels.properties.typography

import app.irmodels.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class LineHeightProperty(
    val height: LineHeight
) : IRProperty {
    override val propertyName = "line-height"
}

/**
 * Line height with dual storage:
 * - multiplier: Normalized unitless multiplier (null for absolute lengths or runtime-dependent)
 * - original: Original CSS format for regeneration
 *
 * Normalization:
 * - normal → 1.2 (common browser default)
 * - 1.5 (number) → 1.5
 * - 150% → 1.5
 * - 24px (absolute length) → null (needs font-size context)
 * - calc(), var() → null (runtime-dependent)
 */
@Serializable(with = LineHeightSerializer::class)
data class LineHeight(
    val multiplier: Double?,
    val original: LineHeightOriginal
) {
    @Serializable
    sealed interface LineHeightOriginal {
        @Serializable
        data object Normal : LineHeightOriginal

        @Serializable
        data class Number(val value: Double) : LineHeightOriginal

        @Serializable
        data class Percentage(val value: Double) : LineHeightOriginal

        @Serializable
        data class Length(val length: IRLength) : LineHeightOriginal

        @Serializable
        data class Expression(val expr: String) : LineHeightOriginal

        @Serializable
        data class Keyword(val keyword: String) : LineHeightOriginal
    }

    companion object {
        /** Default line-height multiplier for 'normal' keyword (common browser default) */
        private const val NORMAL_MULTIPLIER = 1.2

        /** Create from 'normal' keyword */
        fun normal(): LineHeight = LineHeight(
            multiplier = NORMAL_MULTIPLIER,
            original = LineHeightOriginal.Normal
        )

        /** Create from unitless number (already a multiplier) */
        fun fromNumber(value: Double): LineHeight = LineHeight(
            multiplier = value,
            original = LineHeightOriginal.Number(value)
        )

        /** Create from percentage (e.g., 150% → 1.5) */
        fun fromPercentage(percentage: IRPercentage): LineHeight = LineHeight(
            multiplier = percentage.value / 100.0,
            original = LineHeightOriginal.Percentage(percentage.value)
        )

        /** Create from absolute length (cannot normalize without font-size context) */
        fun fromLength(length: IRLength): LineHeight = LineHeight(
            multiplier = null, // Cannot normalize without knowing font-size
            original = LineHeightOriginal.Length(length)
        )

        /** Create from expression (runtime-dependent) */
        fun fromExpression(expr: String): LineHeight = LineHeight(
            multiplier = null,
            original = LineHeightOriginal.Expression(expr)
        )

        /** Create from global keyword (inherit, initial, etc.) */
        fun fromKeyword(keyword: String): LineHeight = LineHeight(
            multiplier = null,
            original = LineHeightOriginal.Keyword(keyword)
        )
    }
}

object LineHeightSerializer : KSerializer<LineHeight> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LineHeight") {
        element<Double?>("multiplier")
        element<JsonElement>("original")
    }

    override fun serialize(encoder: Encoder, value: LineHeight) {
        require(encoder is JsonEncoder)
        val json = encoder.json
        encoder.encodeJsonElement(buildJsonObject {
            value.multiplier?.let { put("multiplier", JsonPrimitive(it)) }
            put("original", when (val orig = value.original) {
                is LineHeight.LineHeightOriginal.Normal -> JsonPrimitive("normal")
                is LineHeight.LineHeightOriginal.Number -> buildJsonObject {
                    put("type", JsonPrimitive("number"))
                    put("value", JsonPrimitive(orig.value))
                }
                is LineHeight.LineHeightOriginal.Percentage -> buildJsonObject {
                    put("type", JsonPrimitive("percentage"))
                    put("value", JsonPrimitive(orig.value))
                }
                is LineHeight.LineHeightOriginal.Length -> buildJsonObject {
                    put("type", JsonPrimitive("length"))
                    put("value", json.encodeToJsonElement(IRLength.serializer(), orig.length))
                }
                is LineHeight.LineHeightOriginal.Expression -> buildJsonObject {
                    put("type", JsonPrimitive("expression"))
                    put("expr", JsonPrimitive(orig.expr))
                }
                is LineHeight.LineHeightOriginal.Keyword -> buildJsonObject {
                    put("type", JsonPrimitive("keyword"))
                    put("keyword", JsonPrimitive(orig.keyword))
                }
            })
        })
    }

    override fun deserialize(decoder: Decoder): LineHeight {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()

        if (element is JsonObject) {
            val multiplier = element["multiplier"]?.jsonPrimitive?.doubleOrNull
            val originalElement = element["original"]

            val original = when {
                originalElement is JsonPrimitive && originalElement.content == "normal" ->
                    LineHeight.LineHeightOriginal.Normal

                originalElement is JsonObject -> {
                    when (originalElement["type"]?.jsonPrimitive?.content) {
                        "number" -> LineHeight.LineHeightOriginal.Number(
                            originalElement["value"]!!.jsonPrimitive.double
                        )
                        "percentage" -> LineHeight.LineHeightOriginal.Percentage(
                            originalElement["value"]!!.jsonPrimitive.double
                        )
                        "length" -> LineHeight.LineHeightOriginal.Length(
                            decoder.json.decodeFromJsonElement(
                                IRLength.serializer(),
                                originalElement["value"]!!
                            )
                        )
                        "expression" -> LineHeight.LineHeightOriginal.Expression(
                            originalElement["expr"]!!.jsonPrimitive.content
                        )
                        "keyword" -> LineHeight.LineHeightOriginal.Keyword(
                            originalElement["keyword"]!!.jsonPrimitive.content
                        )
                        else -> LineHeight.LineHeightOriginal.Normal
                    }
                }
                else -> LineHeight.LineHeightOriginal.Normal
            }

            return LineHeight(multiplier, original)
        }

        // Fallback for old format compatibility
        return LineHeight.normal()
    }
}
