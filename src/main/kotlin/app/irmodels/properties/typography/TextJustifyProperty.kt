package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class TextJustifyValue {
    AUTO,
    NONE,
    INTER_WORD,
    INTER_CHARACTER,
    DISTRIBUTE
}

@Serializable
data class TextJustifyProperty(
    val value: TextJustifyValue
) : IRProperty {
    override val propertyName = "text-justify"
}
