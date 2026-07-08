package app.irmodels.properties.math

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface MathDepthValue {
    @Serializable
    @SerialName("auto")
    data object Auto : MathDepthValue

    @Serializable
    @SerialName("auto-add")
    data object AutoAdd : MathDepthValue

    @Serializable
    @SerialName("add")
    data class Add(val value: Int) : MathDepthValue

    @Serializable
    @SerialName("integer")
    data class Integer(val value: IRNumber) : MathDepthValue

    @Serializable
    @SerialName("keyword")
    data class Keyword(val keyword: String) : MathDepthValue

    @Serializable
    @SerialName("raw")
    data class Raw(val value: String) : MathDepthValue
}

/**
 * Represents the CSS `math-depth` property.
 * Specifies the depth of nested math formulas for scaling purposes.
 */
@Serializable
data class MathDepthProperty(
    val value: MathDepthValue
) : IRProperty {
    override val propertyName = "math-depth"
}
