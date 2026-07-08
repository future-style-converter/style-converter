package app.irmodels.properties.layout.position

import app.irmodels.IRProperty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class ZIndexProperty(
    val value: ZIndex
) : IRProperty {
    override val propertyName = "z-index"
}

/**
 * Z-index with dual storage:
 * - intValue: Normalized integer value (null for expressions)
 * - original: Original CSS format for regeneration
 *
 * Normalization:
 * - auto → 0 (default stacking context)
 * - integer values → as-is
 * - calc/var expressions → null (runtime-dependent)
 */
@Serializable(with = ZIndexSerializer::class)
data class ZIndex(
    val intValue: Int?,
    val original: ZIndexOriginal
) {
    @Serializable
    sealed interface ZIndexOriginal {
        @Serializable
        data object Auto : ZIndexOriginal

        @Serializable
        data class Integer(val value: Int) : ZIndexOriginal

        @Serializable
        data class Expression(val expr: String) : ZIndexOriginal

        @Serializable
        data class GlobalKeyword(val keyword: String) : ZIndexOriginal
    }

    companion object {
        /** Create from 'auto' keyword (default stacking context = 0) */
        fun auto(): ZIndex = ZIndex(
            intValue = 0,
            original = ZIndexOriginal.Auto
        )

        /** Create from integer value */
        fun fromInteger(value: Int): ZIndex = ZIndex(
            intValue = value,
            original = ZIndexOriginal.Integer(value)
        )

        /** Create from expression (calc, var, etc.) */
        fun fromExpression(expr: String): ZIndex = ZIndex(
            intValue = null, // Runtime-dependent
            original = ZIndexOriginal.Expression(expr)
        )

        /** Create from global keyword (inherit, initial, etc.) */
        fun fromGlobalKeyword(keyword: String): ZIndex = ZIndex(
            intValue = null, // Context-dependent
            original = ZIndexOriginal.GlobalKeyword(keyword)
        )
    }
}

object ZIndexSerializer : KSerializer<ZIndex> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ZIndex") {
        element<Int?>("value")
        element<JsonElement>("original")
    }

    override fun serialize(encoder: Encoder, value: ZIndex) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(buildJsonObject {
            value.intValue?.let { put("value", JsonPrimitive(it)) }
            put("original", when (val orig = value.original) {
                is ZIndex.ZIndexOriginal.Auto -> JsonPrimitive("auto")
                is ZIndex.ZIndexOriginal.Integer -> buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("value", JsonPrimitive(orig.value))
                }
                is ZIndex.ZIndexOriginal.Expression -> buildJsonObject {
                    put("type", JsonPrimitive("expression"))
                    put("expr", JsonPrimitive(orig.expr))
                }
                is ZIndex.ZIndexOriginal.GlobalKeyword -> buildJsonObject {
                    put("type", JsonPrimitive("global"))
                    put("keyword", JsonPrimitive(orig.keyword))
                }
            })
        })
    }

    override fun deserialize(decoder: Decoder): ZIndex {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()

        if (element is JsonObject) {
            val intValue = element["value"]?.jsonPrimitive?.intOrNull
            val originalElement = element["original"]

            val original = when {
                originalElement is JsonPrimitive && originalElement.content == "auto" ->
                    ZIndex.ZIndexOriginal.Auto

                originalElement is JsonObject -> {
                    when (originalElement["type"]?.jsonPrimitive?.content) {
                        "integer" -> ZIndex.ZIndexOriginal.Integer(
                            originalElement["value"]!!.jsonPrimitive.int
                        )
                        "expression" -> ZIndex.ZIndexOriginal.Expression(
                            originalElement["expr"]!!.jsonPrimitive.content
                        )
                        "global" -> ZIndex.ZIndexOriginal.GlobalKeyword(
                            originalElement["keyword"]!!.jsonPrimitive.content
                        )
                        else -> ZIndex.ZIndexOriginal.Auto
                    }
                }
                else -> ZIndex.ZIndexOriginal.Auto
            }

            return ZIndex(intValue, original)
        }

        return ZIndex.auto()
    }
}
