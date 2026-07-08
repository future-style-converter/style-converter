package app.irmodels.properties.animations

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransitionPropertyProperty(
    val properties: List<TransitionProperty>
) : IRProperty {
    override val propertyName = "transition-property"

    @Serializable
    sealed interface TransitionProperty {
        @Serializable
        @SerialName("none")
        data class None(val unit: Unit = Unit) : TransitionProperty

        @Serializable
        @SerialName("all")
        data class All(val unit: Unit = Unit) : TransitionProperty

        @Serializable
        @SerialName("property-name")
        data class PropertyName(val name: String) : TransitionProperty
    }
}
