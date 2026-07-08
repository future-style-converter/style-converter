package app.irmodels.properties.speech

import app.irmodels.IRAngle
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ElevationValue {
    @Serializable
    @SerialName("angle")
    data class Angle(val value: IRAngle) : ElevationValue

    @Serializable
    @SerialName("named")
    data class Named(val position: String) : ElevationValue
}

/**
 * Represents the CSS `elevation` property (deprecated).
 * Specifies vertical sound direction.
 */
@Serializable
data class ElevationProperty(
    val value: ElevationValue
) : IRProperty {
    override val propertyName = "elevation"
}
