package app.irmodels.properties.layout.advanced

import app.irmodels.IRAngle
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface OffsetRotateValue {
    @Serializable
    @SerialName("auto")
    data object Auto : OffsetRotateValue

    @Serializable
    @SerialName("reverse")
    data object Reverse : OffsetRotateValue

    @Serializable
    @SerialName("angle")
    data class Angle(val value: IRAngle) : OffsetRotateValue

    /** Combination of auto/reverse with angle, e.g., "auto 45deg" */
    @Serializable
    @SerialName("auto-angle")
    data class AutoAngle(val auto: Boolean, val reverse: Boolean, val angle: IRAngle) : OffsetRotateValue

    @Serializable
    @SerialName("keyword")
    data class Keyword(val keyword: String) : OffsetRotateValue

    @Serializable
    @SerialName("raw")
    data class Raw(val value: String) : OffsetRotateValue
}

/**
 * Represents the CSS `offset-rotate` property.
 * Specifies rotation of element along motion path.
 */
@Serializable
data class OffsetRotateProperty(
    val value: OffsetRotateValue
) : IRProperty {
    override val propertyName = "offset-rotate"
}
