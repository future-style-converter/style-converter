package app.irmodels.properties.typography

import app.irmodels.IRLength
import app.irmodels.IRProperty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class LetterSpacingProperty(
    val spacing: LetterSpacing
) : IRProperty {
    override val propertyName = "letter-spacing"
}

/**
 * Letter spacing with dual storage:
 * - pixels: Normalized pixel value (0 for normal)
 * - original: Original CSS format for regeneration
 *
 * Normalization:
 * - normal → 0px (no additional spacing)
 * - length values → normalized to pixels
 */
@Serializable(with = LetterSpacingSerializer::class)
data class LetterSpacing(
    val pixels: Double,
    val original: LetterSpacingOriginal
) {
    @Serializable
    sealed interface LetterSpacingOriginal {
        @Serializable
        data object Normal : LetterSpacingOriginal

        @Serializable
        data class Length(val length: IRLength) : LetterSpacingOriginal

        @Serializable
        data class GlobalKeyword(val keyword: String) : LetterSpacingOriginal
    }

    companion object {
        /** Create from 'normal' keyword (0 additional spacing) */
        fun normal(): LetterSpacing = LetterSpacing(
            pixels = 0.0,
            original = LetterSpacingOriginal.Normal
        )

        /** Create from length value */
        fun fromLength(length: IRLength): LetterSpacing = LetterSpacing(
            pixels = length.pixels ?: 0.0, // Fallback for relative units
            original = LetterSpacingOriginal.Length(length)
        )

        /** Create from global keyword (inherit, initial, etc.) */
        fun fromGlobalKeyword(keyword: String): LetterSpacing = LetterSpacing(
            pixels = 0.0, // Default fallback
            original = LetterSpacingOriginal.GlobalKeyword(keyword)
        )
    }
}

object LetterSpacingSerializer : KSerializer<LetterSpacing> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LetterSpacing") {
        element<Double>("px")
        element<JsonElement>("original")
    }

    override fun serialize(encoder: Encoder, value: LetterSpacing) {
        require(encoder is JsonEncoder)
        val json = encoder.json
        encoder.encodeJsonElement(buildJsonObject {
            put("px", JsonPrimitive(value.pixels))
            put("original", when (val orig = value.original) {
                is LetterSpacing.LetterSpacingOriginal.Normal -> JsonPrimitive("normal")
                is LetterSpacing.LetterSpacingOriginal.Length -> buildJsonObject {
                    put("type", JsonPrimitive("length"))
                    put("value", json.encodeToJsonElement(IRLength.serializer(), orig.length))
                }
                is LetterSpacing.LetterSpacingOriginal.GlobalKeyword -> buildJsonObject {
                    put("type", JsonPrimitive("global"))
                    put("keyword", JsonPrimitive(orig.keyword))
                }
            })
        })
    }

    override fun deserialize(decoder: Decoder): LetterSpacing {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()

        if (element is JsonObject) {
            val pixels = element["px"]?.jsonPrimitive?.double ?: 0.0
            val originalElement = element["original"]

            val original = when {
                originalElement is JsonPrimitive && originalElement.content == "normal" ->
                    LetterSpacing.LetterSpacingOriginal.Normal

                originalElement is JsonObject -> {
                    when (originalElement["type"]?.jsonPrimitive?.content) {
                        "length" -> LetterSpacing.LetterSpacingOriginal.Length(
                            decoder.json.decodeFromJsonElement(IRLength.serializer(), originalElement["value"]!!)
                        )
                        "global" -> LetterSpacing.LetterSpacingOriginal.GlobalKeyword(
                            originalElement["keyword"]!!.jsonPrimitive.content
                        )
                        else -> LetterSpacing.LetterSpacingOriginal.Normal
                    }
                }
                else -> LetterSpacing.LetterSpacingOriginal.Normal
            }

            return LetterSpacing(pixels, original)
        }

        return LetterSpacing.normal()
    }
}
