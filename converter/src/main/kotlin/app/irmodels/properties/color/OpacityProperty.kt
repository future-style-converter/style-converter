package app.irmodels.properties.color

import app.irmodels.IRProperty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class OpacityProperty(
    val value: Opacity
) : IRProperty {
    override val propertyName = "opacity"
}

/**
 * Opacity with dual storage:
 * - alpha: Normalized 0.0-1.0 value (null for expressions)
 * - original: Original CSS format for regeneration
 *
 * Normalization:
 * - 0.5 (number) → 0.5
 * - 50% → 0.5
 * - expressions → null (runtime-dependent)
 */
@Serializable(with = OpacitySerializer::class)
data class Opacity(
    val alpha: Double?,
    val original: OpacityOriginal
) {
    @Serializable
    sealed interface OpacityOriginal {
        @Serializable
        data class Number(val value: Double) : OpacityOriginal

        @Serializable
        data class Percentage(val value: Double) : OpacityOriginal

        @Serializable
        data class Expression(val expr: String) : OpacityOriginal

        @Serializable
        data class GlobalKeyword(val keyword: String) : OpacityOriginal
    }

    companion object {
        /** Create from number (0.0-1.0) */
        fun fromNumber(value: Double): Opacity = Opacity(
            alpha = value.coerceIn(0.0, 1.0),
            original = OpacityOriginal.Number(value)
        )

        /** Create from percentage (0-100%) */
        fun fromPercentage(percent: Double): Opacity = Opacity(
            alpha = (percent / 100.0).coerceIn(0.0, 1.0),
            original = OpacityOriginal.Percentage(percent)
        )

        /** Create from expression (calc, var, etc.) */
        fun fromExpression(expr: String): Opacity = Opacity(
            alpha = null, // Runtime-dependent
            original = OpacityOriginal.Expression(expr)
        )

        /** Create from global keyword (inherit, initial, etc.) */
        fun fromGlobalKeyword(keyword: String): Opacity = Opacity(
            alpha = null, // Context-dependent
            original = OpacityOriginal.GlobalKeyword(keyword)
        )
    }
}

object OpacitySerializer : KSerializer<Opacity> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Opacity") {
        element<Double?>("alpha")
        element<JsonElement>("original")
    }

    override fun serialize(encoder: Encoder, value: Opacity) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(buildJsonObject {
            value.alpha?.let { put("alpha", JsonPrimitive(it)) }
            put("original", when (val orig = value.original) {
                is Opacity.OpacityOriginal.Number -> buildJsonObject {
                    put("type", JsonPrimitive("number"))
                    put("value", JsonPrimitive(orig.value))
                }
                is Opacity.OpacityOriginal.Percentage -> buildJsonObject {
                    put("type", JsonPrimitive("percentage"))
                    put("value", JsonPrimitive(orig.value))
                }
                is Opacity.OpacityOriginal.Expression -> buildJsonObject {
                    put("type", JsonPrimitive("expression"))
                    put("expr", JsonPrimitive(orig.expr))
                }
                is Opacity.OpacityOriginal.GlobalKeyword -> buildJsonObject {
                    put("type", JsonPrimitive("global"))
                    put("keyword", JsonPrimitive(orig.keyword))
                }
            })
        })
    }

    override fun deserialize(decoder: Decoder): Opacity {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()

        if (element is JsonObject) {
            val alpha = element["alpha"]?.jsonPrimitive?.doubleOrNull
            val originalElement = element["original"]

            if (originalElement is JsonObject) {
                val original = when (originalElement["type"]?.jsonPrimitive?.content) {
                    "number" -> Opacity.OpacityOriginal.Number(
                        originalElement["value"]!!.jsonPrimitive.double
                    )
                    "percentage" -> Opacity.OpacityOriginal.Percentage(
                        originalElement["value"]!!.jsonPrimitive.double
                    )
                    "expression" -> Opacity.OpacityOriginal.Expression(
                        originalElement["expr"]!!.jsonPrimitive.content
                    )
                    "global" -> Opacity.OpacityOriginal.GlobalKeyword(
                        originalElement["keyword"]!!.jsonPrimitive.content
                    )
                    else -> Opacity.OpacityOriginal.Number(1.0)
                }
                return Opacity(alpha, original)
            }
        }

        return Opacity.fromNumber(1.0)
    }
}
