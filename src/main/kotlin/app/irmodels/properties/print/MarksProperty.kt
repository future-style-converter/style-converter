package app.irmodels.properties.print

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class MarksValue {
    NONE, CROP, CROSS
}

/**
 * Represents the CSS `marks` property.
 * Specifies crop and/or cross marks for printing.
 */
@Serializable
data class MarksProperty(
    val values: List<MarksValue>
) : IRProperty {
    override val propertyName = "marks"
}
