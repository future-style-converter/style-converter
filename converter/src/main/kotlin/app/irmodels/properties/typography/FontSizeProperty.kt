package app.irmodels.properties.typography

import app.irmodels.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

/**
 * CSS `font-size` property.
 * Syntax: `<length> | <percentage> | <absolute-size> | <relative-size>`
 */
@Serializable
data class FontSizeProperty(val size: FontSize) : IRProperty {
    override val propertyName = "font-size"
}

/**
 * Font size with dual storage:
 * - pixels: Normalized pixel value (null for context-dependent values)
 * - original: Original CSS format for regeneration
 *
 * Absolute size keyword defaults (based on 16px medium):
 * - xx-small: 9px, x-small: 10px, small: 13px, medium: 16px
 * - large: 18px, x-large: 24px, xx-large: 32px, xxx-large: 48px
 */
@Serializable(with = FontSizeSerializer::class)
data class FontSize(
    val pixels: Double?,
    val original: FontSizeOriginal
) {
    @Serializable
    sealed interface FontSizeOriginal {
        @Serializable
        data class Length(val length: IRLength) : FontSizeOriginal

        @Serializable
        data class Percentage(val value: Double) : FontSizeOriginal

        @Serializable
        data class AbsoluteKeyword(val keyword: AbsoluteSize) : FontSizeOriginal

        @Serializable
        data class RelativeKeyword(val keyword: RelativeSize) : FontSizeOriginal

        @Serializable
        data class Expression(val expr: String) : FontSizeOriginal

        @Serializable
        data class GlobalKeyword(val keyword: String) : FontSizeOriginal
    }

    enum class AbsoluteSize {
        XX_SMALL, X_SMALL, SMALL, MEDIUM, LARGE, X_LARGE, XX_LARGE, XXX_LARGE
    }

    enum class RelativeSize {
        LARGER, SMALLER
    }

    companion object {
        // Typical browser defaults (based on 16px medium)
        private const val XX_SMALL_PX = 9.0
        private const val X_SMALL_PX = 10.0
        private const val SMALL_PX = 13.0
        private const val MEDIUM_PX = 16.0
        private const val LARGE_PX = 18.0
        private const val X_LARGE_PX = 24.0
        private const val XX_LARGE_PX = 32.0
        private const val XXX_LARGE_PX = 48.0

        /** Create from absolute size keyword */
        fun fromAbsoluteKeyword(keyword: AbsoluteSize): FontSize {
            val px = when (keyword) {
                AbsoluteSize.XX_SMALL -> XX_SMALL_PX
                AbsoluteSize.X_SMALL -> X_SMALL_PX
                AbsoluteSize.SMALL -> SMALL_PX
                AbsoluteSize.MEDIUM -> MEDIUM_PX
                AbsoluteSize.LARGE -> LARGE_PX
                AbsoluteSize.X_LARGE -> X_LARGE_PX
                AbsoluteSize.XX_LARGE -> XX_LARGE_PX
                AbsoluteSize.XXX_LARGE -> XXX_LARGE_PX
            }
            return FontSize(
                pixels = px,
                original = FontSizeOriginal.AbsoluteKeyword(keyword)
            )
        }

        /** Create from relative size keyword (cannot normalize without parent context) */
        fun fromRelativeKeyword(keyword: RelativeSize): FontSize = FontSize(
            pixels = null, // Context-dependent
            original = FontSizeOriginal.RelativeKeyword(keyword)
        )

        /** Create from length value */
        fun fromLength(length: IRLength): FontSize = FontSize(
            pixels = length.pixels, // Use IRLength's normalized pixels
            original = FontSizeOriginal.Length(length)
        )

        /** Create from percentage (cannot normalize without parent context) */
        fun fromPercentage(percentage: IRPercentage): FontSize = FontSize(
            pixels = null, // Context-dependent
            original = FontSizeOriginal.Percentage(percentage.value)
        )

        /** Create from expression (runtime-dependent) */
        fun fromExpression(expr: String): FontSize = FontSize(
            pixels = null,
            original = FontSizeOriginal.Expression(expr)
        )

        /** Create from global keyword (inherit, initial, etc.) */
        fun fromGlobalKeyword(keyword: String): FontSize = FontSize(
            pixels = null,
            original = FontSizeOriginal.GlobalKeyword(keyword)
        )
    }
}

