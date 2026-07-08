package app.irmodels.properties.typography

import app.irmodels.IRAngle
import app.irmodels.IRProperty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class FontStyleProperty(
    val style: FontStyle
) : IRProperty {
    override val propertyName = "font-style"

    @Serializable(with = FontStyleSerializer::class)
    sealed interface FontStyle {
        @Serializable data class Normal(val unit: Unit = Unit) : FontStyle
        @Serializable data class Italic(val unit: Unit = Unit) : FontStyle
        @Serializable data class Oblique(val angle: IRAngle? = null) : FontStyle
    }
}

object FontStyleSerializer : KSerializer<FontStyleProperty.FontStyle> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FontStyle")
    override fun serialize(encoder: Encoder, value: FontStyleProperty.FontStyle) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is FontStyleProperty.FontStyle.Normal -> JsonPrimitive("normal")
            is FontStyleProperty.FontStyle.Italic -> JsonPrimitive("italic")
            is FontStyleProperty.FontStyle.Oblique -> if (value.angle != null)
                buildJsonObject { put("oblique", encoder.json.encodeToJsonElement(IRAngle.serializer(), value.angle)) }
            else JsonPrimitive("oblique")
        })
    }
    override fun deserialize(decoder: Decoder): FontStyleProperty.FontStyle {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.content == "normal" -> FontStyleProperty.FontStyle.Normal()
            element is JsonPrimitive && element.content == "italic" -> FontStyleProperty.FontStyle.Italic()
            element is JsonPrimitive && element.content == "oblique" -> FontStyleProperty.FontStyle.Oblique(null)
            element is JsonObject && element.containsKey("oblique") ->
                FontStyleProperty.FontStyle.Oblique(decoder.json.decodeFromJsonElement(IRAngle.serializer(), element["oblique"]!!))
            else -> FontStyleProperty.FontStyle.Normal()
        }
    }
}
