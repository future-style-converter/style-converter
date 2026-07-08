package app.irmodels.properties.typography

import app.irmodels.IRLength
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface HyphenateLimitZoneValue {
    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : HyphenateLimitZoneValue

    @Serializable
    @SerialName("percentage")
    data class Percentage(val value: IRPercentage) : HyphenateLimitZoneValue
}

/**
 * Represents the CSS `hyphenate-limit-zone` property.
 * Sets maximum amount of unfilled space before hyphenation.
 */
@Serializable
data class HyphenateLimitZoneProperty(
    val value: HyphenateLimitZoneValue
) : IRProperty {
    override val propertyName = "hyphenate-limit-zone"
}
