package app.irmodels.properties.layout.grid

import app.irmodels.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

/** Track size value for grid templates */
@Serializable(with = TrackSizeSerializer::class)
sealed interface TrackSize {
    @Serializable data class LengthValue(val length: IRLength) : TrackSize
    @Serializable data class PercentageValue(val percentage: IRPercentage) : TrackSize
    @Serializable data class Flex(val value: IRNumber) : TrackSize
    @Serializable data class MinContent(val unit: Unit = Unit) : TrackSize
    @Serializable data class MaxContent(val unit: Unit = Unit) : TrackSize
    @Serializable data class Auto(val unit: Unit = Unit) : TrackSize
    @Serializable data class FitContent(val size: IRLength) : TrackSize
    @Serializable data class MinMax(val min: TrackSize, val max: TrackSize) : TrackSize
    @Serializable data class Repeat(val count: RepeatCount, val tracks: List<TrackSize>) : TrackSize
}

object TrackSizeSerializer : KSerializer<TrackSize> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TrackSize")

    override fun serialize(encoder: Encoder, value: TrackSize) {
        require(encoder is JsonEncoder)
        val json = encoder.json
        encoder.encodeJsonElement(when (value) {
            is TrackSize.LengthValue -> json.encodeToJsonElement(IRLength.serializer(), value.length)
            is TrackSize.PercentageValue -> json.encodeToJsonElement(IRPercentage.serializer(), value.percentage)
            is TrackSize.Flex -> buildJsonObject { put("fr", json.encodeToJsonElement(IRNumber.serializer(), value.value)) }
            is TrackSize.MinContent -> JsonPrimitive("min-content")
            is TrackSize.MaxContent -> JsonPrimitive("max-content")
            is TrackSize.Auto -> JsonPrimitive("auto")
            is TrackSize.FitContent -> buildJsonObject { put("fit", json.encodeToJsonElement(IRLength.serializer(), value.size)) }
            is TrackSize.MinMax -> buildJsonObject {
                put("min", json.encodeToJsonElement(TrackSizeSerializer, value.min))
                put("max", json.encodeToJsonElement(TrackSizeSerializer, value.max))
            }
            is TrackSize.Repeat -> buildJsonObject {
                put("repeat", json.encodeToJsonElement(RepeatCountSerializer, value.count))
                put("tracks", json.encodeToJsonElement(ListSerializer(TrackSizeSerializer), value.tracks))
            }
        })
    }

    override fun deserialize(decoder: Decoder): TrackSize {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonObject && element.containsKey("fr") ->
                TrackSize.Flex(decoder.json.decodeFromJsonElement(IRNumber.serializer(), element["fr"]!!))
            element is JsonObject && element.containsKey("fit") ->
                TrackSize.FitContent(decoder.json.decodeFromJsonElement(IRLength.serializer(), element["fit"]!!))
            element is JsonObject && element.containsKey("min") -> TrackSize.MinMax(
                decoder.json.decodeFromJsonElement(TrackSizeSerializer, element["min"]!!),
                decoder.json.decodeFromJsonElement(TrackSizeSerializer, element["max"]!!)
            )
            element is JsonObject && element.containsKey("repeat") -> TrackSize.Repeat(
                decoder.json.decodeFromJsonElement(RepeatCountSerializer, element["repeat"]!!),
                decoder.json.decodeFromJsonElement(ListSerializer(TrackSizeSerializer), element["tracks"]!!)
            )
            element is JsonPrimitive && element.content == "auto" -> TrackSize.Auto()
            element is JsonPrimitive && element.content == "min-content" -> TrackSize.MinContent()
            element is JsonPrimitive && element.content == "max-content" -> TrackSize.MaxContent()
            else -> TrackSize.Auto()
        }
    }
}
