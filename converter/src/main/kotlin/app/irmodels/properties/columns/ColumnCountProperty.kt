package app.irmodels.properties.columns

import app.irmodels.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class ColumnCountProperty(
    val count: ColumnCount
) : IRProperty {
    override val propertyName = "column-count"

    @Serializable(with = ColumnCountSerializer::class)
    sealed interface ColumnCount {
        @Serializable data class Auto(val unit: kotlin.Unit = kotlin.Unit) : ColumnCount
        @Serializable data class Number(val value: IRNumber) : ColumnCount
    }
}

object ColumnCountSerializer : KSerializer<ColumnCountProperty.ColumnCount> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ColumnCount")
    override fun serialize(encoder: Encoder, value: ColumnCountProperty.ColumnCount) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is ColumnCountProperty.ColumnCount.Auto -> JsonPrimitive("auto")
            is ColumnCountProperty.ColumnCount.Number -> encoder.json.encodeToJsonElement(IRNumber.serializer(), value.value)
        })
    }
    override fun deserialize(decoder: Decoder): ColumnCountProperty.ColumnCount {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.content == "auto" -> ColumnCountProperty.ColumnCount.Auto()
            element is JsonObject -> ColumnCountProperty.ColumnCount.Number(decoder.json.decodeFromJsonElement(IRNumber.serializer(), element))
            else -> ColumnCountProperty.ColumnCount.Auto()
        }
    }
}
