package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class TextDecorationLineProperty(
    val lines: List<DecorationLine>
) : IRProperty {
    override val propertyName = "text-decoration-line"

    enum class DecorationLine {
        NONE, UNDERLINE, OVERLINE, LINE_THROUGH, BLINK
    }
}
