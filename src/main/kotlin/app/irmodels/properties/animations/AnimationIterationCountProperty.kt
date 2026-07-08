package app.irmodels.properties.animations

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class AnimationIterationCountProperty(
    val counts: List<IterationCount>
) : IRProperty {
    override val propertyName = "animation-iteration-count"

    @Serializable(with = IterationCountSerializer::class)
    sealed interface IterationCount {
        @Serializable data class Infinite(val unit: Unit = Unit) : IterationCount
        @Serializable data class Number(val value: IRNumber) : IterationCount
    }
}

object IterationCountSerializer : KSerializer<AnimationIterationCountProperty.IterationCount> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IterationCount")
    override fun serialize(encoder: Encoder, value: AnimationIterationCountProperty.IterationCount) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is AnimationIterationCountProperty.IterationCount.Infinite -> JsonPrimitive("infinite")
            is AnimationIterationCountProperty.IterationCount.Number -> encoder.json.encodeToJsonElement(IRNumber.serializer(), value.value)
        })
    }
    override fun deserialize(decoder: Decoder): AnimationIterationCountProperty.IterationCount {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.content == "infinite" -> AnimationIterationCountProperty.IterationCount.Infinite()
            element is JsonObject -> AnimationIterationCountProperty.IterationCount.Number(decoder.json.decodeFromJsonElement(IRNumber.serializer(), element))
            else -> AnimationIterationCountProperty.IterationCount.Number(IRNumber(1.0))
        }
    }
}
