package app.irmodels.properties.animations

import app.irmodels.IRProperty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

/**
 * Animation timing function with dual storage: normalized params + original representation.
 *
 * CSS spec keyword → cubic-bezier mappings:
 * - linear → cubic-bezier(0, 0, 1, 1)
 * - ease → cubic-bezier(0.25, 0.1, 0.25, 1)
 * - ease-in → cubic-bezier(0.42, 0, 1, 1)
 * - ease-out → cubic-bezier(0, 0, 0.58, 1)
 * - ease-in-out → cubic-bezier(0.42, 0, 0.58, 1)
 * - step-start → steps(1, start)
 * - step-end → steps(1, end)
 */
@Serializable
data class AnimationTimingFunctionProperty(
    val functions: List<TimingFunction>
) : IRProperty {
    override val propertyName = "animation-timing-function"
}

/**
 * Timing function with normalized parameters for generators.
 */
@Serializable(with = TimingFunctionSerializer::class)
data class TimingFunction(
    /** Normalized cubic-bezier parameters (null for steps/linear) */
    val cubicBezier: CubicBezierParams?,
    /** Normalized steps parameters (null for cubic-bezier/linear) */
    val steps: StepsParams?,
    /** Linear easing stops (null for cubic-bezier/steps keywords) */
    val linearStops: List<LinearStop>?,
    /** Original representation for CSS regeneration */
    val original: TimingOriginal
) {
    /** Cubic bezier control points */
    @Serializable
    data class CubicBezierParams(val x1: Double, val y1: Double, val x2: Double, val y2: Double)

    /** Steps parameters */
    @Serializable
    data class StepsParams(val count: Int, val position: StepPosition)

    /** Linear easing stop */
    @Serializable
    data class LinearStop(val value: Double, val position: Double? = null)

    enum class StepPosition { JUMP_START, JUMP_END, JUMP_NONE, JUMP_BOTH, START, END }

    /** Original value representation */
    @Serializable
    sealed interface TimingOriginal {
        @Serializable data class Keyword(val keyword: String) : TimingOriginal
        @Serializable data class CubicBezier(val x1: Double, val y1: Double, val x2: Double, val y2: Double) : TimingOriginal
        @Serializable data class Steps(val count: Int, val position: String?) : TimingOriginal
        @Serializable data class Linear(val stops: List<LinearStop>) : TimingOriginal
    }

    companion object {
        // CSS spec cubic-bezier values for keywords
        private val CUBIC_BEZIER_LINEAR = CubicBezierParams(0.0, 0.0, 1.0, 1.0)
        private val CUBIC_BEZIER_EASE = CubicBezierParams(0.25, 0.1, 0.25, 1.0)
        private val CUBIC_BEZIER_EASE_IN = CubicBezierParams(0.42, 0.0, 1.0, 1.0)
        private val CUBIC_BEZIER_EASE_OUT = CubicBezierParams(0.0, 0.0, 0.58, 1.0)
        private val CUBIC_BEZIER_EASE_IN_OUT = CubicBezierParams(0.42, 0.0, 0.58, 1.0)

        /** Create from keyword */
        fun fromKeyword(keyword: String): TimingFunction {
            val lower = keyword.lowercase()
            return when (lower) {
                "linear" -> TimingFunction(CUBIC_BEZIER_LINEAR, null, null, TimingOriginal.Keyword(lower))
                "ease" -> TimingFunction(CUBIC_BEZIER_EASE, null, null, TimingOriginal.Keyword(lower))
                "ease-in" -> TimingFunction(CUBIC_BEZIER_EASE_IN, null, null, TimingOriginal.Keyword(lower))
                "ease-out" -> TimingFunction(CUBIC_BEZIER_EASE_OUT, null, null, TimingOriginal.Keyword(lower))
                "ease-in-out" -> TimingFunction(CUBIC_BEZIER_EASE_IN_OUT, null, null, TimingOriginal.Keyword(lower))
                "step-start" -> TimingFunction(null, StepsParams(1, StepPosition.START), null, TimingOriginal.Keyword(lower))
                "step-end" -> TimingFunction(null, StepsParams(1, StepPosition.END), null, TimingOriginal.Keyword(lower))
                else -> TimingFunction(CUBIC_BEZIER_EASE, null, null, TimingOriginal.Keyword(lower))
            }
        }

        /** Create from cubic-bezier parameters */
        fun fromCubicBezier(x1: Double, y1: Double, x2: Double, y2: Double): TimingFunction {
            return TimingFunction(
                CubicBezierParams(x1, y1, x2, y2),
                null,
                null,
                TimingOriginal.CubicBezier(x1, y1, x2, y2)
            )
        }

        /** Create from steps */
        fun fromSteps(count: Int, position: StepPosition?): TimingFunction {
            val pos = position ?: StepPosition.END
            val posStr = position?.name?.lowercase()?.replace("_", "-")
            return TimingFunction(
                null,
                StepsParams(count, pos),
                null,
                TimingOriginal.Steps(count, posStr)
            )
        }

        /** Create from linear() function with stops */
        fun fromLinear(stops: List<LinearStop>): TimingFunction {
            return TimingFunction(
                null, // Linear with stops can't be normalized to simple cubic-bezier
                null,
                stops,
                TimingOriginal.Linear(stops)
            )
        }
    }
}

