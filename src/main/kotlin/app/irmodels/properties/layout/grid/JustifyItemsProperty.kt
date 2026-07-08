package app.irmodels.properties.layout.grid

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class JustifyItemsProperty(
    val value: JustifyItems
) : IRProperty {
    override val propertyName = "justify-items"

    enum class JustifyItems {
        NORMAL, STRETCH, CENTER, START, END,
        FLEX_START, FLEX_END, SELF_START, SELF_END,
        LEFT, RIGHT, BASELINE
    }
}
