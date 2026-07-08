package app.irmodels.properties.effects

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class VisibilityProperty(
    val value: Visibility
) : IRProperty {
    override val propertyName = "visibility"

    enum class Visibility {
        VISIBLE, HIDDEN, COLLAPSE
    }
}
