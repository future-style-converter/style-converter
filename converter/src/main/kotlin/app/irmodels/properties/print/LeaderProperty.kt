package app.irmodels.properties.print

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface LeaderValue {
    @Serializable
    @SerialName("dotted")
    data object Dotted : LeaderValue

    @Serializable
    @SerialName("solid")
    data object Solid : LeaderValue

    @Serializable
    @SerialName("space")
    data object Space : LeaderValue

    @Serializable
    @SerialName("string")
    data class String(val value: kotlin.String) : LeaderValue
}

/**
 * Represents the CSS `leader` property.
 * Specifies leader pattern (dots, lines) for table of contents.
 */
@Serializable
data class LeaderProperty(
    val value: LeaderValue
) : IRProperty {
    override val propertyName = "leader"
}
