package app.irmodels.properties.background

import app.irmodels.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class BackgroundImageProperty(
    val images: List<BackgroundImage>
) : IRProperty {
    override val propertyName = "background-image"

    @Serializable(with = BackgroundImageSerializer::class)
    sealed interface BackgroundImage {
        @Serializable data class None(val unit: Unit = Unit) : BackgroundImage
        @Serializable data class Url(val url: IRUrl) : BackgroundImage
        @Serializable data class LinearGradient(val angle: IRAngle?, val colorStops: List<ColorStop>, val repeating: Boolean = false) : BackgroundImage
        @Serializable data class RadialGradient(val shape: GradientShape?, val size: GradientSize?, val position: Position?, val colorStops: List<ColorStop>, val repeating: Boolean = false) : BackgroundImage
        @Serializable data class ConicGradient(val angle: IRAngle?, val position: Position?, val colorStops: List<ColorStop>, val repeating: Boolean = false) : BackgroundImage
        @Serializable data class Keyword(val keyword: String) : BackgroundImage
        @Serializable data class Raw(val value: String) : BackgroundImage
    }

    @Serializable data class ColorStop(val color: IRColor, val position: IRPercentage?)
    @Serializable data class Position(val x: IRPercentage, val y: IRPercentage)
    enum class GradientShape { CIRCLE, ELLIPSE }
    enum class GradientSize { CLOSEST_SIDE, CLOSEST_CORNER, FARTHEST_SIDE, FARTHEST_CORNER }
}

object BackgroundImageSerializer : KSerializer<BackgroundImageProperty.BackgroundImage> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BackgroundImage")
    override fun serialize(encoder: Encoder, value: BackgroundImageProperty.BackgroundImage) {
        require(encoder is JsonEncoder)
        val json = encoder.json
        encoder.encodeJsonElement(when (value) {
            is BackgroundImageProperty.BackgroundImage.None -> JsonPrimitive("none")
            is BackgroundImageProperty.BackgroundImage.Url -> json.encodeToJsonElement(IRUrl.serializer(), value.url)
            is BackgroundImageProperty.BackgroundImage.LinearGradient -> buildJsonObject {
                put("type", if (value.repeating) "repeating-linear-gradient" else "linear-gradient")
                value.angle?.let { put("angle", json.encodeToJsonElement(IRAngle.serializer(), it)) }
                put("stops", json.encodeToJsonElement(ListSerializer(BackgroundImageProperty.ColorStop.serializer()), value.colorStops))
            }
            is BackgroundImageProperty.BackgroundImage.RadialGradient -> buildJsonObject {
                put("type", if (value.repeating) "repeating-radial-gradient" else "radial-gradient")
                value.shape?.let { put("shape", it.name.lowercase()) }
                value.size?.let { put("size", it.name.lowercase().replace("_", "-")) }
                value.position?.let { put("pos", json.encodeToJsonElement(BackgroundImageProperty.Position.serializer(), it)) }
                put("stops", json.encodeToJsonElement(ListSerializer(BackgroundImageProperty.ColorStop.serializer()), value.colorStops))
            }
            is BackgroundImageProperty.BackgroundImage.ConicGradient -> buildJsonObject {
                put("type", if (value.repeating) "repeating-conic-gradient" else "conic-gradient")
                value.angle?.let { put("angle", json.encodeToJsonElement(IRAngle.serializer(), it)) }
                value.position?.let { put("pos", json.encodeToJsonElement(BackgroundImageProperty.Position.serializer(), it)) }
                put("stops", json.encodeToJsonElement(ListSerializer(BackgroundImageProperty.ColorStop.serializer()), value.colorStops))
            }
            is BackgroundImageProperty.BackgroundImage.Keyword -> JsonPrimitive(value.keyword)
            is BackgroundImageProperty.BackgroundImage.Raw -> buildJsonObject { put("raw", value.value) }
        })
    }
    override fun deserialize(decoder: Decoder): BackgroundImageProperty.BackgroundImage {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.content == "none" -> BackgroundImageProperty.BackgroundImage.None()
            element is JsonPrimitive && element.content in setOf("inherit", "initial", "unset", "revert", "revert-layer") ->
                BackgroundImageProperty.BackgroundImage.Keyword(element.content)
            element is JsonObject && element.containsKey("raw") ->
                BackgroundImageProperty.BackgroundImage.Raw(element["raw"]!!.jsonPrimitive.content)
            element is JsonObject && element.containsKey("type") -> {
                val type = element["type"]!!.jsonPrimitive.content
                when {
                    type.contains("linear-gradient") -> {
                        val angle = element["angle"]?.let { decoder.json.decodeFromJsonElement(IRAngle.serializer(), it) }
                        val stops = decoder.json.decodeFromJsonElement(ListSerializer(BackgroundImageProperty.ColorStop.serializer()), element["stops"]!!)
                        BackgroundImageProperty.BackgroundImage.LinearGradient(angle, stops, type.startsWith("repeating"))
                    }
                    else -> BackgroundImageProperty.BackgroundImage.None()
                }
            }
            element is JsonObject -> BackgroundImageProperty.BackgroundImage.Url(decoder.json.decodeFromJsonElement(IRUrl.serializer(), element))
            else -> BackgroundImageProperty.BackgroundImage.None()
        }
    }
}
