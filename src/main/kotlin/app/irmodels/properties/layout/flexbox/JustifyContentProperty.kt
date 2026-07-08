package app.irmodels.properties.layout.flexbox

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class JustifyContentProperty(
    val value: JustifyContent
) : IRProperty {
    override val propertyName = "justify-content"

    enum class JustifyContent {
        FLEX_START, FLEX_END, CENTER, SPACE_BETWEEN,
        SPACE_AROUND, SPACE_EVENLY, START, END,
        LEFT, RIGHT, NORMAL, STRETCH
    }
}
