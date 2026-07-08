package app.irmodels.properties.animations

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface TransitionBehaviorValue {
    @Serializable @SerialName("normal") data object Normal : TransitionBehaviorValue
    @Serializable @SerialName("allow-discrete") data object AllowDiscrete : TransitionBehaviorValue
    @Serializable @SerialName("list") data class List(val values: kotlin.collections.List<String>) : TransitionBehaviorValue
    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : TransitionBehaviorValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : TransitionBehaviorValue
}

/**
 * Represents the CSS `transition-behavior` property.
 * Controls whether discrete properties can be transitioned.
 */
@Serializable
data class TransitionBehaviorProperty(
    val value: TransitionBehaviorValue
) : IRProperty {
    override val propertyName = "transition-behavior"
}
