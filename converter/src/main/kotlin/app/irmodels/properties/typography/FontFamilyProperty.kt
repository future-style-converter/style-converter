package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class FontFamilyProperty(
    val families: List<FontFamily>
) : IRProperty {
    override val propertyName = "font-family"

    @Serializable(with = FontFamilySerializer::class)
    sealed interface FontFamily {
        @Serializable data class Named(val name: String) : FontFamily
        @Serializable data class Generic(val type: GenericFamily) : FontFamily
        enum class GenericFamily {
            SERIF, SANS_SERIF, MONOSPACE, CURSIVE, FANTASY,
            SYSTEM_UI, UI_SERIF, UI_SANS_SERIF, UI_MONOSPACE,
            UI_ROUNDED, EMOJI, MATH, FANGSONG
        }
    }
}

object FontFamilySerializer : KSerializer<FontFamilyProperty.FontFamily> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FontFamily")
    override fun serialize(encoder: Encoder, value: FontFamilyProperty.FontFamily) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is FontFamilyProperty.FontFamily.Named -> JsonPrimitive(value.name)
            is FontFamilyProperty.FontFamily.Generic -> JsonPrimitive(value.type.name.lowercase().replace("_", "-"))
        })
    }
    override fun deserialize(decoder: Decoder): FontFamilyProperty.FontFamily {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive -> {
                val normalized = element.content.uppercase().replace("-", "_")
                val genericFamily = FontFamilyProperty.FontFamily.GenericFamily.values().find { it.name == normalized }
                if (genericFamily != null) FontFamilyProperty.FontFamily.Generic(genericFamily)
                else FontFamilyProperty.FontFamily.Named(element.content)
            }
            else -> FontFamilyProperty.FontFamily.Named("sans-serif")
        }
    }
}
