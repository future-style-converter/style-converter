package app.irmodels.properties.speech

import app.irmodels.IRAngle
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface AzimuthValue {
    @Serializable
    @SerialName("angle")
    data class Angle(val value: IRAngle) : AzimuthValue

    @Serializable
    @SerialName("named")
    data class Named(val position: String) : AzimuthValue

    @Serializable
    @SerialName("combined")
    data class Combined(val position: String, val behind: Boolean) : AzimuthValue

    @Serializable
    @SerialName("keyword")
    data class Keyword(val keyword: String) : AzimuthValue

    @Serializable
    @SerialName("raw")
    data class Raw(val value: String) : AzimuthValue
}

/**
 * Represents the CSS `azimuth` property (deprecated).
 * Specifies horizontal sound direction.
 */
@Serializable
data class AzimuthProperty(
    val value: AzimuthValue
) : IRProperty {
    override val propertyName = "azimuth"
}