object FontSizeSerializer : KSerializer<FontSize> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FontSize") {
        element<Double?>("px")
        element<JsonElement>("original")
    }

    override fun serialize(encoder: Encoder, value: FontSize) {
        require(encoder is JsonEncoder)
        val json = encoder.json
        encoder.encodeJsonElement(buildJsonObject {
            value.pixels?.let { put("px", JsonPrimitive(it)) }
            put("original", when (val orig = value.original) {
                is FontSize.FontSizeOriginal.Length -> buildJsonObject {
                    put("type", JsonPrimitive("length"))
                    put("value", json.encodeToJsonElement(IRLength.serializer(), orig.length))
                }
                is FontSize.FontSizeOriginal.Percentage -> buildJsonObject {
                    put("type", JsonPrimitive("percentage"))
                    put("value", JsonPrimitive(orig.value))
                }
                is FontSize.FontSizeOriginal.AbsoluteKeyword -> buildJsonObject {
                    put("type", JsonPrimitive("absolute"))
                    put("keyword", JsonPrimitive(orig.keyword.name.lowercase().replace("_", "-")))
                }
                is FontSize.FontSizeOriginal.RelativeKeyword -> buildJsonObject {
                    put("type", JsonPrimitive("relative"))
                    put("keyword", JsonPrimitive(orig.keyword.name.lowercase()))
                }
                is FontSize.FontSizeOriginal.Expression -> buildJsonObject {
                    put("type", JsonPrimitive("expression"))
                    put("expr", JsonPrimitive(orig.expr))
                }
                is FontSize.FontSizeOriginal.GlobalKeyword -> buildJsonObject {
                    put("type", JsonPrimitive("global"))
                    put("keyword", JsonPrimitive(orig.keyword))
                }
            })
        })
    }

    override fun deserialize(decoder: Decoder): FontSize {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()

        if (element is JsonObject) {
            val pixels = element["px"]?.jsonPrimitive?.doubleOrNull
            val originalElement = element["original"]

            if (originalElement is JsonObject) {
                val original = when (originalElement["type"]?.jsonPrimitive?.content) {
                    "length" -> FontSize.FontSizeOriginal.Length(
                        decoder.json.decodeFromJsonElement(IRLength.serializer(), originalElement["value"]!!)
                    )
                    "percentage" -> FontSize.FontSizeOriginal.Percentage(
                        originalElement["value"]!!.jsonPrimitive.double
                    )
                    "absolute" -> {
                        val kw = originalElement["keyword"]!!.jsonPrimitive.content
                            .uppercase().replace("-", "_")
                        FontSize.FontSizeOriginal.AbsoluteKeyword(FontSize.AbsoluteSize.valueOf(kw))
                    }
                    "relative" -> {
                        val kw = originalElement["keyword"]!!.jsonPrimitive.content.uppercase()
                        FontSize.FontSizeOriginal.RelativeKeyword(FontSize.RelativeSize.valueOf(kw))
                    }
                    "expression" -> FontSize.FontSizeOriginal.Expression(
                        originalElement["expr"]!!.jsonPrimitive.content
                    )
                    "global" -> FontSize.FontSizeOriginal.GlobalKeyword(
                        originalElement["keyword"]!!.jsonPrimitive.content
                    )
                    else -> FontSize.FontSizeOriginal.AbsoluteKeyword(FontSize.AbsoluteSize.MEDIUM)
                }
                return FontSize(pixels, original)
            }
        }

        // Fallback
        return FontSize.fromAbsoluteKeyword(FontSize.AbsoluteSize.MEDIUM)
    }
}
