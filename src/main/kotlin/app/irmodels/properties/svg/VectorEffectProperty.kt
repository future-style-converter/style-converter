package app.irmodels.properties.svg

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class VectorEffect {
    NONE,
    NON_SCALING_STROKE,
    NON_SCALING_SIZE,
    NON_ROTATION,
    FIXED_POSITION
}

@Serializable
data class VectorEffectProperty(
    val effect: VectorEffect
) : IRProperty {
    override val propertyName = "vector-effect"
}
