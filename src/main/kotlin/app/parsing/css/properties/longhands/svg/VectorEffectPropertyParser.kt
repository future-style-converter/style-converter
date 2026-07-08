package app.parsing.css.properties.longhands.svg

import app.irmodels.IRProperty
import app.irmodels.properties.svg.VectorEffect
import app.irmodels.properties.svg.VectorEffectProperty
import app.parsing.css.properties.longhands.PropertyParser

object VectorEffectPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val normalized = value.trim().lowercase()
        val effect = when (normalized) {
            "none" -> VectorEffect.NONE
            "non-scaling-stroke" -> VectorEffect.NON_SCALING_STROKE
            "non-scaling-size" -> VectorEffect.NON_SCALING_SIZE
            "non-rotation" -> VectorEffect.NON_ROTATION
            "fixed-position" -> VectorEffect.FIXED_POSITION
            else -> return null
        }
        return VectorEffectProperty(effect)
    }
}