object TimingFunctionSerializer : KSerializer<TimingFunction> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TimingFunction")

    override fun serialize(encoder: Encoder, value: TimingFunction) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(buildJsonObject {
            // Normalized cubic-bezier if available
            value.cubicBezier?.let { cb ->
                put("cb", buildJsonArray {
                    add(cb.x1); add(cb.y1); add(cb.x2); add(cb.y2)
                })
            }
            // Normalized steps if available
            value.steps?.let { s ->
                put("steps", buildJsonObject {
                    put("n", s.count)
                    put("pos", s.position.name.lowercase().replace("_", "-"))
                })
            }
            // Linear stops if available
            value.linearStops?.let { stops ->
                put("linear", buildJsonArray {
                    stops.forEach { stop ->
                        add(buildJsonObject {
                            put("v", stop.value)
                            stop.position?.let { put("p", it) }
                        })
                    }
                })
            }
            // Original representation
            put("original", when (val orig = value.original) {
                is TimingFunction.TimingOriginal.Keyword -> JsonPrimitive(orig.keyword)
                is TimingFunction.TimingOriginal.CubicBezier -> buildJsonObject {
                    put("cb", buildJsonArray { add(orig.x1); add(orig.y1); add(orig.x2); add(orig.y2) })
                }
                is TimingFunction.TimingOriginal.Steps -> buildJsonObject {
                    put("steps", orig.count)
                    orig.position?.let { put("pos", it) }
                }
                is TimingFunction.TimingOriginal.Linear -> buildJsonObject {
                    put("linear", buildJsonArray {
                        orig.stops.forEach { stop ->
                            add(buildJsonObject {
                                put("v", stop.value)
                                stop.position?.let { put("p", it) }
                            })
                        }
                    })
                }
            })
        })
    }

    override fun deserialize(decoder: Decoder): TimingFunction {
        require(decoder is JsonDecoder)
        val obj = decoder.decodeJsonElement().jsonObject

        // Parse normalized values
        val cubicBezier = obj["cb"]?.jsonArray?.let { arr ->
            TimingFunction.CubicBezierParams(
                arr[0].jsonPrimitive.double,
                arr[1].jsonPrimitive.double,
                arr[2].jsonPrimitive.double,
                arr[3].jsonPrimitive.double
            )
        }
        val steps = obj["steps"]?.jsonObject?.let { s ->
            TimingFunction.StepsParams(
                s["n"]!!.jsonPrimitive.int,
                TimingFunction.StepPosition.valueOf(s["pos"]!!.jsonPrimitive.content.uppercase().replace("-", "_"))
            )
        }
        val linearStops = obj["linear"]?.jsonArray?.map { stopEl ->
            val stopObj = stopEl.jsonObject
            TimingFunction.LinearStop(
                stopObj["v"]!!.jsonPrimitive.double,
                stopObj["p"]?.jsonPrimitive?.double
            )
        }

        // Parse original
        val originalEl = obj["original"]!!
        val original = when {
            originalEl is JsonPrimitive -> TimingFunction.TimingOriginal.Keyword(originalEl.content)
            originalEl is JsonObject && originalEl.containsKey("cb") -> {
                val arr = originalEl["cb"]!!.jsonArray
                TimingFunction.TimingOriginal.CubicBezier(
                    arr[0].jsonPrimitive.double, arr[1].jsonPrimitive.double,
                    arr[2].jsonPrimitive.double, arr[3].jsonPrimitive.double
                )
            }
            originalEl is JsonObject && originalEl.containsKey("steps") -> {
                TimingFunction.TimingOriginal.Steps(
                    originalEl["steps"]!!.jsonPrimitive.int,
                    originalEl["pos"]?.jsonPrimitive?.content
                )
            }
            originalEl is JsonObject && originalEl.containsKey("linear") -> {
                TimingFunction.TimingOriginal.Linear(
                    originalEl["linear"]!!.jsonArray.map { stopEl ->
                        val stopObj = stopEl.jsonObject
                        TimingFunction.LinearStop(
                            stopObj["v"]!!.jsonPrimitive.double,
                            stopObj["p"]?.jsonPrimitive?.double
                        )
                    }
                )
            }
            else -> TimingFunction.TimingOriginal.Keyword("ease")
        }

        return TimingFunction(cubicBezier, steps, linearStops, original)
    }
}
