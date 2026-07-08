package app.irmodels.properties.typography

import app.irmodels.IRLength
import app.irmodels.IRProperty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class WordSpacingProperty(
    val spacing: WordSpacing
) : IRProperty {
    override val propertyName = "word-spacing"
}

/**
 * Word spacing with dual storage:
 * - pixels: Normalized pixel value (0 for normal)
 * - original: Original CSS format for regeneration
 *
 * Normalization:
 * - normal → 0px (no additional spacing)
 * - length values → normalized to pixels
 */
@Serializable(with = WordSpacingSerializer::class)
data class WordSpacing(
    val pixels: Double,
    val original: WordSpacingOriginal
) {
    @Serializable
    sealed interface WordSpacingOriginal {
        @Serializable
        data object Normal : WordSpacingOriginal

        @Serializable
        data class Length(val length: IRLength) : WordSpacingOriginal

        @Serializable
        data class GlobalKeyword(val keyword: String) : WordSpacingOriginal
    }

    companion object {
        /** Create from 'normal' keyword (0 additional spacing) */
        fun normal(): WordSpacing = WordSpacing(
            pixels = 0.0,
            original = WordSpacingOriginal.Normal
        )

        /** Create from length value */
        fun fromLength(length: IRLength): WordSpacing = WordSpacing(
            pixels = length.pixels ?: 0.0, // Fallback for relative units
            original = WordSpacingOriginal.Length(length)
        )

        /** Create from global keyword (inherit, initial, etc.) */
        fun fromGlobalKeyword(keyword: String): WordSpacing = WordSpacing(
            pixels = 0.0, // Default fallback
            original = WordSpacingOriginal.GlobalKeyword(keyword)
        )
    }
}

object WordSpacingSerializer : KSerializer<WordSpacing> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("WordSpacing") {
        element<Double>("px")
        element<JsonElement>("original")
    }

    override fun serialize(encoder: Encoder, value: WordSpacing) {
        require(encoder is JsonEncoder)
        val json = encoder.json
        encoder.encodeJsonElement(buildJsonObject {
            put("px", JsonPrimitive(value.pixels))
            put("original", when (val orig = value.original) {
                is WordSpacing.WordSpacingOriginal.Normal -> JsonPrimitive("normal")
                is WordSpacing.WordSpacingOriginal.Length -> buildJsonObject {
                    put("type", JsonPrimitive("length"))
                    put("value", json.encodeToJsonElement(IRLength.serializer(), orig.length))
                }
                is WordSpacing.WordSpacingOriginal.GlobalKeyword -> buildJsonObject {
                    put("type", JsonPrimitive("global"))
                    put("keyword", JsonPrimitive(orig.keyword))
                }
            })
        })
    }

    override fun deserialize(decoder: Decoder): WordSpacing {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()

        if (element is JsonObject) {
            val pixels = element["px"]?.jsonPrimitive?.double ?: 0.0
            val originalElement = element["original"]

            val original = when {
                originalElement is JsonPrimitive && originalElement.content == "normal" ->
                    WordSpacing.WordSpacingOriginal.Normal

                originalElement is JsonObject -> {
                    when (originalElement["type"]?.jsonPrimitive?.content) {
                        "length" -> WordSpacing.WordSpacingOriginal.Length(
                            decoder.json.decodeFromJsonElement(IRLength.serializer(), originalElement["value"]!!)
                        )
                        "global" -> WordSpacing.WordSpacingOriginal.GlobalKeyword(
                            originalElement["keyword"]!!.jsonPrimitive.content
                        )
                        else -> WordSpacing.WordSpacingOriginal.Normal
                    }
                }
                else -> WordSpacing.WordSpacingOriginal.Normal
            }

            return WordSpacing(pixels, original)
        }

        return WordSpacing.normal()
    }
}
