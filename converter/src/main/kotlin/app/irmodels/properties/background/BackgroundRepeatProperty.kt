package app.irmodels.properties.background

import app.irmodels.IRProperty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable
data class BackgroundRepeatProperty(
    val repeats: List<BackgroundRepeat>
) : IRProperty {
    override val propertyName = "background-repeat"

    @Serializable(with = BackgroundRepeatSerializer::class)
    sealed interface BackgroundRepeat {
        @Serializable data class TwoValue(val x: RepeatValue, val y: RepeatValue) : BackgroundRepeat
        @Serializable data class OneValue(val value: RepeatKeyword) : BackgroundRepeat
        @Serializable data class Keyword(val keyword: String) : BackgroundRepeat
        enum class RepeatKeyword { REPEAT, SPACE, ROUND, NO_REPEAT }
        enum class RepeatValue { REPEAT, SPACE, ROUND, NO_REPEAT }
    }
}

object BackgroundRepeatSerializer : KSerializer<BackgroundRepeatProperty.BackgroundRepeat> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BackgroundRepeat")
    override fun serialize(encoder: Encoder, value: BackgroundRepeatProperty.BackgroundRepeat) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(when (value) {
            is BackgroundRepeatProperty.BackgroundRepeat.OneValue -> JsonPrimitive(value.value.name.lowercase().replace("_", "-"))
            is BackgroundRepeatProperty.BackgroundRepeat.TwoValue -> buildJsonObject {
                put("x", value.x.name.lowercase().replace("_", "-"))
                put("y", value.y.name.lowercase().replace("_", "-"))
            }
            is BackgroundRepeatProperty.BackgroundRepeat.Keyword -> JsonPrimitive(value.keyword)
        })
    }
    override fun deserialize(decoder: Decoder): BackgroundRepeatProperty.BackgroundRepeat {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        return when {
            element is JsonObject && element.containsKey("x") -> {
                val x = BackgroundRepeatProperty.BackgroundRepeat.RepeatValue.valueOf(element["x"]!!.jsonPrimitive.content.uppercase().replace("-", "_"))
                val y = BackgroundRepeatProperty.BackgroundRepeat.RepeatValue.valueOf(element["y"]!!.jsonPrimitive.content.uppercase().replace("-", "_"))
                BackgroundRepeatProperty.BackgroundRepeat.TwoValue(x, y)
            }
            element is JsonPrimitive -> {
                val content = element.content.uppercase().replace("-", "_")
                try {
                    BackgroundRepeatProperty.BackgroundRepeat.OneValue(
                        BackgroundRepeatProperty.BackgroundRepeat.RepeatKeyword.valueOf(content)
                    )
                } catch (e: IllegalArgumentException) {
                    BackgroundRepeatProperty.BackgroundRepeat.Keyword(element.content)
                }
            }
            else -> BackgroundRepeatProperty.BackgroundRepeat.OneValue(BackgroundRepeatProperty.BackgroundRepeat.RepeatKeyword.REPEAT)
        }
    }
}
