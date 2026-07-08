package app.irmodels.properties.layout.flexbox

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class AlignItemsProperty(
    val value: AlignItems
) : IRProperty {
    override val propertyName = "align-items"

    enum class AlignItems {
        FLEX_START, FLEX_END, CENTER, BASELINE,
        STRETCH, START, END, SELF_START, SELF_END, NORMAL
    }
}
