package app.irmodels.properties.typography

import app.irmodels.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

/**
 * CSS `font-stretch` property.
 * Syntax: `normal | <keyword> | <percentage>`
 */
@Serializable
data class FontStretchProperty(
    val stretch: FontStretch
) : IRProperty {
    override val propertyName = "font-stretch"
}

/**
 * Font stretch with dual storage:
 * - percentage: Normalized percentage value (50-200%)
 * - original: Original CSS format for regeneration
 *
 * Keyword to percentage mappings (CSS spec):
 * - ultra-condensed: 50%, extra-condensed: 62.5%, condensed: 75%, semi-condensed: 87.5%
 * - normal: 100%
 * - semi-expanded: 112.5%, expanded: 125%, extra-expanded: 150%, ultra-expanded: 200%
 */
@Serializable(with = FontStretchSerializer::class)
data class FontStretch(
    val percentage: Double,
    val original: FontStretchOriginal
) {
    @Serializable
    sealed interface FontStretchOriginal {
        @Serializable
        data class Keyword(val keyword: StretchKeyword) : FontStretchOriginal

        @Serializable
        data class Percentage(val value: Double) : FontStretchOriginal

        @Serializable
        data class GlobalKeyword(val keyword: String) : FontStretchOriginal
    }

    enum class StretchKeyword {
        ULTRA_CONDENSED,
        EXTRA_CONDENSED,
        CONDENSED,
        SEMI_CONDENSED,
        NORMAL,
        SEMI_EXPANDED,
        EXPANDED,
        EXTRA_EXPANDED,
        ULTRA_EXPANDED
    }

    companion object {
        // CSS spec percentage values for each keyword
        private const val ULTRA_CONDENSED_PCT = 50.0
        private const val EXTRA_CONDENSED_PCT = 62.5
        private const val CONDENSED_PCT = 75.0
        private const val SEMI_CONDENSED_PCT = 87.5
        private const val NORMAL_PCT = 100.0
        private const val SEMI_EXPANDED_PCT = 112.5
        private const val EXPANDED_PCT = 125.0
        private const val EXTRA_EXPANDED_PCT = 150.0
        private const val ULTRA_EXPANDED_PCT = 200.0

        /** Create from keyword */
        fun fromKeyword(keyword: StretchKeyword): FontStretch {
            val pct = when (keyword) {
                StretchKeyword.ULTRA_CONDENSED -> ULTRA_CONDENSED_PCT
                StretchKeyword.EXTRA_CONDENSED -> EXTRA_CONDENSED_PCT
                StretchKeyword.CONDENSED -> CONDENSED_PCT
                StretchKeyword.SEMI_CONDENSED -> SEMI_CONDENSED_PCT
                StretchKeyword.NORMAL -> NORMAL_PCT
                StretchKeyword.SEMI_EXPANDED -> SEMI_EXPANDED_PCT
                StretchKeyword.EXPANDED -> EXPANDED_PCT
                StretchKeyword.EXTRA_EXPANDED -> EXTRA_EXPANDED_PCT
                StretchKeyword.ULTRA_EXPANDED -> ULTRA_EXPANDED_PCT
            }
            return FontStretch(
                percentage = pct,
                original = FontStretchOriginal.Keyword(keyword)
            )
        }

        /** Create from percentage value */
        fun fromPercentage(value: Double): FontStretch = FontStretch(
            percentage = value.coerceIn(1.0, 1000.0), // CSS allows 1% to 1000%
            original = FontStretchOriginal.Percentage(value)
        )

        /** Create from global keyword (inherit, initial, etc.) */
        fun fromGlobalKeyword(keyword: String): FontStretch = FontStretch(
            percentage = NORMAL_PCT, // Default fallback
            original = FontStretchOriginal.GlobalKeyword(keyword)
        )
    }
}

object FontStretchSerializer : KSerializer<FontStretch> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FontStretch") {
        element<Double>("percentage")
        element<JsonElement>("original")
    }

    override fun serialize(encoder: Encoder, value: FontStretch) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(buildJsonObject {
            put("percentage", JsonPrimitive(value.percentage))
            put("original", when (val orig = value.original) {
                is FontStretch.FontStretchOriginal.Keyword -> buildJsonObject {
                    put("type", JsonPrimitive("keyword"))
                    put("keyword", JsonPrimitive(orig.keyword.name.lowercase().replace("_", "-")))
                }
                is FontStretch.FontStretchOriginal.Percentage -> buildJsonObject {
                    put("type", JsonPrimitive("percentage"))
                    put("value", JsonPrimitive(orig.value))
                }
                is FontStretch.FontStretchOriginal.GlobalKeyword -> buildJsonObject {
                    put("type", JsonPrimitive("global"))
                    put("keyword", JsonPrimitive(orig.keyword))
                }
            })
        })
    }

    override fun deserialize(decoder: Decoder): FontStretch {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()

        if (element is JsonObject) {
            val percentage = element["percentage"]?.jsonPrimitive?.double ?: 100.0
            val originalElement = element["original"]

            if (originalElement is JsonObject) {
                val original = when (originalElement["type"]?.jsonPrimitive?.content) {
                    "keyword" -> {
                        val kw = originalElement["keyword"]!!.jsonPrimitive.content
                            .uppercase().replace("-", "_")
                        FontStretch.FontStretchOriginal.Keyword(FontStretch.StretchKeyword.valueOf(kw))
                    }
                    "percentage" -> FontStretch.FontStretchOriginal.Percentage(
                        originalElement["value"]!!.jsonPrimitive.double
                    )
                    "global" -> FontStretch.FontStretchOriginal.GlobalKeyword(
                        originalElement["keyword"]!!.jsonPrimitive.content
                    )
                    else -> FontStretch.FontStretchOriginal.Keyword(FontStretch.StretchKeyword.NORMAL)
                }
                return FontStretch(percentage, original)
            }
        }

        // Fallback
        return FontStretch.fromKeyword(FontStretch.StretchKeyword.NORMAL)
    }
}
