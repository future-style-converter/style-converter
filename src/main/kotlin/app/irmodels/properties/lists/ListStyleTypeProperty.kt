package app.irmodels.properties.lists

import app.irmodels.IRProperty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class ListStyleTypeProperty(
    val type: ListStyleType
) : IRProperty {
    override val propertyName = "list-style-type"

    @Serializable(with = ListStyleTypeSerializer::class)
    sealed interface ListStyleType {
        @Serializable data class None(val unit: kotlin.Unit = kotlin.Unit) : ListStyleType
        @Serializable data class Keyword(val value: ListMarkerKeyword) : ListStyleType
        @Serializable data class CustomString(val value: String) : ListStyleType
        @Serializable data class Symbols(val type: SymbolsType, val symbols: List<String>) : ListStyleType
    }

    enum class SymbolsType { CYCLIC, NUMERIC, ALPHABETIC, SYMBOLIC, FIXED }

    enum class ListMarkerKeyword {
        DISC, CIRCLE, SQUARE, DECIMAL, DECIMAL_LEADING_ZERO,
        LOWER_ROMAN, UPPER_ROMAN, LOWER_GREEK, LOWER_LATIN,
        UPPER_LATIN, LOWER_ALPHA, UPPER_ALPHA, ARMENIAN,
        GEORGIAN, HEBREW, HIRAGANA, KATAKANA
    }
}

object ListStyleTypeSerializer : KSerializer<ListStyleTypeProperty.ListStyleType> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ListStyleType")
    override fun serialize(encoder: Encoder, value: ListStyleTypeProperty.ListStyleType) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is ListStyleTypeProperty.ListStyleType.None -> JsonPrimitive("none")
            is ListStyleTypeProperty.ListStyleType.Keyword -> JsonPrimitive(value.value.name.lowercase().replace("_", "-"))
            is ListStyleTypeProperty.ListStyleType.CustomString -> JsonPrimitive(value.value)
            is ListStyleTypeProperty.ListStyleType.Symbols -> buildJsonObject {
                put("type", "symbols")
                put("symbolsType", value.type.name.lowercase())
                put("symbols", JsonArray(value.symbols.map { JsonPrimitive(it) }))
            }
        })
    }
    override fun deserialize(decoder: Decoder): ListStyleTypeProperty.ListStyleType {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.content == "none" -> ListStyleTypeProperty.ListStyleType.None()
            element is JsonObject && element["type"]?.jsonPrimitive?.content == "symbols" -> {
                val typeStr = element["symbolsType"]?.jsonPrimitive?.content ?: "cyclic"
                val type = ListStyleTypeProperty.SymbolsType.values().find { it.name.lowercase() == typeStr }
                    ?: ListStyleTypeProperty.SymbolsType.CYCLIC
                val symbols = element["symbols"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                ListStyleTypeProperty.ListStyleType.Symbols(type, symbols)
            }
            element is JsonPrimitive -> {
                val normalized = element.content.uppercase().replace("-", "_")
                val keyword = ListStyleTypeProperty.ListMarkerKeyword.values().find { it.name == normalized }
                if (keyword != null) ListStyleTypeProperty.ListStyleType.Keyword(keyword)
                else ListStyleTypeProperty.ListStyleType.CustomString(element.content)
            }
            else -> ListStyleTypeProperty.ListStyleType.None()
        }
    }
}
