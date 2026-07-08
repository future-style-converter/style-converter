package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class TextOrientationProperty(
    val orientation: TextOrientation
) : IRProperty {
    override val propertyName = "text-orientation"

    enum class TextOrientation {
        MIXED,
        UPRIGHT,
        SIDEWAYS
    }
}
