package app.irmodels.properties.background

import app.irmodels.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class BackgroundSizeProperty(
    val sizes: List<BackgroundSize>
) : IRProperty {
    override val propertyName = "background-size"

    @Serializable(with = BackgroundSizeSerializer::class)
    sealed interface BackgroundSize {
        @Serializable data class Keyword(val value: SizeKeyword) : BackgroundSize
        @Serializable data class LengthValue(val width: IRLength, val height: IRLength?) : BackgroundSize
        @Serializable data class PercentageValue(val width: IRPercentage, val height: IRPercentage?) : BackgroundSize
        @Serializable data class GlobalKeyword(val keyword: String) : BackgroundSize // inherit, initial, unset, revert
        enum class SizeKeyword { COVER, CONTAIN, AUTO }
    }
}

object BackgroundSizeSerializer : KSerializer<BackgroundSizeProperty.BackgroundSize> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BackgroundSize")
    override fun serialize(encoder: Encoder, value: BackgroundSizeProperty.BackgroundSize) {
        require(encoder is JsonEncoder)
        val json = encoder.json
        encoder.encodeJsonElement(when (value) {
            is BackgroundSizeProperty.BackgroundSize.Keyword -> JsonPrimitive(value.value.name.lowercase())
            is BackgroundSizeProperty.BackgroundSize.GlobalKeyword -> JsonPrimitive(value.keyword)
            is BackgroundSizeProperty.BackgroundSize.LengthValue -> buildJsonObject {
                put("w", json.encodeToJsonElement(IRLength.serializer(), value.width))
                value.height?.let { put("h", json.encodeToJsonElement(IRLength.serializer(), it)) }
            }
            is BackgroundSizeProperty.BackgroundSize.PercentageValue -> buildJsonObject {
                put("w", json.encodeToJsonElement(IRPercentage.serializer(), value.width))
                value.height?.let { put("h", json.encodeToJsonElement(IRPercentage.serializer(), it)) }
            }
        })
    }
    override fun deserialize(decoder: Decoder): BackgroundSizeProperty.BackgroundSize {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        val globalKeywords = setOf("inherit", "initial", "unset", "revert", "revert-layer")
        return when {
            element is JsonPrimitive && element.content in globalKeywords ->
                BackgroundSizeProperty.BackgroundSize.GlobalKeyword(element.content)
            element is JsonPrimitive -> {
                val keyword = when (element.content.uppercase()) {
                    "COVER" -> BackgroundSizeProperty.BackgroundSize.SizeKeyword.COVER
                    "CONTAIN" -> BackgroundSizeProperty.BackgroundSize.SizeKeyword.CONTAIN
                    else -> BackgroundSizeProperty.BackgroundSize.SizeKeyword.AUTO
                }
                BackgroundSizeProperty.BackgroundSize.Keyword(keyword)
            }
            element is JsonObject && element.containsKey("w") -> {
                val width = element["w"]!!.jsonObject
                if (width.containsKey("unit")) {
                    val w = decoder.json.decodeFromJsonElement(IRLength.serializer(), width)
                    val h = element["h"]?.let { decoder.json.decodeFromJsonElement(IRLength.serializer(), it) }
                    BackgroundSizeProperty.BackgroundSize.LengthValue(w, h)
                } else {
                    val w = decoder.json.decodeFromJsonElement(IRPercentage.serializer(), width)
                    val h = element["h"]?.let { decoder.json.decodeFromJsonElement(IRPercentage.serializer(), it) }
                    BackgroundSizeProperty.BackgroundSize.PercentageValue(w, h)
                }
            }
            else -> BackgroundSizeProperty.BackgroundSize.Keyword(BackgroundSizeProperty.BackgroundSize.SizeKeyword.AUTO)
        }
    }
}
