package app.irmodels.properties.columns

import app.irmodels.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class ColumnWidthProperty(
    val width: ColumnWidth
) : IRProperty {
    override val propertyName = "column-width"

    @Serializable(with = ColumnWidthSerializer::class)
    sealed interface ColumnWidth {
        @Serializable data class Auto(val unit: kotlin.Unit = kotlin.Unit) : ColumnWidth
        @Serializable data class LengthValue(val length: IRLength) : ColumnWidth
    }
}

object ColumnWidthSerializer : KSerializer<ColumnWidthProperty.ColumnWidth> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ColumnWidth")
    override fun serialize(encoder: Encoder, value: ColumnWidthProperty.ColumnWidth) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is ColumnWidthProperty.ColumnWidth.Auto -> JsonPrimitive("auto")
            is ColumnWidthProperty.ColumnWidth.LengthValue -> encoder.json.encodeToJsonElement(IRLength.serializer(), value.length)
        })
    }
    override fun deserialize(decoder: Decoder): ColumnWidthProperty.ColumnWidth {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.content == "auto" -> ColumnWidthProperty.ColumnWidth.Auto()
            element is JsonObject -> ColumnWidthProperty.ColumnWidth.LengthValue(decoder.json.decodeFromJsonElement(IRLength.serializer(), element))
            else -> ColumnWidthProperty.ColumnWidth.Auto()
        }
    }
}
