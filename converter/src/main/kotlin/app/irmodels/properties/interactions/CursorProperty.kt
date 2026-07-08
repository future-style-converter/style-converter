package app.irmodels.properties.interactions

import app.irmodels.IRProperty
import app.irmodels.IRUrl
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class CursorProperty(
    val value: Cursor
) : IRProperty {
    override val propertyName = "cursor"

    @Serializable(with = CursorSerializer::class)
    sealed interface Cursor {
        @Serializable data class Keyword(val value: CursorKeyword) : Cursor
        @Serializable data class Url(val url: IRUrl, val fallback: CursorKeyword?) : Cursor
        enum class CursorKeyword {
            AUTO, DEFAULT, NONE, CONTEXT_MENU, HELP, POINTER,
            PROGRESS, WAIT, CELL, CROSSHAIR, TEXT, VERTICAL_TEXT,
            ALIAS, COPY, MOVE, NO_DROP, NOT_ALLOWED, GRAB, GRABBING,
            ALL_SCROLL, COL_RESIZE, ROW_RESIZE, N_RESIZE, E_RESIZE,
            S_RESIZE, W_RESIZE, NE_RESIZE, NW_RESIZE, SE_RESIZE,
            SW_RESIZE, EW_RESIZE, NS_RESIZE, NESW_RESIZE, NWSE_RESIZE,
            ZOOM_IN, ZOOM_OUT
        }
    }
}

object CursorSerializer : KSerializer<CursorProperty.Cursor> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Cursor")
    override fun serialize(encoder: Encoder, value: CursorProperty.Cursor) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is CursorProperty.Cursor.Keyword -> JsonPrimitive(value.value.name.lowercase().replace("_", "-"))
            is CursorProperty.Cursor.Url -> buildJsonObject {
                put("url", encoder.json.encodeToJsonElement(IRUrl.serializer(), value.url))
                value.fallback?.let { put("fb", it.name.lowercase().replace("_", "-")) }
            }
        })
    }
    override fun deserialize(decoder: Decoder): CursorProperty.Cursor {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonObject && element.containsKey("url") -> {
                val url = decoder.json.decodeFromJsonElement(IRUrl.serializer(), element["url"]!!)
                val fallback = element["fb"]?.jsonPrimitive?.content?.let {
                    CursorProperty.Cursor.CursorKeyword.valueOf(it.uppercase().replace("-", "_"))
                }
                CursorProperty.Cursor.Url(url, fallback)
            }
            element is JsonPrimitive -> CursorProperty.Cursor.Keyword(
                CursorProperty.Cursor.CursorKeyword.valueOf(element.content.uppercase().replace("-", "_"))
            )
            else -> CursorProperty.Cursor.Keyword(CursorProperty.Cursor.CursorKeyword.DEFAULT)
        }
    }
}
