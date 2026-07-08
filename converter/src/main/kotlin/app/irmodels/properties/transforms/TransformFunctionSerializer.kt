package app.irmodels.properties.transforms

import app.irmodels.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

object TransformFunctionSerializer : KSerializer<TransformFunction> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TransformFunction")

    override fun serialize(encoder: Encoder, value: TransformFunction) {
        require(encoder is JsonEncoder)
        val json = encoder.json
        encoder.encodeJsonElement(when (value) {
            is TransformFunction.Translate -> buildJsonObject {
                put("fn", "translate")
                put("x", json.encodeToJsonElement(IRLength.serializer(), value.x))
                put("y", json.encodeToJsonElement(IRLength.serializer(), value.y))
            }
            is TransformFunction.TranslateX -> buildJsonObject {
                put("fn", "translateX")
                put("x", json.encodeToJsonElement(IRLength.serializer(), value.x))
            }
            is TransformFunction.TranslateY -> buildJsonObject {
                put("fn", "translateY")
                put("y", json.encodeToJsonElement(IRLength.serializer(), value.y))
            }
            is TransformFunction.TranslateZ -> buildJsonObject {
                put("fn", "translateZ")
                put("z", json.encodeToJsonElement(IRLength.serializer(), value.z))
            }
            is TransformFunction.Translate3d -> buildJsonObject {
                put("fn", "translate3d")
                put("x", json.encodeToJsonElement(IRLength.serializer(), value.x))
                put("y", json.encodeToJsonElement(IRLength.serializer(), value.y))
                put("z", json.encodeToJsonElement(IRLength.serializer(), value.z))
            }
            is TransformFunction.Scale -> buildJsonObject {
                put("fn", "scale")
                put("x", json.encodeToJsonElement(IRNumber.serializer(), value.x))
                put("y", json.encodeToJsonElement(IRNumber.serializer(), value.y))
            }
            is TransformFunction.ScaleX -> buildJsonObject {
                put("fn", "scaleX")
                put("x", json.encodeToJsonElement(IRNumber.serializer(), value.x))
            }
            is TransformFunction.ScaleY -> buildJsonObject {
                put("fn", "scaleY")
                put("y", json.encodeToJsonElement(IRNumber.serializer(), value.y))
            }
            is TransformFunction.ScaleZ -> buildJsonObject {
                put("fn", "scaleZ")
                put("z", json.encodeToJsonElement(IRNumber.serializer(), value.z))
            }
            is TransformFunction.Scale3d -> buildJsonObject {
                put("fn", "scale3d")
                put("x", json.encodeToJsonElement(IRNumber.serializer(), value.x))
                put("y", json.encodeToJsonElement(IRNumber.serializer(), value.y))
                put("z", json.encodeToJsonElement(IRNumber.serializer(), value.z))
            }
            is TransformFunction.Rotate -> buildJsonObject {
                put("fn", "rotate")
                put("a", json.encodeToJsonElement(IRAngle.serializer(), value.angle))
            }
            is TransformFunction.RotateX -> buildJsonObject {
                put("fn", "rotateX")
                put("a", json.encodeToJsonElement(IRAngle.serializer(), value.angle))
            }
            is TransformFunction.RotateY -> buildJsonObject {
                put("fn", "rotateY")
                put("a", json.encodeToJsonElement(IRAngle.serializer(), value.angle))
            }
            is TransformFunction.RotateZ -> buildJsonObject {
                put("fn", "rotateZ")
                put("a", json.encodeToJsonElement(IRAngle.serializer(), value.angle))
            }
            is TransformFunction.Rotate3d -> buildJsonObject {
                put("fn", "rotate3d")
                put("x", json.encodeToJsonElement(IRNumber.serializer(), value.x))
                put("y", json.encodeToJsonElement(IRNumber.serializer(), value.y))
                put("z", json.encodeToJsonElement(IRNumber.serializer(), value.z))
                put("a", json.encodeToJsonElement(IRAngle.serializer(), value.angle))
            }
            is TransformFunction.Skew -> buildJsonObject {
                put("fn", "skew")
                put("x", json.encodeToJsonElement(IRAngle.serializer(), value.x))
                put("y", json.encodeToJsonElement(IRAngle.serializer(), value.y))
            }
            is TransformFunction.SkewX -> buildJsonObject {
                put("fn", "skewX")
                put("x", json.encodeToJsonElement(IRAngle.serializer(), value.angle))
            }
            is TransformFunction.SkewY -> buildJsonObject {
                put("fn", "skewY")
                put("y", json.encodeToJsonElement(IRAngle.serializer(), value.angle))
            }
            is TransformFunction.Perspective -> buildJsonObject {
                put("fn", "perspective")
                put("l", json.encodeToJsonElement(IRLength.serializer(), value.length))
            }
            is TransformFunction.Matrix -> buildJsonObject {
                put("fn", "matrix")
                put("a", json.encodeToJsonElement(IRNumber.serializer(), value.a))
                put("b", json.encodeToJsonElement(IRNumber.serializer(), value.b))
                put("c", json.encodeToJsonElement(IRNumber.serializer(), value.c))
                put("d", json.encodeToJsonElement(IRNumber.serializer(), value.d))
                put("e", json.encodeToJsonElement(IRNumber.serializer(), value.e))
                put("f", json.encodeToJsonElement(IRNumber.serializer(), value.f))
            }
            is TransformFunction.Matrix3d -> buildJsonObject {
                put("fn", "matrix3d")
                put("a1", json.encodeToJsonElement(IRNumber.serializer(), value.a1))
                put("b1", json.encodeToJsonElement(IRNumber.serializer(), value.b1))
                put("c1", json.encodeToJsonElement(IRNumber.serializer(), value.c1))
                put("d1", json.encodeToJsonElement(IRNumber.serializer(), value.d1))
                put("a2", json.encodeToJsonElement(IRNumber.serializer(), value.a2))
                put("b2", json.encodeToJsonElement(IRNumber.serializer(), value.b2))
                put("c2", json.encodeToJsonElement(IRNumber.serializer(), value.c2))
                put("d2", json.encodeToJsonElement(IRNumber.serializer(), value.d2))
                put("a3", json.encodeToJsonElement(IRNumber.serializer(), value.a3))
                put("b3", json.encodeToJsonElement(IRNumber.serializer(), value.b3))
                put("c3", json.encodeToJsonElement(IRNumber.serializer(), value.c3))
                put("d3", json.encodeToJsonElement(IRNumber.serializer(), value.d3))
                put("a4", json.encodeToJsonElement(IRNumber.serializer(), value.a4))
                put("b4", json.encodeToJsonElement(IRNumber.serializer(), value.b4))
                put("c4", json.encodeToJsonElement(IRNumber.serializer(), value.c4))
                put("d4", json.encodeToJsonElement(IRNumber.serializer(), value.d4))
            }
        })
    }

    override fun deserialize(decoder: Decoder): TransformFunction {
        require(decoder is JsonDecoder)
        val obj = decoder.decodeJsonElement().jsonObject
        val json = decoder.json
        return when (obj["fn"]?.jsonPrimitive?.content) {
            "translate" -> TransformFunction.Translate(
                json.decodeFromJsonElement(IRLength.serializer(), obj["x"]!!),
                json.decodeFromJsonElement(IRLength.serializer(), obj["y"]!!)
            )
            "translateX" -> TransformFunction.TranslateX(
                json.decodeFromJsonElement(IRLength.serializer(), obj["x"]!!)
            )
            "translateY" -> TransformFunction.TranslateY(
                json.decodeFromJsonElement(IRLength.serializer(), obj["y"]!!)
            )
            "rotate" -> TransformFunction.Rotate(
                json.decodeFromJsonElement(IRAngle.serializer(), obj["a"]!!)
            )
            "scale" -> TransformFunction.Scale(
                json.decodeFromJsonElement(IRNumber.serializer(), obj["x"]!!),
                json.decodeFromJsonElement(IRNumber.serializer(), obj["y"]!!)
            )
            else -> TransformFunction.Rotate(IRAngle.fromDegrees(0.0))
        }
    }
}
