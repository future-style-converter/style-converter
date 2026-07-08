package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface RubyPositionValue {
    // Single values
    @Serializable @SerialName("over") data object Over : RubyPositionValue
    @Serializable @SerialName("under") data object Under : RubyPositionValue
    @Serializable @SerialName("inter-character") data object InterCharacter : RubyPositionValue
    @Serializable @SerialName("alternate") data object Alternate : RubyPositionValue

    // Combined values (position + alignment)
    @Serializable @SerialName("over-left") data object OverLeft : RubyPositionValue
    @Serializable @SerialName("over-right") data object OverRight : RubyPositionValue
    @Serializable @SerialName("under-left") data object UnderLeft : RubyPositionValue
    @Serializable @SerialName("under-right") data object UnderRight : RubyPositionValue

    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : RubyPositionValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : RubyPositionValue
}

/**
 * Represents the CSS `ruby-position` property.
 * Controls position of ruby text relative to base text.
 */
@Serializable
data class RubyPositionProperty(
    val value: RubyPositionValue
) : IRProperty {
    override val propertyName = "ruby-position"
}
