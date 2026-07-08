package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class DominantBaselineValue {
    AUTO,
    TEXT_BOTTOM,
    ALPHABETIC,
    IDEOGRAPHIC,
    MIDDLE,
    CENTRAL,
    MATHEMATICAL,
    HANGING,
    TEXT_TOP
}

/**
 * Represents the CSS `dominant-baseline` property.
 * Specifies the dominant baseline for alignment.
 */
@Serializable
data class DominantBaselineProperty(
    val value: DominantBaselineValue
) : IRProperty {
    override val propertyName = "dominant-baseline"
}
