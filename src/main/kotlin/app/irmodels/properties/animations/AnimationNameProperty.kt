package app.irmodels.properties.animations

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnimationNameProperty(
    val names: List<AnimationName>
) : IRProperty {
    override val propertyName = "animation-name"

    @Serializable
    sealed interface AnimationName {
        @Serializable
        @SerialName("none")
        data class None(val unit: Unit = Unit) : AnimationName

        @Serializable
        @SerialName("identifier")
        data class Identifier(val name: String) : AnimationName
    }
}
