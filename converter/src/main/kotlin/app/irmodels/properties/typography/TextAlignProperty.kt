package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class TextAlignProperty(
    val alignment: TextAlignment
) : IRProperty {
    override val propertyName = "text-align"

    enum class TextAlignment {
        START, END, LEFT, RIGHT, CENTER, JUSTIFY, MATCH_PARENT
    }
}
