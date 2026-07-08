package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

/**
 * Font weight with dual storage: normalized numeric (100-900) + original representation.
 *
 * CSS spec mappings:
 * - normal → 400
 * - bold → 700
 * - lighter/bolder → relative (cannot normalize without inherited context)
 * - 100-900 → as-is
 */
@Serializable(with = FontWeightPropertySerializer::class)
data class FontWeightProperty(
    /** Normalized weight 100-900, null for relative keywords (lighter/bolder) */
    val numericValue: Int?,
    /** Original representation for CSS regeneration */
    val original: FontWeightOriginal
) : IRProperty {
    override val propertyName = "font-weight"

    /** Original value representation */
    @Serializable
    sealed interface FontWeightOriginal {
        @Serializable data class Numeric(val value: Int) : FontWeightOriginal
        @Serializable data class Keyword(val keyword: String) : FontWeightOriginal
    }

    companion object {
        // CSS spec: normal = 400, bold = 700
        private const val NORMAL_WEIGHT = 400
        private const val BOLD_WEIGHT = 700

        /** Create from numeric value (100-900) */
        fun fromNumeric(value: Int): FontWeightProperty {
            val clamped = value.coerceIn(100, 900)
            return FontWeightProperty(
                numericValue = clamped,
                original = FontWeightOriginal.Numeric(value)
            )
        }

        /** Create from keyword */
        fun fromKeyword(keyword: String): FontWeightProperty {
            val lower = keyword.lowercase()
            val numeric = when (lower) {
                "normal" -> NORMAL_WEIGHT
                "bold" -> BOLD_WEIGHT
                "lighter", "bolder" -> null // Relative - cannot normalize without context
                else -> null
            }
            return FontWeightProperty(
                numericValue = numeric,
                original = FontWeightOriginal.Keyword(lower)
            )
        }
    }

    // Legacy compatibility - returns numeric or keyword string
    val weight: Any get() = numericValue ?: (original as? FontWeightOriginal.Keyword)?.keyword ?: "normal"
}

object FontWeightPropertySerializer : KSerializer<FontWeightProperty> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FontWeightProperty")

    override fun serialize(encoder: Encoder, value: FontWeightProperty) {
        require(encoder is JsonEncoder)
        // Optimized serialization:
        // - Pure numeric (400, 700, etc.) → just the number
        // - Keyword with normalization (normal→400, bold→700) → {"weight": N, "original": "keyword"}
        // - Relative keyword (lighter, bolder) → just the keyword string
        // - Numeric that matches original → just the number
        val orig = value.original
        val result: JsonElement = when {
            // Relative keywords (no numeric value) - output as string
            value.numericValue == null && orig is FontWeightProperty.FontWeightOriginal.Keyword -> {
                JsonPrimitive(orig.keyword)
            }
            // Numeric original that equals normalized - output as number
            orig is FontWeightProperty.FontWeightOriginal.Numeric && orig.value == value.numericValue -> {
                JsonPrimitive(value.numericValue)
            }
            // Keyword with normalization - output as object
            orig is FontWeightProperty.FontWeightOriginal.Keyword -> {
                buildJsonObject {
                    put("weight", value.numericValue!!)
                    put("original", orig.keyword)
                }
            }
            // Fallback - shouldn't happen but handle gracefully
            else -> JsonPrimitive(value.numericValue ?: 400)
        }
        encoder.encodeJsonElement(result)
    }

    override fun deserialize(decoder: Decoder): FontWeightProperty {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()

        return when {
            // Pure number
            element is JsonPrimitive && element.intOrNull != null -> {
                FontWeightProperty.fromNumeric(element.int)
            }
            // String keyword
            element is JsonPrimitive && element.isString -> {
                FontWeightProperty.fromKeyword(element.content)
            }
            // Object with weight and/or original
            element is JsonObject -> {
                val weight = element["weight"]?.jsonPrimitive?.intOrNull
                val original = element["original"]
                when {
                    original is JsonPrimitive && original.intOrNull != null -> {
                        FontWeightProperty(weight, FontWeightProperty.FontWeightOriginal.Numeric(original.int))
                    }
                    original is JsonPrimitive -> {
                        FontWeightProperty(weight, FontWeightProperty.FontWeightOriginal.Keyword(original.content))
                    }
                    weight != null -> {
                        FontWeightProperty(weight, FontWeightProperty.FontWeightOriginal.Numeric(weight))
                    }
                    else -> FontWeightProperty.fromKeyword("normal")
                }
            }
            else -> FontWeightProperty.fromKeyword("normal")
        }
    }
}
