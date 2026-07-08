package app.irmodels.properties.layout.grid

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

/** Repeat count for grid repeat() function */
@Serializable(with = RepeatCountSerializer::class)
sealed interface RepeatCount {
    @Serializable data class Number(val value: Int) : RepeatCount
    @Serializable data class AutoFill(val unit: Unit = Unit) : RepeatCount
    @Serializable data class AutoFit(val unit: Unit = Unit) : RepeatCount
}

object RepeatCountSerializer : KSerializer<RepeatCount> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RepeatCount")

    override fun serialize(encoder: Encoder, value: RepeatCount) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is RepeatCount.Number -> JsonPrimitive(value.value)
            is RepeatCount.AutoFill -> JsonPrimitive("auto-fill")
            is RepeatCount.AutoFit -> JsonPrimitive("auto-fit")
        })
    }

    override fun deserialize(decoder: Decoder): RepeatCount {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.intOrNull != null -> RepeatCount.Number(element.int)
            element is JsonPrimitive && element.content == "auto-fill" -> RepeatCount.AutoFill()
            element is JsonPrimitive && element.content == "auto-fit" -> RepeatCount.AutoFit()
            else -> RepeatCount.Number(1)
        }
    }
}
