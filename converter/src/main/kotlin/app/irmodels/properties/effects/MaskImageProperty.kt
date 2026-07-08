package app.irmodels.properties.effects

import app.irmodels.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable(with = MaskImageValueSerializer::class)
sealed interface MaskImageValue {
    @Serializable data class Image(val url: IRUrl) : MaskImageValue
    @Serializable data object None : MaskImageValue
    @Serializable data class LinearGradient(val angle: IRAngle?, val colorStops: List<ColorStop>, val repeating: Boolean = false) : MaskImageValue
    @Serializable data class RadialGradient(val shape: GradientShape?, val size: GradientSize?, val position: Position?, val colorStops: List<ColorStop>, val repeating: Boolean = false) : MaskImageValue
    @Serializable data class ConicGradient(val angle: IRAngle?, val position: Position?, val colorStops: List<ColorStop>, val repeating: Boolean = false) : MaskImageValue

    @Serializable data class ColorStop(val color: IRColor, val position: IRPercentage?)
    @Serializable data class Position(val x: IRPercentage, val y: IRPercentage)
    enum class GradientShape { CIRCLE, ELLIPSE }
    enum class GradientSize { CLOSEST_SIDE, CLOSEST_CORNER, FARTHEST_SIDE, FARTHEST_CORNER }
}

@Serializable
data class MaskImageProperty(
    val values: List<MaskImageValue>
) : IRProperty {
    override val propertyName = "mask-image"

    constructor(value: MaskImageValue) : this(listOf(value))
}

object MaskImageValueSerializer : KSerializer<MaskImageValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MaskImageValue")

    override fun serialize(encoder: Encoder, value: MaskImageValue) {
        require(encoder is JsonEncoder)
        val json = encoder.json
        encoder.encodeJsonElement(when (value) {
            is MaskImageValue.None -> JsonPrimitive("none")
            is MaskImageValue.Image -> json.encodeToJsonElement(IRUrl.serializer(), value.url)
            is MaskImageValue.LinearGradient -> buildJsonObject {
                put("type", if (value.repeating) "repeating-linear-gradient" else "linear-gradient")
                value.angle?.let { put("angle", json.encodeToJsonElement(IRAngle.serializer(), it)) }
                put("stops", json.encodeToJsonElement(ListSerializer(MaskImageValue.ColorStop.serializer()), value.colorStops))
            }
            is MaskImageValue.RadialGradient -> buildJsonObject {
                put("type", if (value.repeating) "repeating-radial-gradient" else "radial-gradient")
                value.shape?.let { put("shape", it.name.lowercase()) }
                value.size?.let { put("size", it.name.lowercase().replace("_", "-")) }
                value.position?.let { put("pos", json.encodeToJsonElement(MaskImageValue.Position.serializer(), it)) }
                put("stops", json.encodeToJsonElement(ListSerializer(MaskImageValue.ColorStop.serializer()), value.colorStops))
            }
            is MaskImageValue.ConicGradient -> buildJsonObject {
                put("type", if (value.repeating) "repeating-conic-gradient" else "conic-gradient")
                value.angle?.let { put("angle", json.encodeToJsonElement(IRAngle.serializer(), it)) }
                value.position?.let { put("pos", json.encodeToJsonElement(MaskImageValue.Position.serializer(), it)) }
                put("stops", json.encodeToJsonElement(ListSerializer(MaskImageValue.ColorStop.serializer()), value.colorStops))
            }
        })
    }

    override fun deserialize(decoder: Decoder): MaskImageValue {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.content == "none" -> MaskImageValue.None
            element is JsonObject && element.containsKey("type") -> {
                val type = element["type"]!!.jsonPrimitive.content
                when {
                    type.contains("linear-gradient") -> {
                        val angle = element["angle"]?.let { decoder.json.decodeFromJsonElement(IRAngle.serializer(), it) }
                        val stops = decoder.json.decodeFromJsonElement(ListSerializer(MaskImageValue.ColorStop.serializer()), element["stops"]!!)
                        MaskImageValue.LinearGradient(angle, stops, type.startsWith("repeating"))
                    }
                    else -> MaskImageValue.None
                }
            }
            element is JsonObject -> MaskImageValue.Image(decoder.json.decodeFromJsonElement(IRUrl.serializer(), element))
            else -> MaskImageValue.None
        }
    }
}
