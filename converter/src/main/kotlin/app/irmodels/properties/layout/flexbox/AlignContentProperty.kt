package app.irmodels.properties.layout.flexbox

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class AlignContentProperty(
    val value: AlignContent
) : IRProperty {
    override val propertyName = "align-content"

    enum class AlignContent {
        FLEX_START, FLEX_END, CENTER, SPACE_BETWEEN,
        SPACE_AROUND, SPACE_EVENLY, STRETCH,
        START, END, NORMAL, BASELINE
    }
}
