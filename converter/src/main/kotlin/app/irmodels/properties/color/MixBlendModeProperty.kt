package app.irmodels.properties.color

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class MixBlendModeProperty(
    val mode: BlendMode
) : IRProperty {
    override val propertyName = "mix-blend-mode"

    enum class BlendMode {
        NORMAL, MULTIPLY, SCREEN, OVERLAY,
        DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN,
        HARD_LIGHT, SOFT_LIGHT, DIFFERENCE, EXCLUSION,
        HUE, SATURATION, COLOR, LUMINOSITY,
        PLUS_DARKER, PLUS_LIGHTER
    }
}
