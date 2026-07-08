package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface LineGridValue {
    @Serializable
    @SerialName("match-parent")
    data object Match_Parent : LineGridValue

    @Serializable
    @SerialName("create")
    data object Create : LineGridValue

    @Serializable
    @SerialName("named")
    data class Named(val name: String) : LineGridValue
}

/**
 * Represents the CSS `line-grid` property.
 * Establishes a line grid for aligning lines across columns.
 */
@Serializable
data class LineGridProperty(
    val value: LineGridValue
) : IRProperty {
    override val propertyName = "line-grid"
}
