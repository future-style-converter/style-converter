package app.irmodels.properties.interactions

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class ScrollBehaviorProperty(
    val value: ScrollBehavior
) : IRProperty {
    override val propertyName = "scroll-behavior"

    enum class ScrollBehavior {
        AUTO, SMOOTH
    }
}
