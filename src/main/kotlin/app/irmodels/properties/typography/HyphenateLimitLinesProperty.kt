package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface HyphenateLimitLinesValue {
    @Serializable
    @SerialName("no-limit")
    data object NoLimit : HyphenateLimitLinesValue

    @Serializable
    @SerialName("number")
    data class Number(val value: Int) : HyphenateLimitLinesValue
}

/**
 * Represents the CSS `hyphenate-limit-lines` property.
 * Sets maximum number of consecutive hyphenated lines.
 */
@Serializable
data class HyphenateLimitLinesProperty(
    val value: HyphenateLimitLinesValue
) : IRProperty {
    override val propertyName = "hyphenate-limit-lines"
}
