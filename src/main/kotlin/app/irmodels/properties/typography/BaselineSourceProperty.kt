package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class BaselineSourceValue {
    AUTO,
    FIRST,
    LAST
}

/**
 * Represents the CSS `baseline-source` property.
 * Specifies which baseline to use for alignment.
 */
@Serializable
data class BaselineSourceProperty(
    val value: BaselineSourceValue
) : IRProperty {
    override val propertyName = "baseline-source"
}
